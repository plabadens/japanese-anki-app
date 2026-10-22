# Evidence: what is recorded, and for how long

**Status:** REFERENCE. The rules this directory follows are in
[BEST-PRACTICES.md §9](../BEST-PRACTICES.md); this page is only the retention policy for the
files themselves.

## Two locations, one rule

| Location | Holds |
|---|---|
| `docs/evidence/<topic>-<date>/` | Evidence for the **current** release candidate: the run that a ship decision is resting on |
| `docs/archive/evidence/<topic>-<date>/` | The same directories, once a later accepted release has superseded them |

Moving a directory into `archive/` is a curation step, not a deletion: the record of a shipped
build stays readable. Give it a one-line status banner when you move it.

## What belongs here

Curated, small, and readable: a report stating environment, revision, fixture, actual result and
verdict; JUnit XML or summary totals; small screenshots that support a specific claim; a patch or
inventory a reader can act on.

## What does not

- **Raw logcat dumps and full boot logs.** Attach them to the GitHub Release or the issue. A
  4 MB `boot-logcat.txt` is not evidence anyone reads; the verdict derived from it is.
- **Superseded render sets.** One canonical set per accepted design. The mockup harness
  regenerates renders on demand (`mockup/expressive-flow/verify_flow.py`).
- **Anything regenerable by a documented command** when the command itself is recorded. State the
  command and the result; do not commit its output.

These paths are covered by `.gitignore`, so a stray copy will not be committed by accident.

## Retention

Keep evidence for the current accepted release **and one previous** one. Beyond that, a directory
is a candidate for `archive/` pruning under the same rule as code: if nothing cites it and no
shipped artifact depends on it, it belongs in a release attachment rather than the tree.

## Citing evidence

Cite the directory, the revision it was run against, and the verdict — not the file count. If a
claim in an acceptance report has no directory behind it, the claim is NOT RUN.
