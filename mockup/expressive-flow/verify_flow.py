#!/usr/bin/env python3
"""Offline Chromium regression runner for the expressive-flow composition study.

Python >=3.10, standard library only; Chromium must be on PATH (or --chromium).
See VERIFY-HARNESS.md for the explicit interpreter on the authoring host.
No camera, provider, audio, export or Anki calls are made. Service results are
controlled UI fixtures. Assertions are browser observations, not native acceptance.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import time
import traceback
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from xml.sax.saxutils import escape as xml_escape

HERE = Path(__file__).resolve().parent
SAMPLE = "図書館で本を読みました。"
EDITED = "図書館で本を読んだ。"
# Hide the study board, not application content. The phone is the device viewport;
# otherwise the board's gutters make a '360px' browser contain a 344px phone.
DEVICE_CSS = """
body{min-height:0!important}.site{padding:0!important;max-width:none!important}
.site>header,.site>footer,.scenario-picker,.scenario-copy,.board-label,.board-caption{display:none!important}
.scenario-layout{display:block!important}.phone{width:100vw!important;max-width:none!important;margin:0!important;height:100dvh!important}
"""


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Chromium did not return a PNG")
    return list(struct.unpack(">II", data[16:24]))


def rgba(value):
    """Parse actual Chromium rgb()/rgba() computed colors, not role tokens."""
    import re
    nums = re.findall(r"[\d.]+", value)
    if not value.startswith("rgb") or len(nums) not in (3, 4):
        raise ValueError(f"Unsupported computed color: {value!r}")
    return [float(x) for x in nums[:3]] + [float(nums[3]) if len(nums) == 4 else 1.0]


def composite(top, bottom):
    alpha = top[3] + bottom[3] * (1 - top[3])
    if not alpha:
        return [0, 0, 0, 0]
    return [(top[i] * top[3] + bottom[i] * bottom[3] * (1 - top[3])) / alpha
            for i in range(3)] + [alpha]


def luminance(color):
    channels = [c / 255 for c in color[:3]]
    linear = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in channels]
    return sum(c * weight for c, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + .05) / (low + .05)


class Failure(AssertionError):
    pass


class WebSocket:
    """Minimal masked RFC6455 client, including ping and fragmented text frames."""
    def __init__(self, url):
        u = urllib.parse.urlsplit(url)
        self.sock = socket.create_connection((u.hostname, u.port), timeout=12)
        self.buffer = bytearray()
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        target = u.path + ("?" + u.query if u.query else "")
        request = (f"GET {target} HTTP/1.1\r\nHost: {u.hostname}:{u.port}\r\n"
                   f"Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n"
                   "Sec-WebSocket-Version: 13\r\n\r\n")
        self.sock.sendall(request.encode())
        while b"\r\n\r\n" not in self.buffer:
            self.buffer.extend(self.sock.recv(4096))
        headers, rest = bytes(self.buffer).split(b"\r\n\r\n", 1)
        self.buffer = bytearray(rest)
        expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
        if b" 101 " not in headers.split(b"\r\n", 1)[0] or expected.encode() not in headers:
            raise RuntimeError(f"CDP WebSocket upgrade failed: {headers!r}")

    def take(self, length):
        while len(self.buffer) < length:
            chunk = self.sock.recv(max(4096, length - len(self.buffer)))
            if not chunk:
                raise ConnectionError("CDP socket closed")
            self.buffer.extend(chunk)
        result = bytes(self.buffer[:length])
        del self.buffer[:length]
        return result

    def send_frame(self, payload, opcode=1):
        mask = secrets.token_bytes(4)
        size = len(payload)
        header = bytes([0x80 | opcode])
        if size < 126:
            header += bytes([0x80 | size])
        elif size <= 65535:
            header += b"\xfe" + struct.pack(">H", size)
        else:
            header += b"\xff" + struct.pack(">Q", size)
        encoded = bytes(byte ^ mask[i % 4] for i, byte in enumerate(payload))
        self.sock.sendall(header + mask + encoded)

    def send(self, value):
        self.send_frame(json.dumps(value, ensure_ascii=False).encode())

    def receive(self):
        chunks = bytearray()
        while True:
            first, second = self.take(2)
            opcode, size = first & 15, second & 127
            if size == 126:
                size = struct.unpack(">H", self.take(2))[0]
            elif size == 127:
                size = struct.unpack(">Q", self.take(8))[0]
            mask = self.take(4) if second & 128 else None
            payload = self.take(size)
            if mask:
                payload = bytes(c ^ mask[i % 4] for i, c in enumerate(payload))
            if opcode == 8:
                raise ConnectionError("CDP WebSocket close frame")
            if opcode == 9:
                self.send_frame(payload, 10)
                continue
            if opcode == 10:
                continue
            if opcode not in (0, 1):
                raise RuntimeError(f"Unexpected CDP WebSocket opcode {opcode}")
            chunks.extend(payload)
            if first & 128:
                return json.loads(chunks.decode())

    def close(self):
        self.sock.close()


class Browser:
    def __init__(self, html, chromium, scratch, assets):
        self.html = html
        self.temp = tempfile.TemporaryDirectory(prefix="flow-cdp-", dir=scratch)
        self.work = Path(self.temp.name)
        self.errors, self.network, self.events = [], [], []
        self.serial = 0
        self.ws = None
        self.process = None
        self.chrome_log = None
        self.log = (self.work / "chromium.log").open("wb")
        config = self.work / "fonts.conf"
        cache = self.work / "font-cache"
        cache.mkdir()
        config.write_text('<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "fonts.dtd">'
                          '<fontconfig><dir>' + xml_escape(str(assets)) + '</dir><cachedir>' +
                          xml_escape(str(cache)) + '</cachedir><config><rescan><int>0</int>'
                          '</rescan></config></fontconfig>', encoding="utf-8")
        env = dict(os.environ, FONTCONFIG_FILE=str(config), FONTCONFIG_PATH=str(self.work))
        self.command = [chromium, "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                        "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                        "--disable-background-networking", "--disable-component-update",
                        "--disable-sync", "--metrics-recording-only", "--disable-extensions",
                        "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=0",
                        f"--user-data-dir={self.work / 'profile'}", "--window-size=360,828",
                        "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost", "about:blank"]
        try:
            self.process = subprocess.Popen(self.command, stdout=self.log, stderr=self.log,
                                            env=env, start_new_session=True)
            port_file = self.work / "profile" / "DevToolsActivePort"
            deadline = time.monotonic() + 20
            while not port_file.exists():
                if self.process.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError("Chromium failed to start: " + (self.work / "chromium.log").read_text(errors="replace"))
                time.sleep(.05)
            port = int(port_file.read_text().splitlines()[0])
            self.port = port
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open(f"http://127.0.0.1:{port}/json/list", timeout=10) as response:
                targets = json.load(response)
            target = next(t for t in targets if t["type"] == "page")
            self.ws = WebSocket(target["webSocketDebuggerUrl"])
            self.call("Page.enable")
            self.call("Runtime.enable")
            self.call("Log.enable")
            self.call("Network.enable")
            self.call("Network.setBlockedURLs", urls=["http://*", "https://*", "ws://*", "wss://*"])
            self.call("Page.addScriptToEvaluateOnNewDocument", source="""
window.__verifyErrors=[];
addEventListener('error',e=>__verifyErrors.push({kind:'error',message:e.message}));
addEventListener('unhandledrejection',e=>__verifyErrors.push({kind:'unhandledrejection',message:String(e.reason)}));
""")
            self.version = self.call("Browser.getVersion")
            self.call("Emulation.setDeviceMetricsOverride", width=360, height=828, deviceScaleFactor=1, mobile=False)
            self.call("Emulation.setEmulatedMedia", features=[{"name": "prefers-reduced-motion", "value": "reduce"}])
            self.call("Page.navigate", url=html.as_uri())
            self.wait("document.readyState==='complete' && !!window.designStudy", timeout=15)
            self.evaluate("document.fonts.ready.then(()=>true)")
        except BaseException:
            self.close()
            raise

    def event(self, data):
        method, params = data.get("method"), data.get("params", {})
        if method == "Runtime.exceptionThrown":
            self.errors.append({"kind": method, "details": params.get("exceptionDetails")})
        elif method == "Runtime.consoleAPICalled" and params.get("type") == "error":
            self.errors.append({"kind": "console.error", "args": params.get("args")})
        elif method == "Log.entryAdded" and params.get("entry", {}).get("level") == "error":
            self.errors.append({"kind": method, "entry": params.get("entry")})
        elif method == "Network.requestWillBeSent":
            request = params.get("request", {})
            if request.get("url", "").startswith(("http:", "https:", "ws:", "wss:")):
                self.network.append(request.get("url"))

    def call(self, method, **params):
        self.serial += 1
        number = self.serial
        self.ws.send({"id": number, "method": method, "params": params})
        while True:
            value = self.ws.receive()
            if value.get("id") == number:
                if "error" in value:
                    raise RuntimeError(f"{method}: {value['error']}")
                return value.get("result", {})
            self.event(value)

    def evaluate(self, expression):
        result = self.call("Runtime.evaluate", expression=expression, returnByValue=True,
                           awaitPromise=True, userGesture=True)
        if "exceptionDetails" in result:
            raise RuntimeError(f"JavaScript evaluation failed: {result['exceptionDetails']}")
        value = result.get("result", {})
        if value.get("subtype") == "error":
            raise RuntimeError(value.get("description"))
        return value.get("value")

    def wait(self, expression, timeout=4):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = self.evaluate(expression)
            if value:
                return value
            time.sleep(.04)
        raise Failure(f"wait {expression} → expected truthy → actual {value!r}")

    def selector(self, selector, phone="live"):
        return (f'[data-phone="{phone}"] ' + selector) if phone else selector

    def point(self, selector, phone="live"):
        full = self.selector(selector, phone)
        result = self.evaluate("""(()=>{const e=document.querySelector(%s);if(!e)return {error:'missing'};
if(e.disabled||e.closest('[inert]'))return {error:'disabled or inert'};
e.scrollIntoView({block:'nearest',inline:'nearest'});const r=e.getBoundingClientRect();
const x=r.left+r.width/2,y=r.top+r.height/2,hit=document.elementFromPoint(x,y);
return {x,y,width:r.width,height:r.height,hit:hit&&(hit===e||e.contains(hit)),tag:hit?.outerHTML?.slice(0,250)};})()""" % json.dumps(full))
        if result.get("error") or not result.get("hit") or result["width"] <= 0:
            raise Failure(f"click {full} → expected enabled, unobscured DOM target → actual {result}")
        return result

    def mouse(self, x, y, kind="mousePressed", button="left", buttons=None):
        args = {"type": kind, "x": x, "y": y, "button": button}
        if kind in ("mousePressed", "mouseReleased"):
            args["clickCount"] = 1
        if buttons is not None:
            args["buttons"] = buttons
        self.call("Input.dispatchMouseEvent", **args)

    def click_xy(self, x, y):
        self.mouse(x, y, "mouseMoved", "none")
        self.mouse(x, y)
        self.mouse(x, y, "mouseReleased")
        self.evaluate("new Promise(r=>requestAnimationFrame(()=>r(true)))")

    def click(self, selector, phone="live"):
        p = self.point(selector, phone)
        self.click_xy(p["x"], p["y"])

    def focus(self, selector, phone="live"):
        # Handle pointerdown intentionally prevents default focus. Chromium DOM
        # focus plus real key events tests the keyboard handler without changing
        # selection state through the fixture API.
        root = self.call("DOM.getDocument")["root"]["nodeId"]
        node = self.call("DOM.querySelector", nodeId=root,
                         selector=self.selector(selector, phone))["nodeId"]
        if not node:
            raise Failure(f"focus {selector} → expected DOM target → actual missing")
        self.call("DOM.focus", nodeId=node)

    def key(self, key, shift=False, control=False):
        definitions = {"Tab": (9, "Tab"), "Enter": (13, "Enter"), "Escape": (27, "Escape"),
                       "ArrowLeft": (37, "ArrowLeft"), "ArrowRight": (39, "ArrowRight"),
                       "ArrowUp": (38, "ArrowUp"), "ArrowDown": (40, "ArrowDown"),
                       "Backspace": (8, "Backspace"), "a": (65, "KeyA")}
        number, code = definitions[key]
        args = dict(key=key, code=code, windowsVirtualKeyCode=number,
                    nativeVirtualKeyCode=number, modifiers=(8 if shift else 0) | (2 if control else 0))
        # Chromium requires the native Enter text payload for button activation;
        # key/code alone dispatches keydown but does not generate a click.
        if key == "Enter":
            args.update(text="\r", unmodifiedText="\r")
        self.call("Input.dispatchKeyEvent", type="keyDown", **args)
        args.pop("text", None)
        args.pop("unmodifiedText", None)
        self.call("Input.dispatchKeyEvent", type="keyUp", **args)

    def fill(self, selector, text, phone="live"):
        self.click(selector, phone)
        self.key("a", control=True)
        self.key("Backspace")
        self.call("Input.insertText", text=text)

    def close(self):
        if self.ws:
            self.ws.close()
            self.ws = None
        if self.process and self.process.poll() is None:
            os.killpg(self.process.pid, signal.SIGTERM)
            try:
                self.process.wait(timeout=6)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait(timeout=3)
        self.log.close()
        self.temp.cleanup()


class Suite:
    def __init__(self, browser, args):
        self.b, self.args = browser, args
        self.results, self.captures = [], []
        self.case = "startup"
        self.width, self.theme, self.scale = 360, "light", 1

    def check(self, path, expected, actual):
        passed = actual == expected
        entry = {"case": self.case, "path": path, "expected": expected, "actual": actual, "pass": passed}
        self.results.append(entry)
        if not passed:
            raise Failure(f"{self.case}/{path} → expected {expected!r} → actual {actual!r}")
        return actual

    def truth(self, path, actual, explanation):
        self.check(path, {"condition": explanation, "satisfied": True},
                   {"condition": explanation, "satisfied": bool(actual)})

    def run(self, name, function):
        self.case = name
        start = time.monotonic()
        first_result = len(self.results)
        try:
            function()
        except Exception as error:
            if not isinstance(error, Failure) or not self.results or self.results[-1].get("pass"):
                self.results.append({"case": name, "path": "execution", "expected": "case completes",
                                     "actual": str(error), "pass": False,
                                     "traceback": traceback.format_exc()})
            print(f"FAIL {error}", flush=True)
            try:
                self.capture("FAIL-" + name)
            except Exception:
                pass
        else:
            passed = all(item["pass"] for item in self.results[first_result:])
            print(f"{'PASS' if passed else 'FAIL'} {name} ({time.monotonic() - start:.2f}s)", flush=True)

    def state(self, phone="live"):
        return self.b.evaluate(f"JSON.parse(JSON.stringify(designStudy.phones[{json.dumps(phone)}]))")

    def state_is(self, **expected):
        state = self.state()
        self.check("state", expected, {key: state.get(key) for key in expected})
        return state

    def dom(self, expression, phone="live"):
        return self.b.evaluate("(()=>{const p=document.querySelector('[data-phone=" +
                               json.dumps(phone) + "]');return (" + expression + ");})()")

    def reset(self, scenario="type-empty"):
        self.b.evaluate(f"designStudy.setTheme('light');designStudy.setTextScale(1);designStudy.seedLive({json.dumps(scenario)});")
        self.width, self.theme, self.scale = 360, "light", 1
        self.device(360)

    def device(self, width, height=828):
        self.width = width
        self.b.call("Emulation.setDeviceMetricsOverride", width=width, height=height,
                    deviceScaleFactor=1, mobile=False)
        self.b.evaluate("(()=>{let s=document.getElementById('verify-device');if(!s){s=document.createElement('style');s.id='verify-device';document.head.append(s);}s.textContent=" + json.dumps(DEVICE_CSS) + ";scrollTo(0,0);return true;})()")
        self.b.evaluate("new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(true))))")

    def click(self, action):
        self.b.click(f'[data-action="{action}"]')

    def demo(self, name):
        # Study-only fixture buttons are outside the phone, hidden for device
        # captures. Reveal them while physically clicking, then restore the
        # exact device viewport. Never JS-click a display:none control.
        self.b.evaluate("(()=>{const s=document.getElementById('verify-device');if(s)s.disabled=true;})()")
        try:
            self.b.click(f'[data-demo="{name}"]', phone=None)
        finally:
            self.b.evaluate("(()=>{const s=document.getElementById('verify-device');if(s)s.disabled=false;})()")
            self.device(self.width)

    def token(self, text):
        start = self.b.evaluate(f"designStudy.phones.live.tokens.find(t=>t.text==={json.dumps(text)})?.start")
        self.truth("token exists", start is not None, text + " has a selectable token")
        self.b.click(f'[data-token-start="{start}"]')

    def book(self):
        self.token("本")
        # Explicitly choose the dictionary identity; do not assume tapping a
        # homograph opens a sheet when the fixture has an initial noun choice.
        if not self.dom("!!p.querySelector('[data-entry=book]')"):
            self.click("entries")
        self.b.click("[data-entry=book]")
        self.state_is(entryId="book", selection={"start": 4, "end": 5}, status="ready")

    def ready_review(self, photo=True):
        self.reset()
        self.demo("photo" if photo else "type")
        if photo:
            self.click("capture")
            self.click("use-photo")
        else:
            self.b.fill('[data-input="sentence"]', SAMPLE)
            self.click("prepare")
        self.book()
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review' && designStudy.phones.live.status==='ready'")
        return self.state()

    def capture(self, name, phone="live"):
        if self.args.no_screens or not self.args.screens:
            return
        directory = self.args.screens
        directory.mkdir(parents=True, exist_ok=True)
        slug = "".join(c if c.isalnum() or c in "-_" else "-" for c in name)
        filename = f"{len(self.captures)+1:03d}-{slug}.png"
        bounds = self.b.evaluate("(()=>{const r=document.querySelector('[data-phone=" + json.dumps(phone) + "]')?.getBoundingClientRect();return r?{x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height,scale:1}:null;})()")
        if not bounds:
            return
        image = self.b.call("Page.captureScreenshot", format="png", clip=bounds,
                            captureBeyondViewport=True, fromSurface=True)
        data = base64.b64decode(image["data"])
        path = directory / filename
        path.write_bytes(data)
        self.captures.append({"file": filename, "sha256": sha256(path), "bytes": len(data),
                              "size": png_size(data), "case": self.case,
                              "theme": self.theme, "textScale": self.scale,
                              "phoneWidth": bounds["width"], "viewportWidth": self.width,
                              "state": self.state(phone),
                              "scrollTop": self.dom("p.querySelector('.main,.phone-main')?.scrollTop||0", phone)})

    def contract(self):
        self.check("designStudy.apiVersion", 2, self.b.evaluate("designStudy.apiVersion"))
        self.check("fixture language", "ja", self.b.evaluate("(()=>{designStudy.seedLive('type-empty');return document.querySelector('[data-input=sentence]').lang})()"))
        self.check("native service disclaimer", True, self.b.evaluate("/simulat/i.test(document.body.textContent)"))

    def happy(self):
        self.ready_review()
        self.state_is(stage="review", status="ready", entryId="book", hasPhoto=True,
                      sentence=SAMPLE, definition="book; volume", translation="I read a book at the library.")
        self.check("review DOM order", ["photo", "sentence", "translation", "vocabulary"], self.dom("[...p.querySelector('.review-content').children].map(e=>e.matches('.context-strip')?'photo':e.matches('.sentence-audio')?'sentence':e.matches('.word-hero')?'vocabulary':e.matches('.translation')?'translation':'other')"))
        self.capture("happy-review")
        self.click("send")
        self.state_is(status="sending", submission="sending")
        snapshot = self.state()["snapshot"]
        self.check("submitted identity", {"word": "本", "reading": "ほん", "definition": "book; volume"},
                   {key: snapshot.get(key) for key in ("word", "reading", "definition")})
        # Physical second click plus direct dispatch while the first is in-flight.
        self.b.evaluate("designStudy.dispatch('live',{type:'SEND'})")
        self.check("duplicate attempt retains snapshot", snapshot, self.state()["snapshot"])
        self.b.wait("designStudy.phones.live.stage==='input' && designStudy.phones.live.status==='ready'")
        self.state_is(stage="input", status="ready", submission=None, snapshot=None, sentence="")
        self.capture("happy-sent-outcome")

    def edited(self):
        self.reset("entries")
        self.b.key("Escape")
        self.click("edit-sentence")
        self.b.fill('[data-editor="sentence"]', EDITED)
        self.click("save-editor")
        self.state_is(stage="target", sentence=EDITED, selection=None, entryId=None)
        self.check("retokenized sentence", ["図書館", "で", "本", "を", "読んだ", "。"],
                   [t["text"] for t in self.state()["tokens"]])
        self.check("no whole-sentence token", False, self.dom("[...p.querySelectorAll('[data-token-start]')].some(e=>e.textContent===designStudy.phones.live.sentence)"))
        self.token("読んだ")
        self.state_is(entryId="read", selection={"start": 6, "end": 9}, status="ready")
        self.check("known token enables Continue", False, self.dom("p.querySelector('[data-action=continue]').disabled"))
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.check("one continuous inflected highlight", ["読んだ"], self.dom("[...p.querySelectorAll('[data-selected-occurrence]')].map(e=>{const c=e.cloneNode(true);c.querySelectorAll('rt').forEach(r=>r.remove());return c.textContent})"))
        self.capture("edited-sentence-inflected-review")
        self.click("back")
        self.token("で")
        self.check("unknown token boundary disclosed", True, self.dom("!!p.querySelector('[data-no-entry]') && /no entry/i.test(p.querySelector('[data-no-entry]').textContent)"))
        self.check("unknown disables Continue", True, self.dom("p.querySelector('[data-action=continue]').disabled"))
        self.capture("edited-unknown-recoverable")
        self.token("図書館")
        self.state_is(entryId="library", status="ready")

    def spans(self):
        self.reset("entries")
        self.b.key("Escape")
        self.token("図書館")
        self.check("tappable word pills replace selection handles", True,
                   self.dom("p.querySelectorAll('button.word-pill').length>0 && !p.querySelector('[data-handle],.selection-handle')"))
        self.check("no range dropdowns", 0, self.dom("p.querySelectorAll('select').length"))
        # Extend three-character 図書館 across particle + 本 with real key events.
        self.b.focus('[data-sentence-run]')
        self.b.key("ArrowRight", shift=True)
        self.b.key("ArrowRight", shift=True)
        self.state_is(selection={"start": 0, "end": 5}, entryId=None, status="unknown")
        self.check("continuous multi-token span", ["図書館で本"], self.dom("[...p.querySelectorAll('[data-selected-occurrence]')].map(e=>e.textContent)"))
        self.check("no-entry span disclosure", True, self.dom("p.querySelector('[data-no-entry]')?.textContent.includes('図書館で本')"))
        self.check("span cannot continue", True, self.dom("p.querySelector('[data-action=continue]').disabled"))
        self.capture("span-preserved-outcome")
        # Invalid direct API request must not silently invert/collapse the range.
        self.b.evaluate("designStudy.selectSpan('live',5,0,false)")
        self.state_is(selection={"start": 0, "end": 5})
        self.b.focus('[data-sentence-run]')
        self.b.key("ArrowLeft", shift=True)
        self.b.key("ArrowLeft", shift=True)
        self.state_is(selection={"start": 0, "end": 3}, entryId="library", status="ready")
        self.capture("span-trimmed-recovered")
        # Exercise the pointer implementation, not just keyboard boundary controls.
        handle = self.b.point('[data-token-start="0"]')
        destination = self.b.evaluate("(()=>{const e=document.querySelector('[data-phone=live] [data-token-start=\"4\"]');const r=document.createRange();r.selectNodeContents(e);const b=r.getBoundingClientRect();return {x:b.right-1,y:b.top+b.height/2};})()")
        self.b.mouse(handle["x"], handle["y"], buttons=1)
        self.b.mouse(destination["x"], destination["y"], "mouseMoved", "left", buttons=1)
        self.b.mouse(destination["x"], destination["y"], "mouseReleased", buttons=0)
        self.state_is(selection={"start": 0, "end": 5})
        self.capture("span-dragged-outcome")

    def recovery(self):
        for fixture in ("missing", "counter"):
            self.reset(fixture)
            before = self.state()
            failed_entry = before["entryId"]
            self.state_is(status="missing", stage="target")
            self.check(f"{fixture} word + reading visible", True, self.dom("/本/.test(p.querySelector('.entry-name')?.textContent) && /ほん/.test(p.querySelector('.entry-name')?.textContent)"))
            self.check(f"{fixture} unavailable target disabled", True, self.dom("p.querySelector('[data-action=continue]').disabled"))
            if fixture == "counter":
                self.check("counter meaning retained on miss", "counter for long, cylindrical things", before["definition"])
            self.capture(fixture + "-miss")
            self.click("reselect")
            self.state_is(selection=None, entryId=None, definition="", stage="target")
            self.check(f"{fixture} recovery requires explicit choice", True, self.dom("p.querySelector('[data-action=continue]').disabled && !!p.querySelector('.reason')"))
            self.capture(fixture + "-cleared-recovery-outcome")
            self.token("本")
            self.state_is(entryId=failed_entry, status="missing")
            self.check(f"{fixture} retap cannot revive failed identity", True, self.dom("p.querySelector('[data-action=continue]').disabled"))
            self.b.evaluate("designStudy.dispatch('live',{type:'CONTINUE'})")
            self.state_is(stage="target", status="missing", entryId=failed_entry)
            self.click("entries")
            self.b.click('[data-entry="counter"]')
            self.state_is(entryId="counter", definition="counter for long, cylindrical things", status="missing")
            self.capture(fixture + "-counter-explicit-outcome")
            self.click("entries")
            self.b.click('[data-entry="book"]')
            if fixture == "counter":
                self.state_is(entryId="book", status="ready", definition="book; volume")
                self.check("explicit meaning change recovers", False, self.dom("p.querySelector('[data-action=continue]').disabled"))
            else:
                self.state_is(entryId="book", status="missing")
            self.capture(fixture + "-explicit-book-outcome")

    def network_case(self):
        self.reset("entries")
        self.b.key("Escape")
        self.demo("fail")
        retained = self.state()
        self.click("continue")
        self.state_is(stage="preparing", status="generating")
        self.check("preparing is not Review", "Preparing", self.dom("p.querySelector('.toolbar h2').textContent"))
        self.b.wait("designStudy.phones.live.status==='failed'")
        self.state_is(stage="preparing", status="failed", entryId="book")
        self.check("failed generation retains source + meaning", {k: retained[k] for k in ("sentence", "selection", "entryId", "definition", "hasPhoto")},
                   {k: self.state()[k] for k in ("sentence", "selection", "entryId", "definition", "hasPhoto")})
        self.capture("network-failure-outcome")
        self.click("retry")
        self.state_is(stage="preparing", status="generating")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.state_is(status="ready", entryId="book")
        self.capture("network-retry-review-outcome")
        self.click("back")
        self.click("continue")
        self.state_is(stage="preparing", status="generating")
        cancelled_generation = self.state()["generation"]
        self.click("cancel-generation")
        self.state_is(stage="target", status="ready", entryId="book")
        self.truth("cancel invalidates generation", self.state()["generation"] > cancelled_generation, "generation id advances")
        time.sleep(.8)  # Deliberately let the actual scheduled completion fire.
        self.state_is(stage="target", status="ready")
        self.capture("generation-cancel-outcome")
        self.reset("failed")
        self.click("edit-source")
        self.state_is(stage="target", status="ready")
        self.reset("failed")
        self.click("discard")
        self.capture("discard-confirmation")
        self.click("close-sheet")
        self.state_is(stage="preparing", status="failed")
        self.click("discard")
        self.click("confirm-discard")
        self.state_is(stage="input", sentence="", selection=None, snapshot=None)
        self.capture("discard-confirmed-empty-outcome")

    def typed(self):
        self.reset()
        self.capture("type-empty")
        self.check("empty type disabled", True, self.dom("p.querySelector('[data-action=prepare]').disabled"))
        self.check("no empty photo placeholder", 0, self.dom("p.querySelectorAll('img,.context-strip,.source-peek').length"))
        self.b.fill('[data-input="sentence"]', SAMPLE)
        self.click("prepare")
        self.book()
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.state_is(hasPhoto=False, origin="type", stage="review", status="ready")
        self.check("typed review has no photo or placeholder", 0, self.dom("p.querySelectorAll('img,.context-strip,.source-peek,.photo-placeholder').length"))
        self.capture("typed-no-photo-review-outcome")
        self.click("extras")
        self.b.fill('[data-editor="notes"]', "Remember this context.")
        self.b.fill('[data-editor="tags"]', "reading textbook")
        self.click("tag-suggestion")
        self.click("save-editor")
        self.state_is(notes="Remember this context.", tags="reading textbook JLPT::N5", hasPhoto=False)
        self.check("notes + tags outcome displayed", True, self.dom("p.querySelector('.notes-preview')?.textContent==='Remember this context.' && p.querySelectorAll('.tags-preview span').length===3"))
        self.capture("notes-tags-filled-outcome")

    def ui(self):
        self.reset()
        self.demo("photo")
        self.check("no top Photo/Type mode pill", 0, self.dom("p.querySelectorAll('.mode-switch,[aria-label=\"Input mode\"]').length"))
        geometry = self.dom("(()=>{const c=p.querySelector('[data-action=capture]').getBoundingClientRect(),t=p.querySelector('[data-action=type-mode]').getBoundingClientRect();return {right:t.left>=c.right,aligned:Math.abs((c.top+c.bottom)/2-(t.top+t.bottom)/2)<36};})()")
        self.check("Type right of shutter", {"right": True, "aligned": True}, geometry)
        primary = rgba(self.dom("getComputedStyle(p.querySelector('.shutter')).borderTopColor"))
        self.truth("vermilion rather than green", primary[0] > primary[1] and primary[0] > primary[2], "rendered shutter red channel dominates")
        self.click("capture")
        self.click("use-photo")
        self.check("Target has no photo/subtitle", {"photo": 0, "subtitle": False, "settings": 0}, self.dom("({photo:p.querySelectorAll('[data-action=view-photo],.source-peek').length,subtitle:/Choose a word[.!]/.test(p.querySelector('.main').textContent),settings:p.querySelectorAll('[data-action=settings]').length})"))
        self.book()
        self.check("sentence lang", "ja", self.dom("p.querySelector('[data-sentence-run]').lang"))
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.check("no Accent 1 jargon", False, self.dom("/Accent\\s*\\d/i.test(p.innerText)"))
        controls = self.dom("[...p.querySelectorAll('.audio-play')].map(e=>{const s=getComputedStyle(e),r=e.getBoundingClientRect();return {class:e.className,width:r.width,height:r.height,background:s.backgroundColor,color:s.color,svg:e.querySelector('svg').innerHTML}})")
        self.check("two identical icon-only play treatments", 2, len(controls))
        self.check("word and sentence play controls match", controls[0], controls[1])
        self.check("play button accessible names", ["Play sentence", "Play word"], self.dom("[...p.querySelectorAll('.audio-play')].map(e=>e.getAttribute('aria-label'))"))
        labels = self.dom("[...p.querySelectorAll('[data-pitch-katakana] text')].map(e=>e.textContent)")
        self.truth("katakana pitch + following particle", "ホ" in labels and "ン" in labels and "（ヲ）" in labels and not any('ぁ' <= c <= 'ゖ' for label in labels for c in label), "katakana moras, no hiragana, particle present")
        self.check("no pitch axis legend", False, "high" in labels or "low" in labels)
        self.check("status bar decorative", "true", self.dom("p.querySelector('.statusbar').getAttribute('aria-hidden')"))
        self.check("Review settings absent", 0, self.dom("p.querySelectorAll('[data-action=settings]').length"))
        self.click("sentence-audio")
        self.click("word-audio")
        self.click("view-photo")
        self.check("photo opens full image", 1, self.dom("p.querySelectorAll('.full-photo img').length"))
        self.b.key("Escape")
        self.check("photo close restores invoker", "view-photo", self.b.evaluate("document.activeElement.dataset.action"))
        self.capture("review-icons-pitch")
        self.click("edit")
        self.b.click('[data-edit="translation"]')
        self.b.fill('[data-editor="translation"]', "I finished reading at the library.")
        self.click("save-editor")
        self.state_is(translation="I finished reading at the library.", stage="review")
        self.capture("translation-edit-outcome")
        self.reset("readings")
        self.check("pitch sheet shows actual current value", "じょうぶ", self.dom("p.querySelector('[data-editor=word-reading]').value"))
        before = self.state()
        self.click("save-pronunciation")
        self.check("unchanged pronunciation Done preserves full note", before, self.state())
        self.b.click('[data-edit="pitch"]')
        self.b.click('[data-pitch="1"]')
        self.click("save-pronunciation")
        # SPEC §10: a listed pitch alternative changes pitch/recording checks,
        # not the sentence text or its audio. A sentence-reading correction does.
        self.state_is(stage="review", sentenceAudioStale=False, pitchIndex=1)
        self.capture("pronunciation-correction-outcome")
        self.b.click('[data-edit="sentence"]')
        self.click("sentence-readings")
        self.b.fill('[data-reading-start="4"]', "ジョウブ")
        self.click("save-readings")
        self.state_is(stage="target", sentenceAudioStale=True, pitchIndex=1)
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.state_is(sentenceAudioStale=False, pitchIndex=1)
        self.capture("sentence-reading-regeneration-outcome")

    def notifications(self):
        # Expiring a toast must never re-render an active editor and steal focus,
        # whether the toast belongs to this draft or a discarded/reseeded draft.
        for owner in ("same-draft", "previous-draft"):
            if owner == "same-draft":
                self.reset("entries")
                self.b.key("Escape")
                self.demo("fail")
            else:
                self.reset("uncertain")
                self.click("open-anki")
                self.reset("entries")
                self.b.key("Escape")
            self.click("edit-sentence")
            self.b.fill('[data-editor="sentence"]', EDITED)
            time.sleep(2.35)  # Let the real 2200ms notification expiry execute.
            self.check(owner + " expiry preserves editor focus", "sentence",
                       self.b.evaluate("document.activeElement.dataset.editor"))
            self.check(owner + " expiry preserves entered text", EDITED,
                       self.dom("p.querySelector('[data-editor=sentence]').value"))
            self.click("save-editor")
            self.state_is(sentence=EDITED, selection=None, entryId=None)
        self.capture("notification-expiry-edit-outcome")

    def keyboard(self):
        self.ready_review(False)
        # Start keyboard focus at the real Back button then tab to notes/tags.
        self.b.click('[data-action="back"]')
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.b.evaluate("document.querySelector('[data-phone=live] [data-action=back]').focus()")
        for _ in range(30):
            if self.b.evaluate("document.activeElement?.dataset.action==='extras'"):
                break
            self.b.key("Tab")
        self.check("keyboard reaches notes invoker", "extras", self.b.evaluate("document.activeElement?.dataset.action"))
        self.b.key("Enter")
        self.check("sheet modal semantics", {"role": "dialog", "modal": "true"}, self.dom("(()=>{const s=p.querySelector('.sheet');return {role:s?.getAttribute('role'),modal:s?.getAttribute('aria-modal')}})()"))
        self.check("sheet initially focuses close", "close-sheet", self.b.evaluate("document.activeElement.dataset.action"))
        self.b.key("Tab", shift=True)
        self.check("Shift+Tab trapped at last control", "save-editor", self.b.evaluate("document.activeElement.dataset.action"))
        self.b.key("Tab")
        self.check("Tab trapped at first control", "close-sheet", self.b.evaluate("document.activeElement.dataset.action"))
        self.b.key("Tab")
        self.check("notes editable by keyboard", "notes", self.b.evaluate("document.activeElement.dataset.editor"))
        self.b.call("Input.insertText", text="Keyboard note")
        self.b.key("Escape")
        self.state_is(notes="Keyboard note")
        self.check("Escape returns focus to invoker", "extras", self.b.evaluate("document.activeElement.dataset.action"))
        focus = self.b.evaluate("(()=>{const s=getComputedStyle(document.activeElement);return {style:s.outlineStyle,width:parseFloat(s.outlineWidth),color:s.outlineColor}})()")
        self.truth("keyboard focus visible", focus["style"] != "none" and focus["width"] >= 2, "at least 2px non-none focus outline")
        self.capture("keyboard-focus-restored")
        self.click("deck")
        self.b.key("Tab")
        self.b.key("ArrowDown")
        self.check("dictionary/deck radio arrows move focus", "Textbook", self.b.evaluate("document.activeElement.dataset.deck"))
        self.b.key("Enter")
        self.state_is(deck="Textbook")
        self.check("deck selection focus restored", "deck", self.b.evaluate("document.activeElement.dataset.action"))

    def locked(self, snapshot, route):
        state = self.state()
        self.check(route + " submission lock", "uncertain", state.get("submission"))
        self.check(route + " retained snapshot", snapshot, state.get("snapshot"))
        self.check(route + " is not sending", False, state.get("status") == "sending")
        self.check(route + " no Send/Continue/Edit UI", 0, self.dom("p.querySelectorAll('[data-action=send],[data-action=continue],[data-action=edit],[data-edit]').length"))
        self.check(route + " model cannot create or send", [False, False], self.b.evaluate("[designStudy.model.canContinue(designStudy.phones.live),designStudy.model.canSend(designStudy.phones.live)]"))
        # Even bypassing button availability may not resubmit this draft.
        self.b.evaluate("designStudy.dispatch('live',{type:'CONTINUE'});designStudy.dispatch('live',{type:'SEND'});")
        self.check(route + " direct resubmit remains locked", {"submission": "uncertain", "status": "uncertain"},
                   {k: self.state().get(k) for k in ("submission", "status")})

    def uncertain(self):
        routes = ("Back", "Escape", "sheet-Escape", "backdrop", "popstate", "edit", "gear", "direct-Continue", "demo-reset", "recheck", "open-Anki")
        def attempt(route):
            self.reset("uncertain")
            snapshot = self.state()["snapshot"]
            self.truth("submitted snapshot exists", bool(snapshot), "uncertain fixture has submitted identity")
            if route == "Back":
                self.click("back")
            elif route == "Escape":
                self.b.click('[data-action="back"]')
                self.b.key("Escape")
            elif route == "sheet-Escape":
                self.click("discard")
                self.b.key("Escape")
            elif route == "backdrop":
                self.click("discard")
                point = self.dom("(()=>{const o=p.querySelector('[data-backdrop]').getBoundingClientRect(),s=p.querySelector('.sheet').getBoundingClientRect();return {x:o.left+4,y:o.top+4,gap:s.top-o.top}})()")
                self.truth("backdrop has exposed surface", point["gap"] > 5, "backdrop pixel is outside sheet")
                self.b.click_xy(point["x"], point["y"])
            elif route == "popstate":
                self.b.evaluate("window.__verifyPopstates=0;addEventListener('popstate',()=>__verifyPopstates++,{once:true});history.pushState({verify:true},'');history.back();")
                self.b.wait("__verifyPopstates===1")
            elif route in ("edit", "gear"):
                action = "edit" if route == "edit" else "settings"
                self.check(route + " absent from locked UI", 0, self.dom(f"p.querySelectorAll('[data-action={action}]').length"))
                # Adversarial DOM control exercises the actual document handler.
                self.b.evaluate("(()=>{const p=document.querySelector('[data-phone=live]'),b=document.createElement('button');b.dataset.action=" + json.dumps(action) + ";b.id='verify-attack';b.textContent='Regression probe';b.style.cssText='position:absolute;top:100px;left:16px;z-index:99;width:160px;height:48px';p.append(b);})()")
                self.b.click("#verify-attack")
            elif route == "direct-Continue":
                self.b.evaluate("designStudy.dispatch('live',{type:'CONTINUE'});designStudy.dispatch('live',{type:'SET_SENTENCE',text:'本。'});designStudy.dispatch('live',{type:'BACK'});")
            elif route == "demo-reset":
                self.demo("photo")
                self.b.key("Escape")
            elif route == "recheck":
                self.click("recheck")
            elif route == "open-Anki":
                self.click("open-anki")
            self.locked(snapshot, route)
            self.check(route + " note content retained on screen", True, self.dom("p.querySelector('.word-title')?.textContent==='本' && !!p.querySelector('.definition')?.textContent && !!p.querySelector('.translation')?.textContent && !!p.querySelector('.context-strip img') && p.querySelector('.footer-actions').textContent.includes('Japanese') && p.querySelector('.pitch-scroll').getAttribute('aria-label').includes('ホン')"))
            self.capture("uncertain-" + route + "-outcome")
        for route in routes:
            self.run("uncertain-" + route, lambda r=route: attempt(r))
        self.case = "uncertain-explicit-discard"
        self.reset("uncertain")
        self.click("discard")
        self.check("uncertain discard consequence disclosed", True, self.dom("/may still exist in AnkiDroid/.test(p.querySelector('.sheet').textContent)"))
        self.capture("uncertain-discard-confirmation")
        self.click("confirm-discard")
        self.state_is(stage="input", submission=None, snapshot=None, sentence="")
        self.capture("uncertain-explicit-local-discard-outcome")

    def contrast_case(self):
        def measure(theme):
            self.reset("entries")
            self.b.key("Escape")
            self.b.evaluate(f"designStudy.setTheme('{theme}')")
            self.theme = theme
            targets = [('.word-pill[aria-pressed=true]', 'selection'), ('[data-sense="0"]', 'checked-meaning'),
                       ('.flow-progress [data-state=current]', 'current-step'), ('.flow-progress [data-state=completed]', 'completed-step'),
                       ('.flow-progress [data-state=upcoming]', 'upcoming-step')]
            for selector, name in targets:
                colors = self.colors(selector)
                fill = contrast(composite(rgba(colors["background"]), rgba(colors["parentBackground"])), rgba(colors["parentBackground"]))
                border = contrast(rgba(colors["border"]), rgba(colors["parentBackground"]))
                cue = colors["borderWidth"] >= (1 if name.endswith("-step") else 2) and border >= 3
                self.results.append({"case": self.case, "path": f"{theme}/{name}/measurements", "expected": "fill or visible boundary >=3:1", "actual": {**colors, "fillRatio": fill, "borderRatio": border, "boundaryCue": cue}, "pass": fill >= 3 or cue})
                self.truth(f"{theme}/{name}/non-text-contrast", fill >= 3 or cue, "computed rendered fill or boundary contrasts at least 3:1")
            self.click("edit-sentence")
            colors = self.colors('[data-editor="sentence"]')
            border = contrast(rgba(colors["border"]), composite(rgba(colors["background"]), rgba(colors["parentBackground"])))
            self.results.append({"case": self.case, "path": theme + "/field/measured-border", "expected": ">=3:1", "actual": {**colors, "borderRatio": border}, "pass": border >= 3})
            self.truth(theme + "/field/non-text-contrast", colors["borderWidth"] >= 1 and border >= 3, "actual textarea border >=3:1 against field fill")
            self.capture(theme + "-field-border")
            self.b.key("Escape")
        for theme in ("light", "dark"):
            self.run("non-text-contrast-" + theme, lambda t=theme: measure(t))

    def colors(self, selector):
        return self.dom("(()=>{const e=p.querySelector(" + json.dumps(selector) + ");const s=getComputedStyle(e);function bg(n){if(!n)return 'rgb(255, 255, 255)';const c=getComputedStyle(n).backgroundColor;if(c==='rgba(0, 0, 0, 0)'||c==='transparent')return bg(n.parentElement);return c;}return {background:s.backgroundColor,parentBackground:bg(e.parentElement),border:s.borderBottomColor,borderWidth:parseFloat(s.borderBottomWidth),color:s.color};})()")

    def font_metrics(self):
        return self.dom("""(()=>{const output={};for(const e of p.querySelectorAll('*')){
if(e.closest('[aria-hidden=true],.statusbar,.gesture-inset')||e.tagName==='STYLE')continue;
const direct=[...e.childNodes].filter(n=>n.nodeType===3).map(n=>n.textContent).join('').trim();
if(!direct||!e.getClientRects().length)continue;const s=getComputedStyle(e);let size=parseFloat(s.fontSize);
if(e instanceof SVGGraphicsElement){const m=e.getScreenCTM();size*=Math.hypot(m.a,m.b);}
let key=e.tagName+':'+(e.getAttribute('data-action')||e.getAttribute('class')||'')+':'+direct;
let n=0;while(output[key+(n?':'+n:'')])n++;key+=n?':'+n:'';
output[key]={text:direct,pixels:size};}return output;})()""")

    def layout(self):
        # Scroll every text-bearing node into view. Vertical main/pitch scrolling
        # is allowed; hidden clipping and horizontal phone overflow are not.
        return self.dom("""(()=>{const main=p.querySelector('.main'),pr=p.getBoundingClientRect();
const bad=[],reach=[],nodes=[...p.querySelectorAll('.toolbar h2,.word-title,.definition,.translation,.review-options button,.footer-actions>.primary,.pitch-scroll,[data-pitch-katakana] text,.read-sentence rt')];
for(const e of nodes){e.scrollIntoView({block:'nearest',inline:'nearest'});const r=e.getBoundingClientRect();
const a={text:e.getAttribute('aria-label')||e.textContent,rect:{left:r.left,right:r.right,top:r.top,bottom:r.bottom}};
if(r.left<pr.left-1||r.right>pr.right+1)bad.push({...a,kind:'horizontal'});
let ancestor=e.parentElement;while(ancestor&&ancestor!==p){const s=getComputedStyle(ancestor),b=ancestor.getBoundingClientRect();
if(['hidden','clip'].includes(s.overflowY)&&(r.top<b.top-1||r.bottom>b.bottom+1))bad.push({...a,kind:'hidden-y',ancestor:ancestor.className});ancestor=ancestor.parentElement;}
if(e.closest('.main')){const mr=main.getBoundingClientRect();if(r.height<=mr.height+1&&(r.top<mr.top-2||r.bottom>mr.bottom+2))bad.push({...a,kind:'unreachable-y'});}
reach.push(a);}
const primary=p.querySelector('[data-action=send]');primary.scrollIntoView({block:'nearest'});const sr=primary.getBoundingClientRect();
const x=sr.x+sr.width/2,y=sr.y+sr.height/2,hit=document.elementFromPoint(x,y);
return {phoneWidth:pr.width,phoneScrollWidth:p.scrollWidth,phoneClientWidth:p.clientWidth,documentOverflow:document.documentElement.scrollWidth>innerWidth+1,bad,reachable:reach,
send:{enabled:!primary.disabled,hit:hit===primary||primary.contains(hit),top:sr.top,bottom:sr.bottom},main:{height:main.clientHeight,scrollHeight:main.scrollHeight}};})()""")

    def responsive(self):
        # Each cell is independent: one clipping/font failure must not prevent
        # the remaining widths/themes/scales from being exercised and reported.
        for width in (320, 360, 390):
            for theme in ("light", "dark"):
                for scale in (1, 2):
                    self.run(f"responsive-{width}-{theme}-{scale}x",
                             lambda w=width, t=theme, s=scale: self.responsive_cell(w, t, s))
        # Outcome fixtures in dark mode, rather than only a dark main screen.
        for scenario in ("failed", "counter", "uncertain", "span"):
            self.reset(scenario)
            self.b.evaluate("designStudy.setTheme('dark')")
            self.theme = "dark"
            self.capture("dark-" + scenario)

    def responsive_cell(self, width, theme, scale):
        self.ready_review(True)
        self.device(width)
        self.b.evaluate(f"designStudy.setTheme('{theme}')")
        self.theme = theme
        normal = self.font_metrics()
        self.b.evaluate(f"designStudy.setTextScale({scale})")
        self.scale = scale
        self.device(width)
        ratios = {}
        if scale == 2:
            enlarged = self.font_metrics()
            ratios = {key: {"normal": info["pixels"], "large": enlarged.get(key, {}).get("pixels"),
                            "ratio": enlarged.get(key, {}).get("pixels", 0) / info["pixels"]}
                      for key, info in normal.items() if info["pixels"] > 0}
        # Capture both ends before asserting: a failed geometry assertion should
        # still leave the actual failed cell's visual evidence, not skip it.
        measured = self.layout()
        self.results.append({"case": self.case, "path": "geometry", "expected": "scrollable content with reachable primary", "actual": measured, "pass": not measured["bad"] and measured["send"]["hit"]})
        self.dom("p.querySelector('.main').scrollTo(0,0)")
        self.capture(f"review-{width}-{theme}-{scale}x-top")
        self.dom("p.querySelector('.main').scrollTo(0,p.querySelector('.main').scrollHeight)")
        self.capture(f"review-{width}-{theme}-{scale}x-bottom")
        if scale == 2:
            self.results.append({"case": self.case, "path": "text-sizes", "expected": "every nondecorative text run is 2x, including labels, ruby and rendered SVG text", "actual": ratios, "pass": bool(ratios) and all(1.98 <= item["ratio"] <= 2.02 for item in ratios.values())})
            self.truth("true-200-percent", bool(ratios) and all(1.98 <= item["ratio"] <= 2.02 for item in ratios.values()), "all observed nondecorative text sizes double")
        self.check("phone-width", width, measured["phoneWidth"])
        self.check("horizontal-overflow", False,
                   measured["documentOverflow"] or measured["phoneScrollWidth"] > measured["phoneClientWidth"] + 1)
        self.check("no-hidden-or-unreachable-content", [], measured["bad"])
        self.check("Send-reachable", {"enabled": True, "hit": True},
                   {k: measured["send"][k] for k in ("enabled", "hit")})

    def pitch_text_contrast(self):
        for theme in ("light", "dark"):
            self.reset("readings")
            self.b.key("Escape")
            self.b.evaluate(f"designStudy.setTheme('{theme}')")
            labels = self.dom("[...p.querySelectorAll('.word-hero [data-pitch-katakana] text')].map(e=>{const s=getComputedStyle(e);return {text:e.textContent,fill:s.fill,opacity:parseFloat(s.opacity),background:getComputedStyle(p.querySelector('.word-hero')).backgroundColor}})")
            self.truth(theme + " actual SVG text exists", bool(labels), "pitch labels are rendered")
            for label in labels:
                foreground = rgba(label["fill"])
                foreground[3] *= label["opacity"]
                ratio = contrast(composite(foreground, rgba(label["background"])), rgba(label["background"]))
                self.results.append({"case": self.case, "path": theme + "/SVG-fill/" + label["text"],
                                     "expected": ">=4.5:1 actual text fill including opacity", "actual": {**label, "ratio": ratio}, "pass": ratio >= 4.5})
                self.truth(theme + " visible pitch text " + label["text"], ratio >= 4.5, "rendered SVG fill contrasts at least 4.5:1")

    def requested_progress(self):
        for scenario, step in (("type-empty", 1), ("entries", 2), ("failed", 2), ("uncertain", 3)):
            self.reset(scenario)
            self.check(scenario + " top progress", {"role": "progressbar", "min": "1", "max": "3", "now": str(step), "segments": 3}, self.dom("(()=>{const e=p.querySelector('.flow-progress');return e?{role:e.getAttribute('role'),min:e.getAttribute('aria-valuemin'),max:e.getAttribute('aria-valuemax'),now:e.getAttribute('aria-valuenow'),segments:e.children.length}:null})()"))
        self.capture("restored-top-progress")

    def requested_pills(self):
        self.reset("entries")
        self.b.key("Escape")
        self.check("every word token is a button pill", True, self.dom("[...p.querySelectorAll('[data-token-start]')].every(e=>e.matches('button.word-pill') && e.hasAttribute('aria-pressed'))"))
        self.check("pill touch targets >=48px", True, self.dom("[...p.querySelectorAll('.word-pill')].every(e=>{const r=e.getBoundingClientRect();return r.width>=48&&r.height>=48})"))
        self.token("図書館")
        self.state_is(entryId="library", selection={"start": 0, "end": 3})
        self.capture("tapped-word-pill-target")

    def requested_pitch(self):
        self.ready_review()
        self.check("straight pitch segments without axes", True, self.dom("(()=>{const e=p.querySelector('[data-pitch-katakana]'),d=e.querySelector('path').getAttribute('d');return d.includes('L')&&!/[HVQC]/.test(d)&&![...e.querySelectorAll('text')].some(t=>/^(high|low)$/.test(t.textContent))})()"))
        self.check("photo visibly present in Review", True, self.dom("(()=>{const e=p.querySelector('.context-strip img'),r=e?.getBoundingClientRect(),m=p.querySelector('.main').getBoundingClientRect();return !!e&&e.complete&&e.naturalWidth>0&&r.height>=128&&r.top>=m.top-1&&r.bottom<=m.bottom+1})()"))
        self.check("Review photo fills its slot", {"fit": "cover"}, self.dom("(()=>{const e=p.querySelector('.context-strip img');return {fit:getComputedStyle(e).objectFit}})()"))
        self.capture("review-photo-straight-pitch")

    def requested_camera(self):
        self.reset()
        self.demo("photo")
        self.device(360, 828)
        measurement = self.dom("(()=>{const s=p.querySelector('.camera-stage'),v=p.querySelector('.viewfinder'),c=p.querySelector('[data-action=capture]');if(!s)return null;const a=s.getBoundingClientRect(),b=v.getBoundingClientRect(),r=c.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return {centered:Math.abs(a.x+a.width/2-b.x-b.width/2)<1&&Math.abs(a.y+a.height/2-b.y-b.height/2)<1,inside:b.left>=a.left&&b.right<=a.right+1&&b.top>=a.top&&b.bottom<=a.bottom+1,shutter:hit===c||c.contains(hit),height:b.height,width:b.width,overflow:p.scrollWidth>p.clientWidth+1}})()")
        self.truth("360x828 centered portrait camera + reachable shutter", measurement and measurement["centered"] and measurement["inside"] and measurement["shutter"] and not measurement["overflow"], "portrait preview centered within its camera area; controls visible and unobscured")
        self.check("portrait phone width", 360, self.dom("p.getBoundingClientRect().width"))
        self.capture("camera-360x828")
        self.click("capture")
        self.capture("photo-confirm-360x828")
        self.click("use-photo")
        self.book()
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.check("portrait photo retained in Review", True, self.dom("!!p.querySelector('.context-strip img')"))
        self.check("no landscape composition control", 0, self.b.evaluate("document.querySelectorAll('#orientation-toggle,.landscape-phones').length"))
        self.check("review photo fills its slot", {"fit": "cover", "position": "50% 18%"}, self.dom("(()=>{const e=p.querySelector('.context-strip img'),s=getComputedStyle(e);return {fit:s.objectFit,position:s.objectPosition}})()"))
        before = self.state()
        self.device(390, 844)
        self.check("viewport resize preserves the full draft", before, self.state())
        self.device(360, 828)
        self.capture("resize-return-review-photo")
        # A landscape source image in the same portrait composition.
        self.reset()
        self.demo("photo")
        self.device(360, 828)
        self.b.evaluate("designStudy.setPhotoVariant('landscape')")
        self.b.wait("(()=>{const e=document.querySelector('[data-phone=live] .viewfinder img');return !!e&&e.complete&&e.naturalWidth>0})()")
        self.check("phone remains portrait with landscape photo", 360, self.dom("p.getBoundingClientRect().width"))
        self.check("landscape source image", True, self.dom("(()=>{const e=p.querySelector('.viewfinder img');return !!e&&e.naturalWidth>e.naturalHeight})()"))
        self.capture("input-landscape-photo")
        self.click("capture")
        self.capture("photo-confirm-landscape-photo")
        self.click("use-photo")
        self.book()
        self.click("continue")
        self.b.wait("designStudy.phones.live.stage==='review'")
        self.check("landscape photo fills Review slot", {"fit": "cover"}, self.dom("(()=>{const e=p.querySelector('.context-strip img');return {fit:getComputedStyle(e).objectFit}})()"))
        self.check("phone stays portrait in Review", 360, self.dom("p.getBoundingClientRect().width"))
        self.capture("review-landscape-photo")
        self.b.evaluate("designStudy.setPhotoVariant('portrait')")

    def errors(self):
        captured = self.b.evaluate("({windowErrors:window.__verifyErrors||[],studyErrors:window.__designErrors||[]})")
        self.check("uncaught/rejected JavaScript errors", {"windowErrors": [], "studyErrors": []}, captured)
        self.check("CDP exceptions/console.error/log errors", [], self.b.errors)
        self.check("no real network/provider/export requests", [], self.b.network)

    def legacy_red(self):
        self.b.evaluate("designStudy.setView('prototype');Object.assign(designStudy.phones.live,{stage:'review',status:'uncertain',target:2,overlay:null});designStudy.renderPhone(designStudy.phones.live)")
        before = self.state()
        self.capture("legacy-uncertain-before")
        self.b.click('[data-action="back"]')
        after = self.state()
        self.capture("legacy-uncertain-back-after")
        self.results.append({"case": self.case, "path": "legacy/P0-1/observed transition", "expected": "uncertain draft remains blocked", "actual": {"before": {k: before[k] for k in ("stage", "status", "target")}, "after": {k: after[k] for k in ("stage", "status", "target")}}, "pass": after.get("status") == "uncertain"})
        # Continue the actual reported exploit to prove reachability of Send.
        self.b.click('[data-action="generate"]')
        self.b.wait("designStudy.phones.live.status==='ready'", timeout=5)
        self.b.click('[data-action="send"]')
        observed = self.state()
        self.capture("legacy-duplicate-sending")
        self.check("legacy/P0-1/Back→Generate→Send must remain blocked", False, observed.get("status") == "sending")


def self_test():
    """Fast transport/math sanity tests; live legacy RED tests CDP end-to-end."""
    import unittest

    class Tests(unittest.TestCase):
        def test_contrast(self):
            self.assertAlmostEqual(contrast(rgba("rgb(0, 0, 0)"), rgba("rgb(255, 255, 255)")), 21)
            self.assertAlmostEqual(contrast(rgba("rgb(32, 32, 32)"), rgba("rgb(32, 32, 32)")), 1)
            self.assertEqual(composite([255, 0, 0, .5], [0, 0, 0, 1]), [127.5, 0, 0, 1])

        def test_png(self):
            self.assertEqual(png_size(b"\x89PNG\r\n\x1a\n" + b"\0\0\0\rIHDR" + struct.pack(">II", 320, 828)), [320, 828])
            with self.assertRaises(ValueError):
                png_size(b"not a screenshot")

        def test_websocket_fragment_ping(self):
            reader, writer = socket.socketpair()
            ws = WebSocket.__new__(WebSocket)
            ws.sock, ws.buffer = reader, bytearray()
            writer.sendall(b'\x01\x04{"ok' + b'\x89\x01x' + b'\x80\x07":true}')
            self.assertEqual(ws.receive(), {"ok": True})
            response = writer.recv(100)
            self.assertEqual(response[0], 0x8a)
            self.assertTrue(response[1] & 0x80)
            self.assertEqual(response[6] ^ response[2], ord("x"))
            ws.close()
            writer.close()

    return 0 if unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Tests)).wasSuccessful() else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--html", type=Path, default=HERE / "flow.html")
    parser.add_argument("--output", type=Path, default=HERE / "verification.generated.json")
    parser.add_argument("--screens", type=Path, help="Capture real Chromium PNGs and manifest.json here")
    parser.add_argument("--no-screens", action="store_true", help="Skip screenshots even when --screens is given")
    parser.add_argument("--chromium", default=shutil.which("chromium"))
    parser.add_argument("--scratch", type=Path, default=Path(os.environ.get("TMPDIR", "/srv/hermes/.hermes/cache/scratch")))
    parser.add_argument("--legacy-red", action="store_true", help="Run only the legacy uncertain→Back→Generate→Send repro; expected exit 1")
    parser.add_argument("--self-test", action="store_true", help="Test runner primitives without a browser")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    args.html, args.output = args.html.resolve(), args.output.resolve()
    if args.screens:
        args.screens = args.screens.resolve()
    args.scratch = args.scratch.resolve()
    report = {"schemaVersion": 1, "command": [sys.executable, *sys.argv],
              "harnessSha256": sha256(Path(__file__)),
              "startedAt": datetime.now(timezone.utc).isoformat(),
              "html": str(args.html), "mode": "legacy-red" if args.legacy_red else "regression",
              "python": sys.version, "platform": platform.platform(), "dependency": "Python standard library + Chromium",
              "nativeAcceptance": False,
              "scope": "Offline HTML design-study behavior, DOM, keyboard, computed styles and real Chromium screenshots. Not Compose, native fontScale, TalkBack, IME, rotation, camera, OCR, cloud, playback or Anki integration.",
              "deviceFixtureCSS": DEVICE_CSS, "checks": [], "screenshots": []}
    browser = suite = None
    try:
        if not args.html.is_file():
            raise FileNotFoundError(args.html)
        if not args.chromium:
            raise RuntimeError("Chromium is not on PATH; pass --chromium PATH")
        if not args.scratch.is_dir():
            raise RuntimeError(f"Scratch directory is missing: {args.scratch}")
        report["htmlSha256"] = sha256(args.html)
        browser = Browser(args.html, args.chromium, args.scratch, HERE / "assets")
        report["chromium"] = browser.version
        report["browserCommand"] = browser.command
        report["cdpPort"] = browser.port
        suite = Suite(browser, args)
        if args.legacy_red:
            suite.run("legacy-P0-1", suite.legacy_red)
        else:
            suite.run("API-contract", suite.contract)
            if browser.evaluate("designStudy.apiVersion===2"):
                for name, function in [("requested-progress", suite.requested_progress), ("requested-word-pills", suite.requested_pills),
                                       ("requested-straight-pitch-photo", suite.requested_pitch), ("requested-camera-portrait-and-landscape-photo", suite.requested_camera),
                                       ("happy-path", suite.happy), ("P0-1-uncertain-escapes", suite.uncertain),
                                       ("P0-2-edit-retokenize", suite.edited), ("P0-3-contiguous-spans", suite.spans),
                                       ("P0-4-missing-entry-recovery", suite.recovery), ("network-retry-cancel-discard", suite.network_case),
                                       ("typed-no-photo-notes", suite.typed), ("caption-icons-pitch-handles", suite.ui),
                                       ("notification-expiry-focus", suite.notifications),
                                       ("keyboard-focus", suite.keyboard), ("non-text-contrast", suite.contrast_case),
                                       ("pitch-SVG-text-contrast", suite.pitch_text_contrast),
                                       ("responsive-dark-true-200-percent", suite.responsive)]:
                    suite.run(name, function)
            else:
                report["blocked"] = "API v2 is absent; full suite not run. Use --legacy-red to reproduce the original P0 escape."
        suite.run("JavaScript-and-network-errors", suite.errors)
    except Exception as error:
        report["checks"].append({"case": "runner", "path": "startup/execution", "expected": "working browser and artifact", "actual": str(error), "pass": False, "traceback": traceback.format_exc()})
        print(f"FAIL runner → expected working browser/artifact → actual {error}", flush=True)
    finally:
        if suite:
            report["checks"].extend(suite.results)
            report["screenshots"] = suite.captures
        if browser:
            report["browserErrors"] = browser.errors
            report["externalRequests"] = browser.network
            browser.close()
        report["finishedAt"] = datetime.now(timezone.utc).isoformat()
        report["totals"] = {"checks": len(report["checks"]),
                            "passed": sum(check["pass"] for check in report["checks"]),
                            "failed": sum(not check["pass"] for check in report["checks"]),
                            "screenshots": len(report["screenshots"])}
        report["pass"] = bool(report["checks"]) and report["totals"]["failed"] == 0 and not report.get("blocked")
        if args.screens and not args.no_screens:
            args.screens.mkdir(parents=True, exist_ok=True)
            manifest = {"generatedAt": report["finishedAt"], "command": report["command"],
                        "htmlSha256": report.get("htmlSha256"), "screenshots": report["screenshots"]}
            (args.screens / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{'PASS' if report['pass'] else 'FAIL'} {json.dumps(report['totals'])} → {args.output}", flush=True)
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
