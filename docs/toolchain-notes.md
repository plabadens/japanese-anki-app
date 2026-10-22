# Toolchain upgrade notes

## Android Gradle Plugin 9.4.1 / Gradle 9.7.1 — verified upgrade

**Status:** applied on the toolchain-upgrade branch; this is a build-only change.
No screens, strings, navigation, onboarding, AnkiDroid gateway, or application
behaviour were changed.

**Resolved and tested tuple:**

| Component | Previous | Current | Reason |
| --- | --- | --- | --- |
| Android Gradle Plugin | 8.13.2 | 9.4.1 | Stable AGP 9 release that supports compile/target API 37 and supplies the public Material3 1.5 beta's required AGP generation. |
| Gradle | 8.14.4 | 9.7.1 (`pkgs.gradle_9`) | AGP 9.4 requires Gradle 9.6 or newer; the pinned nixpkgs `gradle_9` is 9.7.1. |
| Kotlin Android plugin | 2.2.20 external plugin | AGP built-in Kotlin 2.2.10 | AGP 9 has built-in Kotlin; remove `org.jetbrains.kotlin.android` instead of loading two Kotlin integrations. Keep the Compose compiler plugin aligned at 2.2.10. |
| Compose BOM | 2025.09.00 | 2026.09.00 | Provides the Compose 1.12 line required by Material3 1.5.0-beta02; Gradle resolves Compose UI to 1.12.1. |
| Material3 | 1.4.0 | 1.5.0-beta02 | Required public Expressive APIs; dependency insight selects beta02 over the BOM's 1.4.0 constraint. |
| activity-compose | 1.11.0 | 1.11.0 | Left unchanged: it already resolves with the selected Compose graph and passed compilation; no evidence justified a higher version. |
| JDK | 17 | 17 | Existing pinned JDK meets the AGP/Gradle requirements. |

This is the **minimum verified tuple for the approved API contract**, not a claim
that every earlier AGP/Compose combination was exhaustively tested. The old
AGP 8.13.2 + Material3 1.4.0 graph failed to compile the required public APIs;
Material3 1.5.0-beta02 resolves and compiles with this stable AGP/Gradle 9 stack.
`app/src/test/java/dev/pierre/japaneseanki/Material3ExpressiveApiCompileSmoke.kt`
remains in the unit-test source set and references `MaterialExpressiveTheme`,
`MotionScheme.standard()`, `MotionScheme.expressive()`, and `ButtonGroup` with the
required opt-in. This is a compile check, not reflection or an API-token scan.

**AGP 9 migration kept narrow:** removed the external Kotlin Android plugin and
legacy `kotlinOptions` target setting (Java target remains 17); retained existing
namespace, build features, lint, packaging and R8/D8 configuration. The dependency
cache was regenerated with `scripts/update-gradle-deps.sh`. The Nix build outputs
are `.#debugApk` (x86_64) and `.#apk` (arm64-v8a); sandbox build results are
recorded below after execution.

**Warnings, not papered over:** the old AGP compileSdk-37 and D8 API-37 warnings
are gone. The pinned NDK 27.2 still warns that API 37 exceeds its supported API
35 and substitutes 35 for native build configuration. The Nix SDK AAPT2 override
still emits AGP's experimental-option warning. Existing Kotlin deprecation and
native dependency/toolchain warnings remain. No warning was suppressed and the
SDK target was not lowered. The API-37 x86_64 emulator was already attached when
checked (`emulator-5554`), so install/launch smoke verification was not run to
avoid taking a busy shared device lane.

**Verification command in the pinned Nix development shell:**

```sh
nix develop --command gradle --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8' --console=plain testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

This dev-shell command passed. The report at
`app/build/reports/tests/testDebugUnitTest/index.html` records **322 tests, 0
failures, 0 skipped, 100% successful** — the same count as the pre-upgrade
baseline. No behavioural test was changed; the only test-source addition is the
compile-only Expressive API tripwire above.

## Completed dependency-lock and sandbox verification

The final lock was regenerated after selecting the final version tuple with
`scripts/update-gradle-deps.sh` (the generated fetch-deps update script completed
successfully). Then both Nix APK derivations built inside the sandbox, each with
`testDebugUnitTest` as its check task, bounded to one Nix job/core and one Gradle
worker:

```sh
nix build --no-link --print-out-paths --max-jobs 1 --option cores 1 .#debugApk
# exit 0: /nix/store/lgnkjk4iwpq6sywkx4g056cyqpwxl6iv-japanese-anki-card-creator-0.1.0
nix store ls --recursive /nix/store/lgnkjk4iwpq6sywkx4g056cyqpwxl6iv-japanese-anki-card-creator-0.1.0
# ./japanese-anki-0.4-x86_64-debug.apk

nix build --no-link --print-out-paths --max-jobs 1 --option cores 1 .#apk
# exit 0: /nix/store/fg5j2v5zm5gfxh7a7qwmzvilrz67s6nv-japanese-anki-card-creator-0.1.0
nix store ls --recursive /nix/store/fg5j2v5zm5gfxh7a7qwmzvilrz67s6nv-japanese-anki-card-creator-0.1.0
# ./japanese-anki-0.4-arm64-v8a-unsigned.apk
```

This validates the refreshed Nix Gradle dependency lock against both the debug
x86_64 and release arm64-v8a build graphs; the store outputs contain the expected
ABI-specific APKs. The API-37 x86_64 emulator install/launch check was **NOT RUN**:
`adb devices -l` found `emulator-5554` already attached, so the shared device lane
was left untouched.

Resolved plugin/dependency evidence from the same Nix-pinned environment:

```text
Gradle: 9.7.1; JDK: 17.0.20.1+1-nixos
Resolved plugin [id: 'com.android.application', version: '9.4.1', apply: false]
Resolved plugin [id: 'org.jetbrains.kotlin.plugin.compose', version: '2.2.10', apply: false]
androidx.compose.material3:material3:1.5.0-beta02
  Selected over the Compose BOM's material3:1.4.0 constraint
androidx.compose.ui:ui:1.12.1
androidx.activity:activity-compose:1.11.0
```

`dependencyInsight` for Material3, Compose UI and activity-compose and Gradle's
`--info help` plugin-resolution log all completed successfully.

**Warnings still present:** AGP 9.4.1 no longer emits the prior compileSdk-37
recommendation and D8 API-37 support warning. NDK 27.2 still warns that API 37 is
above its max API 35 and uses 35 for native configuration; the Nix AAPT2 override
is marked experimental; Gradle 9.7 reports deprecated build features that will be
incompatible with Gradle 10; existing Kotlin/CMake/native dependency warnings
remain. None were suppressed and SDK levels were not lowered.

## Gradle dependency cache coverage

The following cache-coverage notes predate the AGP 9.4.1 / Gradle 9.7.1 upgrade.
The refreshed dependency lock and sandbox build evidence for the current tuple are
recorded in the upgrade status above and the completed build section below.

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
