#!/usr/bin/env bash
# Install the checks in a clone of this repository: git runs tools/git-hooks/pre-commit (credentials and private work
# in what you stage) before every commit, and tools/git-hooks/pre-push (the same, over every outgoing commit) before
# every push.
#
#   ./tools/git-hooks/install.sh                    # built-in rules + tools/leak-rules.txt (any contributor)
#   ./tools/git-hooks/install.sh --forbidden FILE   # also refuse the terms in a forbidden list kept OUTSIDE this
#                                                   #   repository (a maintainer's clone)
#   ./tools/git-hooks/install.sh --check            # exit 0 if installed, 1 if not
#
# It sets core.hooksPath to tools/git-hooks, so the hooks are the COMMITTED ones and update with a pull. A clone whose
# hooksPath already points somewhere else is left alone and named.
set -euo pipefail
top="$(git rev-parse --show-toplevel)"
want="tools/git-hooks"
have="$(git -C "$top" config --get core.hooksPath || true)"
case "${1:-}" in
    "") ;;
    --check)
        [ "$have" = "$want" ] && { echo "git-hooks: installed in $top"; exit 0; }
        echo "git-hooks: NOT installed in $top (core.hooksPath=${have:-unset}); run ./tools/git-hooks/install.sh" >&2
        exit 1 ;;
    --forbidden)
        f="$(cd "$(dirname "${2:?--forbidden needs a file}")" && pwd)/$(basename "$2")"
        [ -f "$f" ] || { echo "git-hooks: no such forbidden list: $f" >&2; exit 2; }
        case "$f" in "$top"/*) echo "git-hooks: keep the forbidden list OUTSIDE this repository (it would leak)" >&2; exit 2 ;; esac
        git -C "$top" config moddy.forbidden "$f" ;;
    *) echo "git-hooks: unknown argument $1" >&2; exit 2 ;;
esac
if [ -n "$have" ] && [ "$have" != "$want" ]; then
    echo "git-hooks: core.hooksPath is already '$have' in $top; left alone. Point it at $want by hand." >&2
    exit 1
fi
chmod +x "$top/$want/pre-commit" "$top/$want/pre-push"
git -C "$top" config core.hooksPath "$want"
echo "git-hooks: secret, leak and licence checks on in $top (pre-commit + pre-push)$(git -C "$top" config --get moddy.forbidden >/dev/null && echo ', with a forbidden list')"
