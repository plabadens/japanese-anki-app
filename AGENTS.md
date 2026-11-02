# AGENTS.md

Code and tests describe current behaviour; `USER-STORY.md` and `SCREENS.md` describe intended
behaviour. Resolve contradictions explicitly. Behaviour changes update affected tests and
documentation together.

## Design

Authorities: [architecture](https://developer.android.com/topic/architecture/recommendations),
[state holders](https://developer.android.com/topic/architecture/ui-layer/stateholders),
[data layer](https://developer.android.com/jetpack/guide/data-layer),
[testing](https://developer.android.com/training/testing/fundamentals),
[Material Design 3](https://m3.material.io/). Local taste yields to them.

- Layer and flow one way: UI → state holder → repository → data source; nothing above it reaches a source.
- Screen state belongs in a ViewModel or plain state holder; composables take values and callbacks.
  Give each state one owner; distinguish transient UI state from durable user work.
- Reuse existing code and standard Android/library mechanisms before writing custom infrastructure.
  Add abstractions only for a current need.
- Name composables; keep state in the narrowest scope that reads it.
- Interfaces only at a boundary you do not own; fakes over mocks.
- Test behaviour at the lowest useful layer, plus affected launch, navigation and primary-action wiring.
  Bug fixes include a regression test that fails before the fix.
- Theme, don't style: M3 roles, type and shape scales, tokens; no literal hex, `sp` or `dp` in a screen.
- Material component before a custom one; one primary action, one instruction per state.
- Respect the system: dynamic colour, light and dark, reduced motion.
- Targets ≥ 48 dp; text never clips at 200 % font scale, at 360 × 828 or 390 × 844.

## Never

- Log credentials, keys, sentence text or dictionary content off the device. No upload, no telemetry, no analytics.
- Weaken or delete a test to make a build pass — fix the code, or say why the expectation was wrong.
- Commit secrets, keystores or generated build output.
- Mention NHK recording rights in any output.

## Git

- Author and committer: `Pierre Labadens <labadens.pierre@gmail.com>`. No agent identities, no co-author trailers.
- `dev` is the integration branch: merge into it, and push it only once the 
  gate below is green.
- Before merging: `scripts/dev.sh test` green, `scripts/dev.sh render` complete, `git diff --check` clean.

## Build

No Gradle wrapper; the toolchain is Nix. Go through `scripts/dev.sh`, which caches the dev-shell
environment and keeps the Gradle daemon warm — a bare `nix develop` per command costs ~12 s and a cold
context.

Report the revision, checks actually run, and remaining gaps. Local tests and renders do not
establish device or live-service behaviour.

```sh
scripts/dev.sh test [Class]   # verifyFast: unit, Robolectric and Compose assertions
scripts/dev.sh render         # design states -> build/design-states
scripts/dev.sh apk            # debug APK, both ABIs
scripts/rebuild.sh            # APK + reinstall on the running emulator
nix develop --command ./scripts/ci-build-apk.sh   # full CI: gate + render + APK
```

Design screenshots are artifacts, not sources: never commit PNGs, and put regression value in numeric
assertions rather than image diffs. Use the emulator only when the task asks for device work, always
through `nix develop --command ./scripts/emulator-adb run …` (`ANDROID_DEVICE_SERIAL=<serial>` for an
attached physical device) — never bare `adb`, and never uninstall
when `install -r` will do. If the toolchain misbehaves, report it and propose a fix; do not work
around it silently.
