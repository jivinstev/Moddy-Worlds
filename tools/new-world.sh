#!/usr/bin/env bash
# new-world.sh — scaffold a new world: a folder that builds, tests and claims its worlds on the first try.
#
#   tools/new-world.sh <world> [--name "Moddy Arena"] [--description "…"]
#
# <world> is the folder, the level id and the mod id's suffix (lower-case letters and digits, 2-16 long, starting with
# a letter): `arena` makes arena/ with mod id moddyarena, World Type "Moddy Arena". It writes:
#   <world>/settings.gradle, build.gradle, gradle.properties (level_packages=com/moddingfromamod/<world>), core.lock (a copy of the
#     newest world's pin), datapacks.lock.json with no packs, THIRD_PARTY_NOTICES.txt;
#   src/main/java, all in com.moddingfromamod.levels.<world> (a name the core never uses): the @Mod class
#     (<World>Mod: its Level, LevelPacks, LevelWorldType), a ModdyPlugin that adds its WorldModuleType (claims
#     through LevelClaim) and a WorldModule;
#     META-INF/services listing the plugin;
#   src/level/resources: the world preset (a normal overworld to start from), its World Type tag entry and name,
#     worlds/<world>.json and worlds/rules/<world>.rules.json (no rules yet);
#   src/test/java: a unit test of its Level.
# Nothing is copied from another world's code, so the new one carries none of its specifics. Then: ./gradlew -p <world>
# test jar, and the steps in README.md "Adding a world". Exit 0 written · 2 bad arguments or the folder exists.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
die() { echo "new-world: $*" >&2; exit 2; }
[ $# -ge 1 ] || die "usage: tools/new-world.sh <world> [--name NAME] [--description TEXT]"
WORLD="$1"; shift
NAME=""; DESC=""
while [ $# -gt 0 ]; do
    case "$1" in
        --name) NAME="${2:?--name needs a value}"; shift 2 ;;
        --description) DESC="${2:?--description needs a value}"; shift 2 ;;
        *) die "unknown argument $1" ;;
    esac
done
printf '%s' "$WORLD" | grep -Eq '^[a-z][a-z0-9]{1,15}$' || die "'$WORLD': use 2-16 lower-case letters and digits, starting with a letter"
case "$WORLD" in shared|tools|gradle|build|playtests|levels) die "'$WORLD' is a reserved folder name" ;; esac
[ ! -e "$REPO/$WORLD" ] || die "$REPO/$WORLD already exists"
LOCK="$(ls "$REPO"/*/core.lock 2>/dev/null | head -1)"
[ -n "$LOCK" ] || die "no existing world to copy the core pin from (a */core.lock)"
python3 - "$REPO" "$WORLD" "$NAME" "$DESC" "$LOCK" <<'PY'
import json, pathlib, sys
repo, world, name, desc, lock = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4], pathlib.Path(sys.argv[5])
Cls = world[0].upper() + world[1:]
modid = "moddy" + world
name = name or ("Moddy " + Cls)
desc = desc or (name + ", a Moddy world.")
d = repo / world


def w(rel, text):
    p = d / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def props(path):
    out = {}
    for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


src = props(lock.parent / "gradle.properties")
w("build.gradle", f"""// {name.upper()}, the `{modid}` world jar, beside the Moddy core mod. Everything every world repeats is
// ../shared/level.gradle; this file says only what is this world's own.
plugins {{
    id 'java-library'
    id 'net.neoforged.gradle.userdev' version '7.1.39'
}}

ext.levelId = '{world}'

apply from: '../shared/level.gradle'
""")
w("settings.gradle", f"""// The {name} world jar, `{modid}`: a standalone Gradle build (./gradlew -p {world} jar / test / runClient).
pluginManagement {{
    repositories {{
        gradlePluginPortal()
        maven {{ url = 'https://maven.neoforged.net/releases' }}
        maven {{ url = 'https://libraries.minecraft.net' }}
    }}
}}

plugins {{
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}}

dependencyResolutionManagement {{
    repositories {{
        mavenCentral()
        maven {{ url = 'https://maven.neoforged.net/releases' }}
        maven {{ url = 'https://libraries.minecraft.net' }}
    }}
}}

rootProject.name = '{modid}'
""")
w("gradle.properties", f"""# The {name} world jar. Minecraft and NeoForge are the public core's: they must match the core jar pinned in core.lock.
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.caching=true

minecraft_version={src['minecraft_version']}
minecraft_version_range={src['minecraft_version_range']}
neo_version={src['neo_version']}
java_version={src['java_version']}

mod_id={modid}
mod_name={name}
mod_version=0.1.0
mod_license=MPL-2.0
mod_authors=Moddy contributors
mod_description={desc}
# The core mod it requires, and the version range it accepts.
core_mod_id=moddingfromamod
core_version_range={src['core_version_range']}
# The world's own Java packages besides com.moddingfromamod.levels.<world> (its @Mod class, its code and the shared
# code): tools/check-level-jar.py refuses a class anywhere else in the jar. Keep new code under levels.<world>: the
# core never uses that name, while a package such as com.moddingfromamod.<world> may already be the core's (FML then
# refuses to load the two jars together).
level_packages=
""")
w("core.lock", lock.read_text(encoding="utf-8"))
w("datapacks.lock.json", json.dumps({
    "_comment": "This world's built-in third-party DATAPACKS, pinned by version AND sha1 (see another world's lock "
                "for a row's fields). Read a pack's LICENSE before adding it, and add it to THIRD_PARTY_NOTICES.txt.",
    "v": 1, "packs": {}}, indent=2) + "\n")
w("THIRD_PARTY_NOTICES.txt", f"Third-party content the {name} world jar carries\n\nNone.\n")
w(f"src/main/java/com/moddingfromamod/levels/{world}/{Cls}Mod.java", f"""package com.moddingfromamod.levels.{world};

import com.moddingfromamod.levels.{world}.shared.Level;
import com.moddingfromamod.levels.{world}.shared.LevelPacks;
import com.moddingfromamod.levels.{world}.shared.LevelWorldType;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** <b>{name} as a world jar</b>, loaded beside the core mod. Its hooks are listed in META-INF/services. */
@Mod({Cls}Mod.MODID)
public final class {Cls}Mod {{

    public static final String MODID = "{modid}";

    /** The world as the shared code sees it. Name its third-party pack folders here (datapacks.lock.json). */
    public static final Level LEVEL = new Level(MODID, "{world}", "{name}", List.of());

    public {Cls}Mod(IEventBus modBus) {{
        LevelPacks.register(modBus, LEVEL);
        if (FMLEnvironment.dist.isClient()) {{
            LevelWorldType.register(LEVEL);
        }}
    }}
}}
""")
w(f"src/main/java/com/moddingfromamod/levels/{world}/{Cls}Plugin.java", f"""package com.moddingfromamod.levels.{world};

import com.moddingfromamod.levels.{world}.shared.LevelClaim;
import com.moddingfromamod.plugin.ModdyPlugin;
import com.moddingfromamod.plugin.Plugins;
import com.moddingfromamod.world.WorldModule;
import com.moddingfromamod.world.WorldModuleType;
import net.minecraft.server.MinecraftServer;

/** {name}, plugged into the core: its world module, which runs in the worlds made as {name}. */
public final class {Cls}Plugin implements ModdyPlugin {{

    @Override
    public String name() {{
        return "{world}";
    }}

    @Override
    public void register(Plugins plugins) {{
        plugins.add(WorldModuleType.class, new WorldModuleType() {{
            @Override
            public String id() {{
                return {Cls}Module.ID;
            }}

            @Override
            public WorldModule create() {{
                return new {Cls}Module();
            }}

            /** A world made with World Type "{name}" (its marker pack on, or its saved data says so). */
            @Override
            public boolean claims(MinecraftServer server) {{
                return LevelClaim.claims(server, {Cls}Mod.LEVEL);
            }}
        }});
    }}
}}
""")
w(f"src/main/java/com/moddingfromamod/levels/{world}/{Cls}Module.java", f"""package com.moddingfromamod.levels.{world};

import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.WorldModule;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {name}'s world module: its state, its players' arrivals, and (later) its bases and its wish charge. */
public final class {Cls}Module implements WorldModule {{

    public static final String ID = "{world}";

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/{world}");

    @Override
    public String id() {{
        return ID;
    }}

    @Override
    public Bases start(MinecraftServer server) {{
        LOG.info("{name}: world module started");
        return null;                 // no bases yet
    }}
}}
""")
w("src/main/resources/META-INF/services/com.moddingfromamod.plugin.ModdyPlugin", f"com.moddingfromamod.levels.{world}.{Cls}Plugin\n")
w(f"src/level/resources/data/{modid}/worldgen/world_preset/{world}.json", json.dumps({"dimensions": {
    "minecraft:overworld": {"type": "minecraft:overworld", "generator": {"type": "minecraft:noise",
        "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:overworld"}, "settings": "minecraft:overworld"}},
    "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {"type": "minecraft:noise",
        "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}, "settings": "minecraft:nether"}},
    "minecraft:the_end": {"type": "minecraft:the_end", "generator": {"type": "minecraft:noise",
        "biome_source": {"type": "minecraft:the_end"}, "settings": "minecraft:end"}}}}, indent=2) + "\n")
w("src/level/resources/data/minecraft/tags/worldgen/world_preset/normal.json",
  json.dumps({"replace": False, "values": [f"{modid}:{world}"]}, indent=2) + "\n")
w(f"src/level/resources/assets/{modid}/lang/en_us.json", json.dumps({
    f"generator.{modid}.{world}": name, f"generator.{modid}.{world}.info": desc}, indent=2) + "\n")
w(f"src/level/resources/worlds/{world}.json", json.dumps({
    "v": 1, "id": world, "name": name, "module": world,
    "_notes": "The world's manifest, as its jar ships it: gameplay only (no hostnames, ports, machines or briefs)."},
    indent=2) + "\n")
w(f"src/level/resources/worlds/rules/{world}.rules.json", json.dumps({
    "store": "world-rules", "v": 1, "world": world, "rules": [],
    "_notes": "What this world denies and allows; schema: the core's WorldRules.parse."}, indent=2) + "\n")
w(f"src/test/java/com/moddingfromamod/levels/{world}/{Cls}LevelTest.java", f"""package com.moddingfromamod.levels.{world};

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {name}'s worlds are claimed by its own marker pack, by NeoForge's id for it. */
class {Cls}LevelTest {{

    @Test
    void itsMarkerClaimsItsWorlds() {{
        assertEquals("mod/{modid}:datapacks/level_marker", {Cls}Mod.LEVEL.markPackId());
        assertTrue({Cls}Mod.LEVEL.marks(List.of("vanilla", "mod/{modid}:datapacks/level_marker")));
        assertEquals({Cls}Module.ID, {Cls}Mod.LEVEL.id());
    }}
}}
""")
print(f"new-world: wrote {world}/ (mod id {modid}, World Type \"{name}\"). Next: ./gradlew -p {world} test jar, then "
      "README.md \"Adding a world\" (its table row).")
PY
