#!/usr/bin/env python3
"""core-pins.py — the core-fetch step in the workflows, written from the worlds' core.lock files.

    python3 tools/core-pins.py --write     # rewrite the generated block in every workflow that has one
    python3 tools/core-pins.py --check     # exit 1 when a workflow's block disagrees with the core.lock files (CI)
    python3 tools/core-pins.py --print     # the block, on stdout
    python3 tools/core-pins.py --self-test

WHY THE PIN IS WORKFLOW TEXT. MMVU's real-install finds the jars a mod needs beside it by reading the fork's
workflows: a line holding `curl` and a release URL (https://github.com/<owner>/<repo>/releases/download/<tag>/<x>.jar)
with the jar's 64-hex sha256 on one of the next two lines. So the step that fetches the core in CI and in the release
IS that pin, and the same bytes are what the build then checks against core.lock (shared/level.gradle) and what
tools/fetch-core.sh fetches locally. core.lock is the one place a pin is written; this tool copies it into the workflows
between the BEGIN/END markers, one fetch per distinct core release across the worlds, and --check (run by CI) fails
the moment the two disagree.

While the core's repository is private the plain download is refused, so the line falls back to `gh release download`
with the CORE_READ_TOKEN secret; the sha256 check is the same either way. A core.lock with no sha256 yet makes the step
stop with that message (never a silent skip).
"""
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WORKFLOWS = ROOT / ".github" / "workflows"
BEGIN = "# BEGIN core pins: written by tools/core-pins.py from */core.lock; edit core.lock, then tools/core-pins.py --write"
END = "# END core pins"


def read_lock(path):
    out = {}
    for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def releases(root=ROOT):
    """{(repo, tag, asset, sha256): [worlds]} over every world's core.lock, in a stable order."""
    rel = {}
    for lock in sorted(pathlib.Path(root).glob("*/core.lock")):
        p = read_lock(lock)
        key = (p["core_repo"], p["core_tag"], p["core_asset"], p["core_sha256"])
        rel.setdefault(key, []).append(lock.parent.name)
    return rel


def block(rel, indent):
    pad = " " * indent
    lines = [pad + BEGIN]
    for n, ((repo, tag, asset, sha), worlds) in enumerate(rel.items()):
        lines += [
            f"{pad}- name: The pinned public core {tag} ({', '.join(worlds)})",
            f"{pad}  env:",
            f"{pad}    GH_TOKEN: ${{{{ secrets.CORE_READ_TOKEN }}}}",
            f"{pad}  run: |",
            f"{pad}    mkdir -p \"$RUNNER_TEMP/core\"",
        ]
        if not re.fullmatch(r"[0-9a-f]{64}", sha):
            lines += [f"{pad}    echo \"::error::core.lock pins {asset} with no sha256 yet ({sha}): fill it in, then "
                      f"tools/core-pins.py --write\"", f"{pad}    exit 1"]
        else:
            lines += [
                f"{pad}    curl -fsSL -o \"$RUNNER_TEMP/core/{asset}\" \"https://github.com/{repo}/releases/download/"
                f"{tag}/{asset}\" || gh release download \"{tag}\" -R \"{repo}\" -p \"{asset}\" -D \"$RUNNER_TEMP/core\" --clobber",
                f"{pad}    echo \"{sha}  $RUNNER_TEMP/core/{asset}\" | sha256sum -c -",
                f"{pad}    for w in {' '.join(worlds)}; do mkdir -p \"$w/.core\"; cp \"$RUNNER_TEMP/core/{asset}\" \"$w/.core/\"; done",
            ]
    lines.append(pad + END)
    return "\n".join(lines) + "\n"


def rewrite(text, rel):
    """The workflow text with its generated block replaced; None when it has no block."""
    m = re.search(r"(?m)^( *)" + re.escape(BEGIN) + r"\n.*?^ *" + re.escape(END) + r"\n", text, re.S)
    if not m:
        return None
    return text[:m.start()] + block(rel, len(m.group(1))) + text[m.end():]


def self_test():
    rel = {("o/core", "core-1.0-mc1.21.8", "core-1.0+mc1.21.8.jar", "a" * 64): ["arena", "isle"]}
    b = block(rel, 6)
    lines = b.splitlines()
    i = next(n for n, l in enumerate(lines) if "curl" in l)
    assert "https://github.com/o/core/releases/download/core-1.0-mc1.21.8/core-1.0+mc1.21.8.jar" in lines[i]
    assert re.search(r"\b[0-9a-f]{64}\b", lines[i + 1]), "the sha256 sits on the line after the curl (MMVU reads it there)"
    assert "for w in arena isle" in b
    todo = block({("o/core", "t", "a.jar", "TODO"): ["isle"]}, 6)
    assert "curl" not in todo and "exit 1" in todo, "no sha256 means a red step, never an unpinned download"
    wf = "jobs:\n  x:\n    steps:\n      " + BEGIN + "\n      - old\n      " + END + "\n      - name: after\n"
    out = rewrite(wf, rel)
    assert out.count(BEGIN) == 1 and "- old" not in out and "- name: after" in out and "sha256sum -c -" in out
    assert rewrite(out, rel) == out, "idempotent"
    assert rewrite("no block here", rel) is None
    print("core-pins: self-test passed")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--write", action="store_true")
    g.add_argument("--check", action="store_true")
    g.add_argument("--print", action="store_true")
    g.add_argument("--self-test", action="store_true")
    a = ap.parse_args(argv)
    if a.self_test:
        return self_test()
    rel = releases()
    if not rel:
        print("core-pins: no */core.lock", file=sys.stderr)
        return 2
    if a.print:
        sys.stdout.write(block(rel, 0))
        return 0
    bad = 0
    for wf in sorted(WORKFLOWS.glob("*.yml")):
        text = wf.read_text(encoding="utf-8")
        new = rewrite(text, rel)
        if new is None or new == text:
            continue
        if a.write:
            wf.write_text(new, encoding="utf-8")
            print(f"core-pins: rewrote {wf.relative_to(ROOT)}")
        else:
            print(f"core-pins: {wf.relative_to(ROOT)} disagrees with the core.lock files: run tools/core-pins.py --write",
                  file=sys.stderr)
            bad = 1
    return bad


if __name__ == "__main__":
    sys.exit(main())
