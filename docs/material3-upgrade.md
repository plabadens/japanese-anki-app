# Material3 dependency and toolchain upgrade

**How to use it:** EVIDENCE. Current toolchain status and compile proof are above; sections marked historical describe the superseded Material3 1.4.0 branch. Class inventories alone are not API acceptance.

## Current toolchain upgrade (22 October 2026)

The prerequisite toolchain card supersedes the historical 1.4.0 selection below.
The resolved tuple is AGP **9.4.1**, Gradle **9.7.1** (`pkgs.gradle_9`), AGP
built-in Kotlin **2.2.10**, Kotlin Compose compiler plugin **2.2.10**, Compose BOM
**2026.09.00** (Compose UI resolves to **1.12.1**), Material3
**1.5.0-beta02**, `activity-compose` **1.11.0**, JDK **17**.

`dependencyInsight` confirms `androidx.compose.material3:material3` and
`material3-android` resolve to 1.5.0-beta02, winning over the BOM's 1.4.0
constraint. The compile smoke test is intentionally retained at
`app/src/test/java/dev/pierre/japaneseanki/Material3ExpressiveApiCompileSmoke.kt`;
it calls `MaterialExpressiveTheme`, both `MotionScheme` factories, and `ButtonGroup`
with the real experimental API opt-in. `testDebugUnitTest` compiled it on the
upgraded graph. The source change is only a compile-time API guard; it changes no
app behaviour or UI.

AGP 9's built-in Kotlin replaces the external `org.jetbrains.kotlin.android`
plugin, while the Compose compiler plugin stays version-aligned at 2.2.10. The BOM
moves to the Compose 1.12 line required by Material3 beta02; `activity-compose`
remains at the existing 1.11.0 because the resolved graph compiles without a newer
version. Gradle 9.7.1 satisfies AGP 9.4's Gradle 9.6 minimum; JDK 17 was already
pinned. The old AGP 8.13.2 / Material3 1.4.0 combination did not provide the
callable APIs and is not retained as a fallback.

Exact resolved dependency evidence (debug compile classpath):

- `androidx.compose.material3:material3:1.5.0-beta02` — selected over 1.4.0 by
  conflict resolution; both API and Android variants are present.
- `androidx.compose.ui:ui:1.12.1` — selected in the Compose UI atomic group.
- `androidx.activity:activity-compose:1.11.0`.
- AGP plugin resolution is 9.4.1; Gradle reports 9.7.1 in the Nix environment.

The Nix Gradle MITM lock was regenerated using `scripts/update-gradle-deps.sh`.
See `docs/toolchain-notes.md` for the bounded verification commands, Nix APK build
results, remaining API-37/NDK/AAPT2 warnings, and emulator-lane status.

## Historical outcome: Material3 1.4.0 (superseded)

The earlier investigation pinned `androidx.compose.material3:material3` **1.4.0**
in `gradle/libs.versions.toml`, overriding the Compose BOM's previous 1.3.2
resolution. The BOM (2025.09.00), Kotlin (2.2.20), and AGP (8.13.2) were unchanged
at that time. No native UI implementation, application behavior, or specification
change was included in that earlier dependency-only pass.

1.5.0-beta01 was previously attempted and its AAR metadata required **AGP 9.1.0
or newer**. The failed build is retained in
`archive/evidence/material3-upgrade/beta-build-tests.log`. The scoped AGP 9 migration was
subsequently authorized and is now recorded above.

## Historical verification on the original 1.4.0 toolchain

From the project root:

```sh
nix develop --command bash -c '
  set -euo pipefail
  gradle --no-daemon --max-workers=2 --console=plain \
    -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
    :app:dependencyInsight --dependency androidx.compose.material3 \
    --configuration debugCompileClasspath
  gradle --no-daemon --max-workers=2 --console=plain \
    -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
    testDebugUnitTest assembleDebug
'
```

Actual results:

- Dependency insight resolves both `material3` and `material3-android` to **1.4.0**.
- `testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL in 2m 45s**.
- JUnit XML aggregation: **110 tests, 18 suites, zero failures/errors/skips**.
- Debug APK produced at `app/build/outputs/apk/debug/app-debug.apk`. This is the existing native interface with the dependency upgrade, **not the redesigned HTML study implemented natively**.
- `bash scripts/update-gradle-deps.sh`: **exit 0**, updating `nix/gradle/deps.json` through the repository's normal generated-cache workflow. Its Gradle dependency download completed successfully.
- A subsequent sandboxed `nix build --no-link .#debugApk` was **interrupted**, not passed. Do not describe the pure Nix package build as verified by the dev-shell Gradle result.

Evidence:

- `archive/evidence/material3-upgrade/stable-build-tests.log`
- `archive/evidence/material3-upgrade/unit-test-summary.json`
- `archive/evidence/material3-upgrade/nix-dependency-update.log`
- `archive/evidence/material3-upgrade/baseline-dependency-insight.log`
- `archive/evidence/material3-upgrade/beta-dependency-insight.log`
- `archive/evidence/material3-upgrade/beta-build-tests.log`

## Capability boundary

**Current implementation rule:** the approved [screen/copy contract](card-flow-contract.md) controls native component/motion requirements. This older inventory/build report is not standing permission for substitutes. The later resolved-artifact inspection records Expressive classes; neither class names nor historical token-only matches prove a callable signature compiles. Compile the required native components/opt-ins and verify the theme on the actual dependency graph; propose any dependency/contract adjustment before substituting behavior. Existing build evidence above remains historical evidence only.

The inspected 1.4.0 Android AAR contains `ExperimentalMaterial3ExpressiveApi`, `MotionScheme`, expressive motion tokens, and button/icon-button classes. The inventory is recorded, with its exact AAR hash, in `archive/evidence/material3-upgrade/material3-1.4.0-api-inventory.json`.

That earlier inventory's `ButtonGroup`/`LoadingIndicator` token-class matches did not prove public composable availability; they are not proof of absence either. The HTML study used composition equivalents and never established native component usage. Actual compile/API validation is decisive. No automatic fallback or unapproved toolchain expansion is authorized.

## Historical warnings on AGP 8.13.2

The successful build is not warning-free. AGP 8.13.2 warns about compile SDK 37, D8 reports API 37 exceeds its supported API 36, and NDK 27.2 reports API 37 exceeds its supported API 35. Existing Kotlin deprecation warnings also remained. These warnings were not suppressed and the target API was not downgraded; the AGP 9 toolchain migration is documented in the current section above.
