#!/usr/bin/env bash
# shots.sh - Version 1.0.0
# Screenshots of every installer screen in Arabic and English, from a debug build on an emulator or phone:
#   bash installer/tools/shots.sh installer-debug.apk shots/
# Uses the debug-only --es screen hook (MainActivity.showDemo) and per-app locales (Android 13+).
set -e
APK=${1:?apk}; OUT=${2:?out dir}
PKG=app.awanpro.tv.installer
mkdir -p "$OUT"
adb install -r "$APK" >/dev/null
for loc in ar en; do
  adb shell cmd locale set-app-locales $PKG --locales $loc
  adb shell cmd locale get-app-locales $PKG
  for s in guide find allow pair confirm install done partly problem; do
    adb shell am force-stop $PKG
    adb shell am start -W -n $PKG/.MainActivity --es screen $s >/dev/null
    sleep 3
    adb exec-out screencap -p > "$OUT/$loc-$s.png"
    echo "$loc-$s.png"
  done
done
