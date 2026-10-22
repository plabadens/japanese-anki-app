# Live-testing concept gallery

Open `index.html` locally. This is an interactive HTML/CSS/JS discussion prototype, not native Compose evidence. Fixture data and actions are explicitly illustrative: no camera, dictionary/provider, resource download, persistent settings backend, or Anki service is connected. API key input is a visual fixture and is not submitted or persisted.

## Target alternatives

A — exact source sentence above compact selectable pills. Tapping selects one word and updates the fixture lookup and original UTF-16 offsets. No word is selected on entry. The one-line definition is preview-only; a detail disclosure and a left-aligned in-page sense sheet expose illustrative full details. No Extend/Trim controls.

B — sentence-native text with independent start/end handles. Tap selects the whole word and updates the fixture lookup; drag either handle adjusts a contiguous range. The testable example spans `本を` at original offsets 4–6. Punctuation is outside selectable spans. This is a basic HTML pointer approximation, not native gesture/accessibility proof; tokenization and test data are fixed. No Extend/Trim controls.

Recommendation: A is safer as baseline because word-pill tapping is the settled interaction and its boundaries are apparent. B is worth considering only if native usability testing validates the handle discoverability and precision. Both retain word boundaries and disclose fixture lookup behavior.

## Companion screens

Camera has a full-width 3:4 upright illustrative preview pane with Camera/Type toggle. Type exposes a text field; attachment is a fixture. The camera mode has no connected sensor/capture. Settings is reachable by the gear on Capture/Target/Review and has key, voice, default tags, destination, and an icon Back arrow. Voice/tags/deck autosave to browser-local fixture storage; the API key is deliberately not stored. Review exposes Notes and Tags, two SVG speaker buttons, and illustrative loading spinners; speaker buttons only toggle a visual Play/Stop state. Onboarding shows required key and voice inputs, Wi-Fi default, mobile-data size disclosure/confirmation, and per-resource progress. Downloads and setup completion are fixtures; nothing is downloaded or submitted.

## Browser verification actually performed

Chromium via Browser Use CLI on local `file://` page. On reloaded updated artifact:

- Target B enters with `No target selected`; tapping 本 selects original UTF-16 offsets 4–5. Matching light/normal-scale screenshots at 390px CSS viewport: `evidence/target-a-390-current.png` and `evidence/target-b-390-current.png`.
- Keyboard ArrowRight/ArrowLeft on the end handle moves from 本 (4–5) to 本を (4–6) and back, remaining on whole-word boundaries. At 320px with 200%-style text the final word wraps onto multiple lines; real pointer drag of the start handle across lines adjusts to を読みました (5–11), with punctuation excluded. This toggle is a web approximation (sentence 24/25→50px, body 16→32px, pills 18→36px), not Android system font scaling.
- A's full-detail disclosure opens; the sense dialog is in-page and computed `display:grid`; sense choice and Back close it.
- Gear opens Settings and its Back returns to prior screen. Settings exposes four form controls; browser-local voice/tags/deck edits autosave and are restored when Settings reopens. The API-key fixture is deliberately not stored.
- Camera Type toggle hides the preview and reveals the text field. The illustrative Attach and Capture controls only report/indicate fixture limits; they do not attach or capture. Review speaker toggles accessible label to Stop. Continue/Send/setup buttons explicitly report that they are fixtures, not connected actions.
- Onboarding Finish is blocked until both required fields are present; even with values, the button reports fixture-only status. Browser-native mobile-data consent was exercised for accept and cancel; cancel restores Wi-Fi.
- Responsive matrix: 320/360/390 CSS px × light/dark × normal/200%-style × six screens (A, B, Camera, Review, Settings, Onboarding) = 72 cells; all had document scroll width equal to viewport width. Camera pane matched the visible phone's inner width, with a 1px inset at each edge (the card border; no content padding). Browser viewport was 820px high; tests did not prove all controls fit above fold.
- Font loaded in Chromium. No native Compose, Android sensor, TalkBack, IME/system Back, native secure settings persistence, credential, network/download/hash/interruption, real dictionary/sense, provider, or Anki behavior was verified.

## Styling and limits

Reference images informed only compact rounded word units and strong speaker affordances, plus a dark full-frame camera view and prominent shutter/mode control. No Duolingo branding, zoom slider, or unrelated camera functions copied. The portrait pane is upright 3:4 (rotated landscape 4:3 sensor aspect), uncropped by CSS; the illustrative photo itself is only a book emoji placeholder. Design evidence remains HTML-only and must not be cited as native implementation proof.
