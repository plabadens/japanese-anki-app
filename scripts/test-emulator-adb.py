#!/usr/bin/env python3
"""Regression checks for emulator-only ADB selection and UI dump isolation."""

from __future__ import annotations

import concurrent.futures
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "scripts/emulator-adb"
UI_JOURNEY = ROOT / "scripts/ui-journey.py"
PNG = b"\x89PNG\r\n\x1a\n\x00binary\xffpayload"
XML = b"<hierarchy><node text=\"fixture\" bounds=\"[0,0][1,1]\" /></hierarchy>"


class EmulatorAdbTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="emulator adb tests ")
        self.root = Path(self.temporary.name)
        self.sdk = self.root / "pinned SDK with spaces"
        self.adb = self.sdk / "platform-tools/adb"
        self.adb.parent.mkdir(parents=True)
        self.log = self.root / "adb-calls.jsonl"
        self.tmpdir = self.root / "configured scratch"
        self.tmpdir.mkdir()
        self.adb.write_text(
            "#!/usr/bin/env python3\n"
            "import json, os, sys\n"
            "args = sys.argv[1:]\n"
            "entry = {'argv': args, 'socket': os.environ.get('ADB_SERVER_SOCKET', '<unset>'), 'executable': sys.argv[0]}\n"
            "fd = os.open(os.environ['FAKE_ADB_LOG'], os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)\n"
            "os.write(fd, (json.dumps(entry, ensure_ascii=False) + '\\n').encode())\n"
            "os.close(fd)\n"
            "if 'exec-out' in args and 'screencap' in args:\n"
            f"    sys.stdout.buffer.write(bytes.fromhex('{PNG.hex()}'))\n"
            "if 'uiautomator' in args and 'dump' in args:\n"
            "    if os.environ.get('FAKE_ADB_FAIL_DUMP'):\n"
            "        print('ERROR: null root node returned by UiTestAutomationBridge.')\n"
            "    else:\n"
            "        print('UI hierchary dumped to: ' + args[-1])\n"
            "if 'pull' in args:\n"
            f"    open(args[-1], 'wb').write(bytes.fromhex('{XML.hex()}'))\n",
            encoding="utf-8",
        )
        self.adb.chmod(0o755)
        self.env = os.environ.copy()
        self.env.update(
            {
                "ANDROID_SDK_ROOT": str(self.sdk),
                "ADB_SERVER_SOCKET": "localfilesystem:/must-be-unset",
                "FAKE_ADB_LOG": str(self.log),
                "TMPDIR": str(self.tmpdir),
                "EMULATOR_ADB": str(HELPER),
            }
        )
        self.env.pop("EMULATOR_PORT", None)
        self.env.pop("ANDROID_SERIAL", None)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def calls(self) -> list[dict[str, object]]:
        if not self.log.exists():
            return []
        return [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines()]

    def invoke(self, *args: str, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(HELPER), *args],
            env=self.env if env is None else env,
            check=False,
            capture_output=True,
            text=True,
        )

    def test_default_target_pinned_adb_and_exact_argv(self) -> None:
        result = self.invoke("run", "shell", "input", "text", "東京 city / café")
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        self.assertEqual(
            calls[0]["argv"],
            ["-s", "emulator-5554", "shell", "input", "text", "東京 city / café"],
        )
        self.assertEqual(calls[0]["socket"], "<unset>")
        self.assertEqual(calls[0]["executable"], str(self.adb))

    def test_custom_port_and_custom_emulator_serial(self) -> None:
        port_env = self.env.copy()
        port_env["EMULATOR_PORT"] = "5556"
        self.assertEqual(self.invoke("serial", env=port_env).stdout.strip(), "emulator-5556")
        serial_env = self.env.copy()
        serial_env["ANDROID_SERIAL"] = "emulator-5558"
        self.assertEqual(self.invoke("serial", env=serial_env).stdout.strip(), "emulator-5558")
        result = self.invoke("run", "logcat", "-d", env=serial_env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.calls()[-1]["argv"], ["-s", "emulator-5558", "logcat", "-d"])

    def test_conflicting_selectors_fail_before_adb(self) -> None:
        env = self.env.copy()
        env.update({"EMULATOR_PORT": "5554", "ANDROID_SERIAL": "emulator-5556"})
        result = self.invoke("run", "shell", "getprop", env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("conflicting emulator selectors", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_invalid_serials_and_ports_fail_before_adb(self) -> None:
        for value in ("phone-serial", "emulator-0", "emulator-65536", "emulator-05554"):
            with self.subTest(serial=value):
                env = self.env.copy()
                env["ANDROID_SERIAL"] = value
                result = self.invoke("run", "shell", "getprop", env=env)
                self.assertNotEqual(result.returncode, 0)
        for value in ("nope", "0", "65536", "05554"):
            with self.subTest(port=value):
                env = self.env.copy()
                env["EMULATOR_PORT"] = value
                result = self.invoke("run", "shell", "getprop", env=env)
                self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])

    def test_global_adb_target_override_is_rejected(self) -> None:
        result = self.invoke("run", "-s", "physical-phone", "shell", "getprop")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])

    def test_binary_screenshot_is_preserved(self) -> None:
        destination = self.root / "screenshot with spaces.png"
        result = subprocess.run(
            [sys.executable, str(UI_JOURNEY), "shot", str(destination)],
            env=self.env,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(destination.read_bytes(), PNG)
        self.assertEqual(
            self.calls()[0]["argv"],
            ["-s", "emulator-5554", "exec-out", "screencap", "-p"],
        )

    def test_concurrent_dumps_use_unique_configured_scratch_paths(self) -> None:
        def dump() -> subprocess.CompletedProcess[str]:
            return subprocess.run(
                [sys.executable, str(UI_JOURNEY), "dump"],
                env=self.env,
                check=False,
                capture_output=True,
                text=True,
            )

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda _index: dump(), range(2)))
        for result in results:
            self.assertEqual(result.returncode, 0, result.stderr)
        calls = [call["argv"] for call in self.calls()]
        remote_dumps = {
            argv[-1]
            for argv in calls
            if isinstance(argv, list) and len(argv) >= 6 and argv[2:5] == ["shell", "uiautomator", "dump"]
        }
        local_dumps = {
            argv[-1]
            for argv in calls
            if isinstance(argv, list) and len(argv) >= 5 and argv[2] == "pull"
        }
        self.assertEqual(len(remote_dumps), 2)
        self.assertEqual(len(local_dumps), 2)
        for path in local_dumps:
            self.assertTrue(Path(path).is_relative_to(self.tmpdir), path)

    def test_devices_mode_is_host_scoped(self) -> None:
        result = self.invoke("devices", "-l")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.calls()[0]["argv"], ["devices", "-l"])
        self.assertEqual(self.calls()[0]["socket"], "<unset>")

    def test_failed_hierarchy_dump_reports_device_error(self) -> None:
        env = self.env.copy()
        env["FAKE_ADB_FAIL_DUMP"] = "1"
        result = subprocess.run(
            [sys.executable, str(UI_JOURNEY), "dump"],
            env=env,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("null root node", result.stderr)
        calls = [call["argv"] for call in self.calls() if isinstance(call["argv"], list)]
        self.assertFalse(any(argv[2:3] == ["pull"] for argv in calls))

    def test_wrapper_dump_uses_unique_remote_path_and_cleans_up(self) -> None:
        destination = self.root / "wrapper dump/capture.xml"
        result = subprocess.run(
            [str(ROOT / "scripts/emulator.sh"), "dump", str(destination)],
            cwd=self.root,
            env=self.env,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(destination.read_bytes(), XML)
        calls = [call["argv"] for call in self.calls() if isinstance(call["argv"], list)]
        remote = [argv[-1] for argv in calls if argv[2:5] == ["shell", "uiautomator", "dump"]]
        self.assertEqual(len(remote), 1)
        self.assertRegex(remote[0], r"^/sdcard/_dump-[0-9]+-[0-9]+\.xml$")
        self.assertEqual(calls[1][2:], ["pull", remote[0], str(destination)])
        self.assertEqual(calls[2][2:], ["shell", "rm", "-f", remote[0]])


if __name__ == "__main__":
    unittest.main(verbosity=2)
