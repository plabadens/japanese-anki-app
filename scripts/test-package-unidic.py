#!/usr/bin/env python3
"""Tiny deterministic packaging self-check for the UniDic runtime archive."""

import hashlib
import tempfile
import zipfile
from pathlib import Path

from package_unidic import RUNTIME_FILES, SOURCE_ROOT, package


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        source = root / "source.zip"
        outputs = [root / f"runtime-{index}.zip" for index in range(2)]
        expected = {name: f"fixture:{name}".encode() for name in RUNTIME_FILES}
        with zipfile.ZipFile(source, "w") as archive:
            for name, contents in expected.items():
                archive.writestr(SOURCE_ROOT + name, contents)
            archive.writestr(SOURCE_ROOT + "training.csv", b"excluded")
        for output in outputs:
            package(source, output)
        hashes = [hashlib.sha256(output.read_bytes()).digest() for output in outputs]
        assert hashes[0] == hashes[1]
        with zipfile.ZipFile(outputs[0]) as archive:
            assert archive.testzip() is None
            assert sorted(archive.namelist()) == ["unidic-cwj-3.1.1/" + name for name in RUNTIME_FILES]
            for name, contents in expected.items():
                assert archive.read("unidic-cwj-3.1.1/" + name) == contents
    print("UniDic runtime packager self-check passed")


if __name__ == "__main__":
    main()
