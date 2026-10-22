# Issue → automated fix → signed APK

Operating document for the loop that turns a bug reported from the phone into a
release-signed APK Pierre can install from the phone, with no laptop involved.

Applies to: `plabadens/japanese-anki-app` (public, default branch `main`), applicationId
`dev.pierre.japaneseanki`, `minSdk`/`targetSdk` 37, `versionName` `0.4`, `versionCode` 4,
ABI-split `arm64-v8a` (phone) and `x86_64` (emulator), no universal APK.

Files that implement it:

| File | Role |
|---|---|
| `.github/ISSUE_TEMPLATE/bug.yml` | phone-friendly bug form: build, version, device, repro, screenshot |
| `.github/ISSUE_TEMPLATE/feature-request.yml` | feature requests, deliberately outside the automated loop |
| `.github/ISSUE_TEMPLATE/config.yml` | disables blank issues, links install/build docs |
| `.github/workflows/build-apk.yml` | existing debug CI gate (PR + `main`), unchanged |
| `.github/workflows/release-apk.yml` | tag push → release-signed `arm64-v8a` APK → GitHub Release |
| `.github/scripts/ci-build-release-apk.sh` | gradle entry point for the release build |
| `.github/scripts/verify-release-apk.sh` | signature, ABI, applicationId and version assertions |
| `app/build.gradle.kts` | `releaseEnv` signingConfig, environment-driven, unsigned fallback |

---

## 1. Who triages: local Hermes agent, not a bot account, not a GitHub App

**Recommendation: keep triage in the local Hermes agent that already runs kanban dispatch and
already has `gh` authenticated as `plabadens`.** Do not create a bot account. Do not register a
GitHub App yet.

Why this is the fit for this stack, in order of weight:

1. **No new credentials.** `gh` on this host is already authenticated as the owner with admin
   rights on the repo, so the agent can label, comment, branch, push a tag and create a release
   with the token it already has.
2. **The build gate already lives here.** The release build needs the flake-pinned SDK 37
   closure, the MeCab source and a warm Gradle cache. The host has all three; a new agent
   identity buys nothing on that front. GitHub runners can build it too (that is what
   `release-apk.yml` does), but building is the cheap, already-solved half — deciding *what* to
   fix is the part that needs the agent.
3. **Kanban + worktrees is the concurrency model already in place.** A worker profile per card,
   each in its own `git worktree`, is what stops two fixes from fighting over
   `app/build.gradle.kts`. A bot account would not add any of that.
4. **The commit identity is fixed by policy anyway.** `AGENTS.md` requires every commit to be
   authored and committed by `Pierre Labadens <labadens.pierre@gmail.com>` with no agent
   co-author trailer. A bot account therefore cannot own the commits; its only unique
   contribution would be issue-comment authorship, which a one-line signature in the comment
   ("posted by the local Hermes agent") covers for free.

Tradeoffs:

| | Local Hermes agent (recommended) | Separate bot account | GitHub App |
|---|---|---|---|
| New credentials | none | PAT for the bot + 2FA + org/repo invite | app private key + installation id + token-exchange step |
| Permissions held | owner/admin | whatever you grant, usually more than needed | fine-grained, short-lived per-run tokens |
| Can act while host is off | no | no (still needs a runner) | yes, if you host a webhook receiver — i.e. you rebuild the agent in the cloud |
| Attribution in audit log | indistinguishable from Pierre | distinct actor on comments/labels; commits must still be Pierre's | distinct actor, `[bot]` suffix |
| Failure surface | one host, one token | token rotation, seat management, "who is this person" in every UI | app registration, key storage, installation drift, JWT expiry bugs |
| Setup cost | zero | ~30 min + ongoing hygiene | ~1–2 h + a service to run |

**When a bot account *is* worth the overhead:** more than one human or more than one agent
writes to the repo and you need to tell their actions apart in the audit log; the token the
agent uses must be narrower than the owner's (e.g. a maintainer-scoped PAT because the owner
token also holds admin on unrelated repos); the repo moves under an organisation where a
personal account cannot be a member without a paid seat; or a compliance rule requires that
automation never act as a human.

**When it is not:** one maintainer, one agent, one repo, the host is on during the day, and the
real risk is secret sprawl — every extra identity is another PAT to leak. That is the current
situation. Stay with the local agent.

If you later need triage while the host is off, the right move is **not** a bot account with a
long-lived PAT; it is a GitHub App (or plain Actions with the built-in `GITHUB_TOKEN`), because
both issue short-lived, narrowly scoped credentials. A bot account is the option that adds a
permanent credential without adding a permanent capability.

Consequence to accept: issue comments and labels from the agent look exactly like Pierre's. The
mitigation is procedural, not technical — put the agent's claim in the comment body, and let the
`state:*` labels and the release notes carry the history.

---

## 2. The loop, step by step

```
phone (Pierre)                    host (Hermes agent)                    GitHub
──────────────                    ───────────────────                    ──────
bug.yml  ──submit──▶  issue #N [bug]
                          │
                          ├─ cron poll (15 min) ─▶ triage: read, reproduce, label
                          │                        state:triaged | state:needs-info
                          │
                          ├─ kanban card ─▶ worker profile + git worktree
                          │                   fix + versionCode/versionName bump
                          │                   PR ──▶ build-apk.yml (debug gate)
                          │                   merge to main          state:fixing
                          │
                          └─ tag v0.5-issN ──push──▶ release-apk.yml
                                                     signed arm64 APK + apksigner verify
                                                     GitHub Release + issue comment
                                                                          state:fix-ready
phone: tap release link ─▶ install in place ─▶ confirm ─▶ state:verified ─▶ close
```

### 2.1 Report (30 seconds, on the phone)

Issues → New issue → **Bug report**. The form asks only for things that change the fix:
version dropdown (0.4 / 0.3 / newer / unknown), install channel (release `arm64-v8a`, CI debug
`arm64-v8a`, emulator `x86_64`, local build), device model, Android version, what happened,
repro steps, expected, screenshot upload, optional crash logcat, and three one-tap checkboxes
(regression / crash / single-input-specific).

The install-channel field exists because it decides whether an update will install at all: a
debug-signed APK and a release-signed APK cannot replace each other. The form sets only the
label that exists today (`bug`); the `state:*` labels are applied by triage.

### 2.2 Labels as the state machine

These labels do not exist in the repo yet — create them once (§5, step 5). Until they exist the
workflow still runs; it just skips the label edits (the release workflow tolerates a missing
label instead of failing the publish).

| Label | Meaning | Set by | Next state |
|---|---|---|---|
| `bug`, `enhancement` | type, from the template | template | — |
| `state:needs-info` | cannot reproduce without more from Pierre | triage agent | back to `state:triaged` when answered |
| `state:triaged` | reproduced or root-caused; queued for a fix | triage agent | `state:fixing` |
| `state:fixing` | a worker owns it; this label is the lock against a second worker | dispatch | `state:fix-ready` |
| `state:fix-ready` | signed APK published and linked in the thread | `release-apk.yml` | `state:verified` |
| `state:verified` | Pierre confirmed on the phone | Pierre | issue closed |
| `state:wontfix` | deliberately not fixed; reason in a comment | Pierre | closed |

### 2.3 Triage (automated, every 15 minutes)

The poller is a Hermes cron job on this host — the agent already has `gh`, so this is a shell
call, not a new service:

```
gh issue list -R plabadens/japanese-anki-app --state open --label bug \
  --search '-label:state:triaged -label:state:fixing -label:state:fix-ready -label:state:wontfix' \
  --json number,title,body,labels,createdAt --limit 20
```

Create the job once (§5, step 6). Per issue the agent must do, in order:

1. Read the body. If the version, install channel or repro steps are missing and cannot be
   inferred → comment asking one specific question, add `state:needs-info`. Do not guess a fix.
2. Try to reproduce on the emulator with the `x86_64` debug APK:
   `nix run .#emulator-wrapper -- session app/build/outputs/apk/debug/app-x86_64-debug.apk .tmp-nix/screenshots/issue-N`.
   Device-only or `arm64-v8a`-only defects cannot be reproduced there: say so in the thread and
   work from the screenshot, the logcat and the code instead of claiming a repro.
3. Decide and record: fix, wontfix, or needs-info. A decision without a comment is a bug in the
   loop — the thread is the only audit trail, since the agent's identity is the owner's.

### 2.4 Fix (kanban worker, one worktree per card)

- Card carries: issue number, repro, expected behaviour, acceptance check.
- Worker branch `fix/issue-N`, its own worktree under `.worktrees/` (already gitignored).
- The worker **must** bump `versionCode` by exactly 1 and bump `versionName` to `0.<n>` in the
  same commit series; the tag is derived from `versionName`. An unbumped `versionCode` is the
  single most common cause of a phone refusing the download.
- Acceptance: `nix develop --command ./scripts/ci-build-apk.sh` (core tests + unit tests +
  debug build) passes, and a smoke run in the emulator for UI changes.
- PR → `build-apk.yml` runs the same gate → merge to `main`. No cherry-picks: the tag is cut
  from `main`, so a fix that is not on `main` is not in the APK.

### 2.5 Release (tag push, no human)

```
git tag v0.5-iss12
git push origin v0.5-iss12
```

`release-apk.yml` then: reads `versionName`/`versionCode` from `app/build.gradle.kts`, requires
the signing secrets (fails fast and loudly rather than publishing an unsigned APK), decodes the
keystore into `$RUNNER_TEMP/signing/release.jks` (mode 0700), builds inside the same Nix
toolchain the debug workflow uses (`ci-build-release-apk.sh`), verifies with `apksigner`,
`aapt2`, the ABI checker and an optional certificate-digest pin, publishes the GitHub Release
with `japanese-anki-<versionName>-arm64-v8a-release.apk` + `SHA256SUMS.txt` +
`apksigner-verify.txt`, comments the direct download link on the issue when the tag ends in
`-issN`, and keeps a 14-day workflow artifact as a fallback.

Tag rules:

- `v<versionName>` → normal release, marked **latest** (`v0.5` for `versionName = "0.5"`).
- `v<versionName>-iss<N>` → same-version hotfix for issue N, **not** marked latest.
- Tags are immutable in practice; do not re-point one. Re-running the workflow for an existing
  tag uploads with `--clobber` and rewrites the notes, which is the intended recovery path.

Deliberate omission: the release carries **no dictionary asset**. The app pins the 426 MiB
`unidic-cwj-3.1.1-runtime.zip` (446,602,958 bytes) to the `v0.4` tag in
`UniDicResourceCatalog.kt`, so per-fix releases stay small and first-run provisioning keeps
working. Only change that URL when the pinned dataset itself changes.

### 2.6 Install and confirm

On the phone: tap the comment link → Chrome downloads → open the file → "Update". The package
name and signing certificate match the installed release build, and `versionCode` is higher, so
Android replaces it in place and keeps app data. Chrome needs the "install unknown apps"
permission once.

Then Pierre taps the issue, confirms, applies `state:verified`; the agent closes it. If he does
not confirm within a day, the agent comments once and leaves it open — never close an issue on
the basis of a successful build alone.

### 2.7 Human work during the day, honestly counted

| Moment | Work |
|---|---|
| filing the bug | ~30 s on the phone |
| triage, fix, PR, merge, tag, release | 0, unless the agent needs one answer |
| installing | ~15 s |
| confirming | ~5 s |

The one remaining human decision is the merge into `main`. It can be automated (the agent has
push rights) but should not be: with a single account there is no independent review, so leaving
one deliberate gate is the only place a wrong fix stops.

---

## 3. Signing: a reusable key as repository secrets is the right call here

**Verdict: yes — one long-lived release key, stored as four repository secrets, with an
environment guard on the release job. Not a per-build key, not a debug key, not a keystore in
the repo.** The alternative designs are worse for a sideloaded app:

- A fresh key per release does not work: Android refuses to update an installed package when
  the signature changes (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Every release would be a
  fresh install with data loss.
- A committed keystore is a permanent, unrecoverable leak: anyone can sign a malicious update
  that installs silently over Pierre's app, and the key cannot be rotated without the same
  data-loss problem. This is why the file and its base64 form are forbidden in git even though
  the app is public and the certificate is public.
- Play App Signing (the managed alternative) does not apply: this app is sideloaded from GitHub
  Releases, not distributed through Play.

### 3.1 Mechanics, exactly

Run the `gh` commands below from the repository checkout, or add
`-R plabadens/japanese-anki-app` to each one.

```bash
# one time, on a machine you control
keytool -genkeypair -v \
  -keystore japanese-anki-release.jks -storetype PKCS12 \
  -alias japanese-anki -keyalg RSA -keysize 4096 -validity 10950

base64 -w0 japanese-anki-release.jks > japanese-anki-release.jks.b64
gh secret set ANDROID_KEYSTORE_BASE64          < japanese-anki-release.jks.b64
read -rsp 'store password: ' PW && printf '%s' "$PW" | gh secret set ANDROID_KEYSTORE_PASSWORD
printf '%s' 'japanese-anki' | gh secret set ANDROID_KEY_ALIAS
read -rsp 'key password: ' KP && printf '%s' "$KP" | gh secret set ANDROID_KEY_PASSWORD
shred -u japanese-anki-release.jks.b64 2>/dev/null || rm -f japanese-anki-release.jks.b64
```

Never put a secret in `argv` — `gh secret set` reads stdin, which is why every value above is
piped in (the alias is not sensitive, but one habit is easier to keep than two).

At run time the workflow reassembles the file (`printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64
--decode > "$RUNNER_TEMP/signing/release.jks"`, directory mode 0700, `umask 077`) and passes the
path, store password, alias and key password to Gradle as `JAPANESE_ANKI_RELEASE_KEYSTORE`,
`..._KEYSTORE_PASSWORD`, `..._KEY_ALIAS`, `..._KEY_PASSWORD`. `app/build.gradle.kts` creates the
`releaseEnv` signingConfig only when all of them are present **and** the file exists; otherwise
`assembleRelease` produces the same unsigned APK that `nix build .#apk` and local builds already
produce, and the workflow's verifier refuses to publish it. `KEY_PASSWORD` falls back to the
store password for PKCS12 keystores.

The certificate is public: `ANDROID_RELEASE_CERT_SHA256` is a repository **variable** (not a
secret) and pins the signer digest so a wrong key fails the build instead of shipping.

### 3.2 Why the same identity is mandatory

Android identifies an app by `(applicationId, signing certificate)`. Installing an update means:
same package, signature verified against the installed one, and `versionCode` strictly greater.
Change any of the three and the installer stops with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (key)
or `INSTALL_FAILED_VERSION_DOWNGRADE` (versionCode). There is no way to migrate an install to a
new key without uninstalling, which is why "rotate the signing key" is not routine maintenance
here — it is an incident.

### 3.3 Blast radius, and what must never be committed

Anyone who can read the repository secrets — repo admins, and any workflow on the default
branch that gets edited to print them — can sign an APK that the phone installs as an update.
Practical containment:

- Never commit: `*.jks`, `*.keystore`, `*.b64`, the base64 string, passwords in any file
  (`gradle.properties`, `local.properties`, `.env`, scripts, docs), or screenshots of
  `keytool`/secret output. `.gitignore` excludes `*.apk`, `*.aab`, `*.jks`, `*.keystore`,
  `*.bks`, `*.b64`, `keystore.properties` and `.env`; that is a guard against accidents, not a
  substitute for never writing them down.
- Never use `pull_request_target` (or any workflow that exposes secrets) alongside a checkout of
  PR code: a fork PR could exfiltrate the key. `release-apk.yml` runs on `push` of a tag only.
- Protect the tag/release path: Settings → Environments → create `release` with required
  reviewers, and attach it to the job, or at minimum enable "Require approval for all outside
  collaborators" under Actions. Without a second maintainer this is a speed bump, not a wall —
  say so rather than pretending otherwise.
- Rotation is a last resort: a new key means every installed copy must be uninstalled and
  reinstalled (AnkiDroid notes survive because they live in AnkiDroid; app-side settings, caches
  and any queued cards do not). Therefore: one key, offline backup in two places (password
  manager + printed copy in a safe), 30-year validity, rotate only on suspected compromise, and
  when you do rotate, keep the old key's release published so users can migrate deliberately.

### 3.4 Debug-signed CI artifacts vs a release-signed installable build

| | `build-apk.yml` artifacts | `release-apk.yml` release asset |
|---|---|---|
| Trigger | PR, push to `main`, manual | version tag |
| Variant | `debug` (debuggable, no minify) | `release` (not debuggable) |
| Signer | `sharedDebug` key when `JAPANESE_ANKI_DEBUG_KEYSTORE` is set, otherwise a throwaway key per `ANDROID_USER_HOME` | the stable release key from secrets |
| Purpose | verification on a machine, emulator runs | installation on the phone, in-place updates |
| Lifetime | 14-day workflow artifact | permanent GitHub Release |
| Can update a release install | no | yes |

The distinguishing property is the signature, not the build type: a debug APK and a release APK
of the same app cannot replace each other. That is why the bug form asks which channel the APK
came from.

---

## 4. Repository facts, verified from this host

`gh api repos/plabadens/japanese-anki-app` (2026-10-22):

| Field | Value |
|---|---|
| full name | `plabadens/japanese-anki-app` |
| private | `false` (public) |
| default_branch | `main` |
| has_issues | `true` — the loop can use issues |
| has_discussions | `false` |
| has_projects | `true` |
| has_wiki | `true` |
| has_downloads | `false` (legacy flag; Releases work regardless) |
| allow_auto_merge | `false` — merging is manual by policy, fine |
| delete_branch_on_merge | `false` |
| token permissions on the repo | `admin`, `maintain`, `push`, `triage`, `pull` all `true` |
| existing labels | `accessibility`, `bug`, `documentation`, `duplicate`, `enhancement`, `good first issue`, `help wanted`, `invalid`, `question`, `wontfix` — no `state:*` labels yet |
| releases | `v0.4` — "Japanese Anki v0.4 — debug artifacts", latest, published 2026-10-22T08:12:41Z |

**Not verifiable from this host:** `GET /repos/.../actions/permissions`,
`.../actions/permissions/workflow` and `.../actions/secrets` all return
`403 Resource not accessible by personal access token` with the current `gh` credential. So I
cannot confirm from the API that Actions is enabled, what the default `GITHUB_TOKEN` permissions
are, or which secrets already exist. Indirect evidence that Actions works: the `v0.4` release
exists and `build-apk.yml` is committed and was exercised. Confirm the rest in
Settings → Actions → General, and `gh secret list -R plabadens/japanese-anki-app` with a token
that can read them. The release job sets its own `permissions: contents: write, issues: write`,
so repo-level default token permissions do not affect it.

---

## 5. One-time setup Pierre must do

1. **Create the release key** (§3.1) and store the offline backups. Do this once; it is the
   identity of every future update.
2. **Set the four secrets**: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (§3.1 commands).
3. **Pin the certificate** (optional but recommended, catches a wrong key before it ships):

   ```
   keytool -list -v -keystore japanese-anki-release.jks -alias japanese-anki \
     | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f' \
     | xargs -I{} gh variable set ANDROID_RELEASE_CERT_SHA256 --body '{}'
   ```

4. **Confirm the signing guard is in place.** The root `.gitignore` already excludes `*.jks`,
   `*.keystore`, `*.bks`, `*.b64`, `keystore.properties` and `.env`. Verify with
   `git check-ignore -v japanese-anki-release.jks`; it must report a match.
5. **Create the state labels** (idempotent one-liners, `--force` updates an existing label;
   add `-R plabadens/japanese-anki-app` if you are not in the checkout):

   ```
   gh label create state:needs-info --color FBCA04 --description "Waiting on the reporter" --force
   gh label create state:triaged    --color 0E8A16 --description "Root-caused, queued for a fix" --force
   gh label create state:fixing     --color 1D76DB --description "A worker owns this fix" --force
   gh label create state:fix-ready  --color 5319E7 --description "Signed APK published and linked" --force
   gh label create state:verified   --color 006B75 --description "Confirmed on the phone" --force
   gh label create state:wontfix    --color B60205 --description "Deliberately not fixed" --force
   ```

6. **Schedule the triage poll** (on this host; adjust `--deliver` to your channel):

   ```
   hermes cron create "15m" \
     "Triage new bug reports in /srv/hermes/workspace/japanese-anki-app: run gh issue list \
   -R plabadens/japanese-anki-app --state open --label bug and handle any issue that has none of \
   state:triaged/state:fixing/state:fix-ready/state:wontfix. Follow docs/agent-flow.md section 2.3. \
   Comment your decision, apply the state label, and create a kanban card for real fixes. \
   Do not close issues." \
     --name japanese-anki-triage --workdir /srv/hermes/workspace/japanese-anki-app
   ```

7. **Smoke-test the release path once** before relying on it: bump `versionCode`/`versionName`,
   tag `v0.5-iss1` on a scratch issue, watch the run, install the APK over the existing release
   build. If it installs without uninstalling, the key material is correct.

8. **Optional hardening**: `release` environment with required reviewers; branch protection on
   `main` requiring the `Build APK / Nix debug APK` check.

---

## 6. Failure modes

| Symptom | Cause | Fix |
|---|---|---|
| Phone: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | installed APK was signed with a different key (debug CI artifact, old throwaway debug key, or a re-generated release key) | uninstall once, install the release asset; if it recurs on every release, the secret key does not match the one that signed the installed build |
| Phone: `INSTALL_FAILED_VERSION_DOWNGRADE` | `versionCode` not bumped | bump `versionCode` in `app/build.gradle.kts`, retag |
| Phone: `App not installed` / parse error | truncated download, or the `x86_64` APK installed on an arm64 phone | check `SHA256SUMS.txt`, use the `arm64-v8a` asset |
| Workflow: `missing repository secrets: ...` | secret not set or renamed | set all four (§3.1); the job fails by design rather than shipping an unsigned APK |
| Workflow: `assembleRelease produced an UNSIGNED APK` | signing env did not reach Gradle (wrong variable names, or `nix develop` dropped them) | check the `env:` block on the build step and the `JAPANESE_ANKI_RELEASE_*` names |
| Workflow: `APK is not v2-signed` | keystore unusable / signingConfig not applied | verify the keystore opens with `keytool -list`; check `apksigner-verify.txt` in the artifact |
| Workflow: signer digest mismatch | `ANDROID_RELEASE_CERT_SHA256` is stale or the wrong key was uploaded | regenerate the variable from the keystore (§5.3) |
| Release published, no issue comment | tag was not `*-issN` and no `issue_number` input | re-run with `workflow_dispatch` and `issue_number` set |
| Release exists / assets stale | re-run of an existing tag | intended: uploads `--clobber` and rewrites notes |
| `state:fix-ready` never applied | label does not exist | create labels (§5.5); the workflow skips label edits instead of failing |
| First run downloads 446 MiB and fails | app pins the dataset to `v0.4`; that asset is missing or rate-limited | check `v0.4` assets; unrelated to new releases |
| Two workers edit the same files | card not claimed, or `state:fixing` not applied | one card per issue, own worktree, `state:fixing` is the lock |
| Nothing happens overnight | host off; the poller is pull-based | expected — issues queue and are picked up on the next tick, nothing is lost |

---

## 7. What is deliberately not automated

- **Device acceptance.** `release-apk.yml` runs core tests, `testDebugUnitTest`, and static APK
  assertions; it does not boot an emulator or touch the phone. `arm64-v8a`-only defects are
  therefore never caught by CI, only by Pierre installing.
- **`testReleaseUnitTest` / release lint.** The release job runs the debug unit tests, matching
  `scripts/ci-build-apk.sh`. Release-variant lint is not gated in either workflow (see
  `docs/toolchain-notes.md` for the lint lock gap).
- **Independent review.** With one account and one agent there is no second reviewer; the gates
  are CI plus the deliberate merge. Do not describe this loop as reviewed.
- **Feature requests.** They enter the same labels but stop at `state:triaged` until scope is
  agreed; the automated fix path is for bugs with a reproducible expected behaviour.
- **Signing-key rotation.** Not automated and not routine (§3.3).
