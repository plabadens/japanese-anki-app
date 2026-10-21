#!/usr/bin/env python
"""Build the portable design study from its local template and assets (stdlib)."""
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def build():
    template = (ROOT / "flow.template.html").read_text()
    fonts = json.loads((ROOT / "assets/font-manifest.json").read_text())
    rules = []
    for font in fonts:
        data = base64.b64encode((ROOT / "assets" / font["file"]).read_bytes()).decode()
        rules.append(
            "@font-face{font-family:'%s';font-style:normal;font-weight:%s;"
            "font-display:swap;src:url(data:font/ttf;base64,%s) format('truetype')}"
            % (font["family"], font["weight"], data)
        )
    photo = base64.b64encode((ROOT / "assets/textbook-specimen.jpg").read_bytes()).decode()
    html = template.replace("__FONT_CSS__", "\n".join(rules)).replace(
        "__PHOTO_DATA__", "data:image/jpeg;base64," + photo
    )
    # Carry redistribution notices inside the standalone file as well as assets/.
    notices = "\n\n".join(
        (ROOT / "assets" / name).read_text()
        for name in ("notosansjp-OFL.txt", "roboto-OFL.txt")
    ).replace("--", "—")
    html = html.replace("</head>", "<!-- Embedded font licenses:\n" + notices + "\n-->\n</head>")
    assert "__FONT_CSS__" not in html and "__PHOTO_DATA__" not in html
    output = ROOT / "flow.html"
    output.write_text(html)
    print(f"Built {output} ({output.stat().st_size:,} bytes); assets embedded.")


if __name__ == "__main__":
    build()
