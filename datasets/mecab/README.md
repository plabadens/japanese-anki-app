# MeCab

- Version: 0.996, upstream commit `5a7db65493a0b57d5fc31734e65300320aaf94c8` from [taku910/mecab](https://github.com/taku910/mecab).
- Nix source hash: `sha256-elB0Zr8DDkw3IZvvqVG+OBspZxFLPnvUSM9SRSILYWs=`.
- License: BSD; original `BSD`, `COPYING`, and `AUTHORS` files are preserved under `licenses/`; the BSD notice is bundled with the app.
- MeCab is built for `arm64-v8a` and `x86_64` by the Android NDK. The app downloads the verified UniDic CWJ 3.1.1 runtime archive on first use, extracts it to private storage and loads it through JNI.
- The native bridge reads surface form, UniDic orthographic base (`orthBase`) for dictionary lookup, lemma reading (`lForm`), surface pronunciation, and part of speech. Using `orthBase` avoids lexical lemmas such as `東京` being returned as katakana; Kotlin converts readings to hiragana.
- Android downloads the 426 MiB runtime archive on first use, verifies its build-pinned hash and archive contents, then unpacks about 725 MB with visible progress. Tokenization and target lemma lookup are wired in the editor; JNI behavior still requires device verification.
