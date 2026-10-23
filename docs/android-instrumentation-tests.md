# Device-only tests

**Status: REFERENCE.** The regular Compose/UI gate is the offline JVM task `verifyFast`; it does not need an emulator. `app/src/androidTest` is reserved for four device-only categories. Keep local audio lifecycle, SQLite and WebP contracts in the JVM lane; the real WAV/OGG/MP3 decoder assertion is the narrow device-only exception because Robolectric cannot prove platform codec support. See [testing-without-emulator.md](testing-without-emulator.md) for the adopted JVM lane and its measured results.

## Allowed device boundary

1. **Camera / ML Kit OCR** — real CameraX sensor/preview/capture behavior. Current automated coverage is `app/src/androidTest/java/dev/pierre/japaneseanki/CameraOcrInstrumentedTest.kt`: its local Japanese OCR fixture and live preview/shutter tests.
2. **System insets, edge-to-edge and gesture pill** — actual system UI/window behavior that Robolectric cannot model. No separate test for this category is currently in `androidTest`.
3. **AnkiDroid round-trip** — real intent/provider/URI interaction with the third-party app. Use a disposable emulator and collection; never use a personal installation or collection. No such round-trip test is currently in `androidTest`.
4. **Signed-APK release smoke** — install the actual signed release APK, launch it and exercise one happy path before publication. `ReleaseAudioCodecSmokeTest` preserves the real Android decoder assertion for WAV/OGG/MP3; a debug-emulator run is supplemental and does not prove the signed artifact. Keep signing material out of tests and logs.

The camera/OCR checks and the supplemental Android decoder check run under `connectedDebugAndroidTest`. System insets, AnkiDroid round-trip, and installation/exercise of the signed release artifact remain separate device/release obligations. If a proposed test is outside this list, move it to the JVM lane or remove it.

## Run the debug-device checks

Use a disposable API-37 x86_64 AVD, not an attached personal phone. `connectedDebugAndroidTest` targets every device visible to its ADB server, so first confirm there is exactly one intended target:

```sh
adb devices -l
```

Build the debug and instrumentation APKs from the repository root in `nix develop`:

```sh
gradle --offline --configuration-cache --build-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

With the intended emulator as the only attached device, run:

```sh
export ANDROID_SERIAL=emulator-5554
gradle --offline --configuration-cache --build-cache --max-workers=1 --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:connectedDebugAndroidTest
```

Use the actual serial reported by `adb devices -l`; do not stop a device you did not start, and do not run competing Gradle builds or AVDs on a shared lane. The camera test grants permission to the target package, writes its capture under app-private cache, and removes that test output. The OCR and audio fixtures are local; the decoder test exercises Android `MediaPlayer` with WAV/OGG/MP3 and does not call paid services. A debug AVD result is not physical-hardware or signed-release acceptance.

Connected-test XML is written under `app/build/outputs/androidTest-results/connected/debug/`. JVM unit/Robolectric/Compose XML is under `app/build/test-results/testDebugUnitTest/` and is the primary report for `verifyFast`.

## Dependency lock

If instrumentation dependencies change, refresh the Nix Gradle dependency lock with:

```sh
./scripts/update-gradle-deps.sh
```

A warm Gradle cache is not evidence that the lock contains the artifacts. The camera test dependencies are pinned in `gradle/libs.versions.toml` and covered by `nix/gradle/deps.json`.
