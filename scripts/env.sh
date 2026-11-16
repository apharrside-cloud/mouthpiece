#!/bin/bash
# Source from repo root: sets Android build env (Windows git-bash).
export JAVA_HOME=C:/Android/jdk17
export ANDROID_HOME=C:/Android/sdk
export ANDROID_SDK_ROOT=C:/Android/sdk
export PATH="/c/Android/jdk17/bin:/c/Android/sdk/platform-tools:$PATH"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "sdk.dir=C\:/Android/sdk" > "$ROOT/local.properties"
# Build output lives outside the watched Database folder: Windows file watchers/AV intermittently lock files in it and break Gradle's clean-up.
export PV_BUILD_DIR=C:/pharr-build/voice-app
export PV_BUILD_BASH=/c/pharr-build/voice-app
