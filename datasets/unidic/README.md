# UniDic (Contemporary Written Japanese)

- Edition: UniDic 2.1.2, MeCab-format binary distribution, as shipped by the
  [`unidic-lite`](https://pypi.org/project/unidic-lite/) 1.0.8 source package (MIT packaging around the
  unmodified UniDic data).
- Upstream data: <https://unidic.ninjal.ac.jp/unidic_archive/cwj/2.1.2/unidic-mecab-2.1.2_bin.zip>.
- Source package: `unidic-lite-1.0.8.tar.gz`, SHA-256
  `db9d4572d9fdd4d00a97949d4b0741ec480ee05a7e7e2e32f547500dae27b245`.
- Copyright: The UniDic Consortium. The upstream package offers GPL, LGPL or BSD licensing; this project
  selects the included BSD license. Original `COPYING`, `AUTHORS`, `BSD`, `GPL` and `LGPL` notices are
  preserved under `app/src/main/assets/licenses/unidic/`.
- Runtime files only: `sys.dic`, `unk.dic`, `matrix.bin`, `char.bin`, `dicrc` (260 MB uncompressed,
  47.5 MB compressed). Training and evaluation material is not shipped.

## Why this data set

Earlier releases downloaded a 446 MiB UniDic CWJ 3.1.1 runtime archive on first run, then unpacked
724 MB. `unidic-lite` is the same MeCab UniDic format with a smaller, older lexicon (2.1.2), small
enough to ship inside the APK. The fields this app reads (`pos1`–`pos4`, `lForm`, `lemma`, `orth`,
`pron`, `orthBase`) sit at identical indexes in both data sets, so `native_mecab.cpp` needs no change.

Trade-off, measured against 3.1.1 on the same MeCab 0.996: ordinary sentences tokenize identically.
Newer vocabulary is weaker — `エモい`, `タイパ` and `サブスク` split into pieces, while `ChatGPT` stays
one token. Common words, conjugations and readings match.

## Regenerating the bundled asset

`app/src/main/assets/unidic-lite.zip` (SHA-256
`11dbf1ceeb8dc052b41ac6519b248b17fafbd7a34a6b7aef184d5d54bdd379bd`) was produced deterministically
from the source package:

```sh
curl -L -o unidic-lite-1.0.8.tar.gz \
  https://files.pythonhosted.org/packages/source/u/unidic-lite/unidic-lite-1.0.8.tar.gz
tar xzf unidic-lite-1.0.8.tar.gz
python3 - <<'PY'
import os, zipfile
src = "unidic-lite-1.0.8/unidic_lite/dicdir"
out = "app/src/main/assets/unidic-lite.zip"
files = ["char.bin", "dicrc", "matrix.bin", "sys.dic", "unk.dic"]
with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for name in files:
        info = zipfile.ZipInfo("unidic-lite/" + name, (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        info.external_attr = 0o100644 << 16
        with open(os.path.join(src, name), "rb") as f, z.open(info, "w") as w:
            while chunk := f.read(1 << 20):
                w.write(chunk)
PY
```

`BundledDictionary` pins every entry name and uncompressed size, writes a version marker into the
unpacked directory, and replaces an older extraction (including the retired 3.1.1 data) on next launch.
`BundledDictionaryTest` fails if the asset and the pins diverge.
