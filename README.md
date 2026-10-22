# Japanese Anki Card Creator

See `docs/toolchain-notes.md` for deferred toolchain work: the AGP 9.x upgrade (required eventually for the pinned `compileSdk = 37`), the release-APK lint lock gap, and emulator caveats.

Native Android app for creating Japanese sentence-mining notes in AnkiDroid. Mission: **get everything right the first time, without unnecessary choices**. See [the approved screen-and-copy contract](docs/sentence-mining-flow.md) for every view/string and [SPEC.md](SPEC.md) for technical requirements and acceptance R01–R39. Feature work lands on [dev](docs/branching.md), not directly on main.

**Design approved; implementation compliance pending:** the 22 October contract selects progress A: title-only navigation and one grouped Review busy state in the stable action area. One selectable sentence, no duplicate hero, no generated-field repair workbench, separate optional notes/tags and a backgroundable setup wizard. Native Material 3 Expressive motion/haptics support the task without gamification. Technical requirements retain API 37, Google Chirp 3 HD with reading hints, Google Cloud Translation Basic v2 NMT behind one shared protected key, complete notes, annotated AJT pitch and 1080 px WebP. Approval is not native acceptance. Existing mockups, old matrices and dated status reports do not control UI or establish compliance. Nix development builds remain required.

## Build environment (NixOS x86_64)

The flake declares the shipped target: Android SDK platform **37.0**, build-tools
36.0.0 (the version `app/build.gradle.kts` pins), NDK 27.2.12479018, CMake 4.1.2,
emulator 37.1.11 and the API-37.0 `google_apis` x86_64 system image, all in one
signed-for Nix SDK root. Nothing is expected from a globally installed Android SDK.

```sh
nix develop   # JDK 17, Gradle 8.14.4, SDK 37.0, build-tools, NDK, CMake, emulator, adb
./scripts/test-core.sh
gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest assembleDebug
```

Dependency fetch: the first `gradle` invocation downloads the AGP/Kotlin/AndroidX
artifacts from `google()` / `mavenCentral()` into the Gradle user cache;
it needs network access once and is incremental afterwards. `nix develop` rewrites
the ignored `local.properties` with the pinned SDK path.

`nix build .#apk` builds an unsigned release APK and `nix build .#debugApk` a debug
APK inside the Nix sandbox; both also run `testDebugUnitTest` (`doCheck`). The
first run resolves Maven artifacts through the Nix Gradle MITM cache
(`nix/gradle/deps.json`, refreshed with `./scripts/update-gradle-deps.sh`), so it
needs network access once. Outputs are `/nix/store` paths, not byte-identical
across rebuilds, and no reproducibility claim is made. The primary supported
workflow remains Gradle inside `nix develop`, which writes separate
`app-arm64-v8a-debug.apk` and `app-x86_64-debug.apk` files under
`app/build/outputs/apk/debug/`. Use x86_64 for the emulator and arm64-v8a for
physical devices; there is no universal APK. To install on a physical device, set
`ANDROID_SERIAL` to that phone's serial and use the explicit arm64 path:

```sh
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

The sandbox build has no keystore configured, so `.#debugApk` signs with a throwaway
debug key and cannot replace an existing install; for an update-compatible APK use
the Gradle path with `JAPANESE_ANKI_DEBUG_KEYSTORE` set (see `app/build.gradle.kts`).

The fixed-output inputs pin MeCab and UniDic. The APK does not contain the 426 MiB
runtime dictionary ZIP. The approved flow provisions the pinned UniDic v0.4
asset through backgroundable onboarding/Downloads, verifies its SHA-256/archive,
then extracts about 725 MB privately. Full wizard/background compliance is pending,
not established by the installer alone. `nix build .#unidicRuntime` produces the asset;
it must be uploaded with the APK before the release is usable. The
Japanese OCR model, NHK index, dictionary and pitch data remain bundled; the app
targets API 37 and requires API 37.

## Android emulator (Nix, API 37 x86_64)

`.#emulator-wrapper` provisions the AVD and runs ADB operations against the
flake-declared SDK; the API-37.0 `google_apis;x86_64` system image and emulator
37.1.11 come from the same `nix develop` closure, so `nix build`/`nix run` needs no
extra setup:

```sh
nix run .#emulator-wrapper -- start
```

The wrapper keeps AVD, Android user configuration, temporary files, logs, and screenshots under this checkout's `.tmp-nix/`. Android Emulator does not register with an `adb` server using `localfilesystem:`; the shared `scripts/emulator-adb` helper therefore uses the pinned SDK's ADB executable, unsets `ADB_SERVER_SOCKET`, and supplies one explicit emulator serial for every device-directed command. The default is `emulator-5554`; `EMULATOR_PORT=<n>` selects another port, or `ANDROID_SERIAL=emulator-<n>` may select an emulator directly. If both selectors are set they must agree. Physical-device serials are rejected; this tooling never targets an attached phone. `scripts/ui-journey.py` uses the same helper. Its hierarchy dumps use unique remote names and unique local files under `TMPDIR` (the wrapper configures `.tmp-nix/tmp`), so concurrent runs do not overwrite a shared dump. The AVD is `japanese-anki-pixel7-api37` (Pixel 7 profile, 1080x2400, API 37.0). One AVD instance at a time: a second emulator on the same AVD corrupts its user data. In process-isolated environments the emulator is killed when its launching shell exits, so run boot, install, launch, captures, crash scan, and shutdown together using the single-session command:

```sh
nix run .#emulator-wrapper -- session app/build/outputs/apk/debug/app-x86_64-debug.apk .tmp-nix/screenshots/smoke
```

It creates a Pixel 7 profile, boots headlessly, waits up to 60 seconds for ADB and 240 seconds for `sys.boot_completed=1`, disables Bluetooth to avoid the recurring system crash dialog, installs with `adb install -r`, launches `dev.pierre.japaneseanki/.MainActivity`, captures `<prefix>-dark.png` and `<prefix>-light.png`, writes `<prefix>-logcat.txt`, scans for app crashes, and shuts down the emulator. Outside such a process-isolated environment, individual commands are available:

```sh
nix run .#emulator-wrapper -- start
nix run .#emulator-wrapper -- status
nix run .#emulator-wrapper -- install app/build/outputs/apk/debug/app-x86_64-debug.apk
nix run .#emulator-wrapper -- reinstall app/build/outputs/apk/debug/app-x86_64-debug.apk  # install -r + grant camera + relaunch
nix run .#emulator-wrapper -- launch
nix run .#emulator-wrapper -- restart          # force-stop + launch, no reinstall
nix run .#emulator-wrapper -- grant [permission]  # defaults to android.permission.CAMERA
nix run .#emulator-wrapper -- tap <x> <y>
nix run .#emulator-wrapper -- theme light|dark
nix run .#emulator-wrapper -- screenshot .tmp-nix/screenshots/current.png
nix run .#emulator-wrapper -- dump .tmp-nix/ui.xml   # uiautomator hierarchy dump, pulled locally
nix run .#emulator-wrapper -- share-text 'Japanese sentence to import'
nix run .#emulator-wrapper -- share-image /path/to/image.png
nix run .#emulator-wrapper -- logcat -d
nix run .#emulator-wrapper -- stop
```

For iterating on UI code, `scripts/rebuild.sh [test|build-only]` builds the debug APK (via `nix develop`, reusing the incremental Gradle cache) and reinstalls+relaunches it in one command. `scripts/pixel.py <png> at|row|col ...` inspects screenshot pixels precisely (exact edge/letterbox bounds) instead of eyeballing a downscaled render.

Install AnkiDroid only in this emulator by downloading its APK from the official AnkiDroid release/Play distribution and running the wrapper's `install` command with that APK. Keep all test data disposable and isolated to the emulator; do not touch any attached phone.

For the redesign, provision an API-37 x86_64 emulator and follow the R-series checklist in SPEC §14. Inject local image fixtures and use the Google Cloud Translation Basic NMT path for actual quality checks, with controlled responses for failure tests. No offline TTS engine or ARM64 emulator matrix is required. Verify ARM64 packaging separately; physical-device validation remains optional. Record emulator-only limitations honestly.

## Implementation and evidence status

The published v0.4 payload and earlier test reports predate the approved screen/copy contract. See `docs/evidence/` for dated build/test evidence and `docs/first-run-resource-delivery.md` for resource availability and remaining setup gaps. Neither a published APK nor green component tests establishes the new flow or fixes the user-reported import failure. The compliance task must inventory every surface/string, exercise connected native journeys and verify imports with both a separate dummy provider APK and real AnkiDroid in disposable emulator collections. Label unrun gates explicitly; do not restore old manual-field editing, obsolete providers or prototype progress controls from historical status text.
