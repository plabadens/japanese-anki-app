# Design: what this app should feel like

**Status:** AUTHORITATIVE for intent, tone, and visual direction. `SPEC.md` remains the
authoritative statement of required behavior; where this document and `SPEC.md` differ, `SPEC.md`
wins and this document is the one to fix.

**Who should read it:** anyone building or reviewing a screen, writing user-facing copy, or judging
whether a UI change "fits". Read `SPEC.md` §4 for what each screen must do; read this for why it
looks and reads the way it does.

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
8. **Restraint is the style.** One accent colour, one primary action, one step indicator. Emphasis
   is spent on the Japanese and on the word, not on chrome.

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

**Typography.** A short scale, used by role rather than by size: 22/500 for screen titles and the
review sentence, 28/500 for a transition headline, 36/700 for the vocabulary word and nothing else,
16/400 for body (definition, translation, typed text), 14 for supporting text and button labels.
Japanese inside the flow may run larger than the Latin scale — the Review sentence sits at 22 and the
Target word pills at 24 — because legibility of the script is the point. Ruby is a proportional
annotation at about half the base size, never a separate line, never a smaller font squeezed into
the baseline.

**Shape and space.** A small scale (4/8/12/16/24/28) with generous gutters (16, or 24 while writing)
and pills for anything you can touch. Primary actions are 56 dp high; anything tappable is at least
48 dp and never overlaps its neighbour. The camera shutter is the one circular, oversized control,
because it is a camera, not a card.

**Composition.** One column, portrait, top bar, step indicator, content, one primary action at the
bottom. Content that does not fit scrolls above the action rather than being cut. The layout is
centred and capped in width so a rotated or large window keeps the phone composition instead of
stretching it.

## 5. Motion

Motion explains a change; it does not perform. The list is short and deliberate:

- **Local, never global.** Tapping a word highlights the word. The screen does not fade, slide, or
  re-enter. A whole-screen transition on every render is the anti-pattern this design rejects.
- **In place.** Pending values become small spinners where the value will appear. They never
  replace the screen with a loading page and never leave a blank gap that later jumps.
- **One global movement.** The three-segment progress bar is the only element that reports where
  you are, and it is compact: completed segments filled, the current one outlined, the rest empty.
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

Input → Target → Review. Two transitions, one backward path, one primary action at each step.

**Input.** A camera in a portrait frame, gallery to the left, Type to the right. The photograph is
the default subject; typing is a first-class alternative that may carry its own photo. The preview
does not move or resize while the app is capturing, processing, or failing. Landscape photographs
are normal input; the interface stays portrait, and the draft survives rotating away to take the
picture and back.

**Target.** The sentence as a row of word pills, one selected, its entry and reading beneath it,
and the meanings listed as explicit choices. The meaning is visible before Continue is possible —
never let someone continue without having seen which meaning they are exporting. Editing the
sentence re-tokenizes it; it never leaves an unusable selection or a whole sentence pretending to
be one word.

**Preparing.** A short transition, not a screen: the app fetches what it needs for the revision you
confirmed, shows per-item progress in place, and does not repeat paid work because you came back or
the process was restored.

**Review.** A composed artifact, in reading order: the photograph, the sentence with its target
highlighted and furigana inline, **the translation directly beneath the sentence**, then the word
with its reading, pitch contour and definition. Notes and tags are secondary and optional; the only
things editable here are notes and tags. Corrections go back to preparation, where the content
lives. The card fills its fixed-height photo slot (144 in the study's scale) with a focal crop that
keeps the selected line visible, while the full-image viewer always shows the whole frame.

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
- **The word is the hero of Review**, not the photo and not the chrome. The photo carries the
  memory; the word and its pronunciation carry the learning.
- **Nothing incomplete leaves.** Blocked with an explanation beats sent with a hole in it.

## 8. What "done" looks like from the outside

A reviewer should be able to answer yes to all of these without reading code:

- In five seconds on the Review screen, can you tell which sentence, which word, which meaning, and
  which translation you are about to keep?
- Can you send without scrolling, with the primary action visible and unambiguous?
- With one required value missing, is it obvious *what* is missing and *where* to fix it?
- In dark mode, at 200 % text, with one hand: is anything clipped, overlapping, or reachable only
  by guessing?
- Does the app say anything the user did not need to know about how it is built?

## 9. What this document is not

- Not a widget checklist. The inventory of screens, controls, states, and acceptance criteria is
  `SPEC.md` §4 and §14.
- Not a pixel contract. The study in `mockup/expressive-flow/` is the reference for structure,
  hierarchy and identity; native Material components supply the actual behaviour. Pixel parity is
  explicitly not required — the composition and the rules above are.
- Not a claim that the app already looks like this. See the screenshots in
  `docs/archive/evidence/v0.4/` for the last accepted build, and treat the gaps as work, not as
  design decisions.

## 10. Where the intent came from

- `docs/redesign-interview.md` — the user's own statement of the workflow and its quality boundary.
- `mockup/expressive-flow/README.md` — the study's visual system, type and colour roles.
- `mockup/expressive-flow/REVIEW-PASS-2.md` — the decisions that fixed reading order, the photo
  slot, and the landscape mistake.
- `mockup/expressive-flow/P0-P3-HANDOFF.md` — the review that established the safety rules the
  flow now keeps (terminal uncertain send, re-tokenize after edit, spans, entry identity).
- `SPEC.md` §4.1 and §4 — the normative UI rules this document interprets.
