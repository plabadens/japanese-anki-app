#!/usr/bin/env python3
"""Exercise CI entry-point sequencing with a fake Gradle executable, without Android."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DEBUG = "scripts/ci-build-apk.sh"
RELEASE = ".github/scripts/ci-build-release-apk.sh"


class CiScriptsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        directory = Path(self.temp.name)
        self.log = directory / "gradle.jsonl"
        gradle = directory / "gradle"
        gradle.write_text(
            f"#!{sys.executable}\n"
            "import json, os, sys\n"
            "with open(os.environ['GRADLE_TEST_LOG'], 'a') as log:\n"
            "    log.write(json.dumps(sys.argv[1:]) + '\\n')\n"
            "sys.exit(1 if os.environ.get('FAIL_TASK') in sys.argv[1:] else 0)\n"
        )
        gradle.chmod(0o755)
        keystore = directory / "test.keystore"
        keystore.touch()
        self.env = {
            **os.environ,
            "PATH": f"{directory}:{os.environ['PATH']}",
            "ANDROID_HOME": str(directory / "sdk with spaces"),
            "GRADLE_TEST_LOG": str(self.log),
            "JAPANESE_ANKI_RELEASE_KEYSTORE": str(keystore),
            "JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD": "test-only",
            "JAPANESE_ANKI_RELEASE_KEY_ALIAS": "test-only",
        }
        self.env.pop("FAIL_TASK", None)

    def run_script(self, script, *args):
        self.log.unlink(missing_ok=True)
        return subprocess.run(
            ["bash", script, *args], cwd=ROOT, env=self.env,
            text=True, capture_output=True, check=False,
        )

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def tasks(self):
        return [[arg for arg in call if not arg.startswith("-")] for call in self.calls()]

    def test_default_entry_points_keep_checks_before_assembly(self):
        for script, variant in [(DEBUG, "Debug"), (RELEASE, "Release")]:
            with self.subTest(variant=variant):
                result = self.run_script(script)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual([
                    ["verifyFast"], [":app:renderDesignStates", f"assemble{variant}"],
                ], self.tasks())
                for call in self.calls():
                    self.assertIn("--build-cache", call)
                    self.assertIn(
                        f"-Pandroid.aapt2FromMavenOverride={self.env['ANDROID_HOME']}/build-tools/36.0.0/aapt2",
                        call,
                    )

    def test_gated_build_jobs_only_assemble(self):
        for script, variant in [(DEBUG, "Debug"), (RELEASE, "Release")]:
            with self.subTest(variant=variant):
                result = self.run_script(script, "--build-only")
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual([[f"assemble{variant}"]], self.tasks())

    def test_failed_gate_prevents_render_and_assembly(self):
        self.env["FAIL_TASK"] = "verifyFast"
        for script in [DEBUG, RELEASE]:
            with self.subTest(script=script):
                result = self.run_script(script)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual([["verifyFast"]], self.tasks())

    def test_unknown_arguments_do_not_invoke_gradle(self):
        for script in [DEBUG, RELEASE]:
            for args in [("--skip-tests",), ("--build-only", "extra")]:
                with self.subTest(script=script, args=args):
                    self.assertEqual(2, self.run_script(script, *args).returncode)
                    self.assertEqual([], self.calls())

    def test_build_only_release_still_requires_signing(self):
        for missing in [
            "JAPANESE_ANKI_RELEASE_KEYSTORE",
            "JAPANESE_ANKI_RELEASE_KEYSTORE_PASSWORD",
            "JAPANESE_ANKI_RELEASE_KEY_ALIAS",
        ]:
            with self.subTest(missing=missing):
                saved = self.env.pop(missing)
                try:
                    self.assertNotEqual(0, self.run_script(RELEASE, "--build-only").returncode)
                    self.assertEqual([], self.calls())
                finally:
                    self.env[missing] = saved


if __name__ == "__main__":
    unittest.main()
