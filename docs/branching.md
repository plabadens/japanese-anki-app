# Branch model

Three kinds of branch, with one landing rule each.

## `main` — what ships

Every commit on `main` has been through the merge train: parents verified, components explicitly merged, affected gates run on the merged tree. When a change is published, it is tagged here.

Never force-push `main`.

## `dev` — integration

Feature work lands here first. `dev` is where several finished branches are combined and the merged tree is exercised as a whole, without committing to a release.

- CI builds every push to `dev` (`Build APK`), so integration breakage is visible immediately rather than at release time.
- Breaking `dev` is a nuisance; breaking `main` is a delivery failure. That is the difference the branch exists for.
- Never force-push `dev`.

## Feature branches

Per task: `japanese-anki-app/<task-id>-<slug>`, cut from `dev` (not from `main`) unless the task is explicitly a hotfix against what shipped.

- One task, one branch, one worktree.
- **These branches stay local.** They are never pushed — not for backup, not to get CI, not for review. Publishing a task branch is how the remote fills with dead refs that nobody dares delete.
- Land by merging into `dev` locally, then pushing `dev`. Only `main`, `dev` and deliberate long-lived release branches belong on `origin`.
- Do not merge straight into `main`; a feature branch that skips `dev` has skipped the integration check that branch exists to provide.

## Promotion: `dev` → `main`

Through a merge train, not by hand:

1. Confirm `dev` is green in CI.
2. Merge `dev` into a merge-train branch, run the merged-tree gates on it.
3. Land on `main`, push without force, and publish the release from `main` if the change is user-facing.

## Binaries

Built APKs and resource bundles are never committed. They live in `releases/` (gitignored, local) and on the GitHub release for the tag. Nothing over 100 MB can be tracked at all, so a "publish everything" instruction means release assets, never commits.
