# Japanese Anki Card Creator — Specification

**Status:** AUTHORITATIVE for required behavior, data contracts and acceptance criteria. It does not
cover how the work is built, tested or verified — that is [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md)
— or what the app should feel like — that is [docs/DESIGN.md](docs/DESIGN.md). Document map:
[docs/README.md](docs/README.md). Target alternatives and any pitch-backend migration remain
proposal/approval gates; no second pitch backend is approved.

**Target:** v0.3. This document defines the required product behavior, technical constraints, and acceptance criteria.

**Product:** A personal, sideloaded Android app built with Kotlin and Jetpack Compose.

## 1. Purpose and quality contract

Mine one interesting Japanese sentence and one new vocabulary item, anchored by the situation and preferably a photograph. The normal journey is:

> Capture or type → choose the sentence → choose the target and meaning → generate → inspect → send to AnkiDroid.

The user selects the vocabulary and intended meaning, glances at the completed note, may play the word pronunciation, optionally adds notes/tags, then sends. Listening to every sentence recording is not a prerequisite. Evaluate the chosen sentence voice before relying on this workflow.

The app creates Anki **notes**; AnkiDroid owns the note type, templates, cards, media copies, scheduling, and synchronization. This app is not a reviewer or collection editor.

### Required content

| Content | Completion requirement |
|---|---|
| Sentence and target | Nonempty Japanese sentence; one valid selected occurrence |
| Vocabulary and definition | Resolved dictionary entry/reading and user-selected meaning |
| Sentence and vocabulary furigana | Complete readings for kanji spans; kana-only text needs no ruby |
| Pitch | A source-backed pattern for the selected vocabulary reading, retaining source phonetic annotations including nasalization |
| Word audio | A matching recorded pronunciation, not synthesized substitution |
| Sentence audio | Completed high-quality Japanese TTS for the current sentence |
| Translation | Completed English sentence translation, with targeted corrections allowed |
| Image | Retained for Photo input; preferred but optional for Type/text-share input |
| Notes and tags | Optional |

Missing required data, failed generation, unresolved corrections, and unfinished selected media **block export**. Never send a degraded note for later repair. A missing dictionary, pitch, or recorded-pronunciation entry is an accepted reason that a sentence/word cannot be mined with this app. Log lookup misses locally (§11).

Completeness is testable; universal linguistic correctness is not. Do not invent readings, pitch marks, pronunciation-match claims, or confidence scores. Known-bad results must be corrected or rejected. A successful HTTP response alone is not evidence of pronunciation quality.

## 2. Platform and build scope

| Area | Decision |
|---|---|
| Language/UI | Kotlin, Jetpack Compose Material 3 Expressive |
| Android | Android 17 / API 37; `minSdk = 37`, `targetSdk = 37`, `compileSdk = 37` |
| Device ABI | `arm64-v8a` |
| Emulator ABI | `x86_64` on API 37 |
| Delivery | Personal sideloaded APK supporting both ABIs |
| Build environment | Pinned Nix development environment on x86_64 NixOS |
| Signing | Local signing outside the repository and Nix store; preserve update identity |

Physical-device testing is optional and does not block delivery. Verify ARM64 packaging/native linkage and exercise runtime workflows on the API-37 x86_64 emulator. Report packaging checks separately from runtime tests.

A clean checkout must build and run its tests using documented commands inside `nix develop`. Dependency fetching is allowed.

## 3. Scope and one-path architecture

### One implementation path per operation

| Operation | Selected path |
|---|---|
| OCR | On-device Japanese recognition, model bundled in the APK |
| Tokenization and initial readings | Packaged MeCab native code and first-run verified UniDic; adapt AJT behavior where relevant (§5) |
| English dictionary/meaning selection | Bundled JMdict-derived Japanese/English data |
| Pitch and phonetic marks | One complete pinned AJT dataset, bundled and locally indexed with original annotations (§5) |
| Word recording | Bundled NHK-2016 index, with its recordings fetched per word (§6) |
| Sentence TTS | Google Cloud Chirp 3 HD, with phonetic hints and a cloud-listed Japanese voice selected in Settings (§6) |
| Translation | Google Cloud Translation Basic v2, default neural machine translation (NMT), Japanese → English (§7) |
| Image export | Android platform lossy WebP, at most 1080 px on the long edge (§7) |
| Export | Supported AnkiDroid Android API/content provider |

Use one provider path per operation, with reuse of valid cached results. Settings discovers voices within Google Chirp 3 HD. Translation uses Google Cloud Translation Basic's default NMT model. Speech and translation share one runtime Google Cloud API key. An unavailable service fails visibly and preserves the draft.

Bundle assets by default. Provision an asset at first run only when shipping it would dominate the download: today that means UniDic alone, at roughly 426 MB compressed, which would otherwise inflate the APK toward half a gigabyte. Everything else ships inside the APK — the Japanese OCR model, the NHK-2016 index, the dictionary and the pitch dataset — because at a few megabytes to a few tens of megabytes each, a first-run download costs more in failure modes than it saves in bytes.

The runtime-provisioned payload (UniDic) is downloaded once with a build-pinned hash and progress (§4.8). Bundled data and local tokenization work without network access after UniDic is validated. NHK recording files remain a documented on-demand network operation (§6.1). Native executable code and required notices remain packaged; do not download executable code.

### Included

- One sentence, one contiguous target, one image, and one durable draft.
- Camera, system image picker, typing/paste, plain-text share, one-image share, and selected-text (`PROCESS_TEXT`) entry.
- Dictionary entry/sense selection, automatic enrichment, targeted corrections, playback, and new-note creation.
- Destination and default tags in autosaved Settings; defaults seed each fresh note, with per-note Review overrides and existing-tag suggestions.

### Excluded

Batch mining, draft libraries, background clipboard monitoring, generated mnemonic stories/images, vocabulary-knowledge tracking, review/sync/editing existing notes, deck/template installation, arbitrary field mapping, microphone/audio import/editing, image crop tools, freehand pitch editing, provider fallbacks, background generation queues, and automatic retry after restart.

Review is a native Compose presentation of the note content. The app does not render the Anki template or attempt pixel-identical card previews.

## 4. Workflow and native UI

### 4.1 Design rules

Use actual Compose Material 3 Expressive components, typography, color roles, shapes, state layers, and restrained transitions. Use a coherent light/dark theme; normal Material accent/error colors and appropriate motion are welcome. Pitch colors must remain distinguishable and must not be changed accidentally by theme tinting.

Use conventional top app bars, icon Back navigation, settings lists, bottom sheets, chips, progress, and brief success feedback where appropriate. Settings is reachable at the top right of Capture, Target and Review. Use native component behavior and Material color roles consistently. One visible blocker owns its explanation/recovery; do not duplicate status/instructions across body/footer or classify ungenerated content as an error. Sheets are left-aligned; definition previews use one ellipsized line without truncating stored/exported meanings.

Support English UI, system light/dark mode, a portrait-only app composition with landscape photograph capture/import, 200% font scaling, TalkBack labels/states, at least 48 dp non-overlapping touch targets, 4.5:1 normal-text contrast and 3:1 actionable-icon contrast. Respect system-bar/keyboard insets; long content scrolls. Support reduced/disabled animation settings and do not use color alone to communicate state.

Intent, tone and the visual language these rules serve are in [docs/DESIGN.md](docs/DESIGN.md); the prototype-then-reuse working method is in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §10.

### 4.2 Capture

Offer **Camera** (initial default) and **Type** in a bottom mode toggle, remembering the last used mode. Use CameraX with standard uncropped 4:3 capture and orientation-aware capture. The app interface remains portrait-only; landscape captures and picked/shared landscape images are stored orientation-corrected and displayed correctly in the portrait composition. Use a full-width uncropped viewfinder (upright portrait aspect 3:4) without synthetic masks/card padding/rounded clipping; ordinary chrome/extra space below is allowed. Preview position and dimensions must not move during capture, progress or errors. Reserve stable control/status slots and match saved bounds without stretching. Lock competing capture actions while a still is pending.

The usual sequence—rotate to landscape, capture, rotate back to portrait, continue—must preserve the photo and draft without switching the interface to a landscape composition. Store orientation-corrected media. Request camera permission through the normal Android flow; denial preserves work and leaves Type/image-picker entry usable.

Photo input uses the capture/picked/shared image as its contextual image. It can be replaced without rerunning OCR or altering the sentence, but the photo-origin draft still requires an image. Type input may attach, replace, or remove an image independently. Taking a contextual photo from Type must not replace typed text with OCR. Input origin belongs to the draft, not the currently displayed mode switch; toggling modes cannot bypass the image rule.

### 4.3 Sentence, target, and meaning — local phase

1. Run OCR on a locally decoded source image **before** export downscaling/lossy encoding. Keep enough source detail to recognize small text; do not use the compressed 1080 px note image as the OCR input.
2. Show recognized text below a small photo reference. A single clear region populates the editable sentence directly. For several plausible regions, show readable selectable excerpts; the user chooses one, then trims/corrects it. No bounding-box drag editor, zoom-coordinate selection, or second selection mode. Choosing text never crops the image.
3. Tokenize locally without blocking input. Present the exact original sentence prominently, preserving occurrence-aware spans and separate raw morphology/grouped units. The approved production design is Target A: compact Material word pills below the hero sentence. Tapping a pill selects that whole word and updates lookup automatically. Target B's sentence-native handles remain a discussion prototype, not a second production selection mode. Do not preselect an initial target without a tap or force-open a chooser on every tap. Remove visible Extend/Trim controls; preserve accessible selection equivalents. Correct the sentence through a compact action in this preparation surface, not a separate full row. Do not ship prototype debug offsets or explanatory fixture text.
4. Show dictionary spelling, reading, and English senses. Resolve entry ambiguity and let the user select the intended meaning. A sole entry can resolve silently, but its meaning is visible before the user continues. Select one sense by default; allow several and targeted definition edits. Do not blend unrelated readings or meanings.
5. Validate local reading/pitch availability and any locally searchable recording index before paid requests. A local miss keeps the selection and explains the problem; no adjacent-word guess or free-text replacement lemma.
6. **Continue** confirms the preparation and starts generation. Do not trigger network calls merely because text was typed or a token tapped.

A target identifies a span/occurrence, not a global text replacement. Preserve Unicode boundaries and highlight only the chosen occurrence. An inflection such as `食べた` remains in the sentence while vocabulary resolves to `食べる`. The vocabulary reading, pitch, and recording must agree on the chosen entry/reading.

### 4.4 Generate — network phase

Freeze the preparation inputs for a generation revision. Start the required recording fetch, sentence TTS, and translation; independent operations may run concurrently. Finish image encoding without blocking the local choice flow.

Fresh explicit Continue authorizes work for the frozen revision upon entering Review: automatically obtain recorded word audio, synthesize sentence audio, and translate without extra Generate taps. Show small inline per-item spinners in the composed Review, not a blocking loading screen or editable field inventory. Large speaker-icon controls provide playback/stop; do not auto-play. Recomposition, revisiting or process restoration must not duplicate paid work or automatically restart failed/interrupted operations. Send is unavailable until required content/media are ready. Preserve source and successful results; manual Retry repeats only appropriate failed/invalidated work on the same path. Permanent/input/setup errors require correction. Cancel stops or ignores obsolete work; no result may attach to a newer revision.

A required dataset miss is not fixed by repeatedly calling a service. Show that distinction and offer reselection/correction/discard. A network error is not logged as evidence that a dataset lacks the word.

### 4.5 Review and corrections

Present a composed learning artifact: contextual image when present, prominent Japanese sentence with target/ruby, its translation immediately beneath the sentence, then a vocabulary/definition block with pitch and word playback. Sentence playback remains available but secondary. There are no permanent text boxes, mandatory row per stored field, empty image placeholder for Type, or always-present empty Notes row.

**Send to AnkiDroid** is the primary action. Only **Notes and Tags** are editable in Review, using scoped secondary editors. Display chosen tags and notes without duplicate read-only copies while editing. Large speaker buttons play word/sentence audio; small inline spinners represent pending audio. A top-right Settings gear preserves the draft. Destination selection belongs only in Settings, not a recurring Review control. Return to retained preparation for content corrections.

Targeted editing in retained preparation, not Review, supports:

- Sentence/target/entry/senses through the same preparation flow, not another set of nested reselection sheets.
- A reading correction for a selected kanji span; no raw bracket/HTML editor. Vocabulary reading changes must re-resolve dictionary/recording/pitch identity, not manufacture a nonexistent pronunciation.
- Definition and translation text corrections.
- Selecting another source-listed pitch pattern for the same reading, never drawing an accent by hand.
- Replacing the image; removing it only where optional.
- Explicit retry of rejected sentence audio from the same model/voice. No silent switch to a different voice or provider; persistent bad pronunciation requires correction or abandoning the note.

Text editors expose **Done** and autosave. Back dismisses keyboard/editor first and preserves work. Source edits invalidate dependent results; rules are in §10. Do not require the user to listen to sentence audio before every export.

### 4.6 Send and next note

While sending, keep the submitted content visible and freeze editing. Prevent duplicate taps. Show success only after the note, destination, and media references are confirmed; then return to blank capture with brief accessible feedback. Preserve settings and capture mode; seed the next note from default tags, not the sent note's overrides. Do not retain the prior sentence/target/media/notes.

Blocked or uncertain submission retains the same content with actionable recovery (§9). Never use the generation Retry button to repeat a possibly completed Anki insertion.

### 4.7 Setup and incoming content

Settings contains existing note type/deck selection, **Default tags**, one **Google Cloud API key** shared by speech/translation, separate service validation/status, a **Sentence voice** selector, cache clearing and Sources/Licenses. Use the app's shared theme/insets and normal Back arrow; there is **no Save button**. Autosave all settings consistently with protected/debounced credential persistence, visible storage failures and tested flush behavior. Defaults seed every fresh note; Review overrides affect that note only. Changing defaults must not overwrite the active draft. Show **Google Cloud Translation Basic — default neural (NMT)** read-only.

On Settings open, load voices with an available key using Google Cloud `GET /v1/voices?languageCode=ja-JP`; provide a Reload action and filter to Japanese-capable Chirp 3 HD voices. Never synthesize paid previews merely on open. Populate a native selector from returned IDs, not a hardcoded catalogue. Show friendly names/metadata; retain the exact explicitly selected ID. Fence listing/cache/service status to credential and request revision. Expose loading/empty/auth/retry, honestly labelled cached offline display, and no selection when first fetch fails without cache. Onboarding uses the same settings/credential/listing contracts.

Keep the saved selection through a refresh failure. If a successful refresh no longer lists that voice, require reselection before new synthesis; never silently pick another. Changing the saved voice invalidates only sentence audio (§10), not translation, readings, word recordings, or image. Voice listing is a separate metadata operation and must not automatically synthesize paid previews.

AnkiDroid detection/API permission and required-field validation must succeed before export. A missing Google Cloud API key need not block local preparation, but blocks speech and translation requests. Setup uses one Google Cloud project with billing and both the Cloud Text-to-Speech API and Cloud Translation API enabled; restrict the shared key to those APIs. Validate the services separately and show which service has an authentication, API-enablement, billing, or quota problem when the response identifies it. Voice listing validates speech access only, not synthesis quality or translation access. A translation check is an explicit, potentially billable action using a disclosed sample sentence; entering a key alone must not trigger paid requests. Keep setup/errors discoverable before the user invests in a note.

Text shares enter Type preparation; a single shared image follows Photo preparation. Neither guesses a target or sends automatically. Confirm before replacing a nonempty draft; declining preserves it. No broad clipboard or library permission, URL scraping, or multi-item share workflow.

### 4.8 First-run onboarding and resource provisioning

Provide an onboarding wizard covering permissions, the protected shared Google API key/voice setup, and the one first-run data download: UniDic. Key and voice setup are required before completing onboarding; no skip-key completion path. Show UniDic transfer, hash/archive verification and extraction progress with actionable failures; do not freeze the interface. Default downloads to Wi-Fi; allow mobile data only after showing download size and obtaining explicit confirmation. Respect permission denial, explain required capabilities and preserve pending incoming content.

The only first-run dataset download is the UniDic CWJ 3.1.1 runtime ZIP (`446,602,958` bytes; SHA-256 `4963afe095a031b9a15e97325795cf28c772ff108138172e69c786cbc4c9b3fe`), delivered from the versioned v0.4 GitHub release asset. Pin the URL, exact byte count, hash and five expected archive entries in the build. Verify transfer length and SHA-256, validate ZIP paths/CRC/entry sizes, then extract to a temporary directory and require the exact `FILE_SIZES` set and `TOTAL_BYTES` before activating. Failed downloads or extraction must not activate partial data; a valid existing installation is reused. The archive is data, not executable code. Never ship placeholder URLs/hashes or call a local fixture a deployed download.

The Japanese ML Kit model, NHK-2016 pronunciation index, JMdict dictionary and AJT pitch database ship in the APK. OCR and NHK headword lookup therefore work offline immediately after install; local tokenization/linguistic preparation works offline after UniDic has been downloaded and validated. Individual NHK recording files remain an on-demand network operation (§6.1), not a first-run dataset download. Native libraries and required notices stay in the APK; runtime executable-code downloads are forbidden. Keep licenses/notices accessible before and after UniDic provisioning. Do not claim NHK recordings themselves are available offline unless cached.

## 5. Linguistic processing and AJT compatibility

### 5.1 Sources and matching

Use [AJT Japanese](https://github.com/Ajatt-Tools/Japanese) as the behavioral reference for furigana and pitch output. Pin the upstream revision and derived-data sources, preserve notices, and document transformations. Keep dictionary, morphological analysis, pitch, and audio identities separate.

Use packaged MeCab native code with verified provisioned UniDic for tokenization/lemma analysis and bundled JMdict for definitions and part of speech. Match entries using surface, lemma, and reading. Preserve entry/sense boundaries; no manual vocabulary fallback. Test actual mining examples, not only textbook-perfect strings.

**Single pitch backend:** pin AJT Japanese at `5729b8c080823eadaa121c76e0f23c55d6a81935` and ingest **all three** `pitch_accents_formatted.1.csv`, `.2.csv` and `.3.csv` parts as one dataset, matching the plugin's complete input enumeration. Hash/pin every part and preserve deterministic ordering/provenance and supplied nasal/devoicing markup. A part-3-only index is incomplete, not evidence of an upstream gap. Golden fixtures include 受付/うけつけ (heiban with devoiced ツ) and 出きる/出来る/出來る. No Kanjium fallback or backend migration without a separate approved proposal. Unmarked records mean no mark supplied, not verified absence of nasalization.

### 5.2 Furigana

Generate sentence and vocabulary readings locally. Follow AJT-style okurigana preservation and whole-word handling for irregular readings; preserve punctuation and the selected occurrence. Render native inline ruby in Compose; export Anki bracket-furigana compatible with the existing note type's `furigana:` filters.

Kana-only text is already complete. Every kanji-containing span needs a supported automatic or user-corrected reading. Unknown or pending spans are visibly unresolved and block Send until corrected; do not silently export them as if annotation succeeded. Allow targeted reading correction without changing the original sentence. Never invent per-character alignment where only a whole-word reading is supported.

### 5.3 Pitch, nasalization, and HTML output

`VocabPitch` uses AJT **`html_and_number` with `HTMLPitchPatternStyle.none`**, which emits class-based spans: (`high`, `low`, `high_drop`, `low_rise`), nested `nasal`/`handakuten`/`devoiced` spans where supplied, and a trailing `pitch_number` span. Preserve nested structure and source phonetic distinctions. Golden fixtures include 悪逆 / アクギャク / accent 0, whose source marks the nasal キ and degree symbol inside a high span; the mark is not an extra mora.

Select one matching pattern for the chosen spelling/reading. The first source-listed pattern is the deterministic default, not a frequency or correctness ranking. Other matching patterns are available in preparation. Missing pitch blocks export. Zero is valid heiban, never a missing sentinel.

Preserve complete mora sequences, contracted kana, small `っ`, `ん`, long vowels, source accent positions (including final-mora accents) and accent positions of ten or more in data/export/accessibility. Native diagram labels use katakana; solid and hollow markers have equal outer diameters and connector segments are clipped outside hollow interiors. End the drawn contour at the final mora circle: no extra tail or additional final-drop indicator. Do not discard source accent metadata to achieve this presentation. Permit scrolling for long readings; color alone cannot identify a pattern or selection.

Nasalization support is required at the dataset/renderer level, not a requirement that every word have a nasal mora. Preserve all supplied marks, disclose the source's incomplete annotation coverage in Sources, and never call an unmarked record verified non-nasal. A valid unmarked pitch record remains usable, as in AJT; a missing pitch record does not. No per-note phonetic-provenance editor is required.

The recording must match the selected vocabulary reading and numeric accent using NHK-2016 index metadata; spelling/headword lookup constrains candidates. Reject conflicts or an unresolvable match rather than silently using the first recording. Matching these values does not prove every phonetic detail agrees across dictionary editions. Do not claim such a guarantee merely because both sources are called NHK. Readings may be normalized for comparison using tested AJT-style rules, but retain original nasal marks for display/export.

## 6. Audio

### 6.1 Recorded vocabulary

Use the NHK-2016 pronunciation index at `b0207d240f0f28cdc2dd491dd83343c3a5630119` as the sole recorded-word path. Download recordings on demand; cache valid files. Select deterministically among matches for the resolved spelling/reading. No synthesized word fallback or audio-source picker.

Verify actual matching information, bounded downloads, response type, and playable output. Missing/unusable audio blocks export and retains the draft. A source update is a pin change. Keep source notices and distinguish the index license from recording rights. Personal use is not evidence of recording permission; do not make unsupported licensing claims.

### 6.2 Sentence TTS

Use **Google Cloud Text-to-Speech Chirp 3 HD directly**, with the Japanese voice explicitly selected in Settings (§4.7). Use synchronous file synthesis via `POST /v1/text:synthesize`, `languageCode = ja-JP`, the exact selected voice name, MP3 output, and a fixed delivery configuration. Validate decoding before marking complete. No voice cloning, recording, model catalogue, or fallback chain. Store the Google credential at runtime (§11).

**Phonetic hints are required, not an optional best-effort feature.** Build the synthesis request from the current sentence and its structured, resolved readings, including manual corrections. Send Google Japanese yomigana pronunciation overrides for resolved kanji-containing spans using `input.customPronunciations.pronunciations` and `PHONETIC_ENCODING_JAPANESE_YOMIGANA`. Kana-only text needs no fabricated hint. Do not send Anki bracket notation, ruby HTML, parenthesized reading prose, or conversational instructions as spoken text. Keep exported sentence text unchanged; avoid replacing the entire sentence with kana.

The hint builder must preserve inflection/okurigana and apply the sentence occurrence's reading, not blindly substitute the vocabulary lemma's reading. Do not apply one reading globally when the same spelling has different readings within the sentence. Use a tested occurrence-safe request representation, or report that the conflicting hints cannot be represented and block generation; never silently omit them. Reject invalid/unresolved readings before a paid request. Do not invent IPA, nasalization, or sentence-level pitch accents; yomigana hints specify readings, not a guarantee of dictionary accent in connected speech.

An unsupported or rejected hint request is a generation failure; do not retry without hints. Tests must inspect the serialized outbound request and audition ambiguous-reading fixtures with the selected voice; a successful response alone does not establish that Google honored the hints. Japanese hint support for the exact voice/API combination requires runtime verification (§15). Reading corrections invalidate hinted sentence audio (§10).

Target sentence-TTS spend is **at most USD 5/month at roughly two cards/day**. Review non-promotional rates, billing units, top-up fees, and a retry allowance. Quality acceptance prioritizes correct readings, counters, particles, omissions/additions, and natural Japanese prosody over expressive acting. Do not claim perfect pitch-controlled sentence speech.

### 6.3 Lifecycle and reuse

Generation begins automatically on Review entry authorized by fresh explicit Continue after the local meaning choice is confirmed. Never repeat billable work automatically on revisit, recomposition or process restoration. Preserve completed matching results; explicit Retry resumes only appropriate transient/invalidated work, not unchanged permanent rejections. Cancel or ignore superseded responses. Audio for outdated inputs is unavailable for playback/export. Do not auto-play audio.

Keep one bounded reusable audio cache with a 250 MB limit. Cache keys include source/model, text or recording identity, exact voice ID/configuration, the canonical phonetic-hint payload and hint-builder version for sentence speech, and revision where relevant. Active-draft media is durable and protected from eviction. Cache clearing must not remove the draft or AnkiDroid's copies. No cache-management dashboard is needed.

### 6.4 Small modular provider boundary

Keep sentence-speech operations behind a small injectable contract: list compatible voices and synthesize a structured request to an audio file. The workflow passes sentence text, occurrence-aware reading hints, selected voice ID, and delivery settings; it does not construct Google JSON. Keep Google authentication, HTTP/JSON mapping, voice filtering, response validation, and sanitized provider errors inside the Google adapter. Keep reading/hint construction in a pure, independently tested function. Compose consumes app-level voice/result/error data, not Google SDK types or raw HTTP responses.

Provide one production adapter (Google) and a test fake/transport. Inject credential access and HTTP transport so neither is coupled to an Activity or Settings widget. Translation and recorded-word lookup remain separate operations rather than subclasses of the sentence-speech contract.

## 7. Images, translation, notes, and tags

### 7.1 Images

Use **lossy WebP through Android `Bitmap.CompressFormat.WEBP_LOSSY`**. Modern Anki WebViews are the rendering target; verify an actual imported image there.

1. Decode safely off the UI thread; apply orientation and preserve the full frame.
2. Run OCR from the source-resolution decode, independently of export compression.
3. Resize to **1080 pixels maximum on the long edge**, preserve aspect ratio, never upscale. A 4:3 landscape frame at the limit is 1080×810.
4. Re-encode with a fixed measured quality preset, stripping location/EXIF metadata and normalizing display color as needed.
5. Store a durable processed file with matching extension/MIME type. Release the original once OCR and draft processing no longer need it; do not store duplicate originals indefinitely.

**Quality preset: 75.** Validate readability and file size on representative photographs and Japanese small-text fixtures using Android's encoder and actual AnkiDroid rendering. Perform OCR before resizing; image compression cannot recover detail lost by downscaling.

No crop editor, per-note quality slider, or multi-codec policy. A thumbnail crop is display-only. Attached images must finish processing before Send becomes available; import occurs during submission and must succeed before note insertion. Failure permits retry/replacement, or explicit removal for Type input only. No silent omission.

### 7.2 Translation

Use **Google Cloud Translation Basic v2 with its default neural machine translation (NMT) model** for faithful Japanese-to-English sentence translation. Call `POST https://translation.googleapis.com/language/translate/v2` with the current sentence as `q`, `source = ja`, `target = en`, `format = text`, and `model = nmt` explicitly. Authenticate with the same runtime Google Cloud API key used for sentence speech, sent in the `X-Goog-Api-Key` header (§11).

Send only the original sentence text for translation; dictionary senses, reading hints, photographs, and private Notes remain outside the translation payload. Keep the selected dictionary meaning in the vocabulary definition. Translation consumes plain text, with no prompt or user-selectable model. Treat response text as untrusted plain text and safely serialize it for Anki export.

Evaluate NMT against representative short mining sentences before release (§15), including ambiguous vocabulary, omitted subjects, colloquial language, and names. The app does not claim that translation is constrained to the user-selected dictionary sense. Require one nonempty English translation for the submitted sentence; malformed, empty, or failed output blocks export. The translation is editable in retained preparation, not Review. Preserve manual corrections according to §10. Keep request serialization, bounded response handling, sanitized errors, and injected transport tests separate from Compose; retry retains the same NMT path.

### 7.3 Notes and tags

Notes are optional plain text with line breaks preserved and HTML escaped. No generated mnemonic, rich-text toolbar, or separate source-entry feature; an existing template may interpret a user-written `Source:` prefix.

Tags are optional Anki metadata, not fields. **Default tags** in Settings seed every fresh note; absent defaults means empty tags. Review changes affect only the current note, never the defaults or the next note's seed. Preserve active-draft tags when changing defaults. Offer existing AnkiDroid suggestions and hierarchical tags such as `JLPT::N4`; no tag-management screen. Suggestion failure must not block manual tags or export.

## 8. Anki output contract

Select an existing note type by name and remember its ID; map by exact field name, not order. The reference is `misc/Sentence (JA)/template.json`. Require the distinct field names below except the explicitly optional `VocabPartOfSpeech`; recognize and populate that field when present. Other extra fields are allowed and remain empty. Report missing/ambiguous fields and deleted model/deck identities without substituting another destination. Derive POS from the selected JMdict senses with inherited POS resolved, stable deduplication and safe escaping; absent source POS is not an export blocker and must not be invented.

| Field | Exported value |
|---|---|
| `SentKanji` | Original sentence; only the selected occurrence wrapped in controlled `<b>` markup |
| `SentFurigana` | Complete Anki bracket-furigana with the same highlighted occurrence |
| `SentEng` | Current completed/corrected English translation |
| `SentAudio` | Imported sentence audio reference |
| `VocabKanji` | Resolved dictionary lemma |
| `VocabFurigana` | Resolved vocabulary with Anki bracket-furigana |
| `VocabDef` | Selected English senses or targeted correction, safely serialized |
| `VocabPartOfSpeech` (when the note type has it) | Available JMdict POS from selected senses, respecting inherited data, stably deduplicated and safely serialized; empty when unavailable, never guessed from MeCab |
| `VocabPitch` | One AJT-compatible HTML pattern with source-backed phonetic marks (§5) |
| `VocabAudio` | Imported recorded-word audio reference |
| `Image` | Imported WebP image reference; empty only when optional and intentionally absent |
| `Notes` | Escaped optional plain text |
| `fm_focus_words` | Empty |
| `fm_new_words` | Empty |
| `fm_seen_words` | Empty |

Treat all input/dictionary text as text, not trusted HTML. Generate only controlled highlight, furigana, definition, pitch, and media markup. Audio uses the imported filename, typically `[sound:filename]`; images use the imported filename, not a private path or temporary URI. Preserve sense boundaries and valid accent zero.

The app does not install or modify templates. Supply a compatible reference template and setup procedure, including necessary media/fonts/CSS, for the user to install. Its renderer must preserve nested phonetic marks, treat handakuten as an annotation rather than a mora, handle contracted kana and final drops, and parse accent numbers of ten or more correctly. Export one chosen pattern. Test the **final displayed nasalization/devoicing** and pitch contour in AnkiDroid, not just the serialized field string.

## 9. Safe AnkiDroid submission

### 9.1 Boundary and eligibility

Use AnkiDroid's supported Android API/content provider, not desktop AnkiConnect or direct database writes. Allowed reads: note types/decks, tag suggestions, duplicate checks, and submission reconciliation. Allowed writes: media import and new-note creation/destination assignment. No updating/deleting existing notes, modifying templates/decks, synchronization, or automatic media rollback.

Send requires all §1 content complete for one current revision, no unresolved rejected/stale values, a valid target/entry/meaning, valid imported-media candidates, API permission, and a valid note type/deck. An absent optional image/Notes/Tags is not an error. An attached but unfinished/failed image is not absent.

The integration reference is AnkiDroid v2.24.0, source commit `ebcf8e0e34921628b9b8a496c66ffd4adbb3705f`. Verify runtime compatibility against this release. Verify reordered/extra fields and required trailing empty `fm_*` fields; never put dummy text in user-owned fields to bypass provider behavior.

### 9.2 Duplicate rule

Block when the selected model has a note in the destination deck with both the same sentence text and vocabulary lemma. Different sentence or lemma is allowed; identical lemma/sentence at a different occurrence or reading is still a duplicate. No override or merge.

Decode controlled markup, trim outer whitespace, and preserve internal text. The first-field duplicate helper alone is insufficient. Failed duplicate checks block insertion. Serialize sends and check immediately before insertion; external writers remain outside this app's transaction boundary.

Offer Open in AnkiDroid using the source-supported `anki://x-callback-url/browser` route with encoded `search=nid:<noteId>`, targeted to AnkiDroid, after runtime verification. If unavailable, show the note/deck locator and retain the draft.

### 9.3 Submission and interruption

1. Freeze the complete revision; persist submission state and prevent concurrent sends.
2. Revalidate permission/model/deck and duplicate rule.
3. Import every selected media item and save returned references. Import failure prevents insertion; never silently drop a required item or create a broken reference.
4. Insert the note, retain its returned ID, establish its destination, and verify completion.
5. Persist confirmed completion, then reset the draft; the fresh note's tags come from Settings defaults, not the sent note's overrides.

During uninterrupted insertion, destination assignment may move only cards of the newly returned note belonging to this submission. Media import, insertion, and destination assignment are not assumed atomic.

If insertion may have happened, do not retry blindly. Persist the frozen fields, phase, import receipts, known note ID, and pre-insertion duplicate result. Reconcile read-only using model/fields/card-deck membership. A concurrent matching note is not proof of authorship. Reuse safe media receipts, but never delete collection media as rollback.

If the outcome remains uncertain, retain the submitted content and ask the user to check AnkiDroid. If insertion is known but destination incomplete, report the note ID/intended deck for manual repair and recheck. **No recovery card moves or second insertion.** Manual confirmation of existence prevents another insert but does not prove destination success; reset automatically only on verified completion. Do not promise exactly-once behavior the API cannot establish.

## 10. One draft and deliberate rebuilding

Autosave source text/image, target/entry/senses, current results, user corrections, generation status, and submission receipts. Restore after closure, rotation, and process death. Durable media must not depend on temporary picker permission or evictable cache entries. Confirm before discarding nonempty work.

Use a small lifecycle: **Prepare → Generate → Review → Send** with retained failure states. Submission recovery is separate from generation retry. No background job queue or multi-draft repository.

| Edit | Recompute/invalidate |
|---|---|
| Sentence | Target validity, sentence readings, translation, sentence audio; re-resolve affected vocabulary |
| Target/entry/reading | Vocabulary definition/reading/pitch/recording and sentence highlight; recheck affected target ruby; translation remains valid if sentence text is unchanged |
| Meaning | Definition only; sentence-only translation remains valid |
| Sentence reading correction | Its ruby and phonetic hints; invalidate sentence audio and require deliberate regeneration |
| Pitch alternative | Pitch output and recording pitch-consistency check |
| Image | Processed image only; not sentence/OCR |
| Notes/tags | No generation |
| Sentence voice change in Settings | Sentence audio only; regenerate deliberately with new voice and existing hints |
| Translation request-contract version change | Translation only |

Do not start billable work on every keystroke. Source-changing edits return to Prepare; Continue deliberately rebuilds affected results. Reuse unchanged valid results. Cancel/ignore obsolete completions and never play/export mismatched audio.

Before applying a source change that invalidates manual corrections, explain which corrections will be replaced and require confirmation. Cancel preserves the draft; confirmation replaces only affected corrections during rebuilding. Store one active value per field. Direct corrections with unchanged inputs survive retry. Required unresolved values block export.

## 11. Privacy and diagnostics

- Photographs remain on the device through capture, OCR, processing, and draft storage; the app may import them into the local AnkiDroid collection. AnkiDroid's user-controlled sync is outside this app. Never upload photos to OCR/LLM services or diagnostics.
- Google Cloud Text-to-Speech receives sentence text and phonetic hints for synthesis; Google Cloud Translation receives only sentence text for translation; recording hosts receive necessary lookup/download requests. No private Notes, credentials in URLs, raw-media logs, or unrelated network processing.
- No app analytics, ads, clipboard surveillance, automatic log upload, or private-content diagnostic upload. SDK-default diagnostic traffic is permitted only if it excludes user content and credentials; document selected versions and check behavior. Local text logs alone do not establish remote transmission.
- Store one shared Google Cloud API key at runtime using Android Keystore-backed protection. Supply it only to the intended Google Cloud Text-to-Speech and Translation endpoints, in authentication headers, and do not forward it across redirects. Never commit credentials, place them in Nix inputs/APKs, display them in errors, or log them. Exclude credentials, drafts, and media from backup/cloud restore.
- Use HTTPS, bounded downloads/timeouts, and file-format validation. Request camera/AnkiDroid permissions normally; use the system photo picker rather than broad storage access.
- Log dictionary/reading/pitch/recording misses in a bounded local diagnostic log: operation, source/version, spelling/reading, and reason. Distinguish missing record, ambiguous/unverified match, network/authentication failure, and invalid media. Ordinary local text is permitted; credentials and image/audio bytes are not. No telemetry service or analytics dashboard.

## 12. Build and dependency maintenance

The shipped toolchain is the pinned Nix environment defined in §2: a fresh checkout must build and run its tests with the documented commands, without relying on an undeclared global tool. Bundled data must be reproducible from pinned sources with versions, hashes, licences and notices preserved, and signing material must stay outside the repository and the Nix store.

How that is done — gate commands, dependency-lock refresh, dataset rebuild procedure, filtered Nix source sets, deferred toolchain work and signing setup — is in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §5–§6. Acceptance of this section is R01.

## 13. Delivery priorities and quality evidence

Build one connected vertical slice before widening the surface: capture or type → local choice → actual generation → composed Review → confirmed AnkiDroid note. Every visible playback, edit and send control in the delivered slice performs its real operation; a dead control is a defect, not a placeholder.

Local preparation stays responsive: capture, correction, selection and scrolling never wait on OCR, dictionary initialization, image work, network I/O or export, and obsolete local analysis is discarded rather than queued. Latency and memory of the local phase are measured on the selected runtime; image compression and network waits are not hidden inside it.

Quality evidence for a release includes real Japanese mining examples, source-backed phonetic fixtures, image comparisons, and an exported note rendered in AnkiDroid. Sentence-TTS quality is evaluated by live audition when credentials are available (§15); controlled provider tests establish request and error handling, not speech quality.

The working order, prototype loop and gate sequence that produce this slice are in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §5 and §10.

## 14. Acceptance checklist

Record the tested revision, fixture, environment, actual result, and evidence for each scenario. An unrun or blocked check is not a pass. Apply the credential-dependent verification policy in §15 to live-service checks, and the evidence rules in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §9 to how results are recorded.

| ID | Scenario | Required result |
|---|---|---|
| R01 | Clean checkout in Nix | Tests and APK build succeed using documented tools; API 37 config and both intended ABI artifacts verified |
| R02 | API-37 x86_64 emulator | Startup, native libraries, provisioned dictionaries/OCR, media encoding, and complete creation flow work; no ARM64 runtime claim inferred |
| R03 | Fresh install/UniDic provisioning | Only UniDic downloads; progress, SHA-256, exact archive entries and post-extraction `FILE_SIZES`/`TOTAL_BYTES` gate pass; failed/truncated data never activates; bundled OCR and NHK index work offline before UniDic is provisioned; provisioned tokenization works offline |
| R04 | Landscape capture → portrait continuation | Correct orientation/full frame; portrait UI preserved; no lost photo, text, selection, or duplicate generation |
| R05 | One/multiple OCR regions and textbook text | Direct editable text for a single region; readable excerpt selection for several; correction before target selection; photo stays local |
| R06 | Type and share inputs | Typed/text-shared image is optional; image attachment does not replace text; image sharing follows Photo; replacement confirmation protects draft |
| R07 | Inflection, repeats, contiguous selection, Unicode | Correct lemma, one highlighted occurrence, intact surface/characters, no adjacent-target guess |
| R08 | Ambiguous entries and senses | User chooses intended entry/meaning before generation; unrelated readings/senses never mixed |
| R09 | Local dataset miss | Missing entry/pitch/recording-index match blocks the note, logs reason/source, preserves work, and avoids unnecessary paid requests |
| R10 | Furigana correctness/correction | Okurigana, irregular whole-word readings, and kana-only text handled; unknown kanji readings block until resolved; targeted correction persists |
| R11 | AJT HTML pitch parity | Golden fixtures cover zero, final drop, long/contracted morae, accent ≥10, nasalization and devoicing; source and final Anki rendering agree |
| R12 | Recorded pronunciation | Correct spelling/reading/file; no synthesized substitution; missing/corrupt/unverified-match policy exercised; no unsupported pitch-match claim |
| R13 | Chirp 3 HD speech and phonetic hints | Outbound request includes current yomigana hints and exact chosen voice; audition ambiguity, inflection, repeated spellings with different readings, corrections, counters, and kana-only input; no spoken markup/omissions/additions; rejected hints never trigger unhinted fallback; ≤USD 5/month estimate documented |
| R14 | Translation | Basic v2 request explicitly sets Japanese → English, plain text, and NMT; shared key is in the authentication header, not the URL; only sentence text is sent; representative live translations assessed; failures block; manual corrections survive retry; meaning-only edits preserve translation |
| R15 | Offline/auth/quota/timeout/interruption | Specific retained failure, no provider/model switch, explicit Retry only, unchanged valid results reused |
| R16 | Generation race or process death | Durable work restored; no automatic billable restart; obsolete responses cannot attach or change exported notes |
| R17 | Image processing | Orientation/full frame, max 1080 long edge/no upscale, WebP/metadata removal, measured size/readability, decodable Android and Anki output |
| R18 | Incomplete required result or attached image | Send unavailable; no partial note or silent media omission; Type-only image removal remains possible |
| R19 | Source edit after manual correction | Clear destructive-change confirmation; cancel preserves current draft; accept rebuilds only affected results with no stale export |
| R20 | Review and preparation corrections | Composed learning content, translation beneath sentence, only Notes/Tags editors, large speakers/inline progress, automatic authorized generation once; content corrections in retained preparation |
| R21 | Material/accessibility/navigation | Native components, themes, motion settings, 200% fonts, TalkBack, 48 dp targets, contrast, insets, Back, portrait layout, and landscape capture/rotation work |
| R22 | Model/deck/permission failures | No draft loss or silent destination substitution; reordered/extra/trailing empty fields verified against actual AnkiDroid |
| R23 | Duplicate rule | Same sentence+lemma+model in destination deck blocked; different sentence/lemma or other deck allowed; query failure blocks |
| R24 | Successful export | Required fields, phonetic markup, image/audio, tags, and actual destination verified; confirmed success alone resets to fresh capture |
| R25 | Repeated Send, partial import, uncertain insertion | Serialized sends, durable receipts, no blind reinsert, no recovery card moves/deletions, manual repair/read-only recheck where needed |
| R26 | Cache/storage failures | Draft media protected, cache bounded, no deletion of Anki media, interrupted writes/storage exhaustion preserve durable work |
| R27 | Privacy and lookup diagnostics | No photo upload, secret/media-byte logs, private-content telemetry, or backup leakage; one protected shared Google key reaches only intended speech/translation endpoints and is not forwarded across redirects; bounded local miss logs distinguish source misses from service failures |
| R28 | Cloud voice selector | Google Japanese Chirp 3 HD results populate Settings; loading/empty/failure/refresh/cached states work; exact selection persists; missing saved voice requires reselection; change invalidates only sentence audio and does not trigger paid synthesis |
| R29 | Modular speech boundary | Fake provider/transport tests cover voice listing/filtering, structured hint generation, actual request serialization, audio validation, auth/quota/timeouts/cancellation, and hint-aware cache identity without Compose or live credentials |
| R30 | Shared Google Cloud setup | One credential entry supplies both adapters; service checks/status remain separate; disabled API/auth/billing/quota failures are actionable without discarding the key or draft; local preparation works without a key; key entry triggers no paid request |
| R31 | Complete AJT ingestion | All three pinned CSV parts included/hashed; 受付 exact devoiced-ツ HTML and できる spelling variants resolve; no second backend; missing/rejected/infrastructure outcomes distinguished |
| R32 | Settings and default tags | No Save button; icon Back; all values autosaved with visible failures; default tags seed each fresh note, per-note overrides never become defaults; voice list loads on open with Reload and fenced credential provenance |
| R33 | Review-entry audio | Fresh Continue authorizes one missing-operation attempt on Review entry; inline spinners and big speaker controls; no auto-play or paid call duplication on revisit/recompose/process restore |
| R34 | Part-of-speech export | VocabPartOfSpeech receives available selected-sense JMdict POS with inheritance/deduplication/escaping; missing POS stays empty; older note types lacking the optional field still work |
| R35 | Bundle-by-default release packaging | APK is at most 130,000,000 bytes and excludes only the UniDic runtime ZIP; it includes bundled ML Kit Japanese OCR, NHK index, dictionary and pitch assets. UniDic is the sole first-run dataset download and must pass the build-pinned length/SHA/archive/extraction gates. Offline OCR and NHK headword lookup pass on a fresh install; per-word recording media remains on-demand. No executable-code download. |

## 15. Delivery and verification policy

Deliver the installable APK for both ABIs, a checksum, installation/setup instructions, screenshots of the delivered build, and an acceptance report with explicit PASS, FAIL, PARTIAL and NOT RUN results per §14 row. Configure credentials through app Settings after installation; do not embed developer credentials.

The Google Cloud API key is not required for APK delivery. Exercise shared credential entry/access, separate service validation, translation request/response/error handling, voice-list filtering/state, synthesis and error handling, and cache behavior with controlled transport and provider tests. Without credentials, report live Google listing, synthesis, listening assessment and translation quality as **NOT RUN**. Controlled-response tests support integration evidence but do not substitute for live-service verification. Missing in-app credentials block only the operations that require them, not local preparation.

Do not silently remove required content, fabricate phonetics or test results, upload photographs, or export partial notes to satisfy acceptance.

How that evidence is produced, gated and recorded — clean-checkout build, evidence areas, gate ladder, verdict definitions — is in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §9.

## 16. References

This document is the product contract for v0.3. The vision and feel it serves are in [docs/DESIGN.md](docs/DESIGN.md); the engineering standards behind its build and verification requirements are in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md); the full document map is [docs/README.md](docs/README.md).

- `misc/Sentence (JA)/template.json` defines the reference field schema. `Recognition/front.html`, `Recognition/back.html`, and `template.css` provide the reference presentation. Validate their compatibility under §8.
- [AJT Japanese pinned source](https://github.com/Ajatt-Tools/Japanese/tree/5729b8c080823eadaa121c76e0f23c55d6a81935) — furigana behavior, pitch HTML, and phonetic data.
- [NHK-2016 pronunciation index](https://github.com/Ajatt-Tools/nhk_2016_pronunciations_index/tree/b0207d240f0f28cdc2dd491dd83343c3a5630119).
- [Bundled ML Kit Japanese OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
- [Android bitmap compression](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat#WEBP_LOSSY).
- [Google Chirp 3 HD](https://docs.cloud.google.com/text-to-speech/docs/chirp3-hd), [cloud voice listing](https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/voices/list), [synthesis input](https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/SynthesisInput), and [Japanese yomigana encoding](https://cloud.google.com/python/docs/reference/texttospeech/latest/google.cloud.texttospeech_v1.types.CustomPronunciationParams.PhoneticEncoding).
- [Cloud Translation Basic v2 translate method](https://docs.cloud.google.com/translate/docs/reference/rest/v2/translate), [translation authentication](https://docs.cloud.google.com/translate/docs/authentication), and [Google Cloud API-key restrictions](https://docs.cloud.google.com/api-keys/docs/add-restrictions-api-keys).
- [AnkiDroid integration reference](https://github.com/ankidroid/Anki-Android/tree/ebcf8e0e34921628b9b8a496c66ffd4adbb3705f/api).
- [Material Design 3](https://m3.material.io/).
