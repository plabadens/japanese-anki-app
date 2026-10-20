# v0.2 acceptance-test matrix and Android emulator runbook

**Historical / superseded:** this is the pre-redesign v0.2 runbook and A01–A67 matrix, preserved as implementation evidence only. Use **SPEC.md §14 R01–R29** for current acceptance. The new scope is API 37, ARM64 device packaging and x86_64 emulator, local preparation followed by Google Chirp 3 HD speech and OpenRouter translation, complete-or-block export, WebP at 1080 px, and a Nix development build without byte-equality gates. Old fallback, optional-enrichment, custom camera, mockup-conformance, API-35/36 matrix, and reproducibility instructions below must not drive new implementation. Commands describe the old checkout and need revalidation on the API-37 runner. Nothing here is a conformance report.

## 1. Toolchain and emulator

Repository: `japanese-anki-app`, x86_64 Linux/NixOS. Use the pinned flake from the checkout; no system Java/Android SDK/Gradle is required. Current flake configuration: JDK 17, Gradle 8.14.4, AGP 8.13.2, compile/target SDK 37, min SDK 35, Build Tools 36.0.0, NDK 27.2.12479018, CMake 4.1.2; app ABI filters are `arm64-v8a` and `x86_64`. Nix Android SDK licenses are accepted in `flake.nix` (`licenseAccepted = true`). The API-37 compile warning from AGP 8.13.2 is known; see `docs/toolchain-notes.md`.

The reproducible local AVD exposed by this flake is only API 35 / Android 15, `google_apis`, `x86_64`, Pixel 7 profile, 1080×2400, 420 dpi, 2 GiB RAM, 10 GiB data partition, back camera emulated, Play Store disabled. The wrapper starts it headlessly with SwiftShader, no audio, on serial `emulator-5554`; device operations explicitly target that serial and unset `ADB_SERVER_SOCKET`, so they do not touch an attached phone. The wrapper stores its AVD, Android user state, logs, and screenshots beneath ignored `.tmp-nix/`.

Important matrix limitation: SPEC §14 requires API 35, 36, and 37 on both ARM64 and x86_64 (six combinations), and all combinations are emulator-testable; physical-device testing is optional and never a release blocker. This Nix flake currently provides an API-35 x86_64 emulator only, so the other five combinations need separately provisioned/pinned emulator images. Emulator camera is synthetic; route realistic image fixtures through the emulator's camera/image input, and install a test TTS engine/voice in the emulator. Record simulation limits separately; they do not require a physical device.

Build the emulator tooling once (first realization in this environment fetched roughly 1.7 GiB):

```sh
nix build .#emulator .#emulator-wrapper
```

From repository root, start and inspect the AVD:

```sh
nix run .#emulator-wrapper -- start
nix run .#emulator-wrapper -- status
```

Build and install the current debug APK:

```sh
nix develop --command bash -lc 'gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest assembleDebug --no-daemon --max-workers=2'
nix run .#emulator-wrapper -- install app/build/outputs/apk/debug/app-debug.apk
nix run .#emulator-wrapper -- launch
```

`nix develop` pins the toolchain and writes ignored `local.properties`. There is no Gradle wrapper. The direct Gradle command above can use the local Gradle/Maven cache; that is not proof of the Nix sandboxed APK build or dependency-lock completeness. `docs/toolchain-notes.md` records the current lock gap (ML Kit artifacts / release lint coverage) and `SPEC.md` §12.3 requires two clean unsigned-APK builds for reproducibility.

The wrapper's single-session smoke command is the reliable option in process-isolated shells, where background children may be killed when a command returns:

```sh
nix run .#emulator-wrapper -- session app/build/outputs/apk/debug/app-debug.apk .tmp-nix/screenshots/smoke
```

It creates the Pixel 7 AVD if needed, waits for boot, disables Bluetooth (avoids a recurring system crash prompt), installs/launches the APK, captures dark and light startup PNGs, writes logcat, scans for app crashes, then shuts the AVD down. `start`/`status`/`install`/`launch`/`stop` are also available separately when the host keeps the emulator alive between commands. The session path is preferable for one-shot smoke checks, not for walking through dozens of UI states; on a persistent emulator use the individual commands below, or run an equivalent scripted sequence inside one process.

Useful interaction, screenshot, and diagnostic commands:

```sh
nix run .#emulator-wrapper -- tap <x> <y>
nix run .#emulator-wrapper -- theme dark
nix run .#emulator-wrapper -- screenshot .tmp-nix/screenshots/<case>-dark.png
nix run .#emulator-wrapper -- theme light
nix run .#emulator-wrapper -- screenshot .tmp-nix/screenshots/<case>-light.png
nix run .#emulator-wrapper -- dump .tmp-nix/ui/<case>.xml
nix run .#emulator-wrapper -- share-text '食べた'
nix run .#emulator-wrapper -- share-image "$PWD/<image-fixture.png>"
nix run .#emulator-wrapper -- logcat -d
nix run .#emulator-wrapper -- stop
```

For emulator-side settings not exposed by the wrapper, use `adb -s emulator-5554 ...` from `nix develop`. Examples: `adb -s emulator-5554 shell settings put system font_scale 2.0` (restore to `1.0`), `adb -s emulator-5554 shell svc wifi disable` / `enable` (restore networking), and `adb -s emulator-5554 shell settings put system accelerometer_rotation 0` followed by `settings put system user_rotation 1` for the landscape photo test. Restore settings after each case. Keep the device serial explicit. `scripts/emulator.sh` also provides `reinstall`, `restart`, `grant`, `share-text`, `share-image`, `dump`, and `logcat`.

### Emulator installation data and AnkiDroid

The bundled Nix AVD is Google APIs, not Play Store. For AnkiDroid provider tests, install AnkiDroid separately from an official distribution into this emulator only (`nix run .#emulator-wrapper -- install /path/to/official-ankidroid.apk`). Use a disposable emulator collection, never a personal collection. Create a test deck and a note type containing the exact 14 required fields listed in SPEC §8.1; also create variants with reordered required fields plus an extra field, and one with a missing required field. `misc/Sentence (JA)/template.json`, `Recognition/front.html`, `Recognition/back.html`, `template.css`, and the `misc/Sentence (JA)` README are reference setup material; the app does not install or modify templates, note types, or decks. Grant the supported AnkiDroid API permission through its actual Android flow.

Use separate disposable collection states for: (1) empty deck; (2) exact same sentence+lemma in the selected deck; (3) same sentence only; (4) same lemma only; (5) exact match in another deck; (6) several existing tags including `JLPT::N4`; (7) model/deck deleted or required field removed. Verify final note fields, deck membership, imported media, tags, and no collection writes on blocked/error cases inside AnkiDroid.

### First launch / device caveats observed during preparation

On base revision `88c5252`, the app installed and `MainActivity` resumed on the API-35 x86_64 AVD with no app crash lines. First launch prompts for camera permission and extracts UniDic into private app storage (about 725 MB); the wrapper's fixed 5-second settle is too short for that initialization. A later capture showed 26% extraction progress. This environment also showed an Android `ANR in com.android.systemui` startup dialog (`ProcessRecord ... failed to complete startup`); choosing **Wait** returned to the app while extraction continued. This was observed, not diagnosed as an app defect or proven to be harmless. For acceptance captures, grant camera permission, wait for extraction to finish before starting cases, then confirm status and collect logcat. Do not force-stop/uninstall or erase AVD app data while the initial extraction is running. An `avdmanager` warning about reading `devices.xml` from the Nix store appeared, but the AVD still booted with `sys.boot_completed=1` and reported API 35 / x86_64.

## 2. Test data and preconditions

Use these deterministic text fixtures, derived from the current checked-in tests and pinned dictionary asset:

| Fixture | Input/selection | Expected invariant | Source |
|---|---|---|---|
| Inflection/lemma | Sentence `食べた`; target `食べた` | Keep sentence surface `食べた`; resolve vocab lemma `食べる` / reading `たべる` | SPEC A03; `JapaneseMorphologyTest.inflectedVerbAndAuxiliaryResolveToVerbLemma`; `test-dictionary-asset.py` |
| Repeated occurrence | `猫と猫`; target UTF-16 span `[2,3)` (the second `猫`) | Highlight only second occurrence in both serialized sentence fields | `TargetSpanTest.selectionRetainsExactOccurrence`; `ReviewSentencePresentationTest` |
| Contiguous phrase / outside ruby | `東京へ行く`; target `東京へ` span `[0,3)` | Preserve chosen contiguous phrase and ruby outside it (`行く` / `いく`) | `ReviewSentencePresentationTest.multiTokenTargetUsesTargetReadingAndRetainsFuriganaOutsideTarget` |
| Sentence reading | `昨日は学校`; target `学校` span `[3,5)` | Target selection stays exact; known non-target readings are automatic and optional | `JapaneseMorphologyTest.sentenceFuriganaSuggestionsExcludeTargetAndUseAnalyzerReadings` |
| Kana-only | `こんにちは` | No unnecessary ruby; still sendable | `ManualNoteDraftTest.kanaOnlyContentDoesNotRequireManualReadingConfirmation` |
| Dictionary ambiguity | `人気` | Pinned SQLite contains distinct entries/readings (including `じんき`, `にんき`, `ひとけ`); confirm one entry before dependent vocabulary fields settle | `datasets/jmdict/JMdict_e.gz`, bundled `dictionary.sqlite.gz`; check actual candidate list in app |
| Pitch alternatives | `あいの手` / `あいのて` | Pinned Kanjium row has accents `3,4`; initially choose first silently; only Edit word exposes both listed choices | `datasets/kanjium/accents.txt`; generated SQLite; check actual matched entry/readings |
| Four pitch shapes / morae | Unit fixture reading `がっこう`, accents `0,1,2,4`; `しゅっちょう` | Cover heiban, atamadaka, nakadaka, odaka, valid zero, contracted kana, small `っ` and long vowel | `PitchAccentTest` |
| Unknown/unresolved reading and pitch | Use an entry/reading with no available source reading/pitch and record the exact pair in the run log | No fabricated annotation/diagram; optional fields do not disable otherwise valid Send | SPEC A08/A12/A43; current unit tests cover sendability but cannot prove UI state by themselves |
| OCR single / multiple block | Required image fixture content: one Japanese region containing `食べた。`; two visibly separate regions containing `猫がいます。` and `犬もいます。` | Single block proceeds directly; multiple blocks require choice; OCR remains on-device | SPEC A41/A47/A63. No Japanese OCR image fixture is currently checked in; prepare/hash realistic fixtures, inject them into the emulator's camera or image-picker input, and then mark the UI/OCR checks runnable. |

For realistic OCR acceptance, prepare copyright-safe, sharp Japanese-text image fixtures with known transcriptions: one image with a single text block, one with two separated plausible blocks, and varied orientation/contrast/distance examples. Record exact expected text, fixture hash, input route, orientation, network state, and OCR output. `misc/Sentence (JA)/samples/book.jpg` is a decorative book photo without legible Japanese and is not an OCR fixture. Inject fixtures through the emulator's camera or image-picker input; actual physical-camera capture is optional.

Other external inputs are not repository fixtures: official AnkiDroid APK + disposable emulator collection; Azure test credentials (never commit them, place them only in local profile/runtime settings, and remove them after the run); an installed Japanese offline Android TTS test engine/voice in the emulator; controlled audio HTTP/index responses for missing/available/failure/cancellation cases; and a translation model download path. Lack of credentials or provider test doubles can block the relevant external integration gate, but lack of a physical device cannot.

## 3. Existing automated checks and baseline evidence

Run from the repository root:

```sh
nix build .#checks.x86_64-linux.core
nix develop --command bash -lc 'gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest assembleDebug --no-daemon --max-workers=2'
```

The Nix core check runs dictionary-builder/asset/UniDic packager checks and `scripts/test-core.sh`; the Gradle `testDebugUnitTest` suite includes all current `app/src/test` classes. Python helper checks and the README's dataset build sequence are in `README.md`. There are JUnit local tests and no `app/src/androidTest` files at the prepared baseline; passing JVM tests do not establish Android permission, Compose interaction, real camera, AnkiDroid provider, or hardware TTS behavior.

Baseline run actually performed on clean detached worktree at `88c5252` (kept under ignored `.tmp-nix/prep-baseline` to avoid racing implementation edits):

- `nix build .#checks.x86_64-linux.core`: exit 0.
- Full `testDebugUnitTest assembleDebug --no-daemon --max-workers=2`: exit 0; 48 Gradle tasks executed; all 17 JUnit XML suites report zero failures/errors/skips; both NDK ABI builds ran; debug APK produced at `.tmp-nix/prep-baseline/app/build/outputs/apk/debug/app-debug.apk`.
- `nix build .#emulator .#emulator-wrapper --no-link -L --max-jobs 2`: exit 0.
- `nix run .#emulator-wrapper -- session ...`: exit 0; installed APK; `MainActivity` resumed; wrapper app-crash scan found no app crash lines; startup PNGs and logcat were saved under `.tmp-nix/screenshots/`.
- A direct cached Gradle invocation in the shared checkout was UP-TO-DATE and not evidence of rerunning tests. A forced `--rerun-tasks` there failed before compilation because a previous copied `app/build/generated/unidicAssets/unidic-cwj-3.1.1.zip` was read-only (`Permission denied`); the clean detached worktree rerun passed. Treat this as a generated-output permission/cache issue, not a test failure. Keep implementation/build work isolated from concurrent shared-worktree edits.
- The AVD startup `ANR` / long UniDic extraction described above remains an environment/runtime item to retest after initialization. No mockup-complete app behavior was claimed by this baseline.

Existing test ownership map:

| Concern | Existing automated coverage |
|---|---|
| Span boundaries, repeated occurrence, surrogate-pair protection | `TargetSpanTest`, `ReviewSentencePresentationTest` |
| Morphology, inflected lemma, reading alignment, unresolved phrases | `JapaneseMorphologyTest` |
| OCR candidate ordering/geometry | `OcrCandidateSelectionTest` (not OCR recognition itself) |
| JMdict/Kanjium contract, field mapping, duplicate key rules | `AnkiFieldContractTest`, `scripts/test-dictionary-asset.py` |
| Safe furigana/definition/notes/media serialization | `AnkiFieldSerializerTest`, `ManualNoteDraftTest`, `AnkiMediaImporterTest` |
| Pitch mora counting, zero, contour, colors, unknown/stale values | `PitchAccentTest`, `PitchPatternDiagramTest` |
| Audio provider order/cache/revision/retry | `AudioGenerationTest`, `Nhk2016PronunciationSourceTest`, `AzureSpeechSynthesizerTest`, `ReviewAudioPresentationTest` |
| Translation state/fallback/retry | `TranslationServiceTest` |
| Submission recovery state machine | `SubmissionRecoveryTest` |
| AVIF header validation only (not Android encoding/quality) | `ImageMediaProcessorTest` |

Do not use `nix build .#apk` / `.#debugApk` as a routine pass until the Gradle dependency lock covers the exact configurations; do not use a warm-cache Gradle result as proof of sandboxed Nix reproducibility. Release signing remains separate and must use a locally held signing identity; do not fabricate a signed artifact.

## 4. Acceptance matrix: SPEC §14 IDs

Run relevant unit tests first, then emulator scenarios. App-specific routes and labels may move during v0.2 implementation; preserve the condition/expected result and update only the concrete interaction steps. For AnkiDroid/provider cases, use disposable emulator collection data described above.

| ID | Automated check / fixture | Emulator or device scenario and pass evidence |
|---|---|---|
| A01 | Native/build verification: `testDebugUnitTest assembleDebug`; inspect APK ABI entries. | Install/run on all six API/ABI combinations (§14) using emulators. Current prepared AVD covers only API 35 x86_64; the other five emulator combinations require separately provisioned/pinned images. Record `getprop ro.build.version.sdk`, ABI, install result, app logs. Physical-device testing is optional and never required. |
| A02 | Dictionary/morphology/pitch/serializer unit tests; inspect bundled OCR dependency/model packaging. | Disable AVD Wi-Fi and run local enrichment; fresh install must OCR with no network/model download; separately verify translation before and after model download, then offline. OCR local-model availability and ML Kit model lifecycle remain release gates. |
| A03 | `JapaneseMorphologyTest`; `食べた` → `食べる`. | Type sentence, select inflected surface. Verify sentence unchanged and vocabulary uses lemma; change by reselection only. |
| A04 | `TargetSpanTest` + `ReviewSentencePresentationTest`; `猫と猫`, span `[2,3)`. | Select second occurrence; inspect review and exported `SentKanji`/`SentFurigana`, only that occurrence bold. |
| A05 | `TargetSpanTest`, `JapaneseMorphologyTest`, `ManualNoteDraftTest`. | Select contiguous tokens and adjust endpoints; then choose a no-entry selection. Verify original sentence/selection survives and no manual vocab entry/neighbor auto-expansion appears. |
| A06 | `OfflineDictionary.search` against `人気`; state/presentation tests. | From initial Target, confirm candidate sheet overlays intact Target; repeat from Edit word and confirm Review remains beneath it. Choose one entry; dependent fields settle before Send is enabled. |
| A07 | `AnkiFieldSerializerTest.definitionsKeepSenseBoundariesAndEscapeMarkup`; `ManualNoteDraftTest`. | Choose multiple senses, edit combined definition including markup-like text, Done, export. Check safe field in AnkiDroid. |
| A08 | `ManualNoteDraftTest` unresolved reading cases; `ReviewSentencePresentationTest.leavesUnresolvedKanjiMarkedAndShowsKnownReadingInline`. | Trigger unresolved target/non-target/vocab readings. No invented ruby, dotted underline/accessible explanation, no reading editor; valid note remains sendable. |
| A09 | `ManualNoteDraftTest.kanaOnlyContentDoesNotRequireManualReadingConfirmation`. | Enter `こんにちは`; verify no forced furigana prompt and export eligibility. |
| A10 | `JapaneseMorphologyTest`, `AnkiFieldSerializerTest`. | Exercise `食べた`, `昨日は学校`, and `東京へ行く`; verify okurigana/punctuation/span are unchanged and no invented per-kanji split reading. |
| A11 | `PitchAccentTest.handlesAtamadakaAndInternalDropPatterns` and four pattern colors; `がっこう` accents 0/1/2/4. | Review four actual source-backed patterns across themes; compare contour/drop position with exported classes and reference template. |
| A12 | `ManualNoteDraftTest.unknownPitchIsEmptyAndStalePitchDoesNotBlockOrSerialize`. | Choose a resolved entry with no exact Kanjium expression+reading row. No diagram; neutral mark; Send remains eligible; `VocabPitch` empty. |
| A13 | Query Kanjium `あいの手` / `あいのて` accents 3 and 4. | Review silently shows first listed choice; only Edit word opens the two source values, with kana/full-scale diagram; select one and verify one export pattern. |
| A14 | `PitchPatternDiagramTest.dotsStayFilledWithoutSourceBackedNasalization`; source inspection. | Current Kanjium has pitch positions only, so all dots remain filled. Do not invent nasal/devoicing marks. The future marked-mora half is gated by §15 `phonetic-pitch-source`. |
| A15 | `Nhk2016PronunciationSourceTest` controlled provider tests. | With real pinned recording available, entry resolution starts matching word recording; verify automatic selection, cache, attached media and neutral pitch-not-verified explanation when source lacks metadata. Use emulator network/provider controls; record any untestable external-service limitation. |
| A16 | `AudioGenerationTest.vocabularyUsesRecordingThenAzureThenOfflineAndSentenceSkipsRecording`. | Fake unavailable/missing NHK recording with Azure ready; verify word uses Azure and source picker absent. |
| A17 | `AudioGenerationTest.azureFailureFallsBackToOfflineProvider`; `AzureSpeechSynthesizerTest`. | Fail Azure; install an offline Japanese TTS test engine/voice in the emulator that supports file output. Verify audio file/playback; record emulator/runtime limitations. Physical-device validation is optional. |
| A18 | `AudioGenerationTest.allUnavailableReturnsFailureAndUnchangedReadyRequestDoesNotRestart`. | Disable/fail all sources. Neutral state replaces play, other fields remain, Send allowed, Retry is explicit and item-scoped. |
| A19 | `AudioGenerationTest.obsoleteAudioCompletionCannotReplaceNewRevisionAndFailureCanBeRetriedExplicitly`. | Resolve audio, edit sentence/target, save. Old result must disappear while new chain runs; no manual regeneration control. |
| A20 | Retired by SPEC §3.2/§6.5. | Not applicable; do not add recording UI tests. |
| A21 | `ImageMediaProcessorTest` is header-only; add API-level instrumentation for encoder/metadata/dimensions. | Photo-picker/camera full frame on each API/ABI release target. Verify orientation, no crop, no upscale, max edge 2048, stripped EXIF/location, static AVIF and durable draft copy. API35 x86_64 only is provisioned locally. |
| A22 | Add representative small-print image fixture and decode/output checks. | Export the same AVIF into disposable AnkiDroid; inspect small text legibility and record platform vs bundled encoder, API/ABI and actual renderer. |
| A23 | Revision/cancellation tests in `AudioGenerationTest`, `TranslationServiceTest`. | While enrichment is pending, reselect target; confirm no late result mutates current card or already-frozen export. |
| A24 | `TranslationServiceTest`, `ManualNoteDraftTest`, submission/state tests. | Edit definition/translation, change source input; retain manual text as stale, show separate suggestion, verify Keep/Use-new resolution and optional omission. No silent overwrite. |
| A25 | `SubmissionRecoveryTest`, draft-state tests where present. | Save a nonempty draft and media, rotate/kill/restore; verify selection/data return and interrupted billable requests do not silently restart. Trigger optional Retry explicitly. |
| A26 | Provider/error tests where present. | Test with no AnkiDroid, API permission denied, invalid/missing-field model, renamed/deleted model. Draft intact, actionable setup error, no collection mutation. |
| A27 | `AnkiFieldContractTest` duplicate-key tests. | Add exact sentence+lemma in selected model/deck; Send blocked without override. Force duplicate-query failure; Send remains blocked with retry. |
| A28 | `AnkiFieldContractTest` scoped/two-field duplicate cases. | Independently test sentence-only, lemma-only, and full match in other deck; all should be allowed by this app's combined destination-deck policy. |
| A29 | Optional media/state tests. | Hold audio/image/readings pending, then Send valid required fields. Pending omissions visible; card freezes; late results do not change submitted note. |
| A30 | `SubmissionRecoveryTest` repeated-send/reconciliation cases. | Repeatedly tap Send and simulate lost response with a provider fake or controlled test gateway; one insert at most; reconcile before any checked retry. Never induce an uncertain send against a valuable collection. |
| A31 | Submission/media importer unit tests. | Fault media import / note creation independently. Keep draft and returned receipts; no deletion of AnkiDroid collection media as rollback. |
| A32 | State tests for blank reset/tag history if present. | Successful disposable export; verify deck, fields, media, tags; next blank capture retains last mode/deck/tags but drops sentence/target/media/notes/translation. |
| A33 | `AnkiFieldContractTest.mapsRequiredFieldsByNameAndLeavesExtrasEmpty`; serializers. | Inspect actual AnkiDroid note by field name. All 14 required values valid; all `fm_*` and extra fields empty. |
| A34 | `AudioGenerationTest.cacheEvictsLeastRecentlyUsedReusableFilesAndKeepsDraftCopyAfterClear`, `oversizedFileIsNotCached`. | Fill/clear cache; active-draft audio and AnkiDroid imported media remain untouched. |
| A35 | Static manifest/backup review; secret-redaction tests. | Inspect backup rules, local logs, crash diagnostics and APK; ordinary text in local logs is permitted, but no credentials/secrets in logs or build outputs and no raw private media in logs. Verify logs/private content are not uploaded (including through CCT or Google Cloud Logging) and outbound diagnostic payloads contain no user content or credentials; no app-added analytics. Check SDK telemetry against §15 `sdk-privacy`. |
| A36 | No passing baseline proof; Nix lock gap documented. | Run two clean builds in genuinely isolated Nix builders and compare unsigned universal APK hashes/bytes; warm Gradle cache is not acceptable evidence. |
| A37 | No JVM test can establish this. | Test 200% font, TalkBack, light/dark, keyboard/IME, scrolling, rotation, contrast and nonoverlapping ≥48dp actions on Android. Capture each screen/theme; inspect semantics, not just PNGs. |
| A38 | Manifest intent filters in `AndroidManifest.xml`; share driver. | Share Japanese text from another app and invoke selected-text `PROCESS_TEXT`. Verify Type-mode text only; no guessed target/send; decline replacement preserves existing draft. |
| A39 | `AnkiFieldContractTest.mapsRequiredFieldsByNameAndLeavesExtrasEmpty` / `rejectsMissingOrRepeatedRequiredFields`. | Configure reordered required fields + extra field and missing-field model in disposable AnkiDroid; map by name; extras empty; missing fields block. |
| A40 | `AnkiFieldContractTest` duplicate-check fail-closed cases; `SubmissionRecoveryTest`. | Retry duplicate or incomplete-check state repeatedly; no override/insert; check result never treated as permission to bypass policy. |
| A41 | OCR candidate layout unit tests only cover ranking/geometry, not recognizer behavior. | Share/capture a two-region fixture; choose one; one-region fixture must proceed without chooser. No real Japanese OCR image is currently supplied; add/hash one before claiming execution. |
| A42 | `ReviewCard` UI test if added; target span/morphology tests. | Open Edit word, choose contiguous sentence tokens (no free-text lemma/reading); selecting a new target invalidates/re-resolves vocabulary-dependent data. |
| A43 | Same source lookup as A12; `PitchAccentTest`. | No pitch diagram for unknown pitch; neutral mark and sendability confirmed in actual Review card. |
| A44 | `TranslationServiceTest` local/cloud state tests. | Exercise Azure result and local fallback; only local carries mark; saved manual edit clears it; TTS voice availability does not change translation. |
| A45 | Draft/tag preference tests where present. | Fresh install first card uses Settings seed; after sending modified tags, next card uses exactly sent tags. |
| A46 | Preference persistence tests where present. | Switch Photo/Type, close and relaunch; chosen mode is restored. |
| A47 | Manifest image `SEND` filter; OCR candidate tests. | Share one image; same OCR/ambiguity path as Photo; original image linked by default and can be separated later. Use one-image fixture; verify actual image URI permission handling. |
| A48 | `ManualNoteDraftTest.typedVocabularyWithoutDictionaryEntryCannotSendOrExportPitch`. | Select no-match span initially and via Edit word; preserve sentence/image/span; initial Target vs Edit word Review layering follows §4.10; no manual vocab/adjacent expansion; Send disabled. |
| A49 | `SubmissionRecoveryTest` and error-state unit tests. | Cause a recoverable non-submission error; saved draft retained and Return to draft works. Confirm uncertain insertion never exposes generic blind Retry. |
| A50 | Compose/UI instrumentation. | From both capture modes, Settings is in mode row outside status bar; set model/deck/Azure before sentence. No Settings entry on Review. |
| A51 | Submission recovery/presentation state tests. | Capture Sending, Success, Duplicate, and Uncertain. Review card/Notes remain unchanged under outcomes; only bottom action area changes until confirmed success reset. |
| A52 | Audio/translation unit presentation tests. | First Review render while online fields pending uses neutral spinner in place of value/icon; local furigana/definition/pitch should not show routine spinner. |
| A53 | `AnkiDroidGateway` URI/source tests if present. | On pinned AnkiDroid, Open in AnkiDroid opens browser filtered to `nid:<id>`; absent/unsupported handler leaves draft and shows locator. Source evidence alone is not pass. |
| A54 | Compose/UI instrumentation. | Settings reachable only from Capture; all Review states have no Settings/deck/model display. Verify both Photo and Type. |
| A55 | Compose/UI instrumentation. | Tap each explanatory mark; popover is row-associated/dismissible, does not cover Send/edit controls, and remains reachable on scroll. |
| A56 | State/save tests + Compose/UI instrumentation. | Edit image/word/definition/tags/Notes over current card; discrete choices autosave, text editors expose Done, Back preserves edits and full draft. |
| A57 | `AzureSpeechSynthesizerTest`, `TranslationServiceTest`. | Validate Speech and Translator separately with test key/resource; remove credentials after. Translation local model must be independent of offline TTS voice. No credentials: mark external service part BLOCKED. |
| A58 | `PitchAccentTest` and presentation tests. | Long reading, grayscale and TalkBack; all morae accessible, neutral check/outline and tail/drop distinguish selection/drop without color reliance. |
| A59 | Not executable until §15 `phonetic-pitch-source` resolved. | Until then assert all dots filled and no fabricated marks; do not report nasal test as passed. |
| A60 | Emulator rotation setting and `Photo` mode | Rotate the AVD; confirm locked layout and only mode labels/settings glyph counter-rotate; frame/shutter/thumbnail fixed. Use emulator capture; physical-device test is optional. |
| A61 | Asset/vector scan + accessibility test. | Verify icons are inlined Material Symbols vectors, labels on controls rather than glyph, font-size sizing, TalkBack names/states at 200%. |
| A62 | Pixel measurement using emulator screenshot and UI layout. | Verify CameraX preview flush to both edges with no side rounding/margins; only mode/shutter rows bound top/bottom. Do not infer OCR correctness from preview. |
| A63 | Realistic Japanese OCR image fixtures; fresh install with no network | Inject copyright-safe signs/menu/book pages/screenshots with known transcriptions through emulator camera/image input; log accuracy, model/app size, permissions/network trace, terms, privacy, and API/ABI. Physical-camera testing is optional. |
| A64 | `TranslationServiceTest` controlled backend tests. | Force pre-download failure and Retry; complete model download; then offline translation on short mined text; record quality/privacy/size and prove TTS-independent behavior. |
| A65 | Inspect pinned resolved `material3` dependency and compile/UI tests. | Verify stable Expressive APIs or explicitly approved experimental APIs; capture components/interaction states. No inference from static HTML mockups. |
| A66 | `ManualNoteDraftTest` / audio state tests; UI regression tests. | Incorrect automatic reading/completed audio cannot be independently corrected/removed/regenerated; relevant source edit still invalidates; media-import failure retains explicit retry/remove recovery. |
| A67 | Submission destination-assignment/recovery tests. | Simulate insertion accepted but destination assignment incomplete. No recovery card move and no second insertion; retain pending state; after manual user handling, read-only recheck confirms outcome. |

## 5. Mockup-to-capture matrix

There are 34 PNG/HTML reference pairs in `mockup/screens/` (33 portrait references plus `06b` rotated capture); `00-pitch-component` is a component reference, not a navigable app screen. Each PNG is generated from its same-basename HTML using `./mockup/render.sh`; preserve the current PNGs as goldens and compare structure/spacing/color, not just pixel diff. `mockup/mockup.html`, `mockup/REVIEW.md`, `mockup/shared.css`, and `docs/ui-plan.md` explain states/tokens and known deviations. Capture app PNGs to `.tmp-nix/screenshots/<id>-<state>-<theme>.png`, keep matching UI dump/logcat, and compare against `mockup/screens/<id>-*.png`.

| Mockup reference | SPEC / matrix | Reproducible state and evidence |
|---|---|---|
| `00-pitch-component` | §5.5, A11–A14/A58–A59 | Capture compact Review diagram and full Edit word diagram for `あいの手`; compare these separately to compact/full reference. Nasal marks remain all-filled until source gate closes. |
| `01-setup-not-installed` | §4.8, A26 | Launch on AVD with AnkiDroid absent; capture actionable install/setup state. |
| `02-setup-permission` | §4.8/A26 | Install official AnkiDroid in disposable AVD, start supported API permission flow, capture request/denial/recovery state. |
| `03-shared-text-received` | §4.8, A38 | `share-text '食べた'`; choose app; sentence arrives in Type-mode form, no target guessed. |
| `04-shared-image-received` | §4.8, A41/A47 | Share one Japanese single-block OCR image; linked image retained and OCR runs. |
| `05-draft-restore-prompt` | §4.8/§10.1, A25 | Create nonempty draft, invoke incoming share that would replace it; capture prompt; decline and verify draft survives. |
| `06-capture-photo` | §4.2, A01/A60/A62 | Allow camera, Photo selected; capture portrait viewfinder/shutter; wait for dictionary init first. Camera feed is synthetic on emulator. |
| `06b-capture-rotated` | §4.2, A60 | Rotate device/system to landscape during Photo; screen stays locked and required glyphs counter-rotate. Physical test preferred. |
| `07-capture-type` | §4.2, A46 | Switch to Type, enter `食べた`; show independent optional image slot; relaunch to test remembered mode. |
| `08-ocr-ambiguity-sheet` | §4.2, A41/A47 | Use two-block Japanese image; chooser overlays Photo with both regions; select one and continue. No checked-in photo fixture yet. |
| `09-target-text` | §4.3–§4.4, A03–A05 | Use `食べた`, then `猫と猫` second occurrence; editable text + tokens remain together. |
| `10-dictionary-ambiguity-sheet` | §4.3/A06 | Select `人気`; confirm entry from sheet while Target (or Edit word per §4.1) remains underneath. |
| `11-dictionary-lookup-failed` | §4.10, A05/A48 | Select a no-entry span; preserve Target or Review according to entry route; offer edit/reselect only. |
| `12-generic-error-sheet` | §4.10, A49 | Inject safe non-submission failure; show preserved work and Return to draft. Never inject uncertain insertion with generic Retry. |
| `13-review-card` | §4.5/A03–A05 | Resolve `食べた`; confirm image optional, highlighted sentence, vocabulary ruby/pitch, definition, translation, Notes row, tags, Send. |
| `14-review-card-generating` | §4.1/A52 | Hold online translation/audio requests pending; neutral spinner replaces value/icon; local fields populated. |
| `15-review-card-flags` | §4.1/A13/A44 | Use `あいの手` alternative pitch + local translation fallback; two separate neutral explanation/edit icons. |
| `16-flag-explainer` | §4.1/A55 | Tap pitch explanation mark; capture inline row popover without covering Send or pencil. |
| `17-sending` | §4.5/§9.4/A30/A51 | Use controlled slow provider fake to freeze submission; card and empty Notes remain legible, Send disabled with spinner. |
| `18-success` | §4.5/§9.4/A32/A45/A51 | Successful disposable AnkiDroid insert; capture brief same-card confirmation, then blank capture with last mode/deck/tags. |
| `19-duplicate-blocked` | §9.3/A27/A51/A53 | Seed exact sentence+lemma in selected deck; blocked card remains frozen; Open in AnkiDroid targets existing note. |
| `20-send-uncertain` | §9.5/A30/A51 | Fake provider returns uncertain insert, then unresolved reconciliation; capture Checking first (no buttons), then unresolved actions. Never risk real insertion for this screenshot. |
| `21-change-image-sheet` | §4.5/§7.1/A21 | Open Image edit on resolved review card; swap/remove image, sentence unaffected; verify full frame, not crop. |
| `22-edit-word-sheet-single` | §4.5/A42 | Open Edit word on `食べた`; sentence tokens and one pitch match, no typed lemma/reading field. |
| `23-edit-word-sheet-multi` | §5.5/A13 | Open Edit word on `あいの手`; show only listed patterns 3/4 with full-scale mora kana and neutral selection. |
| `24-edit-definition-sheet` | §4.5/§5.3/A07 | Choose multiple senses, edit combined definition, Done; original Review remains under sheet. |
| `25-add-notes-inline` | §4.5/§7.3/A56 | Expand always-present Notes row, enter plain multiline text, Done/Back; keep line breaks safely. |
| `26-add-tag-sheet` | §4.5/§7.4/A45 | Load tags from disposable AnkiDroid; toggle existing `JLPT::N4`, add new tag; selected tag tap removes. |
| `27-settings-main` | §4.9/A50/A54 | Enter Settings from capture only; inspect model/deck, seed tags, credentials/voice/cache, Sources & licenses at bottom. |
| `28-settings-note-type-picker` | §4.8–§4.9/A39/A50 | Choose existing note type; validate required names, reordered/extras, missing-field behavior. |
| `29-settings-deck-picker` | §4.9/A28/A50 | Choose existing deck; delete remembered deck and verify no silent substitution. |
| `30-settings-azure` | §4.9/§7.2/A57 | Runtime key/region/voice/rate setup; validate Speech and Translator separately. Do not capture key or secret in screenshots/logs. |
| `31-settings-ondevice` | §4.9/§6.4/A17/A57 | Discover installed offline-capable Japanese TTS engine/file-output support; no engine/voice must be described clearly. |
| `32-settings-licenses` | §4.9/§12.4/A35 | Scroll to persistent Sources & licenses; check required notices and data provenance. |

For each reachable app screen capture both light/dark when theme-dependent, using a stable post-layout state and no keyboard unless the reference calls for it. Store screenshots, UI hierarchy, logcat, and a short result row beside the run. Screens whose state depends on AnkiDroid/provider/cloud errors need controlled test doubles or explicit test setup; do not invent hidden production debug controls. Use real app flows for layout acceptance, unit/instrumentation fakes only for deterministic exceptional states.

## 6. Execution order and exit criteria

1. Confirm checkout revision and no concurrent source/build writes; run `git status --short --branch`.
2. Run Nix core check, all Gradle unit tests, and debug build; record actual test report totals and warnings.
3. Build/boot AVD, install exact APK, grant permissions, wait for UniDic extraction, verify API/ABI and no app crash.
4. Run capture/target/dictionary/review/edit/send scenarios with the text and disposable Anki fixtures above; capture all mockup-backed states and accessibility/theme variants.
5. Run offline/OCR/translation/audio/image gates with controlled network/provider failures using emulator inputs and test engines; run accessibility/TalkBack checks in the emulator and provision pinned emulator images for the full API/ABI release matrix.
6. Verify AnkiDroid exported note contents, deck, media, tags, duplicate scope, and recovery states from the collection itself.
7. Run reproducibility builds only when Nix Gradle dependency lock and clean builders are ready; sign only outside Nix/repository.
8. Report every A01–A67 and mockup row PASS / FAIL / BLOCKED with evidence. Unprovisioned API/ABI, missing photo fixtures, unavailable provider test doubles, and unresolved §15 gates remain visible blockers.
