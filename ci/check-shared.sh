#!/usr/bin/env bash
# Keep the files that kapp667/use4llm-plugins and kapp667/use4llm share byte for byte identical.
# use4llm-plugins holds the reference copy. ci/shared-files.txt lists the shared paths.
# Usage: ci/check-shared.sh [--write]   (SHARED_REF picks the reference ref, default develop)
#   With no flag, it names each local file that differs from the reference and exits 1.
#   With --write, it replaces the local copies with the reference.
# Exits 0 only when every listed file matches, and 2 when the reference cannot be read.
set -uo pipefail

ref="${SHARED_REF:-develop}"
base="https://raw.githubusercontent.com/kapp667/use4llm-plugins/$ref"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

curl -fsSL "$base/ci/shared-files.txt" -o "$tmp/list" || { echo "SHARED: cannot read the list at $ref" >&2; exit 2; }
bad=0
while IFS= read -r f; do
	case "$f" in ''|'#'*) continue ;; esac
	curl -fsSL "$base/$f" -o "$tmp/file" || { echo "SHARED: cannot read $f at $ref" >&2; exit 2; }
	if [ "${1:-}" = --write ]; then
		mkdir -p "$(dirname "$f")" && cp "$tmp/file" "$f"
		case "$f" in *.sh) chmod +x "$f" ;; esac
	elif ! cmp -s "$tmp/file" "$f"; then
		echo "SHARED: $f differs from kapp667/use4llm-plugins@$ref"; bad=1
	fi
done < "$tmp/list"
[ "$bad" = 0 ] && echo "SHARED OK"
exit "$bad"
