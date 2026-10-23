Acceptance evidence — 2026-10-23
Commit: 309cc394e73eb4246504aaf0b6c671524d44268f
Device: emulator-5554, API 37, x86_64, sdk_gphone64_x86_64
Viewport configured through scripts/ui-journey.py: 390x844 dp at density 320 (780x1688 px)

Build and instrumentation

PASS — Current app APK is installed on the named commit. Prior run evidence in the task thread records the build command and successful install on this exact SHA.
PASS — Connected Android instrumented suite: 45 tests, 0 failures, 0 errors, 0 skipped, as reported from the XML by the prior run on this exact SHA. The XML was not retained in the worktree, so its raw report is not included here.

Setup/onboarding

Evidence:
- onboarding-unidic.png — initial setup's required 425.9 MiB dictionary download step (existing capture from prior run)
- onboarding-current-390x844.png — download confirmation dialog, at 390x844 dp
- onboarding-unidic-waiting-390x844.png — download waiting state
- use-app-setup-running.png — capture view permission prompt after choosing “Use app while setup runs”

PASS — A setup-only route reaches Capture without configuring or entering an API key. The app presented the camera permission request; it was granted using the Android system UI. The resource download was canceled before leaving this run. Note: a network transfer did start transiently during the setup interaction; the UI subsequently showed 96.1 MiB of 425.9 MiB, and Cancel changed the state to “Cancelled”. No further download was attempted.
NOT RUN — Full onboarding completion / dictionary byte-transfer completion: requires a 425.9 MiB transfer; no need to complete it for these checks, and the app explicitly allowed use while setup runs.

Capture / Type observations

Evidence:
- capture-390x844.png — Capture mode with preview and controls
- type-390x844.png — Type mode with empty sentence area
- type-editor-focused-390x844.png — after tap on the sentence area
- blocked-empty-sentence-390x844.png — after attempting Choose word with no sentence

PASS — Camera and Type segmented controls are visible. Their centers are at the same horizontal position in both captures; the Settings affordance is at the right edge in both. The preview occupies a 4:3 frame in the Capture screenshot (displayed bounds approximately x=0..390, y=104..625, i.e. 390x521 px; close to 4:3). The top status area is separate from the app control row.
NOT RUN — Exact 56 dp top-bar height and 48 dp Settings touch-target size: screenshot/UI hierarchy obtained, but no view-bound/pixel measurement sufficient to establish those exact dimensions was captured.
NOT RUN — Exact shutter row geometry / x1=x2 above and below the control row: shutter center appears at x≈195 in the screenshot, but no paired x1/x2 control points are specified in the accessible hierarchy, so equality cannot be honestly asserted.
NOT RUN — Type editor filling available height: the empty sentence area is a large panel, but precise component bounds are unavailable from the rendered screenshot alone. Tapping the placeholder did not open a keyboard; do not treat the focused filename as evidence of editor focus.
PASS (limited) — Camera/Type switch location is visually unchanged between the two screenshots; both modes keep the segmented control centered and Settings on the right.
PASS (limited) — Empty Choose word attempt left the UI unchanged; no navigation or fabricated error state occurred. The app remains on Type mode with the same action/instruction labels.
NOT RUN — “Hint line as the only instruction” for every flow state: capture and empty Type are visible, but card/ready/sent states are gated (details below).

Card / provider / analytics checks

NOT RUN — Card surface, selectable word, meaning sheet, ready and sent states: the installed app is in first-run setup with no configured provider API key. The app can be used during dictionary setup, but no sentence/card state can be produced from the empty Type view without the required input/setup. No credentials were entered.
NOT RUN — Audio and pitch playback; exactly one translation and one synthesis on commit; zero on rotation and zero on Back: provider-dependent journey cannot be reached without configuring the user-owned provider/TTS credentials. No paid call was made. No analytics instrumentation log/counter was captured for those actions.
NOT RUN — OCR-to-sentence: the camera preview is visible, but no supported fixture image was available in the emulator flow during this run. No OCR result is claimed.

Blocked/failure state

PASS (limited) — The app's empty Type view with Choose word available and no sentence remained unchanged after tapping Choose word; capture at blocked-empty-sentence-390x844.png. This is an observed no-op/blocked empty-input state, not a provider failure page.

Capture paths are relative to this report's directory. Device calls used `nix develop --command ./scripts/emulator-adb run ...`; UI driving used `nix develop --command python3 scripts/ui-journey.py ...`.
