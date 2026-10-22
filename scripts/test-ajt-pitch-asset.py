#!/usr/bin/env python3
"""Validate the bundled AJT index against the pinned source and plugin lookup rules."""

import gzip
import shutil
import sqlite3
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/pitch_ajt.sqlite.gz"
SOURCE_HASHES = (
    "4068078c59667f963922a4c0adae9b56e9a878b41e769bb87249b7236e99d234",
    "ddc8a9a92f80ad03834fe4f1e2a352ef4cc85921e96cccab03094376cc17c5c1",
    "08a419115a6dafe19631294a56fad064092a62a377478d6263a714ea5c0a4bd1",
)
UKETSUKE_HTML = '<span class="low_rise">ウ</span><span class="high">ケ<span class="devoiced">ツ</span>ケ</span>'
UKETSUKE_PLAIN_HTML = '<span class="low_rise">ウ</span><span class="high">ケツケ</span>'
DEKIRU_HTML = '<span class="low_rise">デ</span><span class="high_drop">キ</span><span class="low">ル</span>'
DEKIRU_HEIBAN_HTML = '<span class="low_rise">デ</span><span class="high">キル</span>'


def hiragana(text: str) -> str:
    return "".join(chr(ord(char) - 0x60) if "ァ" <= char <= "ヶ" else char for char in text)


def katakana(text: str) -> str:
    return "".join(chr(ord(char) + 0x60) if "ぁ" <= char <= "ゖ" else char for char in text)


def lookup(connection: sqlite3.Connection, expression: str, reading: str):
    records = connection.execute(
        """SELECT pitch_html, pitch_number FROM pitch_ajt
           WHERE reading=? AND (headword=? OR reading=?) ORDER BY source_order""",
        (hiragana(reading), katakana(expression), hiragana(expression)),
    ).fetchall()
    return list(dict.fromkeys(records))


def main() -> None:
    with tempfile.NamedTemporaryFile(suffix=".sqlite") as database_file:
        with gzip.open(ASSET, "rb") as compressed:
            shutil.copyfileobj(compressed, database_file)
        database_file.flush()
        connection = sqlite3.connect(database_file.name)
        columns = {row[1] for row in connection.execute("PRAGMA table_info(pitch_ajt)")}
        assert {"expression", "headword", "reading", "pitch_html", "pitch_number", "source_order"} <= columns
        assert connection.execute("SELECT count(*) FROM pitch_ajt").fetchone()[0] == 676_632
        indexes = {row[1] for row in connection.execute("PRAGMA index_list(pitch_ajt)")}
        assert {"pitch_ajt_headword_lookup", "pitch_ajt_reading_lookup"} <= indexes

        metadata = dict(connection.execute("SELECT key, value FROM metadata"))
        assert metadata["source"] == (
            "AJT Japanese 5729b8c080823eadaa121c76e0f23c55d6a81935 "
            "pitch_accents_formatted.1.csv, pitch_accents_formatted.2.csv, pitch_accents_formatted.3.csv"
        )
        assert tuple(metadata[f"source_sha256_part_{part}"] for part in (1, 2, 3)) == SOURCE_HASHES
        assert tuple(metadata[f"source_part_{part}"] for part in (1, 2, 3)) == tuple(
            f"pitch_accents_formatted.{part}.csv" for part in (1, 2, 3)
        )
        assert metadata["source_sha256"] == "0a140e3f4632ffddf4d5facc92dbca6f3d566bd5464848acfddadd582996e09e"
        assert metadata["license"] == (
            "See datasets/README.md; third-party source-data rights remain unresolved; "
            "AGPL-3.0 covers AJT software only."
        )

        expected = [(UKETSUKE_HTML, 0)]
        assert lookup(connection, "受付", "うけつけ") == expected
        assert expected[0] in lookup(connection, "うけつけ", "ウケツケ")
        kana_results = lookup(connection, "うけつけ", "ウケツケ")
        assert len(kana_results) == len(set(kana_results))
        assert {(html, accent) for html, accent in kana_results} == {
            (UKETSUKE_HTML, 0),
            (UKETSUKE_PLAIN_HTML, 0),
        }
        for spelling in ("受けつけ", "受け付", "受け付け", "受付け", "受附"):
            assert lookup(connection, spelling, "うけつけ") == [(UKETSUKE_PLAIN_HTML, 0)]

        assert lookup(connection, "出来る", "できる") == [(DEKIRU_HTML, 2)]
        assert lookup(connection, "出來る", "できる") == [(DEKIRU_HTML, 2)]
        assert lookup(connection, "出きる", "できる") == [(DEKIRU_HTML, 2)]
        assert lookup(connection, "できる", "できる") == [(DEKIRU_HTML, 2), (DEKIRU_HEIBAN_HTML, 0)]
        assert {accent for _html, accent in lookup(connection, "出切る", "できる")} == {0, 2}
        connection.close()
    print("bundled AJT pitch asset regression checks passed")


if __name__ == "__main__":
    main()
