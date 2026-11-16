#!/usr/bin/env bash
# Publishes release/share/* (APK, SHA256SUMS.txt, version.json) to the download site's data folder.
# Deploy target comes from ~/.pharr-voice/deploy.env (not in the repo), which sets:
#   PV_SSH_KEY=<path to ssh key>   PV_SITE_DEST=<user@host:/path/to/public/>   PV_SITE_URL=<https://your-site>
# No rebuild or restart needed: the page reads version.json on every request.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f "$HOME/.pharr-voice/deploy.env" ] && . "$HOME/.pharr-voice/deploy.env"
: "${PV_SSH_KEY:?set PV_SSH_KEY in ~/.pharr-voice/deploy.env}" "${PV_SITE_DEST:?set PV_SITE_DEST}" "${PV_SITE_URL:?set PV_SITE_URL}"
K="-o BatchMode=yes -i $PV_SSH_KEY"
scp $K release/share/*.apk release/share/SHA256SUMS.txt release/share/version.json "$PV_SITE_DEST"
echo "Published. Check: curl -s $PV_SITE_URL/version.json"
