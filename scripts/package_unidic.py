#!/usr/bin/env python3
"""Build a deterministic, runtime-only archive from official UniDic CWJ data."""

import argparse
import shutil
import zipfile
from pathlib import Path

SOURCE_ROOT = "unidic-cwj-3.1.1/"
RUNTIME_FILES = ("char.bin", "dicrc", "matrix.bin", "sys.dic", "unk.dic")


def package(archive: Path, output: Path) -> None:
    with zipfile.ZipFile(archive) as source, zipfile.ZipFile(
        output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9,
    ) as destination:
        names = set(source.namelist())
        for filename in RUNTIME_FILES:
            source_name = SOURCE_ROOT + filename
            if source_name not in names:
                raise ValueError(f"UniDic archive is missing {source_name}")
            info = zipfile.ZipInfo("unidic-cwj-3.1.1/" + filename, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            with source.open(source_name) as input_file, destination.open(info, "w", force_zip64=True) as output_file:
                shutil.copyfileobj(input_file, output_file)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True, help="Pinned official UniDic CWJ 3.1.1 zip")
    parser.add_argument("--output", type=Path, required=True, help="Output runtime-only zip")
    args = parser.parse_args()
    package(args.archive, args.output)


if __name__ == "__main__":
    main()
