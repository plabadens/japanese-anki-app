# Toolchain upgrade notes

Deferred build-toolchain work that is deliberately not done yet. Each item records
why it is deferred and what must be verified before the change is made.

## Android Gradle Plugin 9.x (do this later, on purpose)

**Status:** deferred. The project is pinned to AGP 8.13.2 and Gradle 8.14.4
(`gradle/libs.versions.toml`, `flake.nix`).

**Why it matters:** `app/build.gradle.kts` sets `compileSdk = 37`, `targetSdk = 37`
and `minSdk = 35` as required by `SPEC.md` §2. AGP 8.13.2 was tested up to API
level 36.1, so every build prints:

```
WARNING: We recommend using a newer Android Gradle plugin to use compile SDK version 37.0
This Android Gradle plugin (8.13.2) was tested up to compile SDK version 36.1.
```

That is a warning about untested *tooling* paths (resource merging, lint, R8,
manifest handling for API 37) — not a runtime problem, and the app uses no
API-37-only platform features. The build still produces a working APK.

**When to revisit:** before the first release that depends on API 37 platform
features, or when AGP 9.x is the current stable that the pinned nixpkgs provides.

**What the upgrade requires (verify each, do not assume):**

- AGP 9.4 advertises a maximum API level of 37; AGP 9.0 still caps at 36.1, so the
  specific 9.x minor matters.
- Gradle **9.6.0 or newer** for AGP 9.4 (AGP 9.0 needs Gradle 9.1.0+). The flake
  currently pins Gradle 8.14.4, so `flake.nix`, `nix/gradle/deps.json` and any
  `gradle/*` configuration must move together.
- AGP 9 breaking changes to check against this codebase: non-final `R` class in
  application modules by default, `targetSdk` defaulting to `compileSdk`, Kotlin
  Gradle plugin pairing, and build-feature default changes.
- Regenerate the Gradle dependency lock (`scripts/update-gradle-deps.sh`) and
  re-verify the Nix build outputs (`.#debugApk`, `.#apk`) plus the offline
  reproducibility comparison in `SPEC.md` §12.3.
- Re-run the full unit-test suite and the on-device/emulator checks for the
  AnkiDroid provider, media import and native library loading.

**Interim option (not currently applied):** the warning can be silenced with
`android.suppressUnsupportedCompileSdk=37.0` in `gradle.properties`. It changes
nothing except the message, so it is only worth adding if the warning becomes
noise in CI. AndroidX itself uses this pattern.

## Gradle dependency cache coverage

**Status:** resolved for the APK the project ships in this pass. `nix build .#apk`
and `nix build .#debugApk` complete inside the sandbox (assemble plus
`testDebugUnitTest`) by resolving Maven artifacts through the Nix Gradle MITM
cache, so the first build needs network access. Rebuilds are not byte-identical and
nothing here claims they are. What remains open: the lock was populated for the
configurations exercised by `testDebugUnitTest`/`assembleDebug`/`assembleRelease`;
if a later change pulls new artifacts the sandbox build fails until
`./scripts/update-gradle-deps.sh` refreshes the lock. The historical gap was the
missing ML Kit artifacts
`com.google.mlkit:text-recognition-japanese:16.0.1` and
`com.google.mlkit:translate:17.0.3` (and `com.android.tools.lint:lint-gradle`
for lint tasks, which the shipped build does not run). Refresh and validate the lock using
`scripts/update-gradle-deps.sh` in an environment where the configured Maven
repositories are reachable, with both `testDebugUnitTest assembleDebug` and
`assembleRelease` represented in the captured configurations. Do not treat a
successful build from a warm Gradle cache as proof of the Nix dependency lock
or offline reproducibility.

## Nix Android AAPT2 selection

The APK derivation explicitly sets
`-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2`.
The same SDK-provided binary succeeded in a sandboxed direct Gradle debug build
and release build, including resource processing; there is no evidence that an
AGP/AAPT2 compatibility change is needed. Keep this override rather than
letting AGP resolve its Maven-hosted AAPT2 binary, which is incompatible with
the NixOS sandbox. This does not address the separate Gradle dependency-cache
coverage above.

## Filtered Nix source inputs

`flake.nix` does not hand `self` to the APK derivations or the core check. Each
build has its own explicit `lib.fileset.toSource` input set:

- `apkSource` — `app/` (manifest, Kotlin, resources, assets, native CMake, unit
  tests), root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` and
  `gradle/` (version catalog and wrapper metadata). Documentation, screenshots,
  `datasets/` and the tooling scripts are absent, so editing them does not rebuild
  the APK.
- `coreCheckSource` — the standalone Kotlin sources and unit tests that
  `scripts/test-core.sh` compiles, the three Python checks with the builders they
  import (`build_dictionary.py`, `package_unidic.py`), the packaged
  `app/src/main/assets/dictionary.sqlite.gz`, and the `datasets/jmdict/JMdict_e.gz`
  plus `datasets/kanjium/accents.txt` inputs whose hashes
  `scripts/test-dictionary-asset.py` recomputes.

Both sets are additive and explicit; there is no exclude list guessing at build
outputs. New files under `app/` (including new unit tests) are picked up without a
flake edit, while a new dataset or script read by the core check must be added to
the set on purpose. Source identity can be checked with:

```sh
nix eval --raw .#debugApk.src.outPath                 # APK source tree
nix eval --raw .#checks.x86_64-linux.core.drvPath     # core-check derivation
```

Editing documentation or screenshots leaves both unchanged; Kotlin, asset or
Gradle edits change the APK source tree; dataset, builder-script or
dictionary-asset edits change the core-check derivation while leaving the APK
source tree alone. These check *which* inputs feed each build — they are not proof
that the build succeeds, which needs a fresh sandboxed build.

## Emulator as the default test target

**Status:** working, with environment-specific caveats documented in
`scripts/emulator.sh` and its README section. Constraints discovered here:

- Device targeting is single-sourced in `scripts/emulator-adb` (installed as
  `emulator-adb` by the flake, and exported to the dev shell and the wrapper as
  `EMULATOR_ADB`). It resolves one emulator serial from `EMULATOR_PORT`
  (default `5554`) or `ANDROID_SERIAL=emulator-<port>`, refuses conflicting
  selectors and any physical-device serial, runs the pinned SDK's `adb`, and unsets
  `ADB_SERVER_SOCKET` for every device-directed command. `scripts/emulator.sh` and
  `scripts/ui-journey.py` both go through it; do not add an unqualified `adb` call.
- The emulator itself only registers with a default-port ADB server (not a
  `localfilesystem:` socket), which is why the helper always drops
  `ADB_SERVER_SOCKET`.
- A sandbox that kills the launcher's process tree requires a single-session mode
  (boot, install, capture, shut down in one command).
- The API-37.0 AVD boots with a 4 GB guest, so the host needs roughly 5 GB of free
  RAM per emulator. Booting it under memory pressure fails inside the emulator
  (crashpad `read out of range`, exit 139), not with a clean error.
- One instance per AVD. Two emulators on the same AVD corrupt the user data and
  the guest then loops restarting zygote; recover by recreating the AVD.
- `avdmanager` 22.0 prints `Could not load devices.xml` for the 37.0 system image
  but still writes a correct Pixel 7 profile, so the wrapper ignores that error.
- The emulator injects `$HOME/.android/adbkey` into a fresh userdata by creating
  `data/misc/adb` with mode `02750`. This host refuses an explicit SGID bit
  (`mkdir`/`chmod` return `EPERM`), after which the emulator dereferences the
  missing `adb_keys` file and dies with SIGSEGV. `scripts/emulator.sh` pre-creates
  that directory without SGID, which turns the failing `mkdir` into an `EEXIST`
  no-op. Without this every fresh AVD crashes on first boot.
