# API and terms checklist

Check each item again before the first public release. Write the date and what you found.

## Lichess
- [ ] API docs for user games export: https://lichess.org/api (path, `max`/`since`/`until`, `Accept` for PGN vs NDJSON)
- [ ] Rate limits and the "one request at a time" rule
- [ ] Required `User-Agent` / contact info
- [ ] Terms of service: https://lichess.org/terms-of-service
- [ ] Is commercial use, caching and showing games allowed?
- [ ] Name and logo use (trademark)

## Chess.com
- [ ] PubAPI docs: https://www.chess.com/news/view/published-data-api (archives list, monthly games, PGN)
- [ ] Rate limits, parallel request rules, `User-Agent` rule
- [ ] Terms of service and PubAPI usage terms
- [ ] Is commercial use, caching and showing games allowed?
- [ ] Name and logo use (trademark)

## Google Play
- [ ] Target API level rule at release time
- [ ] 16 KB page size rule: https://developer.android.com/guide/practices/page-sizes
- [ ] Data safety form matches real network calls (Lichess, Chess.com)
- [ ] Rules on running bundled executables

## Stockfish
- [ ] GPL-3.0 duties: license text, source link for the exact binary, build steps, patches
- [ ] License of the NNUE net file
- [ ] "Stockfish" name use in store listing
