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
nix build .#checks.x86_64-linux.core
nix build .#apk
```

The flake pins Nixpkgs, JDK 17, Gradle, Android SDK platforms 35/37, build tools, CMake, and NDK. It now exposes a Nix core-test check, an unsigned release APK (`.#apk`, reproducibility target), and a signed debug APK (`.#debugApk`, for sideload testing). `nix develop` provides the same SDK/native tools and rewrites the ignored `local.properties` with the pinned SDK path. Nixpkgs' Gradle MITM cache is used to lock resolved Maven/plugin artifacts in `nix/gradle/deps.json`; initialize or refresh it with `./scripts/update-gradle-deps.sh` when Maven/Google and artifact redirect hosts are reachable. Until that lock is populated, the APK derivation is not yet verified as an offline build. The existing fixed-output inputs pin MeCab and UniDic; the flake packages UniDic into a deterministic 426 MiB runtime dictionary asset. First use expands UniDic to about 725 MB in app-private storage. A Gradle `testDebugUnitTest assembleDebug` run now succeeds: Kotlin and both NDK ABIs compile, unit tests pass, and the debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. The APK passes 16 KB `zipalign` and v2 signature verification. A Pixel 7 on API 37 confirmed UniDic extraction, native MeCab tokenization, and SQLite/JMdict lookup. Device logs showed a stale APK asset-name mismatch and 4 KB ELF segments; both are fixed, and the updated app no longer logs the page-size mismatch dialog. The Nix APK outputs, dependency-lock refresh, network-disabled build, and clean-build reproducibility comparison remain unverified. The app targets API 37 and supports API 35+. The Nix-pinned `gradle` command is used directly; there is no separately downloaded Gradle wrapper.

Build and install the Nix-packaged debug APK on a connected device:

```sh
nix build .#debugApk
adb install -r result/japanese-anki-debug.apk
```

The direct Gradle output is also available at `app/build/outputs/apk/debug/app-debug.apk` after `assembleDebug`.

## Android emulator (Nix, API 35 x86_64)

The Nix flake provides `.#emulator` using nixpkgs `androidenv.emulateApp` and a separately composed SDK with the API 35 `google_apis` x86_64 image. `.#emulator-wrapper` is the thin project-state/ADB control. First build the image and wrapper (large download on first use), then boot headlessly:

```sh
nix build .#emulator .#emulator-wrapper
nix run .#emulator-wrapper -- start
nix run .#emulator-wrapper -- status
nix run .#emulator-wrapper -- screenshot .tmp-nix/screenshots/boot.png
```

The wrapper keeps the AVD, Android user configuration, temporary files, emulator log, and PID under this checkout's `.tmp-nix/`; it uses a separate adb server socket at `/home/pierre/Development/japanese-anki-app/.tmp-nix/adb.sock` so it does not attach to the user's ordinary adb server. `start` waits at most 180 seconds for ADB, then up to 240 seconds for `sys.boot_completed=1` (with a clear timeout error). Stop it with `nix run .#emulator-wrapper -- stop`. Other commands:

```sh
nix run .#emulator-wrapper -- install app/build/outputs/apk/debug/app-debug.apk
nix run .#emulator-wrapper -- launch
nix run .#emulator-wrapper -- share-text 'Japanese sentence to import'
nix run .#emulator-wrapper -- share-image /path/to/image.png
nix run .#emulator-wrapper -- logcat -d
```

Install AnkiDroid only in this emulator by downloading its APK from the official AnkiDroid release/Play distribution and running `nix run .#emulator-wrapper -- install /path/to/AnkiDroid.apk`. The wrapper's ADB endpoint is intentionally isolated; these instructions do not operate on or erase any physical device.

Use this emulator for repeatable UI-state, light/dark theme, Android share-intent, AnkiDroid provider-flow, and screenshot-based checks. Keep camera behavior, real installed TTS voice quality, Audible/audio-quality, and Azure audition checks on the physical phone. This AVD is x86_64; it exercises the x86_64 native ABI, not the physical phone's arm64-v8a ABI. The emulator image is API 35 (Android 15); this pinned nixpkgs lane requests no API 36/37 x86_64 images. Emulator tooling is development/test infrastructure only: it does not change the APK derivation or its reproducibility boundary (`.#apk` / `.#debugApk`).

## Current implementation

The editor supports durable manual sentence/target/vocabulary/reading/definition/translation/notes/tags entry, readiness feedback, stale-value blocking, pitch entry/contour, Android `SEND` / `PROCESS_TEXT`, AnkiDroid setup, safe field mapping, duplicate blocking, and interrupted-send reconciliation. Settings cover default tags, optional Azure credentials (encrypted with Android Keystore), Android TTS engine/installed offline Japanese voice selection, and speech rates. Speech generation and cache controls are not active yet. JMdict and Kanjium are bundled in an offline SQLite index for lookup, sense selection, and pitch. UniDic CWJ 3.1.1 and MeCab 0.996 are pinned; token chips, inflection-aware lemma lookup, editable target/vocabulary reading suggestions, and best-effort sentence furigana with per-token corrections are wired. Native CMake builds succeeded for `arm64-v8a` and `x86_64`, and a host JNI smoke test verified token/feature output. The debug APK is built and smoke-tested on a Pixel 7/API 37, including first-launch UniDic extraction, tokenization, and offline dictionary lookup. AnkiDroid integration references v2.24.0 commit `ebcf8e0e34921628b9b8a496c66ffd4adbb3705f` (the release available on Google Play; its note/media provider, contract, browser manifest, and browser search files are byte-identical to the previously referenced v2.24.1 revisions); provider behavior and insertion with trailing empty fields remain release gates. Audio generation, image/media workflows, remaining settings, acceptance testing, and reproducible APK builds are not implemented or verified.
