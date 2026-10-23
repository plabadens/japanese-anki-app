# Compose/UI tests without an emulator

**Status: adopted and implemented.** The emulator is not the routine way to check a Compose screen. Use the offline JVM lane for semantics, layout, behavior and visual goldens; reserve a device for the four boundaries in §5.

## 1. The lane

The root `verifyFast` task depends on `:app:verifyRoborazziDebug`. That task runs `:app:testDebugUnitTest` and Roborazzi verification; the task graph was checked with Gradle's dry run. It is the single gate for local JVM, Robolectric, Compose and golden tests. It does not build or start an emulator.

```sh
nix develop --command bash -lc 'gradle --offline --configuration-cache --build-cache -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" verifyFast'
```

Primary machine-readable output is `app/build/test-results/testDebugUnitTest/*.xml`; Gradle's summary is `app/build/reports/tests/testDebugUnitTest/index.html`. Roborazzi failures are reported by its verification task. Record a changed golden deliberately with `:app:recordRoborazziDebug`; recording is not part of the normal verification command.

Robolectric plus `ui-test-junit4` drives the Compose semantics tree and checks numeric behavior: bounds, selection, enabled state, callbacks and accessibility. Roborazzi adds image comparison in the same JVM lane. The project uses native graphics where pixel rendering is under test. Compose tests pin SDK 37 and `w390dp-h844dp-xxhdpi`; app motion is disabled for motion-sensitive layout tests, and the system-reduced-motion test sets Robolectric's animator scale to zero. Compose UI tests use the test scheduler, not elapsed wall-clock time.

The Gradle test task sets `robolectric.offline=true`, points Robolectric at the pre-seeded runtime jar directory, and writes temporary files under the build tree. UI fixtures use fake providers/transfers and local assets; there are no network calls, credentials, or paid services in `verifyFast`. Nix pins the CJK font through `FONTCONFIG_FILE` (`pkgs.noto-fonts-cjk-sans`); no product font resource or user font is read.

## 2. What moved to the JVM

Portable tests now live in `app/src/test`. The move keeps their existing test method names and assertions where possible; the runner/configuration and the file paths for evidence changed, not production behavior.

- `InputScreenLayoutTest` and `SentenceSelectionLayoutTest`: Compose geometry, selection, responsive states and feedback.
- `CardSurfaceBehaviorTest`: state transitions, meaning-sheet selection, audio advisory, sentence geometry and accessibility.
- `ImageExportInstrumentedTest`: Robolectric/native-graphics export, decode, descriptor accounting and failure-path checks. It is not a device codec guarantee.
- `DraftAudioPlaybackTest`: materialized WAV/OGG/MP3 files pass format validation and the local `MediaPlayer` lifecycle under Robolectric's configured shadow. The real decoder assertion is preserved in `app/src/androidTest/java/dev/pierre/japaneseanki/ReleaseAudioCodecSmokeTest.kt`; a Robolectric shadow cannot establish codec support.
- `AndroidPlatformSmokeTest`: local SQLite/WebP calls and the modeled `MediaPlayer` completion callback. Robolectric shadows/native graphics are not substitutes for device acceptance.
- UniDic/WorkManager coverage: `work-testing` drives a test scheduler against a real on-disk WorkManager Room database. The lifecycle test closes the engine/database, reopens that same database under a fresh engine, verifies the enqueued request survived, and checks the transfer offset in the durable task store/partial-transfer file before resume, verification and install. WorkInfo progress is asserted while the worker is running; WorkManager may clear it on retry. Robolectric cannot kill the OS process, so this is an explicit process-restart simulation, not a process-kill claim; the 426 MiB real-host transfer remains NOT RUN.
- `AjtPitchRepository`, `OfflineDictionary`, asset-policy and other portable tests stay in the same JVM suite. The JVM audio fixtures are under `app/src/test/resources/assets`, with matching copies under `app/src/androidTest/assets` for the real decoder check.

`app/src/androidTest` contains `CameraOcrInstrumentedTest.kt` (the local Japanese OCR fixture and real CameraX preview/capture) plus `ReleaseAudioCodecSmokeTest.kt` (real Android decoder acceptance for WAV/OGG/MP3). The latter belongs to the release/device boundary; its emulator debug run is supplemental and is not evidence that a signed release APK was installed.

## 3. Measurements: baseline and post-migration

The earlier spike measured different suites, so its timing was not evidence that one complete lane was faster than the other:

| Spike baseline on `a8fc7b1` | Cold run | Warm run | What it measured |
|---|---:|---:|---|
| JVM `testDebugUnitTest` — 338 tests | 96 s, 338 passed | 19 s, task `UP-TO-DATE` | Warm time was Gradle overhead, not a second test execution. |
| Device `connectedDebugAndroidTest` — API 37 `emulator-5554` | 213 s, 45 passed | 92 s, 45 passed | Warm run executed all 45 tests. |

Post-migration verification on this tree:

| Current lane | Tests | Result | Measured time |
|---|---:|---|---:|
| `verifyFast` / `testDebugUnitTest` | 381 | 0 failures, 0 errors, 0 skipped | 68.45 s suite; 49 s normal cached run; 2m24s clean-HEAD run with all tasks rerun and build cache disabled |
| `connectedDebugAndroidTest` / camera + decoder smoke | 3 | 0 failures, 0 errors, 0 skipped | 9.770 s instrumentation; 22 s for this incremental Gradle run |

The current device run used the existing API-37 x86_64 `emulator-5554`; its XML is `app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 17.xml`. It reports two camera/OCR tests and one real Android decoder test, all passing. The JVM report lists 381 tests with zero failures/skips. These counts are intentionally different: 381 portable checks now run without a device, while camera/ML Kit and Android decoder behavior remain in the device lane. The 9.770-second instrumentation time is from an incremental build and is not directly comparable to the earlier cold run. The spike's 338/45 comparison is historical, not a post-migration performance claim.

The post-migration `verifyFast` execution completed successfully without an emulator. The clean-HEAD run used `--rerun-tasks --no-build-cache` to force execution (30 tasks executed); the normal configured command with the Gradle build cache also passed. The device run completed the two camera/OCR tests and decoder smoke on the attached emulator. System-inset, AnkiDroid, and actual signed-release installation checks were not run. The emulator decoder result is not a physical hardware-codec guarantee.

## 4. Why these tools

- **Robolectric + `ui-test-junit4`**: interaction, semantics, state and numeric layout assertions using Android framework APIs on the JVM.
- **Roborazzi**: multi-frame screenshot comparison alongside those assertions.
- **Paparazzi**: not selected; it renders a composable frame but cannot drive the interactions this contract needs.
- **Compose Preview Screenshot Testing**: not selected; the version evaluated was alpha and tied to AGP release candidates. Revisit when it is stable and offers a concrete advantage over the current lane.

## 5. The only device-only categories

Keep a thin device lane, and do not add another emulator test without showing that it belongs to one of these categories:

1. **Camera / ML Kit OCR** — live sensor and CameraX pipeline. The local OCR fixture may move JVM-side if its boundary is isolated, but the current camera/OCR instrumented test stays on device.
2. **System insets, edge-to-edge and gesture pill** — real system UI/window behavior.
3. **AnkiDroid round-trip** — intent/provider/URI behavior against the real third-party app and a disposable collection.
4. **Signed-APK release smoke** — install the actual signed release artifact and exercise one happy path before publication. `ReleaseAudioCodecSmokeTest` preserves the real decoder check; a debug-emulator run is only supplemental and does not satisfy signed-artifact acceptance.

Everything else that can run under Robolectric belongs in `app/src/test`; tests that have no useful assertion should be removed, not kept merely to justify an emulator boot. `docs/android-instrumentation-tests.md` is the device-lane runbook.

## 6. Separate core checks and release scope

`./scripts/test-core.sh` remains a separate, fast check for pure core sources and Nix packaging. `verifyFast` does not replace it. `nix build .#debugApk` now runs `verifyFast` as its Gradle check task, and `scripts/ci-build-apk.sh` runs both the core check and `verifyFast` before packaging.

This lane does not prove live Google provider quality, the behavior of a real AnkiDroid installation, signed-release installation, or physical hardware codec output. Report those as device/release checks, never as consequences of a green JVM suite.
