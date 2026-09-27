# Japanese Anki Card Creator — Product and Technical Specification

**Status:** Approved first-version requirements. External dependency verification remains subject to the release gates in this document.

**Distribution:** Personal, sideloaded Android application.

**Implementation status (existing implementation, not proof of conformance to the revised UI below):** The Compose editor, durable manual draft fields, target-span validation/selection, Android text-entry intents, AnkiDroid setup, controlled field serialization, duplicate blocking, manual pitch editing, and stale-value protection are implemented. Pinned JMdict/Kanjium data is indexed in SQLite for offline candidate/sense and pitch lookup. UniDic CWJ 3.1.1 and MeCab 0.996 are pinned and packaged; the editor wires token selection, orthographic-lemma lookup for supported inflections, editable target/vocabulary reading suggestions, and best-effort full-sentence furigana with per-token corrections. Settings now cover default tags, optional Azure configuration with Android Keystore encryption, Android TTS engine/installed offline Japanese voice discovery, and speech rates; speech generation and audio-cache controls are not yet connected. NDK CMake builds succeeded for `arm64-v8a` and `x86_64`, and a host JNI smoke test verified native feature output. The debug APK builds and has been smoke-tested on a Pixel 7/API 37: first-launch UniDic extraction, MeCab tokenization, and offline SQLite dictionary lookup work. The native library and APK pass 16 KB alignment checks. The Nix flake exposes core-test, unsigned APK, and debug APK targets with a Gradle dependency-lock bootstrap; the Gradle artifact lock is not yet populated, so Nix APK output and reproducible offline build verification remain pending. Audio generation, media workflows, remaining settings, and acceptance testing remain pending. AnkiDroid integration is wired against the v2.24.0 source contract (commit `ebcf8e0e34921628b9b8a496c66ffd4adbb3705f`, the release available on Google Play) but untested on-device; provider behavior with trailing empty fields remains a release gate.

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

- One Japanese sentence and one selected target per note, entered by photo capture (with on-device OCR) or by typing.
- One photo may serve as both the OCR source and the note's image, or the two may be set independently.
- Ordinary user-initiated paste, Android text sharing, single-image sharing, and selected-text actions, in addition to capture.
- Automatic tokenization and manually adjustable contiguous selection; changing the target is done by reselecting a span of the sentence, not by typing a replacement lemma.
- English dictionary lookup and selectable senses.
- Automatic best-effort sentence and vocabulary furigana, shown inline (ruby); unresolved readings remain unannotated and never block export. No manual reading or omission controls.
- Automatic word-level pitch pattern selection, with a picker exposed only when a spelling has more than one listed pattern.
- Recorded word pronunciation (NHK-2016) fetched on demand, preferred over synthesized audio.
- Azure TTS and Azure Translator, each with an on-device fallback.
- Automatic sentence translation, editable, marked when produced by the offline fallback rather than Azure.
- One optional image from the photo picker or camera, with cropping.
- Optional plain-text notes and tags; tags default to the previous note's tags and can be added from AnkiDroid's existing tag list.
- One autosaved draft.
- Creation of new notes through the AnkiDroid API.
- Destination-deck duplicate blocking when sentence and vocabulary lemma both match.

### 3.2 Excluded

- Card preview, including rendering the supplied template inside the application.
- Card reviewing, scheduling, collection synchronization, or existing-note editing.
- Batch input, batch generation, or multiple targets in one note.
- Multiple saved drafts or a draft library.
- Background clipboard monitoring.
- Microphone recording of sentence or vocabulary audio, and any related record/replay/replace controls.
- Manual audio source selection or freehand pitch-position editing; both are picked automatically, with alternatives (when they exist) chosen from a list, never drawn or typed.
- Audio-file import, trimming, waveform editing, noise reduction, or normalization controls.
- Known/new-word classification or vocabulary-knowledge tracking.
- Runtime dictionary downloads or a dictionary marketplace.
- Automatic installation or modification of Anki note types, card templates, fonts, or decks.
- Sentence-level intonation generation or guaranteed pitch-controlled TTS.
- Tablet-specific layouts or store-distribution features.

## 4. User interface and workflow

### 4.1 Interaction model

The application is not a single scrolling form. A compact **review card** — laid out like the eventual Anki note, one row per field — is the constant home screen once a sentence has a target; everything else is either automatic, a small icon on that card, or one **sheet** raised from the bottom for a specific, bounded decision. There is no general **Generate** button and no card preview.

The interface must:

- Use English UI text and system light/dark theme.
- Support phone portrait and landscape without losing the draft or selection.
- Support font scaling to 200%, accessible touch controls of at least 48 × 48 dp without overlapping hit regions, and meaningful row-specific TalkBack labels and selected/disabled states. Normal text must reach 4.5:1 contrast in both themes; actionable icons must reach 3:1. Reserve system-bar and keyboard insets, scroll long card/sheet content, and keep the active editor and its completion action reachable in portrait and landscape. Do not shrink text or pitch diagrams indefinitely to fit.
- Use exactly one neutral ink tone (light theme: a dark charcoal; dark theme: a light parchment tone) for every interactive control — buttons, selected chips, sheet titles. The only saturated color anywhere in the interface is the four-way pitch-pattern color coding described in §5.5, reused from the reference template's own palette (`misc/Sentence (JA)/template.css`); color never carries any other meaning.
- Identify loading, missing, and failed values without relying on color alone. A field still generating (typically an online-sourced one — audio, translation; local lookups such as furigana and pitch are fast enough to rarely show this state) shows a small neutral spinner in place of its usual icon or value, never a blank or stale-looking gap.
- Mark a field with something worth a second look — an ambiguous or missing pitch pattern, a translation produced by the offline fallback — with one small neutral icon, not colored, not a caption. That icon's only job is explanation: tapping it surfaces a short, dismissible note (e.g. "this spelling has more than one listed pattern — picked automatically"). It is not the field's edit entry point; the separate edit icon is.
- Never phrase visible interface text in terms of the interface's own mechanics — no "no sheet needed," no "tap to confirm the dialog." Say what happened or what to do, nothing about how the screen is built. Where there is nothing to confirm, the absence of a prompt is the signal; add no text to announce it.
- Allow playback of attached sentence and word audio from the review card.
- Show current tags before sending. The destination deck and note type are Settings facts (§4.9), not something the review card redisplays.
- Provide a direct **Send to AnkiDroid** action from the review card.
- Offer a Settings entry point on the capture screen (§4.2) — its sole placement — so setup (note type, deck, Azure) can happen before a first sentence exists. Place it in the capture screen's own mode-switch row, to the right of the Photo/Type control; it is application content, not a system status-bar element, and must never be drawn into the OS status bar area.

Automatic choice sheets are raised only for OCR-region or dictionary-entry ambiguity. A single match resolves silently. User-requested editing sheets, draft-replacement confirmation, and error recovery are separate, explicit cases; they do not violate this rule. Pitch alternatives appear only when the user opens Edit word, never as an automatic interruption.

Once a target has resolved, the review card (§4.5) is the ground truth for the rest of the session: every subsequent screen — dictionary/pitch/definition sheets, the sending state, success, duplicate blocking, uncertain-outcome recovery, and draft-preserving error handling (§4.10) — is that same card, frozen where noted, with a sheet or a small status change layered on top of it. Nothing replaces it with an unrelated full-screen layout.

Reference mockups for every screen described in this section are in `mockup/screens/` (see `mockup/mockup.html` for the annotated walkthrough); `mockup/screens/00-pitch-component.png` is the canonical reference for the pitch diagram used throughout (§5.5).

### 4.2 Capture

Entering a sentence has two modes, switched with a small control that remembers whichever was used last:

- **Photo** (default): the camera viewfinder is the first thing shown. Capturing or picking a photo runs on-device OCR and, by default, uses the same photo as the note's image. A photo containing more than one plausible block of text raises a sheet to pick which one was meant; a single clear block fills in without one.
- **Type**: a plain text field, with an optional, independent image attachment offered alongside it.

The photo and the sentence text are independent pieces of state even when Photo mode links them by default: the review card's image row can swap or remove the linked photo at any time without touching the sentence, and Type mode never implies an image at all. This covers, without additional modes, all of: the same photo serving both roles; a photo scanned for text with a different image wanted; typed text with an optional image; and text with no image at all.

Android text sharing, selected-text (`PROCESS_TEXT`) actions, and image sharing remain further entry points (§4.8): shared text populates the sentence field directly, in Type-mode form; a shared image is treated exactly as a just-captured Photo, running the same on-device OCR and ambiguity handling as §4.2's Photo mode.

Both capture modes show the sole Settings entry point (§4.9) in the application row, to the right of the Photo/Type control and outside the OS status bar, so note type, deck, and Azure can be configured before a first sentence exists. The review card has no Settings entry point.

### 4.3 Target and dictionary resolution

Tokenization runs automatically and without blocking typing. The sentence text and its tokens appear on one screen: tapping a token selects it as the target; the sentence text itself is directly editable on this same screen, so a misread from OCR is fixed exactly where it is noticed, not on a separate confirmation screen first. Editing the text re-tokenizes it.

If dictionary lookup for the target's surface form or analyzed lemma returns more than one matching entry, a sheet overlays the complete target screen (mockup 09), retaining its sentence, image, tokens, and selection beneath the dimming. The sheet requires confirming the entry before treating the vocabulary as resolved; a single match resolves silently.

### 4.4 Target semantics

- A target is an occurrence identified by its span in the original sentence, not a global string replacement.
- Highlight only that occurrence when the same text appears several times.
- Boundary adjustment must not split an encoded Unicode character or corrupt the text.
- A target may cover one token or a contiguous phrase; nonadjacent selections are excluded.
- The sentence retains the selected surface form.
- Vocabulary fields use the resolved dictionary lemma; change it by reselecting the target or dictionary entry, never by typing a replacement lemma.
- Lookup failure preserves the sentence and selected span. Offer editing or reselection (§4.10), not manual vocabulary entry or automatic expansion to a nearby span.

Example: selecting `食べた` may resolve to vocabulary lemma `食べる`. The sentence still highlights `食べた`; vocabulary readings, pitch lookup, and default word audio use `食べる`.

### 4.5 The review card

Once a target resolves, the review card populates automatically: sentence (with the target highlighted and, per §5.4, resolved kanji spans shown as inline ruby), vocabulary with its own furigana and a pitch diagram (§5.5), definition, and sentence translation (§7.2). Each row shows the field's value plus, where applicable, a small neutral **play** icon and a small neutral **edit** icon — never more than that. A field with something worth a second look (an ambiguous or missing pitch pattern, a translation produced by the offline fallback) carries one additional small neutral mark; per §4.1, that mark explains itself on tap and is never the row's edit entry point.

The vocabulary row's pitch diagram uses the compact scale from §5.5 without kana labels under the dots: the vocabulary's own ruby provides its reading. The sentence target may be an inflected surface form with a different reading. Unknown readings are not invented to populate either ruby or a pitch diagram.

Editing behavior per row:

- **Image**: shown as a plain square at the top of the card, left-aligned like any other row, with no caption. Its edit icon opens a sheet to swap or remove it.
- **Sentence**: its edit icon returns to the capture/target screen described in §4.3.
- **Vocabulary**: its edit icon opens the **Edit word** sheet — the sentence's tokens again, to reselect which span is the target (there is no free-text lemma or reading field; the resolved entry can also be changed from this sheet) — followed by the pitch pattern picker described in §5.5 when more than one pattern is listed.
- **Definition**: its edit icon opens a sheet listing the resolved entry's senses as selectable checkboxes, with the combined result also directly editable as text.
- **Translation**: directly editable in place; saving an edit clears the local-fallback mark if the new text no longer needs it.
- **Notes**: an optional value with an always-present row, empty by default, expanding to a plain text field on tap.
- **Tags**: shown as chips seeded from whatever tags the previous note used; a trailing "+" opens a sheet listing tags loaded from AnkiDroid plus a field to create a new one.

The card's bottom action area is a single region that changes state rather than being replaced by a different screen: a **Send to AnkiDroid** button once required fields are complete; a disabled, spinner-marked "Sending…" state while submission is in flight, with the rest of the card — including an empty Notes row exactly as elsewhere — unchanged and fully legible; on success, a small neutral confirmation in the same area immediately before the card is replaced by a fresh blank capture screen; on a blocked duplicate or an uncertain outcome, a small neutral status message with its recovery action(s) in that same area. During sending, success confirmation, duplicate blocking, and uncertain-outcome recovery, the rest of the card remains exactly as submitted, including the Notes row whether empty or populated, until success resets to capture or the other outcome is resolved (§9.4, §9.5). The note type and destination deck are configured in Settings (§4.9) and are not redisplayed on the card itself.

A field's explanatory popover (§4.1) is non-modal and associated with the tapped mark. Keep Send and field controls unobscured; if space is tight, place the explanation in the card's flow rather than clipping it. All rows remain accessible by scrolling. An optional-enrichment status can offer Retry for a failed/interrupted item; this is not a control for regenerating an already completed result.

### 4.6 Audio

Word and sentence audio generate automatically once their inputs resolve, following the source priority in §6.1, and are played from the review card's neutral play icons. There is no manual **Regenerate**, **Record**, or source-selection control: saving an edit to the sentence text or to the selected target (via the Edit word sheet) is itself what invalidates and silently regenerates the affected audio. A failed or still-generating item shows its neutral state icon in place of the play icon rather than blocking the row.

### 4.7 Normal creation

1. Capture a photo or type a sentence (§4.2).
2. Resolve OCR ambiguity if more than one text region was found.
3. Tap a token, or a contiguous phrase, to select the target; edit the sentence text on the same screen if needed.
4. Resolve dictionary ambiguity if more than one entry matched.
5. The review card populates automatically: furigana, definition, pitch, translation, and both audio items begin generating.
6. Correct any field directly on the card, or via its sheet (§4.5); optionally add notes, adjust tags, or attach a different image.
7. Send directly to AnkiDroid after validation.
8. On confirmed success, display success feedback and immediately start a blank note.

The new blank note retains the last deck, the previous note's tags (§7.4), application settings, and whichever capture mode (Photo or Type) was last used. It does not retain the preceding sentence, selected target, attached media, notes, or translation.

### 4.8 Initial setup and incoming text

Before export is possible, the application must:

1. Detect AnkiDroid and obtain the required API permission through the supported Android flow.
2. Let the user select an existing note type by name and validate its required fields, in Settings.
3. Let the user select an existing destination deck, in Settings.

Register Android text share, image share, and selected-text (`PROCESS_TEXT`) actions.

- Shared plain text populates the sentence field in Type-mode form, then uses the normal target-selection flow.
- A single shared image is treated exactly as a just-captured Photo (§4.2): the same on-device OCR and text-region-ambiguity handling applies, and it becomes the note's image under the same default-linked, splittable rule.
- Neither entry point automatically guesses a target or sends a note.

If an unfinished draft exists, ask before replacing its sentence, and preserve the draft if the user declines. Do not request broad clipboard or file access to implement these entry points. URLs without accompanying sentence text and multi-item shares are not required.

### 4.9 Settings

Settings holds what is configured once and rarely revisited, with **Sources & licenses** as its last section rather than a separate top-level screen:

- Existing destination note type (selected by name, retaining its identity to detect deletion/renaming).
- Existing destination deck.
- A seed set of default tags, applied only to the very first note on a fresh install; every note after that seeds its tags from whichever set the previous note was sent with (§7.4).
- Azure multi-service resource key and region, Japanese TTS voice, and speaking rate. The single-key setup requires a resource that authorizes both Speech and Translator, not merely a shared Azure account; validate the two services separately and use their respective local fallbacks on failure (§7.2).
- Installed Android TTS engine/Japanese voice and speaking rate — the offline fallback for audio only. Translation uses a separate bundled ja→en model and never depends on the selected TTS engine or voice.
- Audio cache usage and clearing.
- Dataset/source versions and attribution.
- **Sources & licenses**, at the bottom of this screen.

Azure configuration is optional for note creation. Missing cloud credentials must not disable offline enrichment or export of an otherwise valid note; every online service in §6 and §7.2 degrades to a local alternative.

The Azure key is entered here, at runtime, and encrypted at rest (§11) — it is not embedded at build time. This was reconsidered given the application's single-user, personally-sideloaded nature and reaffirmed: a build-time secret would sit in the APK's resources, which §11 already prohibits for exactly this kind of credential, and would conflict with §12's requirement that the sandboxed build be reproducible and network-independent once its declared inputs are fetched — a personal key is not a fixed, publicly declarable input. Runtime entry also allows rotating the key or changing region without a rebuild. Settings-based configuration stands.

### 4.10 Dictionary misses and error recovery

- **No usable dictionary entry for the selected span.** Preserve the sentence, image, selection, and other draft data. A short sheet says “No dictionary match for this selection” and offers **Edit or reselect**, returning to the target screen. The user may fix OCR/text or choose another contiguous span. Do not silently select an adjacent word, declare the whole sentence non-Japanese, or invent a manual vocabulary entry. Export waits for a resolved dictionary entry.
- **Unexpected error.** Preserve the last durably saved draft. Explain that work was kept and offer **Return to draft**; offer Retry only when the failed operation is safe to repeat. Specifically defined permission, optional-enrichment, image-encoding, duplicate, and uncertain-submission errors retain their own handling. Never retry a possibly completed insertion through a generic error action.
- Error-sheet dismissal does not discard work. Starting over requires an explicit discard action and confirmation for a nonempty draft (§10.1).

### 4.11 Navigation and editing completion

Android Back dismisses the keyboard first, then the current sheet, or returns to the preceding screen without deleting the draft. Settings has a visible back control; from the review hub, Back returns to capture with the draft intact, making its sole Settings entry reachable. Returning from Settings restores the draft; changing the note type/deck invalidates export validation, not sentence content.

A token tap selects one token. Long-press begins a contiguous multi-token selection with adjustable start/end handles; provide equivalent accessible start/end actions. Never split a Unicode character or silently expand the target. Dictionary ambiguity may be reopened from Edit word to choose a different entry for the same span.

Discrete choices (image, dictionary entry, pitch, tags) apply immediately and autosave. Definition, Notes, and translation text editors expose **Done**, which closes editing and saves; text changes are also durably autosaved. Dismissing editing preserves current text. Settings credential/voice forms use **Save settings**; leaving with unsaved changes asks whether to discard those settings edits, without discarding the note draft. Tapping a selected tag in Add tag removes it; selecting an unselected tag adds it.

While submitting or reconciling an uncertain insertion, freeze editing and keep the submitted values legible. Show one state at a time: checking (no manual-resolution buttons yet), then unresolved (open AnkiDroid to check, confirm it exists, or request a checked retry). A retry always revalidates and reruns duplicate detection; it is never an override. Confirmed success resets to capture with a brief, accessible “Sent” message; do not require waiting on a separate success screen.

## 5. Linguistic resources and processing

### 5.1 Selected stack

| Function | Selected candidate | Packaging |
|---|---|---|
| Morphological analysis | MeCab | Bundled native library for both ABIs |
| Morphological dictionary | Full UniDic | Bundled, pinned edition |
| English definitions | JMdict Japanese/English data | Bundled, locally searchable representation |
| Pitch metadata | Kanjium | Bundled, locally searchable representation |
| Sentence/word OCR | Candidate: on-device Japanese text recognizer, source TBD | On-device only; no network call for text recognition |
| Translation fallback | Candidate: compact bundled ja→en model, source TBD | Bundled native runtime + model, pinned |

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
- Do not offer manual vocabulary entry when no suitable result exists; preserve the draft and offer text editing or target reselection (§4.10).

Definition serialization must preserve sense boundaries and safely handle dictionary/user text. Its exact list presentation is an implementation detail compatible with `VocabDef` and the supplied template.

### 5.4 Automatic furigana

Attempt readings for all kanji-containing spans in the sentence and resolved vocabulary. Readings are automatic suggestions, not assertions of linguistic certainty. There is no manual kana editor, raw bracket-notation editor, or explicit “leave unannotated” step.

- Preserve punctuation, sentence content, target occurrence, and okurigana.
- Align readings to kanji spans where supported; use whole-word spans for irregular readings rather than inventing per-character readings.
- Show resolved spans as inline ruby. Show an unresolved kanji span with a dotted underline and no ruby, including when it is the target or vocabulary. Kana-only text is plain. A contextual accessibility description explains missing readings; the underline is not an edit control.
- Missing, failed, or pending furigana never blocks Send. Export unresolved spans as their original text without fabricated annotations. The vocabulary dictionary entry and definition must still resolve (§9.2).
- Correct underlying text, target, or dictionary-entry selection through the normal editors; those changes rerun automatic analysis. Do not expose a separate reading-override path.
- Serialize resolved spans to Anki bracket-furigana notation, including required separators, without altering the intended rendered sentence. Snapshot only available readings on Send; late readings cannot modify the exported note.

### 5.5 Pitch metadata

- Match pitch by chosen vocabulary spelling and reading, not spelling alone.
- Select the source's first listed matching pattern automatically and silently; do not interrupt with a confirmation sheet merely because a pattern was chosen.
- When a spelling has more than one listed pattern, expose the alternatives only inside the word-edit sheet (§4.5), as a pick-list drawn from the source's own listed patterns for that spelling/reading — never freehand accent-position entry, typing, or a draggable control.
- Store/export one selected pattern only.
- Render pitch as a per-mora block diagram: one block per mora, a dot placed high or low within it, blocks connected by the contour line. Color the whole diagram by pattern type — heiban, atamadaka, nakadaka, or odaka — using the same four hues as the reference template (`misc/Sentence (JA)/template.css`), adapted per light/dark theme; this is the only chromatic UI element in the application (§4.1). Never label the diagram with the pattern's name or accent number in the interface.
- Two scales share the same drawing rules (`mockup/screens/00-pitch-component.png`) but differ in one respect: the **full** scale, used in the word-edit sheet's pick-list, prints the mora's own kana beneath its dot, since nothing else on that sheet shows the reading; the **compact** scale, inline in the review card, omits the kana entirely, since the vocabulary's own ruby shows its lemma reading (§4.5).
- Treat accent position `0` as an explicit heiban value, never a missing-value sentinel.
- Correctly distinguish morae such as contracted kana, small `っ`, `ん`, and long vowels.
- Include devoicing/nasalization marks only when verified source data provides them; do not infer missing phonetic detail.
- Unknown pitch produces an empty `VocabPitch`: no diagram is shown, only the small neutral mark described in §4.1, and the note remains exportable.
- A source record's ordering must not be presented as a frequency or correctness ranking unless the source establishes that meaning.
- Preserve source reading identity and full mora count. For long words, allow horizontal diagram scrolling rather than truncation or unreadably small kana. Show selection with a neutral check/radio and outline, not pitch color alone. An unlabelled trailing contour segment distinguishes a final-mora drop (odaka) from no drop (heiban); it is a boundary cue, not an extra mora or a claim about sentence intonation. TalkBack describes the reading and drop position even though visible numeric/name labels are omitted.

Pitch metadata describes the selected vocabulary pronunciation. It does not describe the full sentence's intonation or guarantee agreement with synthesized or unverified recorded audio.

## 6. Audio

### 6.1 Source priority

**Vocabulary audio:**

1. Matching NHK-2016 recorded pronunciation, using a cached copy when available.
2. Azure TTS if configured and available.
3. Installed offline-capable Japanese Android TTS voice.

**Sentence audio:**

1. Azure TTS if configured and available.
2. Installed offline-capable Japanese Android TTS voice.

Each item's source is chosen automatically, in this order, with no manual source-selection control anywhere in the interface.

If all eligible sources fail, retain the other note data, show that item's small neutral state icon in place of its play control (§4.1), and allow export without audio. Pending optional audio must not block export.

### 6.2 Recorded word pronunciation

The selected candidate is AJATT's **NHK-2016 pronunciation index**.

- Integrate a known source/index; download recordings on demand rather than bundling the complete collection.
- Pin the source integration/index version where practicable and document source identity.
- Cache completed downloads for reuse.
- Filter candidates using available spelling/reading information.
- Automatically choose the first matching recording; there is no alternatives list or manual replacement control.
- When source metadata does not verify that the recording matches the selected pitch, include “Recording pitch not verified” in the vocabulary row's neutral explanatory mark. Combine reasons in one popover if pitch is also ambiguous; do not add several identical marks or claim that synthesized audio guarantees a pitch match.
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
- Do not automatically replace all kanji with kana for synthesis.
- Changing the sentence text or the resolved target invalidates the corresponding generated audio; saving that edit is what automatically starts a new synthesis request (§6.7) — there is no separate manual trigger and no spoken-text override control.
- Surface authentication, quota, network, and service failures without exposing credentials.

The provider is approved subject to a Japanese voice audition, pricing review, API/output verification, and credential-handling verification. No current price or untested pronunciation quality is promised by this specification.

### 6.4 Android TTS

- Use an installed Android TTS engine; do not bundle an engine or voice.
- Discover/select a suitable Japanese voice that supports file synthesis.
- For the offline fallback, use a voice that is actually offline-capable.
- Clearly identify missing engines, missing voices, unsupported file output, or voices requiring a network.
- Do not silently introduce another network-dependent provider as an alleged offline fallback.

### 6.5 Recording (retired)

Microphone recording of sentence or vocabulary audio is no longer in scope (§3.2); NHK-2016 recorded pronunciation and TTS fully cover word and sentence audio. This section number is retained as a placeholder so later section references do not shift.

### 6.6 Playback, formats, and cache

- Allow playback before export.
- Select actual encoding/container formats during implementation and verify playback through the chosen AnkiDroid version; filename extensions must match content.
- Maintain a bounded reusable cache of **250 MB** for downloaded and generated audio.
- Cache identity must distinguish source identity and, for synthesis, text, engine, voice, and rate.
- Keep active-draft media outside the evictable cache or otherwise durably protected from eviction.
- Clearing reusable cache must not remove active-draft media or AnkiDroid's imported media.
- AnkiDroid owns its exported copies. Application cleanup must not delete collection media.

### 6.7 Automatic requests and lifecycle

For this personal application, configuring the online sources authorizes their automatic use within the selected workflow; no repeated disclosure or confirmation dialogs are required.

- Start each audio item's source-priority chain automatically once the selected target's dictionary entry is resolved. Do not start audio merely because a token was tapped while entry ambiguity remains.
- Saving an edit that invalidates an audio item's generated result — sentence text or the reselected target (§4.5) — automatically restarts that item's source-priority chain. There is no separate manual **Regenerate** control and no stale-audio state shown to the user; the previous result is simply replaced once the new one is ready, with its neutral state icon shown meanwhile.
- Avoid repeated requests for unchanged valid results.
- Cancel or ignore work for superseded sentence/target revisions.
- Do not let late responses overwrite a newer selection or modify an exported note.
- Do not silently retry potentially billable requests after process restart.
- Preserve completed results. A failed or interrupted optional item exposes Retry through its neutral status explanation; Retry restarts only that item's source-priority chain and is user-initiated after process restart. There is no manual regeneration of a valid completed result. Never export or play an old result as if it matched changed inputs.
- Pending optional audio may finish while editing; sending a note excludes any optional result not ready in the frozen submission and does not wait for it.
- Background completion guarantees and persistent background audio jobs are not required.

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

`SentEng` generates automatically once the sentence is known, using the same online-preferred, offline-fallback shape as audio (§6.1):

1. Azure Translator, if configured and available.
2. A bundled local ja→en model.

The result is always directly editable in place. When the local fallback produced the current text, the translation row carries the same small neutral mark used for pitch uncertainty (§4.1, §5.5); saving an edit clears it regardless of which source produced the original text. Saving an edit to the sentence text invalidates its translation. Automatically replace machine-owned text; preserve manually edited text for the explicit keep/replace decision in §10.2. There is no manual regeneration of valid completed text.

**Release gate:** the local fallback model's translation quality against real mined-sentence text — short, often context-free fragments such as signs, menus, and subtitles, not clean textbook Japanese — must be verified before it ships as the default behavior; see §15. Azure Translator requires its own service authorization even when the same multi-service resource key is used for TTS (§4.9). If both translation providers fail, leave the optional value empty with Retry in its status explanation; the note remains sendable.

### 7.3 Notes

Notes are optional plain text. Preserve intended line breaks and escape text safely for the Anki field. There is no rich-text toolbar, raw HTML editor, or separate source field.

The existing template may interpret a note beginning with `Source:` specially; the application does not invent that format or require it.

### 7.4 Tags and destination

- Tags are Anki note metadata, not an extra model field.
- A new note's starting tags are whichever set the previous note was sent with; Settings holds one seed value used only for the very first note on a fresh install (§4.9).
- Adding a tag offers both manual entry and picking from tags already present in the AnkiDroid collection.
- Support ordinary Anki tag conventions, including hierarchical tags such as `JLPT::N4`.
- Use the last selected existing deck for the next note.
- Never silently substitute a different deck if the remembered deck is missing.

## 8. Anki output contract

### 8.1 Existing model requirement

`misc/Sentence (JA)/template.json` is a reference example, not a required model name, field order, or exclusive schema. The user selects an existing note type by name in settings. Validate that it contains the following **14 distinct field names**; map values by field name into the selected model's actual field order. Extra fields are allowed and receive empty values on new notes. Never assume an extra field can be populated by this application.

| Reference position | Required field | Value contract |
|---|---|---|
| 1 | `SentKanji` | Original sentence with the selected occurrence highlighted using controlled markup |
| 2 | `SentFurigana` | Best-effort whole-sentence Anki bracket-furigana, preserving the same highlighted occurrence |
| 3 | `SentEng` | Automatically generated translation (§7.2), editable; remains optional for export |
| 4 | `SentAudio` | Imported sentence audio reference, or empty |
| 5 | `VocabKanji` | Lemma from the resolved dictionary entry |
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

The provider constants and insert/query behavior currently reference AnkiDroid **v2.24.0** source commit `ebcf8e0e34921628b9b8a496c66ffd4adbb3705f`, the release available on Google Play. The note/media provider source, `FlashCardsContract`, the browser intent's manifest declaration, and its browser search parsing are byte-identical at this tag and at the previously referenced v2.24.1 commit `9f579c10bb151146728220729c510acbbd8faba7`, verified by direct file comparison. This source reference is not proof of runtime compatibility; the exact supported AnkiDroid release must be pinned and validated on-device before release. Do not claim support for every historical AnkiDroid version based only on an API specification number. In this source version, `Utils.splitFields()` drops trailing empty strings; verify how provider insertion handles the required empty trailing fields in the reference schema. Never pad an extra/user-owned field to work around this without approval.

### 9.2 Export eligibility

A note is sendable when all of the following hold:

- Nonempty sentence and a valid nonempty target occurrence.
- Resolved vocabulary dictionary entry; no manual vocabulary-entry fallback.
- Nonempty definition.
- No unresolved stale required fields.
- AnkiDroid permission and validation of the selected model's required field names succeed.
- An existing valid destination deck is selected.
- Required target/entry processing and any prior export operation have settled.

Furigana (including target/vocabulary readings), pitch, either audio item, image, translation, notes, and tags are optional. Missing or pending optional data must not become a hidden required field.

Keep Send available while optional enrichment, audio, or image processing runs. Show clearly which optional work is still ongoing and which not-yet-ready results will be omitted if the user sends now. Freeze the current eligible revision on Send; pending optional results are not imported or attached to that submission, and late results cannot update an exported note. Cancellation/retry controls remain available while editing. Inline optional errors do not require a separate dismissal step.

If a completed, selected optional media item cannot actually be imported, do not create a broken reference or silently omit it. Preserve the draft and allow retry or explicit removal. Media that is still processing may instead be explicitly omitted from the frozen submission as described above.

### 9.3 Duplicate blocking

Block a new note if an existing note in the **destination deck**, using the selected model, has **both** the same sentence text (without generated highlight markup) **and** the same vocabulary lemma. No override or merge is offered. A different sentence or a different lemma is allowed, regardless of vocabulary reading; two targets with the same lemma in the same sentence count as duplicates even if their occurrence or reading differs.

The blocked state's **Open in AnkiDroid** link opens AnkiDroid's browser filtered to the matching note ID, not an internal comparison or a scheduled review session. The pinned v2.24.0 source exports `anki://x-callback-url/browser` and reads its `search` query parameter; use an encoded `nid:<noteId>` search and target the installed AnkiDroid package. This is source-supported, not yet device-validated (§15). If the installed version cannot handle it, retain the draft and show the deck/lemma locator rather than a broken link. Never invent a provider-data URI as an activity deep link.

Comparison must decode serialized text, trim outer whitespace, and preserve internal text rather than applying aggressive linguistic normalization. Equivalent field markup should not create a false distinction. AnkiDroid's built-in first-field duplicate helper does not implement this combined, deck-scoped rule; check both fields against notes in the destination deck.

If the duplicate check cannot be completed, block Send with a retryable, actionable error rather than risk adding a duplicate. Never silently bypass the check or update an existing note. The query and insert are not atomic with respect to other apps or AnkiDroid: serialize this app's sends, check immediately before insertion, and document that concurrent external writers cannot be excluded by this API.

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
- If note insertion may have succeeded, reconcile the collection state before another insertion. Persist the frozen revision, submission phase, imported filenames, known note ID if returned, and pre-insertion duplicate-check result. Compare model, sentence, lemma, and actual card deck membership; an ambiguous or concurrent matching note is not proof of which writer created it.
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
| Sentence text | Tokenization, target validity, sentence furigana, sentence audio, sentence translation, and any target analysis affected by the edit |
| Target span or dictionary entry (reselected via Edit word, §4.5) | Entry candidates, vocabulary data, sentence highlight, pitch, word audio |
| Selected pitch | Pitch serialization and recorded-audio pitch-verification status; no guaranteed pitch-controlled synthesis |
| Audio voice, rate, or service settings | Invalidate affected generated audio; regenerate during the active session, never silently after process restart |
| Image selection/crop | Processed image |

Rules:

- Automatically refresh affected untouched generated values; unresolved automatic readings anywhere never block export.
- Preserve manual corrections and mark affected values stale.
- Automatically restart affected audio after relevant edits/settings changes. Restart translation after sentence changes only when the translation is still machine-owned (§6.7, §7.2). Preserve a manually edited translation or definition as stale when its inputs change; its explanation offers Keep my text or Use new suggestion before export, or explicit omission for optional translation. Never silently replace protected manual text. Stale audio is unavailable while its replacement is pending, not a playable old attachment.
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
- Do not log sentence content, private notes, or private media.
- OCR runs entirely on-device; a captured photo is never uploaded for text recognition.
- Online pronunciation lookups, cloud synthesis, and cloud translation necessarily disclose their request text/lookup terms to configured services; no additional unrelated network processing is allowed.
- Use secure transport and validate downloaded media before use.
- Request camera and AnkiDroid access only through supported permission mechanisms and handle denial gracefully.
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

Automate linguistic, serialization, state, and build tests where practical. Use real-device/emulator checks for Android permissions, camera, TTS engines, and AnkiDroid rendering/API behavior.

| ID | Scenario | Required result |
|---|---|---|
| A01 | Install on Android 15 ARM64 phone and Android 16/17 x86_64 emulators | Application starts, bundled native dependencies load, local enrichment works across the supported releases and both ABIs |
| A02 | Run without network after installation | Tokenization, JMdict lookup, furigana, and pitch lookup remain available |
| A03 | Select an inflected word | Sentence preserves surface form; vocabulary uses the resolved dictionary lemma, changed by reselection rather than free-text editing |
| A04 | Select a repeated word occurrence | Only the chosen occurrence is highlighted in both sentence fields |
| A05 | Adjust selection across tokens | Contiguous phrase is preserved; lookup failure preserves the draft and offers text editing or reselection, not manual vocabulary entry |
| A06 | Several dictionary entries match | The ambiguity sheet overlays the complete Target screen (mockup 09), with sentence, image, tokens, and selection retained beneath the dimming; user confirms the entry before resolved vocabulary-dependent population |
| A07 | Select several senses or enter a manual definition | Export preserves the selected meaning content safely |
| A08 | Unknown or pending kanji reading in target, vocabulary, or elsewhere | Automatic unresolved spans have no fabricated ruby and are visibly marked; no manual correction/omission step; otherwise valid note remains sendable |
| A09 | Kana-only sentence/vocabulary | No unnecessary annotation is required; furigana completion is valid |
| A10 | Irregular reading and okurigana | Valid span annotation renders correctly without inventing character readings |
| A11 | Heiban, atamadaka, nakadaka, and odaka data | Numeric position and contour agree; zero is not treated as missing |
| A12 | Missing pitch | Empty pitch field; valid note remains exportable |
| A13 | A spelling with more than one listed pitch pattern | First pattern is selected and shown silently, no confirmation sheet; alternatives, drawn only from the source's own listed patterns and labelled by the word's reading (never a pattern name or number), appear only inside the word-edit sheet; one selected pattern is exported |
| A14 | Source-backed devoicing/nasalization | Compatible marks are preserved; absent data is not fabricated |
| A15 | NHK-2016 recording available after entry resolution | Matching recording is attached when ready with no source picker; absent pitch-verification metadata is explained through the vocabulary mark |
| A16 | Recorded audio missing; Azure available | Word synthesis falls back to Azure |
| A17 | Azure unavailable; offline voice installed | Synthesis falls back to suitable on-device file output |
| A18 | All audio sources unavailable | The row's small neutral state icon replaces its play control; other data preserved; export allowed without audio |
| A19 | Edit the sentence text or reselect the target after initial audio generation | Saving the edit automatically restarts the affected audio's source-priority chain; card text is unaffected; no manual control is involved |
| A20 | *(retired)* — recording removed from scope (§3.2, §6.5) | — |
| A21 | Pick/capture and crop an image | Correct orientation, at most 2048 px longest edge, no upscaling, AVIF output, metadata removal |
| A22 | Screenshot with small text | Fixed AVIF preset retains acceptable readability and actual AnkiDroid displays it |
| A23 | Change target while requests run | Obsolete results cannot overwrite the new target |
| A24 | Change text after a manual definition/translation correction | Protected text remains and is marked stale; Keep my text / Use new suggestion resolves it without silently overwriting work |
| A25 | Close/rotate/kill app with a draft or interrupted optional request | Saved work and durable media restore; billable work does not silently retry; the interrupted item offers explicit Retry without regenerating completed items |
| A26 | AnkiDroid missing, permission denied, selected model missing required fields, or model removed | Clear actionable error; no collection modification or draft loss |
| A27 | Same sentence and lemma in selected model/destination deck, regardless of reading | Export blocked without override; a failed duplicate check also blocks with retry |
| A28 | Only sentence or only lemma matches; or the full match exists only in another deck | Export permitted by the app's deck-scoped combined duplicate policy |
| A29 | Tap Send while optional audio/image/readings are still processing | Pending optional work and its omission are visible; valid required data can be sent without waiting; late results cannot alter the exported note |
| A30 | Repeated Send taps or interrupted submission | No concurrent submissions; uncertain outcomes are reconciled before retry |
| A31 | Media imported but note creation fails | Draft/import state retained; no blind deletion of collection media |
| A32 | Confirmed successful export | Correct note/deck/media/tags; blank editor with last deck, last capture mode, and the just-sent note's tags as the new starting chips |
| A33 | Inspect exported fields in AnkiDroid | Required names receive valid furigana/highlight/pitch/media references; all `fm_*` values and any extra model fields are empty |
| A34 | Clear/reach audio cache limit | Reusable cache bounded; active draft and exported Anki media unaffected |
| A35 | Secrets/backup review | No credentials/content in logs or build outputs; private data excluded from Android backup |
| A36 | Two clean builds with identical pinned inputs | Unsigned universal APKs are byte-identical |
| A37 | 200% fonts, TalkBack, dark mode, keyboard, portrait and landscape | 48dp non-overlapping touch targets, labelled states, legible contrast, scrollable card/sheets, and reachable editing actions without losing work |
| A38 | Share selected Japanese text or plain text from another Android app | Editor receives sentence without guessing target or sending; an existing draft is not silently replaced |
| A39 | Select a differently named note type with required fields reordered and extra fields | Field-name mapping exports valid values; extra fields remain empty; missing required fields block export |
| A40 | Repeated Send attempts after duplicate or interrupted duplicate check | Same sentence **and** lemma cannot be inserted by deliberate retry; missing check outcome never becomes an automatic override |
| A41 | Photo containing two plausible text regions | A sheet requires picking one before proceeding; a photo with a single clear region proceeds without one |
| A42 | Reselect the target via the Edit word sheet | The sheet shows the sentence's tokens, not a text field; picking a different token re-resolves vocabulary/dictionary/pitch/audio as in normal target selection |
| A43 | Unknown pitch for the resolved vocabulary | No diagram is shown; the row's small neutral mark appears in its place; the note remains exportable |
| A44 | Translation produced by the local fallback vs. by Azure | Only the local-fallback result carries the small neutral mark; editing and saving the translation clears it regardless of which source produced the original text |
| A45 | Sending a note, then starting a new one | The new note's tags default to the just-sent note's tags, not the Settings seed value; the seed applies only on a fresh install with no prior note |
| A46 | Switching between Photo and Type capture across sessions | The next session's capture screen opens in whichever mode was last used |
| A47 | Share a single image from another app | Treated as a just-captured photo: on-device OCR runs, text-region ambiguity is handled the same as Photo-mode capture, the image is linked by default |
| A48 | Dictionary lookup returns no candidate for the selected span | Preserve sentence/image/selection; offer Edit or reselect; no silent adjacent-span selection or manual vocabulary entry |
| A49 | An unexpected failure occurs outside specifically handled paths | Preserve the saved draft and offer Return to draft; safe operations may retry; uncertain insertion never blindly retries or discards |
| A50 | Tap Settings from either capture mode before any sentence exists | Settings sits in the application row to the right of Photo/Type, outside the OS status bar; note type, deck, and Azure configuration are reachable; no Settings entry exists on the review card |
| A51 | Sending, success confirmation, a blocked duplicate, and an uncertain outcome | The submitted review card remains visible and unchanged, including Notes whether empty or populated; only the bottom action area's status changes, until confirmed success resets to blank capture |
| A52 | Translation or audio still generating when the review card first appears | The affected row shows a small neutral spinner in place of its icon/value; furigana, definition, and pitch — resolved locally — do not typically show this state |
| A53 | Tap Open in AnkiDroid on a blocked duplicate | Pinned-version external intent opens the browser filtered to the matching note ID; unsupported handler keeps the draft and displays the locator fallback |
| A54 | Open Settings from the capture screen and inspect review hub states | Reachable there and works identically regardless of Photo/Type mode; review hub states, including generating, flagged, and sending, have no Settings entry, deck selector, or deck/note-type display |
| A55 | Tap a field's explanatory mark | Explanation is associated with its row; Send and editing controls remain unobscured, and all rows remain reachable by scrolling |
| A56 | Open Edit word/image/definition/tags or edit Notes | Complete current card persists behind the edit; discrete choices autosave, text edits offer Done, and Back preserves the draft |
| A57 | Configure Azure or change offline voice | Validate Speech and Translator separately for the shared multi-service key; offline translation is independent of TTS voice availability |
| A58 | Long pitch reading, grayscale, and TalkBack | All morae remain accessible without shrinking kana indefinitely; neutral selected-state cue and final-drop contour distinguish alternatives without color alone |

Template defects must be distinguished from application serialization defects. Accents of 10 or more and unusual mora sequences should be included in compatibility reporting even when an external template limitation prevents perfect display.

## 15. Release gates and unresolved verification

Product behavior is approved. Feasible candidate designs are not validated integrations. OCR, local translation, and AVIF still require pinned code/model/runtime revisions, checksums, applicable licenses, both-ABI packaging, offline-first-launch behavior, and measured memory/latency/quality. They cannot be omitted or replaced by a network-only service without approval. The following external facts must be verified during implementation before release:

1. **SDK/toolchain:** pin working Android 15/API 35 minimum and Android 17/API 37 target SDK/toolchain, JDK, Gradle/Android plugin, NDK, and Nix inputs; verify native loading and platform compatibility on Android 15–17 and both ABIs.
2. **Linguistic stack:** UniDic CWJ 3.1.1 source is pinned and the BSD license selected in `datasets/unidic/`; complete and verify MeCab Android binaries/JNI, full-dictionary loading, licensing, coverage, memory use, reading alignment, and reproducible packaging.
3. **JMdict/Kanjium:** pin snapshots; verify notices, transformations, lookup correctness, and source-backed phonetic capabilities.
4. **Recorded audio rights:** establish the permitted use of the NHK-2016 recordings and source. Do not enable it based solely on repository accessibility or a code license.
5. **Azure:** approve TTS voice quality/price and Translator quality/price, verify region/key configuration for both, downloadable TTS output, rate control, and safe credential storage.
6. **On-device TTS:** verify voice discovery, offline capability, file synthesis, and usable output with the selected test engine.
7. **AnkiDroid:** pin a tested release/API version; verify field-name mapping for reordered/extra fields, permissions, media transfer, destination-deck duplicate blocking (including permitting same sentence with a different lemma despite the first-field helper), and uncertain-outcome handling.
8. **AVIF:** select a working encoder and fixed preset, verify licenses/native packaging, acceptable processing behavior, and actual AnkiDroid rendering.
9. **Reproducibility:** demonstrate clean-build equality, not just dependency pinning or successful builds inside a shell.
10. **Template compatibility:** test generated notes with the supplied template in AnkiDroid and document external rendering limitations separately.
11. **On-device OCR:** select a working Japanese text recognizer; verify accuracy on realistic photographed text (signs, menus, book pages, screenshots), licensing, packaging size, and that it runs fully on-device with no network call.
12. **Local translation fallback:** select a candidate bundled ja→en model; verify translation quality against real mined-sentence text (short, often context-free fragments, not clean textbook Japanese), licensing, packaging size, and native-runtime integration on both ABIs.
13. **AnkiDroid note deep-link:** validate the source-supported browser `search=nid:<noteId>` route from this third-party app against the pinned installed version (§9.3), including handler absence and return-to-draft behavior. Source evidence is not a device test.

If a gate cannot be satisfied, return for an explicit decision. Do not silently substitute a provider, change image format, weaken reproducibility, require a network for linguistic enrichment, or omit the preferred recorded-word source.

## 16. Reference material

### Repository contract

- `misc/Sentence (JA)/template.json` — reference model name and field names; order is not required for a selected model.
- `misc/Sentence (JA)/Recognition/front.html` — field filters, highlighting, pitch rendering, and known limitations.
- `misc/Sentence (JA)/Recognition/back.html` — answer-side audio behavior.
- `misc/Sentence (JA)/template.css` — expected markup styling.
- `misc/Sentence (JA)/samples.json` — compatibility examples, not authoritative linguistic fixtures.
- `misc/Sentence (JA)/README.md` — template behavior and integration notes.
- `mockup/mockup.html` — annotated walkthrough of every screen referenced in §4; `mockup/screens/*.png` are individual renders, `mockup/screens/*.html` their reproducible sources, and `mockup/render.sh` regenerates the PNGs from the HTML.

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
- [Azure Translator authentication and multi-service resources](https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/reference/authentication)
- [ML Kit Japanese text recognition, bundled option](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) — candidate only; verify SDK data collection/terms against §11, offline first launch, and both ABIs before selection.
- [OPUS-MT Japanese–English model card](https://huggingface.co/Helsinki-NLP/opus-mt-ja-en) — candidate weights, not a selected Android runtime; verify artifact licensing, conversion, tokenizer parity, footprint, and translation quality.
- [Pinned AnkiDroid browser intent](https://github.com/ankidroid/Anki-Android/blob/ebcf8e0e34921628b9b8a496c66ffd4adbb3705f/AnkiDroid/src/main/AndroidManifest.xml) and [search-parameter parsing](https://github.com/ankidroid/Anki-Android/blob/ebcf8e0e34921628b9b8a496c66ffd4adbb3705f/AnkiDroid/src/main/java/com/ichi2/anki/browser/CardBrowserLaunchOptions.kt)
- [libavif](https://github.com/AOMediaCodec/libavif)

## 17. Decision precedence

This specification consolidates the approved interview outcomes. Later decisions supersede earlier suggestions:

- No preview, despite its inclusion in the initial workflow.
- Android text sharing, single-image sharing, and selected-text actions are included; no background clipboard access.
- The review card is the ground truth for the whole session once a target resolves; sending, success, duplicate blocking, and uncertain-outcome recovery are states of that same card's bottom action area, never separate full-screen layouts.
- Dictionary misses preserve the draft and allow text editing or target reselection, never manual vocabulary entry. Unexpected errors preserve saved work; discarding is explicit and confirmed. These supersede the earlier discard-and-restart error policy.
- A field's small neutral mark (§4.1) explains itself on tap; it is never that field's edit control. The two are always separate icons.
- The Azure key is configured at runtime in Settings, not embedded at build time — reconsidered and reaffirmed given this is a personally-sideloaded application (§4.9).
- Settings has exactly one entry point, on the capture screen; the review card does not redisplay or link to the configured note type or deck (supersedes the earlier dual-entry-point model).
- A blocked duplicate's recovery action opens the existing note in AnkiDroid itself, subject to the platform gate in §15 — this application does not build its own note-comparison view.
- No general Generate button or regeneration of completed results. Initial enrichment is automatic; edits refresh machine-owned results while protecting manual text. Failed/interrupted optional items offer explicit Retry without retrying possibly completed note insertion.
- Dictionary ambiguity requires entry confirmation; pitch-pattern ambiguity does not — the app picks silently and exposes alternatives only inside the word-edit sheet.
- All readings are automatic and optional, including target/vocabulary furigana; there are no reading-correction or explicit-omission controls. Missing readings and pitch never block export. This supersedes the earlier required-target-reading policy.
- Word audio prefers NHK-2016 recordings over both Azure and on-device TTS; recordings are not bundled in full. There is no microphone recording, no manual audio source selection, and no alternates-list UI (supersedes the earlier microphone-recording scope).
- Sentence translation is automatic, not manual, following the same Azure-preferred/local-fallback shape as audio; a local-fallback result is marked with a small neutral icon, an Azure result is not (supersedes the earlier "no translation API" exclusion).
- "Editing a word" means reselecting its span or dictionary entry via Edit word, never typing a replacement lemma or reading directly.
- One photo may serve as both the on-device OCR source and the note's image by default, splittable into two independent choices in one tap; typing remains a fully independent entry path with its own optional image.
- A new note's starting tags default to the previous note's tags, not a fixed Settings default; the Settings value only seeds the first note on a fresh install (supersedes the earlier fixed-default-tags model).
- The interface uses one neutral ink tone for every control; the only saturated color anywhere is the four-way pitch-pattern coding, reused from the reference template's own palette.
- Online service configuration permits automatic requests without repeated consent dialogs.
- Successful export starts a blank note instead of retaining the sentence for another target.
- Matching sentence **and** lemma in the selected model/destination deck blocks export without override; either value differing permits export.
- Images export as AVIF, not an unspecified general-purpose format.
- Android 15/API 35 is the minimum; Android 17/API 37 is the target.
- A reproducible unsigned APK build is required, not merely a Nix development shell.
