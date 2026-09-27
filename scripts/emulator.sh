#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SDK_ROOT:?Use nix run .#emulator-wrapper -- <command>}"
ADB_SERVER_SOCKET="${ADB_SERVER_SOCKET:-localfilesystem:/home/pierre/Development/japanese-anki-app/.tmp-nix/adb.sock}"
export ADB_SERVER_SOCKET
ROOT="$PWD/.tmp-nix"
export ANDROID_USER_HOME="$ROOT/emulator-home"
export ANDROID_AVD_HOME="$ROOT/avd"
export HOME="$ANDROID_USER_HOME"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$ROOT/tmp"
export TMPDIR="$ROOT/tmp"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/22.0/bin/avdmanager"
AVD=japanese-anki-api35
SERIAL=emulator-5554
usage() { echo 'Usage: emulator.sh {start|stop|status|install <apk>|launch|screenshot <file>|share-text <text>|share-image <file>|logcat [args...]}' >&2; exit 2; }
[[ $# -gt 0 ]] || usage
adb() { "$ADB" -s "$SERIAL" "$@"; }

case "$1" in
  start)
    if "$ADB" devices | grep -q "^${SERIAL}[[:space:]]*device"; then echo "$SERIAL already connected"; exit 0; fi
    export ADB_SERVER_SOCKET
    "$ADB" start-server >/dev/null
    if ! "$AVDMANAGER" list avd | grep -q "Name: $AVD"; then
      printf 'no\n' | "$AVDMANAGER" create avd --force -n "$AVD" -k 'system-images;android-35;google_apis;x86_64' -p "$ANDROID_AVD_HOME/$AVD.avd"
    fi
    setsid nohup "$EMULATOR" -avd "$AVD" -port 5554 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot >"$ROOT/emulator.log" 2>&1 </dev/null &
    echo "$!" > "$ROOT/emulator.pid"
    if ! timeout 180 "$ADB" -s "$SERIAL" wait-for-device; then
      echo 'ERROR: ADB did not see emulator-5554 within 180 seconds; see .tmp-nix/emulator.log' >&2
      exit 1
    fi
    for _ in $(seq 1 120); do
      if [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then echo 'Emulator boot completed (API 35 x86_64 google_apis)'; exit 0; fi
      sleep 2
    done
    echo 'ERROR: emulator did not set sys.boot_completed=1 within 240 seconds; see .tmp-nix/emulator.log' >&2
    exit 1
    ;;
  stop)
    if "$ADB" devices | grep -q "^${SERIAL}[[:space:]]*device"; then adb emu kill; else echo 'Emulator is not running'; fi
    ;;
  status)
    "$ADB" devices -l
    if "$ADB" devices | grep -q "^${SERIAL}[[:space:]]*device"; then adb shell getprop sys.boot_completed; fi
    ;;
  install)
    [[ $# == 2 && -f $2 ]] || { echo 'install requires an existing APK path' >&2; exit 2; }; adb install -r "$2" ;;
  launch)
    adb shell am start -n dev.pierre.japaneseanki/.MainActivity ;;
  screenshot)
    [[ $# == 2 ]] || usage; mkdir -p "$(dirname "$2")"; adb exec-out screencap -p > "$2"; echo "Screenshot saved: $2" ;;
  share-text)
    [[ $# == 2 ]] || usage; adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "$2" ;;
  share-image)
    [[ $# == 2 && -f $2 ]] || { echo 'share-image requires an existing image file' >&2; exit 2; }
    remote=/sdcard/Pictures/"$(basename "$2")"; adb push "$2" "$remote"; adb shell am start -a android.intent.action.SEND -t image/* --eu android.intent.extra.STREAM "file://$remote" ;;
  logcat)
    shift; adb logcat "$@" ;;
  *) usage ;;
esac
