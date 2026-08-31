#!/usr/bin/env bash
set -euo pipefail

api_script_dir="$(cd "$(dirname "$0")" && pwd)"
api_repo_dir="$(cd "$api_script_dir/.." && pwd)"
api_mode="${1:---check}"

if [[ "$api_mode" != "--check" && "$api_mode" != "--write" ]]; then
  echo "usage: $0 [--check|--write]" >&2
  exit 2
fi

api_javap="$(command -v javap)"
api_jar="$(command -v jar)"
api_unzip="$(command -v unzip)"
api_tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/seatlayer-api.XXXXXX")"
trap 'rm -rf "$api_tmp_dir"' EXIT

dump_api() {
  local api_aar="$1"
  local api_output="$2"
  local api_classes_file="${3:-}"
  local api_include_constants="${4:-yes}"
  local api_classes_jar="$api_tmp_dir/$(basename "$api_aar").classes.jar"
  local api_classes="$api_tmp_dir/$(basename "$api_output").classes"
  local -a api_javap_args=(-public -s)

  "$api_unzip" -p "$api_aar" classes.jar > "$api_classes_jar"
  if [[ -n "$api_classes_file" ]]; then
    cp "$api_classes_file" "$api_classes"
  else
    "$api_jar" tf "$api_classes_jar" |
      LC_ALL=C sort |
      sed -n '/^io\/seatlayer\/.*\.class$/p' |
      sed -E '/\$[a-z][^/]*\$?[0-9]+\.class$/d; /\$[0-9]+\.class$/d' |
      sed 's#/#.#g; s#\.class$##' > "$api_classes"
  fi

  if [[ "$api_include_constants" == "yes" ]]; then
    api_javap_args+=(-constants)
  fi
  xargs -n 80 "$api_javap" "${api_javap_args[@]}" -classpath "$api_classes_jar" \
    < "$api_classes" |
    awk '
      BEGIN { skip = 0 }
      / access\$/ { skip = 1; next }
      skip && /descriptor:/ { skip = 0; next }
      /^Compiled from / { next }
      NF { print }
    ' > "$api_output"
}

check_or_write() {
  local api_generated="$1"
  local api_expected="$2"
  local api_label="$3"
  if [[ "$api_mode" == "--write" ]]; then
    cp "$api_generated" "$api_expected"
    echo "Wrote $api_label API dump"
  elif ! cmp -s "$api_expected" "$api_generated"; then
    echo "$api_label public API differs. Review it, then run:" >&2
    echo "  scripts/verify-public-api.sh --write" >&2
    diff -u "$api_expected" "$api_generated" >&2 || true
    return 1
  else
    echo "Verified $api_label API dump"
  fi
}

core_aar="$api_repo_dir/seatlayer/build/outputs/aar/seatlayer-release.aar"
compose_aar="$api_repo_dir/seatlayer-compose/build/outputs/aar/seatlayer-compose-release.aar"
core_generated="$api_tmp_dir/seatlayer-android.api"
compose_generated="$api_tmp_dir/seatlayer-android-compose.api"
raw_generated="$api_tmp_dir/seatlayer-android-0.2.0.api"

dump_api "$core_aar" "$core_generated"
dump_api "$compose_aar" "$compose_generated"
dump_api \
  "$core_aar" \
  "$raw_generated" \
  "$api_repo_dir/api/seatlayer-android-0.2.0.classes" \
  "no"

check_or_write \
  "$core_generated" \
  "$api_repo_dir/api/seatlayer-android.api" \
  "seatlayer-android"
check_or_write \
  "$compose_generated" \
  "$api_repo_dir/api/seatlayer-android-compose.api" \
  "seatlayer-android-compose"

if ! cmp -s "$api_repo_dir/api/seatlayer-android-0.2.0.api" "$raw_generated"; then
  echo "The supported raw 0.2.x JVM ABI changed:" >&2
  diff -u "$api_repo_dir/api/seatlayer-android-0.2.0.api" "$raw_generated" >&2 || true
  exit 1
fi
echo "Verified raw 0.2.x compatibility surface"
