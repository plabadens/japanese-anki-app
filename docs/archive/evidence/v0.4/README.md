# Japanese Anki v0.4 — package and install

> **HISTORICAL (2026-10-22):** install notes for the v0.4 build, kept as the release record. `SPEC.md`
> governs what the app must do; current packaging facts are in
> `docs/first-run-resource-delivery.md`.

Personal sideloaded app. v0.4 is the expressive-flow UI (Input → Target → Review) built natively in
Kotlin/Compose from the `mockup/expressive-flow` portrait study. Behaviour is v0.3's: local OCR,
UniDic/JMdict, AJT pitch, NHK word audio, Google Chirp 3 HD sentence audio, Cloud Translation Basic
v2 and AnkiDroid export.

## Package

| | |
|---|---|
| File | `releases/v0.4/japanese-anki-0.4-debug.apk` |
| Size | 548 657 117 bytes |
| SHA-256 | `32e48055185d4b069d20816d1d2c90ee195d6830278af57d350684bac6d64d90` (`SHA256SUMS.txt`) |
| Version | `versionName 0.4`, `versionCode 4` (0.3 shipped as 3) |
| SDK | `minSdk 37`, `targetSdk 37`, `compileSdk 37` (Android 17) |
| ABIs | `arm64-v8a` (phone) and `x86_64` (emulator) |
| Signature | debug identity `CN=Android Debug`, cert SHA-256 `1446fc8e57cc68f3e585f6f6dfa31ab60c5346644f4f1df53f4133915285635d` — the same key as the delivered v0.2 and v0.3 APKs |

The signing key is a local debug key, kept outside the repository at
`/srv/hermes/.android/debug.keystore` and supplied through `JAPANESE_ANKI_DEBUG_KEYSTORE`.

## Install / update

```
adb install -r -g releases/v0.4/japanese-anki-0.4-debug.apk
```

Updating an installed v0.3 works in place — same certificate, no data loss. `-g` grants camera up
front; denying it keeps Type and the gallery usable.

First launch after a fresh install unpacks UniDic (about 725 MB, progress in the app). Keep ~1 GB
free. OCR, dictionary and pitch work offline afterwards.

## Rebuild

```
nix develop --command ./scripts/test-core.sh
nix develop --command bash -c 'export JAPANESE_ANKI_DEBUG_KEYSTORE=/srv/hermes/.android/debug.keystore; \
  gradle --no-daemon --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest assembleDebug'
```

Artifact: `app/build/outputs/apk/debug/app-debug.apk`.

## Acceptance

`docs/archive/evidence/v0.4/ACCEPTANCE.md` holds the tested revision, commands, test totals, the
runtime journey, the screenshot index, the SPEC matrix and the known defects. Screenshots are the
numbered PNGs in this directory.

Live Google services and AnkiDroid export were not exercised in this run; those rows are NOT RUN
there.
