#!/usr/bin/env python3
"""check-level-jar.py — a level jar holds its level and nothing else.

    python3 tools/check-level-jar.py isle/build/libs/moddyisle-0.1.0+mc1.21.8.jar [--level isle]   # the world's folder
    python3 tools/check-level-jar.py --self-test

A level jar is loaded BESIDE the core mod, so FML refuses it outright if it carries a package the core carries (a
split package), and the store reviews what it ships. This checks, entry by entry, that the jar holds only:

  * classes in the level's own packages: com/moddingfromamod/levels/<level>/ (its @Mod class and its copy of the
    shared code) and the level_packages its gradle.properties lists; no GameTest or test class;
  * its metadata: META-INF/MANIFEST.MF, META-INF/neoforge.mods.toml, META-INF/accesstransformer.cfg, its one
    META-INF/services/com.moddingfromamod.plugin.ModdyPlugin entry naming only its own classes;
  * its resources: assets/<modid>/, data/<modid>/, data/moddingfromamod/structure/<level>/,
    data/minecraft/tags/worldgen/world_preset/, worlds/<level>.json and worlds/rules/<level>.rules.json;
  * its built-in packs under datapacks/<folder>/ for exactly the folders datapacks.lock.json names and the generated
    marker pack (datapacks/level_marker/), plus
    THIRD_PARTY_NOTICES.txt and this repository's licence (LICENSE_<modid>);
  * no nested jar (META-INF/jarjar/), no secret-shaped or private file name (an .env, a key, a store file);
  * no package the pinned core jar also carries (FML refuses a split package), checked when the core jar is in
    <level>/.core/ (tools/fetch-core.sh puts it there; CI and the release do).

Exit 0 clean, 1 findings (each named), 2 the jar or the lock could not be read.
"""
import argparse
import json
import pathlib
import re
import sys
import tempfile
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent

def level_config(level, root=ROOT):
    """A world's mod id, packages and pack lock, read from its own folder (gradle.properties, datapacks.lock.json)."""
    d = pathlib.Path(root) / level
    props = {}
    for line in (d / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    pkgs = [f"com/moddingfromamod/levels/{level}/"]
    pkgs += [p.strip().strip("/") + "/" for p in props.get("level_packages", "").split(",") if p.strip()]
    return {"modid": props["mod_id"], "packages": pkgs, "lock": d / "datapacks.lock.json"}


FORBIDDEN_NAMES = re.compile(r"(^|/)(\.env[^/]*|[^/]*\.(pem|key|p12|jks)|[^/]*(secret|credential|forbidden)[^/]*"
                             r"|[^/]*real-wishes[^/]*|[^/]*agent-actions[^/]*)$", re.I)


def allowed(name, level, cfg, folders):
    modid = cfg["modid"]
    if name.endswith("/"):
        return None                                         # a directory entry: judged by what it holds
    if name.endswith(".class"):
        if not any(name.startswith(p) for p in cfg["packages"]):
            return "a class outside the level's packages"
        base = name.rsplit("/", 1)[-1]
        if re.search(r"(GameTest|Test)(\$.*)?\.class$", base):
            return "a test class"
        return None
    fixed = {"META-INF/MANIFEST.MF", "META-INF/neoforge.mods.toml", "META-INF/accesstransformer.cfg",
             "META-INF/services/com.moddingfromamod.plugin.ModdyPlugin", "THIRD_PARTY_NOTICES.txt",
             f"LICENSE_{modid}", f"worlds/{level}.json", f"worlds/rules/{level}.rules.json"}
    if name in fixed:
        return None
    prefixes = [f"assets/{modid}/", f"data/{modid}/", f"data/moddingfromamod/structure/{level}/",
                "data/minecraft/tags/worldgen/world_preset/"] + [f"datapacks/{f}/" for f in folders]
    if any(name.startswith(p) for p in prefixes):
        return None
    if name.startswith("datapacks/"):
        return "a datapack folder datapacks.lock.json does not name"
    if name.startswith("META-INF/jarjar/"):
        return "a nested jar (a level bundles nothing; the core carries the libraries)"
    return "a file outside the level's resources"


def check(jar, level, cfg=None, core=None):
    cfg = level_config(level) if cfg is None else cfg
    packs = json.loads(pathlib.Path(cfg["lock"]).read_text())["packs"]
    folders = sorted(row["folder"] for row in packs.values()) + ["level_marker"]   # the marker every level carries
    findings = []
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        for n in names:
            why = allowed(n, level, cfg, folders)
            if why:
                findings.append(f"{n}: {why}")
            if FORBIDDEN_NAMES.search(n):
                findings.append(f"{n}: a secret-shaped or private file name")
        for need in ("META-INF/neoforge.mods.toml", "META-INF/services/com.moddingfromamod.plugin.ModdyPlugin",
                     f"worlds/{level}.json", f"worlds/rules/{level}.rules.json", "THIRD_PARTY_NOTICES.txt"):
            if need not in names:
                findings.append(f"{need}: missing")
        for f in folders:
            if not any(n.startswith(f"datapacks/{f}/") for n in names):
                findings.append(f"datapacks/{f}/: missing (datapacks.lock.json names it, or it is the marker)")
        if "META-INF/services/com.moddingfromamod.plugin.ModdyPlugin" in names:
            for line in z.read("META-INF/services/com.moddingfromamod.plugin.ModdyPlugin").decode().splitlines():
                cls = line.split("#", 1)[0].strip()
                if cls and not any(cls.replace(".", "/").startswith(p) for p in cfg["packages"]):
                    findings.append(f"META-INF/services/…ModdyPlugin: {cls} is not one of the level's classes")
        if "META-INF/neoforge.mods.toml" in names:
            toml = z.read("META-INF/neoforge.mods.toml").decode()
            if f'modId="{cfg["modid"]}"' not in toml:
                findings.append(f"META-INF/neoforge.mods.toml: does not declare modId {cfg['modid']}")
            if "${" in toml:
                findings.append("META-INF/neoforge.mods.toml: an unexpanded ${...} placeholder")
    core = core_jar(level) if core is None else core
    if core is not None:
        clash = sorted(packages_of(jar) & packages_of(core))
        for pkg in clash:
            findings.append(f"{pkg}/: a package the core jar ({pathlib.Path(core).name}) also carries: FML refuses to "
                            f"load the two together (a split package); move it under com/moddingfromamod/levels/{level}/")
    return findings


def packages_of(jar):
    """Every package (directory) that holds a class in a jar."""
    with zipfile.ZipFile(jar) as z:
        return {n.rsplit("/", 1)[0] for n in z.namelist() if n.endswith(".class") and "/" in n}


def core_jar(level, root=ROOT):
    """The pinned core jar fetched into <level>/.core/ (tools/fetch-core.sh), or None when it is not here."""
    d = pathlib.Path(root) / level
    try:
        props = dict(l.split("=", 1) for l in (d / "core.lock").read_text(encoding="utf-8").splitlines()
                     if "=" in l and not l.startswith("#"))
    except OSError:
        return None
    jar = d / ".core" / props.get("core_asset", "").strip()
    return jar if jar.is_file() else None


def self_test():
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        lock = d / "lock.json"
        lock.write_text(json.dumps({"v": 1, "packs": {"a": {"folder": "pack_a"}}}))
        cfg = {"modid": "moddyisle", "packages": ["com/moddingfromamod/levels/isle/", "com/moddingfromamod/isle/",
                                                  "com/moddingfromamod/bank/"], "lock": lock}
        good = {"META-INF/MANIFEST.MF": "", "META-INF/neoforge.mods.toml": 'modId="moddyisle"',
                "META-INF/services/com.moddingfromamod.plugin.ModdyPlugin": "com.moddingfromamod.isle.IslePlugin\n",
                "worlds/isle.json": "{}", "worlds/rules/isle.rules.json": "{}", "THIRD_PARTY_NOTICES.txt": "",
                "com/moddingfromamod/isle/IslePlugin.class": "", "datapacks/pack_a/pack.mcmeta": "",
                "datapacks/level_marker/pack.mcmeta": "",
                "data/moddingfromamod/structure/isle/starter_island.nbt": ""}
        bad_extra = {"com/moddingfromamod/wish/Wish.class": "a class outside",
                     "com/moddingfromamod/isle/IslandGameTest.class": "a test class",
                     "datapacks/other/pack.mcmeta": "a datapack folder",
                     "META-INF/jarjar/x.jar": "a nested jar",
                     "data/moddingfromamod/lessons/x.md": "a file outside",
                     "worlds/.env": "secret-shaped"}

        def jar(entries, name):
            p = d / name
            with zipfile.ZipFile(p, "w") as z:
                for k, v in entries.items():
                    z.writestr(k, v)
            return p

        assert check(jar(good, "good.jar"), "isle", cfg) == [], check(jar(good, "good.jar"), "isle", cfg)
        for k, want in bad_extra.items():
            got = check(jar({**good, k: ""}, "bad.jar"), "isle", cfg)
            assert any(k in f and want in f for f in got), (k, got)
        missing = dict(good)
        del missing["datapacks/pack_a/pack.mcmeta"]
        assert any("pack_a" in f and "missing" in f for f in check(jar(missing, "m.jar"), "isle", cfg))
        nomark = dict(good)
        del nomark["datapacks/level_marker/pack.mcmeta"]
        assert any("level_marker" in f and "missing" in f for f in check(jar(nomark, "n.jar"), "isle", cfg))
        core = jar({"com/moddingfromamod/wish/Wish.class": "", "com/moddingfromamod/isle/Other.class": ""}, "core.jar")
        got = check(jar(good, "c.jar"), "isle", cfg, core=core)
        assert any("com/moddingfromamod/isle/" in f and "split package" in f for f in got), got
        assert check(jar(good, "c2.jar"), "isle", cfg, core=jar({"com/moddingfromamod/wish/W.class": ""}, "k.jar")) == []
    print("check-level-jar: self-test passed")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("jar", nargs="?")
    ap.add_argument("--level", default="isle", help="the world's folder (default isle)")
    ap.add_argument("--self-test", action="store_true")
    a = ap.parse_args(argv)
    if a.self_test:
        return self_test()
    if not a.jar:
        ap.error("name a jar (or --self-test)")
    try:
        findings = check(a.jar, a.level)
    except (OSError, zipfile.BadZipFile, KeyError, ValueError) as e:
        print(f"check-level-jar: cannot read {a.jar}: {e}", file=sys.stderr)
        return 2
    for f in findings:
        print(f"check-level-jar: {f}")
    print(f"check-level-jar: {a.jar}: {len(findings)} finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
