# Review pass 2 — landscape, translation order, photo fill

**Date:** 2026-10-21
**Target:** current `mockup/expressive-flow` build (`flow.html`, `study.js`, `flow-layout.css`, `study.css`).
**Status:** delta review. The earlier `P0-P3-HANDOFF.md` is historical; the P0 flow fixes in `REMEDIATION.md` are implemented and are not re-litigated here.

> **Implementation status:** the three decisions below are implemented in the current build. This file is review input, not current test evidence; `README.md`, `REMEDIATION.md` and `verification.current.json` describe the current state.

The current revision is materially better than the version in pass 1: the P0 flow guards exist and are tested, there is a real Chromium harness and model tests, the vermilion/paper/ink palette is coherent, the sentence highlight is continuous, the pitch has a particle and no "Accent 1", word pills replaced the broken span dropdowns, and Target/Review lost the settings gear. Credit where due.

The three new decisions are still correct, and one of them is a P0: the landscape composition is not just unnecessary; it breaks the Target decision.

---

## Decision 1 — No landscape mode; support landscape photographs (P0)

### What the current build does

- `flow-layout.css:24-44` has a full landscape composition: `.landscape-phones .phone { width:828px; height:390px; }`, a `@container phone (aspect-ratio > 1)` block, a horizontal camera with a right-hand control rail, and a two-column Review grid.
- `study.js:500-503` toggles `body.landscape-phones`; `study.template.html:23` exposes a **Landscape** button.
- `capture_delivery.py:47-51` captures 828×390 landscape screens, and `delivery/input-landscape.png`, `target-landscape.png`, `review-landscape.png` are shipped.
- `README.md:9` says "In landscape, camera controls form a right-hand rail"; `README.md:17` advertises the Landscape button; `README.md:102` says the study includes portrait and landscape compositions.
- `REMEDIATION.md:5` says the decision was to "center the camera and support landscape"; `REMEDIATION.md:20` describes "a control rail in landscape"; `REMEDIATION.md:24` calls landscape Review "scrollable, not an all-content-at-once layout."
- `SPEC.md:92` says "portrait/landscape"; `SPEC.md:98` says "orientation-aware portrait/landscape layout"; `SPEC.md:382` (R21) says "both orientations work."

This is a real landscape mode, not landscape-photo support.

### Why it is wrong

The interview already says the right thing (`docs/redesign-interview.md:44`):

> Standard orientation-aware camera; landscape capture then portrait continuation must work. Remove custom screen-lock/counter-rotation choreography.

"Landscape capture then portrait continuation" is not a landscape app layout. The current build implements the opposite: the app becomes landscape and stays there.

The shipped landscape screens prove it is not merely unnecessary:

1. **Landscape Target loses the decision.** `delivery/target-landscape.png` shows only the sentence pills and "Edit sentence," with an enabled **Continue**. The dictionary meaning — the entire point of Target — is not visible at the first fold. A user can press Continue without seeing which meaning they are exporting. That is a wrong-meaning risk.
2. **Landscape Review loses the review.** `delivery/review-landscape.png` shows the photo, sentence, and a clipped word card; the translation is below the fold. The two-column grid uses the width but not the 390 px height, so the "glance and send" becomes a scroll hunt. The translation is not visible at all.
3. **Landscape Input is worse than portrait.** `delivery/input-landscape.png` centers a small portrait viewfinder in an 828 px-wide canvas, leaves the left half empty, and pushes gallery/shutter/Type into a right-hand rail. The portrait `delivery/input-portrait.png` has a larger preview and a better shutter composition. The landscape mode reduces the camera and spends the extra width on nothing.

This is the answer to "leaves something to be desired": the landscape layout is the problem, not the execution of it. Do not refine it. Remove it.

### What to build instead

- **Portrait-only app composition.** The phone stays 360×828 (or 390×844) in every state. Target, Review, and the camera controls never become a landscape layout.
- **Landscape photo support.** Accept a 4:3 landscape capture, a picked landscape image, or a shared landscape image. Store it orientation-corrected. Show it in the portrait UI:
  - Input preview: keep the full captured bounds visible without stretching. If a landscape image is in the portrait viewfinder, a fitted preview with letterboxing is acceptable because it represents the capture; do not crop the preview without disclosing it.
  - Review card: the photo fills its slot (see Decision 3), including a landscape image.
  - Full-photo overlay: contain the whole image; never crop the viewer.
- **Rotation continuity.** Rotating to landscape to take the photo and back to portrait must preserve the draft. The UI does not reflow; the capture pipeline handles sensor orientation, EXIF, and the stored media.
- **Replace the landscape fixture, not the support.** The study's "Landscape" button should become a **Landscape photo** fixture that swaps the source image inside the same portrait phone. That demonstrates the real requirement without a landscape UI. If the button cannot be repurposed, remove it.
- **Update the authority documents.** `SPEC.md` should say "portrait UI; landscape capture supported," not "portrait/landscape layout." `README.md` and `REMEDIATION.md` must drop the right-hand rail and landscape composition claims. The interview line 44 is the source of truth for capture/continuation.

### Acceptance

- No `#orientation-toggle`, no `.landscape-phones`, no `@container phone (aspect-ratio > 1)`, no 828×390 phone at any point.
- A landscape source image is displayed in the portrait Input preview and the portrait Review card; the Review card has no letterbox bands.
- The draft survives a simulated rotate-away/rotate-back; the phone composition remains portrait.
- Target's meaning is visible at the first fold in portrait, and Continue cannot be pressed without a visible selected meaning.
- `delivery/` contains no landscape composition screenshots; a portrait-phone landscape-photo fixture replaces them.
- `verify_flow.py` has no landscape-layout cells; it has a landscape-photo cell in the portrait phone.

### Files

`flow-layout.css:24-44`; `study.js:500-503`; `study.template.html:23`; `capture_delivery.py:47-51`; `delivery/*-landscape.png`; `delivery/manifest.json`; `README.md:9,17,83,102`; `REMEDIATION.md:5,20,24,30`; `SPEC.md:92,98,100,365,382`; `verify_flow.py` landscape section.

---

## Decision 2 — Translation directly below the sentence (P1)

### What the current build does

`study.js:171-179` renders Review in this order:

```
photo → sentence (.sentence-audio) → vocabulary (.word-hero) → translation (.translation)
```

`verify_flow.py:487` asserts exactly that order:

```python
["photo", "sentence", "vocabulary", "translation"]
```

`README.md:11` says "continuous Japanese/ruby, vocabulary and pitch, definition, then translation." `REMEDIATION.md:18` says the same. `SPEC.md:125` says the contextual image, sentence, vocabulary/definition block with pitch, then translation.

The portrait screenshot shows the result: "I read a book at the library." sits below the entire 本 card, just above Edit/Deck, reading like a caption for the vocabulary card rather than the meaning of the sentence.

### Why the user's order is right

The translation is the meaning of the sentence. A reader should be able to read Japanese and English as one unit, then study the word and pitch. Putting the vocabulary block between them makes the user parse the word card before knowing what the sentence says. It also makes the translation fragile in shorter viewports: at the 246 px photo variant, the translation is pushed below the fold entirely.

The current order follows the old SPEC. The user's latest decision overrides it. This is a deliberate spec change, not a bug fix; record it.

### What to build

- Move the `<p class="translation">` immediately after the `.sentence-audio` block, before `.word-hero`.
- Give the pair a tight relationship: sentence bottom margin ~8 px, translation `bodyLarge` (16/1.6), then 24 px before the word card. The translation may stay `onSurfaceVariant` for hierarchy, but it is key content; do not make it a 12 px caption.
- Keep the translation's `data-edit="translation"` target on the `<p>`; the existing edit test should still work.
- Update `verify_flow.py`'s "review DOM order" to `["photo","sentence","translation","vocabulary"]`.
- Update `README.md:11`, `REMEDIATION.md:18`, and `SPEC.md:125` to sentence → translation → vocabulary/pitch/definition. The note order in Anki is separate; this is the Review reading order.
- Recapture `review-portrait`, `review-landscape` (if it survives), `happy-review`, `review-*-top/bottom`, and the delivery images. Regenerate manifests and hashes.

### Acceptance

- DOM order is photo → sentence → translation → vocabulary; the harness asserts it.
- The translation is visible at the first fold in portrait, at normal and doubled text, with the photo at its normal slot.
- Screen-reader order matches the visual order.
- The translation edit target still opens the editor and returns to the same position.
- No CSS `order` trick: the DOM order is the reading order.

### Files

`study.js:171-179`; `study.css` `.sentence-audio` / `.translation`; `verify_flow.py:487`; `README.md:11`; `REMEDIATION.md:18`; `SPEC.md:125`; captures.

---

## Decision 3 — The Review photo fills its slot (P1)

### What the current build does

- The Review slot is `.context-strip` at **328 × 144**.
- The specimen is **900 × 1200** (3:4 portrait).
- `flow-layout.css:21-22` overrides the base style to `height:144px` and `object-fit:contain; object-position:center`.
- `README.md:62` documents the intent: "Review image height: 144 pixels in portrait, with the full image contained rather than cropped."

With `contain`, the rendered page is about **108 × 144** inside a **328 × 144** card. Roughly **110 px of empty pale background on each side**. It reads as a thumbnail in a frame, not the remembered situation.

The base `study.css` already had `object-fit:cover; object-position:center 33%` on `.context-strip img`. `flow-layout.css:22` is the regression that introduced `contain`.

### Why "fill" is right — and why a blind `cover` is not enough

The photo is the mnemonic. A 108 px-wide page in a 328 px card cannot carry the place, the page, and the selected line. The user's instruction is correct.

But the selected sentence sits near the **top** of this page. The current `object-position:center` is tuned for `contain` (it does nothing). If you switch to `cover` and leave the position at center, the image scales to ~437 px tall and the 144 px window starts around the middle; the title and the selected first line are cropped out. That would fill the slot while destroying the mnemonic.

The tested compromise:

- `object-fit: cover`
- `object-position: center 18%` for this fixture
- keep the 144 px slot height

At 18%, the page fills the full 328 px width, the page title and the selected 図書館で本を読みました line remain visible, and the bottom of the page is cropped (acceptable). The slot no longer looks like a placeholder.

A 4:3 / 246 px cover variant shows more of the page, but it pushes the translation below the fold unless Decision 2 is implemented first. It is an option, not a requirement. The minimal correct change is `cover` at the current 144 px with a focal point.

### The policy that should be explicit

- **Review card:** `cover`, anchored to the selected line (per-photo focal point or a fixture value). Fill the slot.
- **Full-photo overlay:** `contain`. Show the whole image; never crop the viewer.
- **Camera preview:** `contain` or an accurate fitted preview. Represent the captured bounds; do not stretch. A landscape photo may letterbox in the preview; that is honest framing, not the Review card.
- **Landscape photos:** the same Review policy applies. A landscape image fills the 144 px card naturally; a portrait page needs the focal point.

For production, the focal point should come from the OCR line or a user-adjustable crop, not a hard-coded 18%. The fixture value demonstrates the composition; it is not a general crop rule.

### Acceptance

- `getComputedStyle(img).objectFit === "cover"` on the Review card; no background bands at the slot edges.
- The selected sentence line is visible in the Review card; a screenshot crop proves it.
- A landscape source image fills the same slot without letterboxing.
- The full-photo overlay still shows the uncropped image (`contain`).
- The photo remains 144 px tall in portrait; the fix is fill, not a larger hero.
- The selected line's visibility is checked at 320/360/390 widths and at doubled text.

### Files

`flow-layout.css:21-22`; `study.css` `.context-strip` / `.context-strip img`; `README.md:62`; `verify_flow.py` photo checks; `review-photo-straight-pitch` and delivery captures.

---

## New in this revision

These are not part of the three decisions, but they were introduced or exposed by the current build:

1. **Primary and error are both red.** `--primary` `#9C3329`, `--error` `#A51D26`. The semantics are carried by icon, container, and label, but the hue no longer separates "action" from "error." The destructive button is outlined, which helps. If the vermilion identity stays, consider a deeper burgundy for error or a distinct error container. This is P2, not a blocker.
2. **The landscape Target's enabled Continue without a visible meaning** is the strongest evidence that the landscape composition should be removed, not refined. It is a wrong-meaning risk, not a cosmetic flaw.
3. **The plus control** in Review (`aria-label="Add notes and tags"`) is visually an unlabeled `+` next to Edit. The accessible name is good; a visible label or tooltip would help. P3.
4. **The translation at `onSurfaceVariant`** is correct as secondary text, but once it moves under the sentence it is the sentence's meaning, not metadata. Keep it 16 px and legible; do not let it become a 12 px caption. P2.
5. **The evidence counts** (`delivery-summary.json`: 382 checks, 82 screenshots) and the DOM-order assertion are bound to the old layout. They must be regenerated, not hand-edited.

---

## Delta task list

| ID | Priority | Task | Acceptance |
|---|---|---|---|
| **P0-1** | P0 | Remove the landscape composition: the orientation toggle, `.landscape-phones`, the `@container phone (aspect-ratio > 1)` block, landscape captures, and landscape claims in README/REMEDIATION/SPEC. Keep the portrait phone at all times. | No landscape phone, toggle, CSS block, or screenshot remains; Target meaning is visible at the first fold. |
| **P0-2** | P0 | Add a **Landscape photo** fixture inside the portrait phone: a landscape source image in Input and Review, orientation-corrected, draft preserved across rotate-away/back. | Phone width remains 360/390; landscape image displays without letterbox bands in Review; full viewer contains. |
| **P1-1** | P1 | Move the translation directly below the sentence, before the vocabulary card. Update DOM, CSS spacing, harness order, README, REMEDIATION, SPEC §4.5. | DOM order photo → sentence → translation → vocabulary; translation visible at first fold; screen-reader order matches. |
| **P1-2** | P1 | Make the Review photo fill its slot with `cover` and a focal point; remove the `contain` override. Keep the full viewer `contain`. | No background bands; selected sentence visible; landscape photo fills; 144 px height retained. |
| **P2-1** | P2 | Separate action and error hues, or explicitly document why vermilion is used for both. | Error and action remain distinguishable by more than copy; contrast checked in both themes. |
| **P2-2** | P2 | Label or tooltip the Review `+` control; keep the translation legible when moved. | `+` has a visible/accessible label; translation is bodyLarge, not caption-sized. |
| **P2-3** | P2 | Regenerate harness results, delivery manifest, final-screens, hashes, and counts from the final build. | `verify_flow.py` exits 0; counts match the files; no stale landscape capture remains. |
| **P3-1** | P3 | Rename/repurpose the study control from "Landscape" to "Landscape photo" (or remove it). | The control changes the photo, not the phone composition. |

---

## Test matrix

| Path | Expected |
|---|---|
| Portrait 360×828 Review with photo | DOM: photo, sentence, translation, vocabulary. Translation directly under the sentence. Photo fills 328×144; selected line visible. |
| Portrait 390 / 320, light/dark, 1×/2× | Same order; no clipping; translation and selected line visible; actions reachable. |
| Landscape photo fixture in portrait phone | Phone width 360; image natural width > height; Review card cover; Input preview fitted; full viewer contain; draft survives rotation. |
| Landscape composition | Does not exist: no toggle, no 828×390 phone, no two-column Review, no right-hand rail. |
| Target | Meaning visible at first fold; Continue disabled until the meaning is selected; no landscape variant. |
| Full photo | Whole image visible, letterboxed if needed; close returns to the Review card. |
| Harness | Review order updated; landscape-layout cells replaced with landscape-photo cells; counts regenerated. |

## Do not

- Do not keep the landscape two-column Review as a "responsive" variant. The user said no real landscape mode.
- Do not merely hide the Landscape button. Remove the layout and the tests.
- Do not use `object-fit:contain` in the Review card.
- Do not use `cover` with `object-position:center`; it crops the selected line on this fixture.
- Do not change the full-photo viewer to `cover`; it must show the whole image.
- Do not move the translation with CSS `order`; move it in the DOM.
- Do not update screenshots or check counts without rebuilding `flow.html` and running the harness.
- Do not change `SPEC.md` silently; record the user's decisions in §4.1/§4.2/§4.5 and the acceptance matrix.

## Superseded items in the first handoff

- `P0-P3-HANDOFF.md` **P1-1** says "if following SPEC, vocabulary before translation." The user's latest decision puts translation directly below the sentence. **Superseded.**
- `P0-P3-HANDOFF.md` **P1-7** suggested shrinking the photo. The user wants the existing slot filled, not a larger hero. Keep 144 px; change `contain` to a focal `cover`. **Reframed.**
- `P0-P3-HANDOFF.md` **P2-5** allowed "either add a landscape composition or state it is not represented." The user chooses neither: no landscape composition, but landscape photo support. **Superseded.**
