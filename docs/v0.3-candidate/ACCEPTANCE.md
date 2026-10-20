# v0.3 acceptance record

Candidate: `releases/v0.3/japanese-anki-0.3-debug.apk` (SHA-256 in `SHA256SUMS.txt`,
`fb2eb0fc…296b60d`), built from a **fresh clone** of branch
`japanese-anki-app/t_845e4675-implement-v0.3-complete-mining-workflow` at commit `d11bbb5`.
The shipped APK was then reinstalled on the emulator and launched against the existing draft
(`screens/47-release-apk-running.png`).

Environment: NixOS host, JDK 17 + Gradle 8.14.4 + Android SDK 37 inside `nix develop`; API-37
`google_apis` x86_64 AVD (`api37`, headless, 1080x2400), AnkiDroid 2.24.0 installed from the
image; `com.ichi2.anki` collection created fresh for this pass.

Legend: **PASS** = executed and observed; **FAIL** = executed with a defect; **PARTIAL** = the
available part executed, the rest named; **NOT RUN** = not executed (reason given). A successful
build is not counted as acceptance.

## Build and packaging

| ID | Result | Evidence |
|---|---|---|
| R01 | **PARTIAL** | Clean clone of `d11bbb5` in `nix develop`: `./scripts/test-core.sh` → `OK (62 tests)`. Worktree: `gradle :app:testDebugUnitTest` → 110 tests, 0 failures; `gradle :app:assembleDebug` → BUILD SUCCESSFUL. `nix build .#checks.x86_64-linux.core` → dictionary/UniDic self-checks pass (see log line below). APK badging: versionName `0.3`, versionCode `2`, minSdk/targetSdk/compileSdk 37, `native-code: 'arm64-v8a' 'x86_64'`. Every `lib/arm64-v8a/*.so` is `ELF64 AArch64`. Not run: `nix build .#apk` (Gradle dependency-lock/mitm path is not populated for this change set); the Gradle commands above are the verified build. |
| R02 | **PARTIAL** | Launch on the API-37 x86_64 AVD, UniDic unpack (725 MB, progress shown), dictionary + pitch index open, MeCab tokenization, ML Kit Japanese OCR on a fixture photo, NHK word recording download, Review rendering, rotation. Not run: on-device APK-level native linkage beyond the packaged file check, and ARM64 runtime (no ARM64 device; no claim made). |

## Workflow

| ID | Result | Evidence |
|---|---|---|
| R03 | **NOT RUN** | No airplane-mode / no-network pass. The credential-missing path was exercised instead (see R15). |
| R04 | **PARTIAL** | Portrait→landscape→portrait rotation preserved the review draft and re-laid it out (`screens/32-landscape.png`). Landscape *capture* → portrait continuation was not run (camera preview is a blank surface on the headless AVD). |
| R05 | **PASS** | A fixture photo with Japanese text was picked through the system picker; OCR produced several line regions and the app showed the excerpt sheet ("Choose the text region", `screens/14-photo-ocr.png`). Single-region text populates the editor. |
| R06 | **PARTIAL** | Plain-text `ACTION_SEND` created a Type draft without guessing a target (`screens/05-text-share.png`, `26-journey-target.png`). The system picker attaches an image to a Type draft. FAIL: an image `ACTION_SEND` (content URI + read grant) was not picked up by the app in this pass; it left the capture screen unchanged (`screens/18-shared-image.png`). Replacement confirmation exists but was not re-run after that. |
| R07 | **NOT RUN** | Only a dictionary-word target (`新しい`) was mined end to end. Inflected/duplicated/Unicode-boundary cases are covered by unit tests (`TargetSpanTest`, `JapaneseMorphologyTest`) but not by a device pass. |
| R08 | **PASS** | Two JMdict entries for 喫茶 (きっさ / きっちゃ) produced a chooser; senses are listed with part of speech and one selected by default (`screens/07-entry-chosen.png`, `27-selected.png`). |
| R09 | **PASS** | 喫茶 has no AJT pitch record in the pinned data; the app says "No AJT pitch record matches this entry/reading, so the note cannot be mined", keeps the selection, and (after this pass) disables **Continue to generate**. |
| R10 | **PARTIAL** | Sentence ruby rendered for きのう/ともだち/あたらしい/きっさ/てん/いっ with the target highlighted; kana-only spans are not annotated. Targeted reading correction was not exercised on device; unit tests cover the serializer. |
| R11 | **PARTIAL** | Golden fixture 悪逆/アクギャク accent 0 passes in `PitchAccentTest` and `AjtPitchHtml` keeps the nested nasal handakuten. Corrected reference template parses it with 4 morae and a nasal mark (`docs/v0.3-reference-template/parser-test.mjs`, 5/5). NOT RUN: final rendering in AnkiDroid (no note could be sent). |
| R12 | **PASS** | Real NHK-2016 index (37 MB) downloaded; the recording for 新しい/あたらしい was fetched, validated and is playable in Review (`screens/30-review-audio.png`). No synthesized fallback exists in the build. |
| R13 | **NOT RUN** | Live Google listing/synthesis/audition. Unit tests cover the outbound JSON (hints, `PHONETIC_ENCODING_JAPANESE_YOMIGANA`, exact voice), rejection without fallback, and cache identity (`speech/GoogleSpeechProviderTest`, `SpeechHintBuilderTest`). No claim of cloud audio quality. |
| R14 | **NOT RUN** | Live OpenRouter translation. The missing-key state is clear and blocks Send ("Add your OpenRouter API key in Settings.", `screens/28-review-generating.png`); the model is pinned in code. Manual correction/retry paths are unit-tested only. |
| R15 | **PASS** | With no credentials: sentence audio reports unavailable, translation reports its own actionable error, word audio still completes, Send stays disabled, draft survives back-navigation/restart. No provider or model switch. |
| R16 | **PARTIAL** | Process restart and APK reinstall restored the draft and its results (Review re-rendered with ruby, pitch and word audio). Billable work was not restarted automatically. Race/obsolete-completion behaviour is unit-tested (`AudioGenerationTest`) but not fault-injected on device. |
| R17 | **PARTIAL** | `ImageMediaProcessorTest` covers orientation, 1080 long edge, no upscale, WebP 75 and metadata stripping. The runtime WebP was produced for the picked photo but its rendering inside AnkiDroid is NOT RUN (export blocked). |
| R18 | **PASS** | Review shows "Still needed · A completed English translation is required." and Send is disabled; no partial-note path. Attached-image readiness now blocks Send while processing (no "send without image" shortcut). |
| R19 | **NOT RUN** | Destructive-edit confirmation after manual corrections was not exercised on device; serializer/staleness rules are unit-tested. |
| R20 | **PARTIAL** | Composed card: sentence with ruby + highlighted target, vocabulary with pitch diagram and playback, definition, translation, notes, tags, Send (`screens/30-review-audio.png`, `46-dark-review-final.png`). Empty image placeholder is no longer shown for a Type draft. Edit dialogs exist; only the image-remove path was checked (correctly disabled for a photo-origin draft). |
| R21 | **PARTIAL** | Light and dark themes both render; status-bar icon appearance now follows the theme (fixed in this pass); rotation works; 48 dp targets and content descriptions are present on the exercised controls. NOT RUN: 200% font scale, TalkBack walkthrough, reduced-motion setting. |
| R22 | **PASS** | AnkiDroid detection, API permission grant, note-type list (Basic/Cloze/…) and deck list load; selecting a note type without the contract fields prints the exact missing-field list and no destination is substituted (`screens/45-note-type-basic-errors.png`). |
| R23 | **NOT RUN** | Duplicate rule needs an existing note with the same sentence+lemma; no note was inserted. Query-failure blocking is unit-tested. |
| R24 | **NOT RUN** | Requires completed sentence audio + translation (credentials). No note was sent, so fields, media, tags and destination are unverified against AnkiDroid. |
| R25 | **NOT RUN** | Same reason as R24; recovery/serialization code is unit-tested (`SubmissionRecoveryTest`, 17 tests). |
| R26 | **PARTIAL** | Cache is bounded and draft audio lives outside it; Settings now reports cache size and clears it without touching drafts or AnkiDroid media. Storage-exhaustion and interrupted-write paths were not fault-injected. |
| R27 | **PARTIAL** | Code review: photos never leave the device (OCR is bundled ML Kit), Google receives sentence + hints only, OpenRouter receives sentence + meaning, credentials are Keystore-encrypted, excluded from backup, never logged, and HTTP follows no redirects (custom transport rejects them). Not instrumented with a capture proxy in this pass. |
| R28 | **PARTIAL** | Settings implements loading/empty/error/refresh/cached states, an explicit saved voice, a "saved voice no longer listed" warning, and voice-change invalidation of sentence audio only. Live `GET /v1/voices` is NOT RUN (no key); filtering and persistence are unit-tested. |
| R29 | **PASS** | `FakeSentenceSpeechProvider` + injectable transport tests cover listing/filtering, structured hint building, actual request serialization, audio validation, auth/quota/timeout/cancel mapping and hint-aware cache identity without Compose or credentials. |

## Exact commands and results

```
# clean clone of d11bbb5
nix develop --command ./scripts/test-core.sh
  OK (62 tests)

# worktree
gradle :app:testDebugUnitTest -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2
  BUILD SUCCESSFUL — 110 tests, 0 failures, 0 errors
gradle :app:assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2
  BUILD SUCCESSFUL — app-debug.apk

nix build .#checks.x86_64-linux.core --no-link --print-build-logs
  dictionary builder self-check passed
  bundled dictionary asset self-check passed
  UniDic runtime packager self-check passed

nix shell nixpkgs#nodejs -c node docs/v0.3-reference-template/parser-test.mjs
  5/5 parser fixtures passed

aapt2 dump badging japanese-anki-0.3-debug.apk
  versionCode='2' versionName='0.3' compileSdkVersion='37' minSdkVersion:'37' targetSdkVersion:'37'
  native-code: 'arm64-v8a' 'x86_64'
apksigner verify --print-certs …
  CN=Android Debug (debug identity, not a release key)

adb install -r -g …   → Success
adb shell dumpsys package dev.pierre.japaneseanki → primaryCpuAbi=x86_64, versionName=0.3
```

## Known defects / gaps

1. **No note was exported in this pass.** Sentence audio and translation need credentials, so
   R24/R25 and the AnkiDroid field/media rendering checks are NOT RUN. The export path itself is
   implemented and unit-tested; it has not been proven against a real collection.
2. **Replacement image on a photo-origin draft** clears `photoSourcePath` when the new image is
   attached without OCR. The image rule still holds (`draftOrigin` stays Photo), but "retry photo
   text reading" then opens the picker instead of re-reading the previous file.
3. **Review after process death** shows sentence audio as "unavailable" with no reason until
   the user presses Continue again; the previous failure message is not persisted.
4. **Settings is a dialog**, not a settings screen/list as §4.7 describes. It scrolls, but it is
   a single modal.
5. **NHK index is 37 MB** on first word-audio use, with no progress indicator; on a slow link the
   card shows "Generating Vocabulary audio" for a long time.
6. **Image share intent** was not accepted in this pass (R06 FAIL detail).
7. `nix build .#apk` is unverified; only the Gradle-in-`nix develop` path is proven.
8. Reference template must be installed by hand; the app never modifies note types.

## Live checks still required (user, after entering keys)

* Google `GET /v1/voices?languageCode=ja-JP` → Chirp 3 HD list, selection persistence, refresh
  and cached-offline behaviour with a real key.
* Chirp 3 HD synthesis auditions: ambiguous readings (今日 きょう/こんにち), inflected targets,
  counters, kana-only sentence, reading correction → regenerated audio.
* OpenRouter translation of the same sentences; manual correction survives retry.
* One full Send into a disposable Anki collection, then check fields, WebP image, both audio
  files and the pitch diagram (nasal dot, no `°` column) in AnkiDroid.
