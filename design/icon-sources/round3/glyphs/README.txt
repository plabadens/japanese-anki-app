GLYPH PROVENANCE / ROUND 3

Family: Noto Sans JP
Variable axis instantiated: wght=700
Font source file: /srv/hermes/.hermes/cache/fonts/notosansjp.ttf
SHA-256: c2f3b4d463500a2ddcd3849cded1fceeb9fd6d1c32e6cbecd568453ba50fc68f
License: SIL Open Font License 1.1; see notosansjp-OFL.txt.

These are real outlines generated with the app-icon-design script. They are not
traces of screenshots or substitute hand-drawn glyphs. The source font is not
bundled. All finished SVGs can be displayed without any installed font.

Commands executed from the task worktree:

nix-shell -p python3Packages.fonttools --run 'python3 /srv/hermes/.hermes/skills/creative/app-icon-design/scripts/outline_glyph.py /srv/hermes/.hermes/cache/fonts/notosansjp.ttf 本 icon-proposals-round3/glyphs/hon-noto-700.svg 512 0.14 700'

nix-shell -p python3Packages.fonttools --run 'python3 /srv/hermes/.hermes/skills/creative/app-icon-design/scripts/outline_glyph.py /srv/hermes/.hermes/cache/fonts/notosansjp.ttf ア icon-proposals-round3/glyphs/a-katakana-noto-700.svg 512 0.14 700'

nix-shell -p python3Packages.fonttools --run 'python3 /srv/hermes/.hermes/skills/creative/app-icon-design/scripts/outline_glyph.py /srv/hermes/.hermes/cache/fonts/notosansjp.ttf メ icon-proposals-round3/glyphs/me-katakana-noto-700.svg 512 0.14 700'

The actual run combined these three invocations in one Nix shell, also containing
the build-time Pillow, skia-pathops and librsvg tools.

hon-composed.svg is the same source outline, uniformly scaled and translated to
its final position before Boolean subtraction from A's disc. It is provided so
the complete glyph's safe-zone bounds can be inspected independently of the
intentional rim openings. There is no glyph distortion or terminal redrawing.
