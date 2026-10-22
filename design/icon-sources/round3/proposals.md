# Japanese Anki / icon proposals, round 3

Recommendation: A, Open roundel. It has the strongest identity without asking the launcher to explain the workflow. C is the more explicit pronunciation-led alternative. NEW is intentionally the most abstract.

These are proposals, not approved launcher assets. No mipmap resource or manifest is changed. View `48px-gate.png` and `launcher-context.png` before the 512 px artwork. All comparison-sheet labels use Latin characters.

## A / Open roundel

Silhouette: A heavy off-centre disc is opened across its top and right by a branching counter-form.

The mark is the actual 本 outline from Noto Sans JP, instantiated at weight 700 through the requested fontTools pipeline. 本 keeps the book/root association and the stronger branching structure; 字 would introduce an enclosed counter and a less immediate small silhouette. No Mincho, hand-drawn substitute or font-dependent text is used.

The disc sits lower and to the left of the glyph. The character is genuinely subtracted from the red shape, not printed over it: its upper stem and rightward strokes open the rim. The remaining heavy crescent gives the mark its weight. This uses figure-ground play and one deliberate asymmetric composition, rather than adding a second symbol.

At 48 px, the disc spans 33.75 px and the complete source glyph spans 28.5 px across. The rim openings and branching shape remain visible in both colour and the single-path themed version. The disc's lower-left area is intentional adaptive bleed; the complete glyph is within the central 66% square. All foreground ink also clears each delivered launcher mask.

Trade-off: this remains in the Japanese seal family. The rightmost stroke terminals intentionally run out of red field, so the viewer reads the whole character from its central structure rather than seeing every terminal enclosed in a tidy border. Do not recenter it or close those openings during implementation; that would restore the rejected stamp.

Colours: primary `#9C3329` on surface `#FFF8F5`.

## C / Two beats

Silhouette: Two separated angular forms sit above a broad descending step with softly rounded ends.

The reading is アメ, ame, here representing 雨 (rain), with a high first mora and low second mora. Katakana carries the same pronunciation with markedly simpler geometry than round 2's hiragana あめ. Both glyphs are real Noto Sans JP 700 outlines. This is not a rain illustration: the lower mark represents pitch, not weather or a waveform.

The two glyphs and the two-level contour are the entire icon. There is no viewfinder, bead, accent-colour overlay or size-dependent hidden part. The ink-blue field comes from the app's tertiary role, used for pitch; the kana and contour share the same light token. The contour has one descending junction and optically rounded terminals, with no black points obscuring it.

At 48 px, the kana outlines are approximately 13.86 and 14.17 px high; those heights alone did not rescue round 2. The material change is the reduction in stroke complexity and the complete removal of competing parts. The separate angular glyphs and the high-to-low relationship are visible in the actual native-size render, including monochrome. At 192 px, アメ and its falling contour are plainly legible. The contour is 28 source units thick, or 2.625 px at 48 px. All essentials fit the central 66% square.

Trade-off: this is the most explicit about pronunciation and the least distinctive typographic composition. Japanese readers can read the word; everyone else sees a pair of clear angular forms over a falling step. It does not promise that everyone can read Japanese from a launcher grid.

Colours: surface `#FFF8F5` on tertiary `#354B70`.

## NEW / Lifted shard

Silhouette: A tilted five-sided block hangs over three broad strokes arranged in an open crescent.

A word becomes a valuable fragment removed from a stream: the selected piece is enlarged, lifted upward and cut into an asymmetric five-sided shape. The remaining marks change direction around the empty pocket beneath it. This takes lexical mining literally enough to give the abstraction a point of view, without adding a pickaxe, sparkle, card or selection frame.

The first iteration of this round retained three little dashes on a diagonal rail. It still resembled a pointer above a segmented control and was discarded after native-size review. The delivered version has no straight track, uniform dots, knob, arrowhead or equal-spaced baseline. Its stream is a broken curve of broad chisel strokes, with a visibly larger floating piece.

At 48 px, the lifted piece is approximately 17.63 px wide by 16.69 px high; the stream strokes are about 4.3 px thick. The size hierarchy and the open crescent remain visible when every part is tinted identically. Its combined bounds are within the central 66% square, and the three masks leave all marks intact.

Trade-off: the shape says extraction or a recovered fragment, not a literal Japanese word. Without product context it may read as a floating stone or fragment over an excavated pocket. That abstraction is deliberate; a word glyph would make it another version of A or C.

Colours: primary `#9C3329` and on-surface `#2B1712` on surface `#FFF8F5`.

## Adaptive and themed delivery

Each concept includes:

- `icon-{a,c,new}.svg`: flattened opaque, square, full-bleed 512 px store artwork.
- `icon-*-foreground.svg` and `icon-*-background.svg`: standalone adaptive layers, with identical alignment and canvas.
- `icon-*-mono.svg`: exactly one filled path, including any disconnected subpaths and true counters, with no stroke or colour dependency.
- Colour and monochrome PNGs at native 48, 192 and 512 px.

Every artwork SVG uses `viewBox="0 0 512 512"`. All characters are paths. There are no embedded rasters, external references, live fonts, gradients, shadows or textures. No concept exceeds two foreground colours plus its background. All colours are existing roles in `app/src/main/java/dev/pierre/japaneseanki/ui/FlowTheme.kt`.

Here "central 66%" is checked as the central square from 87.04 to 424.96 on both axes. `safe-zone-preview.png` makes that boundary explicit. The full foreground, including A's decorative disc bleed, is additionally tested against circle, exponent-4 squircle and 22%-corner-radius rounded-square masks; the visual previews use those same mask definitions. This is proposal geometry, not a claim of testing installed launcher motion or OEM-specific scaling.

The monochrome path geometry is the same as the colour foreground's silhouette. A retains the cut-out disc; C retains both the word and the falling contour; NEW retains its size and position hierarchy without relying on red.

## Review files

- `contact-sheet.png`: all three at native 48/192/512 sizes, with the small-size row first.
- `48px-gate.png`: colour and monochrome, all three masks, every icon exactly 48 px.
- `mask-preview.png`: each concept at exactly 192 px in a circle, true squircle and rounded square.
- `launcher-context.png`: each concept at exactly 48 px among 14 assorted-hue placeholder neighbours, separately on light and dark wallpapers.
- `mono-preview.png`: native 192 and 48 px themed examples, light and dark.
- `safe-zone-preview.png`: central-66% guide and A's complete source-glyph bounds.
- `geometry.json`: explicit bounds, palette source and font provenance.
- `verification.json`: output of the runnable automated checks, not an artistic-approval claim.

## Reproduction

The saved glyph outlines are sufficient to rebuild everything without retrieving a font. From this directory:

    nix-shell -p python3Packages.fonttools python3Packages.pillow python3Packages.skia-pathops librsvg --run 'python3 build_proposals.py && python3 verify_proposals.py'

The outline provenance and original pipeline commands are in `glyphs/README.txt`; the Noto Sans JP OFL notice is included. The font itself is not bundled. Build-time path operations perform a real Boolean subtraction for A and normalize the monochrome paths; delivered SVGs have no dependency on those packages.

Automated checks cover SVG independence and palette, exactly one path per themed variant, the essential bounds, all requested PNG sizes and opacity, agreement between separated layers and flattened renders, monochrome silhouette equivalence, masks and review-sheet dimensions. Native-size colour, monochrome, mask and launcher-context artwork has also been visually inspected. The choice of identity remains Pierre's approval decision; nothing has been installed.
