# Rocket Chess

An offline chess analysis app for Android. It runs Stockfish on the device.
Package ID: `com.uncosens.rocketchess`.
Status: M1 (engine check). The app only runs one Stockfish search and shows the result.

## License

GPL-3.0. See [LICENSE](LICENSE). Third-party parts are listed in [THIRD_PARTY.md](THIRD_PARTY.md).

## Build Stockfish

Needs Linux or WSL, `git`, `make`, `curl`, and Android NDK r28 or newer.

```sh
ANDROID_NDK_HOME=/path/to/ndk scripts/build-stockfish.sh
```

- Source: Stockfish `sf_19`, commit `edb0d9db6731067ec50ce619ff372b463bc4dd5d`.
- Command: `make net` then `make build ARCH=armv8 COMP=ndk`. The NNUE net is embedded in the binary.
- Output: `build/stockfish/stockfish-arm64-v8a`, plus `build.log` and `build-info.txt` (hash, NDK version, ELF type).
- Tested setup: WSL2 Ubuntu 26.04.1 LTS, NDK r30 (`30.0.16248370`, `android-ndk-r30-linux.zip` SHA1 `5107f898313790e449e87eee2183d9a20602dee9`), clang 21.0.0, GNU Make 4.4.1.
- Last build (2026-10-17): ELF 64-bit aarch64 PIE, Android 29, 104,999,448 bytes (99,641,448 after `llvm-strip`),
  sha256 `6bef3cb2e94e59d2041209f65e4cc15862f12535384c7dd4cc0d8491dece341e` (unstripped).
  NNUE net (98,511,183 bytes) found byte-for-byte inside the binary. LOAD segments aligned to 16 KB (`0x4000`).
  Needs only `libc.so`, `libm.so`, `libdl.so`.
- The script also writes a stripped copy to `app/src/main/jniLibs/arm64-v8a/libstockfish.so` (sha256 `e35bd90a…f0bb`). This file is not in git.
- Patches, if any, go in `stockfish-patches/*.patch`.
- The NDK build targets Android API 29 (`aarch64-linux-android29-clang++` in the Makefile).

## Build the app

Needs JDK 21 and an Android SDK with `platforms/android-37.0`. Set `sdk.dir` in `local.properties`. Build Stockfish first.

```sh
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest   # engine tests on a connected device
```

- AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, Compose BOM 2026.09.00.
- compileSdk 37 (Compose needs it), targetSdk 36, minSdk 29, ABI `arm64-v8a` only.

## How the engine runs

Stockfish is a normal executable, not a library. It is packed in the APK as `lib/arm64-v8a/libstockfish.so`
with `useLegacyPackaging = true`, so the installer extracts it to `nativeLibraryDir`.
The app starts it with `ProcessBuilder` and talks UCI over stdin/stdout. It never calls `System.loadLibrary()`.

## M1 engine check (2026-10-17)

| Check | Galaxy Tab S7 (SM-T870, SM8250, Android 13, 4 KB pages) |
|---|---|
| APK: `libstockfish.so` stored compressed, zipalign `-P 16` check passes | pass (debug APK 110,457,591 bytes) |
| Installed file: `-rwxr-xr-x`, 99,641,448 bytes, sha256 matches build | pass |
| Run with `ProcessBuilder` | pass |
| `uci`/`uciok`, `isready`/`readyok`, `ucinewgame`, `position`, `go depth 12`, `bestmove` | pass |
| `go infinite` then `stop` gives `bestmove`, session still usable | pass |
| `quit` ends process; `kill -9` is reported as an error; a new session works | pass |
| App force-stop leaves no engine process; relaunch works | pass |
| App screen shows `id name Stockfish 19` and `bestmove e2e4 ponder c7c5` | pass |
| Offline | the app has no `INTERNET` permission |
| 16 KB page device | **not verified** (tablet uses 4 KB pages) |

16 KB pages: only static checks pass. The ELF LOAD segments are aligned to `0x4000`, and `zipalign -c -P 16 -v 4` passes on the APK.
The run on a 16 KB device is **not verified**. The Android emulator hangs at start on this PC (WHPX on AMD Ryzen 7 6800HS), for both the 16 KB and the 4 KB x86_64 images.
Ways to check: a Pixel 8/9 with "Boot with 16KB page size", Samsung Remote Test Lab 16 KB devices, or Cuttlefish in WSL2.
Play blocks updates without 16 KB support from 2027-02-01 (see the Android page-size guide).

## Sources checked

| Source | Checked | Note |
|---|---|---|
| https://github.com/official-stockfish/Stockfish (tag `sf_19`, `src/Makefile`) | 2026-10-17 | `COMP=ndk` supported, "NDK r27c or later", one default net `nn-1a298aa575a0.nnue` |

## Open checks

See [docs/API_TERMS_CHECKLIST.md](docs/API_TERMS_CHECKLIST.md).
