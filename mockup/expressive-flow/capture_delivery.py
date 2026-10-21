#!/usr/bin/env python3
"""Capture the current offline design board and a landscape-photo fixture in the portrait phone."""
import argparse
import base64
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil

from verify_flow import Browser, sha256

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--html', type=Path, default=ROOT / 'flow.html')
    parser.add_argument('--output', type=Path, default=ROOT / 'delivery')
    parser.add_argument('--scratch', type=Path, default=Path('/srv/hermes/.hermes/cache/scratch'))
    parser.add_argument('--chromium', default=shutil.which('chromium'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    browser = Browser(args.html.resolve(), args.chromium, args.scratch, ROOT / 'assets')
    captures = []

    def capture(name, selector, theme, photo_variant):
        bounds = browser.evaluate("""(()=>{const r=document.querySelector(%s).getBoundingClientRect();
return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1}})()""" % json.dumps(selector))
        result = browser.call('Page.captureScreenshot', format='png', clip=bounds,
                              captureBeyondViewport=True, fromSurface=True)
        path = args.output / name
        path.write_bytes(base64.b64decode(result['data']))
        captures.append({'file': name, 'sha256': sha256(path), 'bytes': path.stat().st_size,
                         'bounds': bounds, 'theme': theme, 'photoVariant': photo_variant})

    try:
        browser.call('Emulation.setDeviceMetricsOverride', width=1440, height=1240,
                     deviceScaleFactor=1, mobile=False)
        browser.evaluate("designStudy.setView('overview');document.fonts.ready.then(()=>true)")
        assert browser.evaluate("[...document.images].every(i=>i.complete&&i.naturalWidth>0)")
        capture('overview-light.png', '.site', 'light', 'portrait')
        for view in ('input', 'target', 'review'):
            capture(f'{view}-portrait.png', f'[data-phone={view}]', 'light', 'portrait')
        browser.evaluate("designStudy.setTheme('dark')")
        capture('overview-dark.png', '.site', 'dark', 'portrait')
        browser.evaluate("designStudy.setTheme('light')")
        browser.evaluate("designStudy.setPhotoVariant('landscape')")
        browser.evaluate("new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))")
        assert browser.evaluate("[...document.images].every(i=>i.complete&&i.naturalWidth>0)")
        for view in ('input', 'review'):
            capture(f'{view}-landscape-photo.png', f'[data-phone={view}]', 'light', 'landscape')
        browser.evaluate("designStudy.setPhotoVariant('portrait')")
        assert not browser.errors and not browser.network
    finally:
        browser.close()
    manifest = {'generatedAt': datetime.now(timezone.utc).isoformat(),
                'htmlSha256': sha256(args.html), 'captureScriptSha256': sha256(Path(__file__)),
                'nativeAcceptance': False, 'screenshots': captures}
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Captured {len(captures)} current design previews; no JS errors or external requests.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
