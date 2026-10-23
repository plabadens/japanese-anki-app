#!/usr/bin/env python3
"""Small UI-driving harness for the API-37 emulator acceptance run.

Run it inside `nix develop` (the pinned Android SDK supplies `adb`). Every device
command goes through `scripts/emulator-adb` (or `$EMULATOR_ADB` when the flake
installed the helper), which resolves one emulator serial and rejects physical
devices. Every subcommand is one bounded action so a journey can be replayed step
by step and its evidence captured:

    scripts/ui-journey.py theme dark
    scripts/ui-journey.py size 360x828
    scripts/ui-journey.py shot .tmp-nix/screenshots/input-dark.png
    scripts/ui-journey.py tap "Continue"
    scripts/ui-journey.py dump --grep 本
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "dev.pierre.japaneseanki"
ADB_HELPER = Path(os.environ.get("EMULATOR_ADB", Path(__file__).with_name("emulator-adb"))).resolve()


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(ADB_HELPER), "run", *args],
        check=check,
        capture_output=True,
        text=True,
    )


def shell(*args: str, check: bool = True) -> str:
    return adb("shell", *args, check=check).stdout


def screencap(path: str) -> None:
    result = subprocess.run(
        [str(ADB_HELPER), "run", "exec-out", "screencap", "-p"],
        check=True,
        capture_output=True,
    )
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_bytes(result.stdout)


def dump_xml() -> ET.Element:
    scratch_dir = os.environ.get("TMPDIR") or None
    remote_dump = f"/sdcard/ui-journey-{uuid.uuid4().hex}.xml"
    with tempfile.TemporaryDirectory(prefix="ui-journey-", dir=scratch_dir) as temporary:
        local_dump = Path(temporary) / "window_dump.xml"
        # uiautomator exits 0 even when it cannot dump a hierarchy, so check its
        # output instead of letting the following pull fail with a bare exit status.
        # --compressed is also the form that works on the API-37 image, where a plain
        # `uiautomator dump` returns "null root node returned by UiTestAutomationBridge".
        dumped = adb("shell", "uiautomator", "dump", "--compressed", remote_dump, check=False)
        if "dumped to" not in dumped.stdout:
            detail = (dumped.stdout + dumped.stderr).strip() or "(no output)"
            raise RuntimeError(f"uiautomator dump failed for {remote_dump}: {detail}")
        adb("pull", remote_dump, str(local_dump))
        try:
            return ET.parse(local_dump).getroot()
        finally:
            adb("shell", "rm", "-f", remote_dump, check=False)


def nodes(root: ET.Element):
    for node in root.iter("node"):
        bounds = node.get("bounds", "")
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            continue
        x1, y1, x2, y2 = (int(value) for value in match.groups())
        yield {
            "text": node.get("text", ""),
            "desc": node.get("content-desc", ""),
            "cls": node.get("class", ""),
            "enabled": node.get("enabled") == "true",
            "checked": node.get("checked") == "true",
            "selected": node.get("selected") == "true",
            "bounds": (x1, y1, x2, y2),
            "center": ((x1 + x2) // 2, (y1 + y2) // 2),
        }


def find(label: str, *, desc: bool = False, exact: bool = False, index: int = 0):
    root = dump_xml()
    wanted = label if exact else None
    matches = []
    for node in nodes(root):
        value = node["desc"] if desc else node["text"]
        if (value == wanted) if wanted is not None else (label in value):
            matches.append(node)
    if index >= len(matches):
        return None
    return matches[index]


def print_tree(grep: str | None) -> None:
    for node in nodes(dump_xml()):
        label = node["text"] or node["desc"]
        if not label:
            continue
        if grep and grep not in label:
            continue
        flags = "".join(
            [
                "E" if node["enabled"] else "-",
                "C" if node["checked"] else "-",
                "S" if node["selected"] else "-",
            ]
        )
        print(f"{flags} {node['center']!s:>14}  {node['cls'].split('.')[-1]:<18} {label}")


def wait_for(label: str, *, desc: bool = False, timeout: float = 30.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if find(label, desc=desc):
            return True
        time.sleep(1.0)
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("dump")
    p.add_argument("--grep", default=None)

    p = sub.add_parser("tap")
    p.add_argument("label")
    p.add_argument("--desc", action="store_true")
    p.add_argument("--exact", action="store_true")
    p.add_argument("--index", type=int, default=0)

    p = sub.add_parser("tapxy")
    p.add_argument("x", type=int)
    p.add_argument("y", type=int)

    p = sub.add_parser("shot")
    p.add_argument("path")

    p = sub.add_parser("theme")
    p.add_argument("value", choices=["light", "dark"])

    p = sub.add_parser("size")
    p.add_argument("value", help="WIDTHxHEIGHT in dp, for example 360x828")
    p.add_argument("--density", type=int, default=320)

    p = sub.add_parser("font")
    p.add_argument("scale", type=float)

    p = sub.add_parser("share-text")
    p.add_argument("value")

    p = sub.add_parser("text")
    p.add_argument("value")

    p = sub.add_parser("key")
    p.add_argument("value")

    p = sub.add_parser("wait")
    p.add_argument("label")
    p.add_argument("--desc", action="store_true")
    p.add_argument("--timeout", type=float, default=30.0)

    p = sub.add_parser("restart")
    p.add_argument("--apk", default=None)

    args = parser.parse_args()

    if args.command == "dump":
        print_tree(args.grep)
    elif args.command == "tap":
        node = find(args.label, desc=args.desc, exact=args.exact, index=args.index)
        if node is None:
            print(f"no node matching {args.label!r}", file=sys.stderr)
            return 1
        x, y = node["center"]
        shell("input", "tap", str(x), str(y))
        print(f"tapped {args.label!r} at {x},{y}")
    elif args.command == "tapxy":
        shell("input", "tap", str(args.x), str(args.y))
    elif args.command == "shot":
        screencap(args.path)
        print(f"wrote {args.path}")
    elif args.command == "theme":
        shell("cmd", "uimode", "night", "yes" if args.value == "dark" else "no")
        time.sleep(2.0)
    elif args.command == "size":
        width, height = (int(part) for part in args.value.lower().split("x"))
        scale = args.density / 160.0
        shell("wm", "size", f"{int(width * scale)}x{int(height * scale)}")
        shell("wm", "density", str(args.density))
        time.sleep(3.0)
    elif args.command == "font":
        shell("settings", "put", "system", "font_scale", str(args.scale))
        time.sleep(2.0)
    elif args.command == "share-text":
        adb(
            "shell",
            "am",
            "start",
            "-a",
            "android.intent.action.SEND",
            "-t",
            "text/plain",
            "--es",
            "android.intent.extra.TEXT",
            args.value,
        )
        time.sleep(2.0)
    elif args.command == "text":
        shell("input", "text", args.value.replace(" ", "%s"))
    elif args.command == "key":
        shell("input", "keyevent", args.value)
    elif args.command == "wait":
        ok = wait_for(args.label, desc=args.desc, timeout=args.timeout)
        print("found" if ok else "timeout")
        return 0 if ok else 1
    elif args.command == "restart":
        if args.apk:
            adb("install", "-r", args.apk)
        shell("am", "force-stop", PACKAGE)
        shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")
        time.sleep(3.0)
    return 0


if __name__ == "__main__":
    sys.exit(main())
