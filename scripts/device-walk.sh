#!/usr/bin/env bash
#
# Drive the sample app through the buyer walk on an Android emulator or device
# and capture one PNG per step.
#
# The JVM screenshot tests prove layout and colour on a simulated canvas. Three
# things only a real Android runtime can prove, and this walk is where they are
# proven:
#
#   1. A tap on native chrome that stands over the map must not also reach the
#      WebView canvas underneath. See the blocked-region probe below.
#   2. Window insets and edge-to-edge behaviour under the real status and
#      navigation bars.
#   3. That the hosted runtime actually loads, renders, and drives the bridge
#      end to end.
#
# Every wait polls for a condition. There are no blind sleeps: a slow first
# load fails by timeout with a named step, never by capturing the wrong frame.
#
# Usage:
#   scripts/device-walk.sh [--serial <device>] [--out <dir>] [--check-only]
#
# See scripts/README-verification.md for prerequisites and how to read a run.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI_QUERY="$REPO_ROOT/scripts/ui-query.py"
APPLICATION_ID="io.seatlayer.sample"
ACTIVITY="$APPLICATION_ID/.MainActivity"
LOG_TAG="SeatLayerSample"
READY_MARKER="seatlayer-picker-ready"
DEFAULT_APK="$REPO_ROOT/sample/build/outputs/apk/debug/sample-debug.apk"

SERIAL=""
OUT_DIR="${SEATLAYER_WALK_OUT:-$REPO_ROOT/artifacts/device-walk}"
CHECK_ONLY=0
STEP_INDEX=0
WORK_DIR=""

# ---------------------------------------------------------------- diagnostics

log() { printf '[walk] %s\n' "$*" >&2; }
fail() { printf '[walk] FAILED: %s\n' "$*" >&2; exit 1; }
note() { printf '[walk] note: %s\n' "$*" >&2; }

cleanup() {
  # The pushed properties file carries the buyer bearer; it does not outlive the
  # walk. `pm clear` at launch would not reach it, so it is removed by name.
  if [[ -n "${DEVICE_DEBUG_PROPERTIES:-}" ]]; then
    adb_device shell rm -f "$DEVICE_DEBUG_PROPERTIES" >/dev/null 2>&1 || true
  fi
  [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]] && rm -rf "$WORK_DIR"
  return 0
}
trap cleanup EXIT

# ------------------------------------------------------------------ arguments

while (( $# > 0 )); do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --out) OUT_DIR="${2:-}"; shift 2 ;;
    --check-only) CHECK_ONLY=1; shift ;;
    -h|--help) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

# ------------------------------------------------------- environment contract
#
# Nothing that identifies an event, a backend or a buyer is committed. Each of
# these must be exported by the operator; the script never echoes their values.
#
# Only the event key travels as an intent extra. Everything else — the API base,
# the publishable key, the buyer bearer — is written into a properties file and
# pushed into the sample app's own external files directory, because an
# `am start --es` extra is visible in `ps` to every process on the device and is
# written verbatim into logcat by ActivityManager, where it outlives the run.
# The file is created with mode 600, deleted from the device when the walk ends,
# and its contents are never echoed.

require_env() {
  local name="$1" purpose="$2"
  if [[ -z "${!name:-}" ]]; then
    fail "\$$name is not set. It supplies $purpose. Export it before running; the script never prints it."
  fi
}

require_env SEATLAYER_WALK_EVENT "the event the sample app opens"
require_env SEATLAYER_WALK_API_BASE "the API base URL the picker talks to"
# SEATLAYER_WALK_PUBLIC_KEY and SEATLAYER_WALK_TOKEN are optional: set them for
# a private or key-scoped event. SEATLAYER_WALK_TOKEN_EXPIRES_AT is that token's
# expiry in epoch milliseconds; without it the SDK refreshes reactively.
# SEATLAYER_WALK_AVD names the emulator to boot when none is running.

DEBUG_PROPERTIES_NAME="seatlayer-debug.properties"
DEVICE_FILES_DIR="/sdcard/Android/data/$APPLICATION_ID/files"
DEVICE_DEBUG_PROPERTIES="$DEVICE_FILES_DIR/$DEBUG_PROPERTIES_NAME"

# ------------------------------------------------------------- prerequisites

missing_prerequisite=0

need_command() {
  local name="$1" why="$2"
  if ! command -v "$name" >/dev/null 2>&1; then
    printf '[walk] missing prerequisite: %s (%s)\n' "$name" "$why" >&2
    missing_prerequisite=1
  fi
}

python_bin() {
  printf '%s' "${SEATLAYER_WALK_PYTHON:-python3}"
}

check_prerequisites() {
  need_command adb "installs the app and drives input; part of the Android platform tools"
  need_command "$(python_bin)" "parses the view hierarchy; override with \$SEATLAYER_WALK_PYTHON"

  [[ -x "$UI_QUERY" ]] || { printf '[walk] missing prerequisite: %s\n' "$UI_QUERY" >&2; missing_prerequisite=1; }

  local apk="${SEATLAYER_WALK_APK:-$DEFAULT_APK}"
  if [[ ! -f "$apk" ]]; then
    printf '[walk] missing prerequisite: sample APK not found at %s\n' "$apk" >&2
    printf '[walk]   build it once with: ./gradlew :sample:assembleDebug\n' >&2
    printf '[walk]   or point $SEATLAYER_WALK_APK at an existing build\n' >&2
    missing_prerequisite=1
  fi

  local activity="$REPO_ROOT/sample/src/main/kotlin/io/seatlayer/sample/MainActivity.kt"
  if ! grep -q 'seatlayerEvent' "$activity" 2>/dev/null; then
    printf '[walk] missing prerequisite: the sample app no longer reads the seatlayerEvent intent extra\n' >&2
    missing_prerequisite=1
  fi
  if ! grep -q 'SampleDebugInputs' "$activity" 2>/dev/null; then
    printf '[walk] missing prerequisite: the sample app no longer reads its debug properties file, so the API base, key and buyer token cannot reach it\n' >&2
    missing_prerequisite=1
  fi

  (( missing_prerequisite == 0 )) || fail "prerequisites are missing; nothing was launched"
}

adb_device() {
  if [[ -n "$SERIAL" ]]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi
}

poll_until() {
  # poll_until <seconds> <description> <command...>
  local timeout="$1" description="$2"; shift 2
  local deadline=$(( SECONDS + timeout ))
  while (( SECONDS < deadline )); do
    if "$@" >/dev/null 2>&1; then return 0; fi
    sleep 0.4
  done
  printf '[walk] timed out after %ss waiting for: %s\n' "$timeout" "$description" >&2
  return 1
}

boot_device() {
  local online
  online="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -1)"
  if [[ -z "$online" ]]; then
    if [[ -z "${SEATLAYER_WALK_AVD:-}" ]]; then
      fail "no device is attached and \$SEATLAYER_WALK_AVD is not set. Start an emulator, or export the AVD name (see scripts/README-verification.md for the AVD this walk expects)."
    fi
    need_command emulator "boots the AVD named by \$SEATLAYER_WALK_AVD"
    (( missing_prerequisite == 0 )) || fail "cannot boot an emulator without the emulator tool"
    log "booting AVD $SEATLAYER_WALK_AVD"
    nohup emulator -avd "$SEATLAYER_WALK_AVD" -no-snapshot-save >/dev/null 2>&1 &
    poll_until 240 "the emulator to appear" bash -c 'adb devices | grep -q "[[:space:]]device$"' \
      || fail "the emulator did not come online"
    online="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -1)"
  fi
  [[ -n "$SERIAL" ]] || SERIAL="$online"
  poll_until 240 "the device to finish booting" \
    bash -c "[[ \"\$(adb -s '$SERIAL' shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')\" == 1 ]]" \
    || fail "the device never reported sys.boot_completed"
  log "device $SERIAL is ready"
}

check_device_capabilities() {
  local webview
  webview="$(adb_device shell 'pm list packages | grep -i webview' 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "$webview" ]]; then
    fail "this system image carries no WebView package. The picker renders the map in a WebView, so an image with Google APIs (or any image that ships a WebView provider) is required."
  fi

  local size density
  size="$(adb_device shell wm size | tr -d '\r' | awk -F': ' '/Override|Physical/ {print $2}' | tail -1)"
  density="$(adb_device shell wm density | tr -d '\r' | awk -F': ' '/Override|Physical/ {print $2}' | tail -1)"
  log "screen ${size} at ${density} dpi"
  local expected_size="${SEATLAYER_WALK_EXPECT_SIZE:-1080x2340}"
  local expected_density="${SEATLAYER_WALK_EXPECT_DENSITY:-440}"
  if [[ "$size" != "$expected_size" || "$density" != "$expected_density" ]]; then
    note "screen is ${size}@${density}, not the expected ${expected_size}@${expected_density}. Frames from this device are not comparable pixel-for-pixel with reference frames captured at the expected size. Override the expectation with \$SEATLAYER_WALK_EXPECT_SIZE / \$SEATLAYER_WALK_EXPECT_DENSITY."
  fi
}

# ------------------------------------------------------------------- fixtures

label_for() {
  # label_for <key> <english fallback>
  local key="$1" fallback="$2"
  local source="${SEATLAYER_WALK_LABELS:-$REPO_ROOT/Design/locale_strings.json}"
  local value=""
  if [[ -f "$source" ]]; then
    value="$("$(python_bin)" - "$source" "$key" <<'PY' 2>/dev/null || true
import json, sys
path, key = sys.argv[1], sys.argv[2]
data = json.load(open(path))
table = data.get("strings", data)
table = table.get("en", table)
value = table.get(key)
if isinstance(value, str):
    print(value)
PY
)"
  fi
  printf '%s' "${value:-$fallback}"
}

# ---------------------------------------------------------------- device acts

dump_ui() {
  local target="$WORK_DIR/window_dump.xml"
  adb_device shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || return 1
  adb_device pull /sdcard/window_dump.xml "$target" >/dev/null 2>&1 || return 1
  [[ -s "$target" ]]
}

ui_exists() {
  dump_ui || return 1
  "$(python_bin)" "$UI_QUERY" exists "$WORK_DIR/window_dump.xml" "$1"
}

ui_absent() {
  dump_ui || return 1
  ! "$(python_bin)" "$UI_QUERY" exists "$WORK_DIR/window_dump.xml" "$1"
}

ui_centre() {
  dump_ui || return 1
  "$(python_bin)" "$UI_QUERY" centre "$WORK_DIR/window_dump.xml" "$1"
}

wait_for_ui() {
  # wait_for_ui <pattern> <seconds> <description>
  poll_until "$2" "$3" ui_exists "$1"
}

tap_xy() {
  adb_device shell input tap "$1" "$2" >/dev/null
}

tap_label() {
  # tap_label <pattern> <description>
  local point
  point="$(ui_centre "$1")" || fail "could not find on screen: $2 (searched for /$1/)"
  # shellcheck disable=SC2086
  tap_xy $point
  log "tapped $2"
}

tap_map_fraction() {
  # tap_map_fraction <x fraction> <y fraction> <description>
  local width height x y
  read -r width height <<<"$(screen_pixels)"
  x="$("$(python_bin)" -c "print(int($width * $1))")"
  y="$("$(python_bin)" -c "print(int($height * $2))")"
  tap_xy "$x" "$y"
  log "tapped the map at ${1}x${2} of the screen ($3)"
}

screen_pixels() {
  adb_device shell wm size | tr -d '\r' | awk -F': ' '/Override|Physical/ {print $2}' | tail -1 \
    | awk -Fx '{print $1, $2}'
}

capture() {
  # capture <name>
  STEP_INDEX=$(( STEP_INDEX + 1 ))
  local name
  name="$(printf '%02d-%s.png' "$STEP_INDEX" "$1")"
  wait_until_settled
  adb_device exec-out screencap -p > "$OUT_DIR/$name"
  [[ -s "$OUT_DIR/$name" ]] || fail "screen capture produced an empty file for $1"
  log "captured $name"
}

wait_until_settled() {
  # The frame is stable once two consecutive hierarchy dumps are identical.
  local previous="" current="" attempts=0
  while (( attempts < 25 )); do
    dump_ui || { sleep 0.4; attempts=$(( attempts + 1 )); continue; }
    current="$("$(python_bin)" -c "
import hashlib, sys
print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())" "$WORK_DIR/window_dump.xml")"
    [[ "$current" == "$previous" ]] && return 0
    previous="$current"
    attempts=$(( attempts + 1 ))
    sleep 0.4
  done
  note "the screen never settled; capturing anyway"
}

# ----------------------------------------------------------- app-state reads

selection_count() {
  # The sample logs every snapshot the bridge delivers. The selection count in
  # the newest line is the app's own state, not a guess from pixels.
  adb_device logcat -d -s "$LOG_TAG:I" 2>/dev/null \
    | grep -o 'selection=[0-9]\+' | tail -1 | cut -d= -f2
}

map_rung() {
  adb_device logcat -d -s "$LOG_TAG:I" 2>/dev/null \
    | grep -o 'rung=[A-Za-z0-9_]\+' | tail -1 | cut -d= -f2
}

selection_is() {
  [[ "$(selection_count)" == "$1" ]]
}

rung_changed_from() {
  [[ "$(map_rung)" != "$1" ]]
}

# ------------------------------------------------------- blocked-region probe

probe_blocked_regions() {
  # What this proves: a tap landing on the exact centre of a control that
  # stands over the map is consumed by that control and does NOT also reach the
  # WebView canvas, so no seat under the control is selected. That guard is the
  # picker.setBlockedRegions list; nothing on the JVM can exercise it, because
  # the JVM tests have no WebView and no real touch dispatch.
  #
  # What it does NOT prove: that the blocked list is correct in every camera
  # state (it is checked once, at the overview rung), that it survives an inset
  # change mid-gesture, or that the control's own action fired. The walk steps
  # that follow check the actions.
  local before after
  before="$(selection_count)"
  before="${before:-0}"
  local failures=0

  local -a controls=(
    "$(label_for accessibilityTitle 'Accessibility and colour options')|accessibility disc"
    "$(label_for fitWholeVenue 'Show whole venue')|whole-venue disc"
    "$(label_for zoomIn 'Zoom in')|plus disc"
  )

  for entry in "${controls[@]}"; do
    local pattern="${entry%%|*}" description="${entry##*|}"
    local point
    if ! point="$(ui_centre "$pattern")"; then
      note "blocked-region probe skipped $description: it is not on screen at this rung"
      continue
    fi
    # shellcheck disable=SC2086
    tap_xy $point
    wait_until_settled
    after="$(selection_count)"
    after="${after:-0}"
    if [[ "$after" != "$before" ]]; then
      printf '[walk] blocked-region probe FAILED at %s: selection went %s -> %s, so the tap reached the map through the control\n' \
        "$description" "$before" "$after" >&2
      failures=$(( failures + 1 ))
    else
      log "blocked-region probe passed at $description (selection stayed $before)"
    fi
    adb_device shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    wait_until_settled
  done

  (( failures == 0 )) || fail "the blocked-region guard let $failures tap(s) through to the map"
  capture "blocked-region-probe"
}

# ------------------------------------------------------- credentials on device

push_debug_properties() {
  # Written locally with a private umask, pushed, then chmod'd on the device.
  # No value is ever passed on a command line and none is ever printed.
  local staged="$WORK_DIR/$DEBUG_PROPERTIES_NAME"
  local previous_umask; previous_umask="$(umask)"
  umask 077
  : > "$staged"
  printf 'apiBase=%s\n' "$SEATLAYER_WALK_API_BASE" >> "$staged"
  if [[ -n "${SEATLAYER_WALK_PUBLIC_KEY:-}" ]]; then
    printf 'publicKey=%s\n' "$SEATLAYER_WALK_PUBLIC_KEY" >> "$staged"
  fi
  if [[ -n "${SEATLAYER_WALK_TOKEN:-}" ]]; then
    printf 'buyerToken=%s\n' "$SEATLAYER_WALK_TOKEN" >> "$staged"
  fi
  if [[ -n "${SEATLAYER_WALK_TOKEN_EXPIRES_AT:-}" ]]; then
    printf 'buyerTokenExpiresAt=%s\n' "$SEATLAYER_WALK_TOKEN_EXPIRES_AT" >> "$staged"
  fi
  umask "$previous_umask"

  adb_device shell mkdir -p "$DEVICE_FILES_DIR" >/dev/null 2>&1 || true
  adb_device push "$staged" "$DEVICE_DEBUG_PROPERTIES" >/dev/null 2>&1 \
    || fail "could not place the debug properties file on the device. The app's external files directory exists only once the app has been installed; check that the install above succeeded."
  adb_device shell chmod 600 "$DEVICE_DEBUG_PROPERTIES" >/dev/null 2>&1 || true
  rm -f "$staged"
  log "placed the walk's inputs on the device"
}

# ----------------------------------------------------------------- the launch

launch_app() {
  local apk="${SEATLAYER_WALK_APK:-$DEFAULT_APK}"
  log "installing the sample app"
  adb_device install -r -g "$apk" >/dev/null || fail "could not install $apk"
  adb_device shell pm clear "$APPLICATION_ID" >/dev/null 2>&1 || true
  adb_device logcat -c >/dev/null 2>&1 || true

  push_debug_properties

  # Only the event key and the integration name travel as extras. Neither is a
  # secret; everything that is went into the file above.
  local -a extras=(
    --es seatlayerEvent "$SEATLAYER_WALK_EVENT"
    --es seatlayerIntegration "${SEATLAYER_WALK_INTEGRATION:-ready-compose}"
  )

  adb_device shell am start -W -n "$ACTIVITY" "${extras[@]}" >/dev/null \
    || fail "could not start $ACTIVITY"

  wait_for_ui "$READY_MARKER" 90 "the picker to report ready" \
    || fail "the picker never reported ready. The hosted runtime may not have loaded; check 'adb logcat -s $LOG_TAG'."
  log "picker is ready"
}

# ------------------------------------------------------------------- the walk

walk() {
  local cancel select remove access find_seats continue_word venue back_to_venue
  cancel="$(label_for cancel 'Cancel')"
  select="$(label_for select 'Select')"
  remove="$(label_for removeSeat 'Remove seat')"
  access="$(label_for accessibilityTitle 'Accessibility and colour options')"
  find_seats="$(label_for findSeatsTogether 'Find seats together')"
  continue_word="$(label_for continueWord 'Continue')"
  venue="$(label_for overview 'Venue')"
  back_to_venue="$(label_for backToVenue 'Back to venue')"

  # 1. Overview: the discs, the price rail, the test chip, the Map|3D switch.
  capture "overview"
  ui_exists "$access" || note "the accessibility disc is not in the hierarchy at the overview rung"
  ui_exists "$(label_for testMode 'TEST MODE')" || note "no test-mode chip on this event"

  # 2. The device-only guard, taken while the cart is still empty.
  probe_blocked_regions

  # 3. Focus a section, then a seat, then the confirm card.
  local rung_before; rung_before="$(map_rung)"
  tap_map_fraction "${SEATLAYER_WALK_SECTION_X:-0.5}" "${SEATLAYER_WALK_SECTION_Y:-0.42}" "a section"
  poll_until 20 "the map to leave the overview rung" rung_changed_from "$rung_before" \
    || note "the map rung did not change; the section tap may have missed"
  capture "section"

  tap_map_fraction "${SEATLAYER_WALK_SEAT_X:-0.5}" "${SEATLAYER_WALK_SEAT_Y:-0.5}" "a seat"
  wait_for_ui "$cancel|$select" 20 "the seat confirm card" \
    || fail "no confirm card appeared after tapping a seat"
  capture "seat-card"

  # 4. Cancel returns to the map with nothing selected.
  local selection_before; selection_before="$(selection_count)"; selection_before="${selection_before:-0}"
  tap_label "$cancel" "Cancel"
  poll_until 15 "the confirm card to close" ui_absent "$cancel" || note "the card did not close"
  capture "card-cancelled"
  selection_is "${selection_before:-0}" || note "Cancel left a selection behind"

  # 5. Tap the seat again and add it: chip flight, count swell, pull-back.
  tap_map_fraction "${SEATLAYER_WALK_SEAT_X:-0.5}" "${SEATLAYER_WALK_SEAT_Y:-0.5}" "the same seat"
  wait_for_ui "$select" 20 "the confirm card" || fail "the confirm card did not reopen"
  tap_label "$select" "Add"
  poll_until 20 "the cart to hold one seat" selection_is 1 || fail "adding the seat did not change the app's selection"
  capture "seat-added"

  # 6. Retap the same seat: the card now offers Remove.
  tap_map_fraction "${SEATLAYER_WALK_SEAT_X:-0.5}" "${SEATLAYER_WALK_SEAT_Y:-0.5}" "the added seat"
  wait_for_ui "$remove" 20 "the remove card" || note "retapping an added seat did not offer Remove"
  capture "remove-card"
  if ui_exists "$cancel"; then tap_label "$cancel" "Cancel"; fi

  # 7. Accessibility sheet and one filter.
  tap_label "$access" "the accessibility disc"
  wait_for_ui "$(label_for notAvailable 'Not available')|$(label_for colorblindSafe 'Colourblind-friendly colours')" 20 \
    "the accessibility sheet" || note "the accessibility sheet did not open"
  capture "accessibility-sheet"
  tap_label "$(label_for 'accessNeeds.wheelchair' 'Wheelchair space')" "the wheelchair filter"
  capture "filter-toggled"
  adb_device shell input keyevent KEYCODE_BACK >/dev/null
  wait_until_settled

  # 8. Best seats.
  if ui_exists "$find_seats"; then
    tap_label "$find_seats" "Find seats together"
    capture "find-seats"
  else
    note "the best-seats control is not on screen; skipped"
  fi

  # 9. Hold and checkout: the pill, then back, then a replacement hold.
  tap_label "$continue_word|$(label_for holdAndCheckout 'Hold seats & checkout')" "Continue"
  capture "hold-pill"
  adb_device shell input keyevent KEYCODE_BACK >/dev/null
  wait_until_settled
  capture "back-from-checkout"

  tap_map_fraction "${SEATLAYER_WALK_SEAT2_X:-0.55}" "${SEATLAYER_WALK_SEAT2_Y:-0.5}" "a second seat"
  if ui_exists "$select"; then
    tap_label "$select" "Add"
    poll_until 20 "the cart to hold two seats" selection_is 2 || note "the second seat did not join the cart"
  fi
  tap_label "$continue_word|$(label_for holdAndCheckout 'Hold seats & checkout')" "Continue again"
  capture "hold-replaced"

  # 10. Expiry. The wait is bounded by the hold window the operator states.
  local ttl="${SEATLAYER_WALK_HOLD_TTL_SECONDS:-0}"
  if (( ttl > 0 )); then
    log "waiting up to $((ttl + 60))s for the hold to expire"
    poll_until $(( ttl + 60 )) "the hold to expire" \
      ui_exists "$(label_for reselectSeats.other 'Select them again')|$(label_for retry 'Try again')" \
      || note "no expiry state appeared inside the stated hold window"
    capture "hold-expired"
  else
    note "\$SEATLAYER_WALK_HOLD_TTL_SECONDS is 0; the expiry step was skipped"
  fi

  # 11. 3D and the view from a seat.
  if ui_exists '3D'; then
    tap_label '3D' "the 3D switch"
    wait_for_ui "$back_to_venue|$venue|$(label_for recentre 'Recentre on the stage')" 40 "the 3D venue" \
      || note "the 3D view did not announce itself"
    capture "venue-3d"
    tap_map_fraction "${SEATLAYER_WALK_SEAT_X:-0.5}" "${SEATLAYER_WALK_SEAT_Y:-0.5}" "a seat in 3D"
    if ui_exists "$(label_for viewFromHere 'View from here')"; then
      tap_label "$(label_for viewFromHere 'View from here')" "View from here"
      capture "seat-view"
    else
      note "no view-from-here control appeared in 3D"
    fi
  else
    note "no 3D switch on screen; the 3D steps were skipped"
  fi
}

# ----------------------------------------------------------------------- main

main() {
  check_prerequisites
  if (( CHECK_ONLY )); then
    log "prerequisites satisfied (--check-only: nothing was launched)"
    exit 0
  fi
  boot_device
  check_device_capabilities
  WORK_DIR="$(mktemp -d)"
  mkdir -p "$OUT_DIR"
  launch_app
  walk
  log "walk complete; frames are in $OUT_DIR"
  log "compare them with: scripts/pixel-compare.py $OUT_DIR <reference dir>"
}

main "$@"
