# Worktree and branch cleanup

Audit of the Kanban leftovers in this repository, the rule used to decide what is
safe to delete, and the removal plan. The repeatable tool is
[`scripts/prune-worktrees.sh`](../scripts/prune-worktrees.sh).

## Status

Audit, tooling and removal: **complete**, in two passes. Every number below is
measured, not projected. Re-running the tool now reports "Nothing to remove": that
is the idempotence check.

```bash
./scripts/prune-worktrees.sh                          # dry run against main
./scripts/prune-worktrees.sh --base dev                # what merged into dev
./scripts/prune-worktrees.sh --apply --base dev \
  --keep 'japanese-anki-app/t_3264cf90*' \
  --keep 'japanese-anki-app/t_132e540a*'               # aliases are labels, not work
```

A second pass ran after `main` was merged into `dev`, which is what makes
"merged into the integration branch" a different set from "merged into main":
a branch that landed on `dev` only becomes removable once `--base dev` asks the
question. That pass also cleared the five merged worktrees that the first pass
refused as dirty, after archiving their untracked content outside the repository.

## Before / after

Pass 1 = before → after the first prune (base `main`). Pass 2 = after the
`main → dev` merge, the five dirty worktrees and a live worker excluded.

| Measure | Before | After pass 1 | After pass 2 | Note |
| --- | --- | --- | --- | --- |
| Registered worktrees | 91 | 55 | **48** | 43 removed in total |
| Local branches | 96 | 61 | **53** | 43 removed in total |
| `du -sh .worktrees` | 226G | 132G | **127G** | 99G reclaimed |
| Filesystem used | 293G | 196G | **196G** | 97G reclaimed on the volume |
| `du -sh .git` | 190M | 184M | 184M | refs only; no `gc`, no history rewrite |

The remaining 126G is 48 live worktrees, each with its own Gradle/CMake build
tree. Their branches carry work that is not on the integration branch (see
below); their build directories are regenerable and can be removed separately
without touching a branch.

`.git` barely shrinks because deleting a branch only drops a ref; the objects stay
until a prune/gc, which is deliberate — this cleanup does not touch history.

### Concurrency: a worker ran during pass 2

Pass 2 overlapped a live Kanban worker (`t_d0cde8b0`, toolchain upgrade), and three
other worktrees appeared while it ran (`t_65e006b3`, `t_08553820`, `t_e5dff0ed`).
The tool only refuses worktrees that are *dirty*, and a worker that has just
committed has a clean tree — so a clean-but-live worktree is indistinguishable
from an abandoned one. Pass 2 therefore used `--keep` for the running task:

```bash
--keep 'japanese-anki-app/t_d0cde8b0*'
```

Before any future `--apply`, check `pgrep -af 'work kanban task'` and pass every
running task id to `--keep`. `t_08553820` and `t_e5dff0ed` were left alone because
their trees are dirty, which is the only reliable signal the tool has.

### What went wrong, and what changed because of it

The first `--apply` also deleted the two merge-train aliases,
`…/t_3264cf90-…` and `…/t_132e540a-…`, which the plan protected. Cause: the tool
protected a ref only while *its commit equalled `main`'s tip*. The commit that
landed this cleanup moved `main`, so the aliases instantly became ordinary
ancestors of `main` — merged, clean, and removable. No commits were lost (both
pointed into `main`'s history) and both refs were recreated at `6860951`, but the
protection rule was wrong: it protected an accident of timing rather than a name.

The script now also protects `backup/*` and accepts repeatable `--keep <glob>`.
Because an alias is only recognisable while it equals `main`, keeping one across a
`main` advance requires the explicit form:

```bash
./scripts/prune-worktrees.sh --apply \
  --keep 'japanese-anki-app/t_3264cf90*' --keep 'japanese-anki-app/t_132e540a*'
```

## How each branch was classified

1. `git merge-base --is-ancestor <branch> main` → **merged**, safe.
2. otherwise `git cherry main <branch>`:
   - any `+` line → at least one commit's patch is **not** on main;
   - no `+` line (and non-empty output) → every patch is already applied →
     **superseded**, safe. A single `+` is enough to keep the branch; `git
     cherry` alone is never treated as a verdict, the diff was inspected for the
     one branch this rule fired on (`t_0984c8f0`, see below).
3. everything else → **carries work absent from main**, kept and reported.

`git diff main...<branch>` was also read for every unmerged branch. It is not a
classification test on its own: the three-dot diff shows what the branch changed
since its merge base whether or not main already contains an equivalent, and the
two-dot diff is dominated by the ~1.47M lines of assets main gained after these
branches forked (46 of the 50 unmerged branches still carry the pre-asset tree). Net content
equality with main is therefore not available as a proof for anything except
patch-identical commits, which is why only one branch qualifies as superseded.

Worktrees were additionally screened with `git -C <path> status --porcelain`.
13 of 90 have local state; none of them were touched.

## Removed — 35 branches, 36 worktrees

34 branches whose tip was an ancestor of `main` (merged through the v0.3/v0.4
merge trains) and whose worktree was clean:

```
t_02b1be7a t_04784186 t_05478257 t_1a52fb6f t_1d52647b t_1eb7d549 t_2592aa66
t_2bad2b51 t_2ea914c4 t_2eb81c2e t_4c25c995 t_4d11b083 t_54c30ad2 t_7f577009
t_7f80ebda t_845e4675 t_8de16ee7 t_93c3dc8a t_9415927f t_95b8b37a t_9d995704
t_a2e36dc2 t_b2997680 t_c3e9ffba t_d8412a14 t_ddd50984 t_e5b6e218 t_e7982c2a
t_ebfb4dfa t_eefa407e t_f2cda05c t_f58843c6 t_f984c95c t_fff4b495
```

One superseded branch, `japanese-anki-app/t_0984c8f0-independently-verify-repair-integrate-an`:
its single commit `b97e9f7 build: bump versionCode to 3` is patch-identical to
`3a21d59` on main (`git cherry` reports `-`), and main has since moved the same
line to `versionCode = 4`. Nothing is lost.

One stale detached worktree, `.tmp-nix/prep-baseline`, holding `b9dabd9`
(2026-10-09, ancestor of main) in a clean tree — an old checkout of a commit main
already has.

After the worktree is gone the branch is deleted with `git branch -D`; every one
of these tips is reachable from `main`, so the commits survive.

## Rejected — removed without merging

`japanese-anki-app/t_bd3ce676-verify-and-fix-camera-capture-and-real-i` (2 commits,
tip `5535223`) implemented an *orientation-aware viewfinder, landscape shutter and
uncropped preview*. Pierre rejected it on 2026-10-22 as a mistake.

The distinction that matters, because the words are close:

- **Required** (`SPEC.md` §4): "orientation-aware capture" and "landscape captures
  and picked/shared landscape images are stored orientation-corrected and displayed
  correctly in the portrait composition", plus the rule that rotating to landscape,
  capturing and rotating back must preserve the photo and draft **without switching
  the interface to a landscape composition**.
- **Rejected** (this branch): making the camera surface itself follow the device —
  a landscape viewfinder and a relocated shutter. That breaks the contract's fixed
  full-width portrait viewfinder with a stable shutter row, and the interface is
  portrait-only by rule.

The required half was already met: `ImageDecoder` applies EXIF orientation when
decoding (`ImageMediaProcessor`, `JapaneseOcr`), and `MainActivity` restores the
draft from saved state after the activity is recreated. So the branch added a
forbidden behaviour on top of a requirement that was already satisfied.

Archived rather than silently dropped, in case a future agent rediscovers it:

- `/srv/hermes/archive/japanese-anki-pruned-20261022/t_bd3ce676-rejected-camera-rotation.patch`
- `…/t_bd3ce676-commits.txt` — the two commit SHAs

Do not merge it. If rotation is ever reported as broken, fix the *media and state*
requirements above; do not reintroduce a rotating viewfinder.

## The five dirty worktrees — archived, then removed in pass 2

The first pass refused all five because their trees were not clean. They were
merged into `main`, so the branches were redundant, but each held untracked
output that existed only on disk. Before removal that content was copied out of
the repository to `/srv/hermes/archive/japanese-anki-pruned-20261022/` (1.5 MB):

| Worktree | Local state | Disposition |
| --- | --- | --- |
| `t_5fc88e60` | untracked `icon-proposals/` | round-1 proposals archived (24 files), worktree removed |
| `t_83a89a4f` | modified `app/build.gradle.kts` | uncommitted ML Kit swap archived as a patch, worktree removed |
| `t_858f4a85` | untracked `icon-proposals-round3/` + zip | archived; the same files are tracked on `dev` as `design/icon-sources/round3/` |
| `t_8c55e074` | untracked `dist/` | 506 MB of superseded v0.2 APKs discarded as build output |
| `t_a7f67b81` | untracked `icon-proposals-round2/` + zip | round-2 proposals archived (38 files), worktree removed |

The ML Kit patch is kept as evidence, not as a proposal: it switches from the
bundled `com.google.mlkit:text-recognition-japanese` to the Google-managed
`play-services-mlkit-text-recognition-japanese`, which `SPEC.md` R35 forbids.

## Protected — never candidates

`main`, `dev` (`origin/dev`, 6 commits ahead of main), `backup/*`, and all
remote-tracking refs. The two local merge-train aliases
(`…/t_3264cf90-…`, `…/t_132e540a-…`) sit on `6860951` — main's tip before this
cleanup — and are kept by `--keep`, not by the on-main-tip rule (see above). The
script treats *any* branch whose commit equals main's tip as an alias, so a future
merge train is protected without editing the script, but only until `main` moves.

## Branches carrying work absent from the integration branch (kept — 44)

None of these are provably merged into `dev` or provably superseded, so all 44 stay.
Most are the v0.2 checklist plan that the v0.3/v0.4 redesign replaced; keeping them
costs a ref and a branch label, and deleting one would cost a fix if the judgement
is ever wrong. `t_bd3ce676` was the one branch judged and rejected — see above.
"Conflicts" is the conflict count from `git merge-tree --write-tree main <branch>`
— a high count means the branch no longer applies to the current tree.

### v0.2 checklist plan (18 branches, base 2026-10-14, superseded design)

Main was redesigned into the v0.3/v0.4 Input/Target/Review flow (`0c9e7e6`,
2026-10-21) and the v0.2 screens no longer exist in the same shape. Every branch
below conflicts on 3–29 files. Judgement: stale work from a superseded plan; the
feature area exists in main under the new architecture.

| Branch | Commits | Conflicts | Judgement |
| --- | --- | --- | --- |
| `t_79a42f0b` v0.2-1 pin toolchain | 3 | 3 | Stale — main pins the toolchain through `flake.nix` and `docs/toolchain-notes.md`. |
| `t_88ab315a` v0.2-2 offline assets | 2 | 4 | Stale — `dictionary.sqlite.gz`, `nhk-2016`, UniDic catalog all ship in main. |
| `t_88e6a4a5` v0.2-3 capture/OCR | 2 | 5 | Stale — `JapaneseOcr.kt` / `ui/CameraCapture.kt` came in through later work. |
| `t_5a089018` v0.2-4 tokenization | 4 | 5 | Stale — `TargetSpan.kt` + `TokenGrouping.kt` on main. |
| `t_264349ff` v0.2-5 dictionary ambiguity | 5 | 5 | Stale — `OfflineDictionary.kt` + `OcrCandidateSelection.kt` on main. |
| `t_8939b98e` v0.2-7 pitch | 6 | 7 | Stale — `AjtPitch*`, `PitchPatternDiagram.kt` on main. |
| `t_10338bf0` v0.2-8 review hub | 18 | 26 | Stale — Review hub replaced by `ui/ReviewCard.kt` + `ReviewSentencePresentation.kt`; also dirty (`SPEC.md`, untracked evidence). |
| `t_a8f64981` v0.2-9 review fields | 8 | 4 | Stale — review editors live in `ui/ReviewCard.kt` on main. |
| `t_5394ffba` v0.2-10 translation | 2 | 3 | Stale — `TranslationService.kt` on main. |
| `t_15e26b6c` v0.2-11 audio | 3 | 7 | Stale — `AudioGeneration.kt`, `AudioProvider.kt`, `ReviewAudioGenerationCoordinator.kt` on main. |
| `t_c2ac292f` v0.2-12 AVIF media | 6 | 7 | Stale — `ImageMediaProcessor.kt` on main. |
| `t_c3520c39` v0.2-13 AnkiDroid contract | 4 | 3 | Stale — `AnkiDroidGateway.kt` + `AnkiFieldContract.kt` on main. |
| `t_bb7b6ecf` v0.2-14 draft state | 3 | 3 | Stale — `ManualNoteDraft.kt` + settings autosave on main. |
| `t_e404a7b8` v0.2-15 settings | 3 | 4 | Stale — native `ui/SettingsScreen.kt` landed on main in `06beb3d`. |
| `t_2c11f603` v0.2-16 submissions | 5 | 7 | Stale — `SubmissionRecovery.kt` on main. |
| `t_6a31d19a` v0.2-17 privacy | 23 | 28 | Stale — privacy posture documented in `docs/archive/live-testing-remediation.md`. |
| `t_3071059d` v0.2-18 acceptance | 28 | 29 | Stale — v0.2 device evidence superseded by `docs/archive/acceptance-test-matrix.md` + `docs/archive/evidence/`. |
| `t_5f107af0` integrate units 1-17 | 24 | 29 | Stale — the integration it performs was itself replaced by main's v0.3 history. |

### v0.2 release/verification bundles (8 branches, base 2026-10-14)

Mostly evidence: 100–160 files, 20k+ insertions, 29 conflicts each, because they
still carry the pre-asset tree. Judgement: stale release evidence for a version
that already shipped; keep only as a record, never merged again.

| Branch | Commits | Conflicts | Judgement |
| --- | --- | --- | --- |
| `t_5a0ea653` Oct-16 release gates | 43 | 29 | Stale — release gates re-run for v0.3/v0.4; evidence under `docs/archive/evidence/`. |
| `t_93909531` API/ABI runtime cells | 44 | 29 | Stale — ABI splits shipped in `8fa4682`; runtime matrix harness superseded. |
| `t_c193f786` v0.2 APK delivery | 30 | 29 | Stale — v0.2 artifacts; only v0.3/v0.4 builds are shipped, in GitHub Releases. |
| `t_f2e01b5e` mockup comparison | 29 | 29 | Stale — mockup adjudication belongs to the v0.2 visual system that was replaced. |
| `t_10bc8d65` mockup verification | 43 | 29 | Stale and **dirty** (30 modified sources) — the on-disk edits are uncommitted leftovers. |
| `t_804506ce` visual-state fixtures | 43 | 29 | Stale and **dirty** (12 modified + untracked debug sources). |
| `t_b717287c` A35 privacy | 36 | 29 | Stale — privacy retest recorded in `docs/toolchain-notes.md`. |
| `t_f9737519` Oct-16 reconcile | 42 | 29 | Stale — reconciliation completed on main's own line. |

### Privacy / Review-route side branches (`wt/*`, 8 branches, base 2026-10-14)

These are parallel-worker copies of the v0.2 privacy and Review-route audits.
Same story: 27–28 conflicts, evidence-heavy, superseded by the shipped tree.

| Branch | Commits | Conflicts | Judgement |
| --- | --- | --- | --- |
| `wt/t_0b1ace4a` | 34 | 28 | Stale — duplicate of the unit-17 privacy audit. |
| `wt/t_1d715073` | 24 | 28 | Stale — SDK diagnostics inventory, folded into main's privacy docs. |
| `wt/t_200793a1` | 24 | 28 | Stale — same privacy audit, different worker. |
| `wt/t_3f2360f3` | 33 | 28 | Stale — combined-APK privacy results, superseded evidence set. |
| `wt/t_a576650b` | 19 | 27 | Stale — Review-route smoke evidence for the replaced hub. |
| `wt/t_c69f1059` | 23 | 27 | Stale, **dirty** (2 untracked evidence txt) — 196 added files belong to the old hub. |
| `wt/t_cbbd2877` | 3 | 0 | Stale but harmless — audit matrix for the replaced hub; merges cleanly, no value to main. |
| `wt/t_bb20a20c` | 1 | 2 | Stale — build/emulator baseline doc already covered by `docs/archive/emulator-readiness.md`. |

### Recent branches — possible unique work, human decision needed

Base 2026-10-20..22, 0–13 conflicts, small diffs. These are the only unmerged
branches that could plausibly still matter, and two of them are already contained
in `dev`.

| Branch | Commits | Conflicts | Judgement |
| --- | --- | --- | --- |
| `t_3331487e` launcher icon | 1 | 0 | Redundant, not unique: merged into `dev` (`35f98ab`), which is pushed as `origin/dev`. Delete when `dev` lands on main. |
| `t_c203859a` Review audio fix | 1 | 0 | Same: merged into `dev` (`8a36045`). Nothing lost by deleting it, but it is not merged to main, so it was left alone. |
| `t_23c28d5f` ML Kit privacy assessment | 1 | 0 | **Possibly needed** — 64-line assessment doc, no equivalent on main, applies cleanly. Salvage candidate. |
| `t_24143309` automatic ruby | 2 | 0 | **Possibly needed** — automatic ruby + unresolved-reading serialization; main has ruby rendering in `ReviewCard.kt` but not this serialization path. |
| `t_430ab378` native Settings screen | 2 | 4 | Superseded by `06beb3d` on main; the 24 evidence files are the only unique part. Stale. |
| `t_647f8547` expressive flow | 8 | 13 | Stale — main implemented the same flow in `0c9e7e6`; this branch is v0.4 evidence plus tweaks that no longer apply. |
| `t_6ef76557` reading correction fix | 1 | 5 | **Needs a look** — targeted reading correction / unresolved-vocabulary reset; main has `SentenceReadings.kt` but I could not prove the fix is there. |
| `t_c8c8b1b1` reading correction evidence | 1 | 0 | Duplicate of the above as documentation (11 files), applies cleanly. Salvage if the fix is wanted. |
| `t_e58cbdc9` disabled send button | 4 | 0 | **Possibly needed** — main gates actions via `actionsEnabled`, but this branch's "look disabled" state is not provably present. Applies cleanly. |
| `t_bd3ce676` camera orientation | 2 | 2 | **Rejected by Pierre (2026-10-22)** — a landscape viewfinder contradicts the portrait-only composition in `SPEC.md` §4. Removal recorded above. |
| `t_b5634e0b` camera OCR run | 3 | 2 | Evidence bundle around `t_bd3ce676`, which is rejected; no independent value. |
| `t_1474969d` disable CCT telemetry | 35 | 29 | Stale — main no longer uses Custom Tabs anywhere in `app/src`, so the component this hardens does not exist. |
| `wt/t_0e0e1a8d` v0.2 checklist map | 1 | 0 | Stale but **dirty** (`docs/v0.2-implementation-checklist.md` modified) — the doc is absent from main; salvage candidate if the mapping is still wanted. |
| `wt/t_fd0fc5ca` unit-8 manifest | 1 | 0 | Stale — 272-line integration manifest for the replaced review hub. |

## Policy

A worktree may be pruned only when **all** of these hold:

1. It is not the main checkout.
2. `git -C <path> status --porcelain` is empty — no staged, unstaged or
   untracked-but-not-ignored file. Ignored build output is not a reason to keep
   it and is discarded by `git worktree remove --force`.
3. Its branch (or detached HEAD, if detached) is an ancestor of `main`, i.e. the
   commits are reachable without it. Detached worktrees additionally need a clean
   tree; a detached HEAD outside `main` is reported, never removed.
4. The branch is not `main`, `dev`, `backup/pre-v0.3-integration`, an alias
   sitting on main's tip, or a remote-tracking ref.

A branch may be deleted only when it is **provably** merged (ancestor of main) or
**provably** superseded (`git cherry main <branch>` shows no `+` line — every
patch already applied) **and** its worktree has been removed or does not exist.

Never: `git gc`, history rewrite, force-push, `git branch -D` on an unproven
branch, or removing a worktree with local state. Anything that cannot be proven
stays and is listed in this document instead.
