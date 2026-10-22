#!/usr/bin/env python3
"""Regression checks for all three pinned AJT formatted pitch source parts."""

import hashlib
import sqlite3
import tempfile
from pathlib import Path

from build_ajt_pitch import build, to_class_spans

HEADER = "headword\traw_headword\tkatakana_reading\thtml_notation\tpitch_number\tfrequency\n"
DEVOICED = "<low_rise>ウ</low_rise><high>ケ<devoiced>ツ</devoiced>ケ</high>"
PLAIN = "<low_rise>ウ</low_rise><high>ケツケ</high>"
DEKIRU = "<low_rise>デ</low_rise><high_drop>キ</high_drop><low>ル</low>"


def source_row(headword: str, expression: str, reading: str, html: str, accent: str = "0") -> str:
    return f"{headword}\t{expression}\t{reading}\t{html}\t{accent}\t0\n"


def hiragana(text: str) -> str:
    return "".join(chr(ord(char) - 0x60) if "ァ" <= char <= "ヶ" else char for char in text)


def katakana(text: str) -> str:
    return "".join(chr(ord(char) + 0x60) if "ぁ" <= char <= "ゖ" else char for char in text)


def query(connection: sqlite3.Connection, expression: str, reading: str):
    records = connection.execute(
        """SELECT pitch_html, pitch_number FROM pitch_ajt
           WHERE reading=? AND (headword=? OR reading=?) ORDER BY source_order""",
        (hiragana(reading), katakana(expression), hiragana(expression)),
    ).fetchall()
    return list(dict.fromkeys(records))


def main() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        parts = [root / f"pitch_accents_formatted.{part}.csv" for part in (1, 2, 3)]
        parts[0].write_text(
            HEADER
            + source_row("ウケツケ", "受付", "ウケツケ", DEVOICED)
            + source_row("デキル", "できる", "デキル", DEKIRU, "2"),
            encoding="utf-8",
        )
        parts[1].write_text(
            HEADER
            + source_row("受付", "受付", "ウケツケ", DEVOICED)
            + source_row("受ケツケ", "受けつけ", "ウケツケ", PLAIN)
            + source_row("受ケ付", "受け付", "ウケツケ", PLAIN)
            + source_row("受ケ付ケ", "受け付け", "ウケツケ", PLAIN)
            + source_row("受付ケ", "受付け", "ウケツケ", PLAIN)
            + source_row("受附", "受附", "ウケツケ", PLAIN)
            + source_row("出来ル", "出来る", "デキル", DEKIRU, "2")
            + source_row("出來ル", "出來る", "デキル", DEKIRU, "2")
            + source_row("出切ル", "出切る", "デキル", "<low_rise>デ</low_rise><high>キル</high>", "0"),
            encoding="utf-8",
        )
        parts[2].write_text(HEADER, encoding="utf-8")

        output = root / "pitch_ajt.sqlite"
        records, _packed_size = build(parts[2], output)
        connection = sqlite3.connect(output)
        columns = {row[1] for row in connection.execute("PRAGMA table_info(pitch_ajt)")}
        assert {"expression", "headword", "reading", "pitch_html", "pitch_number", "source_order"} <= columns
        assert records == 11

        # Source rows are retained; app/plugin result de-duplicates identical rendered marks.
        raw = connection.execute(
            "SELECT pitch_html, pitch_number FROM pitch_ajt WHERE expression=? AND reading=? ORDER BY source_order",
            ("受付", "うけつけ"),
        ).fetchall()
        converted_devoiced = to_class_spans(DEVOICED)
        assert raw == [(converted_devoiced, 0), (converted_devoiced, 0)]
        assert query(connection, "受付", "うけつけ") == [(converted_devoiced, 0)]
        kana_results = query(connection, "うけつけ", "ウケツケ")
        assert (converted_devoiced, 0) in kana_results
        assert len(kana_results) == len(set(kana_results))

        converted_plain = to_class_spans(PLAIN)
        for spelling in ("受けつけ", "受け付", "受け付け", "受付け", "受附"):
            assert query(connection, spelling, "うけつけ") == [(converted_plain, 0)]

        converted_dekiru = to_class_spans(DEKIRU)
        assert query(connection, "出来る", "できる") == [(converted_dekiru, 2)]
        assert query(connection, "できる", "できる") == [
            (converted_dekiru, 2),
            (to_class_spans("<low_rise>デ</low_rise><high>キル</high>"), 0),
        ]

        metadata = dict(connection.execute("SELECT key, value FROM metadata"))
        assert [metadata[f"source_part_{part}"] for part in (1, 2, 3)] == [path.name for path in parts]
        assert [metadata[f"source_sha256_part_{part}"] for part in (1, 2, 3)] == [
            hashlib.sha256(path.read_bytes()).hexdigest() for path in parts
        ]
        assert "pitch_accents_formatted.1.csv" in metadata["source"]
        assert "pitch_accents_formatted.2.csv" in metadata["source"]
        assert "pitch_accents_formatted.3.csv" in metadata["source"]
        assert metadata["license"] == (
            "See datasets/README.md; third-party source-data rights remain unresolved; "
            "AGPL-3.0 covers AJT software only."
        )
        connection.close()

        # A canonical AJT part without its siblings must fail before replacing prior output.
        incomplete = root / "incomplete"
        incomplete.mkdir()
        canonical = incomplete / "pitch_accents_formatted.3.csv"
        canonical.write_text(HEADER, encoding="utf-8")
        protected = incomplete / "preserve.sqlite"
        protected.write_bytes(b"keep")
        try:
            build(canonical, protected)
        except SystemExit as error:
            assert "Missing AJT formatted source part" in str(error)
        else:
            raise AssertionError("an incomplete canonical AJT shard set was accepted")
        assert protected.read_bytes() == b"keep"

    print("AJT pitch builder regression checks passed")


if __name__ == "__main__":
    main()
