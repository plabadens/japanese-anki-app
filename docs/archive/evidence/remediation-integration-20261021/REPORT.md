# Remediation integration — evidence (2026-10-21)

> **HISTORICAL (2026-10-22):** dated integration evidence for a branch that has since been merged.
> It is the record of what was integrated and verified on that branch, not a statement about the
> current tree.

Integration branch: `japanese-anki-app/t_d8412a14-integrate-remediation-bundles-own-produc`
Integration worktree: `/srv/hermes/workspace/japanese-anki-app/.worktrees/t_d8412a14`
Cleanup baseline: `1dfd1871258ff7ce2a504e3ca1c433999231b6b3` (not modified, not pushed).

## 1. Merge parent manifest

Every parent branch was merged explicitly into this worktree, in dependency order
(foundation first; media/audio/lookups each already contained foundation, so the later merges
fast-forwarded their own ancestry and did not re-merge it).

| # | Parent card | Merged commit | Merge commit in this branch |
| - | ----------- | ------------- | --------------------------- |
| 1 | t_ddd50984 foundation      | `499a51753dea3409a701502bfb3221f34dae9b42` | `4b1001e1d46b77f94439f7e944108cf6321c73bd` |
| 2 | t_04784186 media           | `8056cec38d18f081ac317cd81c045361e0115c6c` | `3fdcee19d82aacb31d50778277ac1b2b4d44e47d` |
| 3 | t_02b1be7a audio           | `8dc005281d49d99a9997a731ae7cbcc93a23c555` | `c243f2ad32e9e5c2631f3de5fd839b1884254d5a` |
| 4 | t_a2e36dc2 lookups         | `c3c2fbc93166722a6d9bb7e08767053babbf5a7b` | `0aba2d26142a39f5cf678363da9c39844e85d2d4` |
| 5 | t_2bad2b51 services        | `3685ad55dbe371fbfb2b80a22203bf51273363bc` | `28345b8e4a60d59037aa9bbfc1bb6d1dee414f07` |
| 6 | t_eefa407e tooling         | `d323fae454c50536626378949065dcef3eee449c` | `7d387b1e806ac98f2aa415bba946f73e725821d1` |
| 7 | t_ebfb4dfa prototype       | `fa999ed2c704f0b8ee12c55cad3d7b0a01bd0115` | `fd9141c796c96656f478be0095e058798356dfdd` |

All seven merges were conflict-free (`ort`); no production file needed hand-merging.

Own integration commits on top of the merges:

* `2abd603` integrate(remediation): wire draft audio controller, AJT ownership and media receipts
* `36f3b31` fix(audio): declare inputs on Review without requiring generationStarted
* final docs commit: handoff docs marked consumed (`docs/archive/audio-reliability-integration.md`,
  `docs/archive/media-receipt-wiring.md`) + this evidence directory

## 2. Own changed files (integration owner)

```
app/build.gradle.kts                                        (packageUniDicRuntime writable copy)
app/src/main/java/dev/pierre/japaneseanki/MainActivity.kt    (production wiring)
app/src/main/java/dev/pierre/japaneseanki/ui/ReviewCard.kt   (Generate/Retry action label only)
app/src/main/java/dev/pierre/japaneseanki/DraftAudioController.kt (commit-gated retirement, clear())
app/src/main/java/dev/pierre/japaneseanki/DraftAudioStore.kt      (staging-name filter in sweep)
app/src/main/java/dev/pierre/japaneseanki/AjtPitch.kt             (hasBundledAsset)
app/src/main/java/dev/pierre/japaneseanki/AjtPitchRepository.kt    (missing vs unopenable)
app/src/main/java/dev/pierre/japaneseanki/AudioGeneration.kt      (RevisionedAudioResult removed)
app/src/test/java/dev/pierre/japaneseanki/AudioGenerationTest.kt  (two revision tests removed)
app/src/test/java/dev/pierre/japaneseanki/DraftAudioControllerTest.kt (3 commit-gating/clear tests)
app/src/test/java/dev/pierre/japaneseanki/DraftAudioStoreTest.kt  (unknown staging file retained)
app/src/androidTest/java/dev/pierre/japaneseanki/AjtPitchRepositoryAndroidTest.kt (named ctor arg)
scripts/test-core.sh                                              (explicit manifest reconciliation)
docs/archive/audio-reliability-integration.md, docs/archive/media-receipt-wiring.md, docs/archive/evidence/...
```

`git diff --stat fd9141c..HEAD` on code: 13 files, +668/−484.

## 3. Production wiring performed

* `DraftAudioController` constructed once per draft generation with a committing
  `SharedPreferencesDraftAudioPersistence`; `DisposableEffect` disposes it; a new controller is
  built after `discard()` so a retained Send freeze cannot lock the next draft.
* Inputs are declared (no provider call) when the Review route is shown; a restored Ready item
  whose identity still matches keeps its file. Generation happens only from the user's
  Generate/Retry tap (`confirm`/`retry` + blocking `runAttempt` on `Dispatchers.IO`).
* Playback takes a `PLAYBACK` lease and releases it on stop/completion/destroy.
* Send calls `freezeForSend()` before any import, builds `FrozenSubmission` audio from the frozen
  `DraftAudioFile` metadata, and releases the freeze on every outcome — retained
  (`retainFiles = true`) for an uncertain Send so recovery still owns the files.
* `discard()` on every draft reset (after releasing a freeze), plus the ownership-based startup
  sweep over `filesDir/draft` + private `filesDir/audio-staging`.
* `clearPersistedDraft` now also removes every `draft_audio_*` key.
* AJT: process-owned `AjtPitchRepository`, `availability.collectAsState()` as a Compose key,
  retryable `prepare()`, `pitch()` queries; the `ajtPitchIndex` field is gone.
* `hasEntry` replaces `search(limit = 1)` in the grouping probe (readiness gate and
  exception→false retained).
* Media receipts: `importedReceiptFromPreferences` + `liveMediaIdentity` once per phase;
  `frozenImagePermitsImport` for both frozen checks; post-import writes stay inline.
* Obsolete `RevisionedAudioResult` + `AudioGenerationState`/`AudioRequestIdentity` and their two
  tests deleted only after the wiring was compiled, tested and exercised on device.

## 4. Gates actually run (on the merged tree)

| Gate | Command | Result |
| ---- | ------- | ------ |
| JVM unit tests | `gradle testDebugUnitTest --rerun-tasks --offline` | **258 tests, 0 failures, 0 errors** (31 suites) |
| Core harness | `./scripts/test-core.sh` | **OK (183 tests)** — new controller/store/GoogleHttp/IndexHeadwords cases actually executed |
| Core harness (sandbox) | `nix build .#checks.x86_64-linux.core` | `/nix/store/795xh1prk068aiipgfqs0i4qsbs44dpz-japanese-anki-core-tests` — "Core tests passed." |
| Python self-checks | `test-build-dictionary.py`, `test-dictionary-asset.py`, `test-package-unidic.py`, `test-emulator-adb.py` | 4/4 pass (emulator-adb: 10 tests) |
| Lint (offline) | `gradle lintDebug --rerun-tasks --offline` | **0 errors, 42 warnings, 18 hints** |
| Lint baseline (offline, same machine) | same command in a scratch worktree at `1dfd187` | 0 errors, 44 warnings, 17 hints |
| Debug APK (sandbox) | `nix build .#debugApk` | `/nix/store/5l59vwxmlj0i9xjjlk5fw959gi025m46-...` |
| Release APK (sandbox) | `nix build .#apk` | `/nix/store/l1anm33byv1fbhqv9z8fzqh41jaw2fgh-...` |
| Repeat build | `gradle packageUniDicRuntime --rerun-tasks` ×2 | both succeed; output `-rw-r--r--` (read-only copy bug fixed) |
| APK assemble | `gradle assembleDebug assembleDebugAndroidTest` | success |
| Instrumentation | 5 suites on exclusive `emulator-5572` (API 37, x86_64, own AVD home/port) | **22/22 OK** (3 platform smoke, 5 image export, 2 audio playback, 5 AJT repository, 7 dictionary) |
| Boot smoke | install + launch on `emulator-5572` | MainActivity resumed, no FATAL/AndroidRuntime, sweep logged `retired=0 kept=0 unknown=0 failed=0` |
| Prototype model | `node --test model.test.cjs` | 33/33 |
| Prototype browser | `verify_flow.py` (fresh Chromium) | **382/382 checks, 81 captures, 0 JS/network errors** |

### Lint delta explanation (like-for-like, offline)

* `UseKtx` 34 → 32: two fewer `SharedPreferences.edit` call sites in `MainActivity` (the old
  audio path writes were removed).
* `AutoboxingStateCreation` 17 → 18: `audioRetry` (Int state) removed, `audioInputRevision` and
  `audioControllerGeneration` (Long states) added.
* No new issue category, no error, no finding introduced by the new production code.

### Artifact hashes

```
nix debug APK     0c2eb754aadd00c0cf26687bbf87e14be9d0a1f183dd2fa2e3121c199876ebfe
nix release APK   a8f88daeb9769bb26e030c13ce5782703832d25c3c8bb898b225ff68048e5259
gradle debug APK  520266d0eb9acf8f7aa2348dde06f1afa2177eb7b2f98a4a9123c853232a8181
androidTest APK   03811cccf5bd346f88d721c8c0b4ec1e9d4d1fb055c4512ad6b59662a1ca0a68
flow.html         1a4eb21cf09a1b75c4570095c16abc753f0b27da4cfb071545785f0a4cdac703
prototype JSON    45347c1767a61f30ead7bbe3c06f6f4aacbe7a8f640002f636cac1cf7aff39ed
```

Both APKs package `lib/arm64-v8a` and `lib/x86_64`, `assets/dictionary.sqlite`,
`assets/pitch_ajt.sqlite`, `assets/unidic-cwj-3.1.1.zip` and the UniDic licence files.
Only the x86_64 side was executed (API 37 emulator); ARM64 is packaging evidence only.

## 5. Thirteen acceptance criteria

| # | Item | Where it holds now | Evidence |
| - | ---- | ------------------ | -------- |
| 1 | MP3 lookup + cache-independent atomic materialization | `AudioProvider`/`AudioGeneration`/`DraftAudioFileStore.publish`; controller stages, fsyncs, validates, publishes | `AudioReliabilityTest`, `DraftAudioStoreTest`, device `DraftAudioPlaybackTest` (WAV/Ogg/MP3) |
| 2 | WebP output closed | `ImageMediaProcessor.processBlocking` (`use` + post-close checks) | device `ImageExportInstrumentedTest` 5/5 incl. descriptor growth and failed encoder write |
| 3 | One AJT owner | `AjtPitchRepository` process singleton + observable availability; `MainActivity` holds no handle | `AjtPitchRepositoryTest`, device `AjtPitchRepositoryAndroidTest` 5/5 |
| 4 | Safe staging/orphan retirement | private staging root, ownership leases, commit-before-retire, recognised-name sweep | `DraftAudioStoreTest`/`DraftAudioControllerTest`, startup sweep in `MainActivity` |
| 5 | Production audio controller | `DraftAudioController` wired in `MainActivity`; old state machine deleted | 21+ controller tests, 3 new commit-gating tests, device playback suite |
| 6 | Shared Google HTTP | `GoogleHttp.kt` used by both adapters | `GoogleHttpTest` (JVM and core harness) |
| 7 | Shared entity scanner with explicit policies | `HtmlEntityDecoder` + policy call sites | `AnkiFieldContractTest`/`TranslationServiceTest`; audited 311,739-case differential |
| 8 | NHK first stage only | `IndexHeadwords.recording()` single pass | `IndexHeadwordsTest`; measured ~400→197 ms / 526 MB→1.3 KB (audited run, not re-measured here) |
| 9 | Narrow existence query | `OfflineDictionary.hasEntry` + `MainActivity` grouping call | device `OfflineDictionaryAndroidTest` 7/7 (query plan, corruption boundary) |
| 10 | Unified receipt validation | `matchImportedReceipt`/`importedReceiptFromPreferences`/`frozenImagePermitsImport` wired | `SubmissionRecoveryTest`, `MainActivity` call sites |
| 11 | Shared emulator ADB policy | `scripts/emulator-adb` + `emulator.sh`/`ui-journey.py` | `test-emulator-adb.py` 10/10; exclusive AVD smoke on `emulator-5572` |
| 12 | Filtered Nix inputs | `flake.nix` filesets for APK and core | fresh sandboxed debug/release/core builds above; this docs-only change did not invalidate either derivation |
| 13 | Mockup handle cleanup | prototype bundle merged unchanged | 33/33 model, 382/382 Chromium, 3 captures byte-identical to the audited evidence |

## 6. Remaining limits (honest)

* No ARM64 runtime was executed; both ABIs are packaged, x86_64 API 37 was run.
* No physical phone, no real Google/paid provider calls, no real AnkiDroid instance, no live NHK
  download. Audio playback/import paths were exercised with generated fixtures.
* The NHK timing/allocation numbers are inherited from the audited lookups run and were not
  re-measured on the merged tree.
* Lint is 42/18 offline against a re-measured 44/17 baseline (delta explained above); the
  networked run (which adds 8 `GradleDependency` warnings) was not repeated.
* The reviewer's note about `assets/licenses/OCR-SOURCES.md` describing retired Tesseract paths was
  left untouched on purpose: it is historical attribution, not current build metadata, and this
  card does not authorise rewriting it.
* Live camera-layout verification was not added; the cleanup removed a dead helper test, and this
  card preserves the existing visible controls and geometry unchanged.
* The integration branch is local only: no push, no merge into `main`.
