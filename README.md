# Japanese Anki Card Creator

Native Android app for creating Japanese sentence-mining notes in AnkiDroid. See [SPEC.md](SPEC.md) for approved behavior and release gates.

## Build environment (NixOS x86_64)

```sh
nix develop
./scripts/test-core.sh
python3 scripts/test-build-dictionary.py
python3 scripts/test-package-unidic.py
python3 scripts/build_dictionary.py
python3 scripts/test-dictionary-asset.py
gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest assembleDebug
```

The Nix shell pins JDK, Gradle, Android SDK platforms, build tools, CMake, and NDK through `flake.lock`. The Gradle command above selects Nix's `aapt2` because Maven's generic Linux binary cannot run directly on NixOS. It fetches pinned UniDic (526 MiB) and MeCab sources and creates a deterministic 426 MiB runtime dictionary asset for the APK. First use expands UniDic to about 725 MB in app-private storage, so plan for that in addition to the installed app. `./scripts/test-core.sh` runs the platform-independent Kotlin/JUnit tests without resolving Android Gradle Plugin artifacts. A full APK build requires Maven/Google repository access; the current environment cannot resolve AGP 8.13.2. This is a development shell, not yet the fully sandboxed, network-independent reproducible APK derivation required for release. The app targets API 37 and supports API 35+. The root `gradle` command comes from the Nix shell, so no independently downloaded Gradle wrapper is used.

Install a debug build on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Emulator

No Android emulator MCP server is connected or discoverable in this session, and `adb devices -l` reports no attached devices. Emulator provisioning is not yet part of this environment; use a local Android Studio/SDK emulator or a connected phone for device tests.

## Current implementation

The editor supports durable manual sentence/target/vocabulary/reading/definition/translation/notes/tags entry, default tags, readiness feedback, stale-value blocking, pitch entry/contour, Android `SEND` / `PROCESS_TEXT`, AnkiDroid setup, safe field mapping, duplicate blocking, and interrupted-send reconciliation. JMdict and Kanjium are bundled in an offline SQLite index for lookup, sense selection, and pitch. UniDic CWJ 3.1.1 and MeCab 0.996 are pinned; token chips, inflection-aware lemma lookup, editable target/vocabulary reading suggestions, and best-effort sentence furigana with per-token corrections are wired. Native CMake builds succeeded for `arm64-v8a` and `x86_64`, and a host JNI smoke test verified token/feature output. The Android APK, on-device behavior, and first-launch 725 MB extraction remain unverified. AnkiDroid integration references v2.24.1 commit `9f579c10bb151146728220729c510acbbd8faba7`; device/API behavior and insertion with trailing empty fields remain release gates. Audio, image/media workflows, full settings, acceptance testing, and reproducible APK builds are not implemented or verified.
