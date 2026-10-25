"""Pick a set of well-tested puzzles from the Lichess puzzle database (CC0) for the app.

Usage:
  curl -L -o lichess_db_puzzle.csv.zst https://database.lichess.org/lichess_db_puzzle.csv.zst
  python scripts/puzzle_set.py lichess_db_puzzle.csv.zst app/src/main/assets/puzzles/puzzles.tsv

Needs zstandard. Keeps popular, often played puzzles with a settled rating, then takes about the
same number from each 100-point rating band. Output rows: id, FEN, moves, rating, themes (tab separated).
"""

import csv
import io
import random
import sys

import zstandard

TOTAL = 100_000
# Sample kept per band while reading.
KEEP = 20_000
MIN_POPULARITY = 80
MIN_PLAYS = 300
MAX_DEVIATION = 90
BAND = 100
# Only the themes the app matches or shows.
THEMES = {
    "mate", "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5", "fork", "hangingPiece", "pin", "skewer",
    "discoveredAttack", "doubleCheck", "sacrifice", "deflection", "attraction", "endgame",
}


def main(src, out):
    rng = random.Random(1)
    bands = {}
    seen = {}
    with open(src, "rb") as f:
        text = io.TextIOWrapper(zstandard.ZstdDecompressor().stream_reader(f), encoding="utf-8")
        for row in csv.DictReader(text):
            if int(row["Popularity"]) < MIN_POPULARITY or int(row["NbPlays"]) < MIN_PLAYS:
                continue
            if int(row["RatingDeviation"]) > MAX_DEVIATION:
                continue
            rating = int(row["Rating"])
            band = rating // BAND
            themes = " ".join(t for t in row["Themes"].split() if t in THEMES)
            item = (row["PuzzleId"], row["FEN"], row["Moves"], rating, themes)
            # A random sample of up to KEEP puzzles per band, enough for any band's share.
            n = seen.get(band, 0) + 1
            seen[band] = n
            keep = bands.setdefault(band, [])
            if len(keep) < KEEP:
                keep.append(item)
            else:
                j = rng.randrange(n)
                if j < KEEP:
                    keep[j] = item
    # Even share per band; what small bands leave goes to the others.
    picked = []
    left = sorted(bands, key=lambda b: len(bands[b]))
    room = TOTAL
    while left:
        share = room // len(left)
        b = left.pop(0)
        rng.shuffle(bands[b])
        take = bands[b][:share]
        picked += take
        room -= len(take)
    picked.sort(key=lambda p: (p[3], p[0]))
    with open(out, "w", encoding="utf-8", newline="\n") as w:
        for p in picked:
            w.write("\t".join(map(str, p)) + "\n")
    print(f"{len(picked)} puzzles, ratings {picked[0][3]}-{picked[-1][3]}, from {sum(seen.values())} candidates")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
