# API and terms checklist

Check each item again before the first public release. Write the date and what you found.

## Lichess
- [x] API docs for user games export (checked 2026-10-17 in github.com/lichess-org/api `doc/specs`)
- [x] Rate limits and the "one request at a time" rule (429: wait a minute)
- [ ] Required `User-Agent` / contact info
- [ ] Terms of service: https://lichess.org/terms-of-service
- [ ] Is commercial use, caching and showing games allowed?
- [ ] Name and logo use (trademark)

## Chess.com
- [x] PubAPI docs: https://www.chess.com/news/view/published-data-api (checked 2026-10-17)
- [x] Rate limits, parallel request rules, `User-Agent` rule
- [ ] Chess.com asks apps to respect its IP (piece sets, colors, classification glyphs). We use standard `?!`/`?`/`??` marks and our own colors; confirm this is fine
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
