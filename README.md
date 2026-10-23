# Japanese Anki Card Creator

Mine a Japanese sentence and one vocabulary item into a note in AnkiDroid. The app creates notes; AnkiDroid owns cards, scheduling, and sync.

## Build, test, and render

```sh
nix develop
./scripts/test-core.sh
gradle --offline --configuration-cache --build-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  verifyFast
gradle --offline --configuration-cache --build-cache \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:renderDesignStates assembleDebug
```

`verifyFast` runs the JVM and Compose checks without an emulator. Rendered design states are written to `build/design-states/`.

## Install a debug build

```sh
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Cut a release

```sh
git checkout main
# Bump versionName and versionCode in app/build.gradle.kts.
git tag "v$(sed -n 's/.*versionName = \"\(.*\)\".*/\1/p' app/build.gradle.kts | head -n 1)"
git push origin "$(git describe --tags --exact-match)"
```
