#!/bin/bash
# Streams a Lichess standard game dump to stdout, then checks its SHA-256 against Lichess's list.
# Usage: scripts/lichess_dump.sh YYYY-MM | python scripts/opening_book.py extract WORK
# One connection can be slow or break off, so the file comes in 1 GiB pieces, three at a time,
# each resumed until complete. Pieces wait in a temp folder and are deleted once sent.
set -euo pipefail

MONTH=$1
NAME="lichess_db_standard_rated_$MONTH.pgn.zst"
BASE="https://database.lichess.org/standard"
PIECE=${PIECE:-1073741824}
AHEAD=3
TMP=$(mktemp -d)
trap 'rm -rf "${TMP:?}"' EXIT

SIZE=$(curl -sSI "$BASE/$NAME" | tr -d '\r' | awk 'tolower($1) == "content-length:" { print $2 }')
N=$(( (SIZE + PIECE - 1) / PIECE ))

fetch() {
  local a=$(( $1 * PIECE )) b=$(( ($1 + 1) * PIECE - 1 ))
  (( b >= SIZE )) && b=$(( SIZE - 1 ))
  local want=$(( b - a + 1 )) f="$TMP/p$1" have
  for try in $(seq 1 30); do
    have=$(stat -c %s "$f" 2>/dev/null || echo 0)
    (( have == want )) && return 0
    curl -sS -r "$(( a + have ))-$b" "$BASE/$NAME" >> "$f" || { echo "piece $1: retry $try" >&2; sleep 5; }
  done
  echo "piece $1: failed" >&2
  return 1
}

# One hash over all pieces in order.
exec 3> >(sha256sum | cut -d' ' -f1 > "$TMP/sum")
SUM_PID=$!
declare -A pid
for ((k = 0; k < N; k++)); do
  for ((j = k; j < k + AHEAD && j < N; j++)); do
    [[ -z "${pid[$j]:-}" ]] && { fetch "$j" & pid[$j]=$!; }
  done
  wait "${pid[$k]}"
  tee /dev/fd/3 < "$TMP/p$k"
  rm "$TMP/p$k"
  echo "piece $((k + 1))/$N sent" >&2
done
exec 3>&-
wait "$SUM_PID"

want=$(curl -sS "$BASE/sha256sums.txt" | awk -v n="$NAME" '$2 == n { print $1 }')
got=$(cat "$TMP/sum")
if [[ "$got" != "$want" ]]; then
  echo "SHA-256 mismatch: got $got, Lichess lists $want" >&2
  exit 1
fi
echo "SHA-256 ok: $got" >&2
