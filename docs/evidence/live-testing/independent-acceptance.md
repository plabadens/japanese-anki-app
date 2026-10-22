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
- NOT RUN — No `adb` executable is available in this acceptance environment, so no new device/native rerun was possible. Native/API-37 results below are explicitly parent handoff evidence only.
- PASS — `bash scripts/test-core.sh`: JUnit `OK (192 tests)`. Java emitted a Jansi native-library/noexec warning from `/tmp`; it did not affect test completion.
- PASS — `python3 scripts/test-build-ajt-pitch.py`: builder regression passed, including required 受付 devoiced-ツ reference markup, duplicate/source-order handling, kana/alternate spelling and 出来る forms, all three shard metadata/hash recording, and refusal of incomplete shard sets.
- PASS — `python3 scripts/test-ajt-pitch-asset.py`: bundled 676,632-record index regression passed; confirms exact 受付 `ウケツケ` devoiced `ツ` output, plain variants, all required 出きる/出来る/出來る behavior, and source hashes for all three AJT shards.
- PASS — `VocabularyPartOfSpeechTest`: XML confirms 5 tests, 0 failures/errors/skips. These cover selected-sense JMdict POS mapping; compatibility for absent optional model field is covered by model/test serialization in the suite.
- PASS — Git ancestry checks for contract and integration; `git diff --check 2c473ed..HEAD` clean.

## Outcome matrix

- PASS — build and JVM/core gates above, including typed import/media, selected-sense POS, settings, generation coordinator, Review presentation, pitch rendering, and recovery tests.
- PASS (integration handoff evidence, not independently reproduced in this run) — parent `t_c3e9ffba` reports API-37 camera PreviewView bounds and a real captured/saved image decode; its API-37 instrumentation suite reports 27 tests, 0 failures/skips. This acceptance run had no attached device and `adb devices -l` returned an empty list.
- NOT RUN — independent native/API-37 rerun, OCR/dictionary choice and dismissal/reopen flows, photo picker selection, AnkiDroid provider imports/chooser, and actual media gateway post-write uncertainty on-device. No device was connected; the parent also reports `com.ichi2.anki` absent.
- NOT RUN — live Google OCR/TTS/translation quality or paid request behavior. No credentials or paid calls were used. Restore/revisit no-repeat behavior has local coordinator tests, not a live provider observation.
- NOT RUN — native light/dark/200% text runtime visual comparison and resource download interruption/storage behavior in an Android flow. The integrated `82e2232` tree does not contain resource foundation commit `c5f411e` (verified non-ancestor); onboarding/resource distribution is therefore outside this integration's delivered scope, not accepted as present. The separate resource card describes a verified downloader foundation, not complete production onboarding or released assets.
- NOT RUN — first-run distribution asset/license clearance. AJT metadata correctly retains unresolved third-party data rights; no licensing or publication claim is made.

No acceptance claim is based on a fixture as live-provider quality. Native gaps remain explicit; this is not an unconditional release approval. Target A is the approved production direction; the B alternative remains prototype-only. No implementation changes were made in this acceptance task.
