# Android instrumentation tests

## Approved-flow compliance scope

Tooling below is a harness, not acceptance of the approved [screen/copy contract](sentence-mining-flow.md). SPEC.md R01–R39 controls the current gate. Coordinate a single API-37 x86_64 emulator lane; do not boot a competing AVD or launch unrestricted heavy builds while another owner uses it.

For design compliance, exercise the connected Capture/Target/Review/Send journey and each catalogued exception: one heading, one selectable sentence, left-aligned Edit sentence, separate metadata editors, progress A's one stable-footer indicator, dismissal/reopen, durable setup/background/recreation and no paid work on revisit. Inventory visible/accessibility/notification copy against catalogue IDs; assert both absence of removed UI and presence of necessary recovery. Capture both themes and narrow/200% text, check semantics/contrast and reduced-motion/touch-feedback settings. Emulator haptic dispatch is not proof of physical sensation.

Import acceptance additionally requires a separate test-only dummy AnkiDroid APK with a pinned package/provider/permission contract, actual cross-UID URI grants and persisted decodable WebP/MP3/Ogg bytes, note fields/tags/deck and durable receipts. Inject failures/lost responses, repeated Send and recreation; no blind reinsert after an uncertain write. Keep its control channel out of production. Run the same happy-path assertions against supported real AnkiDroid in a separate disposable emulator collection before claiming compatibility. Never replace a personal installation/collection. Controlled generation does not establish live cloud quality; no credential access or paid calls without separate authorization.

## Existing platform harness

The project uses the AndroidX JUnit runner for `app/src/androidTest`. Its pinned
test-only dependencies are declared in `gradle/libs.versions.toml` and covered by
`nix/gradle/deps.json`; production dependencies and the Android toolchain are not
changed for this harness.

The smoke suite exercises Android's SQLite implementation, platform WebP
encoding/decoding, and local `MediaPlayer` playback from a generated silent WAV.
It uses no credentials, paid services, or network calls. Keep platform I/O tests
under `app/src/androidTest`; JVM tests in `app/src/test` do not provide those
Android framework guarantees.

## Build and run on API 37 x86_64

There is no Gradle wrapper in this repository. `nix develop` supplies the pinned
Gradle 8.14.4, JDK 17, SDK platform 37.0, build-tools 36.0.0, emulator 37.1.11,
and `google_apis` x86_64 system image. From a `nix develop` shell, build the test
APKs with:

```sh
gradle -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  assembleDebug assembleDebugAndroidTest
```

Use an AVD and ADB port owned by this run. For example, from the repository root
inside `nix develop` (choose unused ports if 5560/5043 are occupied):

```sh
export ANDROID_USER_HOME="$PWD/.tmp-nix/android-test/user-home"
export ANDROID_AVD_HOME="$PWD/.tmp-nix/android-test/avd"
export ANDROID_ADB_SERVER_PORT=5043
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/22.0/bin/avdmanager" create avd --force \
  --name japanese-anki-foundation-api37 \
  --package 'system-images;android-37.0;google_apis;x86_64' --device pixel_7
mkdir -p "$ANDROID_AVD_HOME/japanese-anki-foundation-api37.avd/data/misc/adb"
adb start-server
"$ANDROID_HOME/emulator/emulator" -avd japanese-anki-foundation-api37 -port 5560 -no-window -no-audio \
  -no-boot-anim -gpu swiftshader_indirect -no-snapshot &
EMU_PID=$!
trap 'adb -s emulator-5560 emu kill >/dev/null 2>&1 || true; wait "$EMU_PID" 2>/dev/null || true; adb kill-server >/dev/null 2>&1 || true' EXIT
adb -s emulator-5560 wait-for-device
until [ "$(adb -s emulator-5560 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell am instrument -w -r \
  -e class dev.pierre.japaneseanki.AndroidPlatformSmokeTest \
  dev.pierre.japaneseanki.test/androidx.test.runner.AndroidJUnitRunner
```

The explicit `adb -s` calls prevent an instrumentation run from drifting onto
another device. `connectedDebugAndroidTest` targets every device attached to its
ADB server; use it only when that server has exactly the AVDs you intend to test.
Do not stop a device you did not start. The checked-in `scripts/emulator.sh` is
for the app's own interactive AVD, not a general-purpose instrumentation runner.

Refresh the Nix Gradle artifact lock after changing test dependencies with:

```sh
./scripts/update-gradle-deps.sh
```

That update resolves Android-test configurations explicitly. A warm Gradle
cache is not evidence that the sandbox lock contains the instrumentation
artifacts.
