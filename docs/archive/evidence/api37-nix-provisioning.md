# API-37 Nix provisioning verification (task t_05478257)

> **HISTORICAL (2026-10-22):** provisioning evidence at baseline `156d108`. The flake still declares the
> same API-37 target, but re-verify before citing any command or path here.

Baseline: `main` @ `156d108`. Branch:
`japanese-anki-app/t_05478257-provision-api-37-emulator-in-flake.nix-a`.
Commit: the branch head that contains this file (see `git log -1`). The commands
below were run from this worktree with the same `flake.nix`/`scripts/emulator.sh`;
only documentation files were added afterwards, so the APK inputs are unchanged.

## What changed

- `flake.nix`: one `composeAndroidPackages` call now declares the shipped target —
  platform `37.0`, build-tools `36.0.0`, NDK `27.2.12479018`, CMake `4.1.2`,
  emulator `37.1.11`, system image `system-images;android-37.0;google_apis;x86_64`.
  `nix develop`, `.#emulator-wrapper` and the APK derivations all use
  that single SDK root, so nothing comes from a global/hand-provisioned SDK.
  The duplicate `emulatorSdk` / `emulatorSdk37` / `emulateApp` targets were dropped:
  `androidenv.emulateApp` cannot create an API-37.0 AVD (`avdmanager` cannot parse
  the 37.0 package inside its generated `run-test-emulator`), so keeping them only
  advertised a path that does not work.
- `scripts/emulator.sh`: API-37.0 AVD (`japanese-anki-pixel7-api37`, Pixel 7,
  1080x2400), system image from the flake SDK, optional `EMULATOR_PORT`, and the
  boot message now reports the observed SDK level.
- `README.md`, `docs/toolchain-notes.md`: documented clean commands, the
  `nix develop` contents, and that `nix build .#apk` / `.#debugApk` work but are
  not byte-reproducible (the Gradle-in-`nix develop` path stays primary).

## Environment as provided by `nix develop`

```
$ nix develop --command bash -c 'java -version; gradle --version; ls $ANDROID_HOME/...'
openjdk version "17.0.20.1" 2026-08-18
Gradle 8.14.4
ANDROID_HOME=/nix/store/w218spk15022c7c726krzfjb0b52r3w2-androidsdk/libexec/android-sdk
platforms: android-37.0
build-tools: 36.0.0
ndk: 27.2.12479018
cmdline-tools: 22.0
Android emulator version 37.1.11.0 (build_id 15917651)
Android Debug Bridge version 1.0.41
local.properties: sdk.dir=<same SDK root>
```

The SDK closure contains the API-37.0 system image:

```
system-images/android-37.0/google_apis -> /nix/store/f0ar0r9kkw9j4wzpq6cg959fpk4bldj9-android-sdk-system-image-37.0-google_apis-x86_64-37.0-google_apis-x86_64/...
```

## Core tests

```
$ nix develop --command bash -c './scripts/test-core.sh'
JUnit version 4.13.2
..............................................................
[62 tests]
Time: 0.38

OK (62 tests)
```

## Gradle unit tests + debug APK

```
$ nix develop --command bash -c \
    'gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
       testDebugUnitTest assembleDebug'
> Task :app:testDebugUnitTest
> Task :app:assembleDebug
BUILD SUCCESSFUL in 3m 44s

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 1 hermes hermes 546904852 app-debug.apk
```

Without `-Pandroid.aapt2FromMavenOverride` the same run fails with `AAPT2 ...
Daemon startup failed`; the flag is part of the documented command.

## Emulator

AVD + boot through the flake wrapper (fresh AVD on a clean `.tmp-nix/avd`):

```
$ nix run .#emulator-wrapper -- start
Emulator boot completed (API 37 x86_64 google_apis, emulator-5554)
$ echo $?
0

$ adb -s emulator-5554 shell getprop ...
sys.boot_completed=1
ro.build.version.sdk=37
ro.build.version.release=17
ro.product.cpu.abi=x86_64
ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys
ro.product.model=sdk_gphone64_x86_64

$ nix run .#emulator-wrapper -- status
List of devices attached
emulator-5554          device
boot_completed=1
x86_64
37
```

Install and launch of the Gradle-built debug APK (546,904,852 bytes):

```
$ nix run .#emulator-wrapper -- install app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success
real 1m37s

$ nix run .#emulator-wrapper -- launch
Starting: Intent { cmp=dev.pierre.japaneseanki/.MainActivity }

$ nix run .#emulator-wrapper -- grant android.permission.CAMERA
$ nix run .#emulator-wrapper -- restart
$ adb -s emulator-5554 shell 'dumpsys activity activities | grep topResumedActivity'
topResumedActivity=ActivityRecord{187854698 u0 dev.pierre.japaneseanki/.MainActivity t10}

$ nix run .#emulator-wrapper -- screenshot .tmp-nix/screenshots/api37-launch.png
Screenshot saved: .tmp-nix/screenshots/api37-launch.png
```

The screenshot shows the shipped screen: `Photo`/`Type` toggle, settings gear,
camera viewfinder area and the `Use a photo from this device` button. The
viewfinder is black because a headless AVD has no camera surface — the same
limitation recorded for the v0.3 acceptance. The capture is checked in as
`docs/archive/evidence/api37-emulator-launch.png`.

The only FATAL EXCEPTION lines in logcat come from `com.google.android.gms`
(permission-controller/GMS services); nothing from `dev.pierre.japaneseanki`.

## Nix build targets

`.#apk` and `.#debugApk` are now a working (not byte-reproducible) Nix path; the
first build needs network access because Gradle resolves Maven artifacts through
the Nix MITM cache.

```
$ nix build .#apk --no-link -L
japanese-anki-card-creator> BUILD SUCCESSFUL in 5m 19s
japanese-anki-card-creator> checkPhase completed in 5 minutes 25 seconds
APK_BUILD_EXIT=0
$ nix path-info /nix/store/bqdqpwfqh5fqr2fg7qsncbmzmk8zpivi-japanese-anki-card-creator-0.1.0
/nix/store/bqdqpwfqh5fqr2fg7qsncbmzmk8zpivi-japanese-anki-card-creator-0.1.0
-r--r--r-- 531674871 .../japanese-anki-unsigned.apk
```

```
$ nix build .#debugApk --no-link -L
japanese-anki-card-creator> BUILD SUCCESSFUL in 7m 5s
DEBUGAPK_BUILD_EXIT=0
-r--r--r-- 546904852 /nix/store/c9hfx66wzjxw60nv4xbzb2aypmpqm3b8-japanese-anki-card-creator-0.1.0/japanese-anki-debug.apk
```

The Nix-built debug APK has the same size as the Gradle-built one but a different
hash (`a36055d0…` vs `69fae073…`): the sandbox build signs with a freshly generated
throwaway debug key because no `JAPANESE_ANKI_DEBUG_KEYSTORE` is configured, so it
cannot replace an existing install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). After
`adb uninstall dev.pierre.japaneseanki` it installed and launched:

```
$ nix run .#emulator-wrapper -- install /nix/store/c9hfx66…-japanese-anki-card-creator-0.1.0/japanese-anki-debug.apk
Performing Streamed Install
Success
$ nix run .#emulator-wrapper -- restart
topResumedActivity=ActivityRecord{20560275 u0 dev.pierre.japaneseanki/.MainActivity t12}
```

First launch starts the UniDic extraction (`Installing UniDic: 15% (about 725 MB)…`).
During that extraction the screenshot also captured a `System UI isn't responding`
ANR dialog on this loaded host; the app process itself stayed up and no
`dev.pierre.japaneseanki` FATAL EXCEPTION appeared. The earlier run of the
Gradle-built APK (same screen, no ANR) is the clean capture.

## Limitations

- Host memory: the API-37.0 guest runs with ~4 GB, so a boot needs roughly 5 GB of
  free RAM on a shared box. (The failures seen here were traced to the SGID bug
  below, not to memory; the RAM figure is the observed resident size, not a proven
  minimum.)
- Explicit SGID directory creation is denied on this host (`mkdir`/`chmod` EPERM).
  The emulator injects its adb key into a fresh userdata with `mkdir(..., 02750)`
  and segfaults when that fails; `scripts/emulator.sh` pre-creates
  `data/misc/adb` to avoid it. AVDs created elsewhere on such a host will hit the
  same crash on first boot.
- Two emulators on the same AVD corrupt the user data (zygote restart loop). The
  wrapper documents one instance per AVD; `EMULATOR_PORT` exists so parallel work
  uses separate ports, but each parallel job still needs its own AVD.
- Headless AVD has no camera, so camera capture paths are not exercised here.
- APK artifacts are not byte-identical between builds and no reproducibility claim
  is made. `nix build` outputs are `/nix/store` paths.
- Only the x86_64 emulator target is provisioned; `arm64-v8a` packaging is verified
  by the APK contents, not by an ARM emulator run (SPEC does not require one).
