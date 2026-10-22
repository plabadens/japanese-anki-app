# Emulator readiness and audit handoff

> **HISTORICAL (2026-10-22):** API-35 runner evidence recorded on 2026-10-14. Superseded by the
> API-37 emulator recipe in `docs/BEST-PRACTICES.md` §5 and by
> `docs/archive/evidence/api37-nix-provisioning.md`; no claim here applies to the current runner.

**Historical runner evidence:** this API-35 environment predates the redesign. Current acceptance targets API 37 on the x86_64 emulator (SPEC §2 and §14); do not count the old runner or ARM64-emulator discussion below as current acceptance. Recheck live processes, paths, and artifacts before using any commands.

Verified at `2026-10-14T14:23:17+02:00` (host clock). This is runner-readiness evidence, not a complete application acceptance report.

## Working runner

- Primary checkout: `/srv/hermes/workspace/japanese-anki-app`.
- Existing AVD: `japanese-anki-pixel7-api35`, Pixel 7, 1080×2400, 420 dpi, Google APIs, x86_64, API 35.
- ADB serial: `emulator-5554`, default TCP ADB server. Unset `ADB_SERVER_SOCKET` for direct ADB calls; always specify the serial.
- Current tooling (`2026-10-21`) replaces direct `adb` calls: `scripts/emulator-adb run <adb args>` pins the emulator serial, rejects physical-device serials and conflicting selectors, and unsets `ADB_SERVER_SOCKET` itself. `scripts/emulator.sh` and `scripts/ui-journey.py` both use it.
- Emulator: 37.1.11.0. KVM is accessible.
- The emulator was left running. At final verification, process 2974829 had survived tool-call boundaries for over five minutes; ADB reported `device` and `sys.boot_completed=1`.
- APK installed successfully from `.worktrees/t_10338bf0/app/build/outputs/apk/debug/app-debug.apk`, built from implementation commit `3ac8ad887d01d9947d283c9db034a4d686ab5487`.

Run the wrapper from the **primary checkout** for startup, because its AVD/state paths are relative to the current working directory:

```sh
cd /srv/hermes/workspace/japanese-anki-app
nix run .#emulator-wrapper -- status
# Only if not already running:
nix run .#emulator-wrapper -- start
nix run .#emulator-wrapper -- launch
nix run .#emulator-wrapper -- dump .tmp-nix/emulator-health/ui.xml
nix run .#emulator-wrapper -- screenshot .tmp-nix/emulator-health/app.png
```

## Checks actually exercised

- Wrapper startup completed; ADB status was read back repeatedly, including from the #8 worktree.
- APK installation returned `Success`; camera permission grant and `MainActivity` launch succeeded.
- Activity diagnostics identified `dev.pierre.japaneseanki/.MainActivity` as resumed.
- Photo capture screen rendered a live synthetic camera preview; UI hierarchy and screenshot capture succeeded.
- Tapping Type via coordinates derived from the UI hierarchy switched to the Type form.
- Dark theme and system font scale `2.0` rendered successfully; screenshot and UI hierarchy were captured.
- Font scale was restored to `1.0` and theme to light. App remains on the empty Type capture screen.
- The crash log buffer was empty after these checks.
- TalkBack (`com.google.android.marvin.talkback`) and Google TTS (`com.google.android.tts`) are installed. Presence does not prove a completed TalkBack audit, Japanese voice availability, or offline synthesis.

Evidence in the primary checkout:

- `.tmp-nix/emulator-health/app.png`: light Photo capture with synthetic camera preview.
- `.tmp-nix/emulator-health/ui.xml`: Photo capture hierarchy.
- `.tmp-nix/emulator-health/type.png`: Type form.
- `.tmp-nix/emulator-health/dark-font200.png`: dark Type form, 200% system font.
- `.tmp-nix/emulator-health/dark-font200.xml`: corresponding hierarchy.
- `.tmp-nix/emulator.log`: startup diagnostics.

## Failed-run distinction

The #8 worker's isolated AVD logs show a pre-ADB SIGSEGV with `process_memory_range.cc:75 read out of range`. That diagnostic does not by itself establish a root cause. The primary-checkout wrapper started successfully on this host; both SDK paths inspected resolve to the **same** emulator 37.1.11 executable. Consequently the binary is present and demonstrably capable of booting here; a host-wide unusable emulator is not supported by the latest evidence.

The failed custom AVD setup differs from the known-good Pixel 7 configuration (one inspected custom AVD had the default 320×640/160dpi profile and 96M RAM setting). These are differences to investigate, not proven causes. No emulator binary, system image, AVD data, or app source was patched to obtain the successful run. Do not claim a permanent fix for every isolated AVD or intermittent startup failure.

## Shared-session ownership

The readiness session is handed to audit preparation task `t_cbbd2877`. Coordinate any later transfer explicitly. Other workers must not stop/recreate the shared AVD, wipe its data, install conflicting APKs, or launch another emulator on port 5554 during that audit. Avoid the wrapper's `session` subcommand on this shared instance: its EXIT trap stops the emulator even if it was already running.

This verifies **API 35 x86_64 only**. Other required API/ABI combinations, full Review-route audits, TalkBack interaction, offline Japanese voice checks, and AnkiDroid acceptance remain separate work. A working emulator must not be mistaken for a release acceptance pass.
