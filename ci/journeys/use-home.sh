# Sourced by the run.sh of each journey. Sets USE_HOME to an unpacked USE 7.5.0 release, which the
# journeys pass to USE as -H so that it finds the plugins. Uses $USE_HOME if it is set, else
# $MV_CACHE/use/use-7.5.0 (MV_CACHE defaults to $HOME/.cache/use4llm-gate, the cache of
# ci/lead-gate.sh in kapp667/use4llm-plugins), and downloads the release there when it is missing.
if [ -z "${USE_HOME:-}" ]; then
	_cache="${MV_CACHE:-$HOME/.cache/use4llm-gate}"
	USE_HOME="$_cache/use/use-7.5.0"
	if [ ! -d "$USE_HOME" ]; then
		echo "fetching the USE 7.5.0 release into $_cache" >&2
		_tmp="$(mktemp -d)"
		curl -fsSL --retry 5 --retry-delay 10 --retry-all-errors -o "$_tmp/use.zip" \
			https://github.com/useocl/use/releases/download/v7.5.0/use-7.5.0-use-bin.zip || { echo "download failed" >&2; exit 1; }
		echo "9b5a49d1d2345e65d1da8518008887e8bc12df388b6eef08228c5a1052618d10  $_tmp/use.zip" | sha256sum --check --quiet || exit 1
		mkdir -p "$_tmp/use" "$_cache"
		unzip -q "$_tmp/use.zip" -d "$_tmp/use"
		rm -rf "$_cache/use"; mv "$_tmp/use" "$_cache/use"; rm -rf "$_tmp"
	fi
fi
export USE_HOME
