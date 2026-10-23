# Testing Compose without the emulator

**Status: DECISION — adopted 23 October 2026.** The tool choice is settled; §7 is the work. Nothing
here overrides `SPEC.md`, `BEST-PRACTICES.md` or `android-instrumentation-tests.md`: it changes where
tests run, not what they must prove.

## 0. The decision

**Robolectric + `ui-test-junit4` for behaviour, Roborazzi for pixels, one source set, one Gradle
task.** Not Paparazzi, not Compose Preview Screenshot Testing, not two parallel test trees.

- **Robolectric** answers "is it 56 dp, do the bounds move when a word is selected, does commit fire
  exactly one synthesis". Numbers, not pictures.
- **Roborazzi** renders the same composables and compares against committed references. Same JVM,
  same task as the assertions, so one command proves both and a failure says which.
- The same test file still runs on a device under `@RunWith(AndroidJUnit4::class)`. Moving a check
  back to the device lane, if fidelity ever demands it, is a source-set move — not a rewrite.

**Rejected.** *Paparazzi* renders one frame from a `@Composable` call and cannot interact, so it
cannot prove selection, sheets or transitions — which is what this contract is about. *Compose
Preview Screenshot Testing* has the nicest diff report of the three but is `0.0.1-alpha16` pinned to
AGP 9 RCs; making an alpha renderer the backbone of verification in the same month as a toolchain
upgrade is how a suite becomes the next incident. Revisit when it leaves alpha.

## 0.1 Mockups stop being HTML

`mockup/` and `docs/evidence/prototype/*.html` are hand-built reproductions, and they have already
drifted: they still draw surfaces the contract retired, and judging the build against them produced
contradictions twice. They are replaced by **`@Preview` composables that call the real screens**,
rendered by Roborazzi into committed PNGs under `docs/design/states/`.

That inverts the failure: a mockup can no longer disagree with the app, because it *is* the app. A
design change and its picture land in the same commit, or the golden fails and names the pixels. The
contract remains the authority for **which states exist**; the preview catalogue is the record of
**what has been drawn** — one preview per contract state, and none for a state the contract does not
have. The HTML files move to `docs/archive/` as decision history and stop being implementation
reference.

## 0.2. Spike result — 23 October 2026

The JVM lane is proven on this tree. `ComposeLaneSmokeTest` runs under `AndroidJUnit4` and Robolectric
4.17 at API 37; its two cases cover the ported sentence-geometry/audio-state assertion and a Capture
screen image at `w390dp-h844dp-xxhdpi`. `recordRoborazziDebug` recorded the golden, and a subsequent
ordinary `testDebugUnitTest` run compared it successfully.

| Lane | Cold | Warm |
|---|---:|---:|
| JVM `testDebugUnitTest` — 338 tests | 78 s, 338 passed | 19 s, 338 passed |
| Device `connectedDebugAndroidTest` — `emulator-5554`, API 37 | 301 s, 45 passed | 90 s, 45 passed |

Cold runs followed `gradle --stop` and `gradle clean` inside `nix develop`; the warm run immediately
repeated the same task. Wall time was measured with Bash's `$SECONDS`. These are whole-task counts,
not filtered samples. The two lanes were run on the same host and source tree; the JVM task required
no emulator. From the repository root, compare a golden or re-record it with:

```sh
nix develop --command bash -lc 'gradle --no-daemon --max-workers=1 --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" testDebugUnitTest'
nix develop --command bash -lc 'gradle --no-daemon --max-workers=1 --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" recordRoborazziDebug'
```

The device timing used the same command shape with `connectedDebugAndroidTest` instead of
`testDebugUnitTest`. When changing Gradle dependencies, run `bash scripts/update-gradle-deps.sh` to
refresh the Nix offline dependency lock.

The font gate passes without an app font resource: the Nix shell uses
`pkgs.makeFontsConf { fontDirectories = [ pkgs.noto-fonts-cjk-sans ]; }` and exports its result as
`FONTCONFIG_FILE`. Verify the selected face with
`nix develop --command bash -lc 'fc-match "Noto Sans CJK JP:lang=ja"'`; it returned
`NotoSansCJK-VF.otf.ttc: "Noto Sans CJK JP" "Regular"`. The committed
[`capture-screen-japanese.png`](../app/src/test/screenshots/capture-screen-japanese.png) visibly
renders `古い本を読みました。` as Japanese, not tofu. The PNG is 1170×2532 pixels, corresponding to
390×844 dp at xxhdpi. No TTF is stored in the app or repository.

**Go for both dependent cards.** This is not an apples-to-apples speedup claim: the measured suites are
338 JVM tests versus 45 device tests, and only two cases are new in this spike. The gate it clears is
that a real Capture screen and numeric geometry assertion run at fixed qualifiers, with Japanese
rendering and no emulator. Move the portable Compose tests and build the real-screen preview
catalogue; measure the migrated suite again rather than extrapolating 301 s / 78 s. Keep the camera,
system-inset, AnkiDroid and release checks on device.


---

## 1. The short answer

Yes. A Compose UI test is ordinary Kotlin that drives a semantics tree; the same
`createComposeRule()` / `runComposeUiTest { }` source runs on a device **and** on the JVM. On the
JVM, Robolectric supplies the Android framework. Google states it plainly: most UI tests do not
interact with the framework in ways that need a device, and a Compose test that asserts the UI
changed after a tap is explicitly called out as a case where Robolectric's fidelity is "more than
enough" (`developer.android.com/training/testing/local-tests/robolectric`, updated 2026-03-05).

The parts that must stay on a device are narrow and can be named in one list (§4).

## 2. The three techniques, and what each really runs

| Technique | Runs | Interaction | Visual golden | Good for |
|---|---|---|---|---|
| **Robolectric + `ui-test-junit4`** | JVM, real framework classes, shadows for services | yes | no (needs a companion lib) | behaviour, semantics, geometry, state transitions |
| **Roborazzi** | JVM, Robolectric + native graphics | yes, multi-frame | yes (`captureRoboImage`, compare) | the visual contract, multi-step screenshots |
| **Paparazzi** | JVM, layoutlib — no Android runtime | no | yes | single-frame layout from a `@Composable` call |
| **Compose Preview Screenshot Testing** | JVM, AGP plugin, renders `@Preview` | limited | yes (HTML report) | teams already living in `@Preview` |

Two facts worth knowing before choosing:

- **Robolectric is not a niche hack.** It is Google-maintained, supports every API since 21, and
  Google's own guidance says most Google apps rely on it heavily. AndroidX itself ships
  Robolectric-based local-testing artifacts for Compose, WorkManager, Navigation and Glance — the
  pattern of "one binary, two runtimes" is the platform's own approach
  (`robolectric.org/blog/2026/07/03/designing-local-testing-libraries`).
- **Compose Preview Screenshot Testing is still alpha** (`com.android.compose.screenshot`
  `0.0.1-alpha16`, paired with AGP 9.0.0-rc03 per `developer.android.com/studio/preview/compose-screenshot-testing`,
  updated 2026-09-25). Roborazzi and Paparazzi are the mature options today.

## 3. What it means for *this* app

The four measurements the 23 October acceptance asked for are all JVM-testable, and none of them
needs a device:

| Acceptance check | JVM test |
|---|---|
| 56 dp top bar, 48 dp gear, segmented control height | `onNodeWithTag(...).assertHeightIsEqualTo(56.dp)` |
| shutter `x₁ = x₂` above and below at 390×844 | `@Config(qualifiers = "w390dp-h844dp-xxhdpi")` + `getBoundsInRoot()` arithmetic |
| sentence bounds unchanged when a word is selected | measure before/after selection; assert equality |
| hint line is the only instruction in each state | assert exactly one node in the hint slot; assert absence of the retired surfaces |
| 200% text and dark/light | `@Config(fontScale = 2.0f)` and `qualifiers = "night"` |
| exactly one translation and one synthesis on commit | fake provider, assert call counts — **this is where the injectable boundary pays off** |

The card-surface behaviour tests that tonight ran as `androidTest` (`CardSurfaceBehaviorTest`,
`DraftAudioPlaybackTest`, parts of `ImageExportInstrumentedTest`) are candidates to move to `test`
with `@RunWith(AndroidJUnit4::class)` — the same file, a different source set.

**The UniDic transfer card is a special case worth calling out.** `androidx.work:work-testing` runs
the WorkManager engine and its database on the JVM under Robolectric. The resume-from-persisted-offset
state machine — enqueue, progress, process death, retry, verify→install — is therefore provable
without an emulator *and* without the 426 MiB download, by injecting a fake transfer at the seam.
Only "real bytes from the real host over a real network" stays unprovable, which is exactly the
line we already drew.

## 4. The line that stays on a device

Keep a thin `androidTest` lane, and only for:

1. **Camera / ML Kit OCR** — needs a real camera pipeline. Unless the OCR boundary is separated
   and fed synthetic bitmaps, in which case the *contract* moves to JVM and only the sensor stays.
2. **System insets, edge-to-edge, gesture pill** — Robolectric does not model system UI; the
   window-inset positioning of the shutter row genuinely needs a device.
3. **AnkiDroid round-trip** — a real third-party app receiving an intent and confirming import.
4. **Release smoke** — install the signed APK, launch, one happy path. Once, before publication.

That is a handful of tests, not 45.

## 5. The agent loop — where the time actually goes

Emulator verification costs four things per run, and only the first is obvious: **boot** (a cold
AVD start is the dominant fixed cost of every run), **install** (build + `adb install` +
`am start`), **execution** (each test round-trips over adb with a UI automation connection), and
**flake** (a wedged emulator or a stale `adb` server turns a red test into an infrastructure
question, which is how an agent burns an hour on the wrong hypothesis — see the `ANDROID_SDK_ROOT`
incident on 23 October, where a wrapper exiting 2 was read as "no emulator exists").

A JVM lane removes boot and install entirely and collapses execution into one Gradle test task.
To make that loop fast *for an agent*, in order of payoff:

1. **Seed Robolectric's runtime jars.** Robolectric downloads `android-all` jars on first use; set
   `robolectric.offline=true` and `robolectric.dependency.dir` to a pre-seeded directory (the Nix
   store is the natural home) so no run depends on Maven Central. This is the single biggest
   recurring failure mode for hermetic agent loops.
2. **Gradle configuration cache + build cache**, `--offline` for dependency resolution, and one
   aggregate task (`verifyFast`) that runs the unit + Robolectric + golden lane and fails fast.
3. **Determinism knobs**: fixed clock, `GraphicsMode.NATIVE`, `@Config(qualifiers=…)` instead of
   device resolution, animations disabled, no real network in the fast lane.
4. **Machine-readable verdicts.** JUnit XML plus Roborazzi's diff report — an agent should read a
   pass/fail table and a list of differing pixels, not decode a screenshot.
5. **Retire the `scripts/test-core.sh` kotlinc hack** once the JVM lane works. It exists to dodge
   AGP start-up cost, and it has already cost us a blocked card on a Kotlin-version mismatch with
   Gradle 9.7.1. A real Gradle test task with caching is both faster in steady state and far less
   fragile.

The first cold/warm measurement is recorded in §0.2. It is evidence for moving the portable tests and
starting the preview catalogue, not a claim that every UI interaction belongs on Robolectric; keep the
device boundary in §4.

## 6. Risks and costs specific to this app

- **CJK font determinism is solved for the JVM golden lane.** The Nix development shell pins
  `noto-fonts-cjk-sans` and supplies a `makeFontsConf` through `FONTCONFIG_FILE`; the spike rendered
  kana and kanji with Noto Sans CJK JP and committed the verified image. Keep fonts out of `res/font`
  and do not check TTFs into the repository. Device/system-font consistency remains a separate
  product question; the golden lane no longer makes a host-font guess.
- **Alpha dependency risk** for Compose Preview Screenshot Testing, coupled to AGP 9 RCs. Roborazzi
  is the safer default; revisit when the Google tool leaves alpha.
- **Fidelity gaps** are real: insets, `WebView`, and some `MediaPlayer` behaviour. Keep them on
  device (§4) rather than discovering them as mysterious JVM-only failures.
- **Golden-image maintenance**: every intentional design change requires updating references. That
  is the point (it makes visual change reviewable), but it must be a deliberate, reviewed act, not
  a mass `recordRoborazzi`.

## 7. Proposed sequence

1. **Spike (completed 23 October 2026, no production change).** Added Robolectric + `ui-test-junit4`
   + Roborazzi to `test`, ported one sentence geometry/audio-stability assertion, captured the
   Capture screen golden, measured cold/warm runtime, and settled the CJK font question. The result
   and evidence are recorded in §0.2.
2. **Migrate the surface tests.** Card surface, capture screen, motion/haptics presentation logic →
   JVM. Keep the device lane for §4 only.
3. **WorkManager on JVM.** Move the UniDic lifecycle proof to `work-testing` + the injected fake;
   close `t_4d2eef32` on that evidence.
4. **Two-tier CI.** Fast lane on every commit (`verifyFast`); device lane nightly and before a
   release.
5. **Then, and only then,** decide whether the emulator remains in the per-card agent loop at all.

## 8. What this does *not* fix

It does not remove the need for provider credentials, and it does not make the app's provider
integration verified. A fake provider proves the wiring, the state machine and the layout; it
cannot prove that Google returns a correct translation or that ML Kit reads a sign. Those stay a
human, on-device pass — with a real key — and should be scheduled as such rather than expected from
acceptance cards.
