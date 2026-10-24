# AGENTS.md

Working rules for this repository. Deliberately short: **the code and its tests are the contract.**
- If you need to know what a screen does, read the screen and its tests.
- `USER-STORY.md` says why the app exists. `SCREENS.md` says what each screen is for and what must
  not appear on it.
- When behaviour changes, the tests change with it. A behaviour change with no test change is not
  finished.

## Never do these

- Don't log credentials, API keys, sentence text, or dictionary content off the 
  device. No remote
  upload, no telemetry, no analytics endpoint.
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
- `dev` is the integration branch. You should not push to other branches unless 
  instructed to do so.
- Publishing means merging into `dev` and pushing `dev`. Before that: `verifyFast` green,
  `renderDesignStates` producing its full set, `git diff --check` clean.

## Build, test, render

The toolchain comes from Nix. There is no Gradle wrapper. Enter the flake's shell through
`scripts/dev.sh` instead of wrapping every command in `nix develop`: it captures the dev-shell
environment once (keyed on `flake.nix`/`flake.lock`), then runs Gradle with the daemon and both
caches warm. A per-command `nix develop` costs ~12 s of evaluation and a cold Gradle context, and
the flake no longer disables Gradle's native services in the shell, so the daemon is reusable.

```sh
scripts/dev.sh test                    # verifyFast: unit + JVM Compose assertions
scripts/dev.sh test 'dev.pierre…Test'  # one class while iterating
scripts/dev.sh render                  # design states -> build/design-states
scripts/dev.sh apk                     # debug APK (both ABIs)
scripts/dev.sh gradle <args…>          # anything else
scripts/rebuild.sh                     # APK + reinstall on the running emulator
```

The test task runs in parallel forks (`-Panki.testForks=N` overrides the default). The hermetic
path is unchanged and still what CI and releases use:

```sh
# full CI: fast gate + design render + debug APK
nix develop --command ./scripts/ci-build-apk.sh
```

Design screenshots are **artifacts, not sources**. Do not commit PNGs; CI publishes them so the
design can be reviewed without a checkout. Regression value lives in numeric assertions — bounds
that must not move, symmetry, hit-target sizes, one hint line — not in image diffs.

Only use the emulator when the task explicitly asks for device work. Every call 
goes through the pinned wrapper:

```sh
nix develop --command ./scripts/emulator-adb run <shell|install|exec-out|logcat|…>
```

Never bare `adb`. Never uninstall when `install -r` will do.

If the toolchain, emulator or Nix setup misbehaves, report it and propose a 
clean fix. Do not work around it silently.
