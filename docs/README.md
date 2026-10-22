# Documentation index

Read this page first. It lists every document an agent or maintainer is expected to consult, what
each one is for, and how far to trust it.

Three documents carry authority; the README is the entry point, and everything else is support,
evidence or history:

| Document | Status | Answers |
|---|---|---|
| [SPEC.md](../SPEC.md) | **AUTHORITATIVE** | What the app must do: behaviour, data contracts, acceptance criteria R01–R35 |
| [DESIGN.md](DESIGN.md) | **AUTHORITATIVE** | What it should feel like: intent, tone, visual language, non-negotiables |
| [BEST-PRACTICES.md](BEST-PRACTICES.md) | **AUTHORITATIVE** | How work is done: modules, conventions, gates, evidence rules, commit rules |
| [../README.md](../README.md) | **REFERENCE** | What the project is and how to get it running |

If two documents disagree, the higher row wins and the lower one is a bug.

## Documents in `docs/`

| File | What it is | Status | Read it if… |
|---|---|---|---|
| [DESIGN.md](DESIGN.md) | Product vision and feel: the moment the app serves, principles, colour/type/motion, workflow non-negotiables | AUTHORITATIVE | you are building, reviewing or judging any screen or string |
| [BEST-PRACTICES.md](BEST-PRACTICES.md) | Engineering standards: module boundaries, Kotlin/Compose conventions, test policy, gate commands, commits, documentation rules, verification policy | AUTHORITATIVE | you are about to write code, tests, commits or docs |
| [android-instrumentation-tests.md](android-instrumentation-tests.md) | Runbook for `app/src/androidTest` on an API-37 x86_64 AVD: dedicated AVD/ports, explicit `adb -s` targeting, lock refresh | REFERENCE | you need platform guarantees (SQLite, WebP, `MediaPlayer`, bundled assets) proven |
| [toolchain-notes.md](toolchain-notes.md) | Deferred toolchain work and its reasons: AGP 9.x for `compileSdk = 37`, Gradle lock coverage, aapt2 override, emulator caveats | REFERENCE | you are touching the build, dependencies, or emulator tooling |
| [first-run-resource-delivery.md](first-run-resource-delivery.md) | Asset inventory with exact sizes and SHA-256 per bundled/provisioned resource, the verified first-run installer contract, and the open licensing/delivery questions | REFERENCE | you are packaging a release, changing an asset, or auditing hashes and licences |
| [redesign-evidence.md](redesign-evidence.md) | Research behind three SPEC decisions: lossy WebP at Q75/1080 px with the measured sweep, the AJT pitch HTML contract and its fixtures, NHK index matching limits, furigana behaviour | EVIDENCE | you need to know *why* the image, pitch or reading contract is what it is |
| [material3-upgrade.md](material3-upgrade.md) | Why the project pins `material3` 1.4.0 (1.5.0-beta01 requires AGP 9.1.0), with the build/test output and the API-availability caveat | EVIDENCE | you are changing Compose/Material dependencies or using an "Expressive" API |
| [redesign-interview.md](redesign-interview.md) | The user's own statement of the workflow, quality boundary and refused directions, recorded before the SPEC rewrite; explicitly **not** a second specification | REFERENCE | you want the reasoning and the user's words behind DESIGN.md and SPEC §4 |
| [agent-flow.md](agent-flow.md) | Operating document for the loop that turns a bug reported from the phone into a release-signed APK installed from the phone: triage, issue templates, release workflow, signing setup, failure modes | REFERENCE | you are working on issue triage, release signing, or the GitHub workflows |
| [cleanup-worktrees.md](cleanup-worktrees.md) | Audit of the Kanban branch/worktree leftovers, the classification rule, and the record of the applied prune (91 → 55 worktrees, 96 → 61 branches, 94 GB reclaimed) with `scripts/prune-worktrees.sh` | REFERENCE | you are about to create, prune, or reuse a worktree |
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
| [../mockup/expressive-flow/README.md](../mockup/expressive-flow/README.md) | The HTML design study: flow, type/colour roles, fixtures. Reference for structure and identity, never shipped | REFERENCE |
| `../mockup/expressive-flow/REVIEW-PASS-2.md`, `P0-P3-HANDOFF.md`, `REMEDIATION.md` | Review passes that produced the rules now in DESIGN.md and SPEC §4 | HISTORICAL |
| [archive/evidence/v0.4/](archive/evidence/v0.4/README.md) | Install and acceptance record for the v0.4 build | HISTORICAL |

## Reading paths

**New to the repository.** `README.md` → `SPEC.md` §1–§4 → `DESIGN.md` → `BEST-PRACTICES.md` §2, §5.
Then run `nix develop` and `./scripts/test-core.sh` before reading code.

**Implementing or reviewing UI.** `DESIGN.md` in full → `SPEC.md` §4 and the R20/R21 rows of §14 →
`mockup/expressive-flow/README.md` for the reference composition → `ui/FlowTheme.kt` for the tokens
already in code.

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
