# v0.3 final acceptance — independent verification

Candidate/integrated revision: `e9554d6` on `main` (parent `0cc4794` + acceptance commits `285ccef`,
`e9554d6`; `c318783`/`810af63` are documentation only). Built in the **fresh clone** of `main` advanced to `e9554d6`
at `/srv/hermes/.hermes/profiles/sysadmin/cache/scratch/v03-clone` (working tree clean before the build).

Environment: NixOS, `nix develop` (JDK 17.0.20.1, Gradle 8.14.4, Android SDK build-tools 36.0.0,
platform 37.0), API-37.0 `google_apis` **x86_64** AVD (`japanese-anki-pixel7-api37`, headless,
1080x2400), AnkiDroid **2.24.0** (full-universal) installed from the official v2.24.0 GitHub release.

Legend: **PASS** = executed on the delivered revision and observed; **PARTIAL** = part executed, the
remainder named; **NOT RUN** = not executed (reason given). A build or a unit test is not an
acceptance pass by itself.

## What changed during acceptance

1. **Review sentence ruby rendered illegibly (defect fixed).** The sentence drew ruby runs as
   `AnnotatedString` inline content with a placeholder width of *N em* per run. Any ruby run wider
   than its base (新しい / あたらしい, 喫茶 / きっさ) overflowed and printed over the next run: `は`
   over `友だち`, `と` over `新`, `喫 店` over each other, `行`/`た` split across lines. Fixed in
   `app/src/main/java/dev/pierre/japaneseanki/ui/ReviewCard.kt` by rendering the sentence as a
   `FlowRow` of ruby/plain runs, where each run owns its intrinsic width and wrapping happens
   between runs; unresolved kanji keep a local dotted underline.
   After: `screens/final/review-light.png`, `review-dark.png`, `review-font-scale-200.png`,
   `review-landscape.png`.
2. **Debug signing identity was not stable (defect fixed).** Two builds produced two different
   `CN=Android Debug` certificates because AGP generates a throwaway `debug.keystore` per
   `ANDROID_USER_HOME`; the candidate APK had been signed with a third key, so it could not update
   an installed v0.2 build. `app/build.gradle.kts` now takes `JAPANESE_ANKI_DEBUG_KEYSTORE`, and the
   delivered APK is signed with the same certificate as the delivered v0.2 APK
   (`1446fc8e…635d`, keystore `/srv/hermes/.android/debug.keystore`, outside the repository).
3. **versionCode was not monotonic (defect fixed).** The delivered v0.2 APK is `versionCode 2`, and
   the candidate v0.3 was also `versionCode 2` — an in-place update would carry an equal, not newer,
   code. `versionCode` is now `3` for `versionName 0.3`; the in-place update from the installed
   `versionCode 2` build was executed on the emulator (`lastUpdateTime` advanced, draft preserved).

## Independent finding about the previous candidate

The candidate APK `fb2eb0fc…296b60d` declares `minSdkVersion 37`, and the only AVD that existed in
the repository was `japanese-anki-pixel7-api35` (API 35). Installing that APK there fails:

```
adb -s emulator-5554 install -r -g releases/v0.3/japanese-anki-0.3-debug.apk
Failure [INSTALL_FAILED_OLDER_SDK: Requires newer sdk version #37 (current version is #35)]
```

So the candidate's R02 claim ("API-37 emulator") could not have come from the APK it shipped. For
this acceptance an API-37.0 system image was provisioned
(`system-images;android-37.0;google_apis;x86_64`) and every runtime row below was re-run on it with
the APK built from `285ccef`. Emulator: `ro.build.version.sdk=37`, `release=17`, `abi=x86_64`
(`evidence/device-runtime.txt`).

## Build and packaging

| ID | Result | Evidence |
|---|---|---|
| R01 | **PASS** | Fresh clone of `e9554d6`: `nix develop --command ./scripts/test-core.sh` → `OK (62 tests)`; `gradle :app:testDebugUnitTest` → 110 tests, 0 failures, 0 errors; `gradle :app:assembleDebug` → BUILD SUCCESSFUL. APK badging: `versionCode=3 versionName=0.3 minSdk=37 targetSdk=37 compileSdk=37`, `native-code: arm64-v8a x86_64`; `lib/arm64-v8a/*.so` ELF `e_machine=b7` (AArch64), `lib/x86_64/*.so` `e_machine=3e`. `evidence/apk-verification.txt`, `evidence/unit-tests.txt`, `evidence/core-tests.txt`. Note: the flake's `nix build .#apk` derivation was **not** exercised (its fixed-output Gradle dependency cache is not populated for this change set); the documented clean-checkout path is Gradle inside `nix develop`. |
| R02 | **PARTIAL** | On the API-37 x86_64 AVD the delivered APK installs, starts, unpacks UniDic (725 MB, progress shown), opens the dictionary + AJT pitch index, tokenizes 昨日は友だちと新しい喫茶店に行った。, resolves JMdict senses, downloads and plays the NHK recording, OCRs a fixture photo (7 regions → excerpt sheet), renders Review, survives rotation / process death / in-place APK update, and runs in both themes at 100 % and 200 % font. **NOT RUN:** camera capture (headless AVD has no usable camera surface) and any ARM64 runtime claim. |

## Workflow

| ID | Result | Evidence |
|---|---|---|
| R03 | **NOT RUN** | No airplane-mode pass. The credential-missing path is covered by R15. |
| R04 | **NOT RUN** | Landscape *capture* → portrait continuation needs a camera capture, which the headless AVD cannot produce. Draft survives rotation (R21). |
| R05 | **PASS** | Photo picker → bundled ML Kit Japanese OCR produced `店のコーヒーはとてもおいしかった。` and, for the full fixture, 7 readable excerpts (`5:23`, `▲10.0.2.2:8765/fixture.htm`, `昨日は友達と新`, `しい喫茶店に行`, `った。`, …) in the "Choose the text region" sheet. `screens/final/photo-ocr-region.png`, `photo-ocr-multi-region-excerpts.png`. Photos stayed local. |
| R06 | **PARTIAL** | Plain-text `ACTION_SEND` opened Type preparation with the sentence and did not guess a target (PASS). Photo attach on a non-empty draft raised **"Replace current draft?"** with Keep draft / Replace (PASS). **Image `ACTION_SEND` NOT VERIFIED:** `am start --eu android.intent.extra.STREAM content://media/external/images/media/28 --grant-read-uri-permission` reaches the app (it switches to Photo), but the app then gets `SecurityException: dev.pierre.japaneseanki has no access to content://media/external/images/media/28` — the adb-injected URI grant is not delivered by the platform, so no image is read and the draft is kept. That is a harness limitation, not proof that a real gallery share fails, and not proof that it works. Typed input with a Japanese IME was not exercised. |
| R07 | **PASS** | Text share `昨日は友だちと喫茶店でケーキを食べた。` → token 食べ selected → `Selected: 食べ → 食べる`, vocabulary `食べる · たべる`, sense "to eat / Ichidan verb, transitive verb", pitch diagram present, Continue enabled. Inflected surface stays in the sentence while the lemma resolves; no adjacent-word guess. `screens/final/inflected-target-resolves-lemma.png`. Duplicate-occurrence and Unicode-boundary cases remain unit-test only. |
| R08 | **PASS** | Tapping 喫茶 on the same revision opened **"Choose dictionary entry"** with `喫茶 · きっさ` and `喫茶 · きっちゃ`, both glossed, before any generation; the first sense of the chosen entry is checked by default. `screens/final/entry-ambiguity-chooser.png`. |
| R09 | **PASS** | Choosing 喫茶/きっさ shows `No AJT pitch record matches this entry/reading, so the note cannot be mined.`, keeps the selection, and **Continue to generate** is gated on `pitch != null && selectedDictionaryEntry != null` (so it cannot start generation or a paid request for that word). `screens/final/pitch-miss-blocks-word.png`. |
| R10 | **PARTIAL** | Sentence ruby renders correctly for きのう/ともだち/あたらしい/きっさ/てん/いっ with the target highlighted after the fix (screens). Kana-only spans are unannotated. Targeted reading correction NOT RUN on device. |
| R11 | **PARTIAL** | `PitchAccentTest` golden fixture (悪逆/アクギャク, accent 0, nasal キ) passes; `docs/v0.3-reference-template/parser-test.mjs` → 5/5 (nasal handakuten kept as annotation, devoicing inherited, contracted kana, accent ≥ 10, odaka vs nakadaka). Final rendering inside AnkiDroid NOT RUN (no export). |
| R12 | **PASS** | Real NHK-2016 index fetched; recording for 新しい/あたらしい downloaded, validated and played (logcat: `AudioTrack: stop(16): called with 44808 frames delivered`). No synthesized-word fallback exists in the build. |
| R13 | **NOT RUN** | Live Google listing/synthesis/audition (no credential). Outbound request serialization, hint payload, rejection-without-fallback and cache identity are covered by `speech/GoogleSpeechProviderTest`, `SpeechHintBuilderTest`. |
| R14 | **NOT RUN** | Live OpenRouter translation (no credential). The missing-key state is actionable and blocks Send. |
| R15 | **PASS** | With no credentials: sentence audio reports "Add your Google Cloud API key and choose a sentence voice in Settings." with Retry; translation reports its own OpenRouter error with Retry; Send stays disabled; word audio still completes; the draft survives Back, rotation and process death; no provider/model substitution. `screens/final/sentence-audio-unavailable.png`. |
| R16 | **PASS** | `am force-stop` + relaunch, and an in-place APK update, both restored the draft and its completed results; no billable work restarted automatically. Race/obsolete-completion behaviour is unit-tested only. |
| R17 | **PARTIAL** | `ImageMediaProcessorTest` covers orientation, 1080 px long edge, no upscale, WebP 75 and metadata stripping; OCR ran before downscaling. Runtime WebP export and AnkiDroid rendering NOT RUN (export blocked, R24). |
| R18 | **PASS** | Send is disabled while translation and sentence audio are incomplete; the failure is named per field with Retry; no partial-note path and no silent media omission. |
| R19 | **NOT RUN** | Destructive-edit confirmation after manual corrections not exercised on device. |
| R20 | **PARTIAL** | Composed card (sentence + ruby + highlighted target, vocabulary + pitch diagram + playback, definition, translation state, notes, tags, Send) renders correctly in both themes and after the ruby fix; word playback really plays. Edit bottom sheets were not exercised this pass. |
| R21 | **PARTIAL** | Light + dark, 200 % font scale, portrait/landscape, Back navigation, 48 dp controls and content descriptions all behave with no clipping. TalkBack walkthrough, reduced-motion setting and contrast measurement NOT RUN. |
| R22 | **PASS** | AnkiDroid 2.24.0 detected, API permission held, note-type list read from the real collection (Basic, Basic (and reversed card), Basic (optional reversed card), Basic (type in the answer), Cloze, Image Occlusion). Selecting a model without the contract listed all 14 missing fields by name and did not substitute a destination. `screens/final/ankidroid-missing-fields.png`. Reordered/extra/trailing-empty fields against a real reference note type NOT RUN. |
| R23 | **NOT RUN** | Needs an existing note with the same sentence+lemma; no note was inserted. Query-failure blocking is unit-tested. |
| R24 | **NOT RUN** | Successful export needs sentence audio + translation (user credentials). No note was sent, so fields, media, tags and destination are unverified against AnkiDroid. |
| R25 | **NOT RUN** | Same blocker as R24. Serialization/receipt/recovery logic is unit-tested (`SubmissionRecoveryTest`). |
| R26 | **PARTIAL** | Settings shows reusable audio cache size and a clear action, and states the draft's audio is kept outside it; storage-exhaustion and interrupted-write paths NOT fault-injected. |
| R27 | **PARTIAL** | Code review only: OCR is bundled/on-device, photos never leave the device, Google receives sentence + hints, OpenRouter receives sentence + meaning, credentials are Keystore-encrypted and excluded from backup, the HTTP transport rejects redirects, no secret/media logging. No capture proxy run this pass. |
| R28 | **PARTIAL** | Settings exposes Sentence voice + Refresh voices with loading/empty/error/cached handling and the voice-change invalidation note; with no key the controls are correctly disabled. Live `GET /v1/voices?languageCode=ja-JP` NOT RUN. |
| R29 | **PASS** | `FakeSentenceSpeechProvider` + injectable transport tests cover listing/filtering, structured hint building, actual request serialization, audio validation, auth/quota/timeout/cancel mapping and hint-aware cache identity without Compose or credentials. |

## Summary

PASS: R01, R05, R07, R08, R09, R12, R15, R16, R18, R22, R29.
PARTIAL: R02, R06, R10, R11, R17, R20, R21, R26, R27, R28.
NOT RUN (credential-gated): R13, R14, R24, R25, R28-live.
NOT RUN (not exercised in this pass): R03, R04, R19, R23.

## Known defects not fixed in this pass

1. **Stale vocabulary display after an unresolved entry choice.** Tap a target whose word has several
   dictionary entries (喫茶 → きっさ/きっちゃ), then dismiss the "Choose dictionary entry" sheet: the
   screen keeps showing `Selected: 喫茶 → 食べる` with the previous word's definition and pitch until an
   entry is chosen. Generation is still blocked (`selectedDictionaryEntry == null` gates
   "Continue to generate"), so no wrong note can be exported, but the display is misleading.
   `screens/final/pitch-miss-blocks-word.png` shows the clean path; the stale case was observed on the
   same device and is reproducible with the steps above.
2. **Settings is one scrollable dialog**, not a settings screen; Google/OpenRouter/cache controls sit
   below the fold.
3. **First NHK index fetch (37 MB) has no progress indicator.**
4. **After process death** the sentence-audio failure reason is behind the info icon rather than
   restored verbatim.
5. `flake.nix` composes only an `android-35` emulator image; the API-37 AVD was provisioned by hand
   (`evidence/emulator-provisioning.md`).

The app is delivered as an installable APK with the local half of the journey verified on a real
API-37 x86_64 emulator. It is **not** a verified end-to-end mining-to-AnkiDroid release: no note has
been exported from this revision, because sentence audio and translation both need the user's keys.
See `LIVE-VALIDATION.md`.

## Exact commands

```
# fresh clone, working tree clean
git clone /srv/hermes/workspace/japanese-anki-app v03-clone
git -C v03-clone rev-parse HEAD            # 285ccef

cd v03-clone
nix develop --command ./scripts/test-core.sh
  Time: 0.174
  OK (62 tests)

nix develop --command bash -c 'export JAPANESE_ANKI_DEBUG_KEYSTORE=/srv/hermes/.android/debug.keystore; \
  gradle :app:testDebugUnitTest -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2'
  BUILD SUCCESSFUL — 110 tests, 0 failures

nix develop --command bash -c 'export JAPANESE_ANKI_DEBUG_KEYSTORE=/srv/hermes/.android/debug.keystore; \
  gradle :app:assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2'
  BUILD SUCCESSFUL — app/build/outputs/apk/debug/app-debug.apk

nix shell nixpkgs#nodejs -c node docs/v0.3-reference-template/parser-test.mjs
  5/5 parser fixtures passed

# runtime
adb -s emulator-5556 install -r -g japanese-anki-0.3-debug.apk
adb -s emulator-5556 shell am start -n dev.pierre.japaneseanki/.MainActivity
```

Artifact: `japanese-anki-0.3-debug.apk`, 546 904 852 bytes,
sha256 `2cc3c053003af15de44050f350a3758d0c25155910b31a83ff0d6beedf37a15e` (`SHA256SUMS.txt`).
