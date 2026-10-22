# Screen and copy contract — revision 2: Capture → Card

**Status: APPROVED — 22 October 2026.** Pierre approved this design and directed that it supersede the
previous daily-flow instructions. This file is now the sole authority for screen composition,
app-authored copy and interaction choreography. [sentence-mining-flow.md](sentence-mining-flow.md)
remains authoritative only for onboarding, Settings, Downloads, the secondary surfaces it defines,
the send contract and the failure-copy catalogue.

**What it replaces on approval:** the *State and navigation* daily-flow rows, sections **C. Capture**,
**T. Target** and **R. Review**, the progress-A composition rows, and the strings `capture`,
`choose_word`, `review`, `review_card`, `preparing_card` and `continue`. **Everything else stands**:
onboarding, Settings, Downloads, secondary surfaces, failure copy, send semantics and the whole
technical contract in `SPEC.md` — with the single exception of the authorization clause (A6 in
[the review](ui-review-20261022.md)), which moves from *Review card* to *committing a sentence*.

Prototypes: `docs/evidence/prototype/capture.html` (Capture) and
`docs/evidence/prototype/card.html` (the card in four states).

## 1. Why two states

The third screen was doing no work. Choose word with a resolved selection and Review showed the same
composition — same sentence, same word card, same pitch, same definition, same speakers. What the
third screen added was a transition, a Back path, a re-entry rule and a second place to notice the
same error. Removing it removes: Back-preserving-the-draft, Resume draft, "return to the same
selection without regenerating", and the re-entry duplicate-work rules. What is left is one surface
that grows as the person makes decisions.

The cost is a layout obligation rather than a logic obligation: the page must grow **without moving
what the finger is on**, and the one action must stay pinned and legible, which is why the footer is
fixed and the content scrolls behind it.

## 2. Surfaces

| Surface | Purpose | Onward | Back |
|---|---|---|---|
| Capture (camera) | Obtain a photograph | Shutter | Leave app; retained draft survives |
| Capture (type) | Type or paste a sentence | Choose word | Leave app; retained draft survives |
| Card | Choose a word, inspect the note, send it | Send to AnkiDroid | Returns to Capture with the draft intact for a retake |

Settings, Downloads, the photo viewer, the passage sheet, the meaning sheet, the sentence editor and
the note/tag editors are unchanged in composition and copy except where this file says otherwise.

## 3. Capture — camera

Order, top to bottom. Nothing else is on this screen.

1. **Top bar, 56 dp.** No screen title. A centred segmented single-choice control — **`Camera` |
   `Type`**, text, 38 dp tall, equal segments, selection indicated by tonal fill **and** an outline,
   never colour alone. Its horizontal centre is the shutter's centre. `Settings` is a trailing
   icon-button in the same row. The selected segment is the identity of the screen; nothing else needs
   to say "Capture".
2. **Viewfinder.** Uncropped **4:3 sensor, shown upright (3:4)**, full width, square corners, no
   card, no gutter, no rounded clipping, no scrim or mask over it, no text layered on it. Preview
   bounds never change while capturing, during OCR, or while an error is shown.
3. **Control row.** `Gallery` left, shutter centred, **`Torch`** right — all at least 48 dp, all in the
   thumb band. The torch is a toggle, not a flash: continuous light lets the person see the glare in
   the viewfinder and angle the phone until the paper reads, which a burst at the moment of capture
   cannot do. It keeps its state across captures within the session, shows a distinct on-state (tonal
   fill plus outline, not colour alone), and switches off when Capture is left or the app is
   backgrounded. A device without a torch shows no control rather than a dead one.
4. **Equal whitespace.** The gap above the control row (`x₁`) and below it (`x₂`) are equal, so the
   shutter sits inside the thumb arc rather than against the bottom bezel — about 120 dp from the
   edge on a 390 × 844 screen. `x₁ = x₂ = max(12 dp, (available height − control row) / 2)`, measured
   above the system navigation inset.
5. **System inset.** Every control clears the gesture-navigation inset; the primary action's bottom
   edge sits at least 12 dp above it, so a low tap cannot be read as a home gesture.
6. **Short screens.** When the height cannot hold the viewfinder, the equal gaps and the control row,
   the **gaps shrink first** — down to their 12 dp floor — and only then does the viewfinder narrow in
   width and centre with equal side margins. Whitespace is decorative; the preview is the work
   surface, so it gives up size last. It never crops and never stretches.
7. **Status line.** A single transient line (`Reading text…`, an OCR failure with Retry, no text
   found) is anchored to the **bottom of `x₁`**, above the shutter. It never moves the shutter row
   and never appears anywhere else on this screen.

There is **no** instructional text on this screen. A shutter needs no explanation.

## 4. Capture — type

1. The same top bar, with `Type` selected: the switch does not move, change size or reorder.
2. One multiline Japanese sentence field, focus not requested on open. It **starts at the top bar and
   spans the full width, flush with the viewfinder's edges**, and fills the space down to the primary
   action — it is the content of this screen, so it leaves neither a margin mismatch nor an empty
   middle. Text inside it is inset by the normal 16 dp gutter.
3. `Attach photo` — quiet text action beneath the field; optional, never runs OCR, never replaces
   typed text.
4. `Choose word` — primary action, **at the bottom edge of the screen**, disabled while the field is
   blank, with no instructional paragraph next to it.

The switch and the primary action are therefore in the same screen positions in both modes: the mode
control in the bar, the primary control owning the bottom edge.

## 5. Card — order and content

1. **App bar.** Back icon, sole title `Card`, `Settings`.
2. **Photograph band.** Full width, no radius. Its height is capped at **9/16 of its width** — a 16:9
   band, 390 × 219 dp on a 390 dp surface, the largest the photograph may ever occupy at the top of the
   card — and it **shrinks from there as content arrives**, down to a 96 dp floor (§9). The photograph itself
   always **fills the band**: cover-cropped, centred on the selected line where one exists, never
   stretched to the band's proportions and never letterboxed inside it. A square or portrait
   photograph is therefore cropped at the edges, not fitted. Tapping the band opens the existing
   uncropped photo viewer. No placeholder when absent.
3. **Sentence row.** The selectable sentence (§7) with the **sentence speaker** at its end — a
   speaker icon, never a play triangle, and never a text label.
4. **`Edit sentence`**, left-aligned directly beneath the sentence.
5. **Translation**, directly beneath, with no label and no heading.
6. **Word card**, after the first word selection: headword with its reading, **part of speech** (one
   plain English word from the selected dictionary senses), the source-backed pitch diagram, the
   selected definition, the **word speaker** (the same speaker icon and weight as the sentence's), and
   `Change meaning` only when a second viable sense exists. Absent entirely while no word is selected — the space is reserved so nothing jumps.
7. **`Add note` / `Add tags`** — quiet text actions, present only once a word is selected; existing
   note/tag content is shown in place and opens its own editor.
8. **Pinned footer.** Exactly one hint line (§6) and one primary action: `Send to AnkiDroid`,
   `Update card` when a change invalidated prepared content, or one of the existing blocked states.
   The footer never scrolls away, never overlaps content, and never changes height between states.

## 6. The hint line

One line, above the button, in a reserved slot. It is the **only** place in the app where the person
is told what to do next, and it says one thing at a time. It never restates the button label, never
explains the implementation, and is **empty** when the button is self-evident.

| Card state | Line | Action |
|---|---|---|
| No word chosen | `Tap a word to build this card.` | none |
| Word chosen, pronunciation downloading | `Preparing pronunciation…` + loading indicator | none |
| Sentence audio or translation still arriving | (empty — the content is readable and the wait is short) | none |
| Blocked by setup | the applicable existing string (`Finish audio setup.`, `Finish dictionary setup.`) | the existing route |
| A required item failed | the applicable existing failure string | `Retry` for that item only |
| Not ready to send for any other reason | one sentence naming it | its one recovery |
| Ready | (empty) | `Send to AnkiDroid` |

New strings: `card` (title), `card_hint_word`, `card_hint_preparing`, `update_card`. Existing failure
and setup strings are reused unchanged; no synonyms.

## 7. Sentence selection and furigana

The sentence is continuous text in reading order with Japanese spacing; it is not a field of pills.

- Layout is **two aligned bands**: the reading band (ruby) above, the base band below. Kana-only
  words reserve the reading band so baselines match across the line.
- Selection paints the **base band only** — a tonal fill and a 2 dp underline below the base text.
  The reading band is never filled, never outlined, never underlined, and must not be clipped or
  overlapped by the selection treatment.
- Selection is indicated by more than colour: fill, underline, and accessibility selected state.
- No size, padding, weight or baseline change on selection. The sentence must not reflow when a word
  is tapped.
- Every word is an independent target with at least 48 dp effective height; adjacent vertical targets
  never overlap. Punctuation is visible and not selectable. The tapped occurrence is the highlighted
  one, including when a word repeats.
- Line breaking follows Japanese rules: punctuation never begins a line; a word and its trailing
  punctuation stay together. Long words wrap inside themselves. Never ellipsize source text, at any
  text size.

## 8. Loading

- Under 200 ms: show the result; no indicator.
- 200 ms to 5 s: the Material **loading indicator** (the native shape-morph component, ~16 dp) inside
  the hint line, with the hint text. Never inside the button, never as a ring beside a label, never
  in a coloured button.
- Unknown or longer work: one indeterminate indicator in the same hint line. Completed content stays
  readable; pending items keep their space and their accessibility state.
- One indicator at a time. A retry owns its own line only if it is initiated separately, and then no
  second indicator is shown for the same work.
- The button keeps its label and changes only its enabled state while preparing.
- With animation disabled, the indicator is static and the hint text carries the meaning.

## 9. Space

Target: at 390 × 844 with default text, the whole card is visible without scrolling.

When it does not fit, in this order:

1. **The photograph shrinks first**, and it alone: from its 16:9 ceiling down to a 96 dp floor. It is
   the one element whose size can change without costing the person information, and at 96 dp it is
   still a recognisable mnemonic rather than a strip.
2. **The app bar never shrinks or leaves.** It stays at its normal height with its title, Back and
   Settings, because the title is the only thing telling the person where they are.
3. **Then the content scrolls** under the pinned footer.

Rules: collapse only in response to scrolling or a measured shortage — never because content arrived,
never while a finger is on a word, never during a selection. At 200% text the photograph starts
collapsed and the content scrolls. Nothing that a finger is touching may move.

## 10. String delta

| Change | String | Reason |
|---|---|---|
| Remove | `review` | No Review screen |
| Remove | `review_card` | The button no longer exists; preparation is authorized at commit |
| Remove | `preparing_card` | Replaced by the hint line's `Preparing pronunciation…` |
| Remove | `continue` | Never contracted; the action is `Choose word` |
| Add | `card` | Sole title of the card state |
| Add | `card_hint_word` | The one instruction on an empty card |
| Add | `card_hint_preparing` | Named preparation state in the hint line |
| Add | `update_card` | Primary action after an invalidating change |
| Add | `torch` | Torch toggle: accessible name, with an on/off state rather than a second string |

Part of speech, deck names, dictionary text, pronunciation and translations are content, not
app-authored copy.

## 11. Acceptance deltas

- **R20 becomes:** one card surface; sentence, translation, word card and one action in the order
  above; one hint line in every state; no per-item spinner; no generation screen; pinning and
  collapse behave as §9.
- **"Send without scrolling" is replaced** by: the primary action is visible in every state without
  scrolling, and content scrolls beneath it.
- **New checks:** selection does not reflow the sentence; ruby is never painted; the mode switch
  occupies the same slot in both capture modes; the card fits at 390 × 844; collapse order is
  photo → bar → scroll; at 200% text nothing clips and the hint line never wraps to three lines.
- Everything else in R01–R39 is unchanged.

## 12. Non-goals

- **Local translation.** Not now, and not a reason to change this contract. Translation already sits
  behind one small injectable adapter; replacing it later changes no screen. Do not add a second
  provider, a provider selector or a model setting.
- No context photograph (deferred), no sound effects, no photograph-derived colour.
- No third screen, no confirmation screen, no completion screen.

## 13. Open items

1. Whether `Update card` is reached automatically after a word change or only when the person taps
   Send on a stale card. Current wording: it replaces `Send to AnkiDroid` in place, so the person
   never sends stale content by accident.

**Decided, not open:** Capture has no title. The segmented control carries the screen's identity, and
that is a deliberate exception to "one visible heading per screen" — do not restore a title that
duplicates the mode switch.
