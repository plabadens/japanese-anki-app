#!/usr/bin/env python3
"""Check every relative Markdown link and image target in tracked docs.

Reads the file list from `git ls-files` so build output, worktrees and ignored
directories never enter the walk. Reports links that resolve outside the
repository, point at an untracked path, or name a missing file.

Usage: scripts/check-doc-links.py [--all]
  default   check *.md files
  --all     also check the HTML study under mockup/
"""

from __future__ import annotations

import re
import subprocess
import sys
import urllib.parse
from pathlib import Path

INLINE = re.compile(r"!?\[[^\]]*\]\(\s*(<[^>]+>|[^)\s]+)(?:\s+\"[^\"]*\")?\s*\)")
REFDEF = re.compile(r"^\s{0,3}\[[^\]]+\]:\s*(<[^>]+>|\S+)", re.MULTILINE)
HTML_ATTR = re.compile(r"(?:href|src)\s*=\s*[\"']([^\"']+)[\"']")


def tracked(root: Path) -> list[str]:
    out = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        capture_output=True,
        check=True,
    ).stdout.decode()
    return [p for p in out.split("\0") if p]


def targets(text: str, path: Path) -> list[tuple[str, int]]:
    found: list[tuple[str, int]] = []
    for regex in (INLINE, REFDEF):
        for m in regex.finditer(text):
            target = m.group(1).strip("<>")
            line = text.count("\n", 0, m.start()) + 1
            found.append((target, line))
    if path.suffix == ".html":
        for m in HTML_ATTR.finditer(text):
            line = text.count("\n", 0, m.start()) + 1
            found.append((m.group(1), line))
    return found


def main() -> int:
    include_html = "--all" in sys.argv
    root = Path(subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, check=True, text=True
    ).stdout.strip())
    files = tracked(root)
    known = set(files)

    checked = broken = untracked = 0
    for rel in files:
        path = root / rel
        if not (rel.endswith(".md") or (include_html and rel.endswith(".html"))):
            continue
        if not path.exists():
            # Tracked in the index but removed in the working tree: a staged move or
            # deletion. Nothing to read, and its own links are no longer authoritative.
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for target, line in targets(text, path):
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target):  # scheme: http, mailto, data
                continue
            if target.startswith("#"):
                continue
            checked += 1
            path_part = urllib.parse.unquote(target.split("#", 1)[0])
            if not path_part:
                continue
            resolved = (path.parent / path_part).resolve()
            try:
                relres = resolved.relative_to(root)
            except ValueError:
                print(f"{rel}:{line}: escapes the repository: {target}")
                broken += 1
                continue
            if not resolved.exists():
                print(f"{rel}:{line}: missing target: {target}")
                broken += 1
            elif not any(
                k == str(relres) or k.startswith(str(relres) + "/") for k in known
            ):
                print(f"{rel}:{line}: target exists but is not tracked: {target}")
                untracked += 1

    print(f"\n{checked} relative link(s) checked; {broken} broken, {untracked} untracked target(s)")
    return 1 if broken else 0


if __name__ == "__main__":
    raise SystemExit(main())
