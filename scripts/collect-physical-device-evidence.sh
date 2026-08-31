#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "usage: $0 <physical-device-serial> [output.md]" >&2
  exit 2
fi

device_serial="$1"
evidence_script_dir="$(cd "$(dirname "$0")" && pwd)"
evidence_repo_dir="$(cd "$evidence_script_dir/.." && pwd)"
evidence_timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_output="${2:-$evidence_repo_dir/build/device-evidence/device-$evidence_timestamp.md}"

if [[ "$(adb -s "$device_serial" get-state 2>/dev/null)" != "device" ]]; then
  echo "Android device is not connected and authorized." >&2
  exit 3
fi

device_qemu="$(adb -s "$device_serial" shell getprop ro.kernel.qemu | tr -d '\r')"
if [[ "$device_serial" == emulator-* || "$device_qemu" == "1" ]]; then
  echo "Release evidence requires physical Android hardware; emulator rejected." >&2
  exit 4
fi

device_prop() {
  adb -s "$device_serial" shell getprop "$1" | tr -d '\r'
}

device_hash="$(printf '%s' "$device_serial" | shasum -a 256 | awk '{print $1}')"
device_manufacturer="$(device_prop ro.product.manufacturer)"
device_model="$(device_prop ro.product.model)"
device_name="$(device_prop ro.product.device)"
device_api="$(device_prop ro.build.version.sdk)"
device_release="$(device_prop ro.build.version.release)"
device_build="$(device_prop ro.build.id)"
device_patch="$(device_prop ro.build.version.security_patch)"
device_abi="$(device_prop ro.product.cpu.abi)"
device_size="$(adb -s "$device_serial" shell wm size | tr -d '\r' | paste -sd ';' -)"
device_density="$(adb -s "$device_serial" shell wm density | tr -d '\r' | paste -sd ';' -)"
device_webview="$(
  adb -s "$device_serial" shell dumpsys webviewupdate |
    tr -d '\r' |
    sed -n 's/^  Current WebView package (name, version): (\(.*\))$/\1/p' |
    head -1
)"

if [[ -z "$device_webview" ]]; then
  echo "Could not resolve the active Android System WebView package." >&2
  exit 5
fi

source_commit="$(git -C "$evidence_repo_dir" rev-parse HEAD)"
source_state="clean"
if [[ -n "$(git -C "$evidence_repo_dir" status --porcelain)" ]]; then
  source_state="dirty"
fi
sdk_version="$(sed -n 's/^VERSION_NAME=//p' "$evidence_repo_dir/gradle.properties")"
renderer_version="$(
  sed -n \
    's/.*SEATLAYER_HOSTED_WEB_VERSION: String = "\(.*\)".*/\1/p' \
    "$evidence_repo_dir/seatlayer/src/main/kotlin/io/seatlayer/android/SeatLayerConfiguration.kt"
)"

mkdir -p "$(dirname "$evidence_output")"
{
  echo "# SeatLayer Android physical-device environment evidence"
  echo
  echo "- Collected (UTC): \`$evidence_timestamp\`"
  echo "- Device serial SHA-256: \`$device_hash\`"
  echo "- Hardware: \`$device_manufacturer $device_model ($device_name)\`"
  echo "- Android: \`$device_release\` / API \`$device_api\`"
  echo "- Build/security patch: \`$device_build\` / \`$device_patch\`"
  echo "- ABI: \`$device_abi\`"
  echo "- Display: \`$device_size\`; \`$device_density\`"
  echo "- Active System WebView: \`$device_webview\`"
  echo "- SDK candidate: \`$sdk_version\` at \`$source_commit\` (\`$source_state\`)"
  echo "- Hosted renderer pin: \`seatlayer-js@$renderer_version\`"
  echo
  echo "This file contains no host credential, buyer bearer, event key, hold id,"
  echo "raw device serial, or benchmark timing. Attach the Macrobenchmark result and"
  echo "the completed hosted-matrix checklist separately."
} > "$evidence_output"

echo "Wrote physical-device environment evidence: $evidence_output"
