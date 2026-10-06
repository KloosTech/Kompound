#!/usr/bin/env bash
# The one entry point for Maestro runs (ADR 0008, section 6): same command on a laptop with a phone, on an emulator and in CI.
#
#   maestro.sh [options] [-- <extra args for `maestro test`>]
#
#   --device <serial>      which device (default: the only attached one)
#   --tags a,b             run flows with any of these tags (smoke, category:inputs, slow, ...)
#   --exclude-tags a,b     skip flows with these tags (default: quarantine,wip,todo)
#   --component <demo-id>  all flows of one component (tag component:<id>)
#   --flow <path>          one flow file or folder instead of maestro/flows
#   --env KEY=VALUE        pass a variable to the flows (repeatable)
#   --repeat <n>           run the selection n times and report flows that fail only sometimes (flakes)
#   --continuous           re-run when a flow file changes (development)
#   --strict               fail when the device is not set up properly (animations off, screen kept on, font scale 1.0)
#   --no-build             do not build the APK (use the one already built)
#   --no-install           do not install the APK
#   --no-setup             skip the device check
#   --out <dir>            output folder (default build/maestro)
#   -h, --help
#
# Needs: JDK 17 or 21 (found automatically or via JAVA_HOME), the Maestro CLI at the version in maestro/.maestro-version (~/.maestro/bin or PATH), adb.
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 2
APP_ID="tech.kloos.kompound.catalog.maestro"
APK="catalog/androidApp/build/outputs/apk/maestro/androidApp-maestro.apk"

SERIAL=""; TAGS=""; EXCLUDE="quarantine,wip,todo"; COMPONENT=""; TARGET="maestro/flows"; REPEAT=1; CONTINUOUS=0; STRICT=0
BUILD=1; INSTALL=1; SETUP=1; OUT="build/maestro"; ENVS=(); EXTRA=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) SERIAL="$2"; shift 2 ;;
    --tags) TAGS="$2"; shift 2 ;;
    --exclude-tags) EXCLUDE="$2"; shift 2 ;;
    --component) COMPONENT="$2"; shift 2 ;;
    --flow) TARGET="$2"; shift 2 ;;
    --env) ENVS+=("$2"); shift 2 ;;
    --repeat) REPEAT="$2"; shift 2 ;;
    --continuous) CONTINUOUS=1; shift ;;
    --strict) STRICT=1; shift ;;
    --no-build) BUILD=0; shift ;;
    --no-install) INSTALL=0; shift ;;
    --no-setup) SETUP=0; shift ;;
    --out) OUT="$2"; shift 2 ;;
    -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
    --) shift; EXTRA=("$@"); break ;;
    *) echo "unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
done
[ -n "$COMPONENT" ] && TAGS="component:$COMPONENT"

# --- prerequisites ---------------------------------------------------------------------------------------------
find_java() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "(17|21)\.'; then echo "$JAVA_HOME"; return; fi
  if [ -x /usr/libexec/java_home ]; then
    for v in 21 17; do local h; h=$(/usr/libexec/java_home -v "$v" 2>/dev/null) && [ -n "$h" ] && { echo "$h"; return; }; done
  fi
  return 1
}
JH=$(find_java) || { echo "No JDK 17 or 21 found. Install one and set JAVA_HOME (Maestro does not run on newer JDKs reliably)." >&2; exit 2; }
export JAVA_HOME="$JH"
MAESTRO_BIN="${MAESTRO_HOME:-$HOME/.maestro}/bin"
[ -x "$MAESTRO_BIN/maestro" ] && export PATH="$MAESTRO_BIN:$PATH"
command -v maestro >/dev/null 2>&1 || { echo "Maestro CLI not found. Install version $(cat maestro/.maestro-version) (see maestro/README.md)." >&2; exit 2; }
command -v adb >/dev/null 2>&1 || { echo "adb not found (Android platform-tools)." >&2; exit 2; }
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
WANT=$(cat maestro/.maestro-version 2>/dev/null || echo "")
HAVE=$(maestro --version 2>/dev/null | tail -n 1 | tr -d '[:space:]')
[ -n "$WANT" ] && [ "$HAVE" != "$WANT" ] && echo "Warning: Maestro $HAVE found, flows were written for $WANT." >&2

if [ -z "$SERIAL" ]; then
  SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  [ "$(echo "$SERIAL" | grep -c .)" = "1" ] || { echo "Pass --device <serial>: $(echo "$SERIAL" | grep -c .) devices attached." >&2; adb devices >&2; exit 2; }
fi

# --- build, install, device check ------------------------------------------------------------------------------
mkdir -p "$OUT"
if [ "$BUILD" = 1 ]; then ./gradlew --console=plain -q :catalog:androidApp:assembleMaestro || { echo "Build failed." >&2; exit 2; }; fi
[ -f "$APK" ] || { echo "No APK at $APK (run without --no-build)." >&2; exit 2; }
APK_SHA=$(shasum -a 256 "$APK" | cut -c1-12)
if [ "$INSTALL" = 1 ]; then
  STAMP="$OUT/installed-$SERIAL.sha"
  if [ "$(cat "$STAMP" 2>/dev/null)" != "$APK_SHA" ] || ! adb -s "$SERIAL" shell pm list packages "$APP_ID" | grep -q "$APP_ID"; then
    echo "Installing $APK ($APK_SHA) on $SERIAL ..."
    adb -s "$SERIAL" install -r -g "$APK" >/dev/null || { echo "Install failed." >&2; exit 2; }
    echo "$APK_SHA" > "$STAMP"
  fi
fi
if [ "$SETUP" = 1 ]; then
  SARGS=(--serial "$SERIAL"); [ "$STRICT" = 1 ] && SARGS+=(--strict)
  maestro/scripts/device-setup.sh "${SARGS[@]}" || exit 2
fi

# keep the screen awake when the phone cannot be told to (a sleeping screen makes steps fail)
KEEP_PID=""
STAY=$(adb -s "$SERIAL" shell settings get global stay_on_while_plugged_in | tr -d '\r')
if [ "$STAY" = "0" ] || [ "$STAY" = "null" ]; then
  ( while true; do adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; sleep 8; done ) & KEEP_PID=$!
fi

# OnePlus (Oplus HANS) freezes the Maestro driver app for ~50 s at a time (steps stall) unless it is on the Doze
# whitelist. A reinstall wipes the whitelist, so keep the driver installed (--no-reinstall-driver) and whitelist
# it; on the very first run the driver appears mid-run, so a background loop whitelists it as soon as it exists.
DRIVER_PKGS=(dev.mobile.maestro dev.mobile.maestro.test)
whitelist_driver() {
  for p in "${DRIVER_PKGS[@]}"; do
    adb -s "$SERIAL" shell pm path "$p" 2>/dev/null | grep -q package &&
      adb -s "$SERIAL" shell dumpsys deviceidle whitelist +"$p" >/dev/null 2>&1
  done
  return 0
}
# with --no-reinstall-driver Maestro installs the driver when it is missing and then leaves it on the phone; without it the
# driver is removed after every run and the battery setting below is lost with it
DRIVER_ARG=(--no-reinstall-driver)
adb -s "$SERIAL" logcat -c 2>/dev/null || true
whitelist_driver
EXEMPT_PID=""
( while true; do whitelist_driver; sleep 2; done ) & EXEMPT_PID=$!
trap '[ -n "$KEEP_PID" ] && kill "$KEEP_PID" 2>/dev/null; kill "$EXEMPT_PID" 2>/dev/null' EXIT

# --- run -------------------------------------------------------------------------------------------------------
ARGS=(--device "$SERIAL" test ${DRIVER_ARG[@]+"${DRIVER_ARG[@]}"} "$TARGET" --config maestro/config.yaml)
[ -n "$TAGS" ] && ARGS+=(--include-tags "$TAGS")
[ -n "$EXCLUDE" ] && ARGS+=(--exclude-tags "$EXCLUDE")
for e in ${ENVS[@]+"${ENVS[@]}"}; do ARGS+=(--env "$e"); done
[ "$CONTINUOUS" = 1 ] && ARGS+=(--continuous)

STATUS=0; START=$(date +%s)
: > "$OUT/flow-results.tsv"
for i in $(seq 1 "$REPEAT"); do
  REPORT="$OUT/report-$i.xml"
  echo "== run $i of $REPEAT: ${TAGS:-all flows} on $SERIAL"
  maestro "${ARGS[@]}" --test-output-dir "$OUT/artifacts/run-$i" --format junit --output "$REPORT" ${EXTRA[@]+"${EXTRA[@]}"}
  RC=$?
  [ "$RC" != 0 ] && STATUS=$RC
  # one line per flow: run, status, name
  if [ -f "$REPORT" ]; then
    awk -v run="$i" '/<testcase /{ if (match($0, / name="[^"]*"/)) { n=substr($0, RSTART+7, RLENGTH-8) } s=($0 ~ /status="SUCCESS"/)?"pass":"FAIL"; print run "\t" s "\t" n }' "$REPORT" >> "$OUT/flow-results.tsv"
  fi
done
SECS=$(( $(date +%s) - START ))
FROZEN=$(adb -s "$SERIAL" logcat -d 2>/dev/null | grep -c "freeze uid.*dev.mobile.maestro" || true)

# --- summary ---------------------------------------------------------------------------------------------------
TOTAL=$(awk -F'\t' '{print $3}' "$OUT/flow-results.tsv" | sort -u | wc -l | tr -d ' ')
FAILED=$(awk -F'\t' '$2=="FAIL"{print $3}' "$OUT/flow-results.tsv" | sort -u | wc -l | tr -d ' ')
{
  echo "Maestro $HAVE | $(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r') $SERIAL | apk $APK_SHA | ${SECS}s | flows $TOTAL, failing $FAILED, runs $REPEAT"
  [ "${FROZEN:-0}" -gt 0 ] && echo "WARNING the battery manager froze the Maestro driver ${FROZEN} times (steps stall); see maestro/devices.md, 'Stop the battery manager...'"
  if [ "$REPEAT" -gt 1 ]; then
    awk -F'\t' '{runs[$3]++; if($2=="FAIL") f[$3]++} END{ for(n in runs) if(f[n]>0 && f[n]<runs[n]) printf "FLAKY  %s failed %d of %d runs\n", n, f[n], runs[n]; else if(f[n]==runs[n]) printf "BROKEN %s failed all %d runs\n", n, runs[n] }' "$OUT/flow-results.tsv"
  else
    awk -F'\t' '$2=="FAIL"{print "FAILED " $3}' "$OUT/flow-results.tsv"
  fi
} | tee "$OUT/summary.txt"
exit "$STATUS"
