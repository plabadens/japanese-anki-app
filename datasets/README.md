# Bundled linguistic data inputs

These are source inputs for the offline dictionary database. Do not replace them with proprietary Rikaitan downloads.

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

The source inputs are copied without modification. Any generated/combined dictionary database must retain the applicable CC BY-SA attribution and license notices. The app's code is not relicensed by the data license.
