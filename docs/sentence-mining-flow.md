# Sentence mining: user journey and screen contract

**Status: PARTIALLY SUPERSEDED — 22 October 2026.** The daily mining flow described below (Capture →
Choose word → Review, the three-screen contract and its strings) was replaced by the approved
[Capture → Card contract](card-flow-contract.md), which is now the sole authority for screen
composition, app-authored copy and interaction choreography.

**Still authoritative in this file:** first-run onboarding, Settings, Downloads, the send contract,
the secondary surfaces it defines (photo viewer, sentence editor, meaning sheet, note/tag editors)
and the failure-copy catalogue. Those sections are unchanged and remain in force.

**Superseded here:** the daily-flow state table, the one user story, sections C/T/R, the progress
sections describing the three-screen journey, and the strings listed as removed in
`card-flow-contract.md` §10. Nothing below may be reintroduced as a screen or a control.

`SPEC.md` defines the retained technical/content/safety contract and its aligned acceptance criteria.
Approval is not evidence that the running app implements this design.

## Mission

**Get everything right the first time, without presenting unnecessary choices.**

A person encounters a Japanese sentence worth remembering. The app turns that encounter into one complete, correctly linked learning note. The person supplies the context and learning intention; the app owns transcription, linguistic resolution, media preparation, generation and reliable delivery.

The app is a sentence-mining tool, not a form builder, dataset debugger or card-repair workbench. Its normal path is:

**Capture → choose a word and its intended meaning → inspect the card → send.**

The app does not promise infallible Japanese. It resolves what the available evidence supports, asks only about genuine ambiguity, and stops honestly when it cannot produce the required result. It never conceals uncertainty by guessing or sending incomplete content.

## Interaction principles

- Show each piece of content once within a screen. In Target, the selectable sentence IS the sentence display; do not add a plain-text hero above it.
- Make the app's work automatic and the person's decisions explicit. Do not ask whether to tokenize, look up a word, encode a photograph, download pronunciation, translate or synthesize.
- Ask about learning intention, not implementation. Word selection is intentional; dictionary-row selection is normally internal. Ask for meaning only when necessary to distinguish viable interpretations.
- One prominent action per working screen. Secondary actions stay visually quiet and attached to the content they affect.
- Content has visual hierarchy, not a label for every stored field. Translation sits directly below the Japanese sentence without a heading.
- Each screen has exactly one visible heading: its current task/step title at the top. There are no in-content section headings. Fields retain concise labels, actions retain names, and source content retains its own typography; none becomes an extra heading. A sheet/dialog has one title of its own, not a second heading inside its body. Markdown headings in this document are editorial structure, not instructions to render UI headings.
- Show one actionable explanation for a blocker, in the place where it occurs. Technical diagnostics stay outside the normal interface.
- Back preserves the draft. Dismissing a sheet returns to an intelligible state; it never commits an arbitrary choice or hides the only way forward.
- No automatic playback. No automatic billable retry. Returning to a screen is not authorization for another paid request.
- Use native Material components and common behavior: top app bars, icon navigation, selection chips, modal sheets, text fields, list rows, progress indicators and snackbars. No extra dashboard, bottom navigation bar or decorative status-card stack.

## Feel: friendly, tactile and quietly satisfying

Borrow the directness and tactile word selection of language-learning apps: generous interaction targets, warm tonal surfaces, confident typography, responsive presses and a satisfying sense of completion. Do not borrow streaks, XP, hearts, mascots, confetti, artificial praise, punitive red screens or a lesson-completion detour. Delight should come from the task working and the controls responding, not from another reward system.

The expressive focal points are **choosing the word** and **the confirmed send**. Those are the two moments worth polishing. Everything else supports reading and moving forward. Expressive design is not permission to add more controls, arbitrary blobs, headings or animation.

### Material 3 Expressive component contract

These are product-specific applications of the official guidelines, not claims that Material prescribes this exact app layout. References M1–M9 are linked in Sources.

| Area | Required treatment |
|---|---|
| Titles and hierarchy | A single top task title, left/start-aligned. Show Capture, Choose word or Review in the mining journey; show the actual onboarding step title, not Set up plus a repeated subheading. Use spacing, type roles and tonal grouping instead of extra headings. Preserve a real heading semantic for the one title. |
| Typography | Use Material title/body/label and emphasized roles from the theme, with a Japanese-capable font and verified ruby metrics. The sentence is prominent content, not a second heading. Avoid an ad-hoc font-size/weight scale and all-bold screens. |
| Color | Use theme surface/on-surface and primary/secondary/tertiary roles, dynamic color where available, and a coherent fallback palette. Primary emphasizes the onward action; a distinct tonal treatment identifies selection. Keep source-defined pitch colors readable and semantically intact. A normal loading/disabled state is not an error. |
| Shape and containment | Use Material component shapes/tokens consistently. Give the word-selection interaction and main action clear, distinct silhouettes. Do not enclose every line in another rounded card. Photographs remain uncropped where required; the camera viewport never becomes a decorative shape. |
| Camera/Type | Use the Expressive **connected button group** as a single-choice selector. It changes only the pressed/selected control's shape, not adjacent widths or camera geometry. Keep both options in one row. Do not substitute the older segmented-button pattern or independently toggleable choices. [M4] |
| Sentence units | A wrapping set of contextual word-selection chips, not a button group or row of navigation buttons. Preserve full source spelling, punctuation and occurrence identity. Use Material press/state layers, a non-color selected indication and accessibility selection semantics. Do not animate widths, bounce the words or insert a checkmark that reflows the sentence. Long words must remain readable; validate a token-preserving wrap rather than ellipsizing the source sentence. |
| Main actions | Native labelled buttons for Choose word, Review card and Send to AnkiDroid; never chips. Use a comfortable prominent size, not an oversized lesson-game button. Keep the footer stable across ready/loading/error states. [M5] |
| Supporting controls | Native Material Symbols and consistent icon-button treatment; accessible names for icon-only actions. Add note/Add tags remain quiet text actions, not unexplained icons. In the tag editor use removable input chips; read-only tags in Review are compact text/tonal labels, not fake removable inputs. |
| Touch/accessibility | At least 48dp non-overlapping effective touch targets, including compact visible chips; at least 8dp spacing between chips. Preserve non-color selection, 200% text, logical reading/focus order, system insets, and normal text/action contrast. Do not shrink Japanese text to force the sentence onto one line. [M5] |
| App-bar actions | Keep the visible action count small. With a retained Capture draft, Resume draft replaces the Downloads action; Downloads is reached through Settings rather than becoming a third trailing action. Without a retained draft, an active Downloads icon may sit beside Settings. No badges/counts or second progress heading. |

### Motion choreography

Use Material's **expressive motion scheme** centrally. Use spatial spring tokens for position/shape/size and effects spring tokens for color/opacity, without overshooting color or alpha. Prefer the native component's own motion; do not stack another bounce on top of it. Small controls use fast tokens, sheets use default tokens, and larger navigation uses the corresponding theme transition. Do not prescribe arbitrary fixed animation durations for every element. [M1, M2]

| Event | Visual response | Haptic response |
|---|---|---|
| Word selection actually changes | Immediate press state; selected fill/outline transitions with fast effects; a restrained internal shape response contained inside the unchanged hit/layout bounds. Meaning content updates without moving the sentence. | One very light selection tick, synchronized with committed selection; none for tapping the already-selected word. |
| Meaning or Camera/Type selection changes | Native selection/shape transition; no full-screen bounce. Camera dimensions and shutter slot stay unchanged. | One light selection tick; do not duplicate a component's built-in effect. |
| Shutter is accepted | Immediate native press feedback inside the fixed shutter slot. Transition to the captured result without a fake full-screen flash or moving viewfinder. | One crisp, subtle action effect; not an additional buzz for OCR finishing. |
| Open/close sheet | Native modal-sheet spring and scrim response, including predictable gesture dismissal. | No added haptic; system gesture feedback remains system-owned. |
| Content becomes available | Effects-based reveal/crossfade in reserved content space. Do not stagger every word or rerun entrance animation on recomposition. No animated pitch trace that delays reading. | None. |
| Review becomes sendable | The existing footer quietly changes to its ready state; no success celebration for a completed HTTP request. | None. |
| Confirmed note delivery | One restrained confirmation/check treatment with the existing success snackbar and return to Capture. No new completion screen and no minimum animation wait before the next capture. | One modest confirmation effect after verified delivery, never merely on pressing Send. |
| Error, waiting or retry | Stable inline message/recovery; no shaking fields, red full-screen flash or looping attention effect. | No added warning buzz or repeated failure vibration. |

Haptics use action-oriented platform feedback, respect system touch-feedback settings, and remain optional through those settings. Prefer platform-defined effects over raw timed vibration; do not invent a cross-device amplitude/duration scale. Never vibrate for scrolling, every keystroke, progress updates, passive navigation, background task completion or a result arriving while the app is not in use. Suppress duplicate feedback across lifecycle restoration and recomposition. Meaning must remain visible without vibration. Haptic quality needs physical-device assessment; an emulator can verify event dispatch, not sensation. [M6, M7]

Respect disabled/reduced-motion preferences: remove decorative spatial overshoot and looping shape/shimmer effects; use immediate state changes or minimal effects transitions and a static accessible busy state when motion is disabled. Never remove progress semantics, block input waiting for animation, or make animation/haptics the only way to detect selection, failure or completion.

## Progress: one job, one signal

There are three different concepts, and they must not share a decorative progress system:
1. **Navigation:** the current task title and Back are sufficient. Remove the persistent Input/Target/Review segmented progress strip and numbered-step semantics from the daily flow. Moving Back does not mean that processing progress has decreased.
2. **Short app work:** one unobtrusive busy signal for the current operation/group, where its action/result belongs. No loading panel and no running checklist of internal tasks.
3. **A large download:** one real transfer indicator and actual bytes on Dictionary/Downloads, plus its background notification. This is measurable work, not a lesson-progress metaphor.

### Selected composition and rejected alternatives

| Alternative | Experience | Assessment |
|---|---|---|
| **A. Action-centred, no step tracker — selected** | Current title supplies orientation. During initial Review preparation, the fixed bottom action area owns one indicator and Preparing card…; completed content can be read immediately. It changes to Send to AnkiDroid only when ready. Send itself owns one Sending… state. | The only approved implementation. No new progress surface or component inventory. |
| B. One thin indicator at the learning-content container edge — not selected | Title-only navigation with grouped progress at the content edge. | Retained only as decision history; do not implement. |
| C. One compact status row beneath the content — not selected | Title-only navigation with a separate status row. | Retained only as decision history; do not implement. |

A full-page blocking loader, a new generation screen, a fake completion percentage and a spinner beside every field are not recommended alternatives. Subtle non-animated placeholders may reserve the geometry of missing content, but they are not additional animated progress indicators.

The official progress guideline explicitly says to use one indicator for a group of related activities, with individual indicators only for independently activated work. Word audio, sentence audio and translation authorized by one Review card action are one group. **This replaces the earlier per-item spinner proposal.** If a person later retries only word audio, that one independent action may own a local indicator instead; do not also keep a global spinner for the same retry. [M3]

### Timing and truthful measurement

- Under 200ms expected wait: show the resulting content without an indicator flash. Do not add a minimum delay merely to display an animation.
- For an expected short, indeterminate operation between 200ms and 5s, use the actual Material Expressive loading indicator (the native shape-morph component), not a pulsing decorative glyph. Its reserved size may be 24dp in the action area; keep the touch target unchanged.
- For expected longer or uncertain-duration network groups such as Review preparation, use **one indeterminate progress indicator from the start**. Do not turn completed subtask counts into a completion percentage or time estimate. Pick the component family from measured expected behavior, not an arbitrary animation preference.
- Do **not** morph a short loading indicator into a determinate progress indicator. Material specifically advises against that family transition. An indeterminate progress indicator may become determinate when real measurement becomes available. A slow outlier retains its original indicator family until completion or a real timeout; it does not grow a second indicator. [M3, M8]
- For UniDic, use one native linear determinate transfer bar and actual downloaded/total bytes. Use the flat variant: the word interaction carries the playful emphasis. Wavy progress is permitted by Material but not required for Expressive compliance. Preserve the standard stop indicator where contrast requires it.
- Download bytes reaching the transfer total do not mean installation is ready. Reuse the same reserved linear-indicator slot with Verifying… or Installing… and an indeterminate state unless that phase has a trustworthy measure. The transfer byte label disappears for those phases. Do not show a fictitious overall percentage or display 100% as setup completion before activation.
- Ready results stay readable while siblings finish. Pending speakers remain plainly unavailable with accessibility state, not red errors or their own spinners. Failures get one local message/recovery; the group indicator runs only while something is actually still running.
- Expose busy/progress semantics and announce meaningful phase/completion changes to accessibility services, not every byte tick. Layout and touch targets stay stable. Long-running work permits navigation away, preserving task and draft state. [M3, M8]

> **Superseded** — see [card-flow-contract.md](card-flow-contract.md). The story is now two
> states, Capture and the card.

## One user story

I open the app, photograph a sentence and tap the word I want to learn. If the photograph contains several possible passages, I choose the passage. If the word genuinely has competing interpretations, I choose the meaning. Otherwise the app resolves these steps without questioning me.

I select **Review card**. The app shows the learning content immediately while one quiet busy state in the bottom action area represents preparation of the remaining audio and translation. I can glance at completed content, optionally listen, optionally attach my own note or tags, then send it to AnkiDroid. A confirmed send returns me to Capture with a subtle confirmation, ready for another sentence.

I do not edit a field inventory, choose a deck repeatedly, acknowledge duplicate instructions or repair the app's internal state.

> **Superseded** — the surfaces below describe the retired three-screen flow. Current surfaces:
> Capture, Card, Settings, Downloads.

## State and navigation

| Surface | Purpose | Normal onward action | Back/dismissal |
|---|---|---|---|
| Onboarding | Complete one-time setup | Next / Finish | Previous step; preserve entered values |
| Capture | Obtain a photograph or typed sentence | Shutter / Choose word | Leave app without discarding saved work |
| Target | Choose the learning target and, if necessary, meaning | Review card | Return to Capture with the draft retained |
| Review | Inspect the finished learning content | Send to AnkiDroid | Return to the same Target selection; no automatic regeneration |
| Settings | Change lasting preferences | Icon Back | Flush autosave; retain edits and show failure if persistence fails |
| Downloads | Inspect/control the background dictionary task | Return to previous surface | Download continues |

Settings is accessible from Capture, Target and Review. Downloads is accessible while setup/download is incomplete. Neither route is a new mining stage. A locked submission disables navigation that would mutate its frozen contents.

## First use: a real onboarding wizard

The app opens onboarding, not Settings. Its ordered steps are **Dictionary → Camera → Audio → AnkiDroid → Ready**. There is no marketing welcome page.

### O1. Dictionary

Content, in order:
1. Onboarding app bar: Back when a previous step exists and Dictionary as the sole title. No Set up heading, progress strip or numbered step counter.
2. The formatted download size, with no repeated Dictionary heading in the body.
3. One progress item: transfer, verification or installation, never three contradictory completion indicators.
4. The current applicable download action.
5. Next, enabled after a download has started or a valid installation is found. It does not wait for completion; setup can continue while the task runs.

Only UniDic is downloaded during first-run data setup. The existing OCR, dictionary, pitch and pronunciation index remain bundled. Downloads default to Wi-Fi. Mobile transfer requires explicit size disclosure and consent; do not start it simply because Wi-Fi disappeared.

### O2. Camera

Explain the purpose in one sentence and invoke Android's camera permission request. Permission denial leaves Type usable and never loops the system prompt. An already-granted permission passes through without an extra confirmation screen.

### O3. Audio

One protected Google Cloud key field and a voice selector. Load eligible voices when advancing from a saved key, not on each keystroke. Select a voice once; preserve the exact saved selection. No automatic paid audition or translation test. A missing key or valid selected voice prevents finishing setup; it does not block capturing a local draft while setup is incomplete.

### O4. AnkiDroid

Connect through the real Android permission flow. Present note type and deck once. If exactly one compatible note type exists, resolve it automatically; otherwise present the compatible choices. Do not silently invent a deck choice. An unavailable AnkiDroid installation may be configured later; local capture remains possible, but export requires a verified destination. Destination preferences then live in Settings.

### O5. Ready

A compact checklist reports dictionary, camera/typing, audio and AnkiDroid readiness. While required setup is incomplete, the sole top title is Finish setup; use Ready only once the required conditions are met. Each row uses its existing item label and a native status icon with accessible state; no repeated visible Ready beneath the Ready title. Finish becomes available when the dictionary is installed and validated, the key is saved and a valid voice is selected. Camera permission may be declined; AnkiDroid setup may be deferred, with export blocked until configured. Do not report the whole app ready merely because a download reached its expected byte count.

### Background behavior

- The download is a durable task, not a coroutine owned by the camera screen or a Settings dialog.
- Advancing through onboarding, leaving the app and returning must not cancel or duplicate it. Process recreation restores the task's actual status.
- The user may choose Use app while setup runs. Capture/type and local draft preservation work; word analysis waits for validated UniDic, and Review requires completed audio setup. Preserve incoming shared content while waiting.
- After leaving unfinished setup, Settings includes the existing Finish setup action at the top of its list, returning to the first incomplete wizard step. Downloads also offers that action while setup is incomplete. Do not add a Capture banner or third app-bar action. Use app while setup runs is a quiet exit on every unfinished wizard step; it never bypasses required Finish conditions.
- Camera never contains the dictionary download's progress panel. Its app bar may expose the Downloads icon; blocked Choose word opens the existing Downloads surface rather than starting another task or silently doing nothing.
- Downloads and the Android notification are the progress/control surfaces. They offer pause/resume, explicit retry and cancellation as applicable. Cancellation does not discard a captured draft.
- Resume means safely continuing the task. If transfer must restart from the beginning, disclose that before another large mobile transfer; never misrepresent a restart as a byte-range resume.
- Verify expected length/hash, archive contents and extracted files before activating data. Reuse a valid installed dictionary without a redundant download.
- Background-execution and notification behavior must use supported Android facilities. Do not claim completion from a notification alone.

## Daily flow: exact screen composition

> **Superseded in composition** by [card-flow-contract.md](card-flow-contract.md) §3–§4. Retained
> here: the OCR/type/share behaviour and the replacement rules.

### C. Capture

**App bar:** Capture as the sole title; Downloads icon only when relevant; Settings icon. No explanatory subtitle or daily-flow progress strip. With a retained draft, Resume draft takes the Downloads action's place; Downloads remains reachable through Settings.

**Camera mode:** stable full-width uncropped viewfinder; fixed shutter row with Gallery on the left, shutter centered and an equal-width empty right slot; Camera/Type single-choice connected button group beneath. No Type caption beside the shutter. Error feedback and press motion must not resize the preview.

After a capture/import, run OCR automatically. One viable sentence proceeds to Target. Several plausible passages open the passage-choice sheet. No text produces one recoverable error. Preserve the photograph throughout; no OCR crop editor.

**Type mode:** one sentence field; optional attached-photo thumbnail with its own Remove photo action; Attach photo action; Choose word as the primary action; the same Camera/Type selector. Attaching a photograph does not run OCR or replace typed text. Empty input disables Choose word without an instructional paragraph.

Photo/text sharing enters the corresponding flow and never selects a target or sends automatically. Replacing an existing nonempty draft requires the shared discard confirmation.

When Capture is revisited with a retained draft, expose the quiet Resume draft action in its app bar. It returns to the retained Target state without recapturing, resetting selection or making network requests. A new shutter/gallery action does not overwrite the retained draft without the replacement confirmation. On process restoration, reopen the last stable working surface; interrupted generation stays available for explicit retry rather than restarting automatically.

> **Removed.** There is no Target screen; its content is the upper half of the card. See
> [card-flow-contract.md](card-flow-contract.md) §5.

### T. Target

Content, in order:
1. App bar: icon Back, Choose word title, Settings icon.
2. Optional small source-photo thumbnail; tapping opens the photo viewer. No empty placeholder.
3. **One sentence display made from compact selectable word units in natural reading order**, retaining punctuation and exact source spans. This is the primary content, not a second rendering below a hero sentence. Punctuation is visible but not a selectable target.
4. **Edit sentence**, left-aligned directly beneath that sentence display.
5. After a word is selected: its resolved headword and reading, chosen meaning and source-backed pitch presentation. No separate row of debug metadata. The chosen occurrence is highlighted; repeated words are not all selected.
6. A quiet Change meaning action only when there is another viable interpretation. If interpretation genuinely cannot be resolved, present the meaning-choice sheet before enabling Review card. Do not show a list of senses or an entry chooser by default.
7. Bottom primary action: Review card. Enabled only when the selected word, meaning, readings, pitch and recording-index match satisfy local preparation requirements and audio setup is complete.

No initial target. A tap immediately performs local lookup. No visible Extend/Trim controls, duplicate tap instructions, hero copy or preparation-corrections panel.

Review card is the explicit authorization to prepare missing network results for the current selection. It is not followed by another Generate confirmation.

> **Removed.** There is no Review screen; the card is inspected where it is built. See
> [card-flow-contract.md](card-flow-contract.md) §5–§6.

### R. Review

Content, in order:
1. App bar: icon Back, Review title, Settings icon.
2. Context photograph, where present, with restrained height so the sentence remains prominent.
3. Japanese sentence with furigana and the selected occurrence highlighted; sentence speaker control beside it.
4. English translation immediately below it, **without a Sentence translation label**.
5. Vocabulary block: headword/reading, selected meaning, pitch diagram and word speaker control. Source-backed pitch marks and katakana diagram labels are preserved.
6. Optional personal metadata: existing tags as compact text/tonal labels; existing personal note as text. Tapping the note or tag group opens only its own editor; actual removable input chips are used inside the tag editor. When empty, use quiet Add note and Add tags actions. No combined Edit notes & tags control and no permanent empty fields.
7. Bottom primary action: Send to AnkiDroid.

Enter Review immediately and prepare missing audio/translation automatically, once for the explicitly authorized selection. Apply progress A: one indicator in the fixed bottom action area with Preparing card… while the group is working, transitioning to Send to AnkiDroid when all required content is ready. Missing content has stable quiet space, not animated per-field skeletons/spinners. There is no Generate screen, full-screen waiting page or redundant progress prose. Speakers are large, accessible Play/Stop buttons, not text-labelled Play rows.

Send remains unavailable until every required item is complete. Re-entry/recomposition does not repeat successful work, retry failures or restart billable generation. Explicit Retry applies only to the failed item; ready siblings remain intact.

### Corrections: intentionally small

There is **no preparation-corrections screen, expandable form or catch-all repair menu**.

- Fix sentence text with the left-aligned Edit sentence action in Target.
- Fix learning intention by selecting the right word or meaning in Target.
- Replace the source photograph through Capture; Type attachments stay optional.
- Return from Review with normal Back, not a special correction button. Preserve matching generated results. A sentence/selection change invalidates only dependent results; returning without changes does not.
- The approved core flow does not include free-form editors for generated translation, definitions, furigana, vocabulary readings or pitch, or a separate pitch-alternative chooser. The app must derive valid content from the sentence and chosen interpretation. If it cannot, retain the draft and explain the single blocker rather than asking the person to repair a field inventory. Do not migrate the removed workbench into a hidden menu.

### S. Settings

Standard native list/form, not onboarding in disguise:
1. App bar: icon Back and Settings title.
2. AnkiDroid connection row, Note type and Deck, with no section heading above them.
3. Protected Google Cloud API key, Sentence voice and Reload voices. Speech and translation failures remain distinguishable; no paid test runs on open or typing. No Audio heading.
4. Default tags field, with no Defaults heading.
5. Dictionary status opens Downloads; Clear cache follows, with no Storage heading.
6. Sources and licenses.

Only while onboarding is incomplete, put Finish setup before those rows; remove it after valid completion. A blank key gets key_required only on an attempted commit/progression, not as an error on opening an untouched form. Voice metadata listing is nonbillable; a disabled/failed cloud operation never claims that opening Settings will enable a remote service or repair project billing. Settings can replace the key; project owners perform the named service/billing change outside the app, then use the existing explicit Retry/Reload route. No cloud-console screen/action or permanent provider-status rows are added.

Use spacing/dividers and field labels for these groupings. They are not titled cards or expandable sections. Settings remains the only visible heading.

Values autosave. Back flushes pending changes; an actual persistence failure keeps the screen open with Retry. Destination/voice changes never alter a submission already in progress. Default tags seed fresh drafts only; note-specific changes do not become defaults. No Save button, duplicated key forms or feature tutorial.

## Secondary surfaces

| Surface | Exact contents and behavior |
|---|---|
| Passage choice | Choose sentence title; one selectable excerpt per detected passage; Cancel. Selecting a passage commits that sentence and closes the sheet. Back/scrim/swipe commits nothing and retains the photo; Capture offers Choose sentence to reopen it. |
| Sentence editor | Edit sentence title; one Japanese sentence field prefilled with current text; Done. Done commits and reanalyzes the changed sentence. Back/scrim/swipe keeps the previously committed sentence; no partial keystrokes alter generation. Empty text cannot be committed. |
| Meaning choice | Choose meaning title; viable interpretations combining spelling, reading and one-line ellipsized definition; selected interpretation indicated; selecting a row commits and closes. View full definition exposes complete text without selecting that row. Back/scrim/swipe preserves the prior choice; if none exists, Review stays unavailable and Choose meaning remains visible. Never split one decision into consecutive entry and sense choosers. |
| Full definition | Definition title; complete source meaning text; Close. Exported content is never the ellipsized preview. |
| Photo viewer | Photo as the sole top title; full uncropped retained photograph; icon Close. No replacement, caption or editing controls. |
| Personal note editor | Note title once; one optional multiline field, accessibly named by association with the title, without a second visible Note label; Done. Prefilled when editing. An empty saved value removes the personal note. Dismissal preserves the previously committed value. |
| Tags editor | Tags title; selected chips and one tag-entry field; Done. Removing a chip affects this note only. Dismissal preserves previously committed tags. |
| Downloads | Downloads title; Dictionary item; actual phase/progress; applicable controls; Finish setup only when incomplete. Icon Back returns to the originating surface while work continues. Same task as onboarding/notification, never another download. |
| Mobile consent | Use mobile data? title; reuse Download size: {download_size} as the body, plus the existing restart disclosure only when needed; Use mobile data and Wait for Wi-Fi actions. Waiting preserves setup/draft. No second sentence repeating the title/action. |
| Draft replacement | Replace this draft? title; Your current draft will be discarded. body; Replace and Keep draft actions. |
| Cache confirmation | Clear cache? title; Your current draft and saved notes will be kept. body; Clear cache and Cancel actions. Cache clearing never deletes active draft media or Anki media. |
| Native selection pickers | Reuse Note type, Deck or Sentence voice as the sole applicable picker title; dynamic compatible options with native single-choice semantics; selection commits the preference. Back/Cancel preserves the prior selection. No description paragraph, new picker heading or automatic substitute. |
| Sources and licenses | Sources and licenses title, icon Back, actual source notices and license documents as content. No introductory paragraph, marketing copy or in-content app-authored headings. Available before download/credentials and after setup. |

No other app-authored surface or secondary action is implied by this contract.

## Send: what success means

Send freezes one complete note and its destination. Repeated taps, navigation, activity recreation and delayed responses cannot create concurrent submissions. The UI keeps the card visible with one sending state.

Import sentence audio, word audio and photograph as applicable through the actual Android provider boundary, preserving per-operation receipts. Then insert/verify the note, fields, destination and media references. A success response without the required verification is not a successful send.

Confirmed success shows one brief snackbar and returns to fresh Capture. Keep settings, capture mode and default tags. Clear the prior sentence, selected target, personal note and per-note tag overrides.

A failed safe-to-repeat operation retains the card and exposes Retry. If the external write may already have happened, retain the frozen submission and offer Check AnkiDroid, not another blind Send. That action performs read-only reconciliation with one Checking AnkiDroid… footer state, never Sending…. Verified success follows the normal completion/reset path; provably no write permits the existing safe Retry; continued uncertainty retains the frozen snapshot and Check AnkiDroid. Merely not finding a note is not proof that insertion never occurred. A destination mismatch or unverifiable note offers Open AnkiDroid for external inspection plus Check AnkiDroid afterward, without another insert or automatic card move/delete. A pre-insertion duplicate offers Open AnkiDroid and safe Back without claiming authorship or success.

## Shared static-copy catalogue

These are the only approved app-authored strings. IDs are implementation references, not visible text. Strings may be reused by reference; do not invent synonyms or explanatory additions. Dynamic placeholders contain user/source content or actual progress, not new instruction prose. Generated sentences/definitions/translations, dictionary names, returned voice names, deck/note-type names, user tags/notes and source notices are content, not catalogue strings. Android-owned permission dialogs, system picker and notification-permission wording are platform-owned and must not be duplicated by app text.

### Necessity audit — 22 October 2026

Every original catalogue entry was reviewed individually; its necessary use/display condition or distinct recovery is recorded in the last column below. Most copy is conditional, not a paragraph to show on the normal path. A string's presence in this catalogue is not permission to add another control or screen.

- Remove use_mobile_body: it repeated the consent title/action; reuse download_size instead. Keep destructive-action bodies because they explain actual consequences, not instructions already apparent from controls.
- Shorten or clarify restart_download, camera_purpose, expand_definition, loading_audio, camera_denied, dictionary_needed, audio_setup_needed, dictionary_invalid, reading_unresolved, no_pitch, no_recording, key_rejected and send_uncertain. Do not collapse operation-specific failures or missing source data into one generic error. Keep model_incompatible's content-capacity meaning; it does not mean the entire card is incompatible for an unknown reason.
- Add only missing existing-state coverage: key_required, dictionary_install_failed, preparation_interrupted, checking_anki and sentence_reading_unresolved. They distinguish missing input, activation failure, interruption, read-only work and non-target reading failure from rejection/download/sending errors.
- Complete the already-required viewer title and nonvisual pitch equivalents with photo, pitch_flat, pitch_drop, pitch_devoiced and pitch_nasal. These are accessibility/identity coverage, not visible diagram captions, new options or another editing workflow.
- Keep separate Choose/Change meaning, Open/Check AnkiDroid, settings/draft persistence failures and the per-media import failures: the underlying actions/outcomes differ. Native selected/disabled/collection semantics do not need extra Selected prose. Retry is accessibly associated with the failed operation, without another visible label.
- Reuse downloads for the app-authored notification-channel name. Actual byte placeholders include localized units; pitch placeholders refer only to supplied source marks, not inferred phonetics. The accessible pitch description combines the applicable catalogue phrases once with the existing word/reading, without speaking raw SVG/path data or colors.
- No explanatory headings, daily counters, per-item progress labels, praise, combined metadata-editor copy or generated-field repair text survives this audit. No new cloud-console action is added: the named project remedy is done outside the app, or Settings is used to replace the key.

| ID | Exact English text | Necessary use / display condition |
|---|---|---|
| app_name | Japanese Anki | Launcher/notification app identity; not a second screen heading |
| capture | Capture | Capture screen title |
| back | Back | Navigation accessibility label |
| close | Close | Close action/accessibility label |
| settings | Settings | Title; gear accessibility label |
| downloads | Downloads | Title/download icon label; notification-channel name, not a persistent progress heading |
| next | Next | Wizard progression |
| finish | Finish | Complete setup |
| done | Done | Commit scoped editor |
| cancel | Cancel | Cancel an uncommitted choice/action |
| retry | Retry | Explicit safe retry |
| dictionary | Dictionary | Download/setup/storage item |
| download_size | Download size: {download_size} | Pre-transfer disclosure |
| formatted_byte_count | {number} {unit} | Localized byte quantity |
| bytes_unit | B | IEC byte unit |
| kibibytes_unit | KiB | IEC byte unit |
| mebibytes_unit | MiB | IEC byte unit |
| gibibytes_unit | GiB | IEC byte unit |
| tebibytes_unit | TiB | IEC byte unit |
| download | Download | Start dictionary task |
| downloading | Downloading… | Transfer phase |
| byte_progress | {downloaded} of {total} | Transfer only; actual values include localized byte units, including accessibility output |
| verifying | Verifying… | Digest/archive validation phase |
| installing | Installing… | Extraction/activation phase |
| ready | Ready | Final title only when required setup is complete; accessibility readiness state may reuse it without a duplicate visible label |
| waiting_wifi | Waiting for Wi-Fi | Download state |
| paused | Paused | Download state |
| cancelled | Cancelled | User-stopped download state |
| pause | Pause | Download control |
| resume | Resume | Resume task control |
| use_app | Use app while setup runs | Leave setup without declaring it complete |
| finish_setup | Finish setup | Incomplete final-step title and conditional Settings/Downloads re-entry action |
| restart_download | The download will start over. | Only when safe resumption actually requires a byte-zero restart; disclose before consent |
| download_notification | Dictionary download | Background notification title |
| dictionary_ready | Dictionary ready | Completion notification |
| camera | Camera | Mode selector/setup item |
| camera_typing | Camera / typing | Final readiness row for capture input |
| camera_purpose | Capture Japanese text with your camera. | One permission-purpose sentence, not repeated beneath Android's dialog |
| allow_camera | Allow camera | Trigger system permission request |
| use_typing | Use typing | Continue without camera permission |
| gallery | Gallery | Icon accessibility label |
| take_photo | Take photo | Shutter accessibility label |
| type | Type | Mode selector |
| sentence | Japanese sentence | Input/editor accessible label |
| attach_photo | Attach photo | Type attachment action |
| remove_photo | Remove photo | Type attachment action/accessibility label |
| view_photo | View photo | Photo-thumbnail accessibility label |
| photo | Photo | Sole title of the existing full-photo viewer; no caption or extra photo label |
| choose_word | Choose word | Target title; Type onward action |
| resume_draft | Resume draft | Capture app-bar action, retained draft only |
| choose_sentence | Choose sentence | Passage sheet/reopen action |
| reading_photo | Reading text… | Temporary OCR status |
| edit_sentence | Edit sentence | Left-aligned Target action; editor title |
| choose_meaning | Choose meaning | Unresolved ambiguity action/sheet title |
| change_meaning | Change meaning | Existing interpretation action |
| expand_definition | View full definition | Accessible disclosure opens the complete source meaning; not an entry-selection action |
| definition | Definition | Full-definition title |
| review_card | Review card | Target primary action/authorization |
| review | Review | Review title |
| preparing_card | Preparing card… | One grouped Review busy state; not a heading |
| loading_audio | Preparing audio | Accessibility-only state while this audio is actually being prepared; never failed/unrequested audio |
| play_sentence | Play sentence audio | Speaker accessibility label |
| play_word | Play word audio | Speaker accessibility label |
| stop_audio | Stop audio | Active speaker accessibility label |
| pitch_flat | Flat pitch | Accessibility-only equivalent of source-backed accent zero |
| pitch_drop | Pitch falls after mora {mora}. | Accessibility-only equivalent of the supplied accent number; mora is one-based |
| pitch_devoiced | Devoiced mora {mora}. | Accessibility-only supplied devoicing annotation; never guessed |
| pitch_nasal | Nasal mora {mora}. | Accessibility-only supplied nasal annotation; never guessed |
| add_note | Add note | Empty personal-note action |
| edit_note | Edit note | Existing note accessibility label |
| note | Note | Personal-note sheet title once; field derives its accessible name from the title |
| add_tags | Add tags | Empty tag action |
| edit_tags | Edit tags | Existing tags accessibility label |
| tags | Tags | Per-note tag sheet title |
| tag_input | Add a tag | Tag-entry accessible label |
| remove_tag | Remove {tag} | Chip removal accessibility label |
| send | Send to AnkiDroid | Review primary action |
| sending | Sending… | Frozen submission primary-action state |
| sent | Added to AnkiDroid | Single confirmed-success snackbar |
| check_anki | Check AnkiDroid | Read-only uncertain-write recovery |
| checking_anki | Checking AnkiDroid… | Read-only reconciliation busy state in the existing footer, not another write |
| anki | AnkiDroid | Onboarding step title; Settings connection-row label, not a section heading |
| connect_anki | Connect AnkiDroid | Permission/setup action |
| open_anki | Open AnkiDroid | External app action |
| install_anki | Install AnkiDroid | External installation action |
| setup_later | Set up later | Defer unavailable AnkiDroid configuration |
| connected | Connected | Verified connection status |
| not_connected | Not connected | Connection status |
| note_type | Note type | Destination field and native picker title; ask only if several compatible options exist |
| deck | Deck | Destination field and native picker title; no invented default deck |
| audio | Audio | Onboarding step title only; not a Settings section heading |
| google_key | Google Cloud API key | Protected credential field |
| key_required | Enter a Google Cloud API key. | Blank-key validation only after attempted commit/progression; not initial-entry prose |
| show_key | Show API key | Accessibility label |
| hide_key | Hide API key | Accessibility label |
| sentence_voice | Sentence voice | Voice field and native picker title; exact saved voice retained |
| reload_voices | Reload voices | Metadata refresh action |
| loading_voices | Loading voices… | Metadata progress |
| default_tags | Default tags | New-note defaults field |
| clear_cache | Clear cache | Storage action/confirmation |
| clear_cache_title | Clear cache? | Confirmation title |
| clear_cache_body | Your current draft and saved notes will be kept. | Confirmation body |
| cache_cleared | Cache cleared | Confirmed cache-action snackbar |
| sources | Sources and licenses | Settings navigation/title |
| use_mobile_title | Use mobile data? | Consent title |
| use_mobile | Use mobile data | Consent action/download-state action |
| wait_wifi | Wait for Wi-Fi | Consent action |
| replace_title | Replace this draft? | Replacement confirmation title |
| replace_body | Your current draft will be discarded. | Replacement confirmation body |
| replace | Replace | Confirm replacement |
| keep_draft | Keep draft | Decline replacement |

### Failure and recovery copy

Only show the failure that currently blocks the attempted action. Map typed failure states to these strings; never derive recovery behavior by parsing a message. Place Retry next to the affected operation only when repeating it is safe. Prefer inline errors; use a dialog only for an actual destructive choice.

| ID | Exact English text | Distinct blocker / necessary recovery |
|---|---|---|
| camera_denied | Camera access is off. | After denial only: Use typing in the wizard, existing Type/Gallery in Capture; Open settings when a new permission request is impossible |
| open_settings | Open settings | Android app settings for permission repair |
| camera_failed | Could not take the photo. | Shutter retry |
| photo_import_failed | Could not open this photo. | Gallery/Attach photo |
| ocr_empty | No readable Japanese text found. | Retake/choose a photo or Type |
| ocr_failed | Could not read the photo. | Retry OCR without discarding the photo |
| analysis_failed | Could not analyze this sentence. | Retry local analysis or Edit sentence |
| sentence_reading_unresolved | Could not resolve this sentence’s reading. | Unsupported non-target reading, not a failed analyzer; Edit sentence/meaning, no manual ruby editor |
| dictionary_needed | Finish dictionary setup. | Downloads; includes validation/installation, not only transfer; preserve input |
| audio_setup_needed | Finish audio setup. | Finish setup; preserve selection; no generic dead-end disabled Review |
| dictionary_failed | Could not download the dictionary. | Retry; Downloads owns details/progress |
| dictionary_invalid | Could not verify the dictionary download. | Explicit Retry; distinguish integrity failure, never activate it |
| dictionary_install_failed | Could not install the dictionary. | Extraction/activation failure after verification; safe Retry, no partial activation |
| storage_full | Not enough storage to install the dictionary. | Retry after space is freed |
| no_entry | No dictionary match for this word. | Choose another word/Edit sentence |
| reading_unresolved | Could not resolve this word’s reading. | Choose meaning or another word; no invented reading |
| no_pitch | No pitch pattern found for this word. | Source miss; choose another viable interpretation/word, not a network Retry |
| no_recording | No recording found for this word. | Source miss; choose another viable interpretation/word, not a download failure |
| recording_failed | Could not download the word audio. | Retry only that download |
| speech_failed | Could not create the sentence audio. | Retry only synthesis when safe |
| translation_failed | Could not translate this sentence. | Retry only translation when safe |
| preparation_interrupted | Card preparation was interrupted. | Restored unfinished generation; explicit Retry authorizes only unfinished work and preserves successful siblings |
| playback_failed | Could not play this audio. | Retry playback; do not regenerate automatically |
| offline | No internet connection. | Retry the affected network operation |
| key_rejected | API key not accepted. | Settings for key replacement; distinct from a blank field, no unchanged automatic retry |
| speech_service_disabled | Enable Text-to-Speech for this Google Cloud project. | Project owner enables it outside app, then explicit Retry; Settings only for key replacement |
| translation_service_disabled | Enable Translation for this Google Cloud project. | Project owner enables it outside app, then explicit Retry; Settings only for key replacement |
| billing_required | Check billing for this Google Cloud project. | Project owner corrects billing outside app, then explicit Retry; Settings only for key replacement |
| quota_reached | The service limit was reached. Try again later. | Explicit Retry when appropriate |
| speech_input_rejected | This sentence could not be used for audio. | Back to Target/Edit sentence; no silent fallback |
| voices_failed | Could not load voices. | Reload voices |
| voices_empty | No compatible Japanese voices found. | Reload voices/credential correction |
| voice_unavailable | Choose an available sentence voice. | Sentence voice selector |
| cached_voices | Saved voices · offline | Honest cached-list state, never claim fresh validation |
| save_failed | Could not save your changes. | Retry; retain visible values and remain on Settings |
| draft_save_failed | Could not save this draft. | Retry; retain visible work; block unsafe transition |
| cache_failed | Could not clear the cache. | Retry; preserve active draft |
| anki_missing | Install AnkiDroid to send this card. | Install AnkiDroid |
| anki_permission | Allow access to AnkiDroid to send this card. | Connect AnkiDroid |
| destination_needed | Choose a note type and deck in Settings. | Missing/unverified destination; Settings retains the draft and supplies the existing selectors |
| model_incompatible | This note type cannot hold the required card content. | Note type selector |
| duplicate | This sentence and word are already in this deck. | Open AnkiDroid and safe Back before insertion; never duplicate or claim ownership of the existing note |
| duplicate_check_failed | Could not check for an existing note. | Retry the check; do not insert blindly |
| sentence_audio_import_failed | Could not copy the sentence audio to AnkiDroid. | Retry only when receipt state permits |
| word_audio_import_failed | Could not copy the word audio to AnkiDroid. | Retry only when receipt state permits |
| photo_copy_failed | Could not copy the photo to AnkiDroid. | Retry only when receipt state permits |
| note_insert_failed | Could not add the note to AnkiDroid. | Retry only if no write occurred |
| send_uncertain | The note may already be in AnkiDroid. | Check AnkiDroid supplies the action; frozen state prevents blind Send |
| destination_mismatch | The note is not in the selected deck. | Open AnkiDroid then Check AnkiDroid; retain frozen state, no automatic move |
| verification_failed | Could not confirm the note in AnkiDroid. | Check AnkiDroid; do not report success |

Unexpected failures use the operation-specific safe string, while the local diagnostic retains the actual category. No raw exception, provider payload, database identifier or implementation-policy explanation appears in the UI.

## Import verification: a dummy AnkiDroid that exercises Android

The import is currently reported broken. Its cause is not established by this document. A successful parser/unit test is not evidence that the phone-to-provider flow works.

Required test arrangement:
- Install a separate test-only Android APK on an isolated emulator. It implements the AnkiDroid package/provider/permission contract used by the production gateway, pinned against the supported AnkiDroid API. It cannot coexist with the real package on that emulator; never replace a person's installed AnkiDroid or collection.
- Exercise the production gateway and real ContentResolver/FileProvider calls across separate app UIDs. The dummy must actually open the granted content URI, copy and persist bytes, and return realistic provider receipts. An in-process fake that merely returns a filename does not qualify.
- Provide a control channel for tests to configure deterministic responses and inspect stored notes/media. Keep that channel and dependency out of production builds.
- Import real decodable WebP, MP3 and Ogg fixtures, then check persisted bytes, filenames, note fields, tags, deck and note identity after the source URI grant is revoked. Cover the exact MIME/URI/filename and permission contract, not a more permissive substitute.
- Inject permission denial, missing provider, null/malformed return, unreadable URI, media-copy failure, rejected note, destination mismatch and write-completed/response-lost outcomes. Exercise double Send, activity recreation and retry; assert one intended note and no duplicate media import after a durable receipt.
- The same happy-path assertions must also run against a real AnkiDroid installation with an empty test collection before claiming compatibility. The dummy supplies reproducible fault injection, not proof that the dummy's own interpretation of the API is correct.
- A full journey test goes from input through selection, generated fixture content and Send to a persisted external note. Controlled generation is labelled as such; no live-service quality claim follows from it.

## Material guidance audit and implementation proof

Research checked on **22 October 2026**, using Google's official Material and Android documentation. The single-heading restriction, chosen progress alternative and exact haptic events are product decisions; Material does not require those exact layouts. The component, motion, feedback and accessibility rules below are grounded in the cited guidance.

| Guideline / principle | Application in this document | Required implementation evidence |
|---|---|---|
| Expressive hierarchy, limited hero moments [M1] | Word selection and confirmed delivery carry expression; the rest stays quiet. No rewards or extra screens. | Native interaction recording shows no competing animated emphasis or completion detour. |
| Spatial versus effects springs [M2] | Central expressive scheme; geometry uses spatial tokens and color/opacity uses effects tokens. | Inspect theme/component wiring; exercise interruptions and reduced/disabled animation settings. No fixed-duration lookalike claimed as the native motion system. |
| Group related loading work [M3] | One grouped Review busy state; no spinner per generated item. Only an independently initiated retry may own local progress. | Native initial generation and isolated-retry tests assert the indicator count/scope. A slow sibling must not create another global indicator. |
| Correct loading family and measurement [M3, M8] | No indicator flashes for instant work; short morph loader only for expected short tasks; progress component for unknown/long groups/downloads; real bytes; no loading-to-determinate morph. | Delayed/failing controlled transports verify component family, truthful values, phase semantics and absence of invented percentages. |
| Connected groups, not obsolete segmented controls [M4] | Camera/Type has single-choice connected-group semantics and stable external geometry. | Native mode switching, large text and pending capture tests; exactly one selected option, no adjacent-width animation or preview shift. |
| Chips versus onward buttons [M5] | Contextual word choices use chip-style selection; Review/Send are real buttons; tag inputs have removal affordances in the editor. | Full labels/source offsets, non-overlapping touch targets, correct roles and large-text wrapping; do not relabel navigation buttons as chips. The sentence is a domain-specific adaptation, not a claim that Material defines a sentence-token component. |
| Sparse, action-oriented, synchronized haptics [M6, M7] | Light committed-selection feedback; one confirmed-send effect; system preferences respected; no background/progress buzzing. | Instrumentation verifies dispatch and no duplicate events; actual sensation is assessed on hardware and reported unassessed if no device is available. It must not block functional acceptance by pretending an emulator proves haptic feel. |
| Legibility and accessibility | One top heading; visible field/action labels; contrast checked in both themes; semantic selection/progress; reduced motion; at least 48dp targets and 8dp chip spacing. | Native screenshots and accessibility traversal at narrow width/200% text, numeric text/non-text contrast checks, disabled animation/touch-feedback settings. Ordinary text uses at least 4.5:1 contrast; actionable non-text indicators use at least 3:1. |

**Dependency reality:** the repository currently explicitly pins Material3 **1.4.0** with Compose BOM **2025.09.00**. The locally resolved 1.4.0 AAR contains MotionScheme, MaterialExpressiveTheme, ButtonGroup, LoadingIndicator and the experimental Expressive API annotation. Official 1.4.0 release notes also document MotionScheme integration. This is availability inspection, **not** proof that every proposed signature/opt-in already compiles or is in use. Before implementation is accepted, compile the actual required components against the resolved dependency graph, document required experimental opt-ins, and verify the theme supplies the intended scheme. Do not silently replace an unavailable component with a custom imitation while claiming compliance; propose any necessary dependency/contract adjustment first. [M2, M9]

This is a guideline-aligned design contract, not a certified or runtime-verified app. Its native implementation must pass the evidence above before claiming the design is implemented in letter and spirit.

## Approval and ownership

The approved decisions are:
1. Target contains only the selectable sentence, with left-aligned Edit sentence; routine dictionary/sense machinery is automatic unless meaning is genuinely ambiguous.
2. Notes/tags remain optional, accessed individually through quiet content-linked actions, not the combined editing control.
3. The general generated-field correction workflow is removed rather than moved to another hidden form. Sentence editing and intended-word/meaning selection are the human correction path.
4. Progress A is selected. Remove the daily navigation stepper and per-item generation spinners; UniDic keeps one truthful transfer indicator in Downloads/onboarding. B and C are not implementation options.
5. The single top heading, restrained expressive motion and sparse haptic choreography are the requested design direction. No additional controls, haptic settings page, animations or celebratory copy are implied.

This file is the single screen-and-copy contract. The product specification and acceptance criteria must remain aligned with it. Assign one UI owner; component workers may implement existing contracted elements but must not add strings, controls, screens or recovery routes independently. New user-visible elements require a proposed change to this file and approval before implementation. Resource files and tests must trace each app-authored string to its catalogue ID. Dynamic/generated text and platform-owned text are the only explicit exclusions. Feature work lands on dev; no main promotion or release is implied by this approval.

Acceptance requires the connected normal journey, each listed exception, background download/recreation, dismissal/reopen behavior, light/dark and large text, and external note/media verification. A collection of green component tests does not constitute acceptance of the flow.

## Sources

Official primary sources:
- **M1** — [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), published 13 May 2025: hierarchy, shapes, typography, containment and one or two hero moments.
- **M2** — [Material motion physics system](https://m3.material.io/styles/motion/overview/how-it-works) and [Compose MotionScheme reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme): expressive/standard schemes; spatial/effects tokens and component-sized motion.
- **M3** — [Progress indicator guidelines](https://m3.material.io/components/progress-indicators/guidelines): grouped loading, expected wait categories, placement, real determinate values and contrast/stop-indicator requirements.
- **M4** — [Button group guidelines](https://m3.material.io/components/button-groups/guidelines): connected groups for selection, replacement of baseline segmented buttons, stable adjacent controls and one-row layout.
- **M5** — [Chip guidelines](https://m3.material.io/components/chips/guidelines): contextual selection rather than onward navigation, input-chip affordances, wrapping, spacing and effective target size.
- **M6** — [Android haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles), updated 6 October 2026: less is more, action-oriented effects and synchronized, consistent feedback.
- **M7** — [Android haptics API guidance](https://developer.android.com/develop/ui/views/haptics/haptics-apis): platform effects, device behavior and respecting touch-feedback settings.
- **M8** — [Loading indicator guidelines](https://m3.material.io/components/loading-indicator/guidelines): expected 200ms–5s waits, native seven-shape morph, 24dp minimum size, and prohibition on transitioning this loader into a determinate progress indicator. Read in the browser when text extraction failed.
- **M9** — [Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0) and [repository dependency catalogue](../gradle/libs.versions.toml): distinguish design guidance from the project's actual resolved implementation surface.

Project context:

- [Current product specification](../SPEC.md) — retained content, privacy, destination and media requirements, reconciled with this approved flow.
- [Target implementation](../app/src/main/java/dev/pierre/japaneseanki/ui/TargetScreen.kt) — inspected current duplicated sentence, right-aligned edit action and correction form.
- [Review implementation](../app/src/main/java/dev/pierre/japaneseanki/ui/ReviewCard.kt) — inspected current translation heading and combined metadata editor.
- [AnkiDroid gateway](../app/src/main/java/dev/pierre/japaneseanki/AnkiDroidGateway.kt) — inspected the actual ContentResolver/FileProvider boundary that integration tests must exercise.
