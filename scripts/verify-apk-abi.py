#!/usr/bin/env python3
"""Fail unless an APK contains native .so files for exactly the requested ABI."""

import argparse
import sys
import zipfile
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("abi", choices=("arm64-v8a", "x86_64"))
    args = parser.parse_args()

    try:
        with zipfile.ZipFile(args.apk) as apk:
            native_entries = [
                name for name in apk.namelist()
                if name.startswith("lib/") and name.endswith(".so")
            ]
    except (OSError, zipfile.BadZipFile) as exc:
        parser.error(f"cannot read APK {args.apk}: {exc}")

    malformed = [name for name in native_entries if len(name.split("/")) != 3]
    abis = {name.split("/")[1] for name in native_entries if len(name.split("/")) == 3}
    if malformed or not native_entries or abis != {args.abi}:
        print(
            f"{args.apk}: expected native libraries for only {args.abi}; "
            f"found ABIs {sorted(abis)}, {len(native_entries)} libraries, "
            f"malformed paths {malformed}",
            file=sys.stderr,
        )
        return 1

    print(f"{args.apk}: {len(native_entries)} native libraries, ABI={args.abi} only")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
