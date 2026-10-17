# Rocket Chess

An offline chess analysis app for Android. It runs Stockfish on the device.
Package ID: `com.uncosens.rocketchess`.
Status: early setup (M0). There is no app code yet.

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
  Needs only `libc.so`, `libm.so`, `libdl.so`. Not run on a device yet.
- Patches, if any, go in `stockfish-patches/*.patch`.
- The NDK build targets Android API 29 (`aarch64-linux-android29-clang++` in the Makefile).

## Sources checked

| Source | Checked | Note |
|---|---|---|
| https://github.com/official-stockfish/Stockfish (tag `sf_19`, `src/Makefile`) | 2026-10-17 | `COMP=ndk` supported, "NDK r27c or later", one default net `nn-1a298aa575a0.nnue` |

## Open checks

See [docs/API_TERMS_CHECKLIST.md](docs/API_TERMS_CHECKLIST.md).
