#!/usr/bin/env python3
"""Build a compact pitch index from AJT Japanese's formatted pitch records.

Input: all three pitch_accents_formatted.N.csv parts at AJT commit
5729b8c080823eadaa121c76e0f23c55d6a81935. Pass any canonical part; all three are required.
Output: SQLite with raw expression, AJT headword, hiragana reading, class-span HTML and pitch number.

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
SOURCE_PARTS = (1, 2, 3)
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


def source_files(csv_path: Path) -> list[Path]:
    """AJT's canonical split input is atomic: never silently build from one shard."""
    if not re.fullmatch(r"pitch_accents_formatted\.[123]\.csv", csv_path.name):
        return [csv_path]
    paths = [csv_path.with_name(f"pitch_accents_formatted.{part}.csv") for part in SOURCE_PARTS]
    missing = [path for path in paths if not path.is_file()]
    if missing:
        raise SystemExit(
            "Missing AJT formatted source part(s): " + ", ".join(str(path) for path in missing)
        )
    return paths


def build(csv_path: Path, output: Path) -> tuple[int, int]:
    inputs = source_files(csv_path)
    for stale in (output, output.with_suffix(output.suffix + ".gz")):
        if stale.exists():
            stale.unlink()
    connection = sqlite3.connect(output)
    connection.executescript(
        """
        PRAGMA journal_mode=OFF;
        CREATE TABLE pitch_ajt (
            expression TEXT NOT NULL,
            headword TEXT NOT NULL,
            reading TEXT NOT NULL,
            pitch_html TEXT NOT NULL,
            pitch_number INTEGER NOT NULL,
            source_order INTEGER NOT NULL
        );
        CREATE INDEX pitch_ajt_headword_lookup ON pitch_ajt(headword, reading, source_order);
        CREATE INDEX pitch_ajt_reading_lookup ON pitch_ajt(reading, source_order);
        """
    )
    rows = []
    source_order = 0
    for csv_part in inputs:
        with csv_part.open(encoding="utf-8-sig", newline="") as source:
            header = source.readline().rstrip("\n\r").split("\t")
            expected = ["headword", "raw_headword", "katakana_reading", "html_notation", "pitch_number", "frequency"]
            if header != expected:
                raise SystemExit(f"Unexpected AJT header in {csv_part}: {header}")
            for line in source:
                order = source_order
                source_order += 1
                fields = line.rstrip("\n\r").split("\t")
                if len(fields) != 6:
                    raise SystemExit(f"Invalid AJT row {csv_part}:{order + 2}")
                headword, raw_headword, katakana_reading, html_notation, pitch_number, _frequency = fields
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
                        headword or katakana_reading,
                        to_hiragana(katakana_reading),
                        to_class_spans(html_notation),
                        int(pitch_number),
                        order,
                    )
                )
                if len(rows) >= 20000:
                    connection.executemany("INSERT INTO pitch_ajt VALUES (?, ?, ?, ?, ?, ?)", rows)
                    rows.clear()
    if rows:
        connection.executemany("INSERT INTO pitch_ajt VALUES (?, ?, ?, ?, ?, ?)", rows)
    connection.execute("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
    source_hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs]
    aggregate = hashlib.sha256()
    for path, digest in zip(inputs, source_hashes, strict=True):
        aggregate.update(path.name.encode("utf-8"))
        aggregate.update(b"\0")
        aggregate.update(bytes.fromhex(digest))
    metadata = [
        ("source", f"AJT Japanese {SOURCE_COMMIT} " + ", ".join(path.name for path in inputs)),
        ("source_sha256", aggregate.hexdigest()),
        ("license", "See datasets/README.md; third-party source-data rights remain unresolved; AGPL-3.0 covers AJT software only."),
        ("phonetic_note", "Unmarked rows carry no supplied mark; absence is not verified non-nasalisation."),
    ]
    metadata.extend((f"source_part_{index}", path.name) for index, path in enumerate(inputs, start=1))
    metadata.extend((f"source_sha256_part_{index}", digest) for index, digest in enumerate(source_hashes, start=1))
    connection.executemany(
        "INSERT INTO metadata VALUES (?, ?)",
        metadata,
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
    print(
        f"Indexed {count:,} AJT pitch records from {len(source_files(args.csv))} source file(s) "
        f"into {args.output} ({packed:,} bytes gzipped)"
    )


if __name__ == "__main__":
    main()
