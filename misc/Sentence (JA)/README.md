# Sentence (JA)

A recognition card for Japanese sentence mining: a screenshot with the
sentence as a subtitle, and an answer plaque that fills in underneath it.

Import with [gomi](https://github.com/Ajatt-Tools/gomi):

```
gomi import "templates/Sentence (JA)"
```

Review changes with the repo's preview tool before importing:

```
python preview/server.py      # → http://localhost:8002
```

## The shape

```
┌─────────────────────────┐
│                         │
│        screenshot       │   ← stage: identical on both sides
│  ─────────────────────  │
│  仲裁は僧侶の仕事なんだよ。 │   ← subtitle on a scrim, readings hidden
├─────────────────────────┤   ← without an Image: no band, just paper
│  僧侶      単語⏵ 文⏵ 隠す │ ┐
│  ソーリョ                │ │
│  priest; monk; bonze    │ ├ plaque: only the back has it
│  Show translation       │ │
│  Source: …              │ │
│  Jisho·Images·Search  #… │ ┘
└─────────────────────────┘
```

The card is a full-height column that holds its content in the middle, so a
large window suspends it rather than pinning it to the top. The stage lands
in the same place on both sides — measured at `top: 327` front and back on a
1265px-tall window, and zero shift at every card width from 240px to 560px.
Flipping only fills in the plaque underneath.

Three details make that true:

- **Back-only content is `visibility:hidden`, so it is laid out but not
  shown.** That reservation is what lets the card be centred without the
  flip moving anything: the column is the same height on both sides.
- **`#qa` paints nothing — `#stage` and `#plaque` carry the background.**
  The centring column is full-viewport-height, so painting the card colour
  on it turns the card into a full-height slab over Anki's own backdrop.
  Painting the parts instead means the card hugs its content, and the
  reserved-but-hidden plaque on the front paints nothing at all, because
  `visibility:hidden` hides an element's background along with it. This is
  the only reason `#plaque` exists as a wrapper around `#answer` and the
  footer: one box to paint, one box to hide.
- **The answer is shaped on both sides.** The pitch diagram, the parsed
  source line, the hint label and the split tags are all built on the front
  too, hidden — otherwise the front reserves the wrong height and the flip
  nudges the card. Only the replay buttons (which do not exist on the front)
  and the event listeners are back-only.
- **The subtitle exists twice** — `.sentence-plain` (`{{furigana:SentKanji}}`,
  no readings) and `.sentence-ruby` (`{{furigana:SentFurigana}}`). The front
  shows the first, the back the second, at the same line-height.

With an `Image`, the stage is a dark band: the screenshot with the sentence
as a subtitle over a scrim. Without one it is simply the top of the card —
the normal paper background, the sentence centred on it, and a hairline
under it once the answer appears. Same component, same type, no separate
fallback path.

## Design decisions

Each of these reverses or refines something from the round-5 mockup
(`preview/archive/sentence-ja-redesign-proposals/`), and the reasoning is
kept because most of them are non-obvious.

**The image is on the front.** Front and back share the stage; earlier
rounds put the screenshot on the answer side only.

**The SVG pitch diagram is back, on this card's own terms.** Round 5's mora
tokenizer and step-line renderer are ported into `front.html` as
`tokenizeMorae()` + `buildPitchDiagram()` — sharp segments, one dot per mora,
kana under their own dots, which is the variant round 4's gallery settled on.
Two things changed in the port: the tokenizer reads
`.high/.low/.high_drop/.low_rise` by class rather than `:scope > span`, so it
works whether `VocabPitch` wraps its morae in a `.pitch` span (samples 1 and
3) or leaves them bare (sample 2); and the `mark` field (devoiced, nasal) now
survives the small-kana merge — round 5 destructured it away, so a devoiced
りょ would have lost its hollow dot. Cards with an empty `VocabPitch` fall
through to the template's plain-kana branch and are left alone.

**The pill rules are scoped under `#controls` on purpose.** Anki's
`webview.css` styles every bare `<button>` and every `.replay-button`:
`button { padding:8px 10px; margin:0 4px; background:…; border:1px solid …;
font-weight:500 }`, `button:hover { background:linear-gradient(…);
border:1px solid … }`, `button:active { box-shadow:inset … }`,
`.replay-button { margin:3px }`, and `* { box-sizing:content-box }`.
`button:hover` is (0,1,1) and beats a bare `.pill` (0,1,0), so unscoped the
furigana button took Anki's grey gradient on hover while the two anchors
only changed colour, and the differing margins made the row twitch. Every
property Anki touches is pinned in both states, so hover changes nothing
but the colour. **None of this is visible in the preview tool**, which has
no such stylesheet — it has to be reasoned about, or simulated by injecting
those rules.

**Only the first reading is drawn.** `VocabPitch` can hold several accepted
readings, comma-separated, each closed by its own `.pitch_number` — 丈夫
ships three (ジョーフ 1, ジョーブ 0, マスラオ 0). Tokenising every mora span
in the field ran them together into one nonsense contour, so the tokenizer
stops at the first `.pitch_number` and the rest of the field is dropped.
The accent colour already agreed with this: `pitchPattern()` reads the first
digit out of `{{text:VocabPitch}}`, which is the first reading's number.
The "Multiple pitch patterns" sample covers it.

**The play glyph is the triangle alone.** Anki ships `svg.playImage` as a
triangle inside a ring; the ring is `display:none` and the triangle is
scaled 1.4× about its own centre to reclaim the space, which needs
`transform-box: fill-box` — an SVG `transform-origin` is otherwise the
user-space origin rather than the shape — and `overflow: visible` so the
viewBox does not clip it.

**No ruby on the headword.** Round 5 pulled the headword's `rt` out of flow
with `position:absolute; top:.37rem` so the buttons would centre on the kanji
line. With `{{kanji:VocabKanji}}` the headword is kanji only and the reading
is the diagram's own label row, one line below. `align-items:center` is then
simply correct, and a magic number and a duplicate reading both disappear.

**One pitch colour per card, shared.** The target word in the sentence and
the reading under the headword take the same pattern colour, so the
colour-coding reads as one thing. Five `[pitch-pattern="…"]` rules total.

**Two palettes, not four.** Round 5 carried paper tones plus `-dark` tones
plus rebinds on the front plaque and on the caption. Here there is one light
set and one dark set, and the dark set is claimed by `.nightMode` *and* by
`#stage:has(#image)` — which is what keeps the subtitle band dark in both
themes without a third palette, while a stage with no screenshot just
inherits whichever palette is in force.

**The pills are Anki's own replay buttons.** Round 5's 単語 ▶ / 文 ▶ pills
were custom `<button>`s driving `new Audio()`. Here the pill *is*
`a.replay-button` — Anki's markup, Anki's `pycmd` playback — restyled: border,
radius, `currentColor`, and its `svg.playImage` sized to `0.95em`. The script
moves the 単語 / 文 label *inside* the anchor, so the whole pill is the
control rather than a label sitting beside a separate hit target. The 読み
toggle wears the same `.pill` recipe, so all three match without a second set
of rules.

**Audio moved to `back.html`.** Anki collects `[sound:…]` from the rendered
side, so a copy in `front.html` would have autoplayed the answer on the
question side. `back.html` emits the buttons and the script lifts them into
the head row.

**Scrim is solid behind the text.** Round 5's gradient stopped at a
percentage, so a two-line caption pushed its first line into the feathered
zone. It now feathers across the top padding only — `calc(100% - 2.4rem)` —
and holds for any number of lines.

**Tags are back.** Round 5 dropped them; production has always shown them.
They sit in the footer opposite the links, quiet, no `#` prefix.

**The furigana toggle labels the action, not the state.** 隠す while readings
show, 読み while they are hidden. Round 5 resolved this the other way; both
are two glyphs so neither shifts the row. Flip it back in `front.html` if you
prefer the state reading.

**The card hangs in the middle of a large viewport.** `#qa` is a full-height
flex column with `justify-content: safe center`, so a 27" monitor suspends
the card instead of stranding it at the top. `safe` keeps the top edge
reachable when the content is taller than the window.

**Portrait screenshots no longer blow up the card.** `#qa` is a size
container and the image is capped at `min(55vh, 62cqw)` — read off the card
width, not the window, so the cap holds at any card size. A 480×854 still
renders as a 560×347 stage with the image pillarboxed onto it, instead of an
854px wall. The `vh` term covers the short-window case on desktop.

**Space is reserved before anything loads.** `aspect-ratio: auto 16 / 9` on
the image means "the real ratio once it is known, this one until then" — the
stage measures 560×315 with `naturalWidth === 0`, so a 16:9 still causes no
shift at all and other ratios shift by the difference rather than from zero.
Both text faces are `<link rel="preload" as="font" crossorigin>`-ed at the top
of `front.html` so the fetch starts before the CSS is parsed, and
`font-display` is `block` rather than `swap`: the faces are local Anki media
and resolve in milliseconds, so blocking briefly beats reflowing from a
fallback's metrics. The script also sets `decoding="sync"` and
`fetchPriority="high"` on the screenshot.

**The Source: line is parsed.** `Source: Sousou_no_Frieren EP17 (06m46s573ms)`
becomes `Sousou no Frieren · EP17 · 6:46.573` — underscores out, trailing
parenthetical and episode marker split off as their own segments, subs2srs
timestamps reformatted losslessly. A `Notes` field that isn't a `Source:` line,
or that carries markup (furigana), is left exactly as written; sample 3's prose
note passes through untouched.

**Media is referenced by bare filename.** Anki serves `collection.media` at
the document root, so `url("/media/_NotoSerifJP-VF_wght.woff2")` asks for a
media file literally named `media/_NotoSerifJP…` and 404s — the fonts simply
did not load in Anki, while `preview/server.py` served that path happily and
hid the problem. `Word (JA)` had it right all along. The preview server now
resolves `_*.woff2` out of `media/` from any path, so the two environments
agree.

These names are mixed case, and `media/` is the canonical spelling. If the
fonts 404 in Anki, the name is being matched **case-sensitively** against
`~/.local/share/Anki2/<profile>/collection.media/`: check the exact spelling
there, and check which profile is actually active (newest `collection.anki2`).
The files can be present, byte-identical, and still 404 under a different
case. Note that renaming them in `collection.media` only sticks if the
collection agrees — a sync will restore whatever spelling AnkiWeb holds, and
Anki's media DB shows the losing spelling as a tombstone (`dirty=1`, empty
`csum`) beside the live row.

**The card carries a drop-shadow, not a box-shadow.** `filter: drop-shadow()`
on `#qa` traces what is actually painted, so it follows the stage and plaque
as one silhouette rather than outlining the full-height column — and on the
front, where the plaque is hidden, it traces the stage alone. Nothing
suppresses it when the card fills the viewport, because there is then no
backdrop beside the card for it to fall on.

**Phones start the card at the top.** Centring is a large-viewport
behaviour; on a phone the card fills the screen anyway. Top-aligned, nothing
needs reserving — content added below cannot move what is above it — so
`.mobile` drops the plaque from the front outright rather than hiding it,
which also spares the question side a scrollable empty region.

## Typography

One scale, five steps, used everywhere:

| token     | size   | used by                          |
|-----------|--------|----------------------------------|
| `--fs-xl` | 1.75rem| headword                         |
| `--fs-lg` | 1.25rem| the sentence                     |
| `--fs-md` | 1rem   | meaning, pitch reading           |
| `--fs-sm` | 0.9rem | hint link and the translation    |
| `--fs-xs` | 0.8rem | notes, links, tags, controls     |

Round 5 had notes at `.78rem` next to links at `.8rem` — two sizes close
enough to read as a mistake rather than a distinction. They are one size
now, separated by position instead.

Spacing is one horizontal inset (`--pad`) for the stage and the plaque
alike, and one vertical step (`--flow`) applied by a single
`#answer > * + *` rule, with one deliberate exception: the pitch contour
hugs the headword at `.15rem`.

`ruby-align: start` is on every `<ruby>` — the jukugo-spreading fix from
round 1 that never made it into production.

## Contrast

Measured in Firefox against computed backgrounds, both themes:

| | light | dark |
|---|---|---|
| headword / meaning | 17.3 | 16.3 |
| pitch reading (atamadaka) | 6.2 | 8.6 |
| links | 9.2 | 8.5 |
| notes / tags / controls | 6.3 | 7.7 |
| subtitle on the scrim | 15.3 | 15.3 |
| target word on the scrim | 8.1 | 8.1 |

Everything clears AAA for body text.

## Checked

Measured in Firefox through the preview tool on all three samples, at the
point this landed. Worth re-running after any layout change — several of
these are properties the card can lose silently:

- stage lands identically front↔back, and the card is centred: `top: 327` in
  a 1265px window on a 560px card
- zero shift at 560 / 320 / 280 / 260 / 240px card widths — below 300px the
  controls take their own row on both sides, which is what keeps the
  reservation honest there
- front carries no replay anchor, no `<audio>`, and no sound tag
- no-image card: stage background resolves to `rgb(251, 250, 246)` (paper)
  with `--ink` text and no scrim, in light mode; follows night mode in dark
- against a simulated Anki backdrop (`#2c2c2c` night, `#fff` light) the card
  hugs its content: `#qa` computes to `rgba(0, 0, 0, 0)` and the front shows
  the stage alone, with nothing painted in the space the plaque reserves
- all three faces reach `loaded`, and the requests that satisfy them are the
  preload links — so the preloads are being used, not duplicated
- `.mobile`: `justify-content` is `flex-start`, the stage sits at `top: 0` on
  both sides, and the plaque is `display: none` on the front / `block` on the
  back
- readings toggle changes nothing but the readings
- 480×854 portrait test image → stage `347.87`, not `854`; `max-height`
  resolves to `347.2px` = 62cqw of a 560px card, and to `198.4px` at 320px
- image box reserved at `560×315` while `naturalWidth === 0`
- no horizontal overflow at any tested width (`scrollWidth === clientWidth`)
- diagram tokenises 僧侶 to 3 morae — ソ high, ー low, リョ low — and 勉強 to
  4; the りょ label renders at `.85em` so the two-character mora still centres
- 丈夫, which carries three readings, draws only ジョ/ー/フ and is coloured
  atamadaka from the first reading's accent number
- the play glyph has no ring, and the triangle measures 10.0×10.9 in a
  12.1×12.1 box — it fills the space the ring used to occupy
- with Anki's `button` / `.replay-button` rules injected into the preview
  *after* the template CSS (worse than the real cascade order), the three
  pills keep `margin: 0`, one padding, one border width and `border-box`;
  hovering any of them moves nothing and changes only colour and
  border-colour, `rgb(98,93,81)` → `rgb(24,22,15)`, identically
- `Source:` line parses to three segments; `<ol><li>book</li><li>volume</li></ol>`
  renders as `book; volume`
- hint reveals in place at the same size and style as the link it replaces
- separator dots between links are not underlined
- contrast in both themes: pills 6.28 / 7.71, diagram 6.19 / 8.57, subtitle
  15.3, everything else above

## Not checked in Anki itself

Everything above was measured in the preview tool, which reproduces Anki's
markup but is not Anki. Two things deserve a look after the first import:
the `a.replay-button` pill styling, and whether the font preloads are
honoured from inside a card template (Chromium processes `<link rel=preload>`
in the body, but Anki's webview is its own thing). Devoiced and nasal morae have a hollow-dot / coloured-dot hook in the
diagram, but no sample exercises it.
