# Design: what this app should feel like

**Status:** AUTHORITATIVE for intent, tone, and visual direction. Two documents outrank it for their
own subject: [card-flow-contract.md](card-flow-contract.md) (approved 22 October 2026) is the sole
authority for screen composition and app-authored copy, and `SPEC.md` for required behavior and
acceptance. Where this document disagrees with either, they win and this is the one to fix.

**Who should read it:** anyone building or reviewing a screen, writing user-facing copy, or judging
whether a UI change "fits". Read the contract for what each screen says and contains, `SPEC.md` §4
for the technical and accessibility requirements, and this for why it looks and reads that way.

## 1. The job

One Japanese sentence, worth keeping, becomes one Anki note — while the moment is still in front of
you: a page in a book, a sign, a line someone just said. The app exists to close that gap quickly
and without argument.

The competing product is not another app. It is a web form: a screen of labelled boxes, each one
asking to be filled, each one a small reminder that you are doing data entry instead of learning.
That is what the previous version felt like, and the redesign exists to get away from it.

What replaces it: you point the camera, you touch the word you want, you check that the note says
what you meant, you send it. Three decisions, one at a time, with the app doing the paperwork in
between.

## 2. What it must feel like

- **Quick at the front, patient at the back.** Choosing the sentence and the word is the part you
  do while standing up; it must never wait on a network, a spinner, or a progress bar. Everything
  slow (speech, translation, images) happens after you have committed to a word, and is visible
  without being in the way.
- **Calm and warm, not clinical.** Warm paper surfaces, ink-brown text, one vermilion action per
  screen. The screen should feel like a page and a pen, not a dashboard.
- **Trustworthy.** The app never guesses your meaning, never invents a reading, and never sends a
  note you would have to repair later. When it cannot finish something, it says so plainly and
  holds your work.
- **Quietly finished.** Nothing decorative competes with the Japanese. The sentence and the word
  are the only things allowed to be large; everything else is small, plain, and out of the way.
- **Honest.** Status is stated, not implied. Missing data looks missing, pending work looks
  pending, and a failure names its own condition instead of apologising in general terms.

## 3. Principles

1. **The sentence is the subject; the word is the point.** Every screen has one thing it is about.
   Input is about the photograph of the sentence. Target is about which word, and which meaning.
   Review is about the note you are about to keep. Anything that does not serve that one thing is
   secondary, smaller, or absent.
2. **The photograph is the mnemonic.** It is not decoration and not a thumbnail in a frame: it
   carries the place, the page, and the moment. It stays in the composition, it fills its slot, and
   tapping it shows the whole image uncropped.
3. **One decision per screen.** The app asks for the sentence, then the word, then the meaning, and
   only then does it go and fetch things. It never asks two unrelated questions at once and never
   moves the decision the user already made.
4. **The user owns meaning; the app owns mechanics.** Readings, pitch, audio, translation and
   images are produced by the app, but the dictionary entry, the sense, and any correction are the
   user's. The app resolves silently only when there is exactly one possibility, and even then
   shows what it resolved to before continuing.
5. **Complete or blocked, never "good enough for now".** A note leaves only when everything
   required is present and current. A partial note is not a draft to fix later; it is a note that
   teaches the wrong thing.
6. **Corrections happen on the content.** To change the word, touch the word. To change the
   translation, touch the translation. There is no settings-style index of fields, no six-row "edit
   card" menu, and no raw markup editor.
7. **Failure is a state, not an error dialog.** Every failure keeps the work, says which thing
   failed, and offers the one action that can help — retry, choose another word, or check
   AnkiDroid.
8. **Restraint is the style.** One accent colour, one primary action. Emphasis is spent on the
   Japanese and on the word, not on chrome.

## 4. Visual language

The identity is a fixed palette, not a theme to be re-tinted: **vermilion for action, warm paper for
surfaces, ink for text, ink-blue for pronunciation.** It is one proposed identity, not a claim about
Japanese design in general, and it holds in both light and dark.

**Colour roles** (from `mockup/expressive-flow/README.md`, implemented in `ui/FlowTheme.kt`):

| Role | Light | Dark | Used for |
|---|---|---|---|
| surface | `#FFF8F5` | `#1C1411` | page background |
| onSurface | `#2B1712` | `#F5DED4` | text |
| primary | `#9C3329` | `#FFB4A3` | the one action on the screen, selected outlines |
| secondaryContainer | `#F1DDD2` | `#4A3329` | the word/definition card, tonal selection |
| tertiary (pitch) | `#354B70` | `#B3C8EE` | pitch contour only |
| error | `#A51D26` | `#FFB2B7` | genuine errors only |

Rules that follow from it:

- **Saturation is information.** Apart from vermilion actions and the pitch colours, everything is
  ink, paper, or a tonal step between them. A colour that appears for decoration teaches the user
  to ignore the colours that mean something.
- **Pitch colour is data.** The contour colour is a fixed role so theme tinting cannot change it.
  The pitch diagram is the only place blue appears.
- **Selection is never colour alone.** A selected meaning carries a checked control and an outline,
  not just a warmer fill.
- **Error is not the same red as action.** Action and error must stay distinguishable by more than
  their label.

**Typography.** A short scale, used by role rather than by size: 22/500 for the card's sentence and
36/700 for the vocabulary word and nothing else, 16/400 for body (definition, translation, typed
text), 14 for supporting text and button labels. The sentence sits at 22 rather than 16 because
legibility of the Japanese is the point. Ruby is a proportional annotation at about half the base
size, drawn in its own band above the base text rather than squeezed into the baseline — see
[card-flow-contract.md](card-flow-contract.md) §7.

**Shape and space.** A small scale (4/8/12/16/24/28) with generous gutters (16, or 24 while writing)
and pills for anything you can touch. Primary actions are 56 dp high; anything tappable is at least
48 dp and never overlaps its neighbour. The camera shutter is the one circular, oversized control,
because it is a camera, not a card.

**Composition.** One column, portrait: top bar, content, one pinned primary action at the bottom.
There is no stepper and no progress strip — the two states are Capture and the card, and the card's
own assembly is the only progress the person sees. Content that does not fit scrolls behind the
pinned action rather than being cut. The layout is
centred and capped in width so a rotated or large window keeps the phone composition instead of
stretching it.

## 5. Motion

Motion explains a change; it does not perform. The list is short and deliberate:

- **Local, never global.** Tapping a word highlights the word. The screen does not fade, slide, or
  re-enter. A whole-screen transition on every render is the anti-pattern this design rejects.
- **In place, and once.** A pending value keeps its reserved space and is described in the single
  hint line above the primary action — one small Material loading indicator there, never a ring
  inside the button and never a spinner per field. Nothing replaces the screen with a loading page
  and nothing leaves a blank gap that later jumps.
- **One global movement.** There is **no step tracker**. Navigation is title-only, and work is
  reported by a single indicator inside the pinned footer described in
  [card-flow-contract.md](card-flow-contract.md) §6: the hint line names what is happening while the
  selected content is produced, and empties when the Send action is ready to speak for itself.
  Missing values keep stable quiet space; they do not get animated per-field skeletons, spinners, or
  redundant progress prose.
- **Springs and state layers** come from the platform's Material motion; there are no bespoke
  curves invented for this app.
- **Reduced motion is respected.** When animation is disabled by the system, transitions are
  instant and nothing is lost.
- **Pressed feedback is local.** The control you touched responds; the composition does not.

## 6. Voice

The app speaks like a competent assistant, not a system, and not a policy document.

- **State first, action second.** "Send status unknown" then what to do; "Pitch data missing" then
  which word and which choice. Not "No pitch found." Not "Check AnkiDroid first."
- **Use the user's words.** "Choose a word" and "Meaning", not "Target" and "annotation". "Send to
  AnkiDroid", not "Create card" — the app creates **notes**; AnkiDroid makes cards from them.
- **Never explain the implementation.** "Separate entries stay separate; meanings are never
  blended" is internal policy. The interface should simply not blend them.
- **Name the consequence.** "Discard local draft" says what is removed and, where it matters, that
  a note may still exist in AnkiDroid.
- **No encouragement, no exclamation marks, no personality.** The reward is the note, not the copy.

## 7. The workflow, and what may not be traded away

Capture → the card. One surface that becomes the note, one primary action at each moment, and no
second screen between the person and the thing they are keeping.

**Capture.** A camera in the composition, with the mode switch as the only thing in the top bar.
The photograph is the default subject; typing is a first-class alternative that may carry its own
photo. The preview does not move or resize while the app is capturing, processing, or failing.
Landscape photographs are normal input; the interface stays portrait, and the draft survives
rotating away to take the picture and back.

**The card.** The moment a sentence is committed, the same surface becomes the note: the photograph
at the top as a 16:9 band that shrinks as content arrives, the sentence in continuous text with its
readings above and the chosen word highlighted inside it, **the translation directly beneath it**,
then the word with its part of speech, pitch contour, definition and recording. Notes and tags are
separate, optional editors. The only correction offered on the content is the left-aligned **Edit
sentence** action; there is no repair workbench, no per-field edit list and no raw markup.

**Choosing a word is the decision.** Selecting a word inserts its card beneath the translation, so
nothing above it moves. Meanings are resolved silently unless two are genuinely viable, in which case
one sheet asks once. Nothing about dictionary entries, senses or readings is ever a form on the
card.

**Preparing.** Not a screen and not a checklist: the sentence's translation and speech start when
the sentence is committed, the word's recording starts when the word is chosen, and one hint line
above the button names whatever is still arriving. Paid work is never repeated because of a return,
a rotation or a process death.

**Send.** One action, one state at a time: idle → sending → sent, or blocked, or status unknown.
While sending, the note stays visible and frozen; nothing can be edited under the finger that just
pressed Send. Success returns to a blank capture. Anything less than confirmed success keeps the
note.

Non-negotiables of the flow:

- **Portrait only.** There is no landscape composition, no two-column Review, no control rail. A
  landscape photograph is an input, not a layout.
- **No dead ends and no double sends.** A submission whose outcome is unknown is terminal: no route
  — Back, the system back gesture, a backdrop tap, settings, or a second tap — can reach a second
  insertion.
- **Distinct failures.** Network failure, missing source data, and unknown submission are three
  different conditions with three different icons, roles, and recoveries. They are not one pink
  box.
- **The word is the hero of the card**, not the photo and not the chrome. The photo carries the
  memory; the word and its pronunciation carry the learning.
- **Nothing incomplete leaves.** Blocked with an explanation beats sent with a hole in it.

## 8. What "done" looks like from the outside

A reviewer should be able to answer yes to all of these without reading code:

- In five seconds on the card, can you tell which sentence, which word, which meaning, and which
  translation you are about to keep?
- Is the primary action always visible without scrolling, and unambiguous in every state? (Content
  may scroll behind it; the action may not.)
- With one required value missing, is it obvious *what* is missing and *where* to fix it?
- In dark mode, at 200 % text, with one hand: is anything clipped, overlapping, or reachable only
  by guessing?
- Does the app say anything the user did not need to know about how it is built?

## 9. What this document is not

- Not a widget checklist. The inventory of screens, controls and strings is
  [card-flow-contract.md](card-flow-contract.md); the acceptance criteria are `SPEC.md` §14.
- Not a pixel contract. The approved contract is the reference for composition and hierarchy; native
  Material components supply the actual behaviour. Pixel parity is explicitly not required — the
  composition and the rules above are.
- Not a claim that the app already looks like this. See the screenshots in
  `docs/archive/evidence/v0.4/` for the last accepted build, and treat the gaps as work, not as
  design decisions.
- Not derived from the HTML study. `mockup/expressive-flow/` is decision history: its flow, three-
  segment tracker, preparation page and extra headings are superseded, and elements from it must not
  be reintroduced.

## 10. Where the intent came from

- [card-flow-contract.md](card-flow-contract.md) — the approved contract for Capture and the card:
  composition, the static-copy catalogue and the import-verification arrangement. This document
  interprets it; where they differ, the contract governs.
- `docs/redesign-interview.md` — the user's own statement of the workflow and its quality boundary.
- `mockup/expressive-flow/README.md` — the study's visual system, type and colour roles. Historical
  for flow and composition; still the origin of the palette and type scale.
- `mockup/expressive-flow/REVIEW-PASS-2.md` — the decisions that fixed reading order and the photo
  slot.
- `mockup/expressive-flow/P0-P3-HANDOFF.md` — the review that established the safety rules the
  flow keeps (terminal uncertain send, re-tokenize after edit, spans, entry identity).
- `SPEC.md` §4.1 and §4 — the normative technical and accessibility rules this document interprets.
