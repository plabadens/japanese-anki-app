# Material3 dependency upgrade

**How to use it:** EVIDENCE. Why `material3` is pinned to 1.4.0 and which Expressive APIs actually
exist in it. A successful dependency build is not native UI acceptance.

## Outcome

The project now pins `androidx.compose.material3:material3` **1.4.0** in `gradle/libs.versions.toml`, overriding the Compose BOM's previous 1.3.2 resolution. The BOM (2025.09.00), Kotlin (2.2.20), and AGP (8.13.2) are unchanged. No native UI implementation, application behavior, specification, or toolchain migration is included in this change.

1.5.0-beta01 was attempted but its AAR metadata requires **AGP 9.1.0 or newer**. The actual failing build is retained in `archive/evidence/material3-upgrade/beta-build-tests.log`. Since the user authorized either 1.4.0 or 1.5 beta, stable 1.4.0 was selected instead of silently expanding the work into an AGP/Gradle migration.

## Verified execution

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

**How to use it:** EVIDENCE. Why `material3` is pinned to 1.4.0 and which Expressive APIs
actually exist in it. A successful dependency build is not native UI acceptance.

**Current implementation rule:** the approved [screen/copy contract](card-flow-contract.md) controls native component/motion requirements. This older inventory/build report is not standing permission for substitutes. The later resolved-artifact inspection records Expressive classes; neither class names nor historical token-only matches prove a callable signature compiles. Compile the required native components/opt-ins and verify the theme on the actual dependency graph; propose any dependency/contract adjustment before substituting behavior. Existing build evidence above remains historical evidence only.

The inspected 1.4.0 Android AAR contains `ExperimentalMaterial3ExpressiveApi`, `MotionScheme`, expressive motion tokens, and button/icon-button classes. The inventory is recorded, with its exact AAR hash, in `archive/evidence/material3-upgrade/material3-1.4.0-api-inventory.json`.

That earlier inventory's `ButtonGroup`/`LoadingIndicator` token-class matches did not prove public composable availability; they are not proof of absence either. The HTML study used composition equivalents and never established native component usage. Actual compile/API validation is decisive. No automatic fallback or unapproved toolchain expansion is authorized.

## Existing toolchain warnings

The successful build is not warning-free. AGP 8.13.2 warns about compile SDK 37, D8 reports API 37 exceeds its supported API 36, and NDK 27.2 reports API 37 exceeds its supported API 35. Existing Kotlin deprecation warnings also remain. These were not suppressed, nor was the target API downgraded as a workaround. A clean API-37 toolchain migration is separate work.
