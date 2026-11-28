#!/bin/bash
# usage: run.sh <worktree>   (needs the USE release of ../use-home.sh)
W=$(cd "$1" && pwd) || exit 1
. "$(dirname "$0")/../use-home.sh"
H=$USE_HOME
T=$(mktemp -d)
(cd "$W" && mvn -q -B -o -DskipTests package > "$T/build.log" 2>&1) || { echo "FAIL build (see $T/build.log)"; echo "SOME FAIL"; exit 1; }
rm -f "$W/docs/archunit-results/cycles-current-failure-report.txt"
J="$W/use-gui/target/use-gui.jar"

cat > $T/Shop.use <<'EOF'
model Shop
class Item
attributes
  price : Integer
  stock : Integer
constraints
  inv priced: price >= 0
  inv stocked: stock >= 0
end
class Box
attributes
  size : Integer
constraints
  inv fits: size < 10
end
EOF

# suite writer: name, setup lines, test body
mksuite() {
  cat > $T/$1.suite <<EOF
testsuite $1 for model Shop.use
setup
$2
end

$3
EOF
  echo "open $1.suite" > $T/$1.cmd
}

mksuite g1 "  !create i : Item
  !set i.price := 3
  !set i.stock := 4
  !create b : Box
  !set b.size := 2" "testcase a
  assert valid invs, 'all hold'
end
testcase b
  assert valid inv Item::priced, 'holds'
end"
mksuite g2 "  !create i : Item
  !set i.price := 3
  !set i.stock := -4" "testcase a
  assert valid invs, 'stocked violated'
end"
mksuite g3 "  !create i : Item
  !set i.price := 3
  !set i.stock := 4" "testcase a
  assert valid i.nothere > 1, 'no such attribute'
end
testcase b
  assert valid invs
end"
mksuite g4 "  !create i : Item
  !set i.nothere := 3" "testcase a
  assert valid invs
end"
mksuite g5 "  !create i : Item" "testcase a
  assert wibble wobble
end"
mksuite g6 "  !create i : Item
  !set i.price := 1
  !set i.stock := 1" "testcase a
  assert valid invs Item
end
testcase b
  assert valid inv Box::fits, 'holds with no box'
end"
printf '!create i : Item\n!set i.price := 1\n!set i.stock := 1\n' > $T/c1.cmd
printf '!create i : Item\n!set i.price := -1\n!set i.stock := 1\n' > $T/c2.cmd

ok=1
# case NAME CMD FLAGS EXPECTED_EXIT [must-contain...] ; "!text" means must not contain
case_() {
  name=$1; cmd=$2; flags=$3; want=$4; shift 4
  out=$(cd $T && java -Djava.awt.headless=true -jar $J -H=$H -nogui -nr $flags Shop.use $cmd </dev/null 2>&1 | grep -v "Picked up"; echo "EXIT=${PIPESTATUS[0]}")
  code=${out##*EXIT=}
  good=1
  [ "$flags" = "-q" ] && code=$code || true
  if [ "$flags" = "-q" ]; then [ "$code" = "$want" ] || good=0; fi
  for pat in "$@"; do
    if [ "${pat:0:1}" = "!" ]; then
      echo "$out" | grep -qF -- "${pat:1}" && good=0
    else
      echo "$out" | grep -qF -- "$pat" || good=0
    fi
  done
  if [ $good = 1 ]; then echo "PASS $name"; else echo "FAIL $name (want exit $want, got $code)"; ok=0; fi
}

# 1: global assert over several classes and an explicit invariant, both tests pass
# (The no -q run shows the output; the -q run shows the code.)
case_ global-all-pass-output g1.cmd "" 0 "Executing test 2/2 \`b'... success" "### OK ###" "!invNames"
case_ global-all-pass-code g1.cmd "-q" 0
# 2: a violated invariant fails "assert valid invs"
case_ global-violated-output g2.cmd "" 0 "... failure" "### 1 FAILURE ###"
case_ global-violated-code g2.cmd "-q" 2
# 3: an expression error in test 1 ends the suite in error
case_ expr-error-output g3.cmd "" 0 "... error" "### ERROR ###" "!### OK ###"
case_ expr-error-code g3.cmd "-q" 2
# 4: setup error
case_ setup-error-output g4.cmd "" 0 "Error during test setup:" "!### OK ###"
case_ setup-error-code g4.cmd "-q" 2
# 5: the suite does not compile
case_ parse-error-code g5.cmd "-q" 2
# 6: class assert and a named invariant of another class
case_ class-and-named-code g6.cmd "-q" 0
# controls that pass today: plain scripts without a suite
case_ control-valid-state c1.cmd "-q" 0
case_ control-failing-check c2.cmd "-q" 1

if [ $ok = 1 ]; then echo "ALL PASS"; else echo "SOME FAIL"; fi
