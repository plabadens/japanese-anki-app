# Input → Target → Review — UI proposal

**Historical exploration only:** use the [approved 22 October screen/copy contract](../../../../docs/sentence-mining-flow.md) and progress A, not this counter/transition/correction workflow. This study is not native conformance evidence.

**Status:** exploratory design mockups for discussion, not a production UI or a specification replacement. The existing Kotlin/Compose app and the user’s modified `SPEC.md` are unchanged.

## Recommendation

Use three separately routed views with different dominant objects:

| View | Dominant object | User decision | Primary action |
|---|---|---|---|
| Input | Camera photograph, or a writing canvas | What sentence/situation to capture | Shutter, then Use photo; Choose target for Type |
| Target | One selectable sentence and its resolved vocabulary meaning | Which occurrence and which meaning to learn | Create card |
| Review | A composed learning artifact | Is this ready to send? | Send to AnkiDroid |

Generation is a transition between Target and Review, not a fourth user decision and not an editable half-complete review screen. No network operation begins on a token tap. The mockup's generation and Send transitions are explicitly simulated.

The repeated stage title, small `1 / 3` counter, and three-segment progress line establish place without instructional paragraphs. The view hierarchy—not explanatory prose—carries the workflow. Action labels remain visible.

## Baseline and interview alignment

Read `docs/redesign-interview.md`, current `SPEC.md` §4, the superseded `docs/ui-plan.md`, current `MainActivity.kt` and `ui/ReviewCard.kt`, and inspected `releases/v0.3/screens/final/prepare-screen.png` and `review-light.png`.

The interview specifically asks for quick visual confirmation, optional notes/tags, retention of the contextual photograph, and a low-latency local selection phase before network generation. The old UI plan is marked superseded and is not the design authority. The shipped-screen evidence still shows a sentence text field plus separate token controls; the review evidence shows a permanently editable translation, empty Notes, repeated pencils, and inline operational copy.

### How the proposal addresses that

- **Input is its own view.** Photo and Type are two input modes. Photo uses a large 3:4 upright camera frame; confirmation is an Input substate. There is no target list, dictionary panel, or review content here.
- **Target displays the sentence once.** Tap within the sentence; the selected occurrence has tonal containment, stronger type, and an underline. The meaning is visible beneath it. Sentence correction and contiguous boundary adjustment are secondary controls. A small reference photo opens the full frame.
- **Review is reading, not data entry.** Context photo, Japanese/ruby/selected occurrence, translation, and a vocabulary block containing definition, pitch, and word playback. Sentence playback is quieter. One Edit entry point opens targeted corrections. Notes and tags are absent until added, then visible before sending.
- **Destination is remembered.** A quiet deck label appears above Send; choosing a destination is not another recurring mining step.
- **Exceptions stay contextual.** OCR ambiguity and dictionary ambiguity use sheets over the retained stage. Generation failure offers Retry/Edit/Discard. A dataset miss offers reselection rather than network retry. Uncertain insertion offers recheck/open, never a second Send.

## Material 3 Expressive research

### What the official research says

Google describes Expressive as the intentional use of color, shape, size, motion, and containment to make key elements easier to find and related content easier to understand.[1] The published work spans 46 studies and more than 18,000 participants.[1] The reported “up to four times faster” result concerns spotting key elements in the tested designs; it is not evidence that this app or these mockups are four times faster.[1]

The same research warns that breaking familiar interaction patterns, or removing useful action labels, can reduce usability despite visual appeal.[1] That is particularly relevant here: a visually sparse screen still needs understandable, labeled actions.

The official design guidance recommends a limited number of expressive hero moments and explicitly warns that too many are overwhelming.[5] The proposal therefore emphasizes the selected word and the final vocabulary/pronunciation composition, rather than adding expressive ornament to every control.

Expressive buttons have expanded size and shape choices, including pressed/selected shape changes; labels should remain concise and sentence case.[7] Material’s motion system provides expressive and standard schemes, using spatial springs for geometry and effects springs for color/opacity.[8] These are native implementation directions, not a claim that the HTML CSS transitions implement Compose's spring physics.

Android's official Compose guidance covers the Material color/typography/shape systems, dynamic color, role-correct contrast, and the need to verify customized components.[3] Some APIs require opt-in; the production implementation must check the exact selected dependency/API rather than assume every Expressive component is stable.[3]

### Translation into this design

| Expressive principle | Application here | Restraint |
|---|---|---|
| Emphasized typography | Distinct stage titles; Japanese sentence and vocabulary lead | No explanatory headline/subtitle stack inside each step |
| Meaningful containment | Sentence selection and pronunciation each have a deliberate surface | No box or card for every persisted field |
| Shape contrast | Rounded large actions; tighter selected toggle shapes; organic loading indicator | No decorative blobs, badges, or shape library sprinkled through content |
| Semantic color | Sage primary/action roles, tonal selection, separate amber pitch, error roles | A fixed proposed palette, not a claim of dynamic-color implementation |
| Motion | Brief stage change; tactile selected/pressed response; one loading state | No forced waiting choreography, fake progress percentages, or celebratory screen |
| Familiar interaction | App bars, Back, labeled buttons, sheets, grouped choices | No navigation bar pretending the sequential stages are independent destinations |

Three composition approaches were considered: a compact utility layout, a photo-led layout throughout, and the recommended focused-stage layout. The first risks retaining the form mentality; the second makes the photograph dominate decisions where sentence/meaning or finished content should lead. The proposal takes photo dominance only for Input, selectable text for Target, and a composed artifact for Review rather than offering cosmetic theme variants.

## Visual vocabulary

- **Typography:** Roboto UI, Noto Sans JP for Japanese; embedded in the portable artifact. Proposed normal sizes: stage 26, target heading 30, Japanese sentence 25, vocabulary 32, body 15–17, action 16. Native sizes are intended as sp/dp, not a browser-to-device measurement guarantee.
- **Color:** light surface `#F9FAF3`, ink `#1B241C`, primary `#355B3F`, selected container `#C6EBC3`, pronunciation container `#E7EACB`, pitch `#865600`. Dark equivalents are included. Pitch remains semantically separate from the action tint and has a contour/accent number, not color alone.
- **Spacing:** predominantly 8/12/16/20/24; 20-wide content gutters; 16-wide camera gutter.
- **Shape:** 24 for grouped content; capsule primary actions; 14–16 selected controls. No default heavy borders around reading content.
- **Actions:** anchored above the gesture inset, 56 minimum height for primary actions and 48 CSS-pixel targets for controls inside the mock phone artboards. This does not establish Android dp sizing or TalkBack compliance.
- **Motion:** short illustrative CSS transitions. Reduced-motion CSS is included; production should use Material motion tokens and Android animation settings.[8]

## Deliverables

- `flow.html` — portable, self-contained interactive study. Open locally in a browser. Includes Overview, Try the flow, Secondary states, Design rationale, light/dark, and a large-text composition stress toggle.
- `screens/overview-light.png`, `screens/overview-dark.png` — three-stage boards.
- `screens/` — individually captured primary and secondary mockups, enumerated in `screens/manifest.json` after verification.
- `verification.json` — actual browser verification results, scope, and measured role-pair contrast.
- `flow.template.html`, `build_mockups.py`, `assets/` — editable source and local assets. Rebuild with `python build_mockups.py` using Python 3.

## Verified browser evidence

The final `flow.html` was rendered offline in Chromium and exercised with real pointer events: **44/44 checks passed**. The checks cover the photo and Type routes, no guessed target, meaning gating, generation, retained failure/retry, Back, targeted editing, optional Notes, sheet keyboard focus, full-photo inspection, simulated Send locking, and explicit demo feedback. The 390-pixel browser layout has no horizontal overflow, and the large-text Review scrolls above the anchored action.

All measured primary and secondary mock-phone controls have at least 48 × 48 CSS-pixel targets. Twenty semantic text color pairs across light and dark themes meet 4.5:1; the lowest measured pair is 5.09:1. These are scoped measurements, not a claim that every possible dynamic-color combination or Android accessibility scenario has passed.

The screenshot manifest contains 18 images: two boards, six primary-view captures, eight secondary-state captures, one large-text capture, and one derivative contact sheet. Review corrected an incomplete kana font subset and a snackbar that intercepted the primary button; final captures and checks use the corrected artifact. `verification.json` records the exact HTML hash, results, and native checks not run.

## Prototype boundaries

The main sample is **図書館で本を読みました。**, with **本 / ほん / book; volume**. The textbook image is an authored illustrative specimen, not a real photograph and not OCR evidence. The fixture supports three sample word choices; it is not a general dictionary. The simplified pitch diagram is for composition; production must reuse the real source-backed pitch renderer including its nasalization/devoicing support.

Camera capture, picking/attachment, OCR, dictionary lookup, generation, audio, settings destinations, and Anki submission are mocked or represented by navigation. Controls at those boundaries disclose the limitation. “Sent · demo only” does not create an Anki note. No credentials, photographs, or source text are transmitted. The HTML makes no network requests to run; source links open externally only when deliberately followed.

The large-text toggle is a composition stress mode, **not** Android 200% font-scale certification. Native camera behavior, system Back/keyboard/insets, landscape rotation, persistence/process death, TalkBack, dynamic color, and real provider/export behavior remain to be exercised in a production-component Compose prototype after approval. This HTML is not a replacement frontend and must not be shipped in a WebView.

### Visual self-audit

Anti-slop diagnostic: **0/10** for the specified tells. The side-by-side grid compares sequential app views, not equal feature cards; Roboto is deliberately chosen for a native Material study, not an arbitrary web default. There are no gradients, blur panels, accent rails, invented metrics, decorative icon toppers, or marketing heroes. The central objects differ by stage and the only strongly filled action at each stage moves that stage forward.

Browser verification results are recorded separately; the score is a design self-assessment, not measured user preference or usability.

## Sources

[1] https://design.google/library/expressive-material-design-google-research
[3] https://developer.android.com/develop/ui/compose/designsystems/material3
[5] https://m3.material.io/blog/building-with-m3-expressive
[7] https://m3.material.io/components/buttons/overview
[8] https://m3.material.io/styles/motion/overview
