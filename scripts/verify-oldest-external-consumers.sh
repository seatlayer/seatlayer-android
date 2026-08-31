#!/usr/bin/env bash
set -euo pipefail

consumer_script_dir="$(cd "$(dirname "$0")" && pwd)"
consumer_repo_dir="$(cd "$consumer_script_dir/.." && pwd)"
consumer_wrapper_dir="$(mktemp -d "${TMPDIR:-/tmp}/seatlayer-gradle-8.13.XXXXXX")"
trap 'rm -rf "$consumer_wrapper_dir"' EXIT

mkdir -p "$consumer_wrapper_dir/gradle/wrapper"
cp "$consumer_repo_dir/gradlew" "$consumer_wrapper_dir/gradlew"
cp \
  "$consumer_repo_dir/gradle/wrapper/gradle-wrapper.jar" \
  "$consumer_wrapper_dir/gradle/wrapper/gradle-wrapper.jar"
cp \
  "$consumer_repo_dir/gradle/wrapper/gradle-wrapper.properties" \
  "$consumer_wrapper_dir/gradle/wrapper/gradle-wrapper.properties"
sed -i.bak \
  's#gradle-9\.6\.1-bin\.zip#gradle-8.13-bin.zip#' \
  "$consumer_wrapper_dir/gradle/wrapper/gradle-wrapper.properties"
rm "$consumer_wrapper_dir/gradle/wrapper/gradle-wrapper.properties.bak"
grep -q 'gradle-8.13-bin.zip' \
  "$consumer_wrapper_dir/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$consumer_wrapper_dir/gradlew"

"$consumer_wrapper_dir/gradlew" \
  --no-daemon \
  -p "$consumer_repo_dir/consumer-smoke" \
  -PconsumerAgpVersion=8.12.0 \
  -PconsumerKotlinVersion=2.3.10 \
  -PconsumerBuiltInKotlin=false \
  verifyConsumers
