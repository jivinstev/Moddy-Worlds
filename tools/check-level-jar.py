#!/usr/bin/env python3
"""check-level-jar.py — a level jar holds its level and nothing else.

    python3 tools/check-level-jar.py isle/build/libs/moddyisle-0.1.0+mc1.21.8.jar [--level isle]   # the world's folder
        [--core CORE.jar]          # the core jar it was built against (default: <level>/.core/<the pinned asset>)
        [--allow-unpinned]         # the build's own check (shared/level.gradle) of an unpinned build: say it, go on
    python3 tools/check-level-jar.py --self-test

Every world's `build` (and `assemble`) runs this on the jar it made (shared/level.gradle, task checkLevelJar), so CI,
Port CI and a local build all check it.

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
  * no package the pinned core jar also carries (FML refuses a split package), checked against --core (the build
    passes the core jar it compiled against) or else the core jar in <level>/.core/ when it is there;
  * NO MARK OF AN UNPINNED CORE: a jar built with -PcoreJar or on Port CI's dependent gate (PORT_CI_DEP_OVERRIDE naming
    the core) carries the manifest attribute Moddy-Unpinned-Core (shared/level.gradle), and is refused here, so it is
    never released. --allow-unpinned (only the build's own check of such a build passes it) prints it and goes on;
  * NOTHING A STOCK CLIENT CANNOT DRAW (the vanilla-client principle, README "A stock client is all a player needs"):
    no client-only code but the one host-side piece every world shares (the World Type picker that ticks its packs,
    <level>/shared/LevelWorldType, and the @Mod class's Dist check that registers it); no custom network channel or
    payload; no registered block, item, entity type, menu or other registry entry (DeferredRegister, RegisterEvent);
    and no client resource under assets/<modid>/ but its lang/ (the World Type's name on the host's Create World
    screen). Each finding names the class or file and what it referenced.

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


# ── the vanilla-client principle ─────────────────────────────────────────────────────────────────────────────────────
# What a class may not reference (raw names in its constant pool), and the small list of classes allowed to.
CLIENT_REFS = (b"net/minecraft/client/", b"net/neoforged/neoforge/client/", b"com/mojang/blaze3d/",
               b"net/neoforged/api/distmarker/OnlyIn")
DIST_REF = b"net/neoforged/api/distmarker/Dist"
NETWORK_REFS = (b"net/neoforged/neoforge/network/", b"CustomPacketPayload",
                b"net/minecraft/network/protocol/common/custom/")
REGISTRY_REFS = (b"net/neoforged/neoforge/registries/DeferredRegister", b"net/neoforged/neoforge/registries/RegisterEvent",
                 b"net/neoforged/neoforge/registries/DeferredHolder", b"net/minecraft/world/inventory/MenuType",
                 b"net/neoforged/neoforge/registries/NewRegistryEvent")
MOD_ANNOTATION = b"Lnet/neoforged/fml/common/Mod;"


def host_side_class(name, level):
    """The one client piece every world may carry: the shared World Type picker (and its inner classes)."""
    base = f"com/moddingfromamod/levels/{level}/shared/LevelWorldType"
    return name == base + ".class" or name.startswith(base + "$")


def vanilla_client_findings(z, names, level, modid):
    """What in a world jar a stock client could not draw, or that only a client-side mod could use; each named."""
    out = []
    for n in names:
        if n.startswith(f"assets/{modid}/") and not n.endswith("/") and not n.startswith(f"assets/{modid}/lang/"):
            out.append(f"{n}: a client resource (a stock client never gets a mod's assets; only lang/, for the "
                       f"host's World Type name, is allowed)")
        if not n.endswith(".class"):
            continue
        b = z.read(n)
        host = host_side_class(n, level)
        for ref in CLIENT_REFS:
            if ref in b and not host:
                out.append(f"{n}: references client-only code ({ref.decode()}); only the shared World Type picker"
                           f" ({level}/shared/LevelWorldType) may")
        if DIST_REF in b and not host and MOD_ANNOTATION not in b:
            out.append(f"{n}: checks the client side (Dist); only the @Mod class may, to register the World Type"
                       f" picker")
        for ref in NETWORK_REFS:
            if ref in b:
                out.append(f"{n}: a custom network channel or payload ({ref.decode()}): a stock client has none")
        for ref in REGISTRY_REFS:
            if ref in b:
                out.append(f"{n}: registers content ({ref.decode()}): a custom block, item, entity or menu is"
                           f" something a stock client cannot draw")
    return out


UNPINNED = "Moddy-Unpinned-Core"


def unpinned_mark(z):
    """The jar's Moddy-Unpinned-Core manifest attribute (why it was not built against the pinned core), or None."""
    try:
        mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
    except KeyError:
        return None
    lines, cur = [], None
    for raw in mf.replace("\r\n", "\n").split("\n"):      # a manifest line longer than 72 bytes continues after " "
        if raw.startswith(" ") and cur is not None:
            lines[-1] += raw[1:]
        else:
            lines.append(raw)
            cur = raw
    for line in lines:
        k, _, v = line.partition(":")
        if k.strip().lower() == UNPINNED.lower():
            return v.strip() or "(no reason given)"
    return None


def check(jar, level, cfg=None, core=None, allow_unpinned=False):
    cfg = level_config(level) if cfg is None else cfg
    packs = json.loads(pathlib.Path(cfg["lock"]).read_text())["packs"]
    folders = sorted(row["folder"] for row in packs.values()) + ["level_marker"]   # the marker every level carries
    findings = []
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        mark = unpinned_mark(z)
        if mark and allow_unpinned:
            print(f"check-level-jar: WARNING: built against an unpinned core ({mark}): this jar is marked "
                  f"{UNPINNED} and is never released")
        elif mark:
            findings.append(f"META-INF/MANIFEST.MF: {UNPINNED}: {mark}: built against a core that is not the pinned one "
                            f"(-PcoreJar or Port CI's dependent gate); never released: build it again against core.lock")
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
        findings += vanilla_client_findings(z, names, level, cfg["modid"])
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
                "data/moddingfromamod/structure/isle/starter_island.nbt": "",
                "assets/moddyisle/lang/en_us.json": "{}",
                "com/moddingfromamod/levels/isle/IsleMod.class": "Lnet/neoforged/fml/common/Mod;"
                                                                 "net/neoforged/api/distmarker/Dist",
                "com/moddingfromamod/levels/isle/shared/LevelWorldType.class":
                    "net/minecraft/client/gui/screens/worldselection/CreateWorldScreen",
                "com/moddingfromamod/levels/isle/shared/LevelWorldType$1.class": "net/neoforged/neoforge/client/event"}
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
        # the vanilla-client principle: each of these trips it, by name
        client = {"com/moddingfromamod/isle/Hud.class": ("net/minecraft/client/gui/Gui", "client-only code"),
                  "com/moddingfromamod/isle/Side.class": ("net/neoforged/api/distmarker/Dist", "checks the client"),
                  "com/moddingfromamod/isle/Net.class": ("net/neoforged/neoforge/network/event/Register",
                                                         "network channel"),
                  "com/moddingfromamod/isle/Blocks.class": ("net/neoforged/neoforge/registries/DeferredRegister",
                                                            "registers content"),
                  "com/moddingfromamod/levels/isle/shared/Level.class": ("com/mojang/blaze3d/systems/RenderSystem",
                                                                         "client-only code")}
        for k, (body, want) in client.items():
            got = check(jar({**good, k: body}, "cl.jar"), "isle", cfg)
            assert any(k in f and want in f for f in got), (k, got)
        got = check(jar({**good, "assets/moddyisle/textures/item/x.png": ""}, "tx.jar"), "isle", cfg)
        assert any("textures" in f and "client resource" in f for f in got), got
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
        # the mark of an unpinned core (shared/level.gradle): refused, unless the build's own check allows it
        for mf in ("Manifest-Version: 1.0\r\nModdy-Unpinned-Core: PORT_CI_DEP_OVERRIDE=com.moddingfromamod:moddingfromamo\r\n"
                   " d\r\n\r\n", "Manifest-Version: 1.0\nModdy-Unpinned-Core: -PcoreJar=core.jar\n"):
            marked = jar({**good, "META-INF/MANIFEST.MF": mf}, "u.jar")
            got = check(marked, "isle", cfg)
            assert len(got) == 1 and "Moddy-Unpinned-Core" in got[0] and "never released" in got[0], got
            assert check(marked, "isle", cfg, allow_unpinned=True) == []
            assert main([str(marked), "--level", "isle"], cfg=cfg) == 1
        z = zipfile.ZipFile(jar({**good, "META-INF/MANIFEST.MF": "Manifest-Version: 1.0\r\nModdy-Unpinned-Core: "
                                 "PORT_CI_DEP_OVERRIDE=com.moddingfromamod:moddingfromamo\r\n d\r\n"}, "w.jar"))
        assert unpinned_mark(z) == "PORT_CI_DEP_OVERRIDE=com.moddingfromamod:moddingfromamod", unpinned_mark(z)
        assert main([str(jar(good, "ok.jar")), "--level", "isle"], cfg=cfg) == 0
    print("check-level-jar: self-test passed")
    return 0


def main(argv=None, cfg=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("jar", nargs="?")
    ap.add_argument("--level", default="isle", help="the world's folder (default isle)")
    ap.add_argument("--core", help="the core jar the world was built against (default: <level>/.core/<pinned asset>, "
                                   "when it is there)")
    ap.add_argument("--allow-unpinned", action="store_true",
                    help="only for the build's own check of a build that is itself unpinned (shared/level.gradle): "
                         "report the Moddy-Unpinned-Core mark instead of refusing it")
    ap.add_argument("--self-test", action="store_true")
    a = ap.parse_args(argv)
    if a.self_test:
        return self_test()
    if not a.jar:
        ap.error("name a jar (or --self-test)")
    try:
        if a.core and not pathlib.Path(a.core).is_file():
            raise OSError(f"--core {a.core}: no such file")
        findings = check(a.jar, a.level, cfg=cfg, core=a.core, allow_unpinned=a.allow_unpinned)
    except (OSError, zipfile.BadZipFile, KeyError, ValueError) as e:
        print(f"check-level-jar: cannot read {a.jar}: {e}", file=sys.stderr)
        return 2
    for f in findings:
        print(f"check-level-jar: {f}")
    print(f"check-level-jar: {a.jar}: {len(findings)} finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
