#!/usr/bin/env python3
"""changed-worlds.py — which worlds a change needs built and tested.

    python3 tools/changed-worlds.py --base REV     # worlds changed between REV and HEAD, as a JSON list (CI's matrix)
    python3 tools/changed-worlds.py --all          # every world
    python3 tools/changed-worlds.py --self-test

A WORLD is a top-level folder holding core.lock (isle/, ...). A change to a world's folder builds that world. A change
to what every world shares builds them all: shared/, tools/, .github/, the Gradle wrapper (gradle/, gradlew*). Any
other file (the README, LICENSE, .gitignore) builds none. Prints a JSON list on stdout; exit 0, or 2 when git fails.
"""
import argparse
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHARED = ("shared/", "tools/", ".github/", "gradle/", "gradlew", "gradlew.bat")


def worlds(root=ROOT):
    return sorted(p.parent.name for p in pathlib.Path(root).glob("*/core.lock"))


def select(changed, all_worlds):
    """The worlds a list of changed repo-relative paths needs. Pure."""
    if any(c == s or c.startswith(s) for c in changed for s in SHARED):
        return list(all_worlds)
    return sorted({c.split("/", 1)[0] for c in changed if "/" in c and c.split("/", 1)[0] in all_worlds})


def self_test():
    w = ["arena", "isle"]
    assert select(["isle/src/main/java/X.java"], w) == ["isle"]
    assert select(["isle/core.lock", "arena/build.gradle"], w) == ["arena", "isle"]
    assert select(["shared/level.gradle"], w) == w
    assert select(["tools/play.sh"], w) == w
    assert select([".github/workflows/ci.yml"], w) == w
    assert select(["gradlew"], w) == w
    assert select(["README.md", "LICENSE"], w) == []
    assert select(["notaworld/x.txt"], w) == []
    print("changed-worlds: self-test passed")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--base")
    g.add_argument("--all", action="store_true")
    g.add_argument("--self-test", action="store_true")
    a = ap.parse_args(argv)
    if a.self_test:
        return self_test()
    every = worlds()
    if a.all:
        print(json.dumps(every))
        return 0
    try:
        out = subprocess.run(["git", "diff", "--name-only", f"{a.base}...HEAD"], cwd=ROOT, capture_output=True,
                             text=True, check=True).stdout
    except subprocess.CalledProcessError as e:
        print(f"changed-worlds: git diff failed: {e.stderr.strip()}", file=sys.stderr)
        return 2
    print(json.dumps(select([l for l in out.splitlines() if l], every)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
