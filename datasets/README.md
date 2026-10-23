# Bundled linguistic data inputs

These are pinned linguistic data sources for offline processing. Do not replace them with proprietary Rikaitan downloads.

## MeCab and UniDic

See [`mecab/README.md`](mecab/README.md) and [`unidic/README.md`](unidic/README.md) for the pinned morphology engine, the bundled UniDic 2.1.2 data (`unidic-lite`), licenses and attribution.

## JMdict

- Source: [EDRDG JMdict project](https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project), current English file `https://www.edrdg.org/pub/Nihongo/JMdict_e.gz`.
- Source snapshot: server `Last-Modified: 2026-09-26`; SHA-256: `824c37b65ae3a731981da5ca3addcfd15e181067bab96d02dee7f27660044d99`.
- Copyright: James William Breen and the Electronic Dictionary Research and Development Group.
- License: CC BY-SA 4.0; see [EDRDG-licence.html](jmdict/EDRDG-licence.html) and the linked [license deed](https://creativecommons.org/licenses/by-sa/4.0/).
- The upstream license requires attribution in app documentation/About and a procedure to update the data regularly. For this sideloaded app, update the checked-in snapshot at least monthly, rebuild the bundled database/APK, and install the update. Run `scripts/update-jmdict.sh`, record the new upstream date/hash, and rebuild. Runtime dictionary downloads are intentionally not used.

## Kanjium pitch data

- Source: `accents.txt` from [mifunetoshiro/kanjium](https://github.com/mifunetoshiro/kanjium), commit `8a0cdaa16d64a281a2048de2eee2ec5e3a440fa6` (2024-06-16).
- SHA-256: `8bd0dd127dab32ceec94cb03ab1ba6b68858ea73421dfa1731af2f373deb4f20`.
- License: CC BY-SA 4.0; see [LICENSE.txt](kanjium/LICENSE.txt).
- Required attribution: “The pitch accent notation, verb particle data, phonetics, homonyms and other additions or modifications to EDICT, KANJIDIC or KRADFILE were provided by Uros O. through his free database.”
- Source data is a tab-separated spelling, reading, accent-position list. A comma-separated list represents alternatives; `0` is a valid heiban pattern.

## NHK-2016 pronunciation index

- Source: `index.json` from [Ajatt-Tools/nhk_2016_pronunciations_index](https://github.com/Ajatt-Tools/nhk_2016_pronunciations_index), commit `b0207d240f0f28cdc2dd491dd83343c3a5630119`.
- Source bytes: 36,971,461; SHA-256: `9d9714b79c15ae6586d053665bf6cd0b881fcd56ed42ed3b4d758ea0499f1137`.
- The exact index is bundled as `app/src/main/assets/nhk-2016-b0207d240f0f.json`; Android instrumentation checks its size, digest and offline headword lookup.
- The index repository is GPL-3.0; the full license is bundled at `app/src/main/assets/licenses/NHK-2016-GPL-3.0.txt`. This does not establish rights to NHK sound recordings. Recording files remain fetched per word and their rights remain a separate review item.

The source inputs are copied without modification. Rebuild the deterministic SQLite asset with `python3 scripts/build_dictionary.py --jmdict-source 'EDRDG JMdict_e.gz, YYYY-MM-DD snapshot'`; the script records both input hashes in the database metadata. Re-run it after updating either input. Any generated/combined dictionary database must retain the applicable CC BY-SA attribution and license notices. The app's code is not relicensed by the data license.

## AJT pitch index

- Behavioral/data source: [AJT Japanese](https://github.com/Ajatt-Tools/Japanese) at commit `5729b8c080823eadaa121c76e0f23c55d6a81935`; all three `pitch_accents_formatted.N.csv` parts are ingested in source order.
- Source SHA-256 values (parts 1, 2, 3): `4068078c59667f963922a4c0adae9b56e9a878b41e769bb87249b7236e99d234`, `ddc8a9a92f80ad03834fe4f1e2a352ef4cc85921e96cccab03094376cc17c5c1`, `08a419115a6dafe19631294a56fad064092a62a377478d6263a714ea5c0a4bd1`.
- Transformation: `scripts/build_ajt_pitch.py` preserves the raw expression, headword, reading, row order, numeric accent and nested phonetic annotations while converting AJT's angle-bracket notation to equivalent class spans. It does not infer missing nasalization or devoicing.
- Attribution/licensing: the pinned AJT repository's `LICENSE` is AGPL-3.0 for its software. Its README says the pitch databases use NHK Japanese Pronunciation Accent Dictionary and Kanjium data; the formatted CSV files do not carry a separate data-license declaration in that repository. Do not treat the repository's software license as resolving third-party data rights, or apply Kanjium's CC BY-SA terms to the mixed-source CSV without evidence. Distribution licensing and any additional source attribution remain subject to review before release.
- The generated `pitch_ajt.sqlite.gz` is a derived index, not a fresh source snapshot. Its current compressed size is 36,072,769 bytes and SHA-256 is `e26f89e787843ed3a127c16faa4dc226324f07221a361cd029cfdaf1f84c8e9a`.
