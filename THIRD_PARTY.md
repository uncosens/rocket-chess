# Third-party components

Draft. Fill in every row before a release. Each row must match the shipped binary.

| Component | Version / commit | License | Modified | Source |
|---|---|---|---|---|
| Stockfish | `sf_19` / `edb0d9db6731067ec50ce619ff372b463bc4dd5d` | GPL-3.0 | No (patches go in `stockfish-patches/`) | https://github.com/official-stockfish/Stockfish/tree/sf_19 |
| Stockfish NNUE net (embedded) | `nn-1a298aa575a0.nnue` | Ships inside Stockfish under GPL-3.0 (verify) | No | https://tests.stockfishchess.org/api/nn/nn-1a298aa575a0.nnue |

Notes
- Stockfish copyright: The Stockfish developers (see `AUTHORS` in the Stockfish source).
- Android, Kotlin and Jetpack libraries will be added here when the app project is created.
- License status of the NNUE net file is not confirmed yet. Check before release.
