# Privacy notes (draft)

Facts about data flow in the current code, for the privacy policy and the Google Play Data safety form. Not legal advice.

- No own server, no analytics SDK, no ads SDK, no account.
- Network use happens only when the user searches a user name:
  - Chess.com: the name is sent to `api.chess.com` to list public games.
  - Lichess: the name is sent to `lichess.org` to list public games.
  - The sites also see the device IP address and the app's User-Agent, like any web request.
- Stockfish runs on the device. Analysis does not use the network.
- Stored on the device (Room database `games.db`): raw PGN, source, player names, result and date of each saved game.
  The user can delete one game or all games in the app. Uninstalling the app removes them too.
- Analysis results are not stored.
- Logs: the engine's stderr goes to logcat as diagnostics. User names and PGN text are not logged by the app.
- Not stored: the searched user name (only the games the user picks are saved).
