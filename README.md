# Japanese Anki Card Creator

Mine a Japanese sentence and one vocabulary item into a note in AnkiDroid. The app creates notes; AnkiDroid owns cards, scheduling, and sync.

## Build and test

The Nix development shell supplies the pinned Android SDK, Gradle, JDKs, API 37 Robolectric runtime, and fonts. There is no Gradle wrapper. Start with `nix develop`, then run these commands from the repository root:

```sh
gradle --offline --configuration-cache --build-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  verifyFast

gradle --offline --configuration-cache --build-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:renderDesignStates
```

These are intentionally separate invocations. `verifyFast` runs the JVM and Compose assertions under Robolectric, excluding the two preview-catalogue classes. `:app:renderDesignStates` runs the catalogue through Roborazzi and checks that all 152 design-state images were rendered. Together they cover every runtime test in `app/src/test` without an emulator or device. The three
`app/src/androidTest` classes remain device acceptance tests and are deliberately not counted as
Robolectric coverage: `CameraOcrInstrumentedTest` needs CameraX/CameraManager and the installed
ML Kit model, `ReleaseAudioCodecSmokeTest` verifies real platform MediaPlayer codec support, and
`ui/SettingsDialogInputInstrumentedTest` checks the system IME and dialog-window insets.

Clean-checkout-equivalent evidence for the current inventory: `clean verifyFast` executed 377 tests
across 58 XML reports; the separate catalogue run executed 12 tests across 4 reports. That covers
all 62 `AndroidJUnit4` runner classes discovered under `app/src/test` (58 files with `@Test`, with
the two viewport catalogues expanded to four runner classes), for 389 test executions total. The
JUnit XMLs showed 0 failures, 0 errors, 0 skips, and no repeated class/test pairs between the two
lanes. Parity reconciliation against the earlier 59-file inventory: commit `5ac243e` removed four
tests along with production features that were deleted (`UniDicDownloadWorkerInjectionTest`,
`UniDicResourceCatalogTest`, `UniDicWorkManagerLifecycleTest`, and `VerifiedFirstRunResourcesTest`).
It added `BundledDictionaryTest` and `SetupScreenBehaviorTest`; `SettingsScreenBehaviorTest` was
updated for that setup redesign. `RobolectricSetupTest` is the added shared-configuration regression
test. These are source-level removals/replacements/additions, not test filtering. The current
58-file inventory and its 62 runner classes match the executed XML reports above.

For a debug APK, after running the two test commands above, render the catalogue and assemble with:

```sh
gradle --offline --configuration-cache --build-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:renderDesignStates assembleDebug
```

Both GitHub workflows run **lint** and **tests** as separate jobs. The tests job runs the unit/Compose suite and then renders the design catalogue; APK assembly and publication wait for both jobs to pass. Release jobs share one resolved source commit, including manual branch builds. Reports are uploaded even on failure. `scripts/ci-gradle.sh` supplies the shared Nix-shell Gradle options; the build scripts retain the full local gate by default, while CI uses `--build-only` after the prerequisite jobs succeed.

Run `python3 scripts/test-ci-scripts.py` to check entry-point sequencing and release-signing guards without Android. Python dictionary/asset checks in `nix flake check` validate generated data and packaging inputs; they are separate from the Android test suite.

## Robolectric conventions and shared fixtures

Every test class in `app/src/test` uses `@RunWith(AndroidJUnit4::class)`, including logic-only tests. Shared defaults are API 37 and `w390dp-h844dp-xxhdpi`, configured in `app/src/test/resources/robolectric.properties`; omit `@Config` unless a test needs a deliberate SDK or screen-size override. Keep `@GraphicsMode(GraphicsMode.Mode.NATIVE)` explicit where native rendering is exercised. There is no mandatory base test class.

Reuse `AudioFixtures` for structurally valid WAV/Ogg/MP3 container data in unit tests. Those generated files are not decoder-valid playback samples; real codec fixtures for device tests live separately under `app/src/androidTest/assets`. Prefer per-test temporary paths, unique names, and cleanup so tests do not share mutable state. Gradle/JVM resource configuration lives in `app/build.gradle.kts`; dependency versions live in `gradle/libs.versions.toml`.

## Design-state render updates

Design screenshots are generated review artifacts, not checked-in baselines. To refresh them, run `:app:renderDesignStates`; it writes to `build/design-states/` and fails unless exactly 152 PNGs are present. Review the output (CI uploads it as an artifact). Do not commit generated PNGs or treat a recorded image as the regression contract: the contract is in numeric/layout assertions in the tests. If a design change legitimately moves a bound or hit target, update the relevant assertion deliberately and rerun both test commands.

## Install a debug build

```sh
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Cut a release

```sh
git checkout main
# Bump versionName and versionCode in app/build.gradle.kts.
git tag "v$(sed -n 's/.*versionName = \"\(.*\)\".*/\1/p' app/build.gradle.kts | head -n 1)"
git push origin "$(git describe --tags --exact-match)"
```
