#!/usr/bin/env bash
# Builds both release APKs into release/:
#   mouthpiece-<v>.apk        owner build (owner's starter dictionary), installs over the current app
#   share/mouthpiece-<v>.apk  neutral copy for other people (empty dictionary), plus SHA256SUMS.txt
# Usage: scripts/build_release.sh [extra gradle args]   e.g. -PappIdSuffix=.fresh for a side-by-side test copy
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
VER=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | cut -d'"' -f2)
mkdir -p release/share
APK="$PV_BUILD_BASH/outputs/apk/release/app-release.apk"
run() { rm -rf "$PV_BUILD_BASH"/intermediates/incremental/packageRelease; ./gradlew :app:assembleRelease "$@" || { rm -rf "$PV_BUILD_BASH"/intermediates/incremental; ./gradlew :app:assembleRelease "$@"; }; }
run "$@"
cp "$APK" "release/mouthpiece-$VER.apk"
run -Pshare=true "$@"
cp "$APK" "release/share/mouthpiece-$VER.apk"
python3 - "$VER" <<'PY'
import json,hashlib,sys,datetime
v=sys.argv[1]; f=f"mouthpiece-{v}.apk"; b=open(f"release/share/{f}","rb").read()
json.dump({"version":v,"file":f,"size_mb":f"{len(b)/1e6:.1f}","sha256":hashlib.sha256(b).hexdigest(),"date":datetime.date.today().isoformat()},open("release/share/version.json","w"))
PY
find release/share -name "mouthpiece-*.apk" ! -name "mouthpiece-$VER.apk" -delete
( cd release/share && sha256sum "mouthpiece-$VER.apk" > SHA256SUMS.txt && cat SHA256SUMS.txt )
ls -la release release/share
