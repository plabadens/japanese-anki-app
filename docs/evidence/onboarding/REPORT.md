Onboarding and UniDic lifecycle verification

Scope and build
- Tested source branch: `japanese-anki-app/t_cf3dee18-real-onboarding-wizard-durable-backgroun`, based on `d0711b24d655d6f35849de5e7b5917ac0a442f81`. Verification used debug/test builds only; no release build or full production-resource download was produced.
- Device: emulator-5554, API 37, x86_64 (sdk_gphone64_x86_64).
- Debug APK: app/build/outputs/apk/debug/app-x86_64-debug.apk.
- Production UniDic catalog was unchanged from the baseline: version 3.1.1, archive size 446,602,958 bytes, SHA-256 4963afe095a031b9a15e97325795cf28c772ff108138172e69c786cbc4c9b3fe.

Automated gates (all passed)
- `nix develop --command bash -lc 'gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"'` — BUILD SUCCESSFUL.
- `nix develop --command bash -lc 'gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"'` — BUILD SUCCESSFUL; 336 unit tests, 0 failures, 0 skipped (`app/build/reports/tests/testDebugUnitTest/index.html`).
- `nix develop --command bash -lc 'gradle :app:connectedDebugAndroidTest --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"'` — BUILD SUCCESSFUL on `emulator-5554`, API 37 x86_64; 45 tests, 0 failures, 0 errors, 0 skipped (`app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5554 - 17.xml`).
- `nix develop --command bash -lc './scripts/test-core.sh'` — OK (188 tests).
- `git diff --check` — clean before commit.

Controlled resource fixture
- `VerifiedFirstRunResourcesTest` ran as part of the 336-test Gradle suite (13 tests). Its in-memory `QueueTransport` fixture exercised download/verify/activate progress, bad hash, short and incorrect lengths, malformed ZIP/CRC rejection, cancellation after a partial read, and range resume from the stored byte offset. Partial/unverified bytes were not activated. This is a controlled installer fixture, not a WorkManager worker or full-sized local HTTP-server run.
- The worker seam is an internal provisioner/network-state constructor injection with the original production constructor unchanged. Fake provisioning and response bytes are confined to `src/androidTest`/`src/test`; the pinned production catalog is untouched.

Production archive metadata check
- HEAD request to the pinned v0.4 GitHub release asset followed its HTTPS redirect and returned HTTP 200 with Content-Length 446,602,958. `size_download=0`; no archive body was fetched in this metadata check and no full production/mobile transfer was started. This matches the unchanged catalog byte count.

On-device observations
- Dictionary step displayed 425.9 MiB, asked before metered-data transfer, and showed 0 B of 425.9 MiB before consent. Choosing Wait for Wi-Fi left the task queued and visibly waiting, with Pause/Cancel controls. Evidence: `01-dictionary-mobile-consent.png`, `02-waiting-wifi.png`, `17-mobile-consent-updated.png`, `18-wait-wifi-bytes.png`, `19-paused.png`, `20-resume-waiting.png`, `21-final-mobile-consent.png`.
- Pausing and resuming the queued task worked. Force-stopping and relaunching the updated app restored the same WorkManager ID (`cd25bca1-d15b-48ca-8ba7-3e1e4ded7222`), waiting state, 0 B count, and saved deferred-network choice; it did not create a second ID or repeat the prompt on the same network. Evidence: `22-final-relaunch.png`, `22-task-state-deferred.xml`, `22-task-state-after-relaunch.xml`.
- The filtered log and notification-manager snapshot are retained as `22-logcat-filtered.txt` and `22-notification-state.txt`. No active UniDic foreground notification was present while the unique work was queued behind the unmetered-network constraint; no worker transfer was running.
- Earlier UI captures exercise dictionary consent/waiting, camera and typing choice, audio setup/validation, Use app into Capture, Settings re-entry, Type capture/draft, blocked Choose word routing to Downloads, and visible cancellation. Evidence: numbered files `01`–`14` in this directory.

Controlled WorkManager integration (emulator-5554)
- Command: `adb shell am instrument -w -e class dev.pierre.japaneseanki.UniDicWorkManagerLifecycleTest dev.pierre.japaneseanki.test/androidx.test.runner.AndroidJUnitRunner` — `OK (1 test)`, 12.796 s. The fixture is an injected in-process provisioner; no 446 MB archive body or production endpoint was used.
- Logcat records one WorkManager ID (`22746372-7473-439e-aad1-b6f4e91727f8`), `RUNNING/downloading`, 1,048,576 of 446,602,958 bytes; the same worker remained RUNNING after `Use app` navigated to Capture and after HOME/backgrounding. A repeated `start()` returned the same ID and the unique-work query found one RUNNING request. Releasing the fixture produced `SUCCEEDED/ready`.
- The metered-data consent dialog appeared on the emulator and `Use mobile data` was explicitly selected before the controlled worker was allowed to start. Earlier full-device captures record `Wait for Wi-Fi`; the direct worker instrumentation test rejects an unapproved metered handle before invoking its provisioner.
- Full-device screenshot: `active-transfer-capture.png` (780×1688). Runner output and filtered logcat: `workmanager-lifecycle-instrumentation.txt`, `workmanager-lifecycle-logcat.txt`.
- Controlled worker failure/retry and integrity rejection are covered by `UniDicDownloadWorkerInjectionTest`; installer hash/length/ZIP-CRC/activation and resumable-offset cases are covered by `VerifiedFirstRunResourcesTest`. These prove controlled code paths, not the live GitHub transport or a full archive installation.

Operator-named lifecycle cases
1. Transfer continues across screen navigation — PASS (controlled Worker). WorkInfo stayed RUNNING/downloading at 1 MiB while `Use app` moved to Capture; screenshot/logcat above.
2. Transfer survives app backgrounding — PASS (controlled Worker). After HOME, the same WorkInfo ID was still RUNNING; logcat records it.
3. Transfer survives process death and resumes from saved offset — NOT RUN. The host-orchestrated seed did not reach its controlled transfer boundary; no process kill/relaunch was performed. See `process-death-seed-instrumentation.txt`.
4. Mobile-data consent is respected — PASS (controlled). The real metered-network UI prompt was shown and explicitly accepted before the test worker was allowed to start; the direct worker test confirms an unapproved metered network stops before the provisioner and transfers zero fixture bytes. Wait-for-Wi-Fi remains evidenced by the earlier full-device captures.
5. Failure retries cleanly without duplicating work — PASS at controlled component level. The worker test fails a transfer then succeeds on a later attempt; the active WorkManager test repeats `start()` and observes the same ID with exactly one RUNNING unique request. These are separate controlled assertions, not a real production download retry.
6. Size/hash are verified before readiness — PASS at controlled component level. Installer tests reject wrong length/hash and never activate invalid bytes; the worker integrity-failure test returns `dictionary_invalid`/`failed` and does not mark ready. The successful lifecycle worker uses a fake provisioner and is not proof of actual UniDic verification.

Not run / limits
- No 425.9 MiB production transfer was attempted. Consequently, a successful full WorkManager download, actual on-device verification/extraction/ready phases, notification actions during a real transfer, Wi-Fi loss/recovery, and a second mobile-consent prompt after a real network transition remain NOT RUN.
- Active-transfer process death and resume-from-offset were NOT verified. An attempted host-orchestrated process-death fixture did not reach its controlled byte boundary and no process kill was performed; the failed seed runner output is retained as `process-death-seed-instrumentation.txt`. The earlier force-stop/relaunch evidence is for a task queued on Wi-Fi, not an active transfer.
- The controlled lifecycle fixture reports verification/install/ready stages but does not perform UniDic verification or extraction. Actual pinned-resource size/hash/archive checks ran only in the installer unit fixture; no live installed dictionary is claimed.
- AnkiDroid and final readiness screens were not exercised. Audio progression was attempted with invalid/unconfigured credentials; no live credentials or paid service calls were used.
- No device-side active real-download notification or full archive installation is claimed.

Evidence files
- Screenshots/UI dumps: `01`–`14` (earlier UI flow), `16-updated-build-relaunch.png`, `17-mobile-consent-updated.*`, `18-wait-wifi-bytes.*`, `19-paused.*`, `20-resume-consent.*`, `21-final-mobile-consent.png`, `22-final-relaunch.*`, `active-transfer-capture.png`.
- Lifecycle/task records: `18-task-state-before-kill.xml`, `21-task-state.xml`, `22-task-state-deferred.xml`, `22-task-state-after-relaunch.xml`, `22-logcat-filtered.txt`, `22-notification-state.txt`, `workmanager-lifecycle-instrumentation.txt`, `workmanager-lifecycle-logcat.txt`, `process-death-seed-instrumentation.txt`.
