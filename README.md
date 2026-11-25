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
licences (each world's `THIRD_PARTY_NOTICES.txt`). What third-party code and text may come in, and how it is checked:
[CONTRIBUTING.md](CONTRIBUTING.md); the register of third-party files: [THIRD_PARTY.md](THIRD_PARTY.md).

## Layout

```
<world>/                 one folder per world, a standalone Gradle build (./gradlew -p <world> ...)
  build.gradle           a few lines: its level id, anything it adds, then `apply from: '../shared/level.gradle'`
  gradle.properties      its mod id, name, versions, the core version range, its own Java packages
  core.lock              the public core jar it builds and plays against, pinned by version and sha256 (generated from
                         its Port CI workflow's pin: tools/core-pins.py)
  datapacks.lock.json    its built-in third-party packs, pinned by sha1 (with THIRD_PARTY_NOTICES.txt)
  src/main/java          its code: its @Mod class in com.moddingfromamod.levels.<world>, its own packages
  src/main/resources     its plug-in list (META-INF/services/...ModdyPlugin) and its structures
  src/level/resources    its world preset, World Type name, worlds/<world>.json and worlds/rules/<world>.rules.json
  src/test/java          its unit tests
  playtests/             its real-client playtests (tools/play.sh), and play.props
playtests/               real-client playtests across SEVERAL worlds in one game (tools/play.sh isle,arena)
.port/1.21.8/            MMVU's version-branch marker: how Port CI gates a 1.21.8 build (Releases, below)
shared/                  written once, compiled into EVERY world's jar (never a jar of its own)
  level.gradle           the build every world shares
  src/main/java          the shared level code: Level, LevelPacks, LevelWorldType, LevelClaim
  resources/META-INF     neoforge.mods.toml (templated) and the World Type listener's access transformer
tools/                   new-world.sh (the scaffold), play.sh, fetch-core.sh, core-pins.py, check-level-jar.py (incl. the stock-client rule),
                         changed-worlds.py, the leak, secret and licence scans and the git hooks
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
**sha256** (written from the world's Port CI workflow: "Bumping the core pin" below). The build takes that jar from
mavenLocal when Port CI has put it there (`com.moddingfromamod:moddingfromamod:<version>` in `~/.m2`), else downloads
it into `<world>/.core/`, and either way refuses any other bytes. `tools/fetch-core.sh isle` fetches the same jar into
`isle/.core/` (with `GH_TOKEN=…` if the core's repository is ever private).

**The build is sha-checked against core.lock unless -PcoreJar or Port CI's dependent-gate signal
(PORT_CI_DEP_OVERRIDE); neither is ever released.** `-PcoreJar=<jar>` builds against another core jar (a local build
of the core). On Port CI's dependent gate (a run with `dep_override`, which swaps a candidate core into mavenLocal),
the run sets `PORT_CI_DEP_OVERRIDE` to the coordinates it swapped; when that list names exactly
`com.moddingfromamod:moddingfromamod`, the build takes the mavenLocal jar unchecked (any other coordinates change
nothing). Both print a loud warning and mark the jar (the manifest attribute `Moddy-Unpinned-Core`), which
`tools/check-level-jar.py` refuses; Port CI never releases a `dep_override` run either. A `-PcoreJar` build still
builds: its own `build`/`assemble` runs `checkLevelJar` with `--allow-unpinned`, which warns and goes on, so
`./gradlew -p <world> build -PcoreJar=<jar>` gives a marked jar for a local
test before a core cut. Only the strict check, the one a release runs, refuses that jar.

Every `./gradlew -p <world> build` (and `assemble`) also checks the jar it made (task `checkLevelJar`):
`tools/check-level-jar.py` (below) and `tools/leak-scan.py` over the jar's contents but its third-party `datapacks/`.
Both need `python3`; without it the build stops and says so. `./gradlew -p <world> jar` alone skips the check.

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

`tools/check-level-jar.py` refuses a jar that breaks this, naming the class or file and what it referenced (every
world's `build` runs it, so Port CI does on every world). If a world needs something a stock client cannot draw, it is a change to this
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
  `tools/check-level-jar.py` refuses a class outside them, and a package the core jar it was built against also
  carries, in every `build` (so every Port CI run and every release).
- **`@EventBusSubscriber` with no `modid`**, so FML binds it to the jar it ships in.
- **One world jar per server.** `WishCharge` is read with `first`: two world jars that both fill it would race.
- **`shared/` compiles against every world's pinned core, so against the OLDEST one.** A change to `shared/` builds
  every world (each world's Port CI), which catches a break. A shared change that needs a newer core bumps every
  world's pin in the same PR (`tools/core-pins.py --set all <core tag>`), or guards itself so it still compiles and
  behaves on the oldest pin.
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
4. Give it its Port CI workflow, the same command as the other worlds' with its own folder (MMVU's
   `tools/port-ci.py --repo . --mod-dir <world> --also-path shared --also-path gradle --also-path gradlew --dep-jar
   <the same core pin as another world's workflow> --branch <.github/port-ci-branch> --gatec … --no-commit`;
   `port-ci.py args --file .github/workflows/port-ci-arena.yml` prints the arena's), then
   `python3 tools/core-pins.py --write` (its `core.lock` from that workflow) and `--check`. Commit both. Add a row
   to the table above.
5. Write `<world>/playtests/*.playtest` (and `play.props` if it needs launcher properties) and run
   `tools/play.sh <world> --build`; a playtest across worlds goes in the root `playtests/`.

## Keeping this repository free of private work

This repository is public (or will be), and the private side of Moddy must never reach it: no hosted-server settings,
no operations tooling, no player ids or players' words, no credentials. Every check runs the same rules:

- **git hooks**: run `./tools/git-hooks/install.sh` once per clone. `pre-commit` runs the secret scan and the leak scan
  over what you stage; `pre-push` scans every outgoing commit (so something added and deleted again is still
  refused). A maintainer adds the private forbidden list, kept OUTSIDE the repository:
  `./tools/git-hooks/install.sh --forbidden ~/.moddy/forbidden.txt`.
- **CI** (`.github/workflows/ci.yml`, on pull requests to `main` and by hand): the scanners' self-tests,
  `tools/core-pins.py --check`, the leak scan over the tree and every commit, the secret scan over the tree and the
  history, the licence scan, and each changed world's `build` (unit tests, the jar, `checkLevelJar`, `licenceCheck`).
  The release branch `neoforge-1.21.8` is built, gated and released by each world's **Port CI** workflow
  (`.github/workflows/port-ci-<world>.yml`), and `.github/workflows/release-line.yml` checks it holds nothing `main`
  does not (Releases, below).
- **The rules**: `tools/leak-scan.py`'s built-in shapes (emails, routable IPs, bucket URLs, every credential shape in
  `tools/secret_patterns.py`, children's-data file names, the private side's packages), plus
  [`tools/leak-rules.txt`](tools/leak-rules.txt) (committed, shapes only), plus the private list (never committed;
  CI reads it from the `FORBIDDEN_LIST` secret). A finding names the file, line and rule, never what matched.

Never bypass a refusal with `--no-verify`. A real credential that reached any commit is rotated first.

## Releases

**`main` is where the work lands; `neoforge-1.21.8` is the release branch**: `main` at a release point. Each world's
releases are cut from `neoforge-1.21.8` by its own **Port CI** workflow, `.github/workflows/port-ci-<world>.yml`,
generated by [MMVU](https://github.com/jivinstev/mc-mod-version-upgrade)'s `tools/port-ci.py` (one per world; never
edited by hand, regenerated instead). It builds the world with its Gradle `build` against the core its pin names, runs
the gates, and publishes the jar as a **pre-release** tagged `<world>-<mod_version>-mc1.21.8` (from
`<world>/gradle.properties`), with each gate summary beside it. `neoforge-1.21.8` is also the base the other version
branches (`neoforge-1.21.10` and up) are carried up from.

- **Work on a world lands on `main` only**, by pull request: an arena change, an Isle change, a `shared/` change. Never
  commit to `neoforge-1.21.8` directly: `release-line.yml` refuses a push or pull request that brings it a commit with
  no equal (by patch-id) on `main`. Merges of `main` and cherry-picks of `main`'s commits pass.

### The rule: every integrate is a new version

Taking `main` into `neoforge-1.21.8` ships a new version of every world it changes:

1. **Bump on `main` first.** For each world whose folder or `shared/` changed since its newest
   `<world>-*-mc1.21.8` release, a pull request on `main` titled `Version <world> X.Y.Z: <summary>` changes its
   `mod_version` in `<world>/gradle.properties` to the next patch (and adds to `<world>/RELEASE-NOTES.md`). A world
   nothing changed for keeps its version. The weekly round does this by itself, per world, only for those worlds.
2. **Merge `main` into `neoforge-1.21.8`** with a merge commit titled
   `Merge main into neoforge-1.21.8: <world> X.Y.Z[, <world> X.Y.Z], <summary>`.
3. **Port CI on `neoforge-1.21.8`** gates each world and cuts `<world>-<version>-mc1.21.8`: push that tag, or run the
   world's workflow (Actions → Port CI <world> → Run workflow, on `neoforge-1.21.8`) with **release** ticked.

Never release the same version twice: `-r2`, `-r3` names are not for an integrate.

### A hotfix

Made on `main` first (by pull request, with its world's version bump), then taken down: cherry-picked onto
`neoforge-1.21.8` in a pull request of its own (or `main` merged in), then released as above.

### Promotion

A person plays the pre-release, then promotes it: the same workflow with **promote** = the tag and **played_by** = their
GitHub login (or MMVU's `tools/port-ci.py promote --fork jivinstev/Moddy-Worlds --mod <world> --tag <tag>
--played-by <login>`). Nothing becomes a full Release unplayed.

### What the gates are

MMVU gates a Minecraft 1.21.8 build through its version-branch route, which `.port/1.21.8/branch.properties` turns on
(its 1.21.1 harness renamed for 1.21.8, as the core repository does). For a NeoGradle (`net.neoforged.gradle.userdev`)
build like ours MMVU does not inject its own harness, so Gate A runs the world's own unit tests and Gate B boots a
headless server with the core and the world and runs the one default GameTest; data the server rejects fails it. Each
runs twice, `full` and `minimal`. There is **no Gate C** (`--gatec none`): MMVU's client verdict comes from its
injected harness, which a NeoGradle build does not get. **Worlds' real-client coverage stays with its playtests
(`tools/play.sh`, above) until MMVU #298 lands**; run them before promoting.

A `dep_override` run (Port CI's dependent gate: a candidate core) never releases. The pre-releases made before Port CI
(by the Release workflow, up to arena and isle 0.1.6) stay valid and installable as they are.

**The Release workflow (`.github/workflows/release.yml`) is kept for now**, from `neoforge-1.21.8` only, for what Port
CI does not do: `SHA256SUMS` beside the jar, release notes that name the core release and sha256 to install beside it,
the World Type and `<world>/RELEASE-NOTES.md`, the jar's leak scan with the private forbidden list, and notes that do
not call a world an "unofficial port" (Port CI's wording until a world has a publish config saying it is this
repository's own, `.github/port-publish-<world>.json`). Use it only for a version Port CI has not released.

## Bumping the core pin

Each world's core pin is written in exactly one place: the `--dep-jar` step of its Port CI workflow, which downloads
the core release jar, checks its sha256 and installs it into mavenLocal as
`com.moddingfromamod:moddingfromamod:<version>`. `<world>/core.lock` (same keys as ever: `core_repo`, `core_tag`,
`core_asset`, `core_sha256`) is **generated** from it, with a header saying so; the build, `tools/fetch-core.sh`,
`tools/play.sh` and `tools/check-level-jar.py` read it. Each world, and each branch, keeps its own pin. To bump one:

```bash
python3 tools/core-pins.py --set isle moddingfromamod-1.0.11-mc1.21.8    # one world; `all` for every world
python3 tools/core-pins.py --check
./gradlew -p isle build
```

`--set` finds the release's core jar and its sha256 (from the release's `SHA256SUMS` when it has one, else by
hashing the download), regenerates `port-ci-<world>.yml` with MMVU's `tools/port-ci.py` (its other options read back
unchanged; MMVU from `--mmvu DIR`, `$MMVU` or `../mc-mod-version-upgrade`) and rewrites `core.lock`. It refuses a core
for another Minecraft version, and one outside the world's `core_version_range` in its `gradle.properties`, which
stays hand-kept: widen it first when a world needs a newer core. Commit the workflow and `core.lock` together, in a
pull request of their own **on `main`**; the new pin reaches `neoforge-1.21.8` the way everything does, with the next
integrate (a version bump for each world it changes) or as a hotfix taken down. CI runs `tools/core-pins.py --check`: every `core.lock` must be exactly its workflow's pin,
and every pinned core inside its world's range. `--write` rewrites every `core.lock` from the workflows.

**The branch the worlds release from is named once**, in `.github/port-ci-branch` (`neoforge-1.21.8`). `--set` and
`python3 tools/core-pins.py --regenerate <world|all>` (each workflow regenerated with its pin unchanged and the newest
Port CI kit) pass it to `port-ci.py` as `--branch`, and `--check` fails a workflow on any other branch.

MMVU's `real-install` reads the same step to find the jars a world needs beside it: a line holding `curl` and the
release URL, with the jar's sha256 on the next line.

**Known gap:** `real-install` models ONE mod per repository, on a `neoforge-<mc>` branch, taking the newest release
whose tag ends `-mc<mc>`. This repository has several worlds on `main`, each tagged `<world>-<version>-mc<mc>`, so with
two worlds it would pick whichever was released last. The layout stays as it is; until MMVU can select a world (a tag
prefix), `tools/play.sh` is the supported way to install the core and one or more worlds in a test instance.
