#!/usr/bin/env bash
set -euo pipefail

failures=0

report() {
  printf 'public repository hygiene: %s\n' "$1" >&2
  failures=$((failures + 1))
}

while IFS= read -r path; do
  [[ -e "$path" ]] || continue
  lower="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
  base="${lower##*/}"

  case "$lower" in
    doc/*|docs/*|design/*)
      case "/$lower/" in
        */internal/*|*/evidence/*) report "forbidden internal/evidence path: $path" ;;
      esac
      case "$base" in
        *audit*|*comparison*|*parity*|*planning*|*review*|*validation*)
          report "development-process document is not public documentation: $path"
          ;;
      esac
      if [[ "$base" =~ 20[0-9]{2}-[0-9]{2}-[0-9]{2} ]]; then
        report "dated development artifact is not public documentation: $path"
      fi
      ;;
  esac

  case "$lower" in
    *.png|*.jpg|*.jpeg|*.gif|*.mp4|*.mov)
      case "$lower" in
        .github/social-preview.png|doc/media/*|docs/media/*|test/*|tests/*|example/*/assets.xcassets/*|example/android/app/src/main/res/*|sample/src/main/res/*) ;;
        *) report "media is outside an approved public or test-fixture location: $path" ;;
      esac
      ;;
  esac
done < <(git ls-files)

while IFS= read -r match; do
  report "developer-machine path found: $match"
done < <(git grep -n -I -E '(/Users/[^/]+/|/home/[^/]+/|C:\\Users\\[^\\]+\\)' -- . ':(exclude)scripts/check-public-repository.sh' || true)

while IFS= read -r match; do
  report "credential-like value found: $match"
done < <(git grep -n -I -E '(AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)' -- . || true)

if (( failures > 0 )); then
  printf 'public repository hygiene failed with %d issue(s)\n' "$failures" >&2
  exit 1
fi

printf 'public repository hygiene passed\n'
