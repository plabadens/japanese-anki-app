# Engineering standards

**Status:** AUTHORITATIVE for how this repository is built, tested, verified, and documented.
`SPEC.md` states what the app must do; this file states how the work is done. If the two disagree
about behaviour, `SPEC.md` wins. If a command here stops working, fix this file in the same change.

**Who should read it:** anyone writing code, tests, commit messages, or documentation in this
repository — human or agent. It is the single home for the rules that used to be scattered across
`SPEC.md` §12/§13/§15, `AGENTS.md`, and `docs/toolchain-notes.md`.

---

## 1. Repository shape

```
app/src/main/java/dev/pierre/japaneseanki/     Kotlin sources (core + Android layer)
app/src/main/java/dev/pierre/japaneseanki/ui/  Compose screens, theme, pure UI helpers
app/src/main/java/dev/pierre/japaneseanki/speech/  Speech provider adapter + hand-rolled JSON
app/src/test/java/dev/pierre/japaneseanki/    JVM unit tests (JUnit 4)
app/src/androidTest/java/dev/pierre/japaneseanki/  Instrumentation tests (API-37 emulator)
app/src/main/assets/                          bundled OCR model, dictionary, pitch index, NHK index, licences
datasets/                                     pinned upstream data + build scripts for the bundled assets
scripts/                                      build, test, dataset, emulator and screenshot tooling
nix/                                          Gradle dependency lock and Nix-side build inputs
misc/Sentence (JA)/                           reference Anki note type and template
mockup/expressive-flow/                       HTML design study (reference, never shipped)
docs/                                         documentation (see docs/README.md)
```

## 2. Module boundaries

The codebase is split by *what a file is allowed to know*, not by feature folder. Respect the split
when adding files; it is what keeps the fast test lane fast.

**Platform-independent core** — top-level `internal` declarations in
`app/src/main/java/dev/pierre/japaneseanki/` that must not import `android.*` or Compose. This
includes the `speech/` provider adapter and `speech/MiniJson` (they are platform-free and in the
fast lane); it excludes `ui/`, which is Compose. This is where parsing, validation, state machines,
serialization, hint building and provider request mapping live: `TargetSpan`, `AnkiFieldContract`,
`AnkiFieldSerializer`, `ManualNoteDraft`, `SentenceReadings`, `VocabularyReadings`,
`JapaneseMorphology`, `OcrCandidateSelection`, `PitchAccent`, `AjtPitchHtml`, `AudioProvider`,
`AudioGeneration`, `DraftAudioStore`, `DraftAudioController`, `DraftAudioIdentity`, `GoogleHttp`,
`GoogleApiError`, `TranslationService`, `HtmlEntityDecoder`.

Consequences:

- A core file must compile with the JDK alone. Verify with `grep -n '^import android' <file>` — the
  answer must be nothing.
- Every core file that gains a test must be added to **both** lists in `scripts/test-core.sh`: the
  `K2JVMCompiler` source list and the `JUnitCore` runner list. Nothing discovers them automatically;
  a test that is not in the runner list does not run.
- The core lane exists because it runs in seconds without the Android Gradle Plugin. Do not pull an
  Android type into it "just for one call" — wrap the platform call behind a seam instead
  (`AudioFileOperations`, `ResourceTransport`, `DraftAudioPersistence`,
  `GoogleHttpConnectionFactory` are the existing examples).

**Android layer** — anything that needs a `Context`, the framework, SQLite, `MediaPlayer`,
`SharedPreferences`, CameraX, ML Kit or the AnkiDroid provider: `MainActivity`, `OfflineDictionary`,
`OfflineJapaneseAnalyzer`, `NativeMeCab`, `JapaneseOcr`, `ImageMediaProcessor`,
`EncryptedSecretStore`, `SettingsPersistence`, `AnkiDroidGateway`, `AnkiMediaImporter`,
`SubmissionRecovery`, `VerifiedFirstRunResources`, `UniDicResourceCatalog`.
Test these with `app/src/androidTest` when the guarantee is the platform's (SQLite, WebP, playback),
and with a seam plus a JVM test when the logic is yours.

**Compose UI** — everything in `ui/`: `FlowTheme` (palette, type scale, shared controls),
`InputScreen`, `TargetScreen`, `ReviewCard`, `SettingsScreen`, `CameraCapture`,
`ReviewSentencePresentation`, `TargetSelection`, `InputCaptureState`, `MaterialSymbols`.
`MainActivity` owns the activity-level state machine and the wiring between screens; it is already
~4 300 lines, so **new composables and new pure decision helpers belong in `ui/`, not in
`MainActivity`**. When you touch `MainActivity`, extract rather than append.

**Decision logic next to the composable.** Anything a screen *decides* — which fields are editable,
what a control is called, where a crop focuses — is a pure `internal fun` in the same `ui/` file,
with a JVM test beside it (`reviewEditableFields`, `reviewAudioControlLabel`,
`reviewTranslationProgressLabel`, `focalCropOffsets`, `ocrFocusFraction`). Keep composables thin and
these functions testable.

## 3. Kotlin and Compose conventions

These are the conventions the existing code actually follows. Match them instead of introducing a
second style.

- **Visibility:** `internal` for top-level declarations, `private` for file-local helpers. Nothing
  is `public` unless it is a platform entry point.
- **State and results:** `sealed interface` hierarchies for anything with more than two outcomes
  (`TranslationResult`, `HintResult`, `DraftAudioState`, `DraftAudioAttemptResult`,
  `DraftAudioFreeze`, `AjtPitchAvailability`, `ReviewSendPresentation`, `ReviewAudioPresentation`).
  Exhaustive `when` beats nullable flags and string statuses.
- **Errors:** failures are values, not thrown exceptions across a boundary. Provider errors are
  classified once (`GoogleApiError`) and surfaced as fixed, sanitized messages — never a raw
  response body, URL, key, or file path. `CancellationException` is always rethrown, never recorded
  as a failure.
- **Threading:** blocking work (files, network, SQLite, image encoding) runs on
  `Dispatchers.IO`; the composition thread never blocks. Coalesce obsolete local work instead of
  queueing it.
- **Persistence:** critical state uses `commit()` and checks the boolean (see
  `SharedPreferencesDraftAudioPersistence` in `MainActivity.kt`, whose KDoc says "commit, never
  apply"), not `apply()`. Durable files are published atomically — write a temporary file,
  `fsync`, validate, then `ATOMIC_MOVE` (see `VerifiedFirstRunResources` and
  `AudioDraftMaterializer`). Partial files must never be mistaken for valid ones.
- **Compose:** stateless composables that take state plus lambdas, `internal`, sizes and colours
  from `FlowTheme` tokens (`FlowPrimaryButton` 56 dp, `FlowRoundIconButton` 48 dp,
  `AppContentMaxWidth`), not literals. Icon-only controls carry an explicit
  `contentDescription` through `semantics`. Japanese text gets language semantics where Compose
  exposes them, TalkBack labels stay in the UI language, and every screen must survive 200 % font
  scale.
- **No new third-party dependencies** without a reason that survives review. JSON is already handled
  by `speech/MiniJson`; HTTP goes through `GoogleHttp` with an injected
  `GoogleHttpConnectionFactory`; there is no Retrofit, no OkHttp, no JSON library, and no DI
  framework.
- **Comments explain why.** One KDoc line naming the source of a rule ("Semantic roles from the
  portrait expressive-flow study…") is worth more than a paragraph restating the code.

## 4. Test policy

What must be tested where:

| Concern | Where | Why |
|---|---|---|
| Parsing, spans, readings, serialization, state machines, hint building, request mapping, cache identity | `app/src/test` (JVM) | Fast lane; no framework needed |
| SQLite behaviour, WebP encode/decode, `MediaPlayer` playback, bundled-asset presence, OCR model | `app/src/androidTest` | Only the platform can prove these |
| Camera, permission flows, provider insertion into a real AnkiDroid, TalkBack | Emulator runbook + recorded evidence | Not automatable from JVM |

- JUnit 4, one behaviour per test, sentence-style camelCase names
  (`@Test fun selectionRetainsExactOccurrence()`), assertions on values rather than logs.
- Every bug fix lands with a test that fails before it and passes after. Every acceptance row in
  `SPEC.md` §14 that can be automated should point at a named test.
- Keep the golden linguistic fixtures: 悪逆 / アクギャク / accent 0 with nested nasal marks,
  受付 / ウケツケ with devoiced ツ, the できる / 出きる / 出来る spelling variants, accent numbers ≥ 10,
  final-mora accents, contracted kana, small っ, and Unicode surrogate pairs. These are the
  regressions that cost the most when they slip.
- Fixtures are checked in, small, and free of credentials and personal data. No test calls a paid
  service; provider tests use the injected fake transport.
- Do not delete or weaken a test to make a build green. If a test encodes an abandoned decision,
  say so in the change and in `SPEC.md`.

## 5. Running the gates

Everything runs from the repository root inside the pinned environment. There is **no Gradle
wrapper**; `nix develop` supplies JDK 17, Gradle 8.14.4, SDK platform 37.0, build-tools 36.0.0,
NDK, CMake, emulator and `adb`.

```sh
nix develop
```

Inside that shell:

```sh
# 1. Fast lane: platform-independent core tests (seconds). Fails fast if gradle is absent.
./scripts/test-core.sh

# 2. Full JVM unit tests + debug APKs for both ABIs.
gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  testDebugUnitTest assembleDebug

# 3. What CI runs (core lane + the Gradle command above).
./scripts/ci-build-apk.sh
```

Nix-side checks (no dev shell needed):

```sh
nix build .#checks.x86_64-linux.core   # builder + dictionary + UniDic packaging self-checks
nix build .#debugApk                   # sandboxed debug APK, runs testDebugUnitTest (doCheck)
nix build .#apk                        # unsigned release APK
nix build .#unidicRuntime              # the first-run UniDic asset that must ship beside the APK
```

The aapt2 override is not optional: the Maven-hosted aapt2 binary does not work in the NixOS
sandbox, so the SDK-provided one is passed explicitly. Keep it.

**APK outputs.** The Gradle path writes separate `app-arm64-v8a-debug.apk` and
`app-x86_64-debug.apk` under `app/build/outputs/apk/debug/`; there is no universal APK. Use x86_64
for the emulator and arm64-v8a for a phone (`adb -s "$ANDROID_SERIAL" install -r …`). The sandbox
`.#debugApk` signs with a throwaway key and cannot replace an existing install; use the Gradle path
with `JAPANESE_ANKI_DEBUG_KEYSTORE` set when update compatibility matters.

**Lint.** Lint is run on integration (`gradle :app:lintDebug`), not by the Nix sandbox build. A
green `nix build` is not a lint result.

**Instrumentation.** `app/src/androidTest` has its own AVD/port recipe, with explicit `adb -s`
targeting so a run cannot drift onto another device: see
[docs/android-instrumentation-tests.md](android-instrumentation-tests.md). Refresh the Nix Gradle
artifact lock (`./scripts/update-gradle-deps.sh`) after changing test dependencies.

**Emulator.** `nix run .#emulator-wrapper -- <command>` provisions and drives the API-37.0
`google_apis` x86_64 AVD (`japanese-anki-pixel7-api37`, Pixel 7, 1080×2400). Useful commands:
`start`, `status`, `install <apk>`, `reinstall <apk>`, `launch`, `restart`, `grant`, `tap x y`,
`theme light|dark`, `screenshot <png>`, `dump <xml>`, `share-text <text>`, `share-image <png>`,
`logcat -d`, `stop`, and the one-shot `session <apk> <out-prefix>` (boot → install → launch →
capture → crash scan → shutdown). Constraints that bite:

- One emulator per AVD. A second instance on the same AVD corrupts its user data.
- Process-isolated shells kill the emulator with their process tree; use `session`, or run the whole
  sequence in one command.
- Device targeting goes through `scripts/emulator-adb`, which pins one emulator serial, refuses
  physical-device serials and unsets `ADB_SERVER_SOCKET`. Never add an unqualified `adb` call.
- `scripts/rebuild.sh [test|build-only]` builds and reinstalls in one step;
  `scripts/pixel.py <png> at|row|col …` measures screenshot geometry instead of eyeballing it;
  `scripts/ui-journey.py` drives a scripted path.

**Dependency and source identity.** After changing dependencies, regenerate the lock with
`./scripts/update-gradle-deps.sh`; a warm Gradle cache is not evidence that the sandbox lock
contains the artifacts. To check *which* inputs feed a build:

```sh
nix eval --raw .#debugApk.src.outPath
nix eval --raw .#checks.x86_64-linux.core.drvPath
```

Editing documentation or screenshots leaves both unchanged. A new dataset or builder script read by
the core check must be added to `coreCheckSource` in `flake.nix` on purpose.

## 6. Build, toolchain and data maintenance

- `flake.nix` is the only toolchain declaration. Do not rely on a globally installed SDK, Java, or
  Gradle, and do not add an undeclared build step.
- Pin versions and upstream revisions; record hashes for generated data. Dataset rebuilds are
  repeatable scripts under `scripts/` (`build_dictionary.py`, `build_ajt_pitch.py`,
  `package_unidic.py`) with regression checks beside them; provenance, hashes and licences live in
  `datasets/`, `datasets/README.md` and the in-app Sources/Licences screen.
- A dataset update is not finished until the linguistic fixtures have been re-run against it and any
  change in coverage or marks is explained in the change.
- Code reuse and derived data each need their own licensing review, and the notices ship with the
  app. An add-on's code licence does not settle the terms of the data it ships.
- SDK licences, build/test/install commands and emulator provisioning are documented, not assumed:
  they live in this file and in `docs/toolchain-notes.md`.
- Signing material stays outside the repository and the Nix store. Local update identity comes from
  `JAPANESE_ANKI_DEBUG_KEYSTORE` (see `app/build.gradle.kts`).
- Deferred toolchain work is tracked in [docs/toolchain-notes.md](toolchain-notes.md): the AGP 9.x
  upgrade for `compileSdk = 37`, the Gradle dependency-lock coverage, the aapt2 override rationale,
  and the emulator caveats. Read it before proposing a toolchain change; do not perform that
  migration as a side effect of another task.
- Do not claim reproducibility. Builds are not byte-identical and no such claim is made.

## 7. Commits and repository etiquette

- Message form: `type(scope): imperative summary`, as in the existing history —
  `feat(resources): bundle NHK index and provision UniDic`, `fix(integration): wire live-test
  remediation`, `docs(evidence): record independent API37 rerun`, `spec: …`, `ci: …`. The body
  carries the evidence: what was run, what passed, what was not run.
- **Identity:** all commits use `Pierre Labadens <labadens.pierre@gmail.com>` as author *and*
  committer. Never substitute an agent identity and never add co-author trailers. Check the
  effective Git identity before committing.
- Worker sessions commit locally; **pushing and publishing are not part of a worker task**. Do not
  force-push, rewrite history, or commit another worker's files.
- One writer per hot file. When several tasks touch `MainActivity.kt` or `flake.nix`, they are
  sequenced, not parallel.
- Work in a worktree for a task branch; do not run builds that write into another worktree's
  `app/build/`.
- If the environment is broken, say so and propose a clean fix. Do not invent a workaround that
  silently changes the build.

## 8. Documentation rules

The document set is deliberately small. Adding a file needs a reason and a place:

| File | Holds | Must not hold |
|---|---|---|
| `SPEC.md` | required behaviour, contracts, field mappings, acceptance criteria (R01–R35) | how to build, how to test, status reports |
| `docs/DESIGN.md` | intent, tone, visual language, non-negotiables of the feel | widget inventories, pixel specs |
| `docs/BEST-PRACTICES.md` | this: modules, conventions, gates, verification policy, doc rules | product behaviour |
| `docs/README.md` | the index: every doc, its status, who reads it | content |
| `README.md` | what the app is, how to get it running, where to read next | duplicated runbooks |
| `docs/archive/` | point-in-time evidence and superseded documents | anything still authoritative |

Rules:

- **One fact, one home.** If a rule matters, it lives in `SPEC.md` (behaviour) or here (process) and
  is linked, not copied. Duplicated rules drift; the copies then disagree.
- **Every archived document starts with a banner** naming its status and what replaced it:
  `> **HISTORICAL (2026-10-22):** … Superseded by …`. A document without a banner is assumed
  current, so never archive silently.
- **Status vocabulary** is fixed: AUTHORITATIVE, REFERENCE, EVIDENCE, HISTORICAL, SUPERSEDED. Use it
  in the banner and in the index.
- **Dated evidence goes to `docs/archive/evidence/<topic>-<date>/`**, with the revision, the
  commands, the actual output and an explicit PASS / FAIL / PARTIAL / NOT RUN verdict. Do not add
  status reports to the root of `docs/`.
- **Do not restate `SPEC.md` in a new document.** Cite the section instead. When `SPEC.md` changes,
  this file and `DESIGN.md` are updated in the same change if they now contradict it.
- **Moving a file means fixing every link to it.** Grep the repository for the old path (including
  `mockup/`, `releases/` and code comments) and update or stub each hit.
- Do not put credentials, tokens, or personal data in documentation, and do not paste raw provider
  responses into it.

## 9. Verification and evidence policy

- **Verdicts are literal.** PASS = executed on this revision and observed. FAIL = executed with a
  defect. PARTIAL = the named part ran and the rest did not. **NOT RUN = not executed, and it is
  never a pass.** An unrun check is reported as unrun even when it is inconvenient.
- **Record revision, environment, command, and result** for every claim: commit or branch, fixture,
  device/AVD, exact command line, test counts, and where the log or screenshot lives.
- **Controlled responses are not live quality.** Fake-transport tests prove request shape, error
  classification and cancellation. They say nothing about whether Google pronounced the sentence
  correctly or translated it well. Live-service quality needs credentials, and without them the row
  is NOT RUN.
- **Never fabricate.** No invented screenshots, hashes, counts, contrast ratios or "should work"
  claims. If a tool is unavailable, report the blocker.
- **A build is not acceptance, and a warm cache is not a build.** A successful compile proves the
  code compiles. A Gradle run that reports UP-TO-DATE proves nothing was executed.
- **Acceptance starts from a clean checkout**: a build and test run in the Nix environment using the
  documented commands, with the exact commands and the resulting APK recorded.
- **Evidence areas.** A release record covers all five, and marks each row with its verdict:

  1. **Linguistic data and rendering** — source licensing and attribution, derived-index fidelity,
     complete readings, Kotlin serialization fixtures, recording reading/accent matching, and the
     reference template rendered in Anki with nasalization/devoicing and pitch contours.
  2. **Cloud operations** — Chirp 3 HD voice/hint/MP3 contracts, one protected shared Google Cloud
     key with per-service validation, and Cloud Translation Basic v2 default NMT requests. Live
     pronunciation and translation quality are assessed only when a key is available and the service
     is enabled, and are reported separately from controlled-response evidence.
  3. **Images** — orientation, full-frame bounds, the 1080 px limit, WebP at the fixed quality,
     metadata removal, readability, and rendering in AnkiDroid on realistic fixtures.
  4. **Platform and export** — Nix build and tests, both ABI packages, API-37 x86_64 runtime,
     AnkiDroid fields/media/destination, duplicate checks, and interrupted-submission recovery.
  5. **Interaction and privacy** — responsive local preparation, a usable Compose flow,
     accessibility, durable drafts, SDK diagnostics, secret protection, and local photo processing.
- **Gate ladder** — run the cheapest gate that can fail first, and do not run heavyweight gates in
  parallel on this host:

  1. targeted compile / single test class;
  2. `./scripts/test-core.sh` (once per branch);
  3. `testDebugUnitTest assembleDebug` (once per branch), plus `lintDebug` on integration;
  4. one coordinated API-37 emulator lane for the runtime checks that need it, with captures and
     logcat retained.

  Do not boot competing emulators, do not run several Gradle builds at once, and do not count a
  green core lane as proof of CameraX, provider import, modal Back, or native rendering.
- **Deliverable evidence** for a release: installable APK(s) + checksum, install/setup
  instructions, screenshots of the actual build, and an acceptance record with explicit
  PASS/FAIL/PARTIAL/NOT RUN rows.
- Trust the user's report of a defect over the test suite. If they saw it, reproduce it.

## 10. Working order

- Build one connected vertical slice before widening the surface: Input → Target → local choice →
  real generation → composed Review → confirmed AnkiDroid note. Every visible playback, edit and
  send control in the shipped slice must perform its real operation; dead controls are not
  placeholders, they are bugs.
- Validate the design with a small interactive **Compose prototype built from production
  components**, then reuse those components in the app instead of building a second implementation.
  The complete journey — interaction and accessibility included — is what gets validated.
- Keep local preparation responsive: OCR, dictionary initialization, image work and any network or
  export call stay off the composition thread, and obsolete analysis is cancelled rather than
  queued.
- When implementing UI, compare against the reference study in `mockup/expressive-flow/` and this
  repository's screenshots. A missing element is an implementation failure; an element that should
  not be there is an implementation failure. Pixel parity is not required; structure and hierarchy
  are.
- Validate on the real build (emulator, light/dark, 200 % text, Back, rotation) before calling UI
  work complete.
