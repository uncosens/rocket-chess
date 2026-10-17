# Release steps

How to build a release that can be matched to its source. This covers GPL source duties as we understand them; it is not legal advice.

## 1. Build the engine (WSL/Linux)

```sh
ANDROID_NDK_HOME=~/android/android-ndk-r30 scripts/build-stockfish.sh
```

Keep `build/stockfish/build-info.txt` (Stockfish tag and commit, NDK version, command, net, hashes) and `build.log`.

## 2. Update third-party notices

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > deps.txt
python scripts/third_party_notices.py deps.txt
```

The script stops if a library's license is not known. Update `THIRD_PARTY.md` too.

## 3. Commit and tag

The working tree must be clean, or the licenses screen shows `-dirty` after the app commit.
Tag the commit, e.g. `v0.1.0`, and push the tag so the "Source code of this build" link works.

## 4. Build and sign

```sh
./gradlew clean bundleRelease assembleRelease
```

Signing is not set up in this repo. Keep the upload key outside the repo and add the signing config locally or in CI.

## 5. Check the APK

```sh
python scripts/check_apk.py app/build/outputs/apk/release/app-release.apk $ANDROID_HOME/build-tools/36.1.0
```

It checks ABIs, ELF type and 16 KB LOAD alignment, the engine hash, `zipalign -P 16`, and min/target SDK.

## 6. Publish the source with the binary

On the GitHub release for the tag, attach or list:
- APK/AAB SHA-256, `build-info.txt`, the check report.
- Stockfish tag and commit (unmodified; any patch would go in `stockfish-patches/`), NDK version, build command.
- The app source is the repo at the tag. Stockfish source is the official repo at the commit.

The app shows the app commit, the Stockfish commit and the engine SHA-256 on the licenses screen, plus the GPL-3.0,
Apache-2.0 and third-party notice texts.

## Before the first store release

- Play target API rule: API 36 for new apps and updates since 2026-08-31 (extension possible to 2026-11-01). The app targets 36.
- Run the app on a real 16 KB page device (Pixel 8/9 with "Boot with 16KB page size", or Samsung Remote Test Lab). Not done yet.
- Install the AAB through an internal testing track and check that the engine still runs (native libs must be extracted).
- Go through `docs/API_TERMS_CHECKLIST.md` and `docs/PRIVACY_NOTES.md`.
