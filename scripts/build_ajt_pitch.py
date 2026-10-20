#!/usr/bin/env python3
"""Build a compact pitch index from AJT Japanese's formatted pitch records.

Input: pitch_accents_formatted.3.csv at AJT commit 5729b8c080823eadaa121c76e0f23c55d6a81935.
Output: SQLite with expression, hiragana reading, HTML notation (class spans), pitch number.

The upstream CSV uses angle-bracket tags. This conversion only rewrites the tag syntax to the
class-based spans the app exports; nesting and supplied phonetic marks are preserved verbatim.
Unmarked rows stay unmarked: no nasalisation or devoicing is inferred.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import re
import sqlite3
from pathlib import Path

SOURCE_COMMIT = "5729b8c080823eadaa121c76e0f23c55d6a81935"
TAG = re.compile(r"<(/?)([a-z_]+)>")
KATAKANA = "ァィゥェォャュョヮヵヶ"
HIRAGANA = "ぁぃぅぇぉゃゅょゎゕゖ"


def to_hiragana(text: str) -> str:
    out = []
    for ch in text:
        code = ord(ch)
        if 0x30A1 <= code <= 0x30F6:
            out.append(chr(code - 0x60))
        else:
            out.append(ch)
    return "".join(out)


def to_class_spans(notation: str) -> str:
    def replace(match: re.Match[str]) -> str:
        closing, name = match.group(1), match.group(2)
        if closing:
            return "</span>"
        return '<span class="' + name + '">'

    return TAG.sub(replace, notation)


def build(csv_path: Path, output: Path) -> tuple[int, int]:
    for stale in (output, output.with_suffix(output.suffix + ".gz")):
        if stale.exists():
            stale.unlink()
    connection = sqlite3.connect(output)
    connection.executescript(
        """
        PRAGMA journal_mode=OFF;
        CREATE TABLE pitch_ajt (
            expression TEXT NOT NULL,
            reading TEXT NOT NULL,
            pitch_html TEXT NOT NULL,
            pitch_number INTEGER NOT NULL,
            source_order INTEGER NOT NULL
        );
        """
    )
    rows = []
    with csv_path.open(encoding="utf-8-sig", newline="") as source:
        header = source.readline().rstrip("\n\r").split("\t")
        expected = ["headword", "raw_headword", "katakana_reading", "html_notation", "pitch_number", "frequency"]
        if header != expected:
            raise SystemExit(f"Unexpected AJT header: {header}")
        for order, line in enumerate(source):
            fields = line.rstrip("\n\r").split("\t")
            if len(fields) != 6:
                raise SystemExit(f"Invalid AJT row {order + 2}")
            _headword, raw_headword, katakana_reading, html_notation, pitch_number, _frequency = fields
            expression = raw_headword or katakana_reading
            if not expression or not katakana_reading or not html_notation:
                continue
            # Multi-word compounds use a middle-dot separator and compound accent numbers
            # ("2+2"); they are not a single minable vocabulary item and cannot be matched to
            # one recording, so they are not indexed.
            if "\u30fb" in html_notation or "+" in pitch_number:
                continue
            rows.append(
                (
                    expression,
                    to_hiragana(katakana_reading),
                    to_class_spans(html_notation),
                    int(pitch_number),
                    order,
                )
            )
            if len(rows) >= 20000:
                connection.executemany("INSERT INTO pitch_ajt VALUES (?, ?, ?, ?, ?)", rows)
                rows.clear()
    if rows:
        connection.executemany("INSERT INTO pitch_ajt VALUES (?, ?, ?, ?, ?)", rows)
    connection.execute("CREATE INDEX pitch_ajt_lookup ON pitch_ajt(expression, reading, source_order)")
    connection.execute("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
    digest = hashlib.sha256(csv_path.read_bytes()).hexdigest()
    connection.executemany(
        "INSERT INTO metadata VALUES (?, ?)",
        [
            ("source", f"AJT Japanese {SOURCE_COMMIT} pitch_accents_formatted.3.csv"),
            ("source_sha256", digest),
            ("license", "AJT Japanese add-on data; upstream notices preserved in assets/licenses"),
            ("phonetic_note", "Unmarked rows carry no supplied mark; absence is not verified non-nasalisation."),
        ],
    )
    connection.commit()
    count = connection.execute("SELECT COUNT(*) FROM pitch_ajt").fetchone()[0]
    connection.close()
    with output.open("rb") as raw, gzip.open(str(output) + ".gz", "wb", compresslevel=9) as packed:
        packed.write(raw.read())
    return count, output.with_suffix(output.suffix + ".gz").stat().st_size


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    count, packed = build(args.csv, args.output)
    print(f"Indexed {count:,} AJT pitch records into {args.output} ({packed:,} bytes gzipped)")


if __name__ == "__main__":
    main()
