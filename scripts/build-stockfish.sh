#!/bin/sh
# Build Stockfish for arm64-v8a with the NNUE net embedded. Run on Linux/WSL.
# Usage: ANDROID_NDK_HOME=/path/to/ndk scripts/build-stockfish.sh
set -eu

SF_TAG=sf_19
SF_COMMIT=edb0d9db6731067ec50ce619ff372b463bc4dd5d
ROOT="$(pwd)"
OUT="$ROOT/build/stockfish"

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}"
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"

mkdir -p "$OUT"
[ -d "$OUT/src" ] || git clone --depth 1 --branch "$SF_TAG" https://github.com/official-stockfish/Stockfish.git "$OUT/src"
cd "$OUT/src"
[ "$(git rev-parse HEAD)" = "$SF_COMMIT" ] || { echo "commit mismatch"; exit 1; }
for p in "$ROOT"/stockfish-patches/*.patch; do [ ! -e "$p" ] || git apply "$p"; done

cd src
make net
make -j"$(nproc)" build ARCH=armv8 COMP=ndk >"$OUT/build.log" 2>&1 || { tail -20 "$OUT/build.log"; exit 1; }
cp stockfish "$OUT/stockfish-arm64-v8a"

cd "$OUT"
{
  echo "tag=$SF_TAG commit=$SF_COMMIT"
  echo "ndk=$(grep Pkg.Revision "$ANDROID_NDK_HOME/source.properties")"
  echo "cmd=make build ARCH=armv8 COMP=ndk"
  echo "net=$(ls src/src/nn-*.nnue)"
  sha256sum stockfish-arm64-v8a
  file stockfish-arm64-v8a
  ls -l stockfish-arm64-v8a
} | tee build-info.txt
