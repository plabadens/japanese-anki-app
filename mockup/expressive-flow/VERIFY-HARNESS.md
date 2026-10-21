# Executable browser regression harness

`verify_flow.py` drives the built offline HTML through an isolated headless Chromium
using a **Python standard-library-only WebSocket/CDP client**. No Playwright,
websocket-client, npm packages, or global installs are needed. Python 3.10+ and
Chromium are prerequisites; availability in the repository's Nix dev shell is not
assumed. The authoring host has these executables:

```sh
PY=/nix/store/q55vy0pdyihvwpjlwn0xavm8nyz5v4fh-hermes-agent-env/bin/python3.14
CHROME=/etc/profiles/per-user/hermes/bin/chromium
```

Run from the repository root **after rebuilding the final artifact**:

```sh
"$PY" mockup/expressive-flow/verify_flow.py \
  --chromium "$CHROME" \
  --html mockup/expressive-flow/flow.html \
  --output mockup/expressive-flow/verification.current.json \
  --screens mockup/expressive-flow/final-screens
```

These are the current generated evidence paths. Previous hand-written claims,
earlier images and failed integration runs are historical, not current proof.
The harness does not rebuild or modify UI, model, build or dependency sources.
It does not wait for an updated build. `--no-screens` skips image generation;
`--screens` enables real Chromium PNGs and a generated `manifest.json` containing
PNG dimensions, SHA-256 hashes, state, text scale, width, theme and scroll position.
Running into an existing output directory overwrites matching generated filenames
and the manifest; use a fresh directory when retaining an older run.

Every assertion records expected and actual values in the JSON report. Failure
exits **1**, success exits **0**. Each case is independently seeded where possible;
a failing case does not silently mark remaining cases successful. An absent API v2
is a blocking failure, not an implicit skip/pass. The runner prints a case/path →
expected → actual message for failures. There is no hand-written success total.

## Coverage

- Capture → Use photo → explicitly select the noun 本 → Continue → ready Review →
  Send → empty Input; in-flight duplicate dispatch does not replace the snapshot.
- **P0-1:** uncertain Back, Escape, sheet Escape, backdrop, actual history
  `pushState`/`back`/`popstate`, Edit, Settings, direct Continue/Send/Set sentence,
  demo reset, Recheck and Open Anki. Each route must retain the submitted snapshot,
  prohibit creating/sending, and keep note identity visible. Explicit local discard
  is tested separately and must disclose that an Anki note may still exist.
- **P0-2:** edit to `図書館で本を読んだ。`, exact re-tokenization, selectable known
  inflection, one continuous highlight, explicit unknown-token disclosure/recovery.
- **P0-3:** tappable word pills, no dropdowns or handles; real Shift + arrow
  extension/trimming, invalid-range rejection, exact multi-token state and highlight;
  real pointer drag across pills. This follows the user's later pill-selection decision.
- **P0-4:** missing and counter identities remain blocked on recovery/re-tap;
  explicit entry selection is required to change meanings. Clear-target outcomes,
  unavailable word/reading/meaning and recovered outcomes are captured.
- Simulated failure through the visible “Fail next generation” control, retry,
  source retention, cancel, ignored late completion, Edit, discard/cancel-discard.
- Empty Type and typed no-photo Review; notes/tags filled and saved; real outcomes.
- No top Photo/Type pill; Type right of shutter; vermilion shutter; no photo/caption
  atop Target; identical named sentence/word play icon buttons; katakana pitch,
  straight-line mora connections and following particle, no axis legend or “Accent 1”;
  full-photo escape and targeted corrections. Unchanged pronunciation confirmation
  preserves the note; a sentence-reading correction requires deliberate regeneration.
- Restored three-step progress with accessible values; Preparing/failure remain at
  step 2. Completed, current and upcoming progress states receive computed contrast
  checks in both themes.
- Centered portrait camera at 360×828, reachable capture/confirmation, full flow
  to Review with the photo retained, a landscape source-image fixture in the same
  portrait phone, and draft retention on viewport resize.
- Real notification expiry while an editor is active, for both the same draft and
  a previous draft, must preserve focus and entered text.
- Keyboard tab reachability, sheet focus entry/trap/restore, Escape-save,
  focus-visible outline, arrow movement between radios and keyboard deck selection.
- **320/360/390 × light/dark × normal/200%** real rendered photo Review. The
  harness hides only study-board chrome and gives the phone the exact device
  width/828px height; its injected device-fixture CSS is recorded in the report.
  It does not change application-internal sizing/layout, text or overflow rules.
  It compares every nondecorative direct text run's computed/rendered size,
  including labels, ruby and SVG pitch text, against the normal render; a toggle
  class or larger container is insufficient. It checks hidden clipping,
  horizontal overflow and scroll-reachable vocabulary/translation/primary action.
  Both top and scrolled-bottom renders are captured. Dark failure/data/uncertain/
  span fixtures are also captured.
- **Real non-text contrast:** selected-word fill or its actual 2px contrasting
  boundary, checked-meaning fill/boundary, and textarea border against field fill,
  in both themes. Measurements use browser computed colors and record ratios;
  unused role-token pairs are not substituted for rendered elements.
- Uncaught exceptions, rejected promises, console errors and Chromium log errors;
  no external HTTP(S)/WebSocket/provider/export requests.

The fixture API is used for reproducible starting states and adversarial attempts;
normal transitions use real CDP mouse/key/text input on the actual document
handlers. There are no fabricated provider results or real provider/export calls.
The runner allocates its own loopback debugging port via `--remote-debugging-port=0`,
a unique profile and fontconfig under `$TMPDIR`, and terminates its own Chromium
process group on exit. It never touches another browser session/port. `--scratch`
can select an existing scratch directory. The default honors `$TMPDIR`, falling
back to `/srv/hermes/.hermes/cache/scratch`, never `/tmp`.

## Recorded legacy RED

```sh
"$PY" mockup/expressive-flow/verify_flow.py \
  --html mockup/expressive-flow/history/previous-study/flow.html \
  --output mockup/expressive-flow/verification.legacy-red.json \
  --screens mockup/expressive-flow/legacy-red-screens \
  --legacy-red
```

This command was actually run against the preserved legacy artifact. It exited
**1**: Review/uncertain → real Back → Target/ready → Generate → Send reached
`sending`. See `verification.legacy-red.json` for exact observed values and
`legacy-red-screens/manifest.json` for actual Chromium screenshots/hashes. This is
a deliberate regression failure, not a green acceptance report. The history path
makes the baseline reproducible even after the parent rebuilds `flow.html`.

Runner syntax and primitive tests:

```sh
PYTHONPYCACHEPREFIX="$TMPDIR/flow-verify-pycache" "$PY" -m py_compile \
  mockup/expressive-flow/verify_flow.py
"$PY" mockup/expressive-flow/verify_flow.py --self-test
```

The three primitive tests cover computed contrast/compositing, PNG dimensions,
and fragmented/masked WebSocket handling with an interleaved ping. They passed.
The current artifact was rebuilt and exercised. `verification.current.json` is
the authoritative machine-generated total and binds the exact HTML and harness
hashes. `final-screens/manifest.json` binds the outcome images to that artifact.
`capture_delivery.py` separately captures the design board, the portrait
composition, and a landscape-photo fixture in `delivery/`; these are composition
previews, not extra regression checks.

## Boundaries

This is HTML composition-study evidence, **not native acceptance**. Compose,
Android fontScale/IME/TalkBack, native insets, landscape capture/rotation continuity,
Material dependency selection, production tokenizer/dictionary/pitch sources,
camera/OCR, audio, cloud services and Anki duplicate/export integration remain
native/integration work. Pixel screenshots still need human visual review for
kinsoku, ruby aesthetics and hierarchy; DOM geometry/contrast assertions do not
certify those subjective qualities. The text test is genuine 2× browser-rendered
text, not a claim that Android accessibility font scaling has been tested.
