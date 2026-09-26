#!/usr/bin/env python3
"""Check the generated asset contains the pinned JMdict and Kanjium lookups."""

import gzip
import hashlib
import os
import shutil
import sqlite3
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    asset = ROOT / "app/src/main/assets/dictionary.sqlite.gz"
    with tempfile.NamedTemporaryFile(dir=os.environ.get("TMPDIR"), suffix=".sqlite") as database_file:
        with gzip.open(asset, "rb") as source:
            shutil.copyfileobj(source, database_file)
        database_file.flush()
        connection = sqlite3.connect(database_file.name)
        assert connection.execute("PRAGMA user_version").fetchone()[0] == 2
        metadata = dict(connection.execute("SELECT key, value FROM metadata"))
        assert metadata["jmdict_sha256"] == sha256(ROOT / "datasets/jmdict/JMdict_e.gz")
        assert metadata["kanjium_sha256"] == sha256(ROOT / "datasets/kanjium/accents.txt")
        assert connection.execute("SELECT count(*) FROM entries").fetchone()[0] > 200_000
        result = connection.execute(
            """SELECT e.senses FROM forms f JOIN entries e ON e.id=f.entry_id
               WHERE f.expression=? AND f.reading=? LIMIT 1""",
            ("食べる", "たべる"),
        ).fetchone()
        assert result and "to eat" in result[0]
        pitches = connection.execute(
            "SELECT accent FROM pitch WHERE expression=? AND reading=? ORDER BY source_order",
            ("猫", "ねこ"),
        ).fetchall()
        assert pitches and pitches[0][0] == 1
        connection.close()
    print("bundled dictionary asset self-check passed")


if __name__ == "__main__":
    main()
