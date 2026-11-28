#!/usr/bin/env bash
# End-to-end check for use4llm #9. Usage: run.sh <worktree>
# Needs the fork jar of the ModelValidator plugin: $MV_FORK_JAR, else the latest-develop release
# asset of kapp667/use4llm-plugins, fetched into $MV_CACHE. Builds the assembly zip from the worktree, unpacks it, and drives the shipped USE
# with the shipped plugins through the command line.
WT=${1:?usage: run.sh <worktree>}
WT=$(cd "$WT" && pwd)
FORK_JAR=${MV_FORK_JAR:-}
if [ -z "$FORK_JAR" ]; then
  FORK_JAR="${MV_CACHE:-$HOME/.cache/use4llm-gate}/fork/ModelValidatorPlugin.jar"
  if [ ! -f "$FORK_JAR" ]; then
    mkdir -p "$(dirname "$FORK_JAR")"
    curl -fsSL --retry 3 -o "$FORK_JAR" https://github.com/kapp667/use4llm-plugins/releases/download/latest-develop/ModelValidatorPlugin.jar \
      || { rm -f "$FORK_JAR"; echo "cannot fetch the fork jar: set MV_FORK_JAR to a ModelValidatorPlugin.jar built by ci/build-and-test.sh in use4llm-plugins"; echo "SOME FAIL"; exit 1; }
  fi
fi

W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

# A file: URL keeps the build offline once the fix reads the property; today the property is ignored.
if ! (cd "$WT" && mvn -q -B -o package -DskipTests "-Duse4llm.mv.jar.url=file://$FORK_JAR") >"$W/build.log" 2>&1; then
  echo "build failed, last lines:"; tail -n 15 "$W/build.log"
  echo "FAIL build"; echo "SOME FAIL"; exit 1
fi
ZIP=$(ls "$WT"/use-assembly/target/use-*-use-bin.zip | head -n 1)
mkdir "$W/dist" && unzip -q "$ZIP" -d "$W/dist"
H=$(ls -d "$W"/dist/use-*)

cat > "$W/m.use" <<'EOM'
model M
class A
end
EOM
printf 'info model\nexit\n' > "$W/info.cmd"
printf 'mv -vc\nexit\n' > "$W/vc.cmd"
printf 'mv ?\nexit\n' > "$W/help.cmd"
printf 'Integer_min = 0\nInteger_max = 3\nbogusKey = 1\n' > "$W/bad.properties"
printf 'mv -validate %s\nexit\n' "$W/bad.properties" > "$W/val.cmd"

run() { java -jar "$H/lib/use-gui.jar" -H="$H" -nogui -nr "$W/m.use" "$W/$1" 2>&1 | grep -v 'log4j\|^Picked up'; }

fails=0
check() { # name, ok(0/1)
  if [ "$2" -eq 0 ]; then echo "PASS $1"; else echo "FAIL $1"; fails=1; fi
}

# control: USE itself starts and loads the model (passes with either plugin jar)
out=$(run info.cmd); echo "$out" | grep -q 'model M'; check control-use-loads-model $?

# the three other upstream plugins are still shipped (control)
n=0; for j in use-filmstrip.jar ObjectToClassPlugin-2.0.jar OCLComplexityPlugin5.1.0.jar AssociationExtend.jar; do [ -f "$H/lib/plugins/$j" ] && n=$((n+1)); done
[ "$n" -eq 4 ]; check control-other-plugins-kept $?

# mv -vc: the plugin loads without a conflict and answers
out=$(run vc.cmd); echo "$out" | grep -q 'Cannot load plugin'; r=$?; [ "$r" -ne 0 ]; check vc-no-cannot-load-plugin $?
echo "$out" | grep -q 'ERROR: No configuration file is specified.'; check vc-reports-missing-configuration $?

# the fork's check for unknown configuration keys is reachable in the shipped zip
out=$(run val.cmd); echo "$out" | grep -q 'bogusKey: this key names no class'; check validate-reports-unknown-key $?

# the fork's mv ? usage text is reachable
out=$(run help.cmd); echo "$out" | grep -q 'Usage: mv ? \[enable|disable|enabled|ocl-expression\]'; check mv-help-usage $?

if [ "$fails" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAIL"; fi
exit "$fails"
