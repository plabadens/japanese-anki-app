# Japanese Anki — Input · Target · Review

An offline, interactive UI mockup for a native Kotlin/Compose app. Camera, OCR, dictionary lookup, network generation, audio playback and AnkiDroid submission are represented by explicit fixtures; they are not connected services.

## Flow

| View | Content | Primary action |
|---|---|---|
| Input | A centered portrait camera preview. Gallery is left of the shutter; Type is right. Landscape photographs are supported as source images without changing the portrait composition. Type opens an empty writing canvas with an optional photo. | Capture, then Use photo; Continue from Type |
| Target | Tappable word pills, a selected occurrence and its dictionary meaning. Drag across pills or use Shift + arrows on the group to extend a span. | Continue |
| Review | The captured image, continuous Japanese/ruby, translation directly below the sentence, then vocabulary and pitch, definition. Type without an attached photo has no image placeholder. | Send to AnkiDroid |

Preparing is a transition between Target and Review. A network failure retains the current source and resolved vocabulary. A missing dictionary or pronunciation record remains a local selection problem. An uncertain Send retains a read-only submitted snapshot until reconciliation or explicit local-draft discard.

The default Review is not editable. Edit reveals direct correction targets on the actual content. Notes and tags appear only after they are added. The destination is remembered, with a secondary Deck control.

A compact three-segment top progress bar marks Input → Target → Review. Preparing and generation failure remain at step 2; Review is step 3. Completed segments are filled, the current segment is outlined, and accessible values name the current step. The study's Landscape photo control swaps the source image without changing the portrait phone composition or resetting the draft.

## Visual system

The fixed palette combines vermilion actions, warm paper surfaces and ink-blue pronunciation. It is a proposed identity, not a claim that there is one universal Japanese palette. Light and dark use equivalent semantic roles.

Material 3 Expressive uses typography, color, shape, motion and containment to guide attention.[1] This composition concentrates emphasis on the word and pronunciation rather than making every control equally expressive.[2] The HTML maps those roles for discussion; native components and Material motion schemes must provide the actual Android behavior.[3]

### Typography

| Use | Role | Base size / weight |
|---|---|---|
| App bar, entry name, Review sentence | titleLarge | 22 / 500; sentence 400 |
| Preparing headline | headlineMedium | 28 / 500 |
| Vocabulary | displaySmall emphasized | 36 / 700 |
| Definition, translation, entered text | bodyLarge | 16 / 400 |
| Supporting information | bodyMedium | 14 / 400 |
| Button and section labels | labelLarge | 14 / 500 |
| Target word pills / Type canvas | named Japanese text exception | 24 / 400 |
| Ruby | proportional annotation | 50% of the Japanese base |
| Pitch labels | diagram-specific exception | 22 katakana, scaled with the chart; no axis legend |

The 200% switch doubles mock-phone text and pitch geometry. The decorative status-bar simulation is excluded from assistive technology and stays at 12 pixels. Study-page navigation is outside the phone's text-scaling test.

At viewport widths of 360 pixels or less, the compact app-bar title uses a named 20-pixel exception. Word-pill border insets compensate for the selected 2-pixel outline without changing the pill's text position.

### Color roles

| Role | Light | Dark |
|---|---|---|
| surface | `#FFF8F5` | `#1C1411` |
| onSurface | `#2B1712` | `#F5DED4` |
| primary / onPrimary | `#9C3329` / `#FFFFFF` | `#FFB4A3` / `#5D160D` |
| secondaryContainer / onSecondaryContainer | `#F1DDD2` / `#4B2B20` | `#4A3329` / `#F5DCCC` |
| selected-state outline | `#795247` | `#D2A693` |
| outline | `#8B6D60` | `#B89D8E` |
| tertiary: pitch contour | `#354B70` | `#B3C8EE` |
| error | `#A51D26` | `#FFB2B7` |
| uncertainty container / ink | `#F8E6B7` / `#4C370A` | `#4A3B1D` / `#F8E6B7` |
| data-information container / ink | `#DCE6FA` / `#182E50` | `#273D5E` / `#DCE6FA` |

A selected meaning has an explicit checked control and a contrasting outline. A low-contrast tonal fill is not used as the sole selection indicator. Field borders use the outline role. Error, data unavailability and unresolved submission have different labels, icons and actions.

### Shape, spacing and motion

- Content gutters: 16 pixels; writing and transition content: 24. Word-pill container inset: 16. Review image height: 144 pixels in portrait; the image fills the slot with cover, cropped around the selected line. The full-photo viewer contains the whole image.
- Spacing scale: 4, 8, 12, 16, 24; pill gaps are 8 and the contextual correction row has a 16-pixel bottom separation.
- Shape scale: 4/8/12/16/24/28. The circular 80-pixel camera shutter is a camera control, not a content card.
- Primary actions: at least 56 high; standalone icon/text controls and word pills: at least 48 high. The user's requested word-pill selection replaces the previous continuous-text handle design on Target. Review retains continuous Japanese.
- Pressed shape/color feedback is local to controls. Token taps do not animate or fade the whole screen. Reduced-motion preference disables animation and transitions.
- Native mapping: small top app bars, compact progress, buttons/icon buttons, selectable chips, modal bottom sheets, text fields and indeterminate progress. HTML elements are composition equivalents, not native Material components.

## Japanese rendering and corrections

The main fixture is **図書館で本を読みました。**, with **本 / ホン / book; volume**. The illustration is an authored textbook specimen, not a camera photograph or OCR benchmark.

Target separates tokens into tappable pills; Review renders the sentence as continuous Japanese. Selection is stored as one UTF-16 occurrence span, with Unicode boundary validation. A multi-token range is retained exactly; an unsupported range is identified as a missing entry, never silently shortened to its first token. Inflected occurrences remain in the sentence while the vocabulary resolves to a lemma.

Pitch uses katakana morae joined by straight lines, with a following particle in parentheses and no axis legend or unexplained accent number. Word and sentence playback use the same icon-button component. Pronunciation editing shows current readings and the fixture's listed patterns; it does not allow drawing an accent or manufacturing an unsupported word reading. Sentence reading corrections invalidate sentence audio and require deliberate regeneration.

The production app must use its source-backed pitch component, including nasalization/devoicing marks and source ordering. Fixture contours and playback affordances are not linguistic-data or audio-quality acceptance.

## Source and execution

- `study.template.html` — page structure.
- `study.css` — role-based styling.
- `flow-layout.css` — current progress, word pills, camera centering and portrait photo crop.
- `study.js` — rendered views, interaction, pill/range selection and fixture controls.
- `model.js` — pure flow state and guards.
- `model.test.cjs` — executable model regression tests.
- `build_mockups.py` — embeds model, presentation, fonts, specimen and font notices into `flow.html`.
- `verify_flow.py` — executable Chromium regression and evidence capture.
- `verification.current.json` — generated results for the exact exercised artifact, not an acceptance checklist.
- `final-screens/manifest.json` — current captured-image identities and scenarios.
- `delivery/` — selected current preview images and their separate manifest.
- `history/` — historical artifacts, not the current design authority.
- `P0-P3-HANDOFF.md` — independent review input, not a second product specification.
- `REVIEW-PASS-2.md` — review input for the landscape/translation/photo decisions; historical after implementation.
- `REMEDIATION.md` — resolution of the review against the user's latest decisions and actual test scope.

Use an available Python 3 interpreter to run `build_mockups.py`. The browser harness documents its interpreter/Chromium prerequisites in `--help`; it must exit non-zero on failed checks. Model tests use Node's built-in runner, without npm dependencies.

The native dependency change and its actual resolution/build evidence are separate in `../../docs/material3-upgrade.md` relative to this directory's parent project documentation; the project-root path is `docs/material3-upgrade.md`. A successful dependency build does not turn this HTML into native UI acceptance.

## Scope

The study is portrait-only. Landscape photographs are exercised as source images inside the portrait phone; there is no landscape composition. The verification matrix covers 320, 360 and 390-pixel portrait widths, light/dark and doubled text. Browser resize preserves the draft. Browser scroll reachability and viewport resizing are not native font-scale or sensor-rotation certification.

Native camera rotation continuity, IME/system insets, Android Back, TalkBack, process persistence, true speech/translation and confirmed AnkiDroid export require the production-component Compose prototype. The HTML is not a replacement frontend and must not be shipped in a WebView. No photographs, credentials or source text are uploaded by this study.

## Sources

[1] https://design.google/library/expressive-material-design-google-research — Google Design: expressive UX research
[2] https://m3.material.io/blog/building-with-m3-expressive — Material Design: building with M3 Expressive
[3] https://developer.android.com/develop/ui/compose/designsystems/material3 — Android Developers: Material 3 in Compose
