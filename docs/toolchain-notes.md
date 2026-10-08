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

**Status:** open. Direct Gradle release builds work with dependencies already
present in the local Gradle cache, but that does not establish a sandboxed Nix
build. The current Nix debug build stops before compilation because the locked
Gradle cache lacks the declared ML Kit artifacts
`com.google.mlkit:text-recognition-japanese:16.0.1` and
`com.google.mlkit:translate:17.0.3`. The release task may also need
`com.android.tools.lint:lint-gradle:31.13.2`, which is not in
`nix/gradle/deps.json`. Refresh and validate the lock using
`scripts/update-gradle-deps.sh` in an environment where the configured Maven
repositories are reachable, with both `testDebugUnitTest assembleDebug` and
`assembleRelease` represented in the captured configurations. Do not treat a
successful build from a warm Gradle cache as proof of the Nix dependency lock
or offline reproducibility. The sandbox used for this verification blocks
outbound Maven access, so lock regeneration could not be verified here.

## Nix Android AAPT2 selection

The APK derivation explicitly sets
`-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2`.
The same SDK-provided binary succeeded in a sandboxed direct Gradle debug build
and release build, including resource processing; there is no evidence that an
AGP/AAPT2 compatibility change is needed. Keep this override rather than
letting AGP resolve its Maven-hosted AAPT2 binary, which is incompatible with
the NixOS sandbox. This does not address the separate Gradle dependency-cache
coverage above.

## Emulator as the default test target

**Status:** working, with environment-specific caveats documented in
`scripts/emulator.sh` and its README section. Two constraints discovered here:
the emulator only registers with a default-port ADB server (not the project's
`localfilesystem:` socket), and a sandbox that kills the launcher's process tree
requires a single-session mode (boot, install, capture, shut down in one command).
