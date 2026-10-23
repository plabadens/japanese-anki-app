# AGENTS.md

Working rules for this repository. Deliberately short: **the code and its tests are the contract.**
There is no specification document to keep in sync, and adding one back is a regression.

- If you need to know what a screen does, read the screen and its tests.
- `USER-STORY.md` says why the app exists. `SCREENS.md` says what each screen is for and what must
  not appear on it. Neither restates copy, dimensions or element order — those live in resources,
  code and tests.
- When behaviour changes, the tests change with it. A behaviour change with no test change is not
  finished.

## Never do these

- Never log credentials, API keys, sentence text, or dictionary content off the device. No remote
  upload, no telemetry, no analytics endpoint.
- Never make a paid provider call unless the user tapped the control that authorises it. No
  background retries that spend money.
- Never blind-reinsert into AnkiDroid after an uncertain write. Keep the frozen note, read
  AnkiDroid, and resolve the outcome explicitly.
- Never add fake providers, debug backdoors, test API keys, or bypass flags to a production path —
  including "just for acceptance".
- Never weaken or delete a test to make a build pass. Fix the code, or state precisely why the
  expectation was wrong.
- Never commit secrets, keystores, or generated build output.
- Never mention NHK recording rights in any output — replies, reports, task comments, handoffs,
  release notes, or generated artifacts.

## Accessibility floor

- Every interactive control is at least 48 dp.
- Text never clips at 200 % font scale, at 360 × 828 or 390 × 844.
- One instruction per state, in one place.

## Git and delivery

- Commits and authorship are `Pierre Labadens <labadens.pierre@gmail.com>`. No agent identities, no
  co-author trailers.
- Work on your own task branch. Task branches stay local: never push one, not for CI, backup or
  review.
- `dev` is the integration branch; `dev` and `main` are the only branches on the remote.
- Publishing means merging into `dev` and pushing `dev`. Before that: `verifyFast` green,
  `renderDesignStates` producing its full set, `git diff --check` clean.

## Build, test, render

The toolchain comes from Nix. There is no Gradle wrapper.

```sh
# fast gate: JVM tests + Roborazzi, offline, no device
gradle --offline -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" verifyFast

# full CI: fast gate + design render + debug APK
nix develop --command ./scripts/ci-build-apk.sh

# design states -> build/design-states, uploaded by CI as an artifact
:app:renderDesignStates
```

Design screenshots are **artifacts, not sources**. Do not commit PNGs; CI publishes them so the
design can be reviewed without a checkout. Regression value lives in numeric assertions — bounds
that must not move, symmetry, hit-target sizes, one hint line — not in image diffs.

## Emulator

Only when the task explicitly asks for device work. Every call goes through the pinned wrapper:

```sh
nix develop --command ./scripts/emulator-adb run <shell|install|exec-out|logcat|…>
```

Never bare `adb`. Never uninstall when `install -r` will do.

## Environment problems

If the toolchain, emulator or Nix setup misbehaves, report it and propose a clean fix. Do not work
around it silently.
