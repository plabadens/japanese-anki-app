# Reference template for v0.3 (corrected pitch parser)

**How to use it:** REFERENCE, not a v0.3 snapshot. `Recognition/front.html` here is the corrected
parser for the AJT HTML the app exports (nested nasal/handakuten/devoiced marks, accent numbers of
ten or more); `parser-test.mjs` proves it. The copy in `misc/Sentence (JA)/` does **not** carry this
fix, so use this one when validating `SPEC.md` §8 rendering.

`misc/Sentence (JA)/` is the reference note type. Its front template parses the exported
`VocabPitch` HTML to draw the pitch diagram, and that parser could not read the field as the
app now writes it:

* Phonetic marks are **nested** in the source records. 悪逆 ships
  `<span class="high">ク<span class="nasal">キ<span class="handakuten">&#176;</span></span>ャク</span>`.
  The old parser selected only level spans, so the `nasal`/`handakuten`/`devoiced` marks were
  never seen, and the `&#176;` degree symbol inside the handakuten span was counted as an extra
  mora (five columns for a four-mora word).
* `pitchPattern()` read the accent number with `/\d/`, so accent 10 or 11 was read as 1 and
  labelled atamadaka.

`Recognition/front.html` here is the corrected template. Only those two functions changed; CSS,
`back.html`, field names, and the card template list are untouched.

## What changed

1. `tokenizeMoraeHtml()` walks the HTML with a class stack, so marks are inherited from
   enclosing spans, and drops annotation characters (`U+00B0 °`, `U+309A`, `U+309B`) instead of
   drawing them as morae. Contracted kana still join one mora; the scan still stops at
   `.pitch_number`.
2. `pitchPattern()` reads `/\d+/` and therefore handles accent numbers of ten or more.

The blocks are marked `@test-begin`/`@test-end` so `parser-test.mjs` runs the shipped code, not a
copy of it.

## Test

```
nix shell nixpkgs#nodejs -c node docs/v0.3-reference-template/parser-test.mjs
```

Expected: `5/5 parser fixtures passed` (nested nasal handakuten, devoiced, contracted kana,
accent ≥10, odaka vs nakadaka).

## Install on the device (without silently changing the installed note type)

The app never installs or edits note types; the user does this in AnkiDroid.

1. In AnkiDroid, open **Manage note types** (⋮ → Note types, or from the deck screen:
   ⋮ → Manage note types).
2. Either:
   * **Preferred:** duplicate `Sentence (JA)` (long-press → Duplicate), rename it
     `Sentence (JA) v0.3`, then open **Cards → Recognition → Front template** and replace the
     whole template with `Recognition/front.html` from this directory. Leave the back template
     and the shared CSS as they are (the app's exported fields are unchanged).
   * Or update the existing `Sentence (JA)` front template the same way if you accept editing
     it. This directory does not modify anything on the device by itself.
3. Confirm the note type has exactly these fields (order does not matter, the app maps by name):
   `SentKanji`, `SentFurigana`, `SentEng`, `SentAudio`, `VocabKanji`, `VocabFurigana`,
   `VocabDef`, `VocabPitch`, `VocabAudio`, `Image`, `Notes`, `fm_focus_words`, `fm_new_words`,
   `fm_seen_words`. `misc/Sentence (JA)/template.json` is the field list to copy from.
4. In the app: **Settings → Note type** → pick the note type you just prepared.
5. Verify with a sent note before relying on it:
   * 悪逆 / アクギャク / accent 0: four dots, nasal dot on キャ, no separate `°` column.
   * 志向 / シコウ / accent 0: devoiced dot on シ.
   * 急性アルコール中毒 / accent 10: the pattern is nakadaka, not atamadaka.

Rendering evidence for the exported HTML in AnkiDroid is **NOT RUN** in this candidate: a note
could not be sent without the Google/OpenRouter credentials that complete its required audio and
translation. The parser itself is covered by the fixture test above and by the Kotlin
`AjtPitchHtml` tests, which read the same source markup.
