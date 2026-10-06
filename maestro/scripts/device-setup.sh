#!/usr/bin/env bash
# Normalises an Android device for Maestro runs and reports what it could not change (ADR 0008, section 7).
#
#   device-setup.sh [--serial <adb serial>] [--check] [--strict]
#
# Default: wake and unlock the screen, try to switch animations off, keep the screen on, lock rotation, reset the font scale, then print a table.
# --check   change nothing (except waking the screen); only report.
# --strict  exit 1 when a required item is not right (animations off, screen kept on, font scale 1.0). Without it problems are warnings.
#
# Some phones (OxygenOS) refuse `adb shell settings put` unless "USB debugging (Security settings)" is on in Developer options; those items then
# show as MANUAL with the step to do by hand (see maestro/devices.md).
set -u

SERIAL=""; CHECK=0; STRICT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --check) CHECK=1; shift ;;
    --strict) STRICT=1; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

command -v adb >/dev/null 2>&1 || { echo "adb not found (install the Android platform-tools and put them on PATH)" >&2; exit 2; }
if [ -z "$SERIAL" ]; then
  SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n 1)
fi
[ -n "$SERIAL" ] || { echo "no authorised device attached (adb devices)" >&2; exit 2; }
A() { adb -s "$SERIAL" "$@"; }
SH() { A shell "$@" 2>&1 | tr -d '\r'; }

MODEL=$(SH getprop ro.product.model); OS=$(SH getprop ro.build.version.release); API=$(SH getprop ro.build.version.sdk)
echo "Device $SERIAL: $MODEL, Android $OS (API $API)"

BAD_REQUIRED=0
row() { printf '  %-30s %-8s %s\n' "$1" "$2" "$3"; }
fail_required() { BAD_REQUIRED=1; }

# 1. awake and unlocked
if [ "$(SH dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')" != "mWakefulness=Awake" ]; then SH input keyevent KEYCODE_WAKEUP >/dev/null; sleep 1; fi
if SH dumpsys window | grep -q 'isKeyguardShowing=true'; then SH input swipe 540 2000 540 700 200 >/dev/null; sleep 1; fi
if SH dumpsys window | grep -q 'isKeyguardShowing=true'; then row "screen" "MANUAL" "locked: unlock the phone (a PIN cannot be entered by this script)"; fail_required; else row "screen" "OK" "awake and unlocked"; fi

# helper: ensure a global/system setting equals a wanted value; try to change it unless --check
ensure() { # ns key wanted label required(1|0) manual-hint
  local ns="$1" key="$2" want="$3" label="$4" required="$5" hint="$6"
  local have; have=$(SH settings get "$ns" "$key")
  if [ "$have" = "$want" ]; then row "$label" "OK" "$key=$have"; return; fi
  if [ "$CHECK" = 0 ]; then
    local out; out=$(SH settings put "$ns" "$key" "$want")
    if [ -z "$out" ] && [ "$(SH settings get "$ns" "$key")" = "$want" ]; then row "$label" "FIXED" "$key $have -> $want"; return; fi
  fi
  row "$label" "MANUAL" "$key=$have, want $want. $hint"
  [ "$required" = 1 ] && fail_required
}

HINT_SEC="Developer options: turn on \"USB debugging (Security settings)\" so adb may change settings, or set it by hand"
ensure global window_animation_scale 0.0 "window animation" 1 "$HINT_SEC"
ensure global transition_animation_scale 0.0 "transition animation" 1 "$HINT_SEC"
ensure global animator_duration_scale 0.0 "animator duration" 1 "$HINT_SEC"
ensure system font_scale 1.0 "font scale" 1 "Settings > Display > Font size: default"
ensure system accelerometer_rotation 0 "rotation lock" 0 "Quick settings: lock portrait"

# stay awake while plugged in: either the setting is non-zero or the power service says so
STAY=$(SH settings get global stay_on_while_plugged_in)
if [ "$STAY" != "0" ] && [ "$STAY" != "null" ] && [ -n "$STAY" ]; then row "stay awake" "OK" "stay_on_while_plugged_in=$STAY"
else
  [ "$CHECK" = 0 ] && SH svc power stayon true >/dev/null
  if SH dumpsys power | grep -q 'mStayOn=true'; then row "stay awake" "FIXED" "svc power stayon true"
  else row "stay awake" "MANUAL" "Developer options > Stay awake (the screen sleeps after the timeout and Maestro steps fail on a black screen)"; fail_required; fi
fi

LOCALE=$(SH getprop persist.sys.locale)
case "$LOCALE" in en-US*|en_US*) row "system language" "OK" "$LOCALE" ;; *) row "system language" "WARN" "$LOCALE (flows force Kompound's own labels to English with lang=en; system dialogs stay in this language)" ;; esac

if [ "$BAD_REQUIRED" = 1 ] && [ "$STRICT" = 1 ]; then echo "Not ready (strict): fix the MANUAL items above."; exit 1; fi
[ "$BAD_REQUIRED" = 1 ] && echo "Note: some required items are not right; runs will work but may be slow or flaky (use --strict to fail)."
exit 0
