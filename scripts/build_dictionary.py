#!/usr/bin/env python3
"""Build the app's local SQLite index from pinned, licensed dictionary inputs."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import shutil
import sqlite3
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
KANJIUM_COMMIT = "8a0cdaa16d64a281a2048de2eee2ec5e3a440fa6"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def text(element: ET.Element) -> str:
    return "".join(element.itertext()).strip()


def entry_forms(entry: ET.Element) -> set[tuple[str, str]]:
    spellings = [text(item.find("keb")) for item in entry.findall("k_ele")]
    readings = entry.findall("r_ele")
    result: set[tuple[str, str]] = set()
    for item in readings:
        reading = text(item.find("reb"))
        restrictions = {text(value) for value in item.findall("re_restr")}
        if not spellings or item.find("re_nokanji") is not None:
            result.add((reading, reading))
            continue
        allowed = [form for form in spellings if not restrictions or form in restrictions]
        result.update((form, reading) for form in allowed)
    return result


def pitch_alternatives(raw: str, line_number: int) -> list[tuple[str, int]]:
    current_pos = ""
    result = []
    for item in raw.split(","):
        match = re.fullmatch(r"(?:\(([^)]+)\))?(\d+)", item)
        if match is None:
            raise ValueError(f"Invalid Kanjium pitch at line {line_number}: {item!r}")
        if match.group(1) is not None:
            current_pos = match.group(1)
        result.append((current_pos, int(match.group(2))))
    return result


def entry_senses(entry: ET.Element) -> str:
    senses = []
    for sense in entry.findall("sense"):
        glosses = [
            text(gloss)
            for gloss in sense.findall("gloss")
            if gloss.get(XML_LANG, "eng") == "eng" and text(gloss)
        ]
        senses.append(
            {
                "glosses": glosses,
                "pos": [text(item) for item in sense.findall("pos") if text(item)],
                "misc": [text(item) for item in sense.findall("misc") if text(item)],
                "stagk": [text(item) for item in sense.findall("stagk") if text(item)],
                "stagr": [text(item) for item in sense.findall("stagr") if text(item)],
            },
        )
    return json.dumps(senses, ensure_ascii=False, separators=(",", ":"))


def build(
    jmdict: Path,
    kanjium: Path,
    output: Path,
    jmdict_source: str = "EDRDG JMdict_e.gz, 2026-09-26 snapshot",
) -> tuple[int, int]:
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        database_path = Path(temporary) / "dictionary.sqlite"
        connection = sqlite3.connect(database_path)
        connection.executescript(
            """
            PRAGMA page_size=4096;
            PRAGMA journal_mode=OFF;
            PRAGMA synchronous=OFF;
            PRAGMA encoding='UTF-8';
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY,
                senses TEXT NOT NULL
            );
            CREATE TABLE forms (
                expression TEXT NOT NULL,
                reading TEXT NOT NULL,
                entry_id INTEGER NOT NULL REFERENCES entries(id),
                PRIMARY KEY(expression, reading, entry_id)
            ) WITHOUT ROWID;
            CREATE INDEX forms_expression ON forms(expression);
            CREATE INDEX forms_reading ON forms(reading);
            CREATE TABLE pitch (
                expression TEXT NOT NULL,
                reading TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                accent INTEGER NOT NULL,
                PRIMARY KEY(expression, reading, part_of_speech, accent)
            ) WITHOUT ROWID;
            CREATE INDEX pitch_lookup ON pitch(expression, reading);
            CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
            PRAGMA user_version=1;
            """,
        )
        entry_count = 0
        form_batch: list[tuple[str, str, int]] = []
        entry_batch: list[tuple[int, str]] = []
        with gzip.open(jmdict, "rb") as source:
            for _, entry in ET.iterparse(source, events=("end",)):
                if entry.tag != "entry":
                    continue
                entry_id_element = entry.find("ent_seq")
                if entry_id_element is None or not text(entry_id_element):
                    raise ValueError("JMdict entry missing ent_seq")
                entry_id = int(text(entry_id_element))
                entry_batch.append((entry_id, entry_senses(entry)))
                form_batch.extend(
                    (expression, reading, entry_id)
                    for expression, reading in sorted(entry_forms(entry))
                )
                entry_count += 1
                entry.clear()
                if len(entry_batch) >= 1000:
                    connection.executemany("INSERT INTO entries VALUES (?, ?)", entry_batch)
                    connection.executemany("INSERT INTO forms VALUES (?, ?, ?)", form_batch)
                    entry_batch.clear()
                    form_batch.clear()
            if entry_batch:
                connection.executemany("INSERT INTO entries VALUES (?, ?)", entry_batch)
                connection.executemany("INSERT INTO forms VALUES (?, ?, ?)", form_batch)

        pitch_count = 0
        pitch_batch: list[tuple[str, str, str, int]] = []
        with kanjium.open(encoding="utf-8-sig") as source:
            for line_number, line in enumerate(source, 1):
                fields = line.rstrip("\n\r").split("\t")
                if len(fields) != 3:
                    raise ValueError(f"Invalid Kanjium row at line {line_number}")
                expression, reading, raw_accents = fields
                reading = reading or expression
                for part_of_speech, accent in pitch_alternatives(raw_accents, line_number):
                    pitch_batch.append((expression, reading, part_of_speech, accent))
                    pitch_count += 1
                if len(pitch_batch) >= 5000:
                    connection.executemany("INSERT OR IGNORE INTO pitch VALUES (?, ?, ?, ?)", pitch_batch)
                    pitch_batch.clear()
            if pitch_batch:
                connection.executemany("INSERT OR IGNORE INTO pitch VALUES (?, ?, ?, ?)", pitch_batch)

        metadata = {
            "schema_version": "1",
            "jmdict_sha256": sha256(jmdict),
            "kanjium_sha256": sha256(kanjium),
            "kanjium_commit": KANJIUM_COMMIT,
            "jmdict_source": jmdict_source,
        }
        connection.executemany("INSERT INTO metadata VALUES (?, ?)", metadata.items())
        connection.commit()
        connection.execute("PRAGMA optimize")
        connection.close()

        temporary_output = output.with_suffix(output.suffix + ".tmp")
        with database_path.open("rb") as source, temporary_output.open("wb") as destination:
            with gzip.GzipFile(filename="", mode="wb", fileobj=destination, compresslevel=9, mtime=0) as compressed:
                shutil.copyfileobj(source, compressed)
        temporary_output.replace(output)
    return entry_count, pitch_count


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jmdict", type=Path, default=Path("datasets/jmdict/JMdict_e.gz"))
    parser.add_argument("--kanjium", type=Path, default=Path("datasets/kanjium/accents.txt"))
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/dictionary.sqlite.gz"),
    )
    parser.add_argument(
        "--jmdict-source",
        default="EDRDG JMdict_e.gz, 2026-09-26 snapshot",
    )
    args = parser.parse_args()
    entries, accents = build(args.jmdict, args.kanjium, args.output, args.jmdict_source)
    print(f"Indexed {entries:,} JMdict entries and {accents:,} Kanjium pitch patterns: {args.output}")


if __name__ == "__main__":
    main()
