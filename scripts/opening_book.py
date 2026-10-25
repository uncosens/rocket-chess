"""Builds the offline opening book (app/src/main/assets/opening/book.bin) from a Lichess game dump.

Usage:
  scripts/lichess_dump.sh YYYY-MM | python scripts/opening_book.py extract WORK [MAX_GAMES [MIN_RATING]]
  python scripts/opening_book.py count WORK [MORE_WORK ...]
  python scripts/opening_book.py write WORK OUT.bin MIN_GAMES
  python scripts/opening_book.py names CHESS_OPENINGS_DIR OUT.tsv
  python scripts/opening_book.py vectors OUT.tsv

Needs python-chess, numpy and zstandard. Only rated rapid games are used, from the start position,
first MAX_PLY plies. Positions are keyed by a hash of their pieces, side, castling and
en passant square (see position_code), the same key as OpeningBook.kt.
"""
import hashlib
import io
import json
import multiprocessing
import os
import re
import struct
import sys

import chess
import numpy as np

MAX_PLY = 30
# Bands by the mean Lichess rapid rating of the two players. The edges are Chess.com rapid 1200, 1500,
# 1800 and 2100, turned into Lichess rapid with the fit of github.com/theophile-wallez/lichess-chesscom-ratings.
BAND_EDGES = (1560, 1750, 1930, 2140)
CHESSCOM_EDGES = (1200, 1500, 1800, 2100)
BANDS = len(BAND_EDGES) + 1
PARTS = 64
# A record: position key, move, mean rating of the two players, result (0 white wins, 1 draw, 2 black wins).
RECORD = np.dtype([("k", "<u8"), ("m", "<u2"), ("r", "<u2"), ("w", "u1")])
RESULTS = {"1-0": 0, "1/2-1/2": 1, "0-1": 2}
PROMO = {None: 0, chess.KNIGHT: 1, chess.BISHOP: 2, chess.ROOK: 3, chess.QUEEN: 4}
MAGIC = b"RCOB"
VERSION = 2

COMMENT = re.compile(r"\{[^}]*\}|\([^)]*\)|\$\d+")
NUMBER = re.compile(r"^\d+\.+$")


CORNERS = (chess.BB_H1, chess.BB_A1, chess.BB_H8, chess.BB_A8)


def position_code(board):
    """99 bytes: 12 piece bitboards (White P N B R Q K, then Black; little-endian, a1 = bit 0),
    side to move (1 White), castling bits (1 K, 2 Q, 4 k, 8 q; only with king and rook at home),
    en passant square (0..63, only when a capture there is legal, else 64)."""
    boards = [board.pieces_mask(pt, color) for color in (chess.WHITE, chess.BLACK) for pt in chess.PIECE_TYPES]
    rights = board.clean_castling_rights()
    castling = sum(1 << i for i, bb in enumerate(CORNERS) if rights & bb)
    ep = board.ep_square if board.has_legal_en_passant() else 64
    return struct.pack("<12Q3B", *boards, board.turn == chess.WHITE, castling, ep)


def key_of(board):
    """64-bit position key: SHA-1 of position_code, first 8 bytes, big-endian. Same as OpeningBook.kt."""
    return int.from_bytes(hashlib.sha1(position_code(board)).digest()[:8], "big")


def move_code(move):
    """from | to << 6 | promotion << 12 (0 none, 1 N, 2 B, 3 R, 4 Q)."""
    return move.from_square | move.to_square << 6 | PROMO[move.promotion] << 12


def header(text, name):
    m = re.search(r'^\[' + name + r' "([^"]*)"\]', text, re.M)
    return m.group(1) if m else None


def parse_game(text):
    """(mean rating, result, SAN moves) for a usable rated rapid game, else None."""
    if not text.startswith('[Event "Rated Rapid'):
        return None
    result = RESULTS.get(header(text, "Result"))
    we, be = header(text, "WhiteElo"), header(text, "BlackElo")
    if result is None or not (we and be and we.isdigit() and be.isdigit()):
        return None
    rating = (int(we) + int(be)) // 2
    if header(text, "Termination") == "Abandoned" or header(text, "FEN"):
        return None
    body = text[text.find("\n\n") + 2:]
    sans = []
    for tok in COMMENT.sub(" ", body).split():
        if NUMBER.match(tok):
            continue
        if tok in RESULTS or tok == "*":
            break
        sans.append(tok.rstrip("?!"))
        if len(sans) == MAX_PLY:
            break
    return rating, result, sans


# Per worker: move prefix ("e4 e5 Nf3") -> (key before its last move, that move).
# Openings repeat a lot, so most early moves skip SAN parsing and hashing.
PREFIXES = {}
PREFIX_PLY = 16
PREFIX_MAX = 1_000_000


def records(games):
    """Records for a batch of game texts; a bad move ends that game's records."""
    out = []
    for text in games:
        g = parse_game(text)
        if g is None:
            continue
        rating, result, sans = g
        board, done, prefix = None, [], ""
        for i, san in enumerate(sans):
            prefix += " " + san
            hit = PREFIXES.get(prefix) if board is None else None
            if hit:
                out.append((hit[0], move_code(hit[1]), rating, result))
                done.append(hit[1])
                continue
            if board is None:
                # First move not in the cache: replay the cached ones.
                board = chess.Board()
                for m in done:
                    board.push(m)
            try:
                move = board.parse_san(san)
            except ValueError:
                break
            key = key_of(board)
            out.append((key, move_code(move), rating, result))
            board.push(move)
            if i < PREFIX_PLY and len(PREFIXES) < PREFIX_MAX:
                PREFIXES[prefix] = (key, move)
    return np.array(out, dtype=RECORD)


def games_from(stream):
    """Yields the text of each game in a PGN byte stream."""
    rest = b""
    while True:
        block = stream.read(1 << 24)
        if not block:
            break
        parts = (rest + block).split(b"\n\n[Event ")
        rest = parts.pop()
        for p in parts:
            yield p if p.startswith(b"[Event ") else b"[Event " + p
    if rest.strip():
        yield rest if rest.startswith(b"[Event ") else b"[Event " + rest


ELO = re.compile(rb'^\[(?:White|Black)Elo "(\d+)"\]', re.M)


def rapid_batches(stream, max_games, min_rating=0, size=2000):
    """Batches of rated rapid game texts; stops after max_games of them.
    With min_rating, games below that mean rating are dropped here, before any decoding."""
    batch, n = [], 0
    for raw in games_from(stream):
        if not raw.startswith(b'[Event "Rated Rapid'):
            continue
        if min_rating:
            elos = ELO.findall(raw, 0, raw.find(b"\n\n"))
            if len(elos) != 2 or (int(elos[0]) + int(elos[1])) // 2 < min_rating:
                continue
        batch.append(raw.decode("utf-8", "replace"))
        n += 1
        if len(batch) == size:
            yield batch
            batch = []
        if max_games and n >= max_games:
            break
    if batch:
        yield batch


def extract(work, max_games, min_rating):
    """Appends records to WORK. With min_rating, only games whose mean rating reaches it are kept,
    to add games for the higher bands from more months. max_games 0 reads all."""
    import zstandard
    os.makedirs(work, exist_ok=True)
    files = [open(os.path.join(work, f"part{i:02d}.rec"), "ab") for i in range(PARTS)]
    reader = zstandard.ZstdDecompressor().stream_reader(sys.stdin.buffer, read_across_frames=True)
    n_games = n_rec = 0
    with multiprocessing.Pool() as pool:
        try:
            for i, rec in enumerate(pool.imap(records, rapid_batches(reader, max_games, min_rating), chunksize=1), 1):
                part = (rec["k"] >> np.uint64(58)).astype(np.int64)
                for p in np.unique(part):
                    rec[part == p].tofile(files[p])
                n_rec += len(rec)
                n_games += 2000
                if i % 100 == 0:
                    print(f"about {n_games} rapid games, {n_rec} records", flush=True)
        except zstandard.ZstdError as e:
            # A cut stream (e.g. a partial download) still gives the games before the cut.
            print("stream ended:", e, flush=True)
    for f in files:
        f.close()
    print(f"done: {n_rec} records", flush=True)


def count(work, more=()):
    """Per (position, move): games by band and result. Also the totals per position. Bands are set here,
    so new BAND_EDGES need only a new count, not a new extract. Records of [more] work folders
    (e.g. other months) are added; counts.npz goes to [work]."""
    moves, positions = [], []
    for i in range(PARTS):
        rec = np.concatenate([np.fromfile(os.path.join(w, f"part{i:02d}.rec"), dtype=RECORD) for w in (work, *more)])
        if len(rec) == 0:
            continue
        rec = rec[np.lexsort((rec["m"], rec["k"]))]
        code = np.searchsorted(np.array(BAND_EDGES), rec["r"], side="right") * 3 + rec["w"]
        new = np.ones(len(rec), bool)
        new[1:] = (rec["k"][1:] != rec["k"][:-1]) | (rec["m"][1:] != rec["m"][:-1])
        group = np.cumsum(new) - 1
        counts = np.bincount(group * 3 * BANDS + code, minlength=(group[-1] + 1) * 3 * BANDS).reshape(-1, 3 * BANDS)
        keys, mvs = rec["k"][new], rec["m"][new]
        # Pairs seen fewer than 3 times can never pass a useful MIN_GAMES; dropping them keeps memory low.
        keep = counts.sum(1) >= 3
        moves.append((keys[keep], mvs[keep], counts[keep].astype(np.uint32)))
        pos_new = np.ones(len(keys), bool)
        pos_new[1:] = keys[1:] != keys[:-1]
        totals = np.add.reduceat(counts, np.flatnonzero(pos_new), axis=0).astype(np.uint32)
        # Totals only for positions that still have a move.
        used = np.isin(keys[pos_new], keys[keep])
        positions.append((keys[pos_new][used], totals[used]))
        print(f"part {i}: {len(rec)} records, {len(keys)} pairs", flush=True)
    np.savez(os.path.join(work, "counts.npz"),
             mk=np.concatenate([m[0] for m in moves]), mm=np.concatenate([m[1] for m in moves]),
             mc=np.concatenate([m[2] for m in moves]),
             pk=np.concatenate([p[0] for p in positions]), pc=np.concatenate([p[1] for p in positions]))


def varint(n, out):
    while n >= 0x80:
        out.append(n & 0x7F | 0x80)
        n >>= 7
    out.append(n)


def write(work, out_path, min_games):
    """Keeps moves with at least min_games games in some band, and writes the book file.

    Layout (big-endian): "RCOB", version u8, bands u8, Lichess band edges u16 x (bands - 1),
    the same edges as Chess.com ratings u16 x (bands - 1), positions u32,
    then per position key u64 + data offset u32, sorted by unsigned key, then the data.
    Data per position: bands x 3 varint totals (band-major: white, draw, black), varint move count,
    then per move: move code u16 and bands x 3 varint counts. Moves are sorted by total games, most first.
    """
    d = np.load(os.path.join(work, "counts.npz"))
    mk, mm, mc = d["mk"], d["mm"], d["mc"]
    by_band = mc.reshape(-1, BANDS, 3).sum(2)
    keep = by_band.max(1) >= min_games
    mk, mm, mc = mk[keep], mm[keep], mc[keep]
    order = np.lexsort((-mc.sum(1).astype(np.int64), mk))
    mk, mm, mc = mk[order], mm[order], mc[order]
    totals = dict(zip(d["pk"].tolist(), d["pc"]))
    starts = np.flatnonzero(np.r_[True, mk[1:] != mk[:-1]])
    ends = np.r_[starts[1:], len(mk)]
    index, data = bytearray(), bytearray()
    for s, e in zip(starts, ends):
        key = int(mk[s])
        index += struct.pack(">QI", key, len(data))
        for c in totals[key]:
            varint(int(c), data)
        varint(int(e - s), data)
        for j in range(s, e):
            data += struct.pack(">H", int(mm[j]))
            for c in mc[j]:
                varint(int(c), data)
    edges = BANDS - 1
    head = MAGIC + struct.pack(f">BB{edges}H{edges}HI", VERSION, BANDS, *BAND_EDGES, *CHESSCOM_EDGES, len(starts))
    with open(out_path, "wb") as f:
        f.write(head + index + data)
    size = len(head) + len(index) + len(data)
    print(json.dumps({"min_games": min_games, "positions": len(starts), "moves": int(len(mk)), "bytes": size}))


def names(src_dir, out_path):
    """Opening names by position key, from lichess-org/chess-openings a.tsv..e.tsv (eco, name, pgn)."""
    rows = {}
    for vol in "abcde":
        with open(os.path.join(src_dir, f"{vol}.tsv"), encoding="utf-8") as f:
            next(f)
            for line in f:
                eco, name, pgn = line.rstrip("\n").split("\t")[:3]
                board = chess.Board()
                for tok in pgn.split():
                    if not NUMBER.match(tok):
                        board.push_san(tok)
                # The first name for a position wins; later lines are mostly transpositions.
                rows.setdefault(key_of(board), (eco, name))
    with open(out_path, "w", encoding="utf-8") as f:
        for key in sorted(rows):
            eco, name = rows[key]
            f.write(f"{key:016x}\t{eco}\t{name}\n")
    print(f"{len(rows)} names")


def vectors(out_path):
    """Test vectors for OpeningBook.kt: FEN as chesslib writes it (en passant after any double step), key."""
    lines = [
        [],
        ["e4"],
        ["e4", "d5", "e5", "f5"],  # exf6 is legal: en passant kept
        ["e4", "a6", "e5", "d5"],  # en passant legal
        ["a4", "h6", "a5", "b5"],  # en passant legal on b6
        ["d4", "d5"],  # no pawn can take: en passant dropped
        ["Nf3", "Nf6", "g3", "g6", "Bg2", "Bg7", "O-O", "O-O"],
        ["e4", "e5", "Ke2", "Ke7"],  # castling rights gone
        ["h4", "a5", "Rh3", "Ra6", "Rh1", "Ra8"],  # rook moved and back: rights gone
    ]
    fens = [
        "r3k3/8/8/8/8/8/8/4K2R w KQkq - 0 1",  # rights claimed without a rook at home
        "8/8/8/K2pP2r/8/8/8/7k w - d6 0 1",  # exd6 would leave the king in check: en passant dropped
    ]
    with open(out_path, "w", encoding="utf-8") as f:
        for sans in lines:
            board = chess.Board()
            for s in sans:
                board.push_san(s)
            f.write(f"{board.fen(en_passant='fen')}\t{key_of(board):016x}\n")
        for fen in fens:
            f.write(f"{fen}\t{key_of(chess.Board(fen)):016x}\n")


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "extract":
        extract(args[0], int(args[1]) if len(args) > 1 else 0, int(args[2]) if len(args) > 2 else 0)
    elif cmd == "count":
        count(args[0], args[1:])
    elif cmd == "write":
        write(args[0], args[1], int(args[2]))
    elif cmd == "names":
        names(args[0], args[1])
    elif cmd == "vectors":
        vectors(args[0])
    else:
        sys.exit(__doc__)
