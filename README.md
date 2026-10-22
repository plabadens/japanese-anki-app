# Japanese Anki Card Creator

A personal, sideloaded Android app for mining one Japanese sentence and one vocabulary item into a
new note in AnkiDroid. Capture or type a sentence, choose the word and the meaning you want, let the
app produce readings, pitch, audio, translation and image, glance at the composed note, send it.

Native Kotlin + Jetpack Compose, API 37 only, built with Nix and Gradle. It creates **notes**;
AnkiDroid owns the note type, cards, media, scheduling and sync.

## Start here

| Document | What it gives you |
|---|---|
| [SPEC.md](SPEC.md) | **AUTHORITATIVE** — what the app must do: behaviour, contracts, acceptance criteria R01–R35 |
| [docs/DESIGN.md](docs/DESIGN.md) | **AUTHORITATIVE** — what it should feel like: intent, tone, visual language |
| [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) | **AUTHORITATIVE** — how work is done: modules, conventions, gates, commits, evidence |
| [docs/README.md](docs/README.md) | The index of every other document, with its status |
| [AGENTS.md](AGENTS.md) | Root rules for agent sessions |

## Quick start

```sh
nix develop                     # JDK 17, Gradle 8.14.4, SDK 37.0, build-tools 36.0.0, NDK, CMake, emulator, adb
./scripts/test-core.sh          # fast lane: platform-independent core tests
gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  testDebugUnitTest assembleDebug
```

The aapt2 override is required — the Maven-hosted aapt2 does not work in the NixOS sandbox. There is
no Gradle wrapper; nothing is expected from a globally installed Android SDK. The first Gradle run
needs network access to fill the Maven cache.

Output is split by ABI under `app/build/outputs/apk/debug/`: `app-arm64-v8a-debug.apk` for a phone,
`app-x86_64-debug.apk` for the emulator. There is no universal APK. To install on a device:

```sh
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

For an update-compatible build, point `JAPANESE_ANKI_DEBUG_KEYSTORE` at the existing keystore (see
`app/build.gradle.kts`); the Nix sandbox build signs with a throwaway key and cannot replace an
existing install.

## Emulator

```sh
nix run .#emulator-wrapper -- session app/build/outputs/apk/debug/app-x86_64-debug.apk .tmp-nix/screenshots/smoke
```

That boots the API-37.0 `google_apis` x86_64 AVD headlessly, installs and launches, captures
dark/light screenshots and logcat, scans for crashes and shuts down. The individual commands
(`start`, `status`, `install`, `launch`, `tap`, `theme`, `screenshot`, `dump`, `share-text`,
`share-image`, `logcat`, `stop`), the one-emulator-per-AVD rule and the ADB serial rules are in
[docs/BEST-PRACTICES.md §5](docs/BEST-PRACTICES.md#5-running-the-gates). Install AnkiDroid only in
this emulator, from an official distribution, into a disposable collection.

## Release packaging

A signed, installable APK is published by pushing a `v<versionName>` tag; the workflow attaches it to
a GitHub Release so it can be installed from the phone. The loop — issue forms, triage labels,
release signing secrets and failure modes — is [docs/agent-flow.md](docs/agent-flow.md).
Merges to `main` only produce debug APKs, which are debug-signed and cannot update a release install.

- `nix build .#debugApk` / `nix build .#apk` build inside the sandbox and run the unit tests
  (`doCheck`). Outputs are `/nix/store` paths and are not byte-identical across rebuilds; no
  reproducibility claim is made.
- `nix build .#unidicRuntime` produces the first-run UniDic asset (446,602,958 bytes, build-pinned
  SHA-256). The APK bundles the Japanese OCR model, the NHK-2016 index, the dictionary and the pitch
  data; UniDic alone is downloaded on first use (~725 MB extracted) and must be published beside the
  APK or the release is unusable.
- Bundled assets, their exact sizes, hashes and the open licensing questions are in
  [docs/first-run-resource-delivery.md](docs/first-run-resource-delivery.md).

## Where things are

```
app/src/main/java/dev/pierre/japaneseanki/   Kotlin: platform-independent core, Android layer
app/src/main/java/dev/pierre/japaneseanki/ui/  Compose screens and theme
app/src/test, app/src/androidTest            JVM tests, instrumentation tests
datasets/                                    pinned upstream data and asset builders
scripts/                                     build, test, dataset, emulator and screenshot tooling
misc/Sentence (JA)/                          reference Anki note type
mockup/expressive-flow/                      HTML design study (reference only; never shipped)
docs/                                        documentation, evidence, archive
```

## Status

This README does not track implementation status. The last accepted build and its known defects are
recorded in [docs/archive/evidence/v0.4/ACCEPTANCE.md](docs/archive/evidence/v0.4/ACCEPTANCE.md);
what is still unverified — including live Google speech/translation quality and a real AnkiDroid
export — is enumerated as acceptance rows in [SPEC.md §14](SPEC.md) and must be reported as NOT RUN
until it is actually exercised.
