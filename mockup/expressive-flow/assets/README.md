# Design-study assets

`textbook-specimen.jpg` is an authored illustrative textbook scene for the UI mockups. It is not a real photograph, OCR output, or quality benchmark. The Japanese fixture sentence is 図書館で本を読みました。.

`textbook-landscape.jpg` is a 4:3 landscape crop of the same authored specimen. It exists only to exercise landscape-photo display inside the portrait phone; it is not a second photographed scene or a camera sample.

Roboto and Noto Sans JP font files were retrieved using the Google Fonts CSS API. Exact fetched font URLs and weights are retained in `font-manifest.json`. The Japanese subset includes the study's kanji and complete kana coverage; arbitrary Japanese user input is not fully covered by this offline design subset. Production must use platform/full Japanese font support.

Both font families are distributed under the SIL Open Font License. Their notices are included as `roboto-OFL.txt` and `notosansjp-OFL.txt` and embedded in the portable HTML.

The HTML embeds font and specimen bytes; it needs no network connection to run. No supplied user photograph was uploaded or sent to an external image service.
