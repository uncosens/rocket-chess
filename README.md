# Rocket Chess

An offline chess analysis app for Android. It runs Stockfish on the device.
Package ID: `com.uncosens.rocketchess`.
Status: M5 (release checks). All v1 features are in. A real 16 KB page device run and store signing are still open.

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

## M2 board and PGN (2026-10-17)

- `chess/Pgn.kt` reads the first game of a PGN: tags, comments, `;` comments, NAGs, variations (skipped), results, missing result.
  Errors give line, column and reason. `chess/GameLine.kt` replays the moves with chesslib and rejects illegal or ambiguous moves.
- Import: paste text, or open a file with the system file picker (SAF). Files over 5 MB are refused. Text is read as UTF-8.
- The raw PGN text is kept in memory with the game. Saving it (Room) comes later.
- JVM tests: `./gradlew testDebugUnitTest` (7 tests: Opera game fixture, castling, en passant, promotion, Black to move first, disambiguation, error positions).
- Checked on the Galaxy Tab S7: file picker opens `opera.pgn`, final position and check square match the expected FEN,
  first/previous/next/last and tapping a move work, a bad file shows `PGN error at line 2, column 4: illegal move "Ke3"`.
- Not checked yet: portrait layout, screen rotation, light theme, TalkBack walk-through.
- Pieces are drawn with the device font's chess symbols. A device with an odd font may draw them badly; own vector pieces would fix that.

## M3 analysis (2026-10-17)

- Every position (start + one per ply) gets `go depth 14` with MultiPV 2, 2 threads, 32 MB hash (`analysis/Classification.kt`, `AnalysisPolicy`).
- Scores are stored from White's view. Mate keeps who wins and the distance. Win% uses `50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)`; mate is 0 or 100.
- Loss = mover's win% before the move minus after it. Best = the played move equals PV1's first move (UCI compare).
  Then Good < 2, Fine < 5, Weak < 10, Mistake < 20, Blunder >= 20 points (Chess.com's lines). This is our own test policy, not Lichess grading.
- Brilliant (our own rule): a piece sacrifice that is PV1, loses under 2 points, was played when the mover's win% was below 90,
  and leaves the mover at 50% or more.
  A sacrifice means the opponent can win the moved piece for a net 200+ (a minor piece for a pawn, or the exchange), by a static exchange on its square (`chess/Sacrifice.kt`).
- Great (our own rule): PV1 that beats PV2 by 10+ points for the mover and changes the result between them
  (losing < 40% <= even <= 60% < winning; losing to even, even to winning, or losing to winning). A plain recapture (taking back on the square
  the last move captured on) is never Great, and neither is any move before move 6 (we have no opening book).
  Brilliant wins over Great.
- Accuracy is the mean of a score per move, kept to 0..100. Move score = `100 - 208 * (1 - exp(-0.059 * loss))`, where loss is win% lost
  on a flatter curve (`0.00177` instead of `0.00368208`); a negative loss counts as 0. Grades still use the normal curve.
  Fitted to Chess.com's accuracy on 500 random rapid games of `t4ph3s` (1,000 sides, Stockfish 19, depth 14 like the app):
  2.9 points off on average in 5-fold cross-check (84% within 5 points). The old score per grade was 4.4 off (66% within 5).
  Chess.com's own formula is not public.
- A negative loss is graded as no loss. It happens because the next position is searched on its own and can find more.
- Stop keeps the results so far; ungraded moves stay ungraded. Going to the background stops the run and quits the engine.
  Each run has an id, so a stopped run cannot write results. Results are not saved; analyze again to get them back.
- Tests: 7 JVM tests (UCI parsing, White view, mate rules, win%, class edges 2/5/10/20) and a device test that analyzes the Opera game.
- Galaxy Tab S7 timing (Opera game, 34 positions): depth 14 took 13.2 s in the device test. Another run measured 25 positions:
  depth 16 25.8 s, depth 18 44.8 s; depth 14 took 91.8 s in that run (first in the run, cause unknown). Depth 14 kept for heat and time.
- Not done yet: settings screen for threads/hash/depth, eval bar, move arrows.

## M4 online import and saved games (2026-10-17)

- Chess.com: `GET /pub/player/{user}/games/archives`, then the newest monthly archives (at most 3) until 30 games. Only `rules == "chess"`.
- Lichess: `GET /api/games/user/{user}?max=30&pgnInJson=true&clocks=false&evals=false&opening=false` with `Accept: application/x-ndjson`.
  Bad lines are skipped. Only `standard` and `fromPosition` games.
- One request at a time. User-Agent `RocketChess/0.1 (+https://github.com/uncosens/rocket-chess)`. Timeouts 15 s connect, 30 s read. Bodies over 10 MB are refused.
- Errors shown apart: bad name, user not found (404/410), no connection, timeout, rate limited (429, uses `Retry-After` or 60 s), server error, bad data, PGN error.
  Network and 5xx errors retry up to 3 times (1 s, 2 s). 429 is not retried automatically. Search can be cancelled.
- Room keeps each game's raw PGN, source, players, result and date. Duplicates: one row per key (`chesscom:<uuid>`, `lichess:<id>`,
  or `pgn:<sha256 of trimmed text>` for pasted and file games). Importing again opens the saved copy. Delete one or all, with a confirm dialog.
- Tests: 6 JVM tests with JSON/NDJSON fixtures (shape from the docs and live responses, made-up players), a Room test on the device.
- Checked live on the Galaxy Tab S7: Chess.com `erik`, Lichess `thibault` listed and opened; unknown user shows "User not found";
  re-import keeps one copy; delete and delete all work. Test data was removed afterwards.
- Not checked live: offline, timeout, 429 and 5xx (only by unit tests of the mapping).
- Privacy notes for the store listing: [docs/PRIVACY_NOTES.md](docs/PRIVACY_NOTES.md).

## M5 licenses and release checks (2026-10-17)

- In-app "About & licenses" screen: app GPL-3.0 notice with a link to the source of this build, Stockfish tag and source link,
  app commit, Stockfish commit and engine SHA-256 (from `BuildConfig`), and full GPL-3.0, Apache-2.0 and third-party notice texts.
- `scripts/third_party_notices.py` writes the notice file from the release dependency tree (115 libraries, all Apache-2.0, 1 NOTICE file).
- `scripts/check_apk.py` checks an APK. Release and debug APKs pass: only `arm64-v8a`, every `.so` has 16 KB LOAD alignment,
  `zipalign -c -P 16` passes, engine hash matches, minSdk 29, targetSdk 36. Release steps: [docs/RELEASE.md](docs/RELEASE.md).
- Sizes: release APK (unsigned) 105,570,943 bytes, release AAB 88,691,646 bytes.
- Checked on the Galaxy Tab S7: licenses screen, each license text, back navigation.
- Not done: signing, AAB install through Play, run on a 16 KB page device.

## Sources checked

| Source | Checked | Note |
|---|---|---|
| https://support.google.com/googleplay/android-developer/answer/11926878 | 2026-10-17 | API 36 for new apps and updates from 2026-08-31 |
| https://github.com/official-stockfish/networks | 2026-10-17 | CC0-1.0; Stockfish README: nets trained on Lc0 data (ODbL) |
| https://github.com/lichess-org/api (`doc/specs`, games/user, rate limits) | 2026-10-17 | One request at a time; on 429 wait a minute |
| https://www.chess.com/news/view/published-data-api | 2026-10-17 | Serial requests; User-Agent with contact; 404/410/429; respect Chess.com IP (pieces, colors, classification glyphs) |
| https://github.com/bhlangonijr/chesslib (release 1.3.7, README) | 2026-10-17 | Apache-2.0, JitPack only, last push 2026-07 |
| https://github.com/official-stockfish/Stockfish (tag `sf_19`, `src/Makefile`) | 2026-10-17 | `COMP=ndk` supported, "NDK r27c or later", one default net `nn-1a298aa575a0.nnue` |

## Open checks

See [docs/API_TERMS_CHECKLIST.md](docs/API_TERMS_CHECKLIST.md).
