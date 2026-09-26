# Japanese Anki Card Creator

Native Android app for creating Japanese sentence-mining notes in AnkiDroid. See [SPEC.md](SPEC.md) for approved behavior and release gates.

## Build environment (NixOS x86_64)

```sh
nix develop
gradle testDebugUnitTest assembleDebug
```

The Nix shell pins JDK, Gradle, Android SDK platforms, build tools, and NDK through `flake.lock`. Gradle resolves the pinned Android/Compose dependencies on the first build, which requires Maven/Google repository access. This is a development shell, not yet the fully sandboxed, network-independent reproducible APK derivation required for release. The app targets API 37 and supports API 35+. The root `gradle` command comes from the Nix shell, so no independently downloaded Gradle wrapper is used.

Install a debug build on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Emulator

No Android emulator MCP server is connected or discoverable in this session, and `adb devices -l` reports no attached devices. Emulator provisioning is not yet part of this environment; use a local Android Studio/SDK emulator or a connected phone for device tests.

## Current implementation

The editor now supports durable manual sentence/target/vocabulary/reading/definition/translation/notes/tags entry, required-field readiness feedback, a structured manual pitch accent editor/contour, Android `SEND` / `PROCESS_TEXT` entry, safe field serialization, required-field name mapping, and duplicate-key comparison. Automatic linguistic lookup/furigana/pitch, note-type/deck discovery, AnkiDroid permission/export, audio, image handling, settings, and the remaining specification are not implemented yet.
