# v0.4 — expressive-flow UI acceptance record

Status: local acceptance run. Live Google services and AnkiDroid export were not exercised in this
run and are marked NOT RUN below. Nothing here is a claim of pixel parity with the study; the
composition was built from `mockup/expressive-flow` and compared screenshot by screenshot.

> **Post-acceptance tokenizer follow-up:** the selection-unit layer in `TokenGrouping.kt` resolves
> known defect 1 below. The Target row now groups `図書館` and `読みました`, and punctuation is
> rendered beside the last word but outside its selectable pill. The screenshots in this record
> remain the pre-follow-up v0.4 snapshot.

## Revision and environment

| | |
|---|---|
| Branch | `japanese-anki-app/t_647f8547-implement-the-expressive-flow-input-targ` |
| Implementation commit | `fa7f0fa58f4f46203e49fb6d1ade34dc37958dc3` |
| Evidence commit | the branch head, i.e. the commit containing this file (exact SHA in the task handoff) |
| Baseline | `0611796` merged into the task branch (`ee8e5b6`) |
| Build environment | `nix develop`, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.0.21, JDK 17 |
| Emulator | API 37 (`android-37.0;google_apis;x86_64`), serially `emulator-5558` |
| Viewports | 360×828 dp and 390×844 dp at density 320; text scale 1.0 and 2.0 |
| Fixture | `mockup/expressive-flow/assets/textbook-landscape.jpg` (900×675, landscape) |

## Commands

```
nix develop --command ./scripts/test-core.sh
nix develop --command bash -c 'gradle --no-daemon --console=plain \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest assembleDebug'
nix develop --command bash -c 'export JAPANESE_ANKI_DEBUG_KEYSTORE=/srv/hermes/.android/debug.keystore; \
  gradle --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" assembleDebug'
adb -s emulator-5558 install -r -g releases/v0.4/japanese-anki-0.4-debug.apk
```

## Test totals

| Suite | Result |
|---|---|
| Core (platform-independent, `scripts/test-core.sh`) | OK (113 tests) |
| Gradle unit tests (`testDebugUnitTest`) | 166 tests, 25 suites, 0 failures, 0 errors, 0 skipped |
| New UI coverage added by this task | `ui/FlowLayoutTest` (5: cover crop for the 1440×1080 and 900×675 fixtures, focal fraction clamping, OCR line focus), `ui/TargetSelectionTest` (5: span surface, extend/trim edges, blank-token skipping) |
| Existing UI coverage kept | `ui/CameraLayoutTest`, `ui/ReviewAudioPresentationTest`, `ui/ReviewSentencePresentationTest` |

## Runtime journey (fixture-driven, installed APK)

1. **Input (Photo).** Fresh install; UniDic unpacked with progress; camera preview live and centred
   in the rounded frame; gallery / shutter / Type row.
2. **Landscape photo.** Pushed the fixture to `Pictures/`, opened it through the system photo picker
   (gallery remains reachable from Input). On-device ML Kit OCR returned four regions:
   `図書館の午後`, `図書館で本を読みました。`, `窓の席に座りました。`, `机の上に本を開きました。`
   The excerpt sheet listed all four; choosing region 2 loaded it as the sentence (`12`).
3. **Target.** UniDic tokens `図書 / 館 / で / 本 / を / 読み / まし / た / 。`. Selecting `本` opened
   the entry sheet with two entries (`本・ほん` "book; volume; script" and `本・もと` "origin…");
   choosing `ほん` listed the entry's senses with sense 0 checked (`08`, `05`).
4. **Inflected target.** Selecting `読み` resolved the occurrence to the lemma `読む / よむ` and the
   meaning "to read" while the sentence surface stayed `読みました` (`09`, `10`). Entry ambiguity
   was offered again (`読み・よみ` vs `読む・よむ`).
5. **Pitch miss.** Selecting `図書` and the reading `としょ` produced
   "No AJT pitch record matches this entry/reading, so the note cannot be mined." plus
   "A source-listed pitch pattern is required to continue." with Continue disabled (`11`).
6. **Review.** Photo (144 dp, `ContentScale.Crop` focal-centred on the recognised line) → continuous
   Japanese with proportional ruby and the highlighted `本` → translation line **directly beneath the
   sentence** → vocabulary card (`本 / ほん`, pitch contour with 22 sp katakana labels, definition).
   Verified from the UI hierarchy: the sentence node, then the translation node, then the vocabulary
   card (`15`–`23`).
7. **Audio.** After the NHK index download the word control became "Play Word audio" (playable).
   Sentence audio and translation reported "Add your Google Cloud API key in Settings." — no key on
   this emulator, so no live synthesis or translation was attempted.
8. **Rotation.** Rotating either way preserved sentence, photo, target, entry, sense and translation
   attempt; the composition stays the same column, capped at 520 dp and centred (`23`).
9. **Keyboard/Back.** In Type mode the IME shows without covering Continue; system Back dismisses the
   IME and leaves the draft in place (`13`, `14`).
10. **Signed APK.** Uninstalled the throwaway-signed development build, installed
    `releases/v0.4/japanese-anki-0.4-debug.apk`, launch + `share-text` → Target → entry sheet smoke
    (`24`, `25`), `versionName=0.4`.
11. **Edit reveal.** The default Review is read-only; Edit adds the v0.3 correction affordances to
    the content itself: sentence-reading fix, editable translation, "Change word or reading" (word
    pills + vocabulary reading + Apply), "Edit meaning", notes and tags fields (`26`–`28`).
    Notes/tags/word/reading/definition editing, the definition-stale acknowledgement and the failed
    photo retry were carried over from v0.3 rather than dropped in the rewrite.

## Screenshot evidence

| File | State |
|---|---|
| `01`–`04` | Input light/dark at 360×828 and 390×844 |
| `05`–`07` | Target light/dark at 360×828 and 390×844 |
| `08` | Entry ambiguity for `本` (ほん / もと) |
| `09`–`10` | Inflected `読み` → `読む`; entry sheet and resolved Target |
| `11` | Pitch miss for `図書・としょ`, Continue blocked |
| `12` | OCR excerpt sheet over the landscape textbook photo |
| `13`–`14` | Type mode with IME; Back dismisses the IME, draft intact |
| `13b` | Type canvas without the IME (empty-state writing view) |
| `15`–`18` | Review light/dark at 360×828 and 390×844 |
| `19`–`21` | Review at 200% font, light/dark, and scrolled to vocabulary |
| `22` | Full-photo viewer, whole frame fitted |
| `23` | Rotated window: centred portrait column, draft preserved |
| `24`–`25` | Fresh install of the signed APK; share-text smoke to Target |
| `26`–`28` | Edit revealed: correction affordances, word/reading sheet, notes and tags fields |

## SPEC acceptance matrix

PASS = exercised in this run with evidence above. PARTIAL = the requirement has parts that were not
re-run here or that depend on an install/service this run did not have. NOT RUN = not exercised.

| ID | Scenario | Result | Note |
|---|---|---|---|
| R01 | Clean checkout in Nix | PASS | Core 113, Gradle 166, APK builds; `versionCode 4`, `compileSdk/targetSdk/minSdk 37`; `arm64-v8a` + `x86_64` present in the APK |
| R02 | API-37 x86_64 emulator | PASS | Startup, ML Kit OCR, UniDic, JMdict, pitch index and the full Input→Target→Review journey on the API-37 x86_64 AVD; no ARM64 runtime claim |
| R03 | Fresh install without network | PASS | Local OCR/typing/target/meaning work; missing key surfaces as a retained, explicit failure and the draft survives |
| R04 | Landscape source → portrait continuation | PASS | Landscape photo through the portrait composition; rotation preserved photo, sentence, target, entry and senses |
| R05 | OCR regions and textbook text | PASS | Four regions listed, one chosen, editable afterwards; photo stays local |
| R06 | Type and share inputs | PARTIAL | Typing, text share and photo attachment exercised; the replace-nonempty-draft confirmation was not re-run this session |
| R07 | Inflection, repeats, contiguous selection | PASS | `読み` → lemma `読む`; one occurrence; extend/trim controls on a single UTF-16 span |
| R08 | Ambiguous entries and senses | PASS | `本` and `図書` entry sheets; sense selection; no mixed readings |
| R09 | Local dataset miss | PASS | `図書・としょ` pitch miss blocks with a stated reason and keeps the draft |
| R10 | Furigana correctness/correction | PARTIAL | Proportional ruby and unresolved-kanji marking visible; per-span correction not re-run this session |
| R11 | AJT HTML pitch parity | NOT RUN | Visual/Anki-render parity not re-checked this run |
| R12 | Recorded pronunciation | PARTIAL | NHK word audio reached the playable state; audio quality not auditioned |
| R13 | Chirp 3 HD speech and hints | NOT RUN | No Google Cloud key on this emulator |
| R14 | Translation | NOT RUN | No Google Cloud key; failure path and Retry observed |
| R15 | Offline/auth/quota/timeout | PARTIAL | Missing-key failure is specific and retained with explicit Retry; quota/timeout paths not triggered |
| R16 | Generation race or process death | NOT RUN | Rotation was exercised; process death was not |
| R17 | Image processing | PARTIAL | The photo was processed and attached; encoded dimensions/bytes were not measured this run |
| R18 | Incomplete required result | PASS | Missing translation and missing pitch both keep Send unavailable with a stated reason |
| R19 | Source edit after manual correction | NOT RUN | Not exercised this session |
| R20 | Review and targeted Edit | PASS | Composed Review, translation directly beneath the sentence, Send reachable; edit sheets reachable |
| R21 | Material/accessibility/navigation | PARTIAL | Both themes, 200% font, 48 dp targets, insets, Back and rotation verified; TalkBack speech and motion settings not exercised |
| R22 | Model/deck/permission failures | NOT RUN | No AnkiDroid installed in this run |
| R23 | Duplicate rule | NOT RUN | Requires AnkiDroid |
| R24 | Successful export | NOT RUN | Requires AnkiDroid |
| R25 | Repeated/uncertain Send | NOT RUN | Requires AnkiDroid |
| R26 | Cache/storage failures | NOT RUN | Not exercised this session |
| R27 | Privacy and lookup diagnostics | PARTIAL | No image upload or secret logging observed; miss logging not inspected this run |
| R28 | Cloud voice selector | NOT RUN | No Google Cloud key |
| R29 | Modular speech boundary | PASS | Provider/transport unit tests present and green in the 166-test run |
| R30 | Shared Google Cloud setup | NOT RUN | No key entered this run |

## Deviations from the study (reported, not hidden)

1. **No separate Preparing screen.** Generation states appear inside Review (progress text, inline
   spinners, Retry) and the progress bar reads step 3 while work is pending. The study keeps
   Preparing at step 2. SPEC §4.4 requires one understandable generation state, which Review
   provides, and SPEC wins over the study.
2. **Flash control is conditional.** The study always draws a flash button on the preview; the app
   shows it only when CameraX reports a flash unit. The emulator's virtual camera has none, so it is
   absent in `01`–`04`.
3. **Span controls are additional.** `Selected: …`, `◀ Extend`, `Extend ▶`, `Trim` do not exist in
   the study, which selects spans by dragging across pills. They are the accessible equivalent
   required by SPEC §4.3.3 and they add one row under the pills.
4. **Translation failure row.** With no key the app shows the failure and Retry under the sentence
   where the study shows a finished translation.
5. **Wide-window cap.** The composition is capped at 520 dp and centred, so a rotated window shows
   gutters (`23`) instead of stretching the photo slot and sentence across the full width.
6. **Pitch contour.** The production source-backed component is used: no axis legend, 22 sp katakana
   labels, hollow rings for source-backed nasal morae and crosses for devoiced morae. The study's
   contour is a fixture drawing, not linguistic data.
7. **Camera preview letterboxes the emulator feed.** `PreviewView` uses `FIT_CENTER` so the preview
   matches the captured frame; the emulator's virtual camera is 16:9 inside a taller portrait frame,
   so `01`–`04` show black bands above and below the image. On a phone with a 3:4 sensor the frame is
   filled. The study's illustration fills the card edge to edge.

## Known defects

1. **Tokenization of the fixture sentence.** This build's UniDic splits `図書/館` and `読み/まし/た`,
   so the pill row wraps `。` onto its own line and the study's single-token `図書館` / `読みました`
   pills are not reproduced. This is bundled-dictionary data, not layout; the target selection itself
   is correct.
2. **Route is re-derived on configuration change.** `route` is never persisted; a completed draft
   reopens on Review after a theme, display-size or rotation change even if the user had gone Back to
   Target. Draft content is never lost. This is pre-existing v0.3 behaviour, left unchanged here.
3. **Deck control at 200% font.** `Deck · Choose` ends within ~2 dp of the 16 dp gutter with a long
   destination name and has no ellipsis policy.
4. **Unavailable audio has no reason text.** The state is announced as "Word audio unavailable"; the
   cause is only shown for a Failed state (v0.3 parity).
5. **Full-photo viewer has no zoom.** It fits the whole frame as specified; pinch-zoom is out of
   scope.
6. **TalkBack speech not exercised.** Content descriptions and state descriptions are present in the
   hierarchy dumps, but no screen-reader session was recorded.

## Not run / credential-gated

Sentence TTS (Chirp 3 HD), Cloud Translation Basic v2, the shared-key Settings checks and AnkiDroid
export all require credentials or an installed AnkiDroid and were **not** exercised. The UI paths
for their failure states were exercised (missing key, unavailable audio, disabled Send).
