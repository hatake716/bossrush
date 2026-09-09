#!/usr/bin/env bash
set -euo pipefail
serial="${1:-}"
if [[ "$serial" != emulator-* ]]; then
  echo 'Usage: tools/test-emulator.sh emulator-5554 (emulators only)' >&2
  exit 2
fi
adb_bin="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
if [[ "$("$adb_bin" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'The selected device is not an emulator.' >&2
  exit 2
fi
cd "$(dirname "$0")/.."
./gradlew assembleDebug assembleDebugAndroidTest
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p artifacts
"$adb_bin" -s "$serial" shell am instrument -w -r io.github.hatake716.bossrush.test/androidx.test.runner.AndroidJUnitRunner | tee artifacts/instrumentation.txt
if ! rg -q '^OK \([0-9]+ tests?\)' artifacts/instrumentation.txt; then
  echo 'Instrumentation did not report a complete successful run.' >&2
  exit 1
fi
"$adb_bin" -s "$serial" pull /sdcard/Android/data/io.github.hatake716.bossrush/files/screenshots artifacts/
