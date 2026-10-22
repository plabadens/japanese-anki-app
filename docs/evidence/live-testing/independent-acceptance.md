# Independent acceptance — integrated live-test remediation

Date: 2026-10-22 (JST)
Branch: `japanese-anki-app/t_1eb7d549-independent-acceptance-of-integrated-liv`
Accepted integration under test: `82e2232` (`fix(integration): wire live-test remediation`)
Contract: `2c473ed7426850f86ef17bf9c93f69cb21b873ef` is an ancestor of both the integration and this worktree. Integration was fast-forwarded into this acceptance branch; main was not modified. No push, publication, paid service, or credentials used.

## Executed here

- PASS — Independently reran `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` on the merged integration in this acceptance run. Gradle returned `BUILD SUCCESSFUL` (58 actionable tasks; 5 executed, 53 up-to-date). Test XML was programmatically aggregated: 39 suites, 314 tests, 0 failures, 0 errors, 0 skipped.
- PASS — Re-ran `bash scripts/test-core.sh` inside `nix develop --offline`; JUnit `OK (192 tests)`. Jansi emitted a `/tmp` noexec native-library warning, but the command exited 0.
- PASS — Re-ran the AJT builder and bundled-asset regression scripts; both reported passed.
- PASS — Re-ran `git diff --check 2c473ed..HEAD` (clean).
- PASS — Independently reran `:app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest` on the reserved API-37 x86_64 AVD (`emulator-5554`). XML aggregation from `app/build/outputs/androidTest-results/connected/TEST-emulator-5554 - 17-_app-.xml`: 27 tests, 0 failures, 0 errors, 0 skipped (33.669s). This suite is `AjtPitchRepositoryAndroidTest`; it is not evidence of camera/OCR/picker/provider UI interaction.
- PASS — Independently reran `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; Gradle `BUILD SUCCESSFUL` (58 actionable tasks; 5 executed, 53 up-to-date).
- PASS — Independently reran core and AJT checks: JUnit 192 tests; `test-build-ajt-pitch.py` and `test-ajt-pitch-asset.py` passed.
- PASS — `bash scripts/test-core.sh`: JUnit `OK (192 tests)`. Java emitted a Jansi native-library/noexec warning from `/tmp`; it did not affect test completion.
- PASS — `python3 scripts/test-build-ajt-pitch.py`: builder regression passed, including required 受付 devoiced-ツ reference markup, duplicate/source-order handling, kana/alternate spelling and 出来る forms, all three shard metadata/hash recording, and refusal of incomplete shard sets.
- PASS — `python3 scripts/test-ajt-pitch-asset.py`: bundled 676,632-record index regression passed; confirms exact 受付 `ウケツケ` devoiced `ツ` output, plain variants, all required 出きる/出来る/出來る behavior, and source hashes for all three AJT shards.
- PASS — `VocabularyPartOfSpeechTest`: XML confirms 5 tests, 0 failures/errors/skips. These cover selected-sense JMdict POS mapping; compatibility for absent optional model field is covered by model/test serialization in the suite.
- PASS — Git ancestry checks for contract and integration; `git diff --check 2c473ed..HEAD` clean.

## Outcome matrix

- PASS — build and JVM/core gates above, including typed import/media, selected-sense POS, settings, generation coordinator, Review presentation, pitch rendering, and recovery tests.
- PASS — Independent API-37 native instrumentation rerun in this acceptance: 27/27 tests, 0 failures/skips on `emulator-5554`; see exact XML suite above. Parent camera capture result remains handoff evidence only; this rerun did not reproduce camera capture.
- NOT RUN — Independent native camera/PreviewView/shutter bounds; OCR/dictionary choice and dismissal/reopen flows; photo picker selection; AnkiDroid provider imports/chooser; and actual media gateway post-write uncertainty on-device. The AVD does not have `com.ichi2.anki` installed. The 27-test instrumentation class is pitch repository-only, not these paths.
- NOT RUN — live Google OCR/TTS/translation quality or paid request behavior. No credentials or paid calls were used. Restore/revisit no-repeat behavior has local coordinator tests, not a live provider observation.
- NOT RUN — native light/dark/200% text runtime visual comparison and resource download interruption/storage behavior in an Android flow. The integrated `82e2232` tree does not contain resource foundation commit `c5f411e` (verified non-ancestor); onboarding/resource distribution is therefore outside this integration's delivered scope, not accepted as present. The separate resource card describes a verified downloader foundation, not complete production onboarding or released assets.
- NOT RUN — first-run distribution asset/license clearance. AJT metadata correctly retains unresolved third-party data rights; no licensing or publication claim is made.

No acceptance claim is based on a fixture as live-provider quality. Native gaps remain explicit; this is not an unconditional release approval. Target A is the approved production direction; the B alternative remains prototype-only. No implementation changes were made in this acceptance task.

## Local merge-train revalidation

The original native-test narrative above mischaracterizes the XML scope. Use the raw test cases, not the earlier summary. The final merged source tree at `4f7aae8` was independently revalidated by the local merge-train worker; this section supersedes the earlier native-test count/scope claims for the merged tree.

- PASS — `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin`; Gradle `BUILD SUCCESSFUL`. XML aggregation: 41 JVM suites/files, 318 tests, 0 failures/errors/skips. Lint completed with 46 warnings and 19 hints; no lint errors.
- PASS — `bash scripts/test-core.sh`: JUnit `OK (184 tests)`, exit 0. Jansi printed the known `/tmp` noexec native-library warning; it did not affect the test result.
- PASS — AJT builder and bundled-asset regressions: `scripts/test-build-ajt-pitch.py` and `scripts/test-ajt-pitch-asset.py` both passed.
- PASS — `:app:connectedDebugAndroidTest` on the single API-37 x86_64 AVD `emulator-5560`: 29 tests, 0 failures/errors/skips across `AjtPitchRepositoryAndroidTest` (7), `AndroidPlatformSmokeTest` (3), `AssetPolicyAndroidTest` (2), `DraftAudioPlaybackTest` (2), `ImageExportInstrumentedTest` (5), `OfflineDictionaryAndroidTest` (7), and `ui.InputScreenLayoutTest` (3). Raw XML: `app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5560 - 17-_app-.xml`.
- PASS — the native XML includes `InputScreenLayoutTest.cameraViewportAndControlBoundsStayFixedAndBothModesShareTheSelector`, `liveCameraPreviewMatchesItsViewportAndShutterSavesAnImage`, and `shutterComposableKeepsItsRealBoundsAcrossReadyAndErrorStates`; the bundled offline Japanese OCR and NHK headword lookup tests also passed. This is actual camera preview/shutter and offline-fixture evidence, not merely a pitch-only run.
- PASS — split APK inspection: arm64-v8a `125,919,054` bytes with five native libraries under only `lib/arm64-v8a/`; x86_64 `126,492,495` bytes with five under only `lib/x86_64/`. The x86_64 APK contains the 36,971,461-byte NHK index (SHA-256 `9d9714b79c15ae6586d053665bf6cd0b881fcd56ed42ed3b4d758ea0499f1137`) and the bundled dictionary; it does not contain the UniDic runtime ZIP. The APK is not resource-free.
- NOT RUN — integrated OCR/dictionary chooser/dismissal/reopen flows, photo-picker selection, AnkiDroid provider import/chooser (`com.ichi2.anki` is absent), or live Google/paid requests. Those remain residual acceptance gaps.
- BLOCKED — the exact UniDic release URL in `UniDicResourceCatalog` returned HTTP 404 when checked. Hash/length/archive behavior is covered by local fake-transport tests, not a successful public first-run download. Cancel/resume and Wi-Fi/mobile-data consent onboarding remain unimplemented. AJT data licensing and NHK recording rights remain unresolved; this is not unconditional release approval.

The merge-train AVD was stopped after this run; the dedicated ADB server reported no attached devices afterward.
