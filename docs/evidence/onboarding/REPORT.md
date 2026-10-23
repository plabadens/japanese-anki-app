Onboarding and UniDic lifecycle verification

Scope and build
- Tested source branch: japanese-anki-app/t_cf3dee18-real-onboarding-wizard-durable-backgroun, based on d0711b24d655d6f35849de5e7b5917ac0a442f81. The implementation was a dirty worktree when the checks ran; no published or release build was produced.
- Device: emulator-5554, API 37, x86_64 (sdk_gphone64_x86_64).
- Debug APK: app/build/outputs/apk/debug/app-x86_64-debug.apk.
- Production UniDic catalog was unchanged from the baseline: version 3.1.1, archive size 446,602,958 bytes, SHA-256 4963afe095a031b9a15e97325795cf28c772ff108138172e69c786cbc4c9b3fe.

Automated gates (all passed)
- `nix develop --command bash -lc 'gradle :app:assembleDebug --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"'` — BUILD SUCCESSFUL.
- `nix develop --command bash -lc 'gradle :app:testDebugUnitTest --rerun-tasks --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"'` — BUILD SUCCESSFUL; report: 334 tests, 0 failures, 0 skipped.
- `nix develop --command bash -lc 'gradle :app:lintDebug --no-daemon --max-workers=1 -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2"'` — BUILD SUCCESSFUL.
- `nix develop --command bash -lc './scripts/test-core.sh'` — OK (188 tests).
- `git diff --check` — clean.

Controlled resource fixture
- `VerifiedFirstRunResourcesTest` ran as part of the 334-test Gradle suite (11 tests). Its in-memory `QueueTransport` fixture exercised download/verify/activate progress, bad hash, short and incorrect lengths, malformed ZIP/CRC rejection, and cancellation after a partial read; partial/unverified bytes were not activated. This is a controlled installer fixture, not a WorkManager worker or full-sized local HTTP-server run.

Production archive metadata check
- HEAD request to the pinned v0.4 GitHub release asset followed its HTTPS redirect and returned HTTP 200 with Content-Length 446,602,958. `size_download=0`; no archive body was fetched. This matches the unchanged catalog byte count. No mobile transfer was started.

On-device observations
- Dictionary step displayed 425.9 MiB, asked before metered-data transfer, and showed 0 B of 425.9 MiB before consent. Choosing Wait for Wi-Fi left the task queued and visibly waiting, with Pause/Cancel controls. Evidence: `01-dictionary-mobile-consent.png`, `02-waiting-wifi.png`, `17-mobile-consent-updated.png`, `18-wait-wifi-bytes.png`, `19-paused.png`, `20-resume-waiting.png`, `21-final-mobile-consent.png`.
- Pausing and resuming the queued task worked. Force-stopping and relaunching the updated app restored the same WorkManager ID (`cd25bca1-d15b-48ca-8ba7-3e1e4ded7222`), waiting state, 0 B count, and saved deferred-network choice; it did not create a second ID or repeat the prompt on the same network. Evidence: `22-final-relaunch.png`, `22-task-state-deferred.xml`, `22-task-state-after-relaunch.xml`.
- The filtered log and notification-manager snapshot are retained as `22-logcat-filtered.txt` and `22-notification-state.txt`. No active UniDic foreground notification was present while the unique work was queued behind the unmetered-network constraint; no worker transfer was running.
- Earlier UI captures exercise dictionary consent/waiting, camera and typing choice, audio setup/validation, Use app into Capture, Settings re-entry, Type capture/draft, blocked Choose word routing to Downloads, and visible cancellation. Evidence: numbered files `01`–`14` in this directory.

Not run / limits
- No 425.9 MiB production transfer was attempted. Consequently, a successful full WorkManager download, actual on-device verification/extraction/ready phases, active-transfer process death, notification actions during transfer, Wi-Fi loss/recovery, and a second mobile-consent prompt after a real network transition remain NOT RUN. The installer fixture covers its smaller controlled phases and failure paths; it does not establish those worker/device behaviours.
- AnkiDroid and final readiness screens were not exercised. Audio progression was attempted with invalid/unconfigured credentials; no live credentials or paid service calls were used.
- No device-side active download notification or full archive installation is claimed.

Evidence files
- Screenshots/UI dumps: `01`–`14` (earlier UI flow), `16-updated-build-relaunch.png`, `17-mobile-consent-updated.*`, `18-wait-wifi-bytes.*`, `19-paused.*`, `20-resume-consent.*`, `21-final-mobile-consent.png`, `22-final-relaunch.*`.
- Lifecycle/task records: `18-task-state-before-kill.xml`, `21-task-state.xml`, `22-task-state-deferred.xml`, `22-task-state-after-relaunch.xml`, `22-logcat-filtered.txt`, `22-notification-state.txt`.
