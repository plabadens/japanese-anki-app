# Independent acceptance — integrated remediation (13 approved items)

> **HISTORICAL (2026-10-22):** the acceptance verdict for a branch that was later merged; it applies to
> commit `fc5f18f`, not to the current tree.

Verifier: independent acceptance worker `t_2592aa66` (verification only; no product edits).
Date: 2026-10-21 (JST). Host: this checkout's own worktree, no primary/sibling writes.

| Field | Value |
| ----- | ----- |
| Integrated candidate | `fc5f18f36fc5e6afde8e9661f7638bc0c3d908a5` (`docs(evidence): integration report…`) |
| Integration branch | `japanese-anki-app/t_d8412a14-integrate-remediation-bundles-own-produc` |
| Cleanup baseline (main) | `1dfd1871258ff7ce2a504e3ca1c433999231b6b3` (ancestor of the candidate; main itself untouched) |
| Verification worktree | `/srv/hermes/workspace/japanese-anki-app/.worktrees/t_2592aa66` (fast-forwarded to the candidate) |
| Parent card | `t_d8412a14` (sole necessary parent; joins all seven implementation/foundation cards) |
| Verdict | **PASS — accepted, 13/13; local branch only, not pushed, not merged to main** |

## 1. Parent coverage (seven merges, all ancestors of the candidate)

`git merge-base --is-ancestor <merge> HEAD` returned true for every row.

| Bundle card | Parent commit | Merge commit in candidate |
| ----------- | ------------- | ------------------------- |
| t_ddd50984 foundation | `499a51753dea3409a701502bfb3221f34dae9b42` | `4b1001e1d46b77f94439f7e944108cf6321c73bd` |
| t_04784186 media 2,10 | `8056cec38d18f081ac317cd81c045361e0115c6c` | `3fdcee19d82aacb31d50778277ac1b2b4d44e47d` |
| t_02b1be7a audio 1,5,4 | `8dc005281d49d99a9997a731ae7cbcc93a23c555` | `c243f2ad32e9e5c2631f3de5fd839b1884254d5a` |
| t_a2e36dc2 lookups 3,9,8 | `c3c2fbc93166722a6d9bb7e08767053babbf5a7b` | `0aba2d26142a39f5cf678363da9c39844e85d2d4` |
| t_2bad2b51 services 6,7 | `3685ad55dbe371fbfb2b80a22203bf51273363bc` | `28345b8e4a60d59037aa9bbfc1bb6d1dee414f07` |
| t_eefa407e tooling 11,12 | `d323fae454c50536626378949065dcef3eee449c` | `7d387b1e806ac98f2aa415bba946f73e725821d1` |
| t_ebfb4dfa prototype 13 | `fa999ed2c704f0b8ee12c55cad3d7b0a01bd0115` | `fd9141c796c96656f478be0095e058798356dfdd` |

Coverage is exactly once per approved item (1–13); no held unapproved item is present
(no NHK derived-SQLite index — item 8 is the measured first stage only; no Unicode-policy
hardening; no AGP/Kotlin/NDK/compileSdk upgrade — only pinned test-only dependencies were
added). `SPEC.md`, `AGENTS.md`, `releases/`, `app/src/main/res`, `app/src/main/assets` are
byte-unchanged; `app/build.gradle.kts` keeps versionCode 4 / versionName 0.4 / minSdk 37 /
targetSdk 37 / the shared signing configs.

## 2. Gates re-run independently on the merged tree

All Gradle invocations used the documented dev-shell form
`gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" … --offline`
with `TMPDIR` on a non-`noexec` scratch path (the bare command fails AAPT2/jansi daemon
startup on this host; that is an environment invocation detail, not a code failure).

| Gate | Command | Actual result |
| ---- | ------- | ------------- |
| JVM unit tests | `gradle … testDebugUnitTest --rerun-tasks --offline` | **BUILD SUCCESSFUL, 258 tests / 0 failures / 0 errors / 0 skipped, 31 suites** (counted from `app/build/test-results/testDebugUnitTest/*.xml`) |
| New suites actually executed | same | AudioReliabilityTest 13, DraftAudioControllerTest 24, DraftAudioStoreTest 8, GoogleHttpTest 6, IndexHeadwordsTest 13, AjtPitchRepositoryTest 9, SubmissionRecoveryTest 25 |
| Core harness (local) | `./scripts/test-core.sh` | **OK (183 tests)**, exit 0 |
| Core harness (sandbox, fresh execution) | `nix build --rebuild .#checks.x86_64-linux.core` | executes 4 Python checks + 183 Kotlin tests; output `/nix/store/795xh1prk068aiipgfqs0i4qsbs44dpz-japanese-anki-core-tests` (identical path ⇒ deterministic) |
| Python self-checks | `test-build-dictionary`, `test-dictionary-asset`, `test-package-unidic`, `test-emulator-adb` | 4/4 pass; emulator-adb “Ran 10 tests … OK” |
| Lint (merged) | `gradle … lintDebug --rerun-tasks --offline` | **0 errors / 42 warnings / 18 hints** (`UseKtx` 32, `AutoboxingStateCreation` 18 hints, `ApplySharedPref` 8, `UseTomlInstead` 1, `MissingApplicationIcon` 1) |
| Lint (baseline `1dfd187`, clean `git archive` copy) | same command | **0 errors / 44 warnings / 17 hints** (`UseKtx` 34, `AutoboxingStateCreation` 17, others identical) |
| Lint delta | comparison | exactly the accepted delta: `UseKtx` −2, `AutoboxingStateCreation` +1; no new category, no error |
| Debug APK (Gradle) | `gradle … assembleDebug assembleDebugAndroidTest --offline` | success; debug 548,244,392 B, androidTest APK hash `03811cccf5bd346f88d721c8c0b4ec1e9d4d1fb055c4512ad6b59662a1ca0a68` (identical to the integration evidence) |
| Debug APK (fresh sandbox) | isolated copy of the exact tree, `nix build path:… #debugApk` | success in 5m14s → `/nix/store/cxv7swj6sd5g4g3pwy2np0vlk2r9j215-…-0.1.0-vfy/japanese-anki-debug.apk`, sha256 `e81abe5ab43c785de7f63a000c925b0f5853540470e16e2a6421290773efb36a` |
| Release APK (fresh sandbox) | `nix build path:… #apk` | success in 6m42s → `/nix/store/v259w5wfa8rylznipwlxgfi0hrqw8l81-…-0.1.0-vfy/japanese-anki-unsigned.apk`, sha256 `a8f88daeb9769bb26e030c13ce5782703832d25c3c8bb898b225ff68048e5259` — **byte-identical to the integration evidence hash**, which binds this tree to the audited candidate |
| Instrumentation | own AVD `japanese-anki-verify-api37`, API 37 x86_64, `emulator-5574`, own ADB server `:5047` | **OK (22 tests)** — AndroidPlatformSmokeTest 3, ImageExportInstrumentedTest 5, DraftAudioPlaybackTest 2, AjtPitchRepositoryAndroidTest 5, OfflineDictionaryAndroidTest 7; 22 status code 0, 0 failures |
| Boot smoke | install + launch on `emulator-5574` | `topResumedActivity=dev.pierre.japaneseanki/.MainActivity`, 0 `FATAL EXCEPTION`; sweep logged `DraftAudio: retired=0 kept=0 unknown=0 failed=0` |
| Prototype model | `node --test model.test.cjs` (node 22.23.3) | **33/33 pass** |
| Prototype browser | `verify_flow.py --chromium … --screens …` (fresh Chromium 155) | **382/382 checks, 81 captures, 0 JS/network errors; `externalRequests: []`** |

### Lint note
The merge removes two `SharedPreferences.edit` sites in `MainActivity` (UseKtx −2) and swaps the
removed Int `audioRetry` state for two Long states (AutoboxingStateCreation 17→18). No other
finding changed.

## 3. Thirteen approved outcomes — acceptance matrix

Each row was checked in the merged tree by reading the production call path and running the
named regression that exercises it (JVM, core harness, or instrumentation).

| # | Approved outcome | Where it holds (production path) | Independent evidence | Verdict |
| - | ---------------- | -------------------------------- | -------------------- | ------- |
| 1 | MP3 lookup + cache-independent atomic draft materialization | `AudioFileValidator` (MP3 in `AudioFormat`), `ReusableAudioCache.open/store` (pin + atomic rename), `DraftAudioFileStore.beginAttempt/stageCopy/publish`, `DraftAudioController.runAttempt` (miss ⇒ synthesize once from provider output, cache store best-effort) | `AudioReliabilityTest` 13/13 (WAV/Ogg/MP3 round trip, extension mismatch, truncated/garbage, zero budget, oversized, clear-vs-copy race, invalid provider output, cache write failure, atomic publish + partial cleanup); `DraftAudioControllerTest` cache hit/miss/broken-cache/uncopyable-cache; device `DraftAudioPlaybackTest` decodes all three containers | **PASS** |
| 2 | WebP output stream closed | `ImageMediaProcessor.processBlocking` — `openOutput(temporary).use { compress }`, success/size checks after close, temp cleanup, atomic move | device `ImageExportInstrumentedTest` 5/5 (portrait/landscape/small geometry, repeated exports without descriptor growth, failing encoder write keeps destination and cleans up) | **PASS** |
| 3 | Single AJT owner | `AjtPitchRepository` process singleton (`shared`, lazy serialized `prepare`, observable `availability`, retry after failure, handle closed on probe failure), `AjtPitchIndex.probe` bounded schema/emptiness check; `MainActivity` holds no raw handle | `AjtPitchRepositoryTest` 9/9; device `AjtPitchRepositoryAndroidTest` 5/5 (source order/markup, empty index unavailable, unusable schema discarded for retry, no handle leak on repeated failures, singleton reuse) | **PASS** |
| 4 | Safe staging / orphan retirement | private `filesDir/audio-staging`, process ownership registry + leases, commit-before-retire in `setInput/acceptLocked/clear/discard`, startup `sweep` over both roots (recognised names only, canonical children only, symlinks/unknowns preserved) | `DraftAudioStoreTest` 8/8 (staging outside export root, publish rejects invalid/empty with no final, retire refuses outside root/defers to owners, sweep deletes only unowned recognised audio, never follows symlinks, reports delete failures, nested leases, staging-inside-drafts rejected); `DraftAudioControllerTest` commit-gated invalidation/discard/clear, unknown staging file retained | **PASS** |
| 5 | Production audio controller | `DraftAudioController` constructed once per draft in `MainActivity`, per-item jobs, serialized gate, explicit `confirm`/`retry`, token-checked acceptance (epoch+item+identity+attempt), durable publication before Ready, `freezeForSend` leases, restore without provider calls, `dispose`; `RevisionedAudioResult` and the test-only state machine removed | `DraftAudioControllerTest` 24/24 (A→B→A, discard/new draft same inputs, stale success/failure, cancel/dispose, per-item retry leaves sibling, immutable frozen snapshot, restore zero provider calls, persistence failure, commit-gated retirement); device boot sweep + Review Generate label | **PASS** |
| 6 | Shared Google HTTP, separate service policy | `GoogleHttp.request` used by `TranslationService` (256 KiB) and `speech/GoogleSpeech` (32 MiB); key only in `X-Goog-Api-Key`, redirects disabled, no retries, fixed-length POST / bodyless GET, bounded success+error reads, disconnect in `finally` | `GoogleHttpTest` 6/6 in JVM **and** core harness (headers/method/timeouts/redirect flag/fixed length, exact limit vs +1 byte on both streams, null error stream, read/write/response-code failures close+disconnect, both adapters share the primitive with separate limits); `TranslationServiceTest` redirect classes preserved | **PASS** |
| 7 | Shared entity scanner, explicit numeric policies | `HtmlEntityDecoder.decode(text, policy)`; `TranslationService.plainText` = TRANSLATION (rejects 0, permits surrogates), `AnkiFieldContract.DuplicateKey` = strip highlight → decode → trim (permits 0, rejects surrogates); unknown entities literal, non-recursive, 12-char terminator guard | `AnkiFieldContractTest.duplicateEntityScannerPreservesItsDistinctNumericPolicyAndOrdering` (zero, D7FF/D800/DFFF/E000, supplementary, max, out-of-range, overflow, signs, hex case, long/missing terminators, double escape, highlight strip order); `TranslationServiceTest` translation matrix asserts `&#0;` literal and `&#55296;`→`\uD800` | **PASS** |
| 8 | NHK first stage only (measured) | `Nhk2016PronunciationSource.downloadIndex` validates + captures the requested recording in the download pass, then atomically publishes without a second read; `IndexHeadwords` single pass validates every headword/filename while retaining only the first match; later lookups still scan the file (honest residual O(file)) | `Nhk2016PronunciationSourceTest` 12/12 incl. fresh download reads the index once, malformed later content publishes nothing, oversized abort, failed publication, cancel, concurrent download; `IndexHeadwordsTest` 13/13 incl. match followed by malformed content, invalid filename anywhere, duplicate-key/empty-array semantics | **PASS** (timing/allocation numbers inherited from the audited lookups run, not re-measured) |
| 9 | Narrow dictionary existence query | `OfflineDictionary.hasEntry` — trim, false for blank before DB required, forms→entries join, `SELECT 1 … LIMIT 1`, no ordering/sense JSON; grouping probe in `MainActivity` keeps readiness gate and exception→false | device `OfflineDictionaryAndroidTest` 7/7 (agrees with search on bundled data, blanks/metacharacters, restricted entries, query plan without sort/JSON, orphan forms, corrupt senses keep existence but break selection, blanks before DB) | **PASS** |
| 10 | Unified live media receipt validation | `matchImportedReceipt` (pure; saved identity+filename vs current identity), `importedReceiptFromPreferences` adapter, `liveMediaIdentity` once per phase, `frozenImagePermitsImport` kept separate (deleted local export admitted, changed source rejected); MainActivity recomputes at Send and keeps post-import checks | `SubmissionRecoveryTest` 25/25 (exact identity+filename, missing/unsafe filenames, path/size/mtime individually, legacy defaults, adapter reads only its keys, no stat when identity supplied, null for missing/unstatable, frozen deleted-vs-changed source, audio classifications) | **PASS** |
| 11 | Shared emulator ADB policy | `scripts/emulator-adb` single entry (pinned SDK adb, one emulator serial, `ADB_SERVER_SOCKET` unset, conflicting selectors and physical serials rejected); `emulator.sh` + `ui-journey.py` use it; unique remote/local dumps under scratch | `test-emulator-adb.py` 10/10 (default/pinned argv, custom port/serial, conflicts, invalid serial/port, global override rejected, binary screenshot integrity, concurrent unique dumps, host-scoped devices, failed dump error, wrapper unique remote path + cleanup); own exclusive AVD smoke on `emulator-5574` using the pinned SDK adb | **PASS** |
| 12 | Filtered Nix source inputs | `flake.nix` `apkSource` (root Gradle files, `gradle/`, `app/build.gradle.kts`, `app/src`) and `coreCheckSource` (Kotlin core sources/tests, Python builders/checks, dictionary asset, JMdict/Kanjium inputs) | drvPath probes: docs `README.md` edit → debug/release/core drvPaths unchanged; `scripts/build_dictionary.py` edit → core drvPath changed (`y0ifwf…` → `nb3knw…`), APK unchanged; Kotlin `TargetSpan.kt` edit → both debugApk and core drvPaths changed; fresh sandbox debug/release/core builds succeeded (above) | **PASS** |
| 13 | Obsolete mockup selection handles removed | `study.js`/`study.css` contain no handle positioning, caret lookup or handle-only branches; `flow.html` regenerated from sources; pills, drag, focus restore, `stepBoundary` retained | `flow.html` regenerated with `build_mockups.py` → sha256 `1a4eb21c…` and **no git diff** (generated artifact is reproducible); node 33/33; Chromium 382/382 incl. “tappable word pills replace selection handles”; before/after pair 002 and 003 byte-identical between `final-screens/` (pre-cleanup) and `evidence/selection-cleanup-20261021/screens/`; fresh run 66/81 captures byte-identical to committed evidence | **PASS** |

## 4. Focused adversarial checks requested by the card

| Scenario | Evidence |
| -------- | -------- |
| A→B→A / new draft same inputs / stale failure | `DraftAudioControllerTest.aToBToARequiresContinueAndRetiresTheInvalidatedFile`, `discardClearsStatePersistsAndLeavesNoLiveReference`, `lateSuccessAfterInputChangeIsRejected`, `workerCancellationRevokesTheAttempt` |
| No automatic provider after restore | `restoreValidatedReadyMakesZeroProviderCalls` (0 provider calls), `restoreInterruptedOrLegacyStateRequiresRetry`; MainActivity declares inputs only, providers run only from `confirm`/`retry` |
| Cache clear / copy / disabled / oversized | `AudioReliabilityTest.clearAndEvictionSkipPinnedEntriesUntilReleased`, `concurrentClearDuringMaterializationCannotTruncateTheDraft`, `recordingLargerThanTheCacheBudgetIsSkipped`, `zeroBudgetCacheStoresNothingButDraftStillMaterializes`; controller `brokenCacheDoesNotLoseValidDraftAudio`, `uncopyableCacheEntryFailsInsteadOfPayingForSynthesis` |
| Failure / cancel / delete / pref-commit crashes | `persistenceFailureDoesNotClaimReadyAudio`, `invalidationIsNotRetiredWhenTheCommitFails`, `discardKeepsAcceptedFilesWhenTheStateCommitFails`, `sweepReportsDeleteFailuresWithoutClaimingSuccess`, `disposeRejectsLaterAcceptance` |
| Frozen/uncertain media retention + canonical escape | `sendFreezeRequiresBothReadyThenPinsFilesAndBlocksChanges` (freeze blocks setInput/confirm/discard, files remain), `SubmissionRecoveryTest` frozen audio/media round trip; `DraftAudioStoreTest.retireRefusesOutsideRootAndDelaysForOwners`, `sweepNeverFollowsSymlinksOrDeletesOutsideTargets` |
| Different entity zero/surrogate policies | `AnkiFieldContractTest.duplicateEntityScannerPreservesItsDistinctNumericPolicyAndOrdering`, `TranslationServiceTest` translation matrix |
| Later malformed NHK content + residual O(file) | `Nhk2016PronunciationSourceTest.freshDownloadWithMalformedLaterContentPublishesNothing`, `IndexHeadwordsTest.aMatchFollowedByLaterMalformedContentIsRejected`; documented single-pass-whole-file residual |
| On-device wired routes | Real share intent → Target (grouped word pills 図書館/で/本/を/読みました) → dictionary entry sheet (図書館 ずしょかん/としょかん; 本 ほん/もと) → Review. Word audio card shows “Tap Generate to create this audio.” with an explicit **Generate** button (NeedsContinue, no provider call); sentence audio shows the no-credential configuration error with Retry; Send/Send-freeze blocked by missing credentials/AnkiDroid (covered by controller tests instead) |

## 5. Artifact and evidence hashes (this run)

```
flow.html (regenerated, == committed)   1a4eb21cf09a1b75c4570095c16abc753f0b27da4cfb071545785f0a4cdac703
sandbox debug APK (fresh)               e81abe5ab43c785de7f63a000c925b0f5853540470e16e2a6421290773efb36a
sandbox release APK (fresh)             a8f88daeb9769bb26e030c13ce5782703832d25c3c8bb898b225ff68048e5259
Gradle debug APK                       67f07fe9d20cdc18276ba7b15f9899a7e6328675cb1309562c4fc29c1eb8ae5e
Gradle androidTest APK                 03811cccf5bd346f88d721c8c0b4ec1e9d4d1fb055c4512ad6b59662a1ca0a68
JVM test log                           741b721b5ac8c284763115667560da010a285869c9bbf87ac19519d783ed685c
core harness log                       65d07b5dcfc797d4c1981c64eeb67bd64e15763688eceab82d666cd503d44107
lint merged log / XML                  5be0432d… / 6958ab0c456942fe208f2b612055f290235e48c1adf6f8e0c474bf41332ec316
lint baseline log / XML                f2e3e8fb… / 10051a0c8dac3c85f6d83be4be27fcc2ce54869797cbbeed61666b0c397ab115
instrumentation log                    77dea7d49d261f7b2fe75e72a48fc87a537b43205820de7c11084c885a6bdd9a
prototype verification JSON            a5c69c3c12222aa11d9b256a4bede5b3da0af9068483527fdca5477797f3c7ed
prototype run log                      913f2f3379a74909cc32768ff494f4b9d60eace2196f23a341ee2bd8181bdce9
```

Gradle-built APKs are not byte-reproducible (zip timestamps); the release APK produced by the
sandbox from this exact tree is byte-identical to the integration evidence, which is the binding
identity check. The integration evidence's own `nix debug APK` hash differs from this run's fresh
debug build for the same reason (`nix build --rebuild .#debugApk` additionally reports the
non-determinism, while the sandbox release build is deterministic).

## 6. Honest limits of this acceptance

- No ARM64 runtime; both `lib/arm64-v8a` and `lib/x86_64` are packaged in debug and release APKs,
  only x86_64 API 37 was executed. ARM64 is packaging evidence only.
- No physical phone, no real Google/paid calls, no live AnkiDroid instance, no live NHK download.
  Audio was exercised with generated WAV/Ogg/MP3 fixtures and a fake synthesizer; no claim is made
  about real speech quality or provider success. Receipt matching was exercised with fixture
  identities, not a real AnkiDroid media import.
- NHK timing/allocation numbers (≈400→197 ms, 526 MB→1.3 KB) are inherited from the audited
  lookups run; they were not re-measured on this tree. The residual whole-file scan per lookup is
  stated, not eliminated.
- Lint is compared offline (`42/18` vs re-measured `44/17`); the networked run (which adds
  `GradleDependency` warnings) was not repeated.
- The on-device UI walk reached Review and the explicit Generate label, but a full Send/freeze on
  device needs Google credentials and AnkiDroid; that path is covered by controller tests, not
  device execution.
- Browser evidence (382 checks) is mockup evidence, not native Android acceptance.

## 7. Non-blocking observations (no acceptance impact)

1. `AudioDraftMaterializer` / `ReusableAudioCache.copyToActiveDraft` are now referenced only by
   tests; the production path is `DraftAudioFileStore.stageCopy` + `publish`, which carries the
   same validation/atomicity and is what the controller tests exercise. Retained helper is dead
   production code, not a behavioural gap.
2. `ReusableAudioCache.removeTemporaryFiles()` / `sizeBytes()` filter names ending `.tmp`, while
   in-flight cache temporaries are named `.…tmp.<ext>`; a crash-left temporary is therefore only
   reclaimed by budget eviction, never proactively and never published. No partial file can be
   published (publish is a separate atomic move).
3. `README.md` has one documentation line updated for the new `emulator-adb` policy (item 11);
   no product or constraint file changed.

## 8. Readiness

All thirteen approved outcomes are present on the integrated candidate `fc5f18f` and independently
reproduced (JVM 258, core 183, Python 4/4, lint 42/18 vs 44/17, instrumentation 22/22, prototype
382/382 + 33/33, fresh sandbox debug/release/core). No unapproved work is bundled and no
acceptance criterion is satisfied only by a worker claim. **Ready for acceptance on the integration
branch; nothing was pushed and main remains `1dfd187`.**
