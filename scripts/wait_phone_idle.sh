#!/usr/bin/env bash
# Waits until the Pixel has had no user touch for IDLE_MIN minutes while still awake+unlocked
# (owner asleep, screen timeout is 30 min), then exits 0 so a notification wakes the agent.
# Exits 2 if it gives up. Never touches the phone: read-only dumpsys calls only.
export PATH="/c/Android/sdk/platform-tools:$PATH"
IDLE_MIN=${1:-10}
DEADLINE=$(( $(date +%s) + ${2:-21600} ))
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  P=$(adb shell dumpsys power 2>/dev/null)
  AGO=$(echo "$P" | grep -oE "lastUserActivityTime=[0-9]+ \([0-9]+ ms ago" | head -1 | grep -oE "[0-9]+ ms ago" | grep -oE "^[0-9]+")
  AWAKE=$(echo "$P" | grep -c "mWakefulness=Awake")
  LOCKED=$(adb shell dumpsys window 2>/dev/null | grep -c "isKeyguardShowing=true")
  if [ -n "$AGO" ] && [ "$AWAKE" -ge 1 ] && [ "$LOCKED" -eq 0 ] && [ "$AGO" -ge $(( IDLE_MIN * 60000 )) ]; then
    echo "READY idle_ms=$AGO $(date)"; exit 0
  fi
  sleep 45
done
echo "GAVE UP $(date)"; exit 2
