#!/usr/bin/env bash
# fetch-core.sh — the PUBLIC core jar a level is built and played with, exactly as <level>/core.lock pins it.
#
#   tools/fetch-core.sh [LEVEL]        # default isle; writes LEVEL/.core/<asset> and prints its path
#
# Downloads the release asset core.lock names (with `gh release download` when gh is there, which works while the
# core's repository is private given GH_TOKEN with read access to it; else a plain curl, which works once it is
# public) and checks its sha256 against core.lock EVERY time, the already-present file included. A mismatch deletes
# the file. Exit 0 the jar is there and matches · 1 a download failed or the sha256 did not match · 2 core.lock has no
# sha256 yet or is malformed.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
level="${1:-isle}"
lock="$here/$level/core.lock"
[ -f "$lock" ] || { echo "fetch-core: no $lock" >&2; exit 2; }
prop() { sed -n "s/^$1=//p" "$lock" | head -1; }
repo="$(prop core_repo)"; tag="$(prop core_tag)"; asset="$(prop core_asset)"; want="$(prop core_sha256)"
[ -n "$repo" ] && [ -n "$tag" ] && [ -n "$asset" ] || { echo "fetch-core: $lock lacks core_repo/core_tag/core_asset" >&2; exit 2; }
printf '%s' "$want" | grep -Eq '^[0-9a-f]{64}$' || { echo "fetch-core: $lock has no sha256 for $asset yet (core_sha256=$want)" >&2; exit 2; }
dir="$here/$level/.core"; jar="$dir/$asset"
mkdir -p "$dir"
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | awk '{print $1}'; }
if [ ! -f "$jar" ]; then
    if command -v gh >/dev/null && [ -n "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ]; then
        gh release download "$tag" -R "$repo" -p "$asset" -D "$dir" --clobber >&2 \
            || { echo "fetch-core: gh could not download $asset from $repo@$tag (a private repository needs a token that can read it)" >&2; exit 1; }
    else
        curl -fsSL --retry 3 -o "$jar.part" "https://github.com/$repo/releases/download/$tag/$asset" \
            || { rm -f "$jar.part"; echo "fetch-core: could not download $asset from $repo@$tag" >&2; exit 1; }
        mv "$jar.part" "$jar"
    fi
fi
got="$(sha "$jar")"
if [ "$got" != "$want" ]; then
    rm -f "$jar"
    echo "fetch-core: sha256 MISMATCH for $asset: core.lock pins $want, got $got; the file was deleted" >&2
    exit 1
fi
echo "fetch-core: $asset matches core.lock ($repo@$tag)" >&2
echo "$jar"
