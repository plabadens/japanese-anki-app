# Documentation index

Read this page first. It lists every document an agent or maintainer is expected to consult, what
each one is for, and how far to trust it.

Four documents carry authority, and they divide cleanly: the contract says what each screen is, the
SPEC says what the app must do, DESIGN says how it should feel, and BEST-PRACTICES says how the work
is done. The README is the entry point; everything else is support, evidence or history.

| Document | Status | Answers |
|---|---|---|
| [card-flow-contract.md](card-flow-contract.md) | **AUTHORITATIVE** | What each screen contains and says, and in what order: the approved 22 October Capture → Card contract and the only permitted app-authored copy |
| [sentence-mining-flow.md](sentence-mining-flow.md) | **AUTHORITATIVE in part** | Onboarding, Settings, Downloads, the send contract and the failure-copy catalogue. Its daily-flow sections are superseded |
| [SPEC.md](../SPEC.md) | **AUTHORITATIVE** | What the app must do: behaviour, data contracts, acceptance criteria R01–R39 |
| [DESIGN.md](DESIGN.md) | **AUTHORITATIVE** | What it should feel like: intent, tone, visual language, non-negotiables |
| [BEST-PRACTICES.md](BEST-PRACTICES.md) | **AUTHORITATIVE** | How work is done: modules, conventions, gates, evidence rules, commit rules |
| [branching.md](branching.md) | **AUTHORITATIVE** | Where work lands: feature branches → `dev` → merge train → `main` |
| [../README.md](../README.md) | **REFERENCE** | What the project is and how to get it running |

If two documents disagree, the higher row wins and the lower one is a bug.

## Documents in `docs/`

| File | What it is | Status | Read it if… |
|---|---|---|---|
| [sentence-mining-flow.md](sentence-mining-flow.md) | The 22 October contract, now **partially superseded**: still authoritative for onboarding, Settings, Downloads, the send contract, the secondary surfaces and the failure-copy catalogue; its Capture/Target/Review daily flow is retired | AUTHORITATIVE in part | you are building onboarding, Settings, Downloads, sending, or any failure state |
| [DESIGN.md](DESIGN.md) | Product vision and feel: the moment the app serves, principles, colour/type/motion, workflow non-negotiables | AUTHORITATIVE (interprets the contract) | you are judging whether a screen or string "fits" |
| [branching.md](branching.md) | The branch model: feature work lands on `dev`, `main` ships, promotion goes through a merge train | AUTHORITATIVE | you are about to commit, merge, branch, or publish |
| [BEST-PRACTICES.md](BEST-PRACTICES.md) | Engineering standards: module boundaries, Kotlin/Compose conventions, test policy, gate commands, commits, documentation rules, verification policy | AUTHORITATIVE | you are about to write code, tests, commits or docs |
| [android-instrumentation-tests.md](android-instrumentation-tests.md) | Runbook for `app/src/androidTest` on an API-37 x86_64 AVD: dedicated AVD/ports, explicit `adb -s` targeting, lock refresh | REFERENCE | you need platform guarantees (SQLite, WebP, `MediaPlayer`, bundled assets) proven |
| [toolchain-notes.md](toolchain-notes.md) | Verified AGP 9.4.1 / Gradle 9.7.1 stack, Material3 API tuple, Nix APK build results, warnings, and emulator caveats | REFERENCE | you are touching the build, dependencies, or emulator tooling |
| [first-run-resource-delivery.md](first-run-resource-delivery.md) | Asset inventory with exact sizes and SHA-256 per bundled/provisioned resource, the verified first-run installer contract, and the open licensing/delivery questions | REFERENCE | you are packaging a release, changing an asset, or auditing hashes and licences |
| [redesign-evidence.md](redesign-evidence.md) | Research behind three SPEC decisions: lossy WebP at Q75/1080 px with the measured sweep, the AJT pitch HTML contract and its fixtures, NHK index matching limits, furigana behaviour | EVIDENCE | you need to know *why* the image, pitch or reading contract is what it is |
| [testing-without-emulator.md](testing-without-emulator.md) | Proposal: run Compose behaviour and visual-golden tests on the JVM with Robolectric/Roborazzi, keep a thin device lane for camera, insets and AnkiDroid, and make the agent loop hermetic | PROPOSAL | you are tempted to boot an emulator to check a Compose screen |
| [redesign-evidence/acceptance-309cc39/report.md](redesign-evidence/acceptance-309cc39/report.md) | Device acceptance on `309cc39`: 45/45 instrumented, Capture and Type captured at 390×844, provider-gated card states recorded NOT RUN | EVIDENCE | you need to know what was actually observed on a device, and what was not |
| [material3-upgrade.md](material3-upgrade.md) | Resolved Material3 1.5.0-beta02 public API proof and toolchain rationale, with the 1.4.0 investigation retained as historical evidence | EVIDENCE | you are changing Compose/Material dependencies or using an "Expressive" API |
| [redesign-interview.md](redesign-interview.md) | The user's own statement of the workflow, quality boundary and refused directions, recorded before the SPEC rewrite; explicitly **not** a second specification | REFERENCE | you want the reasoning and the user's words behind DESIGN.md and SPEC §4 |
| [ui-review-20261022.md](ui-review-20261022.md) | The review behind the 22 October decisions: the build audited against the contract, the refined user story, the contradictions that let it drift, the rejected alternatives and the decision log | RECORD (adopted) | you need to know why the card flow, the inline selection, the torch or the 16:9 band are what they are |
| [card-flow-contract.md](card-flow-contract.md) | **The screen-and-copy contract**: Capture → Card as one surface, with exact composition, the single hint line, selection/ruby rules, loading, collapse order, the string delta and the capture geometry | **AUTHORITATIVE** | you are building, reviewing, or writing copy for any screen in the daily flow |
| [agent-flow.md](agent-flow.md) | Operating document for the loop that turns a bug reported from the phone into a release-signed APK installed from the phone: triage, issue templates, release workflow, signing setup, failure modes | REFERENCE | you are working on issue triage, release signing, or the GitHub workflows |
| [cleanup-worktrees.md](cleanup-worktrees.md) | Audit of the Kanban branch/worktree leftovers, the classification rule, the record of both applied prune passes (91 → 48 worktrees, 96 → 53 branches, ~100 GB reclaimed) and the concurrency hazard when a worker is live, with `scripts/prune-worktrees.sh` | REFERENCE | you are about to create, prune, or reuse a worktree |
| [ui-plan.md](ui-plan.md) | Short pointer: the plan it used to hold is in `archive/ui-plan.md` | SUPERSEDED | you followed an old link here |
| [v0.3-reference-template/](v0.3-reference-template/README.md) | Corrected Anki reference-template parser (`Recognition/front.html`, `parser-test.mjs`) that reads nested nasal/handakuten/devoiced pitch markup and accent numbers ≥ 10. Despite the name it is **not** a v0.3 snapshot: `misc/Sentence (JA)/Recognition/front.html` does not carry this fix | REFERENCE | you are validating SPEC §8 pitch rendering against a real template |
| [archive/](archive/README.md) | Everything point-in-time: superseded documents and dated evidence, each with a status banner | HISTORICAL / SUPERSEDED | you need the record of a decision, a release, or a verification run |
| [evidence/](evidence/README.md) | Where a release run's evidence is recorded while it is current, and the rule for moving it to `archive/` once superseded | REFERENCE | you are recording or citing an acceptance run |
| [archive/v0.3-final/](archive/v0.3-final/README.md), [archive/v0.3-candidate/](archive/v0.3-candidate/README.md) | Install and acceptance records for the v0.3 build (the candidate that shipped), kept in-repo because `releases/` is a local, untracked staging directory | HISTORICAL | you are auditing what v0.3 delivered |

## Documents outside `docs/`

| File | What it is | Status |
|---|---|---|
| [../SPEC.md](../SPEC.md) | The product contract: §1 quality contract, §2 platform, §3 scope, §4 workflow/UI, §5 linguistics, §6 audio, §7 images/translation/notes, §8 Anki output, §9 submission safety, §10 invalidation, §11 privacy, §12 build constraints, §13 delivery priorities, §14 acceptance, §15 delivery policy, §16 references | AUTHORITATIVE |
| [../AGENTS.md](../AGENTS.md) | Short root contract for agent sessions: commit identity, environment escalation, mockup comparison | AUTHORITATIVE |
| [../misc/Sentence (JA)/README.md](<../misc/Sentence (JA)/README.md>) | The reference Anki note type, field schema and presentation the app exports into | REFERENCE |
| [../datasets/README.md](../datasets/README.md) | Pinned upstream data, transformations, hashes and licences for the bundled assets | REFERENCE |
| [../mockup/expressive-flow/README.md](../mockup/expressive-flow/README.md) | The HTML design study: the origin of the palette, type scale and fixtures. Its flow, step tracker and preparation page are **superseded** by the approved contract; never shipped | HISTORICAL (palette still REFERENCE) | you need the visual origin or the decision history, not the current screen inventory |
| `../mockup/expressive-flow/REVIEW-PASS-2.md`, `P0-P3-HANDOFF.md`, `REMEDIATION.md` | Review passes that produced the rules now in DESIGN.md and SPEC §4 | HISTORICAL |
| [archive/evidence/v0.4/](archive/evidence/v0.4/README.md) | Install and acceptance record for the v0.4 build | HISTORICAL |

## Reading paths

**New to the repository.** `README.md` → `SPEC.md` §1–§4 → `card-flow-contract.md` →
`docs/evidence/prototype/capture.html` and `card.html` (what it looks like) → `DESIGN.md` →
`BEST-PRACTICES.md` §2, §5.
Then run `nix develop` and `./scripts/test-core.sh` before reading code.

**Implementing or reviewing UI.** `card-flow-contract.md` in full — it is the authority for
composition, states and copy IDs → `SPEC.md` §4 and the R20/R21 rows of §14 → `DESIGN.md` for the
reasoning → `ui/FlowTheme.kt` for the tokens already in code. `sentence-mining-flow.md` still governs
onboarding, Settings, Downloads, sending and every failure string. The HTML study in
`mockup/expressive-flow/` is decision history; do not use it to restore removed elements.

**Changing data, assets, or the build.** `BEST-PRACTICES.md` §5–§6 → `toolchain-notes.md` →
`first-run-resource-delivery.md` → `datasets/README.md`.

**Producing evidence for a release.** `SPEC.md` §14 → `BEST-PRACTICES.md` §9 →
`android-instrumentation-tests.md` and the emulator section of §5. Record the result under
`docs/evidence/<topic>-<date>/` with a PASS/FAIL/PARTIAL/NOT RUN verdict. When the next accepted
release supersedes it, move the directory to `archive/evidence/`; see
[evidence/README.md](evidence/README.md) for the retention rule.

## Adding or moving a document

The rules are in [BEST-PRACTICES.md §8](BEST-PRACTICES.md#8-documentation-rules). In short: one
fact, one home; cite `SPEC.md` instead of restating it; give every archived file a status banner;
and when a file moves, update every link to it in the same change — including from `mockup/`,
`releases/` and code comments.
