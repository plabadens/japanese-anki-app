# Archive

Everything here is point-in-time. Nothing in this directory is authority: each document starts with a
status banner saying what it was, what replaced it, and when. If a document here contradicts
`SPEC.md`, `DESIGN.md` or `BEST-PRACTICES.md`, the current document wins.

The material is kept because it is the only record of a decision or of a verification run. Delete
nothing here without checking that its facts exist somewhere current.

## Superseded documents

| File | What it was | Replaced by |
|---|---|---|
| [acceptance-test-matrix.md](acceptance-test-matrix.md) | Pre-redesign v0.2 acceptance matrix (A01–A67) and the API-35 emulator runbook | `SPEC.md` §14 (R01–R39); `BEST-PRACTICES.md` §5 |
| [ui-plan.md](ui-plan.md) | The row-per-field UI plan, its review checklist and its "current gaps" list | `SPEC.md` §4; `docs/DESIGN.md` |
| [live-testing-remediation.md](live-testing-remediation.md) | A remediation contract that overrode parts of `SPEC.md` | Merged into `SPEC.md` §3–§9; gate ladder in `docs/BEST-PRACTICES.md` §9 |

## Point-in-time records

| File | What it is | Status |
|---|---|---|
| [implementation-status.md](implementation-status.md) | Status snapshot at baseline `88c5252`, before the redesign | HISTORICAL |
| [emulator-readiness.md](emulator-readiness.md) | API-35 runner audit from 2026-10-14, with the on-device checks it exercised | HISTORICAL |
| [audio-reliability-integration.md](audio-reliability-integration.md) | The wiring recipe for draft-audio ownership, freeze and sweep — applied, kept as the design record | HISTORICAL |
| [media-receipt-wiring.md](media-receipt-wiring.md) | Why live and frozen media receipts are matched the way they are — applied | HISTORICAL |
| [google-basic-nmt/](google-basic-nmt/README.md) | Translation-adapter integration evidence with settings screenshots and per-row verdicts | HISTORICAL |
| [v0.3-final/](v0.3-final/README.md), [v0.3-candidate/](v0.3-candidate/README.md) | v0.3 install and acceptance documents. Byte-identical to the copies in `releases/v0.3/`, which are canonical; screenshot paths inside are relative to that release folder | HISTORICAL DUPLICATE |

## Release and verification evidence

| Path | What it is | Status |
|---|---|---|
| [evidence/api37-nix-provisioning.md](evidence/api37-nix-provisioning.md), [evidence/api37-emulator-launch.png](evidence/api37-emulator-launch.png) | API-37 AVD provisioning and first launch on the flake-pinned SDK | HISTORICAL |
| [evidence/remediation-integration-20261021/](evidence/remediation-integration-20261021/REPORT.md) | Merge manifest, changed files, and the unit/instrumentation logs for the remediation integration | HISTORICAL |
| [evidence/independent-acceptance-20261021/](evidence/independent-acceptance-20261021/ACCEPTANCE.md) | Independent acceptance of that integration, per-item, with logs | HISTORICAL |
| [evidence/live-testing/](evidence/live-testing/independent-acceptance.md) | Independent acceptance of the live-test remediation | HISTORICAL |
| [evidence/material3-upgrade/](evidence/material3-upgrade/) | `material3` 1.4.0 dependency insight, build and test logs, beta failure log, API inventory | HISTORICAL |
| [evidence/v0.4/](evidence/v0.4/README.md) | v0.4 install notes, the expressive-flow acceptance record, and the 28 screenshots of the last accepted build | HISTORICAL |
| `evidence/image-quality-sweep.csv`, `evidence/book-photo-webp-detail-sheet.png`, `evidence/synthetic-sign-webp-detail-sheet.png` | Measurements and contact sheets for the WebP quality decision | EVIDENCE (still current) |

Data and image files cannot carry a banner; the table above is their status. The v0.4 screenshots are
also the most useful visual reference for what the shipped UI actually looked like — use them when
comparing an implementation against `docs/DESIGN.md`, remembering that they predate the tokenizer
follow-up noted in `evidence/v0.4/ACCEPTANCE.md`.
