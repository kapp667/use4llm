#!/usr/bin/env bash
# Run every end-to-end journey of this folder against a worktree of this repository and print one
# PASS or FAIL line per journey, then ALL PASS or SOME FAIL. A run counts only after ALL PASS.
# Usage: ci/journeys/run-all.sh <worktree>
# Each journey is a folder with a run.sh <worktree>. A journey builds USE from the worktree with
# Maven (offline: warm ~/.m2 first, e.g. with one online `mvn -DskipTests package`) and drives the
# jar with the USE 7.5.0 release as home, fetched into $MV_CACHE (default $HOME/.cache/use4llm-gate)
# by use-home.sh. Journey 9 also needs a ModelValidatorPlugin.jar: set MV_FORK_JAR to an absolute
# path, else the latest-develop release asset of kapp667/use4llm-plugins is fetched.
# Needs a JDK 21, Maven, curl, unzip and sha256sum on PATH. Logs go to the temp folder, one per journey.
[ "$#" -eq 1 ] || { sed -n '2,11p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
wt="$(cd "$1" && pwd)" || exit 2
logs="$(mktemp -d)"
fail=0
for run in "$here"/*/run.sh; do
	name="$(basename "$(dirname "$run")")"
	if bash "$run" "$wt" >"$logs/$name.log" 2>&1 && tail -n 1 "$logs/$name.log" | grep -qx 'ALL PASS'; then
		echo "PASS $name"
	else
		echo "FAIL $name (log: $logs/$name.log)"; tail -n 15 "$logs/$name.log"; fail=1
	fi
done
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAIL"; fi
exit "$fail"
