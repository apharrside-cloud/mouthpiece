#!/bin/bash
# Build, install on the connected Pixel, launch settings.
cd "$(dirname "$0")/.." && source scripts/env.sh
./gradlew :app:installDebug || exit 1
adb shell am start -n com.pharrlabs.voice/.SettingsActivity
