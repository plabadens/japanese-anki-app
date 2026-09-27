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

The flake exposes `.#emulator` using nixpkgs `androidenv.emulateApp` with the API 35 `google_apis;x86_64` system image, plus `.#emulator-wrapper` for project-local AVD state and ADB operations. Build the tooling once:

```sh
nix build .#emulator .#emulator-wrapper
```

The wrapper keeps AVD, Android user configuration, temporary files, logs, and screenshots under this checkout's `.tmp-nix/`. Android Emulator does not register with an `adb` server using `localfilesystem:`; emulator commands therefore use the default ADB server. Every wrapper device operation targets `emulator-5554` explicitly, never the attached phone. In process-isolated environments the emulator is killed when its launching shell exits, so run boot, install, launch, captures, crash scan, and shutdown together using the single-session command:

```sh
nix run .#emulator-wrapper -- session /path/to/app-debug.apk .tmp-nix/screenshots/smoke
```

It creates a Pixel 7 profile, boots headlessly, waits up to 60 seconds for ADB and 240 seconds for `sys.boot_completed=1`, disables Bluetooth to avoid the recurring system crash dialog, installs with `adb install -r`, launches `dev.pierre.japaneseanki/.MainActivity`, captures `<prefix>-dark.png` and `<prefix>-light.png`, writes `<prefix>-logcat.txt`, scans for app crashes, and shuts down the emulator. Outside such a process-isolated environment, individual commands are available:

```sh
nix run .#emulator-wrapper -- start
nix run .#emulator-wrapper -- status
nix run .#emulator-wrapper -- install app/build/outputs/apk/debug/app-debug.apk
nix run .#emulator-wrapper -- launch
nix run .#emulator-wrapper -- screenshot .tmp-nix/screenshots/current.png
nix run .#emulator-wrapper -- share-text 'Japanese sentence to import'
nix run .#emulator-wrapper -- share-image /path/to/image.png
nix run .#emulator-wrapper -- logcat -d
nix run .#emulator-wrapper -- stop
```

Install AnkiDroid only in this emulator by downloading its APK from the official AnkiDroid release/Play distribution and running the wrapper's `install` command with that APK. Never uninstall apps or clear data on the attached physical phone.

Use the emulator for repeatable UI-state, light/dark theme, Android share-intent, AnkiDroid provider-flow, and screenshot-based checks. Keep camera behavior, real installed TTS voice quality, Audible/audio-quality, and Azure audition checks on the physical phone. This AVD is x86_64 and exercises the x86_64 native ABI, not the physical phone's arm64-v8a ABI. The pinned nixpkgs manifest provides Google APIs x86_64 images for API 35, 36, and 36.1, but not API 37; API 35 (Android 15) is the packaged default. Emulator tooling is dev/test-only and does not change the APK reproducibility boundary (`.#apk` / `.#debugApk`).

## Current implementation

The editor supports durable manual sentence/target/vocabulary/reading/definition/translation/notes/tags entry, readiness feedback, stale-value blocking, pitch entry/contour, Android `SEND` / `PROCESS_TEXT`, AnkiDroid setup, safe field mapping, duplicate blocking, and interrupted-send reconciliation. Settings cover default tags, optional Azure credentials (encrypted with Android Keystore), Android TTS engine/installed offline Japanese voice selection, and speech rates. Speech generation and cache controls are not active yet. JMdict and Kanjium are bundled in an offline SQLite index for lookup, sense selection, and pitch. UniDic CWJ 3.1.1 and MeCab 0.996 are pinned; token chips, inflection-aware lemma lookup, editable target/vocabulary reading suggestions, and best-effort sentence furigana with per-token corrections are wired. Native CMake builds succeeded for `arm64-v8a` and `x86_64`, and a host JNI smoke test verified token/feature output. The debug APK is built and smoke-tested on a Pixel 7/API 37, including first-launch UniDic extraction, tokenization, and offline dictionary lookup. AnkiDroid integration references v2.24.0 commit `ebcf8e0e34921628b9b8a496c66ffd4adbb3705f` (the release available on Google Play; its note/media provider, contract, browser manifest, and browser search files are byte-identical to the previously referenced v2.24.1 revisions); provider behavior and insertion with trailing empty fields remain release gates. Audio generation, image/media workflows, remaining settings, acceptance testing, and reproducible APK builds are not implemented or verified.
