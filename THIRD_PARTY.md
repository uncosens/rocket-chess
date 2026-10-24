# Third-party components

Draft. Fill in every row before a release. Each row must match the shipped binary.

| Component | Version / commit | License | Modified | Source |
|---|---|---|---|---|
| Stockfish | `sf_19` / `edb0d9db6731067ec50ce619ff372b463bc4dd5d` | GPL-3.0 | No (patches go in `stockfish-patches/`) | https://github.com/official-stockfish/Stockfish/tree/sf_19 |
| chesslib | `com.github.bhlangonijr:chesslib:1.3.7` (JitPack) | Apache-2.0 | No | https://github.com/bhlangonijr/chesslib/tree/1.3.7 |
| Lichess open database (opening statistics in `assets/opening/book.bin`) | `lichess_db_standard_rated_2026-09.pgn.zst` | CC0-1.0 | Counted by `scripts/opening_book.py` | https://database.lichess.org |
| chess-openings (opening names in `assets/opening/names.tsv`) | `a6189a30dc273ccb21fc2536a9a2fefd5592a67a` | CC0-1.0 | Keyed by position | https://github.com/lichess-org/chess-openings |
| Stockfish NNUE net (embedded) | `nn-1a298aa575a0.nnue` | CC0-1.0 (networks repo); trained on Lc0 data under ODbL | No | https://github.com/official-stockfish/networks |

Notes
- Stockfish copyright: The Stockfish developers (see `AUTHORS` in the Stockfish source).
- App libraries (from `app/build.gradle.kts`): Jetpack Compose BOM 2026.09.00 (incl. Material 3), `androidx.activity:activity-compose` 1.13.0, `androidx.lifecycle:lifecycle-viewmodel-compose` 2.11.0, Room 2.8.5, OkHttp 5.5.0 (with Okio), kotlinx-serialization-json 1.11.0, Kotlin standard library 2.4.21. All Apache-2.0. Exact transitive versions and notices still need a full list before release.
- Test only, not shipped: `androidx.test.ext:junit` 1.3.0, `androidx.test:runner` 1.7.0 (Apache-2.0), JUnit 4 (EPL-1.0).
- The in-app notice file is `app/src/main/assets/licenses/third_party.txt`, made by `scripts/third_party_notices.py`.
