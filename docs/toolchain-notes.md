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

## Release-APK lint artifacts missing from the Gradle lock

**Status:** open. `nix build .#apk` (unsigned release) fails at
`:app:lintVitalAnalyzeRelease` because `com.android.tools.lint:lint-gradle:31.13.2`
is not present in `nix/gradle/deps.json`; the update script only recorded what its
own Gradle invocation requested. Options: refresh the lock with a run that
includes the release tasks, or skip `lintVital` for the Nix release build
(the release derivation already runs `testDebugUnitTest` as its check). This is
the last known blocker before the two-clean-build reproducibility comparison in
`SPEC.md` §12.3.

## Emulator as the default test target

**Status:** working, with environment-specific caveats documented in
`scripts/emulator.sh` and its README section. Two constraints discovered here:
the emulator only registers with a default-port ADB server (not the project's
`localfilesystem:` socket), and a sandbox that kills the launcher's process tree
requires a single-session mode (boot, install, capture, shut down in one command).
