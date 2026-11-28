#!/bin/bash
# usage: run.sh <worktree>   (needs the USE release of ../use-home.sh)  builds USE from the worktree (offline, no tests) and runs end-to-end cases
WT=$(cd "${1:?worktree}" && pwd)
. "$(dirname "$0")/../use-home.sh"
H=$USE_HOME
D=$(mktemp -d)
(cd "$WT" && mvn -q -B -o -pl use-core,use-gui -DskipTests package > "$D/build.log" 2>&1) || { echo "BUILD FAILED, see $D/build.log"; echo "SOME FAIL"; exit 1; }
rm -f "$WT/docs/archunit-results/cycles-current-failure-report.txt"
J=$WT/use-gui/target/use-gui.jar
cd "$D"
printf 'model M\nclass A\nattributes\n  x : Integer\nend\nconstraints\ncontext A inv pos: self.x > 0\n' > model.use
printf '!create a : A\n!set a.x := 1\n' > ok.cmd
printf '!create a : A\n!set a.x := 0\n' > fail.cmd
printf '!create a : A\n!set a.x := 1\n!destroy nobody\n' > destroy.cmd
printf "!create a : A\n!set a.x := 'str'\n" > wrongtype.cmd
printf '!create a : A\n!set a.x := 1\n!insert (a, a) into NoAssoc\n' > noassoc.cmd
printf '!create a : A\n!set a.x := 0\ngen flags NoSuch +d\n' > genflags.cmd
printf '!create a : A\n!set a.x := 0\nconstraints -flags A::pos Foo Bar +d\nconstraints -flags\n' > twounknown.cmd
FAILS=0
# run <args...>: prints output to out.txt, sets CODE
run() { java -jar "$J" -H="$H" -nr "$@" > out.txt 2>&1 < /dev/null; CODE=$?; }
report() { if [ "$2" = ok ]; then echo "PASS $1"; else echo "FAIL $1 ($3)"; FAILS=$((FAILS+1)); fi; }
code_case() { # name expected args...
  local name=$1 exp=$2; shift 2
  run "$@"
  if [ "$CODE" = "$exp" ]; then report "$name" ok; else report "$name" no "exit $CODE, expected $exp"; fi
}
# control cases (right today)
code_case control_valid_exit0 0 -q model.use ok.cmd
code_case control_failing_exit1 1 -qv model.use fail.cmd
# issue 12
code_case destroy_unknown_object_exit2 2 -q model.use destroy.cmd
code_case wrong_attribute_type_exit2 2 -qv model.use wrongtype.cmd
code_case insert_into_unknown_association_exit2 2 -q model.use noassoc.cmd
# issue 15
run -nogui model.use genflags.cmd
if grep -q "^Error: Model has no invariant named .NoSuch'\.$" out.txt && ! grep -q "Ignoring\|syntax is" out.txt; then report gen_flags_alias_unknown_name_message ok; else report gen_flags_alias_unknown_name_message no "$(grep -c . out.txt) lines"; fi
run -nogui model.use twounknown.cmd
if grep -q "named .Foo'\." out.txt && grep -q "named .Bar'\." out.txt && ! grep -q "Ignoring\|syntax is" out.txt && grep -A3 "enabled class invariants" out.txt | grep -q "A::pos"; then report two_unknown_names_both_reported_nothing_changed ok; else report two_unknown_names_both_reported_nothing_changed no "see output"; fi
if [ $FAILS = 0 ]; then echo "ALL PASS"; else echo "SOME FAIL"; fi
rm -rf "$D"
