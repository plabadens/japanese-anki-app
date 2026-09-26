# Japanese Anki Card Creator — Product and Technical Specification

**Status:** Approved first-version requirements. External dependency verification remains subject to the release gates in this document.

**Distribution:** Personal, sideloaded Android application.

**Implementation status:** An initial Compose editor, local draft persistence, target-span validation, Android text-entry intents, AnkiDroid package detection, manual vocabulary/definition/reading entry, controlled field serialization/mapping, duplicate-key comparison, manual pitch editing, and stale-value blocking after sentence/target edits are implemented. AnkiDroid API permission requesting is implemented but untested on-device; model/deck discovery and Anki export, bundled linguistic processing, automatic enrichment/audio, media workflows, full acceptance testing, and reproducible APK builds remain unimplemented and unverified.

## 1. Purpose

Build a simple, modern, native Android application for quickly creating Japanese sentence-mining notes in AnkiDroid. The application brings selected functionality of AJATT's Japanese Anki tools to an input-focused mobile workflow: dictionary lookup, furigana, pitch metadata, pronunciation audio, and optional contextual media.

The application creates **Anki notes**, using an existing, user-selected AnkiDroid note type with the required field names. AnkiDroid generates the card from that note type. The application is not a reviewer, collection editor, synchronization client, or replacement for AnkiDroid.

### 1.1 Primary experience

> Enter a sentence → select a target → resolve dictionary ambiguity → automatically enrich → correct or supplement → send to AnkiDroid.

Optimize for short, repeated creation sessions. Keep required interaction small, but never hide uncertainty by inventing linguistic information or silently replacing manual work.

### 1.2 Normative language

- **Must / must not:** required first-version behavior.
- **Should:** preferred behavior where implementation details permit it.
- **Candidate:** selected integration direction that still needs the explicitly named verification before release.
- **Release gate:** a condition that must be resolved and evidenced before release; not permission to silently remove or substitute a requirement.

Low-level implementation choices not fixed here may be decided during development. Changes to approved user-facing behavior, dependency families, or scope require approval.

## 2. Platform and delivery

| Area | Requirement |
|---|---|
| Application language | Kotlin |
| Native user interface | Jetpack Compose with Material 3 |
| Android support | Android 15 minimum; Android 17 target |
| SDK configuration | `minSdk = 35`, `targetSdk = 37`, `compileSdk = 37` |
| Device ABI | `arm64-v8a` for physical phones |
| Emulator ABI | `x86_64` |
| APK delivery | One universal APK containing support for both ABIs |
| Build host | x86_64 NixOS |
| Build definition | `flake.nix`, pinned dependencies, sandboxed build |
| Reproducibility | Byte-identical unsigned APKs from identical inputs in two clean builds |
| Signing | Separate local step outside the Nix store |
| Distribution | Personal sideloading; no Play Store delivery requirement |

Android 15–17 must be supported; targeting Android 17 does not imply rejecting future compatible releases. Compatibility with future releases is not part of first-version acceptance.

All application-specific linguistic engines and datasets must be bundled. AnkiDroid, Android system facilities, installed Android TTS engines/voices, and explicitly configured online audio services are external dependencies, not assets that the APK must bundle.

## 3. Scope

### 3.1 Included

- One Japanese sentence and one selected target per note.
- Typing, ordinary user-initiated paste, Android text sharing, and selected-text actions.
- Automatic tokenization and manually adjustable contiguous selection.
- English dictionary lookup and selectable senses.
- Editable lemma and readings.
- Best-effort whole-sentence furigana and correctable target/vocabulary furigana.
- Optional word-level pitch metadata and structured correction.
- Recorded word pronunciation fetched on demand.
- Azure TTS with on-device TTS fallback.
- Microphone recording for sentence and vocabulary independently.
- One optional image from the photo picker or camera, with cropping.
- Optional manual translation, plain-text notes, and tags.
- One autosaved draft.
- Creation of new notes through the AnkiDroid API.
- Destination-deck duplicate blocking when sentence and vocabulary lemma both match.

### 3.2 Excluded

- Card preview, including rendering the supplied template inside the application.
- Card reviewing, scheduling, collection synchronization, or existing-note editing.
- Batch input, batch generation, or multiple targets in one note.
- Multiple saved drafts or a draft library.
- OCR or background clipboard monitoring.
- Audio-file import, trimming, waveform editing, noise reduction, or normalization controls.
- Automatic sentence translation.
- Known/new-word classification or vocabulary-knowledge tracking.
- Runtime dictionary downloads or a dictionary marketplace.
- Automatic installation or modification of Anki note types, card templates, fonts, or decks.
- Sentence-level intonation generation or guaranteed pitch-controlled TTS.
- Tablet-specific layouts or store-distribution features.

## 4. User interface and workflow

### 4.1 Editor

Use one scrolling editor rather than a wizard. The sentence and target selection appear first, followed by dictionary/enrichment results and optional content. Optional sections may be collapsed to keep the common path compact.

The interface must:

- Use English UI text and system light/dark theme.
- Support phone portrait and landscape without losing the draft or selection.
- Support font scaling, accessible touch controls, and meaningful TalkBack labels.
- Identify loading, missing, failed, stale, and manually edited values without relying on color alone.
- Allow playback of attached sentence and word audio.
- Show the chosen destination deck and current tags before sending.
- Provide a direct **Send to AnkiDroid** action.

There is no general **Generate** button and no card preview. Target selection and subsequent relevant changes drive automatic non-audio enrichment. Initial audio generation starts automatically once the target's entry is resolved; edits that invalidate audio require a targeted **Regenerate** action. Targeted retry, replacement, recording, and correction controls are permitted and necessary; they are not a separate general generation workflow.

### 4.2 Initial setup and incoming text

Before export is possible, the application must:

1. Detect AnkiDroid and obtain the required API permission through the supported Android flow.
2. Let the user select an existing note type by name and validate its required fields.
3. Let the user select an existing destination deck.

Register Android text share and selected-text (`PROCESS_TEXT`) actions. Accept shared plain text as a sentence in the editor, then use the normal target-selection flow; do not automatically guess a target or send a note. If an unfinished draft exists, ask before replacing its sentence, and preserve the draft if the user declines. Do not request broad clipboard or file access to implement these entry points. Sharing images, URLs without accompanying sentence text, and multi-item shares are not required.

The application must also provide settings for:

- Existing destination note type (selected by name, retaining its identity to detect deletion/renaming).

- Default tags.
- Azure key, region, Japanese voice, and speaking rate.
- Installed Android TTS engine/Japanese voice and speaking rate.
- Audio cache usage and clearing.
- Dataset/source versions and attribution.

Azure configuration is optional for note creation. Missing cloud credentials must not disable offline enrichment, recording, or export of an otherwise valid note.

### 4.3 Normal creation

1. Enter or paste a sentence.
2. Tokenize without blocking typing or the main UI thread.
3. Tap a token to select the target occurrence.
4. Adjust its boundaries if necessary to any contiguous span, including a multi-token phrase.
5. Look up candidate dictionary entries.
6. If multiple entries match, require confirmation of the desired entry before treating the vocabulary selection as resolved.
7. Automatically populate dependent definitions, furigana, and pitch after entry resolution; start initial sentence and word audio generation once for the resolved target. Later edits that invalidate audio expose **Regenerate** instead of silently requesting new audio.
8. Review and correct the results in the editor.
9. Optionally add translation, image, notes, or tags; optionally replace generated audio with a microphone recording.
10. Send directly to AnkiDroid after validation.
11. On confirmed success, display success feedback and immediately start a blank note.

The new blank note retains the last deck, configured default tags, and application settings. It does not retain the preceding sentence, selected target, attached media, notes, translation, or non-default tags.

### 4.4 Target semantics

- A target is an occurrence identified by its span in the original sentence, not a global string replacement.
- Highlight only that occurrence when the same text appears several times.
- Boundary adjustment must not split an encoded Unicode character or corrupt the text.
- A target may cover one token or a contiguous phrase; nonadjacent selections are excluded.
- The sentence retains the selected surface form.
- Vocabulary fields default to an editable dictionary lemma.
- Lookup failure for a phrase must permit manual vocabulary, definition, and reading completion.

Example: selecting `食べた` may resolve to vocabulary lemma `食べる`. The sentence still highlights `食べた`; vocabulary readings, pitch lookup, and default word audio use `食べる`.

## 5. Linguistic resources and processing

### 5.1 Selected stack

| Function | Selected candidate | Packaging |
|---|---|---|
| Morphological analysis | MeCab | Bundled native library for both ABIs |
| Morphological dictionary | Full UniDic | Bundled, pinned edition |
| English definitions | JMdict Japanese/English data | Bundled, locally searchable representation |
| Pitch metadata | Kanjium | Bundled, locally searchable representation |

Exact releases, UniDic edition, source URLs, checksums, and derived-data formats must be pinned during implementation and recorded in build inputs and release documentation.

JMdict is not a MeCab morphological dictionary. MeCab/UniDic, JMdict, and Kanjium have separate responsibilities and licenses.

### 5.2 Offline and coverage requirements

- Tokenization, lemma analysis, dictionary lookup, furigana, and pitch lookup must operate without a network connection after installation.
- Dataset coverage takes priority over APK size; no fixed APK-size cap is approved.
- Do not silently restrict selected datasets to common words to reduce size.
- Filtering JMdict to the approved Japanese/English content is consistent with scope.
- Display missing results honestly. Offline operation does not mean every word or name will be recognized.
- Package any necessary indexes so first use does not require downloading linguistic data.
- Perform any required local initialization with visible progress and without freezing the editor.

### 5.3 Dictionary resolution

- Use surface form, morphological lemma, and available reading information to produce candidates.
- Show enough spelling, reading, and meaning information to distinguish candidates.
- Confirm the entry when there are multiple matching entries.
- Default to the first sense of the resolved entry, not every sense.
- Allow selecting several senses and editing or replacing the resulting definition.
- Maintain the selected entry/reading as the basis for dependent pitch and audio lookup.
- Do not mix unrelated readings or senses merely because they share a spelling.
- Permit a fully manual vocabulary entry when no suitable result exists.

Definition serialization must preserve sense boundaries and safely handle dictionary/user text. Its exact list presentation is an implementation detail compatible with `VocabDef` and the supplied template.

### 5.4 Furigana

Attempt furigana for all kanji-containing spans in the sentence and vocabulary, not only the target. Missing readings outside the selected target are best-effort omissions, not export blockers.

Requirements:

- Use structured token/span reading controls rather than raw bracket notation editing.
- Preserve punctuation, sentence content, target occurrence, and okurigana.
- Align readings to kanji spans where supported by the analysis.
- Represent irregular readings at an appropriate whole-word span rather than inventing per-character readings.
- Identify unresolved readings for optional correction throughout the sentence.
- Require the selected target occurrence and kanji-containing vocabulary to have a resolved reading or an explicit decision to leave them unannotated; allow, but do not require, correction of other spans.
- Distinguish confirmed omission, best-effort unresolved non-target spans, and kana-only text from missing/unprocessed required target/vocabulary readings. Never fabricate a reading to make export eligible.
- Serialize to Anki bracket-furigana notation, including separators needed by Anki's parser.
- Ensure markup and separator insertion do not alter the intended rendered sentence.

Generated readings are suggestions, not assertions of linguistic certainty. Do not silently invent readings for unknown text.

### 5.5 Pitch metadata

- Match pitch by chosen vocabulary spelling and reading, not spelling alone.
- Suggest the source's first listed matching pattern and expose alternatives.
- Store/export one selected pattern only.
- Provide structured accent-position editing and an accompanying contour diagram in the editor. This diagram is not a card preview.
- Treat accent position `0` as an explicit heiban value, never a missing-value sentinel.
- Validate manual accent positions against the mora sequence.
- Correctly distinguish morae such as contracted kana, small `っ`, `ん`, and long vowels.
- Include devoicing/nasalization marks only when verified source data provides them; do not infer missing phonetic detail.
- Unknown pitch produces an empty `VocabPitch` and a nonblocking status.
- A source record's ordering must not be presented as a frequency or correctness ranking unless the source establishes that meaning.

Pitch metadata describes the selected vocabulary pronunciation. It does not describe the full sentence's intonation or guarantee agreement with synthesized or unverified recorded audio.

## 6. Audio

### 6.1 Source priority

**Vocabulary audio:**

1. Matching recorded pronunciation from the selected source, using a cached copy when available.
2. Azure TTS if configured and available.
3. Installed offline-capable Japanese Android TTS voice.

**Sentence audio:**

1. Azure TTS if configured and available.
2. Installed offline-capable Japanese Android TTS voice.

A microphone recording or explicit manual audio selection is protected from automatic replacement. The user may replace either sentence or vocabulary audio independently.

If all eligible sources fail, retain the other note data, show an inline audio error, and allow export without audio. Pending optional audio must not block export.

### 6.2 Recorded word pronunciation

The selected candidate is AJATT's **NHK-2016 pronunciation index**.

- Integrate a known source/index; download recordings on demand rather than bundling the complete collection.
- Pin the source integration/index version where practicable and document source identity.
- Cache completed downloads for reuse.
- Filter candidates using available spelling/reading information.
- Automatically choose the first matching recording and expose alternatives for listening and replacement.
- Show when pitch agreement is unverified.
- Never claim a recording matches the selected accent without supporting metadata.
- Treat missing or unusable recordings as a reason to continue through the TTS fallback chain.

**Rights gate:** verify authorization for accessing, saving, and using the underlying recordings before enabling the integration. A publicly accessible repository or its software license is not sufficient evidence of rights to NHK recordings. If this gate fails, request approval for another source or revised scope; do not silently ship the source or permanently remove recorded-audio functionality.

### 6.3 Azure TTS

- Use the user's API key and region, not an application-owned shared credential.
- Use an available Japanese voice and remembered speaking rate.
- Prefer Azure over on-device TTS where the fallback chain calls for synthesis.
- Save synthesized audio to a local file suitable for Anki media export; do not depend on synthesis at review time.
- Default sentence synthesis text to the original sentence without highlighting/Anki markup.
- Default word synthesis text to the selected vocabulary lemma.
- Provide a separate spoken-text override for each audio item without modifying card text.
- Do not automatically replace all kanji with kana for synthesis.
- Changing synthesis text, voice, or speed invalidates the corresponding generated-audio match; it does not automatically start another synthesis request.
- Surface authentication, quota, network, and service failures without exposing credentials.

The provider is approved subject to a Japanese voice audition, pricing review, API/output verification, and credential-handling verification. No current price or untested pronunciation quality is promised by this specification.

### 6.4 Android TTS

- Use an installed Android TTS engine; do not bundle an engine or voice.
- Discover/select a suitable Japanese voice that supports file synthesis.
- For the offline fallback, use a voice that is actually offline-capable.
- Clearly identify missing engines, missing voices, unsupported file output, or voices requiring a network.
- Do not silently introduce another network-dependent provider as an alleged offline fallback.

### 6.5 Recording

Provide **record, stop, replay, and replace** controls independently for word and sentence.

- Request microphone permission when needed.
- Preserve the draft if permission is denied or recording fails.
- Stop recording on interruptions where continuation is unsafe or unsupported.
- Preserve a recoverable completed portion where possible, without claiming an incomplete file is valid.
- Do not add trimming, noise reduction, normalization controls, or an audio-file import flow.

### 6.6 Playback, formats, and cache

- Allow playback before export and replacement/removal of attached audio.
- Display the active source so a recording, downloaded pronunciation, and synthesized voice are distinguishable.
- Select actual encoding/container formats during implementation and verify playback through the chosen AnkiDroid version; filename extensions must match content.
- Maintain a bounded reusable cache of **250 MB** for downloaded and generated audio.
- Cache identity must distinguish source identity and, for synthesis, text/override, engine, voice, and rate.
- Keep active-draft media outside the evictable cache or otherwise durably protected from eviction.
- Clearing reusable cache must not remove active-draft media or AnkiDroid's imported media.
- AnkiDroid owns its exported copies. Application cleanup must not delete collection media.

### 6.7 Automatic requests and lifecycle

For this personal application, configuring the online sources authorizes their automatic use within the selected workflow; no repeated disclosure or confirmation dialogs are required.

- Start each audio item's initial source-priority chain automatically once the selected target's dictionary entry is resolved (or its manual vocabulary is completed). Do not start audio merely because a token was tapped while entry ambiguity remains.
- Subsequent edits that invalidate an audio item's generated result, including sentence text, target/lemma/reading, spoken-text override, voice, or rate as applicable, mark that item's generated audio stale and **do not** automatically request another download or synthesis. Provide a per-item **Regenerate** control to start its source-priority chain on demand. A newly selected and resolved target begins a new initial generation; a text/setting edit to the current target does not.
- Do not attach stale generated audio to a new revision without explicit regeneration. Preserve microphone recordings and explicitly selected audio under the separate keep-or-replace rule.
- Avoid repeated requests for unchanged valid results.
- Cancel or ignore work for superseded sentence/target revisions.
- Do not let late responses overwrite a newer selection or modify an exported note.
- Do not silently retry potentially billable requests after process restart.
- Preserve completed results and show retry controls for interrupted work.
- Pending optional audio may finish while editing; sending a note excludes any optional result not ready in the frozen submission and does not wait for it.
- Background completion guarantees, persistent background audio jobs, and background recording are not required.

## 7. Images, translation, notes, and tags

### 7.1 Image

Support one optional image through the Android system photo picker or camera, followed by optional cropping.

Processing requirements:

1. Respect image orientation.
2. Apply the chosen crop.
3. Limit the longest edge to **2048 pixels**, preserving aspect ratio and never upscaling.
4. Remove location and EXIF metadata.
5. Encode a static **AVIF** using a balanced fixed lossy preset.
6. Store a durable processed copy for the draft and eventual media import.

Use an encoder that works on both supported ABIs. Prefer a suitable platform capability when available; a bundled encoder such as libavif plus a compatible AV1 encoder is an implementation candidate, not an already verified dependency.

Encoding must run off the main UI thread. Choose exact quality/speed settings through representative image tests, especially screenshots containing small text. The requirement is compact AVIF with acceptable readability, not an impossible guarantee of globally minimal file size.

An encoding failure must preserve the draft and permit retry or removal of the optional image. Do not silently change the required export format.

### 7.2 Translation

`SentEng` is optional, manually entered sentence translation. No translation API or machine-translation generation is included.

### 7.3 Notes

Notes are optional plain text. Preserve intended line breaks and escape text safely for the Anki field. There is no rich-text toolbar, raw HTML editor, or separate source field.

The existing template may interpret a note beginning with `Source:` specially; the application does not invent that format or require it.

### 7.4 Tags and destination

- Tags are Anki note metadata, not an extra model field.
- Allow editing note tags and configuring default tags.
- Support ordinary Anki tag conventions, including hierarchical tags such as `JLPT::N4`.
- Use the last selected existing deck for the next note.
- Apply configured default tags to a new note; do not carry over every tag from the previous note.
- Never silently substitute a different deck if the remembered deck is missing.

## 8. Anki output contract

### 8.1 Existing model requirement

`misc/Sentence (JA)/template.json` is a reference example, not a required model name, field order, or exclusive schema. The user selects an existing note type by name in settings. Validate that it contains the following **14 distinct field names**; map values by field name into the selected model's actual field order. Extra fields are allowed and receive empty values on new notes. Never assume an extra field can be populated by this application.

| Reference position | Required field | Value contract |
|---|---|---|
| 1 | `SentKanji` | Original sentence with the selected occurrence highlighted using controlled markup |
| 2 | `SentFurigana` | Best-effort whole-sentence Anki bracket-furigana, preserving the same highlighted occurrence |
| 3 | `SentEng` | Optional manual translation |
| 4 | `SentAudio` | Imported sentence audio reference, or empty |
| 5 | `VocabKanji` | Selected editable vocabulary lemma |
| 6 | `VocabFurigana` | Vocabulary in Anki bracket-furigana notation |
| 7 | `VocabDef` | Selected English senses or manual definition |
| 8 | `VocabPitch` | One selected template-compatible pitch pattern, or empty |
| 9 | `VocabAudio` | Imported vocabulary audio reference, or empty |
| 10 | `Image` | HTML image reference to imported AVIF media, or empty |
| 11 | `Notes` | Safely serialized optional plain-text notes |
| 12 | `fm_focus_words` | Empty |
| 13 | `fm_new_words` | Empty |
| 14 | `fm_seen_words` | Empty |

Before export, report missing or ambiguous required fields clearly; extra or reordered fields are valid. Do not repair the schema automatically. Handle model renaming/deletion, changes to its field list, and permission loss without losing the draft; require re-selection if the remembered model identity no longer exists. No manual field-mapping configuration is included. All required fields must exist even when their values are optional.

### 8.2 Serialization

- Treat typed/pasted content as text, not trusted HTML.
- Generate highlight, furigana, definition, pitch, and media markup through controlled serialization.
- Highlight only the selected occurrence with the `<b>` convention expected by the supplied template.
- Serialize Anki furigana as bracket notation rather than pre-rendered ruby HTML.
- Serialize audio using the actual media reference returned by AnkiDroid, typically `[sound:filename]`.
- Serialize images using the actual imported filename rather than a private application path or temporary content URI.
- Use `VocabPitch` classes understood by the template: `high`, `low`, `high_drop`, `low_rise`, and `pitch_number`, with source-backed phonetic classes where applicable.
- Keep numeric accent position and contour mutually consistent.
- Preserve a valid accent value of zero.
- Keep unknown pitch empty.

Use the supplied samples to exercise compatibility, but not as unquestioned linguistic truth. In particular, the sample for 勉強 appears to contain inconsistent contour and accent-number information.

### 8.3 Template ownership and known limitations

The application does not render or modify `Recognition/front.html`, `Recognition/back.html`, or `template.css`. The user is responsible for installing the chosen note type and any template-specific font/media dependencies in AnkiDroid. Required field names alone establish export compatibility, not visual or audio rendering compatibility with arbitrary templates; provide a usable setup procedure for the supplied reference template, including its media dependencies.

Known integration limitations to retain in test/release documentation:

- The supplied template displays only the first pitch reading. Exporting one chosen pattern is intentional.
- Its pitch-color code uses a single-digit match, so accent numbers of 10 or greater can be classified incorrectly. Export correct data; do not truncate or falsify the accent number to work around the template.
- Its mora-rendering assumptions must be checked against the application's serialization, especially small kana and source-backed phonetic marks.
- Template-side behavior or escaping defects must be reported as external compatibility issues rather than silently patched by the application.

## 9. AnkiDroid integration and export

### 9.1 API boundaries

Use AnkiDroid's supported Android API/content-provider integration, not desktop AnkiConnect or direct collection-database access.

Allowed collection access is limited to what creation needs: model/deck inspection, duplicate checking, media import, and new-note creation. Input-only scope does not prohibit those reads.

Do not create decks, change note types, update preexisting notes, delete collection content, or trigger synchronization.

The exact supported AnkiDroid release/API dependency must be pinned and validated before release. Do not claim support for every historical AnkiDroid version based only on an API specification number.

### 9.2 Export eligibility

A note is sendable when all of the following hold:

- Nonempty sentence and a valid nonempty target occurrence.
- Resolved vocabulary entry or manually completed vocabulary.
- Nonempty definition.
- Target/vocabulary reading processing complete, including explicit resolution of unknown kanji readings in the selected target occurrence or vocabulary; unresolved non-target sentence spans do not block Send.
- No unresolved stale required fields.
- AnkiDroid permission and validation of the selected model's required field names succeed.
- An existing valid destination deck is selected.
- Required target/entry processing and any prior export operation have settled; an active microphone recording must be stopped or discarded before Send.

Pitch, either audio item, image, translation, notes, and tags are optional. Missing or pending optional data must not become a hidden required field. Other sentence readings are best effort.

Keep Send available while optional enrichment, audio, or image processing runs. Show clearly which optional work is still ongoing and which not-yet-ready results will be omitted if the user sends now. Freeze the current eligible revision on Send; pending optional results are not imported or attached to that submission, and late results cannot update an exported note. Cancellation/retry controls remain available while editing. Inline optional errors do not require a separate dismissal step.

If a completed, selected optional media item cannot actually be imported, do not create a broken reference or silently omit it. Preserve the draft and allow retry or explicit removal. Media that is still processing may instead be explicitly omitted from the frozen submission as described above.

### 9.3 Duplicate blocking

Block a new note if an existing note in the **destination deck**, using the selected model, has **both** the same sentence text (without generated highlight markup) **and** the same vocabulary lemma. No override or merge is offered. A different sentence or a different lemma is allowed, regardless of vocabulary reading; two targets with the same lemma in the same sentence count as duplicates even if their occurrence or reading differs.

Comparison must decode serialized text, trim outer whitespace, and preserve internal text rather than applying aggressive linguistic normalization. Equivalent field markup should not create a false distinction. AnkiDroid's built-in first-field duplicate helper does not implement this combined, deck-scoped rule; check both fields against notes in the destination deck.

If the duplicate check cannot be completed, block Send with a retryable, actionable error rather than risk adding a duplicate. Never silently bypass the check or update an existing note.

### 9.4 Submission sequence

1. Freeze the validated draft revision for submission, explicitly exclude pending optional results shown to the user, and prevent concurrent submissions.
2. Validate permission, required model fields, and destination.
3. Complete the duplicate check; block insertion on a match or check failure.
4. Import only completed, selected media included in the frozen revision and retain the returned references.
5. Create the new note and establish its destination outcome.
6. Persist confirmed completion sufficiently to avoid accidental resubmission after interruption.
7. Show success and reset to a blank note.

Never clear the draft just because a request was initiated. Never let repeated taps create several submissions.

### 9.5 Partial failure and uncertain outcomes

Media import, note insertion, and destination assignment are not assumed to be one transaction.

- Preserve the draft and available import receipts on failure.
- Reuse safely reusable imported media references during retry where supported.
- If note insertion may have succeeded, reconcile the collection state before another insertion.
- Distinguish definite failure, confirmed success, and uncertain outcome.
- Do not promise exactly-once creation if the external API cannot establish it.
- If reconciliation cannot determine the outcome, ask for explicit user resolution rather than blind retry.
- Do not delete collection media or existing notes as an automatic rollback strategy.
- Do not update an exported note when late audio or lookup results arrive.

Failure recovery must respect the prohibition on silently editing existing collection content. Any repair that cannot be safely performed within the creation contract must be surfaced to the user.

## 10. Draft state, manual edits, and concurrency

### 10.1 One durable draft

Autosave the current sentence, selection, chosen entry/senses, generated values, manual corrections, statuses, optional content, and durable media references needed to resume work.

- Restore after normal closure, rotation, or process death.
- Ask before discarding a nonempty unfinished draft.
- Do not add a multi-draft list.
- Make media durable rather than depending indefinitely on temporary picker permissions or cache files.
- Keep confirmed export state distinguishable from an unexported draft across interruption.

### 10.2 Dependency invalidation

The implementation must track enough provenance to distinguish generated values from protected user work.

| Change | Affected dependent data |
|---|---|
| Sentence text | Tokenization, target validity, sentence furigana, sentence TTS, and any target analysis affected by the edit |
| Target span | Entry candidates, vocabulary data, sentence highlight, pitch, word audio |
| Vocabulary lemma/entry/reading | Vocabulary furigana, definitions as applicable, pitch, word audio; relevant target-reading alignment |
| Selected pitch | Pitch serialization and any asserted pitch-to-recording match |
| Spoken-text override, voice, rate | Corresponding synthesized audio |
| Image selection/crop | Processed image |

Rules:

- Automatically refresh affected untouched generated **non-audio** values; best-effort non-target sentence readings may remain unresolved without blocking export.
- Preserve manual corrections and mark affected values stale.
- Mark affected generated audio stale without automatic regeneration after an edit; offer **Regenerate** for the corresponding audio item. Initial audio generation for a newly resolved target remains automatic.
- Preserve microphone recordings and explicitly selected audio; require keep-or-replace confirmation before including them if their applicability is stale. Skipping stale optional audio must remain possible without blocking export.
- Resolve stale required fields before export; optional stale results can be omitted from the frozen submission.
- Missing optional results may remain empty; protected stale content must not silently be presented as freshly verified.
- Ignore or cancel results from obsolete revisions.
- A network failure must not erase successful offline enrichment.

The exact state-management architecture is not prescribed. Correctness and durability are required; unnecessary frameworks or speculative abstractions are not.

## 11. Privacy, permissions, and security

- No analytics, telemetry, advertising, or background clipboard access.
- Store drafts and settings locally.
- Exclude drafts, media, and credentials from Android backup/cloud restore.
- Protect stored API secrets with Android Keystore-backed protection.
- Keep secrets out of source control, Nix inputs/store paths, APK resources, logs, crash messages, and exported fields.
- Do not log sentence content, spoken-text overrides, private notes, or private media.
- Online pronunciation lookups and cloud synthesis necessarily disclose their request text/lookup terms to configured services; no additional unrelated network processing is allowed.
- Use secure transport and validate downloaded media before use.
- Request microphone, camera, and AnkiDroid access only through supported permission mechanisms and handle denial gracefully.
- Use the system photo picker rather than requiring unnecessary broad library access.
- Do not embed credentials in publicly readable media references or filenames.

Ordinary diagnostics may identify operation types, source versions, and sanitized error codes without exposing user content.

## 12. Build, data provenance, and reproducibility

### 12.1 Nix development environment

`flake.nix` must provide the pinned development environment needed on x86_64 NixOS, including compatible JDK, Android SDK/build tools, Gradle support, and native build tooling/NDK for both ABIs.

Document any Android SDK license acceptance or emulator prerequisites. Do not rely on undeclared globally installed tools.

### 12.2 Sandboxed build

- Declare and pin build inputs, including transitive dependencies and linguistic source data.
- Use fixed versions/content hashes rather than floating downloads of `latest`.
- After fetching declared inputs, the build itself must be network-independent and sandboxed.
- Bundle required runtime linguistic data/native libraries in the APK.
- Avoid timestamps, local paths, nondeterministic archive ordering, or generated metadata that changes the unsigned artifact between identical builds.
- Include any preprocessed dictionary/index generation in the reproducibility boundary.

A Nix development shell alone does not satisfy this requirement.

### 12.3 Reproducibility test and signing

Build the same revision and locked inputs in two genuinely clean build environments. Compare the resulting unsigned universal APK bytes/hashes; reusing a cached APK is not a reproducibility demonstration.

Reproducibility applies to the unsigned application artifact and bundled inputs, not to future online TTS responses, downloaded media, Android runtime behavior, or signed artifacts.

Provide a separate documented local signing/install procedure. Signing material must remain outside the Nix store and repository. Personal sideloaded updates must preserve the intended signing identity.

### 12.4 Dataset updates and attribution

- Provide a documented repeatable procedure to update dataset pins and rebuild derived assets.
- Record dataset source, revision/date, license, attribution, and transformations.
- Review relevant linguistic fixtures when updating data; source ordering and readings may change.
- Include a persistent About/Sources/Licenses screen and required bundled notices.
- Satisfy JMdict/EDRDG attribution and update requirements and the selected UniDic/Kanjium conditions.
- Do not assume an upstream software license automatically covers every incorporated dataset or recording.

## 13. Performance and resource behavior

The first version has no approved numeric latency target or APK-size cap. It must nevertheless remain responsive for ordinary sentence-entry use.

- Keep typing, selection, correction, and scrolling responsive while analysis, I/O, or encoding runs.
- Cancel/coalesce obsolete work rather than analyzing every historical edit to completion.
- Use progress and cancellation where a task can take noticeable time.
- Apply bounded network timeouts and make failures recoverable; exact values are implementation details.
- Avoid needless cloud requests and reuse valid cached audio.
- Handle storage exhaustion, malformed media, and interrupted writes without corrupting the saved draft.
- Measure native memory use, dictionary initialization, lookup behavior, and AVIF encoding on the actual supported environments before release.

## 14. Acceptance tests

Automate linguistic, serialization, state, and build tests where practical. Use real-device/emulator checks for Android permissions, microphone/camera, TTS engines, and AnkiDroid rendering/API behavior.

| ID | Scenario | Required result |
|---|---|---|
| A01 | Install on Android 15 ARM64 phone and Android 16/17 x86_64 emulators | Application starts, bundled native dependencies load, local enrichment works across the supported releases and both ABIs |
| A02 | Run without network after installation | Tokenization, JMdict lookup, furigana, and pitch lookup remain available |
| A03 | Select an inflected word | Sentence preserves surface form; vocabulary defaults to editable lemma |
| A04 | Select a repeated word occurrence | Only the chosen occurrence is highlighted in both sentence fields |
| A05 | Adjust selection across tokens | Contiguous phrase is preserved; lookup failure supports manual completion |
| A06 | Several dictionary entries match | User confirms the entry before resolved vocabulary-dependent population |
| A07 | Select several senses or enter a manual definition | Export preserves the selected meaning content safely |
| A08 | Unknown kanji reading in target/vocabulary vs elsewhere in sentence | Target/vocabulary requires manual reading or explicit omission; an unresolved non-target span is visible, correctable, and does not block Send |
| A09 | Kana-only sentence/vocabulary | No unnecessary annotation is required; furigana completion is valid |
| A10 | Irregular reading and okurigana | Valid span annotation renders correctly without inventing character readings |
| A11 | Heiban, atamadaka, nakadaka, and odaka data | Numeric position and contour agree; zero is not treated as missing |
| A12 | Missing pitch | Empty pitch field; valid note remains exportable |
| A13 | Multiple pitch variants | Alternatives are visible; one selected pattern is exported |
| A14 | Source-backed devoicing/nasalization | Compatible marks are preserved; absent data is not fabricated |
| A15 | Multiple recorded pronunciations after entry resolution | Initial audio starts automatically; first matching recording is attached when ready; alternatives can be played and selected; unverified pitch is labeled |
| A16 | Recorded audio missing; Azure available | Word synthesis falls back to Azure |
| A17 | Azure unavailable; offline voice installed | Synthesis falls back to suitable on-device file output |
| A18 | All audio sources unavailable | Inline optional failure; other data preserved; export allowed without audio |
| A19 | Edit spoken-text override, sentence, or voice after initial generation | Corresponding generated audio becomes stale, no automatic new request is made, and **Regenerate** uses the new inputs without changing card text |
| A20 | Record, interrupt, and replace audio | Permissions/errors are handled; recoverable data and other draft fields survive |
| A21 | Pick/capture and crop an image | Correct orientation, at most 2048 px longest edge, no upscaling, AVIF output, metadata removal |
| A22 | Screenshot with small text | Fixed AVIF preset retains acceptable readability and actual AnkiDroid displays it |
| A23 | Change target while requests run | Obsolete results cannot overwrite the new target |
| A24 | Change text after manual correction/recording | Protected values remain, become stale where appropriate, and are not silently replaced |
| A25 | Close/rotate/kill app with a draft | Saved work and durable media restore; billable work is not silently retried |
| A26 | AnkiDroid missing, permission denied, selected model missing required fields, or model removed | Clear actionable error; no collection modification or draft loss |
| A27 | Same sentence and lemma in selected model/destination deck, regardless of reading | Export blocked without override; a failed duplicate check also blocks with retry |
| A28 | Only sentence or only lemma matches; or the full match exists only in another deck | Export permitted by the app's deck-scoped combined duplicate policy |
| A29 | Tap Send while optional audio/image/non-target readings are still processing | Pending optional work and its omission are visible; valid required data can be sent without waiting; late results cannot alter the exported note |
| A30 | Repeated Send taps or interrupted submission | No concurrent submissions; uncertain outcomes are reconciled before retry |
| A31 | Media imported but note creation fails | Draft/import state retained; no blind deletion of collection media |
| A32 | Confirmed successful export | Correct note/deck/media/tags; blank editor with last deck and default tags only |
| A33 | Inspect exported fields in AnkiDroid | Required names receive valid furigana/highlight/pitch/media references; all `fm_*` values and any extra model fields are empty |
| A34 | Clear/reach audio cache limit | Reusable cache bounded; active draft and exported Anki media unaffected |
| A35 | Secrets/backup review | No credentials/content in logs or build outputs; private data excluded from Android backup |
| A36 | Two clean builds with identical pinned inputs | Unsigned universal APKs are byte-identical |
| A37 | Large fonts, TalkBack, dark mode, rotation | Essential editor controls and statuses remain usable without losing work |
| A38 | Share selected Japanese text or plain text from another Android app | Editor receives sentence without guessing target or sending; an existing draft is not silently replaced |
| A39 | Select a differently named note type with required fields reordered and extra fields | Field-name mapping exports valid values; extra fields remain empty; missing required fields block export |
| A40 | Repeated Send attempts after duplicate or interrupted duplicate check | Same sentence **and** lemma cannot be inserted by deliberate retry; missing check outcome never becomes an automatic override |

Template defects must be distinguished from application serialization defects. Accents of 10 or more and unusual mora sequences should be included in compatibility reporting even when an external template limitation prevents perfect display.

## 15. Release gates and unresolved verification

Product behavior is approved. The following external facts must be verified during implementation before release:

1. **SDK/toolchain:** pin working Android 15/API 35 minimum and Android 17/API 37 target SDK/toolchain, JDK, Gradle/Android plugin, NDK, and Nix inputs; verify native loading and platform compatibility on Android 15–17 and both ABIs.
2. **Linguistic stack:** select exact MeCab and full UniDic versions/edition; verify licensing, coverage, memory use, reading alignment, and reproducible packaging.
3. **JMdict/Kanjium:** pin snapshots; verify notices, transformations, lookup correctness, and source-backed phonetic capabilities.
4. **Recorded audio rights:** establish the permitted use of the NHK-2016 recordings and source. Do not enable it based solely on repository accessibility or a code license.
5. **Azure:** approve voice quality and price, verify region/key configuration, downloadable output, rate control, and safe credential storage.
6. **On-device TTS:** verify voice discovery, offline capability, file synthesis, and usable output with the selected test engine.
7. **AnkiDroid:** pin a tested release/API version; verify field-name mapping for reordered/extra fields, permissions, media transfer, destination-deck duplicate blocking (including permitting same sentence with a different lemma despite the first-field helper), and uncertain-outcome handling.
8. **AVIF:** select a working encoder and fixed preset, verify licenses/native packaging, acceptable processing behavior, and actual AnkiDroid rendering.
9. **Reproducibility:** demonstrate clean-build equality, not just dependency pinning or successful builds inside a shell.
10. **Template compatibility:** test generated notes with the supplied template in AnkiDroid and document external rendering limitations separately.

If a gate cannot be satisfied, return for an explicit decision. Do not silently substitute a provider, change image format, weaken reproducibility, require a network for linguistic enrichment, or omit the preferred recorded-word source.

## 16. Reference material

### Repository contract

- `misc/Sentence (JA)/template.json` — reference model name and field names; order is not required for a selected model.
- `misc/Sentence (JA)/Recognition/front.html` — field filters, highlighting, pitch rendering, and known limitations.
- `misc/Sentence (JA)/Recognition/back.html` — answer-side audio behavior.
- `misc/Sentence (JA)/template.css` — expected markup styling.
- `misc/Sentence (JA)/samples.json` — compatibility examples, not authoritative linguistic fixtures.
- `misc/Sentence (JA)/README.md` — template behavior and integration notes.

### External references consulted during specification

These references inform the design; implementations must pin appropriate versions rather than relying on moving default branches.

- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk)
- [AnkiDroid API source](https://github.com/ankidroid/Anki-Android/tree/main/api)
- [AJT Japanese](https://github.com/Ajatt-Tools/Japanese)
- [MeCab](https://github.com/taku910/mecab)
- [UniDic](https://clrd.ninjal.ac.jp/unidic/)
- [JMdict/EDRDG licensing and attribution](https://www.edrdg.org/edrdg/licence.html)
- [Kanjium data and licensing](https://github.com/mifunetoshiro/kanjium)
- [AJATT NHK-2016 pronunciation index](https://github.com/Ajatt-Tools/nhk_2016_pronunciations_index)
- [Azure text-to-speech REST API](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-text-to-speech)
- [Azure language/voice support](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support)
- [libavif](https://github.com/AOMediaCodec/libavif)

## 17. Decision precedence

This specification consolidates the approved interview outcomes. Later decisions supersede earlier suggestions:

- No preview, despite its inclusion in the initial workflow.
- Android text sharing and selected-text actions are included; no background clipboard access.
- No general Generate button; non-audio enrichment is automatic after target/entry resolution, while audio has automatic initial generation and explicit regeneration after edits.
- Dictionary ambiguity requires entry confirmation.
- Pitch and non-target sentence readings are optional, not export requirements.
- Word audio prefers downloaded recordings over TTS; recordings are not bundled in full.
- Online service configuration permits initial automatic audio requests without repeated consent dialogs; edits require explicit regeneration.
- Successful export starts a blank note instead of retaining the sentence for another target.
- Matching sentence **and** lemma in the selected model/destination deck blocks export without override; either value differing permits export.
- Images export as AVIF, not an unspecified general-purpose format.
- Android 15/API 35 is the minimum; Android 17/API 37 is the target.
- A reproducible unsigned APK build is required, not merely a Nix development shell.
