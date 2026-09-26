#!/usr/bin/env python3
"""Small stdlib regression check for the bundled dictionary builder."""

import gzip
import sqlite3
import tempfile
from pathlib import Path

from build_dictionary import build


FIXTURE = """<?xml version='1.0' encoding='UTF-8'?>
<JMdict>
  <entry>
    <ent_seq>10</ent_seq>
    <k_ele><keb>甲</keb></k_ele><k_ele><keb>乙</keb></k_ele>
    <r_ele><reb>こう</reb><re_restr>甲</re_restr></r_ele>
    <r_ele><reb>おつ</reb><re_nokanji/></r_ele>
    <sense><pos>n</pos><gloss>first meaning</gloss></sense>
    <sense><gloss>second meaning</gloss></sense>
  </entry>
</JMdict>
"""


def main() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        jmdict = root / "jmdict.xml.gz"
        with gzip.open(jmdict, "wt", encoding="utf-8") as output:
            output.write(FIXTURE)
        kanjium = root / "accents.txt"
        kanjium.write_text("乙\tおつ\t(名)0,2,(副)1\n", encoding="utf-8")
        database_gzip = root / "dictionary.sqlite.gz"
        entries, pitches = build(jmdict, kanjium, database_gzip)
        assert (entries, pitches) == (1, 3)
        database = root / "dictionary.sqlite"
        with gzip.open(database_gzip, "rb") as source, database.open("wb") as output:
            output.write(source.read())
        connection = sqlite3.connect(database)
        forms = connection.execute(
            "SELECT expression, reading FROM forms ORDER BY expression, reading",
        ).fetchall()
        assert forms == [("おつ", "おつ"), ("甲", "こう")]
        senses = connection.execute("SELECT senses FROM entries WHERE id=10").fetchone()[0]
        assert '"first meaning"' in senses and '"second meaning"' in senses
        pitch = connection.execute(
            "SELECT part_of_speech, accent FROM pitch ORDER BY accent",
        ).fetchall()
        assert pitch == [("名", 0), ("副", 1), ("名", 2)]
        connection.close()
    print("dictionary builder self-check passed")


if __name__ == "__main__":
    main()
