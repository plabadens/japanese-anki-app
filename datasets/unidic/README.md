# UniDic (Contemporary Written Japanese)

- Edition: NINJAL UniDic CWJ 3.1.1, MeCab-format UniDicMA.
- Official source: <https://clrd.ninjal.ac.jp/unidic_archive/cwj/3.1.1/unidic-cwj-3.1.1.zip>.
- Server `Last-Modified`: 2022-09-06.
- Source archive SHA-256: `6f547a3e715639a3e0178a22df94223a147d95aceb112a590f2d684c644ad310`.
- The archive reports 879,222 short-unit entries and 228,315 lexemes. It includes the precompiled MeCab dictionaries, training/evaluation material, and licenses.
- Copyright: The UniDic Consortium and Teruaki Oka. The upstream package offers GPL, LGPL, or BSD licensing; this project selects the included BSD license for distribution. Original `COPYING`, `AUTHORS`, `BSD`, `GPL`, and `LGPL` notices are preserved under `licenses/`.
- This app packages only the runtime files `sys.dic`, `unk.dic`, `matrix.bin`, `char.bin`, and `dicrc`; training and evaluation data are excluded. `scripts/package_unidic.py` extracts those files into a deterministic runtime archive (SHA-256: `27d4301190c334aea179f6c644463701525951ad7678e7633e21a9e1bfce4210` with the pinned Nix Python/zlib).

The source archive is fetched by the pinned Nix build input rather than checked into Git because it is over 500 MB. The Nix input hash and official URL pin the exact data. Rebuild the runtime archive with:

```sh
python3 scripts/package_unidic.py --archive /path/to/unidic-cwj-3.1.1.zip --output /path/to/unidic-runtime.zip
```
