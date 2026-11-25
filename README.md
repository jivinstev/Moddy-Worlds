# Moddy worlds

The **worlds** of [Moddy the Wish Watch](https://github.com/jivinstev/ModdyTheWishWatch): each one a world with its
own rules and its own game, shipped as its own mod (jar) that runs on the public Moddy core. A player installs the
core and the world's jar; nothing else.

## The worlds

Each world has its own page, for players first: how to install it, make the world and play it.

| world | read this | mod id | what it is |
|---|---|---|---|
| Scarcity Isle | **[isle/README.md](isle/README.md)** | `moddyisle` | a tiny island in the sky for every player, and a wish bank where every wish counts |
| Moddy Arena | **[arena/README.md](arena/README.md)** | `moddyarena` | a lit prep room beside a walled arena, on the ground: pick the difficulty (it sets how many monsters come) and the number of waves (5 to 20, or Endless), then fight waves of monsters, with a boss every 5th wave, behind a gate that shuts while a wave is on, with the wishes on your hotbar (5 fires a wave) and 3 lives, a break between waves to change them, and a Champions board of the best runs |

Minecraft 1.21.8, NeoForge 21.8.57, Java 21. Licence: [MPL-2.0](LICENSE). Third-party datapacks keep their own
licences (each world's `THIRD_PARTY_NOTICES.txt`).

## Layout

```
<world>/                 one folder per world, a standalone Gradle build (./gradlew -p <world> ...)
  build.gradle           a few lines: its level id, anything it adds, then `apply from: '../shared/level.gradle'`
  gradle.properties      its mod id, name, versions, the core version range, its own Java packages
  core.lock              the public core jar it builds and plays against, pinned by version and sha256
  datapacks.lock.json    its built-in third-party packs, pinned by sha1 (with THIRD_PARTY_NOTICES.txt)
  src/main/java          its code: its @Mod class in com.moddingfromamod.levels.<world>, its own packages
  src/main/resources     its plug-in list (META-INF/services/...ModdyPlugin) and its structures
  src/level/resources    its world preset, World Type name, worlds/<world>.json and worlds/rules/<world>.rules.json
  src/test/java          its unit tests
  playtests/             its real-client playtests (tools/play.sh), and play.props
playtests/               real-client playtests across SEVERAL worlds in one game (tools/play.sh isle,arena)
shared/                  written once, compiled into EVERY world's jar (never a jar of its own)
  level.gradle           the build every world shares
  src/main/java          the shared level code: Level, LevelPacks, LevelWorldType, LevelClaim
  resources/META-INF     neoforge.mods.toml (templated) and the World Type listener's access transformer
tools/                   new-world.sh (the scaffold), play.sh, fetch-core.sh, core-pins.py, check-level-jar.py (incl. the stock-client rule),
                         changed-worlds.py, the leak and secret scans and the git hooks
```

The shared Java code is written in `com.moddingfromamod.levels.shared` and each world's build compiles a copy into
**`com.moddingfromamod.levels.<world>.shared`**, so two world jars in one game never carry the same package (FML
refuses a split package).

## Play a world

**By hand:** put the Moddy core jar and the world's jar in a NeoForge 1.21.8 instance (the world's
[release notes](../../releases) name the core release it was built against), then **Create World → World tab →
World Type** (for the Isle: **Moddy Isle**; for the arena: **Moddy Arena**, then choose your game mode, and play the
arena in Survival). That is all: the world switches itself on in that world and nowhere else.

**A dedicated server** selects it the vanilla way in `server.properties`, read only when the world is first created.
For the Isle:

```properties
level-type=moddyisle\:isle
initial-enabled-packs=vanilla,mod/moddyisle\:datapacks/standard_skyblock,mod/moddyisle\:datapacks/sky_void_additions,mod/moddyisle\:datapacks/skyblock_advancements,mod/moddyisle\:datapacks/level_marker
```

Every world's recipe ends with its own `mod/<modid>:datapacks/level_marker`: that pack is what makes the world its.

**Scripted:** `tools/play.sh <world>` installs the newest `<world>-*` pre-release of this repository (or `--build`
this checkout, or `--jars DIR`) beside the pinned core in a throwaway instance, with
[MMVU](https://github.com/jivinstev/mc-mod-version-upgrade)'s `tools/real-install.py`, and runs `<world>/playtests/`
in a real client (macOS, or headless Linux under Xvfb). `tools/play.sh isle,arena --build` installs several worlds in
one instance and runs the root `playtests/` instead (worlds side by side: each keeps its own mode). See
`tools/play.sh --help`.

## Build

```bash
./gradlew -p isle jar          # isle/build/libs/moddyisle-<version>+mc1.21.8.jar
./gradlew -p isle test         # its unit tests, and the shared code's
./gradlew -p isle runClient    # a dev client with the core and the Isle (-Plevel=on switches it on by properties)
```

A world compiles against the **public core jar only**: the release asset its `core.lock` pins by version and
**sha256** (the workflows' fetch step is generated from it: "The core pin in the workflows" below). The build downloads it into `<world>/.core/` and refuses any other bytes. While the core's repository is
private, fetch it first with a token that can read it: `GH_TOKEN=… tools/fetch-core.sh isle`. `-PcoreJar=<jar>`
builds against another core jar (a local build of the core); such a build is never released.

## A stock client is all a player needs

**Every world is played with a vanilla Minecraft client.** A player who joins a world (on a server, or a friend's
LAN game) installs nothing: everything he sees and hears is something a stock client already draws: blocks,
vanilla mobs (made tougher by their attributes, named by a custom name), text and item displays, boss bars, titles,
chat, sounds, player heads. So a world jar:

- carries **no client-side code** but the one host-side piece every world shares: the World Type picker that ticks
  its packs on the HOST's Create World screen (`shared/`'s `LevelWorldType`, compiled into the world's own
  `<world>/shared` package), registered from the `@Mod` class behind its one `Dist` check;
- opens **no network channel** and sends no custom payload;
- **registers no content**: no new block, item, entity type, menu or other registry entry (a stock client could not
  draw it); it builds from vanilla blocks and mobs instead;
- ships **no client resources** but `assets/<modid>/lang/` (the World Type's name on the host's screen).

`tools/check-level-jar.py` refuses a jar that breaks this, naming the class or file and what it referenced (CI and the
release run it on every world). If a world needs something a stock client cannot draw, it is a change to this
principle, discussed first, not a quiet exception.

## How a world jar switches itself on

A world jar = a **world type** + **built-in packs** + a **per-world switch-on**, with nothing pre-selected. The
shared code does the parts every world repeats; a world supplies its `Level` (mod id, level id, World Type name, and
its third-party packs, possibly none).

**One way to claim a world, for every world**: each jar also carries a tiny empty built-in pack,
`datapacks/level_marker/`, generated by `shared/level.gradle`. The World Type ticks it with the world's other packs (a
dedicated server names it in `initial-enabled-packs`), and its presence in a world's data configuration is what makes
the world the jar's. A world with no third-party packs claims exactly as one with several: there is no second path.

1. **World type.** A data-driven world preset (`src/level/resources/data/<modid>/worldgen/world_preset/<world>.json`),
   listed in `data/minecraft/tags/worldgen/world_preset/normal.json`, so it appears on Create World's **World Type**
   button, named by `generator.<modid>.<world>` in `assets/<modid>/lang/en_us.json`.
2. **Built-in packs, off by default** (`LevelPacks`). The world's data packs ship inside its jar
   (`datapacks/<folder>/`, downloaded and sha1-checked from `datapacks.lock.json` by the build, plus the marker) and
   are offered on
   `AddPackFindersEvent` with a `PackSource` that never adds them to a world by itself. Their ids are
   `mod/<modid>:datapacks/<folder>`.
3. **The World Type choice ticks the packs** (`LevelWorldType`, client only). It ticks the world's packs when its type
   is picked and unticks them when another is, through the Create World screen's own Data Packs path, which the shared
   `META-INF/accesstransformer.cfg` opens (two members, nothing else).
4. **Per-world switch-on** (`LevelClaim`). The world's `WorldModuleType.claims(server)` says whether this world is one
   of its own: its marker pack in the world's data configuration, or the record it wrote on first start
   (`data/<modid>_level.dat`). A claimed world runs the world's module and its charge; any other world is untouched.

The rules a world jar keeps:

- **It reaches the core through hooks, never by being called.** Its `ModdyPlugin`s are listed in its own
  `META-INF/services/com.moddingfromamod.plugin.ModdyPlugin`; the core's `Plugins.load` finds them with
  `ServiceLoader`. The hooks are the core's documented extension points (`docs/EXTENDING.md` in the core). A hook the
  world needs and the core lacks is added to the core, not faked here.
- **No package in two jars.** Put a new world's code under `com.moddingfromamod.levels.<world>` (its `@Mod` class and
  its copy of the shared code live there too): the core never uses that name, while a package like
  `com.moddingfromamod.<world>` may already be the core's (`com.moddingfromamod.example` is). Any other package of a
  world is listed as `level_packages` in its gradle.properties (the Isle's `isle` and `bank`).
  `tools/check-level-jar.py` refuses a class outside them, and a package the pinned core jar also carries, on every CI
  run and every release.
- **`@EventBusSubscriber` with no `modid`**, so FML binds it to the jar it ships in.
- **One world jar per server.** `WishCharge` is read with `first`: two world jars that both fill it would race.
- **`shared/` compiles against every world's pinned core, so against the OLDEST one.** A change to `shared/` builds
  every world (CI), which catches a break. A shared change that needs a newer core bumps every world's `core.lock` in
  the same PR, or guards itself so it still compiles and behaves on the oldest pin.
- **GameTests stay with the core** (they need its test harness, which no shipped jar carries); a world ships unit
  tests and playtests.

## Adding a world

1. `tools/new-world.sh <world> --name "Moddy <World>"` writes a world that builds, tests and claims its worlds on the
   first try: `settings.gradle`, `build.gradle`, `gradle.properties`, `core.lock` (a copy of an existing world's pin),
   an empty `datapacks.lock.json` and notices file, the `@Mod` class with its `Level`, a `ModdyPlugin` whose world module
   claims through `LevelClaim`, the world preset (a normal overworld to start from), its World Type tag entry and name,
   `worlds/<world>.json`, an empty rules file, a unit test and `playtests/README.md`. Nothing is copied from another
   world's code.
2. `./gradlew -p <world> test jar`, then `python3 tools/check-level-jar.py <world>/build/libs/*.jar --level <world>`.
   Boot it: put `level-type=<modid>\:<world>` and `initial-enabled-packs=vanilla,mod/<modid>\:datapacks/level_marker`
   in `run/server/server.properties` and run `./gradlew -p <world> runServer`; look for `plugin loaded: <world>`,
   `this world was made as …` and `world module <world> started`.
3. Make it yours: its preset, its packs (read each pack's LICENSE first, add a `datapacks.lock.json` row with every
   field, name the folder in its `Level`, and rewrite `THIRD_PARTY_NOTICES.txt`), its rules, its module. World files
   carry gameplay only: no hostnames, ports, machines, cameras, spend caps or agent briefs (the leak scan refuses those
   keys).
4. Add a row to the table above. CI picks the new folder up by itself, and so does the release (both find every folder
   with a `core.lock`, `tools/changed-worlds.py`).
5. Write `<world>/playtests/*.playtest` (and `play.props` if it needs launcher properties) and run
   `tools/play.sh <world> --build`; a playtest across worlds goes in the root `playtests/`.

## Keeping this repository free of private work

This repository is public (or will be), and the private side of Moddy must never reach it: no hosted-server settings,
no operations tooling, no player ids or players' words, no credentials. Every check runs the same rules:

- **git hooks**: run `./tools/git-hooks/install.sh` once per clone. `pre-commit` runs the secret scan and the leak scan
  over what you stage; `pre-push` scans every outgoing commit (so something added and deleted again is still
  refused). A maintainer adds the private forbidden list, kept OUTSIDE the repository:
  `./tools/git-hooks/install.sh --forbidden ~/.moddy/forbidden.txt`.
- **CI** (`.github/workflows/ci.yml`, on pull requests to `main` and by hand): the leak scan over the tree and every
  commit, the secret scan over the tree and the history, and each changed world's build, unit tests and jar check.
- **The rules**: `tools/leak-scan.py`'s built-in shapes (emails, routable IPs, bucket URLs, every credential shape in
  `tools/secret_patterns.py`, children's-data file names, the private side's packages), plus
  [`tools/leak-rules.txt`](tools/leak-rules.txt) (committed, shapes only), plus the private list (never committed;
  CI reads it from the `FORBIDDEN_LIST` secret). A finding names the file, line and rule, never what matched.

Never bypass a refusal with `--no-verify`. A real credential that reached any commit is rotated first.

## Releases

Only the **Release** workflow makes a release (Actions → Release → Run workflow, from `main`, typing the world's folder,
which it checks against the same discovery CI uses): it
builds that world against its pinned core, runs its unit tests, checks the jar holds only the world's own packages,
resources and packs, scans its contents, and publishes the jar with `SHA256SUMS` as a pre-release tagged
`<world>-<version>-mc<minecraft>` whose notes name the core release and sha256 to install beside it.

## The core pin in the workflows, and MMVU

MMVU's `real-install` finds the jars a mod needs beside it by reading the repository's workflows: a line holding
`curl` and a release URL (`https://github.com/<owner>/<repo>/releases/download/<tag>/<asset>.jar`) with the jar's
sha256 on one of the next two lines. So the step that fetches the core in CI and in the release IS that pin.
`tools/core-pins.py --write` writes it from the worlds' `core.lock` files into both workflows, between the
`BEGIN core pins` / `END core pins` markers (one fetch per distinct core release; every world pins the same one today).
CI runs `tools/core-pins.py --check` and fails when a workflow disagrees with a `core.lock`. **Bumping the core is
therefore: edit `core.lock`, run `tools/core-pins.py --write`, commit both.** While the core's repository is private the
plain download is refused and the step falls back to `gh release download` with the `CORE_READ_TOKEN` secret; the
sha256 check is the same either way. Locally, `tools/fetch-core.sh` fetches the same bytes.

**Known gap:** `real-install` models ONE mod per repository, on a `neoforge-<mc>` branch, taking the newest release
whose tag ends `-mc<mc>`. This repository has several worlds on `main`, each tagged `<world>-<version>-mc<mc>`, so with
two worlds it would pick whichever was released last. The layout stays as it is; until MMVU can select a world (a tag
prefix), `tools/play.sh` is the supported way to install the core and one or more worlds in a test instance.
