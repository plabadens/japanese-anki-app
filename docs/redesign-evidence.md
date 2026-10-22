# Redesign evidence: AJT and image encoding

**How to use it:** EVIDENCE. Research that supports three `SPEC.md` decisions (image encoding, AJT
pitch HTML, recording matching); it is not a claim that the app implements them, and its
"still unverified" list remains unverified unless a later record says otherwise.

Research supporting `SPEC.md`, not proof that the Android redesign is implemented. No application code or installed card template was modified in this investigation.

## 1. WebP choice and quality experiment

**Decision: platform lossy WebP, quality 75, maximum 1080 px long edge, no upscaling.** Android exposes `Bitmap.CompressFormat.WEBP_LOSSY` directly. This avoids the existing `AvifWriter` integration and native-encoder contingency. AVIF may achieve smaller files at some visual tradeoffs; it is not claimed universally worse.

Actual host experiment: Pillow 12.3.0, WebP method 4, AVIF speed 6 / 4:2:0, RGB, EXIF transpose, Lanczos resize. Six quality settings per codec, two fixtures; all 24 outputs reopened, dimensions and measured file sizes verified. Quality values are **not equivalent between codecs**. Single-run encoding timings are not a benchmark.

| Quality | Book WebP KiB | Book AVIF KiB | Sign WebP KiB | Sign AVIF KiB |
|---|---:|---:|---:|---:|
| 35 | 11.588 | 5.701 | 21.361 | 14.833 |
| 45 | 13.049 | 7.850 | 23.588 | 19.140 |
| 55 | 14.701 | 10.717 | 25.090 | 23.562 |
| 65 | 16.525 | 14.654 | 26.854 | 27.999 |
| **75** | **18.215** | **21.275** | **29.023** | **33.854** |
| 85 | 26.217 | 28.081 | 35.996 | 39.456 |

Fixtures:
- Repository `misc/Sentence (JA)/samples/book.jpg`: decorative, already JPEG-compressed, 512×342, deliberately not upscaled.
- Generated clean Japanese sign: 2160×2880 → 810×1080, varied text sizes/colors, Noto Sans CJK JP. Synthetic, **not** a real Japanese camera photograph or OCR benchmark.
- Dimension checks also cover 2880×2160 → 1080×810 and 320×240 unchanged.

Visual review: Q35 retains basic photographic recognition and medium/large sign text, but smooths texture and introduces edge artifacts. Q55 is an aggressive candidate. Q75 is the conservative starting preset, not a demonstrated universal optimum. Small glyphs are already compromised by downscaling in the uncompressed reference; higher codec quality cannot recover lost source detail. OCR must precede note-image compression.

At Q75, measured WebP sizes are 18,652 bytes (book) and 29,720 bytes (sign). Do not extrapolate those sizes to noisy full-resolution camera captures. Android's encoder and actual Anki WebView output remain untested in this experiment; qualify Q75 there with real camera fixtures before release. No physical-phone-only gate is implied: imported real captures can be tested in the emulator.

Full measurements: [image-quality-sweep.csv](archive/evidence/image-quality-sweep.csv). Contact sheets: [book](archive/evidence/book-photo-webp-detail-sheet.png), [Japanese sign](archive/evidence/synthetic-sign-webp-detail-sheet.png). The latter shows 2× nearest-neighbor crops, not native viewing size.

Disposable experiment script/output directory: `/srv/hermes/.hermes/cache/scratch/anki-image-encoding-spike/`. That scratch directory may expire; the measurements and WebP visual evidence linked above are preserved in this repository. No new production dependency on Pillow/AVIF is introduced.

Official API: https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat#WEBP_LOSSY

## 2. AJT HTML pitch contract

Inspected AJT Japanese commit **`5729b8c080823eadaa121c76e0f23c55d6a81935`**. The selected class-style export is `html_and_number` with `HTMLPitchPatternStyle.none`—`none` means CSS classes, not absent output.

- Pitch classes: `low`, `low_rise`, `high`, `high_drop`.
- Nested phonetic classes: `nasal`, `handakuten`, `devoiced`.
- A trailing `pitch_number` span carries the accent number, including zero.
- Do not copy multi-result joining or desktop fallback behavior: the app exports one selected pronunciation.

Concrete source-backed fixture: **悪逆 / アクギャク / accent 0**, `pitch_accents_formatted.3.csv:3845`:

```html
<span class="low_rise">ア</span><span class="high">ク<span class="nasal">キ<span class="handakuten">&#176;</span></span>ャク</span> <span class="pitch_number">0</span>
```

The nasal wrapper surrounds キ and its mark; the following small ャ is outside it. Preserve this structure. The mark is not another mora. Nasal and devoiced are different source facts; the previous bespoke hollow-dot contract must not conflate them.

AJT's merged bundle contains NHK and Kanjium data. Nasal/devoicing tags are documented as NHK-only; its stored runtime `source` tracks bundled/user, not reliable per-row NHK/Kanjium provenance. An unmarked record is not proof of absent nasalization. SPEC chooses this one pinned bundle, preserves all supplied marks, and documents incomplete coverage; it does not invent a new fully annotated dataset or add another runtime provider.

Primary source links:
- [Class/style mapping and NHK-only tag comments](https://github.com/Ajatt-Tools/Japanese/blob/5729b8c080823eadaa121c76e0f23c55d6a81935/japanese/pitch_accents/styles.py)
- [Rendering and number output](https://github.com/Ajatt-Tools/Japanese/blob/5729b8c080823eadaa121c76e0f23c55d6a81935/japanese/reading.py)
- [Bundled annotated fixture](https://github.com/Ajatt-Tools/Japanese/blob/5729b8c080823eadaa121c76e0f23c55d6a81935/japanese/pitch_accents/res/pitch_accents_formatted.3.csv)
- [Database provenance and lookup](https://github.com/Ajatt-Tools/Japanese/blob/5729b8c080823eadaa121c76e0f23c55d6a81935/japanese/database/pitch_buddy.py)

## 3. Existing template incompatibility

`misc/Sentence (JA)/Recognition/front.html:134–151` selects the outer pitch spans, checks phonetic classes on those same spans, then splits `textContent` into characters. AJT's nasal/devoicing classes are nested descendants. The current parser therefore loses those marks and can treat the nested degree symbol as a separate mora. Its CSS already distinguishes hollow devoicing dots from nasal-colored dots; that does not fix the parser.

This is source-inspected behavior, not an Android rendering test. An installed-template correction is required for full AJT fidelity; the app must not silently rewrite templates or falsify exported markup. Add the fixture above to both native rendering and final Anki rendering tests. Also test accent numbers ≥10 and final drops; the template has other known limitations.

## 4. Word recordings and matching

NHK-2016 pronunciation index remains pinned at **`b0207d240f0f28cdc2dd491dd83343c3a5630119`**. Verified directly:

- `headwords["悪逆"]` contains `20170630161146.ogg`.
- That file's `kana_reading` is `アクキ゚ャク`, `pitch_pattern` is `アクキ゚ャク━`, and `pitch_number` is `0`.

The index supplies useful reading/accent metadata, but this investigation did not establish complete devoicing annotations. The recording was not auditioned. AJT searches audio independently of selected pitch rows; matching source names or result ordering does not prove alignment. SPEC requires actual normalized reading/accent matching and rejects unresolvable/conflicting candidates rather than claiming an identity join exists.

- [Pinned index](https://github.com/Ajatt-Tools/nhk_2016_pronunciations_index/blob/b0207d240f0f28cdc2dd491dd83343c3a5630119/index.json)
- [AJT audio matching](https://github.com/Ajatt-Tools/Japanese/blob/5729b8c080823eadaa121c76e0f23c55d6a81935/japanese/audio.py)

No claim is made that add-on/index licensing establishes recording rights. Preserve notices and review code/data reuse separately.

## 5. Furigana

AJT combines dictionary information with MeCab rather than blindly trusting morphology alone: whole-text dictionary lookup, segmentation, reading candidates, inflection adjustment, and bracket formatting. Its formatter preserves okurigana and can split compounds, such as `食[た]べた` and `取[と]って 置[お]き`.

Reuse those behavioral fixtures, not the desktop source-manager architecture. Unlike AJT's permissive unchanged-text behavior on misses, this app blocks incomplete required readings and provides targeted correction. Native Compose ruby and exported bracket-furigana must represent the same spans/readings.

- [AJT generator](https://github.com/Ajatt-Tools/Japanese/blob/5729b8c080823eadaa121c76e0f23c55d6a81935/japanese/furigana/gen_furigana.py)
- [Pinned MeCab formatter](https://github.com/Ajatt-Tools/mecab_controller/blob/3d17efbc60829bc87e1c2ad9fdf20dc4de36d7f2/format.py)

## 6. Still unverified

Google Chirp 3 HD hinted-speech audition and cloud voice listing, OpenRouter translation-model validation, Android Q75 output, API-37 app execution, modified-template rendering, actual recorded-audio playback/alignment, licensing compliance, and SDK privacy behavior are not established by this documentation work. SPEC §15 keeps these explicit rather than converting researched capabilities into false passes.
