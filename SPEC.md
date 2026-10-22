# Japanese Anki Card Creator — Specification

> **Approved UI authority:** [Sentence-mining flow and exact-copy contract](docs/sentence-mining-flow.md), approved 22 October 2026 with **progress A**. It is the sole screen/copy/choreography authority; this specification retains the aligned technical, completeness, privacy and submission requirements. Historical remediation notes and mockups do not override it. No second pitch backend is approved.

**Status:** AUTHORITATIVE for required behavior, data contracts and acceptance criteria. The
document that governs what each screen says and looks like is the approved
[screen-and-copy contract](docs/sentence-mining-flow.md); §4 below states the technical and
accessibility requirements those screens must meet. This document does not cover how the work is
built, tested or verified — that is [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) — or the
intent and tone behind it — that is [docs/DESIGN.md](docs/DESIGN.md). Document map:
[docs/README.md](docs/README.md). Target alternatives and any pitch-backend migration remain
proposal/approval gates; no second pitch backend is approved.

**Target:** next development revision after v0.4. This document defines required behavior and acceptance, not a claim that the current APK meets them. Changes land on dev; promotion to main and publication require a separate merge train and authorization.

**Product:** A personal, sideloaded Android app built with Kotlin and Jetpack Compose.

## 1. Purpose and quality contract

**Mission: get everything right the first time, without presenting unnecessary choices.** Mine one interesting Japanese sentence and one new vocabulary item, anchored by the situation and preferably a photograph. The normal journey is:

> Capture or type → choose the word (and meaning only if genuinely ambiguous) → inspect the card → send to AnkiDroid.

The user selects the vocabulary and intended meaning, glances at the completed note, may play the word pronunciation, optionally adds notes/tags, then sends. Listening to every sentence recording is not a prerequisite. Evaluate the chosen sentence voice before relying on this workflow.

The app creates Anki **notes**; AnkiDroid owns the note type, templates, cards, media copies, scheduling, and synchronization. This app is not a reviewer or collection editor.

### Required content

| Content | Completion requirement |
|---|---|
| Sentence and target | Nonempty Japanese sentence; one valid selected occurrence |
| Vocabulary and definition | Resolved dictionary entry/reading and intended meaning; ask only when interpretation is genuinely ambiguous |
| Sentence and vocabulary furigana | Complete readings for kanji spans; kana-only text needs no ruby |
| Pitch | A source-backed pattern for the selected vocabulary reading, retaining source phonetic annotations including nasalization |
| Word audio | A matching recorded pronunciation, not synthesized substitution |
| Sentence audio | Completed high-quality Japanese TTS for the current sentence |
| Translation | Completed English sentence translation; no free-form correction editor |
| Image | Retained for Photo input; preferred but optional for Type/text-share input |
| Notes and tags | Optional |

Missing required data, failed generation, unresolved readings and unfinished selected media **block export**. Never send a degraded note for later repair. A missing dictionary, pitch, or recorded-pronunciation entry is an accepted reason that a sentence/word cannot be mined with this app. Retain the draft and offer sentence/word/meaning correction, not an internal-field repair form. Log lookup misses locally (§11).

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
- Automatic dictionary/reading/pitch resolution; word selection and genuine meaning disambiguation; sentence editing, automatic enrichment, playback and new-note creation.
- Destination and default tags in autosaved Settings; defaults seed each fresh note, with separate per-note note/tag editors in Review.

### Excluded

Batch mining, draft libraries, background clipboard monitoring, generated mnemonic stories/images, vocabulary-knowledge tracking, review/sync/editing existing notes, deck/template installation, arbitrary field mapping, microphone/audio import/editing, image crop tools, generated-field correction workbenches (translation, definition, reading/furigana or pitch editors), pitch-alternative pickers, provider fallbacks, background generation queues, and automatic retry after restart. Durable background dictionary provisioning is explicitly included, not a generation queue.

Review is a native Compose presentation of the note content. The app does not render the Anki template or attempt pixel-identical card previews.

## 4. Workflow and native UI

### 4.1 Design rules

Use actual Compose Material 3 Expressive components, typography, color roles, shapes, state layers, and restrained transitions. Use a coherent light/dark theme; normal Material accent/error colors and appropriate motion are welcome. Pitch colors must remain distinguishable and must not be changed accidentally by theme tinting.

Use the approved contract's exact surface inventory, ordered contents and copy IDs; do not add headings, strings or controls independently. Every screen has one top task heading and no in-content section headings; fields keep concise labels. Settings is reachable at the top right of Capture, Target and Review. Sheets are left-aligned; definition previews use one ellipsized line without truncating stored/exported meanings. One actionable blocker owns its explanation/recovery, without duplicated footer/body status or treating pending work as failure.

Progress A is mandatory: no daily-flow step tracker/counter; one grouped busy indicator in Review's stable bottom action area, then the ready Send action. Pending speakers have accessible state, not individual spinners. Independently initiated retries may own one scoped indicator, never a second signal for the same work. Downloads/onboarding own one truthful UniDic transfer indicator, not Camera. Use short Expressive LoadingIndicator versus longer indeterminate progress according to expected duration, never invented percentages or a loading-to-determinate morph.

Use the theme's expressive motion scheme with spatial versus effects springs and native component behavior. Word selection and confirmed delivery are the expressive focal points; no gamification or new completion screen. Use sparse platform action haptics on committed selections, accepted shutter and verified delivery, respecting system preferences and avoiding lifecycle duplicates. Camera/Type uses a single-choice connected button group with stable external bounds. At least 8 dp chip spacing complements 48 dp effective targets. Reduced/disabled motion retains static busy semantics and immediate operability. See the design contract's component/motion tables and official sources; a lookalike custom animation is not compliance.

Support English UI, system light/dark mode, a portrait-only app composition with landscape photograph capture/import, 200% font scaling, TalkBack labels/states, at least 48 dp non-overlapping touch targets, 4.5:1 normal-text contrast and 3:1 actionable-icon contrast. Respect system-bar/keyboard insets; long content scrolls. Support reduced/disabled animation settings and do not use color alone to communicate state.

Validate production Compose components and the connected journey against the approved text contract. Existing HTML mockups are historical references, not authority to restore extra UI. One UI owner integrates shared screens; components and tests trace visible/accessibility copy to the shared catalogue. Intent and tone are in [docs/DESIGN.md](docs/DESIGN.md); the prototype-then-reuse working method is in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §10.

### 4.2 Capture

Offer **Camera** (initial default) and **Type** in a bottom connected single-choice button group, remembering the last used mode. Use CameraX with standard uncropped 4:3 capture and orientation-aware capture. The app interface remains portrait-only; landscape captures and picked/shared landscape images are stored orientation-corrected and displayed correctly in the portrait composition. Use a full-width uncropped viewfinder (upright portrait aspect 3:4) without synthetic masks/card padding/rounded clipping; ordinary chrome/extra space below is allowed. Preview position and dimensions must not move during capture, progress or errors. Reserve stable control/status slots and match saved bounds without stretching. Lock competing capture actions while a still is pending.

The sole title is Capture. Camera mode has Gallery left, shutter centred, an equal-width empty right slot and the Camera/Type group beneath; no Type caption beside the shutter. Type has one sentence field, optional photo/Remove photo and Attach photo, then Choose word. Resume draft replaces the Downloads app-bar action when a retained draft exists; Downloads remains reachable through Settings. Do not add a third trailing action, dashboard, subtitle or dictionary progress panel.

The usual sequence—rotate to landscape, capture, rotate back to portrait, continue—must preserve the photo and draft without switching the interface to a landscape composition. Store orientation-corrected media. Request camera permission through the normal Android flow; denial preserves work and leaves Type/image-picker entry usable.

Photo input uses the capture/picked/shared image as its contextual image. A new shutter/gallery input replaces a retained Photo draft only after replacement confirmation; it follows the normal OCR path. Do not add a photo-repair surface. Type input may attach or remove an image independently; attachments never run OCR or replace typed text. Input origin belongs to the draft, not the displayed mode switch; toggling modes cannot bypass the required-image rule. Back and Resume draft preserve the existing sentence/selection/results without recapture or network work.

### 4.3 Sentence, target, and meaning — local phase

1. Run OCR on a locally decoded source image **before** export downscaling/lossy encoding. Keep enough source detail to recognize small text; do not use the compressed 1080 px note image as the OCR input.
2. A single viable OCR passage proceeds directly to Target. Several plausible passages open one excerpt-choice sheet. Dismissal retains the photograph and a visible Choose sentence reopen action; it never silently commits a passage. Choosing text never crops the image. No bounding-box/zoom-coordinate editor.
3. Tokenize locally without blocking input. Target's title is Choose word. Its one sentence display consists of compact selectable word units in natural reading order, including visible nonselectable punctuation. No duplicate hero sentence. Preserve raw morphology/grouped units, Unicode and occurrence spans. A tap selects the whole word and immediately updates local lookup; no initial target, Extend/Trim controls or chooser on every tap. Edit sentence is left-aligned directly below the sentence and opens the sole sentence editor. An optional photo thumbnail opens an uncropped viewer.
4. Resolve supported dictionary spelling/reading/meaning automatically. Show the resolved headword, reading, intended meaning and source-backed pitch. Ask only about genuinely competing viable interpretations, through one combined meaning-choice sheet, not consecutive entry/sense forms. Change meaning appears only when an alternative exists. Complete definitions remain accessible without altering exported text. No free-form definition or multi-sense editing workbench.
5. Validate local reading/pitch availability and any locally searchable recording index before paid requests. A local miss keeps the selection and explains the problem; no adjacent-word guess or free-text replacement lemma.
6. **Review card** authorizes missing network preparation for the current selection and immediately opens Review. Enable it only after required local resolution and audio setup. Do not trigger network generation merely because text was typed or a token tapped.

A target identifies a span/occurrence, not a global text replacement. Preserve Unicode boundaries and highlight only the chosen occurrence. An inflection such as `食べた` remains in the sentence while vocabulary resolves to `食べる`. The vocabulary reading, pitch, and recording must agree on the chosen entry/reading.

### 4.4 Review entry — network work, not another screen

Freeze the preparation inputs for a generation revision. Start the required recording fetch, sentence TTS, and translation; independent operations may run concurrently. Finish image encoding without blocking the local choice flow.

Fresh explicit Review card authorizes work for the frozen revision upon entering Review: automatically obtain recorded word audio, synthesize sentence audio and translate without Generate taps or a generation page. Progress A puts one indicator and the preparing state in the fixed bottom action area while completed content remains readable. No per-item spinners, animated skeletons or processing checklist. Large speaker-icon controls provide playback/stop and accessible pending state; no auto-play. Recomposition, revisits and process restoration never duplicate paid work or automatically restart failed/interrupted operations. Send remains unavailable until all required content/media are ready. Manual Retry repeats only the safe failed item and preserves successful siblings. Permanent/input/setup errors require the contracted sentence/meaning/Settings route. Cancel or ignore obsolete work; no result may attach to a newer revision.

A required dataset miss is not fixed by repeatedly calling a service. Show that distinction and offer reselection/correction/discard. A network error is not logged as evidence that a dataset lacks the word.

### 4.5 Review and deliberately limited editing

Present a composed learning artifact: contextual image when present, prominent Japanese sentence with target/ruby, its translation immediately beneath the sentence, then a vocabulary/definition block with pitch and word playback. Sentence playback remains available but secondary. There are no permanent text boxes, mandatory row per stored field, empty image placeholder for Type, or always-present empty Notes row.

**Send to AnkiDroid** is the ready primary action. Only optional Notes and Tags are editable in Review, through separate content-linked editors: quiet Add note/Add tags when absent; tapping existing note/tag content when present. No combined metadata control or permanent empty fields. The sole heading is Review; translation sits directly beneath the sentence without a field heading. Settings preserves the draft; destination selection stays in Settings.

There is no preparation-corrections panel, catch-all repair menu or free-form translation/definition/furigana/reading/pitch editor. Back returns to the same Target selection; Edit sentence and word/meaning selection are the human correction path. New source input follows Capture replacement confirmation. If supported content cannot be derived, retain the draft and show the one actionable blocker; never invent values or ask the user to repair internal fields.

Scoped editors commit with Done. Keyboard Back dismisses the keyboard first; dismissing the editor preserves its previously committed value, not partial keystrokes. Settings alone autosaves. Source edits invalidate only dependencies (§10). Explicit safe retry keeps the same model/voice/provider. Listening before every export is not required.

### 4.6 Send and next note

While sending, keep the submitted content visible and freeze editing. Prevent duplicate taps. Show success only after the note, destination, and media references are confirmed; then return to blank capture with brief accessible feedback. Preserve settings and capture mode; seed the next note from default tags, not the sent note's overrides. Do not retain the prior sentence/target/media/notes.

Blocked or uncertain submission retains the same content with actionable recovery (§9). Never use the generation Retry button to repeat a possibly completed Anki insertion.

### 4.7 Setup and incoming content

Settings is its own native screen, not onboarding. Its one title is Settings. Use the exact contract order: AnkiDroid connection, Note type/Deck, protected Google Cloud API key, Sentence voice/Reload voices, Default tags, Dictionary/Downloads, Clear cache, Sources and licenses. No in-content headings, titled cards, provider/model description row or paid-test controls. Use shared theme/insets and icon Back, with **no Save button**. Autosave protected/debounced settings; Back flushes, and failure retains values and stays open with recovery. Defaults seed only fresh notes; changing preferences never retargets a locked submission.

Only while setup is incomplete, put the existing Finish setup action before that list and in Downloads; it reopens the first incomplete wizard step. No additional Capture banner/action. Native Note type/Deck/Sentence voice pickers reuse their field labels as the single title; selection commits and dismissal retains the old value. Sources and licenses is the existing Back-navigable notice surface, reachable before download/credential completion via Use app and Settings. A project-service/billing remedy is performed outside the app; Settings can replace the key, not enable a remote API. Do not add a cloud-console action or status dashboard.

On Settings open, load voices with an available key using Google Cloud `GET /v1/voices?languageCode=ja-JP`; provide a Reload action and filter to Japanese-capable Chirp 3 HD voices. Never synthesize paid previews merely on open. Populate a native selector from returned IDs, not a hardcoded catalogue. Show friendly names/metadata; retain the exact explicitly selected ID. Fence listing/cache/service status to credential and request revision. Expose loading/empty/auth/retry, honestly labelled cached offline display, and no selection when first fetch fails without cache. Onboarding uses the same settings/credential/listing contracts.

Keep the saved selection through a refresh failure. If a successful refresh no longer lists that voice, require reselection before new synthesis; never silently pick another. Changing the saved voice invalidates only sentence audio (§10), not translation, readings, word recordings, or image. Voice listing is a separate metadata operation and must not automatically synthesize paid previews.

AnkiDroid detection/API permission and required-field validation must succeed before export. A missing Google Cloud API key does not block capturing or locally analyzing a draft, but audio setup is required before Review card. Setup uses one Google Cloud project with billing and both Cloud Text-to-Speech and Cloud Translation enabled; restrict the key to those APIs. Keep failures typed per service and show the applicable catalogue recovery, not duplicate status rows. Voice listing validates speech access only, not synthesis quality or translation access. No paid test/sample controls are part of the approved UI; key entry/opening Settings never triggers speech or translation requests.

Text shares enter Type preparation; a single shared image follows Photo preparation. Neither guesses a target or sends automatically. Confirm before replacing a nonempty draft; declining preserves it. No broad clipboard or library permission, URL scraping, or multi-item share workflow.

### 4.8 First-run onboarding and resource provisioning

Onboarding is a real ordered wizard: **Dictionary → Camera → Audio → AnkiDroid → Ready**, not Settings over Camera. Each step has its own sole top heading; no welcome page, step strip or counter. Start UniDic early; Next advances once the task starts or a valid install is found, while provisioning continues. Camera denial leaves typing/gallery available; already granted permission passes through. Audio requires a saved protected key and explicitly chosen eligible voice. Resolve a sole compatible note type automatically; ask for a deck, never invent it. Unavailable AnkiDroid may be configured later, with export blocked until verified.

Finish requires validated dictionary installation plus saved key/valid voice; camera permission and AnkiDroid setup may be deferred. Use app while setup runs exits without falsely marking setup complete: capture/type/shared input is durable, analysis waits for UniDic and Review waits for audio setup. Ready shows four compact readiness rows, not extra headings or a second work checklist in the mining flow.

The final wizard step uses Finish setup as its sole title while incomplete, Ready only after required conditions hold; native status icons/semantics avoid repeating Ready in the body. Use app remains available on unfinished steps. Blank-key validation follows an attempted progression, not untouched initial entry. Scoped note editors likewise do not duplicate their title as another visible field heading. Viewer/picker titles and all nonvisual pitch/state descriptions come from the same catalogue; do not invent accessibility prose outside it.

The dictionary is a durable background task using supported Android facilities, independent of Camera/Settings/Activity lifetimes. Onboarding, Downloads and its notification refer to the same task. Preserve actual phase across backgrounding/process recreation and provide applicable pause/resume/cancel/retry without losing the draft. Resume must disclose a byte-zero restart before another large mobile transfer. One flat linear transfer indicator shows real bytes, then the same slot shows verification/installation without a fake overall percentage. Never put its progress panel in Capture. Default to Wi-Fi; mobile transfer needs size disclosure/consent, including after connectivity changes. Preserve pending incoming content and reuse valid installed data.

The only first-run dataset download is the UniDic CWJ 3.1.1 runtime ZIP (`446,602,958` bytes; SHA-256 `4963afe095a031b9a15e97325795cf28c772ff108138172e69c786cbc4c9b3fe`), delivered from the versioned v0.4 GitHub release asset. Pin the URL, exact byte count, hash and five expected archive entries in the build. Verify transfer length and SHA-256, validate ZIP paths/CRC/entry sizes, then extract to a temporary directory and require the exact `FILE_SIZES` set and `TOTAL_BYTES` before activating. Failed downloads or extraction must not activate partial data; a valid existing installation is reused. The archive is data, not executable code. Never ship placeholder URLs/hashes or call a local fixture a deployed download.

The Japanese ML Kit model, NHK-2016 pronunciation index, JMdict dictionary and AJT pitch database ship in the APK. OCR and NHK headword lookup therefore work offline immediately after install; local tokenization/linguistic preparation works offline after UniDic has been downloaded and validated. Individual NHK recording files remain an on-demand network operation (§6.1), not a first-run dataset download. Native libraries and required notices stay in the APK; runtime executable-code downloads are forbidden. Keep licenses/notices accessible before and after UniDic provisioning. Do not claim NHK recordings themselves are available offline unless cached.

## 5. Linguistic processing and AJT compatibility

### 5.1 Sources and matching

Use [AJT Japanese](https://github.com/Ajatt-Tools/Japanese) as the behavioral reference for furigana and pitch output. Pin the upstream revision and derived-data sources, preserve notices, and document transformations. Keep dictionary, morphological analysis, pitch, and audio identities separate.

Use packaged MeCab native code with verified provisioned UniDic for tokenization/lemma analysis and bundled JMdict for definitions and part of speech. Match entries using surface, lemma, and reading. Preserve entry/sense boundaries; no manual vocabulary fallback. Test actual mining examples, not only textbook-perfect strings.

**Single pitch backend:** pin AJT Japanese at `5729b8c080823eadaa121c76e0f23c55d6a81935` and ingest **all three** `pitch_accents_formatted.1.csv`, `.2.csv` and `.3.csv` parts as one dataset, matching the plugin's complete input enumeration. Hash/pin every part and preserve deterministic ordering/provenance and supplied nasal/devoicing markup. A part-3-only index is incomplete, not evidence of an upstream gap. Golden fixtures include 受付/うけつけ (heiban with devoiced ツ) and 出きる/出来る/出來る. No Kanjium fallback or backend migration without a separate approved proposal. Unmarked records mean no mark supplied, not verified absence of nasalization.

### 5.2 Furigana

Generate sentence and vocabulary readings locally. Follow AJT-style okurigana preservation and whole-word handling for irregular readings; preserve punctuation and the selected occurrence. Render native inline ruby in Compose; export Anki bracket-furigana compatible with the existing note type's `furigana:` filters.

Kana-only text is already complete. Every kanji-containing span needs a supported automatically resolved reading. Unknown/pending spans block Send; retain the draft and expose the contracted Edit sentence or meaning/word-selection route, not a manual reading editor. Do not silently export unresolved annotations or invent per-character alignment where only a whole-word reading is supported.

### 5.3 Pitch, nasalization, and HTML output

`VocabPitch` uses AJT **`html_and_number` with `HTMLPitchPatternStyle.none`**, which emits class-based spans: (`high`, `low`, `high_drop`, `low_rise`), nested `nasal`/`handakuten`/`devoiced` spans where supplied, and a trailing `pitch_number` span. Preserve nested structure and source phonetic distinctions. Golden fixtures include 悪逆 / アクギャク / accent 0, whose source marks the nasal キ and degree symbol inside a high span; the mark is not an extra mora.

Resolve one source-backed pattern consistently with the chosen spelling/reading and recording. The first valid source-listed match is deterministic, not a frequency/correctness ranking. Retain source alternatives internally; do not expose a pitch-alternative picker. An unresolvable pattern/recording conflict blocks instead of guessing. Missing pitch blocks export. Zero is valid heiban, never a missing sentinel.

Preserve complete mora sequences, contracted kana, small `っ`, `ん`, long vowels, source accent positions (including final-mora accents) and accent positions of ten or more in data/export/accessibility. Native diagram labels use katakana; solid and hollow markers have equal outer diameters and connector segments are clipped outside hollow interiors. End the drawn contour at the final mora circle: no extra tail or additional final-drop indicator. Do not discard source accent metadata to achieve this presentation. Permit scrolling for long readings; color alone cannot identify a pattern or selection.

Nasalization support is required at the dataset/renderer level, not a requirement that every word have a nasal mora. Preserve all supplied marks, disclose the source's incomplete annotation coverage in Sources, and never call an unmarked record verified non-nasal. A valid unmarked pitch record remains usable, as in AJT; a missing pitch record does not. No per-note phonetic-provenance editor is required.

The recording must match the selected vocabulary reading and numeric accent using NHK-2016 index metadata; spelling/headword lookup constrains candidates. Reject conflicts or an unresolvable match rather than silently using the first recording. Matching these values does not prove every phonetic detail agrees across dictionary editions. Do not claim such a guarantee merely because both sources are called NHK. Readings may be normalized for comparison using tested AJT-style rules, but retain original nasal marks for display/export.

## 6. Audio

### 6.1 Recorded vocabulary

Use the NHK-2016 pronunciation index at `b0207d240f0f28cdc2dd491dd83343c3a5630119` as the sole recorded-word path. Download recordings on demand; cache valid files. Select deterministically among matches for the resolved spelling/reading. No synthesized word fallback or audio-source picker.

Verify actual matching information, bounded downloads, response type, and playable output. Missing/unusable audio blocks export and retains the draft. A source update is a pin change. Keep source notices and distinguish the index license from recording rights. Personal use is not evidence of recording permission; do not make unsupported licensing claims.

### 6.2 Sentence TTS

Use **Google Cloud Text-to-Speech Chirp 3 HD directly**, with the Japanese voice explicitly selected in Settings (§4.7). Use synchronous file synthesis via `POST /v1/text:synthesize`, `languageCode = ja-JP`, the exact selected voice name, MP3 output, and a fixed delivery configuration. Validate decoding before marking complete. No voice cloning, recording, model catalogue, or fallback chain. Store the Google credential at runtime (§11).

**Phonetic hints are required, not an optional best-effort feature.** Build the synthesis request from the current sentence and its structured, automatically resolved readings. Send Google Japanese yomigana pronunciation overrides for resolved kanji-containing spans using `input.customPronunciations.pronunciations` and `PHONETIC_ENCODING_JAPANESE_YOMIGANA`. Kana-only text needs no fabricated hint. Do not send Anki bracket notation, ruby HTML, parenthesized reading prose, or conversational instructions as spoken text. Keep exported sentence text unchanged; avoid replacing the entire sentence with kana.

The hint builder must preserve inflection/okurigana and apply the sentence occurrence's reading, not blindly substitute the vocabulary lemma's reading. Do not apply one reading globally when the same spelling has different readings within the sentence. Use a tested occurrence-safe request representation, or report that the conflicting hints cannot be represented and block generation; never silently omit them. Reject invalid/unresolved readings before a paid request. Do not invent IPA, nasalization, or sentence-level pitch accents; yomigana hints specify readings, not a guarantee of dictionary accent in connected speech.

An unsupported or rejected hint request is a generation failure; do not retry without hints. Tests must inspect the serialized outbound request and audition ambiguous-reading fixtures with the selected voice; a successful response alone does not establish that Google honored the hints. Japanese hint support for the exact voice/API combination requires runtime verification (§15). Changes to the sentence or resolved reading identity invalidate hinted sentence audio (§10).

Target sentence-TTS spend is **at most USD 5/month at roughly two cards/day**. Review non-promotional rates, billing units, top-up fees, and a retry allowance. Quality acceptance prioritizes correct readings, counters, particles, omissions/additions, and natural Japanese prosody over expressive acting. Do not claim perfect pitch-controlled sentence speech.

### 6.3 Lifecycle and reuse

Generation begins automatically on Review entry authorized by fresh explicit Review card after local interpretation is resolved. Never repeat billable work automatically on revisit, recomposition or process restoration. Preserve completed matching results; explicit Retry resumes only appropriate transient/invalidated work, not unchanged permanent rejections. Cancel or ignore superseded responses. Audio for outdated inputs is unavailable for playback/export. Do not auto-play audio.

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

Evaluate NMT against representative short mining sentences before release (§15), including ambiguous vocabulary, omitted subjects, colloquial language and names. The app does not claim that translation is constrained to the selected dictionary sense. Require one nonempty English translation for the sentence; malformed, empty, failed or known-bad output blocks export. There is no free-form translation editor. Retain supported sentence/meaning correction routes and unchanged valid results (§10). Keep request serialization, bounded response handling, sanitized errors and injected transport tests separate from Compose; retry retains the same NMT path.

### 7.3 Notes and tags

Notes are optional plain text with line breaks preserved and HTML escaped. No generated mnemonic, rich-text toolbar, or separate source-entry feature; an existing template may interpret a user-written `Source:` prefix.

Tags are optional Anki metadata, not fields. **Default tags** in Settings seed every fresh note; absent defaults means empty tags. Review changes affect only the current note, never the defaults or next note's seed. Preserve active-draft tags when changing defaults. The scoped Tags editor has removable input chips and one entry field, supports hierarchical tags such as `JLPT::N4`, and commits with Done. No tag-management screen or additional suggestion/status UI is required. Existing source suggestions, if shown inside that editor, are dynamic content, not a new surface; their failure never blocks manual tags or export.

## 8. Anki output contract

Select an existing note type by name and remember its ID; map by exact field name, not order. The reference is `misc/Sentence (JA)/template.json`. Require the distinct field names below except the explicitly optional `VocabPartOfSpeech`; recognize and populate that field when present. Other extra fields are allowed and remain empty. Report missing/ambiguous fields and deleted model/deck identities without substituting another destination. Derive POS from the selected JMdict senses with inherited POS resolved, stable deduplication and safe escaping; absent source POS is not an export blocker and must not be invented.

| Field | Exported value |
|---|---|
| `SentKanji` | Original sentence; only the selected occurrence wrapped in controlled `<b>` markup |
| `SentFurigana` | Complete Anki bracket-furigana with the same highlighted occurrence |
| `SentEng` | Current completed English translation |
| `SentAudio` | Imported sentence audio reference |
| `VocabKanji` | Resolved dictionary lemma |
| `VocabFurigana` | Resolved vocabulary with Anki bracket-furigana |
| `VocabDef` | Source meaning of the resolved selected interpretation, safely serialized |
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

The contracted Open AnkiDroid action may use the source-supported `anki://x-callback-url/browser` route with encoded `search=nid:<noteId>`, targeted to AnkiDroid, after runtime verification. If that route is unavailable, open the app normally and retain the frozen draft/read-only recovery. Note/deck identifiers remain diagnostic/reconciliation data; do not invent a separate locator UI.

### 9.3 Submission and interruption

1. Freeze the complete revision; persist submission state and prevent concurrent sends.
2. Revalidate permission/model/deck and duplicate rule.
3. Import every selected media item and save returned references. Import failure prevents insertion; never silently drop a required item or create a broken reference.
4. Insert the note, retain its returned ID, establish its destination, and verify completion.
5. Persist confirmed completion, then reset the draft; the fresh note's tags come from Settings defaults, not the sent note's overrides.

During uninterrupted insertion, destination assignment may move only cards of the newly returned note belonging to this submission. Media import, insertion, and destination assignment are not assumed atomic.

If insertion may have happened, do not retry blindly. Persist the frozen fields, phase, import receipts, known note ID, and pre-insertion duplicate result. Reconcile read-only using model/fields/card-deck membership. A concurrent matching note is not proof of authorship. Reuse safe media receipts, but never delete collection media as rollback.

If the outcome remains uncertain, retain the submitted content and offer Check AnkiDroid, which reconciles read-only. If insertion is known but destination incomplete, use the catalogue's destination failure and Open AnkiDroid route; retain identifiers/receipts for diagnostics and recheck. **No recovery card moves, second insertion or extra manual-confirmation button.** Existence alone never proves destination/authorship; reset automatically only on verified completion. Do not promise exactly-once behavior the API cannot establish.

Read-only checking uses the contracted checking state in the same action area, never the sending state. Verification succeeds only on the required complete match; a proven pre-write failure permits safe Retry, while uncertainty retains the frozen snapshot and Check action. A missing query result alone does not prove no insertion occurred. Duplicate prevention before any write offers Open AnkiDroid and safe Back; it never reports another note as this submission's success.

## 10. One draft and deliberate rebuilding

Autosave committed source text/image, target/interpretation, current results, per-note metadata, generation status and submission receipts. Scoped editor keystrokes are not committed until Done; dismissal retains the previous value. Restore committed work after closure, rotation and process death. Durable media must not depend on temporary picker permission or evictable cache entries. Confirm before replacing nonempty work.

The visible lifecycle is **Capture → Target → Review → Send**, with generation inside Review, retained failure states and Resume draft. Submission recovery is separate from generation retry. Durable dictionary provisioning is the sole background setup task; no background generation queue or multi-draft repository.

| Edit | Recompute/invalidate |
|---|---|
| Sentence | Target validity, sentence readings, translation, sentence audio; re-resolve affected vocabulary |
| Target/resolved interpretation | Re-resolve vocabulary definition/reading/pitch/recording and highlight; invalidate affected ruby/hinted audio if reading identity changes; sentence-only translation remains valid |
| Meaning change with unchanged entry/reading | Definition only; sentence-only translation remains valid |
| Derived reading/hint-builder revision change | Re-resolve affected ruby/hints and invalidate sentence audio; no manual reading editor |
| Type image attachment/removal | Processed image only; not sentence/OCR |
| New Photo capture/gallery input | Confirm replacement, then normal OCR and new sentence/selection; not a photo-repair action |
| Notes/tags | No generation |
| Sentence voice change in Settings | Sentence audio only; regenerate deliberately with new voice and existing hints |
| Translation request-contract version change | Translation only |

Do not start billable work on every keystroke. Committed sentence/word/meaning changes resolve in Target; Review card deliberately rebuilds only missing/invalidated results. Reuse unchanged valid results. Cancel/ignore obsolete completions and never play/export mismatched audio. Returning unchanged to Review does not authorize another attempt.

There are no manual generated-field overrides or correction-replacement dialogs. Store one current value per result. Optional per-note notes/tags and successful unchanged siblings survive retry. Replacing a draft requires the one shared replacement confirmation; required unresolved values still block export.

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
| R03 | Fresh install/UniDic provisioning | Dictionary → Camera → Audio → AnkiDroid → Ready wizard; early UniDic task continues during setup/background/recreation; pause/resume/cancel/retry and Wi-Fi/mobile consent preserve draft. Real bytes/phase, SHA-256, exact archive entries and extracted size gates pass; failed data never activates. Bundled OCR/index work offline; tokenization works offline after validation. No provisioning panel in Camera |
| R04 | Landscape capture → portrait continuation | Correct orientation/full frame; portrait UI preserved; no lost photo, text, selection, or duplicate generation |
| R05 | One/multiple OCR regions and textbook text | Direct editable text for a single region; readable excerpt selection for several; correction before target selection; photo stays local |
| R06 | Type and share inputs | Typed/text-shared image is optional; image attachment does not replace text; image sharing follows Photo; replacement confirmation protects draft |
| R07 | Inflection, repeats, whole-word grouping, Unicode | One selectable sentence without duplicate hero; whole-word tap, correct lemma, one highlighted occurrence, intact surface/punctuation/characters, no adjacent-target guess or initial target; left-aligned Edit sentence |
| R08 | Ambiguous interpretations | Supported interpretation resolves silently and stays visible; genuine ambiguity uses one meaning sheet, not entry-then-sense forms; Change meaning only for viable alternatives; dismissal/reopen retains selection and never commits arbitrarily |
| R09 | Local dataset miss | Missing entry/pitch/recording-index match blocks the note, logs reason/source, preserves work, and avoids unnecessary paid requests |
| R10 | Automatic furigana | Okurigana, irregular whole-word readings and kana-only text handled; unknown kanji readings block with retained sentence/meaning recovery; no manual reading/furigana editor or fabricated alignment |
| R11 | AJT HTML pitch parity | Golden fixtures cover zero, final drop, long/contracted morae, accent ≥10, nasalization and devoicing; source and final Anki rendering agree |
| R12 | Recorded pronunciation | Correct spelling/reading/file; no synthesized substitution; missing/corrupt/unverified-match policy exercised; no unsupported pitch-match claim |
| R13 | Chirp 3 HD speech and phonetic hints | Outbound request includes current automatically resolved yomigana hints and exact chosen voice; audition ambiguity, inflection, repeated spellings with different readings, sentence edits, counters and kana-only input; no spoken markup/omissions/additions; rejected hints never trigger unhinted fallback; ≤USD 5/month estimate documented |
| R14 | Translation | Basic v2 explicitly sets Japanese → English, plain text and NMT; shared key in authentication header, not URL; sentence-only payload; representative live translations assessed; failure blocks; successful unchanged output survives retry/meaning-only edits; no translation editor |
| R15 | Offline/auth/quota/timeout/interruption | Specific retained failure, no provider/model switch, explicit Retry only, unchanged valid results reused |
| R16 | Generation race or process death | Durable work restored; no automatic billable restart; obsolete responses cannot attach or change exported notes |
| R17 | Image processing | Orientation/full frame, max 1080 long edge/no upscale, WebP/metadata removal, measured size/readability, decodable Android and Anki output |
| R18 | Incomplete required result or attached image | Send unavailable; no partial note or silent media omission; Type-only image removal remains possible |
| R19 | Committed source edit and draft replacement | Editor Done commits; dismissal preserves prior value. Sentence/word/meaning changes invalidate only dependent results; unchanged siblings and notes/tags survive. One replacement confirmation protects a retained draft; no correction-override dialog or stale export |
| R20 | Review composition and progress A | One top heading; sentence then unlabelled translation then vocabulary/pitch; separate optional note/tag editors; no repair workbench. One stable-footer preparing/sending indicator, no per-field spinners, no stage tracker, no generation screen; large speakers with accessible pending state; meaningful single blocker/recovery |
| R21 | Material/accessibility/navigation | Native expressive motion scheme and components/connected Camera-Type group; sparse system-respecting haptics without duplicate events; both themes, reduced/disabled motion, 200% fonts, TalkBack, 48 dp targets/8 dp chip spacing, numeric contrast, insets, Back and landscape-source capture. No extra headings or unapproved surfaces; emulator does not prove physical haptic feel |
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
| R33 | Review-entry audio | Fresh Review card authorizes one missing-operation attempt for a resolved revision; progress A groups initial work; isolated Retry repeats only safe failed item; no silent speaker or generic disabled-Send dead end, auto-play or paid duplication on revisit/recompose/process restore |
| R34 | Part-of-speech export | VocabPartOfSpeech receives available selected-sense JMdict POS with inheritance/deduplication/escaping; missing POS stays empty; older note types lacking the optional field still work |
| R35 | Bundle-by-default release packaging | APK is at most 130,000,000 bytes and excludes only the UniDic runtime ZIP; it includes bundled ML Kit Japanese OCR, NHK index, dictionary and pitch assets. UniDic is the sole first-run dataset download and must pass the build-pinned length/SHA/archive/extraction gates. Offline OCR and NHK headword lookup pass on a fresh install; per-word recording media remains on-demand. No executable-code download. |
| R36 | Exact-copy/surface compliance | Inventory all visible/accessibility/notification strings against design catalogue IDs and display conditions; reject extra/missing controls or headings, duplicated explanations and orphaned/unapproved copy. Verify every catalogued exception, not only happy-path screenshots |
| R37 | Durable setup and retained draft | Continue wizard during download; leave via Use app and resume setup without false completion. Recreate/kill/relaunch without duplicate task or paid work; Wi-Fi loss/mobile restart consent, cancel/retry and incoming shares preserve input; Finish enforces dictionary/key/voice; Resume draft restores the last stable work |
| R38 | Cross-process dummy AnkiDroid | Separate test APK/UID with supported provider/permission contract exercises the production gateway; opens granted URIs, persists decodable WebP/MP3/Ogg bytes and notes, returns real-shaped receipts. Inject permission/media/insert/mismatch/lost-response failures, double Send and recreation; no blind duplicate after durable receipts. No test control channel/dependency in production |
| R39 | Real AnkiDroid compatibility | Same media/fields/tags/deck assertions on supported real AnkiDroid in disposable isolated collection; verify note/media after URI grants are revoked and render them. A dummy/in-process fake alone never establishes compatibility |

## 15. Delivery and verification policy

For a separately authorized delivery, provide installable ABI APKs, checksums, setup instructions, screenshots and an acceptance report with explicit PASS, FAIL and NOT RUN results. The present design/specification approval authorizes dev integration and compliance work, not main promotion or a release. Configure credentials through app Settings; do not embed developer credentials.

The Google Cloud API key is not required for APK delivery. Exercise shared credential entry/access, separate service validation, translation request/response/error handling, voice-list filtering/state, synthesis/error handling, and cache behavior with controlled transport/provider tests. Without credentials, report live Google listing, synthesis, listening assessment, and translation quality as **NOT RUN**. Controlled-response tests support integration evidence but do not substitute for live-service verification. Missing in-app credentials block only the operations that require them, not local preparation.

Required verification areas:

1. **Linguistic data and rendering:** source licensing/attribution, derived-index fidelity, complete readings, Kotlin serialization fixtures, recording reading/accent matching, and reference-template rendering of nasalization/devoicing and pitch contours.
2. **Cloud operations:** Google Chirp 3 HD hint/voice/MP3 contracts, one protected shared API key with per-service validation, and Cloud Translation Basic v2 default NMT Japanese-to-English requests. Assess live pronunciation and translation quality when the key is available and the corresponding service is enabled; clearly distinguish it from controlled-response evidence. No provider substitution or unhinted retry.
3. **Images:** orientation, full-frame bounds, 1080 px limit, WebP quality 75, metadata removal, readability, and AnkiDroid rendering on realistic image fixtures.
4. **Platform and export:** Nix build/tests, both ABI packages, API-37 x86_64 runtime, AnkiDroid fields/media/destination, duplicate checks, and interrupted-submission recovery.
5. **Interaction and privacy:** approved screen/copy contract and progress A, responsive local preparation, native Material motion/haptic/accessibility evidence, durable background provisioning/drafts, SDK diagnostics, secret protection and local-photo processing.
6. **Import boundary:** separate test-only dummy AnkiDroid with real cross-UID URI access/persisted bytes, deterministic failures and durable-receipt recovery, plus the real AnkiDroid happy path. Follow the design contract's complete import-verification arrangement; no personal collection is used or replaced.

Do not silently remove required content, fabricate phonetics or test results, upload photographs, or export partial notes to satisfy acceptance.

How that evidence is produced, gated and recorded — clean-checkout build, evidence areas, gate ladder, verdict definitions — is in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md) §9.

## 16. References

This is the technical/content/safety contract for the next development revision. [The approved screen-and-copy contract](docs/sentence-mining-flow.md) controls all user-visible composition and wording; SPEC acceptance R01–R39 is current. Older matrices and mockups are historical evidence only. The intent and tone behind it are in [docs/DESIGN.md](docs/DESIGN.md); the engineering standards behind its build and verification requirements are in [docs/BEST-PRACTICES.md](docs/BEST-PRACTICES.md); the full document map is [docs/README.md](docs/README.md).

- `misc/Sentence (JA)/template.json` defines the reference field schema. `Recognition/front.html`, `Recognition/back.html`, and `template.css` provide the reference presentation. Validate their compatibility under §8.
- [AJT Japanese pinned source](https://github.com/Ajatt-Tools/Japanese/tree/5729b8c080823eadaa121c76e0f23c55d6a81935) — furigana behavior, pitch HTML, and phonetic data.
- [NHK-2016 pronunciation index](https://github.com/Ajatt-Tools/nhk_2016_pronunciations_index/tree/b0207d240f0f28cdc2dd491dd83343c3a5630119).
- [Bundled ML Kit Japanese OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
- [Android bitmap compression](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat#WEBP_LOSSY).
- [Google Chirp 3 HD](https://docs.cloud.google.com/text-to-speech/docs/chirp3-hd), [cloud voice listing](https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/voices/list), [synthesis input](https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/SynthesisInput), and [Japanese yomigana encoding](https://cloud.google.com/python/docs/reference/texttospeech/latest/google.cloud.texttospeech_v1.types.CustomPronunciationParams.PhoneticEncoding).
- [Cloud Translation Basic v2 translate method](https://docs.cloud.google.com/translate/docs/reference/rest/v2/translate), [translation authentication](https://docs.cloud.google.com/translate/docs/authentication), and [Google Cloud API-key restrictions](https://docs.cloud.google.com/api-keys/docs/add-restrictions-api-keys).
- [AnkiDroid integration reference](https://github.com/ankidroid/Anki-Android/tree/ebcf8e0e34921628b9b8a496c66ffd4adbb3705f/api).
- [Material Design 3](https://m3.material.io/).
