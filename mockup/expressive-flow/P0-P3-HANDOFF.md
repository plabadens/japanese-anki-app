# Expressive-flow — P0–P3 remediation handoff

**For:** the agent revising the Input → Target → Review design study.
**Date:** 2026-10-21.
**Status:** action list derived from a driven review of `flow.html`, not a new design brief.

---

## Read this first

### Authority order

1. `SPEC.md` §4, especially §4.3 (sentence/target/meaning), §4.4 (generate), §4.5 (review), §4.6 (send), §4.9/§10 (submission and rebuilding). `SPEC.md` wins unless a deliberate change is documented.
2. `docs/redesign-interview.md`. This is the user's stated workflow and quality boundary.
3. `mockup/expressive-flow/README.md` — the proposal's own claims. If the proposal diverges from SPEC, it must say so explicitly.
4. The current v0.3 screens in `releases/v0.3/screens/final/` for comparison only.
5. Official Material 3 / M3 Expressive guidance for convention checks.

### Artifact map

| Path | What it is |
|---|---|
| `flow.template.html` | Editable source. Most behavior is in `fresh`, `toolbar`, `input`, `target`, `review`, `generating`, `failure`, `overlay`, `renderPhone`, `setEdge`, `generate`, `handle`, and the document click handler. |
| `build_mockups.py` | Inlines fonts/photo into `flow.html`; stdlib only. |
| `flow.html` | Built artifact. Never hand-edit; rebuild it. |
| `screens/` + `screens/manifest.json` | Captures and hashes. Regenerate after changes. |
| `verification.json` | Current claims. Replace with real, reproducible results. |
| `README.md` | Claims and rationale. Update measured numbers when the design changes. |

### Current evidence status

- `flow.html` and all 18 screenshots hash-match their recorded SHA-256. Integrity is good.
- `verification.json` is a **static list of 44 booleans**, not an executable harness. It does not cover the P0 paths below.
- Several screenshots show the *entry* to a state, not the outcome of the action it offers.
- The review was driven in Chromium with DOM clicks on the real handlers. The P0 observations below are reproducible from the current artifact.

### Environment note

`python3` is **not on PATH** on this host (`python3=missing`). Use the repo's Nix dev shell or an explicit Python 3 interpreter (for example `$HERMES_PYTHON`). Do not claim `flow.html` was rebuilt unless the interpreter actually ran.

### Hard constraints

- Do **not** edit `app/` or `SPEC.md` as part of this list. These are design-study changes.
- Do **not** ship the HTML in a WebView or replace the Compose plan with it.
- Do **not** fabricate verification, screenshots, hashes, contrast ratios, or browser results. If a tool is unavailable, report the blocker.
- Do **not** fix a safety issue by only hiding a button. The guard must hold against Back, Escape, backdrop close, and any other route.
- Do **not** regenerate screenshots from a stale `flow.html`. Rebuild → drive → capture → hash, in that order.
- Keep the study's honest disclosure that camera/OCR/cloud/audio/Anki are simulated.

### Severity

- **P0** — safety, duplicate submit, data loss, dead-end primary path, wrong-meaning or wrong-note risk. Stop-the-line.
- **P1** — authoritative-spec deviation, platform convention break, accessibility failure, hierarchy that defeats the stated primary object.
- **P2** — visual craft, copy, token inconsistency, evidence gaps.
- **P3** — polish and nice-to-have.

---

## Decision gate before P1-3 / P1-5

The receiving agent must not silently choose the design-system direction. Pierre needs to pick one:

- **A. Expressive for real:** bump `material3` to the 1.5 line (current stable 1.4.0; 1.5.0-beta01 on 2026-10-07), opt in with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`, update the Nix/build lock, and use the actual components. This is the only path that justifies the "M3 Expressive" label.
- **B. Baseline M3, correctly:** keep the current dependency line, use standard Material components and name the fallbacks. Remove "Expressive" from the study's claims.
- **C. Hybrid:** keep the study as a composition exploration, implement the flow in baseline M3, and mark Expressive component sketches as "future, dependency-gated."

Until this is decided, P1-3, P1-5, and the loading/progress portions of P1-4 are blocked.

---

## P0 — must fix before visual polish

### P0-1. Make uncertain send terminal

**Repro (current artifact):**

1. Open `flow.html`; expose `window.designStudy`.
2. Seed the live phone: `stage='review'`, `status='uncertain'`, `target=2`.
3. Click `[data-action=back]` → state becomes `target/ready`.
4. Click `[data-action=generate]`; wait; click `[data-action=send]`.
5. State becomes `sending`, then returns to Input with "Sent · demo only."

**Observed:** the guard is local to the uncertain screen. Back re-enters the editable Target flow and permits a second Send.

**Required:** the uncertain state must not be escapable into Create/Send. Back should either be disabled with an explanation, open a "Leave without resolving?" dialog whose only safe exit keeps the draft blocked, or route to a read-only submitted snapshot. The submitted snapshot must be retained. Do not create another note from the same draft until the user resolves via Recheck, Open AnkiDroid, or an explicit discard-local-draft action.

**Acceptance:**

- From uncertain, no sequence of Back, Escape, backdrop close, gear, or any visible control reaches `sending` again.
- The screen still shows the submitted word, reading, meaning, translation, deck, and photo.
- A test asserts the blocked state after every escape route.

**Files:** `flow.template.html` (`failure`, `handle`, click handler), `verification.json`. **Size:** M.

---

### P0-2. Re-tokenize after sentence edit

**Repro:**

1. Capture → Use photo → Edit sentence.
2. Change the sentence to `図書館で本を読んだ。` → Done.
3. Target shows the whole sentence as one selected token; `Adjust selection` is disabled; `Create card` is disabled.
4. Click the sentence → it reopens the editor. `Choose another word` loops to the same state.

**Observed:** the primary Edit action destroys target selection and cannot recover it. The screen says "Tap the word you want to keep," but there is no word to tap.

**Required:** after an edit, run the local tokenizer on the new sentence and render tokens. If the sentence is outside the fixture, show an explicit "This sample only has entries for 図書館, 本, and 読みました" state with a route back, not a whole-sentence chip. Preserve or explicitly clear the previous target; never leave an unusable selected token.

**Acceptance:**

- Editing to another Japanese sentence produces selectable tokens.
- Selecting a known token shows its meaning and enables Create.
- Selecting an unknown token shows a disclosed fixture boundary, not a dead end.
- The whole-sentence-as-one-token state is gone.

**Files:** `flow.template.html` (`target`, `save-editor`, `handle`). **Size:** M.

---

### P0-3. Support contiguous spans in Adjust selection

**Repro:**

1. Capture → Use photo → select 図書館 → Adjust selection.
2. Set Start=0, End=2 → Use selection.

**Observed:** target becomes 0 (図書館), status becomes `missing`; the span 図書館で本 is not preserved. Every multi-token start/end collapses to the first token.

**Required:** represent the target as a `{start, end}` span, render one continuous highlight, and look up the span/lemma. If the fixture has no entry for the span, say "No entry for this span" and let the user trim it; do not silently fall back to the first token and do not label it a pitch miss. Replace the two `<select>` dropdowns with sentence handles or token chips that show the range.

**Acceptance:**

- A multi-token span is preserved in state and visually continuous.
- No-entry spans are explicit and recoverable.
- Start/end validation prevents inverted ranges.
- The UI never collapses a span to its first token without telling the user.

**Files:** `flow.template.html` (`target`, `overlay` adjust branch, `save-boundaries`). **Size:** M.

---

### P0-4. Preserve the chosen entry and clear invalid targets on a miss

**Repro A — missing pitch:**

1. Open the "Missing pitch" secondary state.
2. Click `Choose another word`.
3. Target opens with 本 still selected and `Create card` enabled.

**Observed:** the failed target is immediately re-enabled; pressing Create regenerates and reaches Review.

**Repro B — counter entry:**

1. Open "Entry ambiguity" → choose the counter entry (本・ほん, counter).
2. Status becomes `missing`; target remains 2.
3. Recover via `Choose another word`; the book entry (本・ほん, noun) is selected.

**Observed:** the chosen counter entry is silently replaced by the book entry. Wrong-meaning export risk.

**Required:** a data miss marks that target/entry unavailable. Recovery must either keep the chosen entry with a "pitch unavailable" message and no Create, or clear the target and require a new choice. It must never silently select a different meaning. Show the word, reading, and meaning that failed.

**Acceptance:**

- After a miss, Create cannot be pressed with the same failed target.
- Counter entry remains selected after recovery, or is explicitly cleared with copy.
- The failed word/reading is visible on the miss screen.
- No recovery path changes the meaning without telling the user.

**Files:** `flow.template.html` (`failure`, `counter-entry`, `reselect`, `handle`). **Size:** M.

---

### P0-5. Add an executable regression harness

**Required:** a script in `mockup/expressive-flow/` that drives `flow.html` in Chromium and asserts state. It may use Playwright, CDP, or the environment's browser automation; do not add a heavy dependency without checking the Nix setup. It must exit non-zero on failure and print path → expected → actual.

**Minimum cases:**

- happy path: capture → use photo → select 本 → create → review → send;
- P0-1 uncertain escape attempts;
- P0-2 edited sentence;
- P0-3 multi-token span;
- P0-4 missing and counter-entry recovery;
- generation failure → retry;
- Type input with no photo → review without an image placeholder;
- large-text review at 360×828 and 390 width;
- dark mode;
- no JavaScript errors.

**Acceptance:**

- `verify_flow.*` exits 0 on the fixed artifact.
- Reverting any P0 fix makes it exit non-zero with a clear message.
- `verification.json` is generated from this run (or records its exact command/output), not hand-written booleans.

**Files:** new `verify_flow.*`, `verification.json`, `README.md`. **Size:** M.

---

## P1 — spec, convention, accessibility, hierarchy

### P1-1. Reconcile the review order and the generation stage label

**Current:**

- `review()` renders photo → sentence → translation → sentence audio → vocabulary.
- SPEC §4.5 says contextual image → sentence → vocabulary/definition/pitch → translation.
- `generate()` sets `stage='review'` before generation completes; `toolbar()` therefore shows "Review 3/3" during generating and failure.

**Required:** choose one authority. If following SPEC, move the vocabulary block before the translation. If proposing a new order, add a "Proposed SPEC change" note to the README and get approval. During generation/failure, use a transition title ("Preparing", "Couldn't finish") and do not show 3/3 until the composed review is on screen.

**Acceptance:** DOM order matches the chosen authority; generating/failure no longer claim Review 3/3; a test asserts the order.

**Files:** `flow.template.html` (`review`, `generate`, `toolbar`), `README.md`. **Size:** S.

---

### P1-2. Give failure states their own semantics and full context

**Current:** network failure, missing pitch, and uncertain send all use the same pink error container, the same triangle, the same retained-sentence box, and no word/reading/meaning/deck.

**Required:**

- **Network failure:** error role; show what succeeded and what failed; Retry / Edit / Discard.
- **Data miss:** informational or warning role, not error; show the word, reading, and the missing dataset; Choose another word / Change dictionary entry / Discard.
- **Uncertain send:** warning/uncertain role; show the submitted word, reading, meaning, translation, deck, and photo; Recheck / Open AnkiDroid / Discard local draft. No Send.
- State-first headline ("Send status unknown", "Pitch data missing") followed by the action.

**Acceptance:** each state is visually and semantically distinct; the user can identify the note; no two states share the same icon/color/action set unless they are genuinely the same condition.

**Files:** `flow.template.html` (`failure`, `generating`), CSS role tokens. **Size:** M.

---

### P1-3. Replace the bespoke mode switch with a real single-select component

**Current:** Photo/Type is a custom segmented pill with `aria-pressed` on two independent buttons. Current M3 Expressive guidance says segmented buttons are no longer recommended; use a connected button group for a selectable set.

**Required:** use a connected button group if the dependency decision is A; otherwise use a proper single-select control (`radiogroup`/`aria-checked`, or tabs) and document the fallback. Exactly one option selected; arrow-key navigation; selected state at least 3:1 against its track.

**Acceptance:** one selected option at a time; keyboard works; screen reader announces the selected option; selected fill is ≥3:1 or has a non-color cue.

**Files:** `flow.template.html` (`input`, `.mode-switch` CSS). **Size:** S/M.

---

### P1-4. Simplify progress and app-bar chrome

**Current:** stage title + counter + three-segment bar + page heading; `done` and `current` are the same `rgb(53,91,63)`; current adds a 6px dot. A settings gear appears on every stage.

**Required:** one location cue. Either a single title with a subtitle ("Word · 2 of 3") plus no bar, or a labeled stepper. If a linear indicator remains, use `role="progressbar"` with value semantics and make current vs done distinguishable without the dot. Remove the gear from Target and Review; keep Settings on Input or in an overflow. Remove the counter from the title row.

**Acceptance:** one title/subtitle; current state distinguishable at 3:1 or by label; no gear on Target/Review; screen reader gets a value; the 6px dot is gone.

**Files:** `flow.template.html` (`toolbar`, `.progress-track` CSS). **Size:** S.

---

### P1-5. Put type, color, shape, and motion on tokens

**Current measured values:**

- Type: 26/30/25/23/17/15/13/12/10 px; 700 weight on every heading.
- Radii: 16/18/22/24/28; spacing: 3/6/13/18/22/26/38.
- `primary-container` is used for the current mode, selected token, selected meaning, target highlight, and audio button.
- Selected mode fill is **1.10:1** (light) / **1.57:1** (dark) against its track.
- Sheet textarea border is **1.49:1**.
- `.phone-main` runs a 200ms full-screen `enter` animation on every render.

**Required:**

- Map text to M3 roles: headline/title/body/label. Reduce to 5–6 sizes; use 500 for titles, 400 for body; 700 only for the vocabulary hero if needed. Replace 10px ruby, 12px Sentence, and 13px actions with label/body roles.
- Color roles: `primary` for actions, `secondaryContainer` for selection, `tertiary` for pitch, `error` only for errors, `outline` for field borders. Stop reusing `primaryContainer` for five meanings.
- Shape: use the M3 scale (0/4/8/12/16/20/24/28/32); no 18/22 one-offs unless documented.
- Spacing: 4/8/12/16/24; no 3/6/13/18/22/26 one-offs.
- Motion: remove the full-screen enter on every render; use local state layers and spring tokens; keep the reduced-motion media query.
- Contrast: selected fill and field borders ≥3:1, or add a non-color state cue.

**Acceptance:** no off-scale type/shape/spacing values in changed CSS without a comment; selected fill and field border ≥3:1; error color only for errors; no whole-screen fade on token tap; light and dark checked.

**Files:** `flow.template.html` CSS. **Size:** M/L.

---

### P1-6. Cut the Target hierarchy

**Current count:** 13 elements before Create: app-bar title, counter, progress, photo peek, "Choose a word.", sentence, Edit sentence, Adjust selection, entry word + reading, part of speech, meaning row, Change entry, Create. Four bold headings at 23–34 px.

**Required:**

- Keep one instruction: either the app-bar title or "Choose a word.", not both.
- Move Edit sentence / Adjust selection into one contextual row or overflow; show them when the sentence is being edited, not as permanent peers.
- Replace "Change entry" with an entry count next to the reading ("2 entries") that opens the sheet; the label should make clear it is a dictionary homograph, not a new target word.
- Label the meaning section ("Meaning"); use a selected row/radio for a single sense, or checkboxes with "Select all that apply" if multiple are allowed.
- Render the selected token as continuous text with an invisible hit area; do not use a 48px chip.
- Make the reason for a disabled Create visible.

**Acceptance:** one bold instruction max; ≤7 elements before Create; one obvious way to change the target; meaning has a label; disabled Create has an explanation.

**Files:** `flow.template.html` (`target`, `.target-content`, `.sentence-selection`, `.sense-option`). **Size:** M.

---

### P1-7. Cut the Review stack and make the word the hero

**Current:** photo 160 px, sentence 48.8, translation 23.3, Sentence audio 48, vocab 169.5, secondary 48, ~56 px gap, deck 16.8, Send 56. The photo is the brightest, largest object; the pitch chart is 60 px wide.

**Required:**

- Shrink the photo to a 72–96 px thumbnail or a collapsed strip; keep it one tap to full view.
- Put Sentence playback next to the sentence as an icon button, not a 12px right-aligned row after the translation.
- Make the vocabulary/pitch block the dominant content: larger pitch diagram with mora labels, high/low, downstep, and particle; reserve space for the real renderer.
- Remove the duplicate reading and the "Accent 1" jargon; use a self-explanatory label or a legend.
- Remove the deck label, or make it a tappable "Deck · Japanese" chip. If it is not interactive, it should not look like a status.
- Move Edit and Notes & tags into one overflow or a single secondary row; reduce the dead air.

**Acceptance:** at a glance the word/pitch is the dominant content; the photo is secondary; one primary action; all secondary actions reachable; no orphan 12px control.

**Files:** `flow.template.html` (`review`, `.context-photo`, `.vocab-composition`, `.sentence-audio-row`, `.destination`). **Size:** M.

---

### P1-8. Replace the Edit menu with targeted correction

**Current:** "Edit card" is a six-row settings-style list (Sentence/word/meaning, Readings & pitch, Definition, Translation, Photo, Regenerate audio), no current values, repeated icons. "Readings & pitch" is a placeholder with a explanatory paragraph and a Done button.

**Required:** let the user tap the thing to change: word/reading/pitch, translation, definition, photo. Or use a two-level sheet that shows the current value in each row. Implement the reading/pitch correction (select a kanji span, choose a source-listed pitch), not a placeholder. No raw bracket/HTML editor. Preserve the invalidation rules in SPEC §10.

**Acceptance:** editing the reading/pitch actually works; current values are visible; no settings-like index; Back returns to Review with the change applied; dependent results are invalidated per SPEC.

**Files:** `flow.template.html` (`overlay` edit/readings branches, `handle`). **Size:** M/L.

---

### P1-9. Accessibility pass

**Required:**

- `lang="ja"` on the target tokens, entry heading, textarea, and pitch labels.
- Mode switch: single-select semantics, not two independent `aria-pressed` buttons.
- Entry choices: `aria-selected` or `aria-checked`.
- Study nav: replace `aria-selected` on buttons with `aria-current="page"` or a tablist.
- Progress: `role="progressbar"` with `aria-valuenow`/`aria-valuemin`/`aria-valuemax`, or a labeled stepper.
- Status bar: `aria-hidden="true"` (it is decorative in the mockup).
- Photo alt text: describe the content, not "used as a mock camera scene"; decorative images can be empty.
- Focus: after closing a sheet, return to the invoking control, not `<h2>` with a visible default outline; if `<h2 tabindex="-1">` remains, suppress the outline or use a focus style.
- Large text: scale all text, including 12px labels and the pitch SVG; fix the clipped vocabulary card at the fold; test 200%.
- Contrast: fix selected fill and field border; do not measure only text pairs.

**Acceptance:** keyboard-only journey works; sheet focus is contained and restored; screen reader gets language, selection, and progress; 200% text has no clipping; contrast checks cover non-text states.

**Files:** `flow.template.html`, `verification.json`. **Size:** M.

---

### P1-10. Fix Japanese sentence and pitch rendering

**Required:**

- Continuous Japanese text; no 48px min-width on particles/punctuation; no gap before 「。」; check kinsoku at narrow widths.
- Ruby at ~50% of base; a highlight that clears the ruby; no `mark` pill abutting the furigana.
- Inflected target as one continuous highlight (`読` + `みました`), not two pills; the vocabulary lemma can remain 読む.
- Pitch: mora, high/low contour, downstep, following particle, nasalization/devoicing space; remove the duplicate reading; replace "Accent 1" with a learner-facing label.
- Use `lang="ja"` on the sentence and tokens.

**Acceptance:** the sentence reads as one line of Japanese; no period gap; one continuous highlight for an inflected target; furigana legible; pitch is self-explanatory; the real renderer fits the reserved area.

**Files:** `flow.template.html` (`target`, `review`, `pitch`, CSS). **Size:** M.

---

## P2 — craft, copy, evidence

### P2-1. Copy and terminology pass

- **Note vs card:** this app sends a note to AnkiDroid; cards are generated from notes. Use one term consistently.
- **Create card** before the final Send is ambiguous. Prefer **Continue** for preparation and **Send to AnkiDroid** for submission, or define the distinction.
- **Target** is spec jargon. The user-facing decision is "Choose a word" and "Meaning."
- **Change entry** → "Change dictionary entry" or show the entry count.
- Remove internal policy copy: "Separate entries stay separate; meanings are never blended." Explain the choice, not the implementation.
- State-first headlines: "Send status unknown" not "Check AnkiDroid first"; "Pitch data missing" not "No pitch found."
- Replace "completed results" with the specific retained items.
- "Discard" on uncertain → "Discard local draft" and say the note may remain in AnkiDroid.
- Fix "Close Discard this draft?" to "Close."

**Acceptance:** grep the built HTML for both "card" and "note" and justify each; no internal-policy sentences; each error headline names the condition.

**Files:** `flow.template.html`, `README.md`. **Size:** S.

---

### P2-2. Document the token mapping

Add a short table to the README:

| Element | Current | M3 role/token |
|---|---|---|
| App-bar title | 26/700 | titleLarge / headlineSmall |
| Word hero | 32–34/700 | headlineMedium emphasized |
| Sentence | 25/500 | titleLarge or custom bodyLarge+ |
| Ruby | 10 | 50% of base |
| Translation | 15 | bodyMedium/bodyLarge |
| Actions | 12–14 | labelLarge |
| Selection fill | primaryContainer | secondaryContainer |
| Pitch | amber | tertiary |
| Error | pink for all | error only |

**Acceptance:** every changed text/color/shape value maps to a documented token or a named exception.

**Files:** `README.md`. **Size:** S.

---

### P2-3. Rebuild evidence with real outcomes

- Add the missing captures: inflected target (読みました), edited-sentence result, multi-token adjustment, counter-entry recovery, discard confirmation, notes/tags filled, typed review with no photo, 200% text, dark secondary states.
- Each capture must show the **outcome** of the action, not just the entry sheet.
- Regenerate `screens/manifest.json` hashes after the final build.
- Replace `verification.json` with the harness output (command, timestamp, pass/fail, actual values). If a check is manual, say so and record the observation.
- Remove the "44/44" claim until the harness exists.
- Measure non-text contrast (selected fill, field border) and actual rendered text, not unused role pairs.
- The large-text check must assert no clipped card at the fold and reachable actions, not merely that the container scrolls.

**Acceptance:** every P0/P1 behavior has a test or a captured outcome; hashes match; no static booleans without a command.

**Files:** `screens/`, `manifest.json`, `verification.json`, `README.md`, new harness. **Size:** M.

---

### P2-4. Fix documentation inconsistencies

- README says "20-wide content gutters"; CSS overrides to 16 (and the sentence container is 12). Measure and state the real values.
- README says body 15–17 and action 16; actual actions are 12–14 and ruby is 10.
- README says "the only strongly filled action" — the audio button and selected states are also filled; qualify the claim.
- Sources ledger has [2], [4], [6], [9] but the README cites only [1], [3], [5], [7], [8]. Cite or remove; add titles and access dates.
- The README's "44/44" and "large-text scrolls" claims need the P2-3 evidence.

**Acceptance:** README numbers match measured CSS/DOM; every ledger source is cited or removed.

**Files:** `README.md`, `sources-ledger.json`. **Size:** S.

---

### P2-5. Responsive, landscape, and empty-state coverage

- Test 320, 360, and 390 widths. The 390 check currently passes with no horizontal overflow; verify 320.
- The study is portrait-only. SPEC requires landscape and rotation continuity. Either add a landscape composition or state clearly that it is not represented and will be validated natively.
- Show the empty Type state (placeholder, unfocused field, disabled primary) and the no-photo typed review; the current screenshots are prefilled with the sample.
- Check the sheet's bottom padding against the gesture inset (the mockup uses a fake handle; native insets are still unverified).

**Acceptance:** no horizontal overflow at 320/360/390; empty states captured; landscape scope stated.

**Files:** `flow.template.html`, `screens/`, `README.md`. **Size:** M.

---

### P2-6. Destructive-action hierarchy

- The discard confirmation's "Discard draft" is the filled primary (`rgb(53,91,63)`), while "Keep working" is a text link. Use the error role for the destructive action, or make the safe action the filled one. Add "This only removes the local draft" if the note may exist.
- The uncertain state's "Discard" should say "Discard local draft" and explain the AnkiDroid consequence.

**Acceptance:** destructive and safe actions are visually distinct; the consequence is stated; no destructive action uses the same primary fill as Send/Create.

**Files:** `flow.template.html` (overlay discard branch, failure). **Size:** S.

---

## P3 — polish

- **P3-1:** "1 / 3" → "1 of 3" or a subtitle; remove the 6px progress dot if the bar survives.
- **P3-2:** Input: put flash/camera-switch in the empty right shutter slot; make the framing hint visible on light pages; align the shutter with equal side slots.
- **P3-3:** Deck label: "Japanese" → "Deck · Japanese" with a deck icon; make it tappable or remove it.
- **P3-4:** Sheet titles: "Close Discard this draft?" → "Close"; "Add to this card" → "Add note details" if using note terminology.
- **P3-5:** Tag chip border contrast; pressed-shape feedback on tokens/rows; ordinal labels in the study board ("01 Capture the moment" vs the app's "Input"); status-bar simulation hidden from assistive tech.

**Acceptance:** no change to flow behavior; visual polish only.

---

## Suggested sequence

1. **P0-1 → P0-4** in one pass. Do not touch palette or typography until the state machine is right.
2. **P0-5** harness immediately after, so the fixes are locked.
3. **Decision gate** (Expressive vs baseline) before P1-3/P1-4/P1-5.
4. **P1-1, P1-2, P1-9** (model, failure semantics, accessibility).
5. **P1-6 → P1-8, P1-10** (hierarchy and Japanese rendering).
6. **P2-3/P2-4** evidence and docs last; regenerate all screenshots from the final build.
7. **P3** only if the P0/P1 work is green.

## Definition of done

- `flow.html` rebuilds from `flow.template.html` with no unresolved placeholders.
- `verify_flow.*` exits 0; reverting any P0 fix makes it fail.
- All P0/P1 acceptance criteria pass at 360×828 and 390 width, light and dark.
- 200% text has no clipped content and all actions are reachable.
- Non-text contrast for selected states and field borders is ≥3:1, or a non-color cue is present.
- The review order and stage model match the chosen authority (SPEC or a documented proposal).
- Screenshots, manifest, and verification are regenerated from the final artifact and hash-verified.
- README claims match measured values; the "44/44" claim is replaced by a reproducible result.
- No changes to `app/` or `SPEC.md` unless separately approved.
