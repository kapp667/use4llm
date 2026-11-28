#!/usr/bin/env bash
# Checks the plugins in a USE binary zip: lib/plugins must hold exactly one
# ModelValidatorPlugin, and it must be the use4llm-plugins fork build, not the
# upstream one. Needs only bash and unzip.
# Usage: check-plugins.sh <use-*-use-bin.zip>
set -euo pipefail

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "usage: $0 <use-bin.zip>" >&2
  exit 2
fi
zip=$1

# Classes that exist only in the fork (added in use4llm-plugins, absent upstream).
fork_classes=(
  org/tzi/kodkod/model/config/impl/UnknownConfigurationKeys.class
  org/tzi/use/kodkod/solution/NonTranslatedValues.class
)
# Plugins that must keep shipping next to the ModelValidator.
other_jars=(use-filmstrip.jar ObjectToClassPlugin-2.0.jar OCLComplexityPlugin5.1.0.jar AssociationExtend.jar)

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q "$zip" '*/lib/plugins/*' -d "$work" 2>/dev/null || true

plugins_dir=$(find "$work" -type d -path '*/lib/plugins' | head -n 1)
if [ -z "$plugins_dir" ]; then
  echo "ERROR: $zip has no lib/plugins folder" >&2
  exit 1
fi
rel() { echo "lib/plugins/${1#"$plugins_dir"/}"; }

fail=0
err() { echo "ERROR: $*" >&2; fail=1; }

for name in "${other_jars[@]}"; do
  [ -f "$plugins_dir/$name" ] || err "lib/plugins/$name is missing"
done

# Jars whose useplugin.xml declares the ModelValidatorPlugin.
mv_jars=()
while IFS= read -r jar; do
  descriptor=$(unzip -p "$jar" useplugin.xml 2>/dev/null || true)
  if grep -q 'name="ModelValidatorPlugin"' <<<"$descriptor"; then
    mv_jars+=("$jar")
  fi
done < <(find "$plugins_dir" -maxdepth 1 -type f -name '*.jar' | sort)

if [ "${#mv_jars[@]}" -eq 0 ]; then
  err "plugin ModelValidatorPlugin is missing: no jar in lib/plugins has a useplugin.xml with name=\"ModelValidatorPlugin\""
fi
if [ "${#mv_jars[@]}" -gt 1 ]; then
  err "lib/plugins has ${#mv_jars[@]} ModelValidatorPlugin jars, USE refuses to load more than one:"
  for jar in "${mv_jars[@]}"; do echo "  $(rel "$jar")" >&2; done
fi

# grep -q exits at the first match and would break the pipe under pipefail, so list first.
has_class() { local listing; listing=$(unzip -Z1 "$1"); grep -qxF "$2" <<<"$listing"; }

fork_jar=
for jar in "${mv_jars[@]}"; do
  all=1
  for cls in "${fork_classes[@]}"; do has_class "$jar" "$cls" || all=0; done
  if [ "$all" -eq 1 ]; then
    fork_jar=$jar
  else
    err "$(rel "$jar") is the upstream build (it lacks the fork classes)"
  fi
done
if [ -z "$fork_jar" ] && [ "${#mv_jars[@]}" -gt 0 ]; then
  for cls in "${fork_classes[@]}"; do
    err "no ModelValidator jar with $cls"
  done
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi

version=$(unzip -p "$fork_jar" useplugin.xml | grep -o '<plugin [^>]*' | sed -n 's/.*version="\([^"]*\)".*/\1/p' | head -n 1)
echo "OK: lib/plugins has one ModelValidatorPlugin ($version, fork build)"
