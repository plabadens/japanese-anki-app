# Review remediation — current UI study

**Historical / superseded:** the [approved 22 October screen-and-copy contract](../../docs/sentence-mining-flow.md) controls the next implementation. Its progress A removes the tracker/preparation page and generated-field repair workflow described below. Those historical user decisions and browser results must not be treated as current instructions or native acceptance.

## Authority and scope

The user's latest decisions take precedence over the earlier review's visual proposals: **restore the top progress bar; center the camera and support landscape photographs in a portrait-only composition; keep the photo in Review and make it fill its slot; select targets by tapping word pills; draw pitch with straight lines and no axis legend.** These decisions are implemented in the offline HTML study and recorded in `SPEC.md` §4.1, §4.2, §4.5, R04, R20 and R21. They are not a native Compose UI implementation.

The current source is `study.template.html`, `study.css`, `flow-layout.css`, `study.js`, and `model.js`. `flow.html` is generated. The earlier `flow.template.html` and its static verification claims are historical.

## Review resolution

| Review items | Current resolution and evidence |
|---|---|
| P0-1 | Submission guard is independent of presentation stage/status. A frozen submitted snapshot survives Back, Escape, backdrop, history Back, injected Edit/Settings actions, direct dispatch, demo reset, Recheck and Open Anki. No route can generate or send again while unresolved. Browser assertions and model tests exercise the routes. |
| P0-2 | Sentence edits retokenize. The edited inflection is selectable, resolves to its lemma, and reaches Review. Unknown text is an explicit fixture boundary rather than a dead token. |
| P0-3 | Exact contiguous occurrence spans are retained. Tappable pills replace the dropdowns and handles by user direction. Shift + arrows and actual pointer drag extend/trim the range; unsupported ranges do not collapse to the first word or masquerade as a pitch miss. |
| P0-4 | Failed entry identities stay blocked, including the counter. Recovery clears the target explicitly, and changing to the noun requires an explicit dictionary choice. |
| P0-5 | `verify_flow.py` is an executable Chromium/CDP runner, not a boolean checklist. It records expected/actual results, exits nonzero on failure, captures outcomes, and binds hashes. `model.test.cjs` provides dependency-free Node regressions. |
| P1-1 | Review follows image → Japanese sentence → translation → vocabulary/pitch/definition. Preparing/failure use transition titles and remain at step 2. |
| P1-2 | Network failure, local pronunciation miss and uncertain submission use separate roles, icons, labels and recovery actions. The unresolved submission retains read-only note content and destination. The exercised network failure fixture preserves source and selected identity through retry/cancel. |
| P1-3 | The later user decision removes the Photo/Type selector entirely: Gallery / shutter / Type in portrait; no landscape layout. A landscape source-image fixture exercises the same portrait composition. Material3 is separately upgraded to the allowed stable 1.4.0; see the dependency document for actual API limitations. |
| P1-4 | The user explicitly reinstated progress. It is a compact three-step bar with accessible values, filled completed steps and outlined current step, no dot/counter clutter, and no Target/Review settings gear. |
| P1-5 | Named type/color/shape roles and exceptions are documented in README. Actual selected-pill, meaning, field and progress contrast is measured in both themes. Token taps no longer fade the entire screen. |
| P1-6 | Target consists of word pills, a contextual sentence correction, entry/reading and meaning choices. The entry count opens homographs. A disabled Continue has a reason. There is no photo peek or repeated instruction heading. |
| P1-7 | Review visibly retains the photograph, makes the word and pronunciation the learning focus, matches both play buttons, and provides a real Deck control. The photo fills its 144 px slot with a focal crop. Long content scrolls above the primary action. |
| P1-8 | Edit reveals correction targets on the content, not a six-row settings index. Reading/pitch controls contain real current values and listed patterns. Confirming unchanged pronunciation preserves the note; sentence-reading changes require deliberate regeneration. |
| P1-9 | Japanese language tags, selection states, progress values, modal focus containment/return and keyboard behavior are exercised. The fake status bar is decorative. True doubled text, including ruby and SVG text, is measured at 320/360/390 in both themes. SVG text fill/opacity receives its own contrast regression after a real dark-mode failure was found. These are browser checks, not TalkBack acceptance. |
| P1-10 | Review uses continuous Japanese, proportional ruby and one inflected-occurrence highlight. Target intentionally uses the requested pills. Pitch retains katakana and a following particle, with straight connections and no axis legend or unexplained “Accent 1.” Source phonetic-mark parity still requires the native renderer. |
| P2-1, P2-2, P2-4 | Note terminology, Continue vs Send, concise state-first copy, role mapping, actual gutters/type and current evidence paths are documented. The citation ledger uses only the three cited design sources. |
| P2-3 | Current results and screenshot manifests are generated from the final HTML. Earlier failures and the original duplicate-send RED are preserved separately; the old “44/44” claim is not current evidence. |
| P2-5 | Empty Type and no-photo typed Review are captured. Portrait covers 320/360/390, light/dark and doubled text. A landscape source-image fixture runs in the portrait phone; viewport resizing preserves the draft. Native rotation/insets remain separate. |
| P2-6 | Keep working is filled; discard uses an outlined error-role treatment. Uncertain discard explicitly warns that an Anki note may still exist. |
| P3 | Shutter side slots are balanced, Type stays beside the shutter, Deck is actionable with a chevron, sheet Close labels are concise, tag borders and local pressed feedback are present, and simulated status-bar content is hidden from accessibility. No redundant app counter or progress dot is retained. |

## Evidence boundaries

`verification.current.json` and `final-screens/manifest.json` identify the exercised artifact and measured cases. `delivery/manifest.json` identifies the separate composition previews. The semantic regressions primarily run at the normal 360-pixel fixture; the responsive matrix separately measures Review layout, dark mode and doubled text. Do not describe this as every interaction retested under every Android configuration.

Camera, OCR, dictionary/pitch lookup, cloud generation, audio playback and Anki submission are explicit UI fixtures. There is no live provider or export evidence. Native gestures, sensor rotation, IME/system insets, TalkBack, process restoration, phonetic-source parity and media quality are not certified by HTML. The native dependency build/test result is separate in `../../docs/material3-upgrade.md`.
