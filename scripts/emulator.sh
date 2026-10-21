#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SDK_ROOT:?Use nix run .#emulator-wrapper -- <command>}"
ROOT="$PWD/.tmp-nix"
export ANDROID_USER_HOME="$ROOT/emulator-home"
export ANDROID_AVD_HOME="$ROOT/avd"
export HOME="$ANDROID_USER_HOME"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$ROOT/tmp"
export TMPDIR="$ROOT/tmp"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/22.0/bin/avdmanager"
AVD=japanese-anki-pixel7-api37
# API 37.0 (Android 17) google_apis x86_64, supplied by the flake's Android SDK.
SYSTEM_IMAGE='system-images;android-37.0;google_apis;x86_64'
PORT="${EMULATOR_PORT:-5554}"
SERIAL="emulator-$PORT"

# The emulator only registers with adb's default TCP server, not a localfilesystem server.
adb() { env -u ADB_SERVER_SOCKET "$ADB" -s "$SERIAL" "$@"; }
adb_devices() { env -u ADB_SERVER_SOCKET "$ADB" devices "$@"; }
usage() {
  cat >&2 <<'USAGE'
Usage:
  emulator.sh start
  emulator.sh stop|status
  emulator.sh install <apk>
  emulator.sh reinstall <apk>      # install -r, grant camera, force-stop, launch, settle
  emulator.sh launch
  emulator.sh restart              # force-stop + launch, no reinstall
  emulator.sh grant <permission>   # defaults to android.permission.CAMERA
  emulator.sh tap <x> <y>
  emulator.sh theme light|dark
  emulator.sh screenshot <file>
  emulator.sh dump <file.xml>      # uiautomator UI hierarchy dump, pulled locally
  emulator.sh share-text <text>
  emulator.sh share-image <file>
  emulator.sh logcat [args...]
  emulator.sh session <apk> <screenshot-prefix>
USAGE
  exit 2
}

PKG=dev.pierre.japaneseanki

start_emulator() {
  if adb_devices | grep -q "^${SERIAL}[[:space:]]*device"; then
    echo "$SERIAL already connected"
    return
  fi
  env -u ADB_SERVER_SOCKET "$ADB" start-server >/dev/null
  if ! "$AVDMANAGER" list avd | grep -q "Name: $AVD"; then
    # avdmanager 22.0 logs a harmless "could not load devices.xml" error for the 37.0
    # image and still writes a correct Pixel 7 profile.
    printf 'no\n' | "$AVDMANAGER" create avd --force --name "$AVD" \
      --package "$SYSTEM_IMAGE" --device pixel_7
  fi
  # The emulator injects $HOME/.android/adbkey into a fresh userdata by creating
  # data/misc/adb with mode 02750. Hosts that refuse an explicit SGID bit (this
  # hardened NixOS box returns EPERM) make that mkdir fail, after which the
  # emulator dereferences the missing adb_keys file and dies with SIGSEGV.
  # Pre-creating the directory without SGID makes the mkdir an EEXIST no-op.
  mkdir -p "$ANDROID_AVD_HOME/$AVD.avd/data/misc/adb"
  # API 37's CameraX validator rejects a device that reports only one lens because the AVD
  # config sets hw.camera.front=none; every prepare/select pass then fails with
  # CameraIdListIncorrectException and no preview ever starts. A real Pixel 7 has both
  # cameras, so give the emulator an emulated front lens as well.
  env -u ADB_SERVER_SOCKET "$EMULATOR" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
    -camera-back environment -camera-front environment \
    -gpu swiftshader_indirect -no-snapshot -skin 1080x2400 >"$ROOT/emulator.log" 2>&1 &
  EMULATOR_PID=$!
  echo "$EMULATOR_PID" > "$ROOT/emulator.pid"
  if ! timeout 60 env -u ADB_SERVER_SOCKET "$ADB" -s "$SERIAL" wait-for-device; then
    echo "ERROR: ADB did not see $SERIAL within 60 seconds; see .tmp-nix/emulator.log" >&2
    return 1
  fi
  for _ in $(seq 1 120); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
      echo "Emulator boot completed (API $(adb shell getprop ro.build.version.sdk | tr -d '\r') x86_64 google_apis, $SERIAL)"
      return
    fi
    sleep 2
  done
  echo 'ERROR: emulator did not set sys.boot_completed=1 within 240 seconds; see .tmp-nix/emulator.log' >&2
  return 1
}

[[ $# -gt 0 ]] || usage
case "$1" in
  session)
    [[ $# == 3 && -f $2 ]] || usage
    APK=$2
    PREFIX=$3
    mkdir -p "$(dirname "$PREFIX")"
    EMULATOR_PID=''
    cleanup() {
      if adb_devices | grep -q "^${SERIAL}[[:space:]]*device"; then adb emu kill >/dev/null 2>&1 || true; fi
      if [[ -n "$EMULATOR_PID" ]]; then wait "$EMULATOR_PID" 2>/dev/null || true; fi
    }
    trap cleanup EXIT
    start_emulator
    echo "sys.boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')"
    echo "ABI=$(adb shell getprop ro.product.cpu.abi) API=$(adb shell getprop ro.build.version.sdk)"
    adb shell svc bluetooth disable
    adb logcat -c
    adb install -r "$APK"
    adb shell am start -n dev.pierre.japaneseanki/.MainActivity
    sleep 5
    RESUMED=$(adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | tail -n1 || true)
    if [[ "$RESUMED" != *'dev.pierre.japaneseanki'* ]]; then
      RESUMED=$(adb shell dumpsys activity top | grep -m1 '^ACTIVITY' || true)
    fi
    echo "Resumed activity: $RESUMED"
    [[ "$RESUMED" == *'dev.pierre.japaneseanki'* && "$RESUMED" == *'MainActivity'* ]] || {
      echo 'ERROR: expected MainActivity to be resumed' >&2
      exit 1
    }
    adb shell cmd uimode night yes
    sleep 2
    adb exec-out screencap -p > "${PREFIX}-dark.png"
    adb shell cmd uimode night no
    sleep 2
    adb exec-out screencap -p > "${PREFIX}-light.png"
    adb logcat -d -v threadtime > "${PREFIX}-logcat.txt"
    if grep -E 'FATAL EXCEPTION|Process: dev\.pierre\.japaneseanki|Fatal signal.*dev\.pierre\.japaneseanki' "${PREFIX}-logcat.txt"; then
      echo 'App crash lines found above.'
      exit 1
    else
      echo 'App crash scan: no app crash lines found.'
    fi
    echo "Dark screenshot: ${PREFIX}-dark.png"
    echo "Light screenshot: ${PREFIX}-light.png"
    echo "Full logcat: ${PREFIX}-logcat.txt"
    ;;
  start)
    start_emulator ;;
  stop)
    if adb_devices | grep -q "^${SERIAL}[[:space:]]*device"; then adb emu kill; else echo 'Emulator is not running'; fi ;;
  status)
    adb_devices -l
    if adb_devices | grep -q "^${SERIAL}[[:space:]]*device"; then
      echo "boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')"
      adb shell getprop ro.product.cpu.abi
      adb shell getprop ro.build.version.sdk
    fi ;;
  install)
    [[ $# == 2 && -f $2 ]] || usage
    adb install -r "$2" ;;
  reinstall)
    [[ $# == 2 && -f $2 ]] || usage
    adb install -r "$2"
    adb shell pm grant "$PKG" android.permission.CAMERA 2>/dev/null || true
    adb shell am force-stop "$PKG"
    adb shell am start -n "$PKG/.MainActivity"
    sleep 4
    echo "Reinstalled, granted camera, relaunched: $2" ;;
  launch)
    adb shell am start -n "$PKG/.MainActivity" ;;
  restart)
    adb shell am force-stop "$PKG"
    adb shell am start -n "$PKG/.MainActivity"
    sleep 3 ;;
  grant)
    perm="${2:-android.permission.CAMERA}"
    adb shell pm grant "$PKG" "$perm" ;;
  tap)
    [[ $# == 3 ]] || usage
    adb shell input tap "$2" "$3" ;;
  theme)
    [[ $# == 2 ]] || usage
    case "$2" in
      light) adb shell cmd uimode night no ;;
      dark) adb shell cmd uimode night yes ;;
      *) usage ;;
    esac
    sleep 2 ;;
  screenshot)
    [[ $# == 2 ]] || usage
    mkdir -p "$(dirname "$2")"
    adb exec-out screencap -p > "$2"
    echo "Screenshot saved: $2" ;;
  dump)
    [[ $# == 2 ]] || usage
    mkdir -p "$(dirname "$2")"
    adb shell uiautomator dump /sdcard/_dump.xml >/dev/null
    adb pull /sdcard/_dump.xml "$2" >/dev/null
    echo "UI dump saved: $2" ;;
  share-text)
    [[ $# == 2 ]] || usage
    adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "$2" ;;
  share-image)
    [[ $# == 2 && -f $2 ]] || usage
    remote=/sdcard/Pictures/"$(basename "$2")"
    adb push "$2" "$remote"
    adb shell am start -a android.intent.action.SEND -t image/* --eu android.intent.extra.STREAM "file://$remote" ;;
  logcat)
    shift
    adb logcat "$@" ;;
  *) usage ;;
esac
