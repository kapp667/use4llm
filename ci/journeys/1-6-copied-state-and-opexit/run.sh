#!/bin/bash
# usage: run.sh <worktree>
# Needs a JDK, Maven with a warm local repository, and the USE 7.5.0 release (see ../use-home.sh).
# Builds USE from the worktree (offline, tests skipped) and runs end-to-end cases through the jar.
W=$(cd "$1" && pwd)
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../use-home.sh"
HOME_USE=$USE_HOME
LOG=$(mktemp -d)
JAR=$W/use-gui/target/use-gui.jar

(cd "$W" && mvn -q -B -o -DskipTests -pl use-core,use-gui package > "$LOG/build.log" 2>&1) \
  || { echo "BUILD FAILED, see $LOG/build.log"; echo "SOME FAIL"; exit 1; }
rm -f "$W/docs/archunit-results/cycles-current-failure-report.txt"

ALL=0
# check <case> <required-lines-file> <forbidden-lines-file>
# runs cases/<case>/model.use with cases/<case>/script.cmd inside a scratch copy
run_case() {
  local name=$1 dir out tmp
  dir=$HERE/cases/$name
  tmp=$(mktemp -d)
  cp "$dir"/* "$tmp"/; MODEL=$(cd "$tmp" && ls *.use | head -1)
  out=$(cd "$tmp" && java -Djava.awt.headless=true -jar "$JAR" -H=$HOME_USE -nogui -nr $MODEL script.cmd </dev/null 2>&1 | grep -v Picked)
  local ok=1 line
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    printf '%s\n' "$out" | grep -qF -- "$line" || { ok=0; echo "  missing: $line"; }
  done < "$dir/require.txt"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    printf '%s\n' "$out" | grep -qF -- "$line" && { ok=0; echo "  forbidden present: $line"; }
  done < "$dir/forbid.txt"
  if [ $ok = 1 ]; then echo "PASS $name"; else echo "FAIL $name"; ALL=1; printf '%s\n' "$out" | sed 's/^/    | /' | head -40; fi
  rm -rf "$tmp"
}

for c in $(ls "$HERE/cases"); do run_case "$c"; done

if [ $ALL = 0 ]; then echo "ALL PASS"; else echo "SOME FAIL"; fi
