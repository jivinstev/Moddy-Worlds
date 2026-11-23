# Moddy Arena

A Moddy world (mod id `moddyarena`) that runs on the public Moddy core: a lit prep room beside a walled arena, in an
empty sky. Wishes land in the arena and nowhere else. Battles against waves of mobs are what it is for; they are not
built yet (see "Not built yet" below).

## What a player gets

Make a world with **Create World → World tab → World Type → Moddy Arena**. On the first start the world builds its
place once, around x 0, z 0, standing at y 64:

- **The prep room**: 9 by 9 inside, an oak plank floor with four sea lanterns set in it, glass walls, a spruce roof.
  A player arrives here on his first join, in the middle of the room, facing north through the glass at the arena, and
  a few seconds later reads two lines (`worlds/arena.json`'s `intro`, which the core says once per player per world):
  > Welcome to Moddy Arena! You are in the prep room. The arena is through the glass, past the gate.
  >
  > Battles are coming soon. For now, aim your wishes into the arena: that is where they land.
- **The arena**: 31 by 31 inside, a smooth sandstone floor, stone brick walls 4 high with a polished andesite rim on top
  that overhangs one block inward, so nothing climbs out.
- **The gate**: an oak fence gate in the middle of the wall the two share (a player opens it; a mob cannot), with the
  rest of that wall glass across the prep room's width, so the arena is in view.

The world spawn is the arrival spot, facing the arena, so a respawn lands there too. Game mode is never changed.
Everything stands on nothing: there is no ground below, and both rooms are closed, so nothing falls out of them.

**Wishes stay in the arena.** The arena is every player's base (`ArenaBases`), and the world's rules turn on the core's
`base_reach` rule ("a wish stays inside the arena"): a wish must be aimed at the arena's floor or the air above it, and
nothing a wish does may reach past the place. A wish aimed anywhere else, the prep room included, is refused. The rules
also carry a clamp (powers at most 60 seconds, one stack of each item, 2 kinds and 2 XP levels a wish).

## The rules file

`src/level/resources/worlds/rules/arena.rules.json`, the core's rules format, plus the arena's own rule type,
`arena_place` (`ArenaRule`, registered by `ArenaPlugin`):

```json
{"id": "the-arena-place", "type": "arena_place", "params": {"arenaSize": 31, "prepSize": 9, "wallHeight": 4}}
```

The arena's inside width (odd, 15 to 63), the prep room's (odd, 5 to 15, smaller than the arena) and the wall's height
(3 to 8). Each is optional. A file with a wrong size is refused whole, in words, as the core refuses any bad rule.

The sizes are read when the place is first built and saved with the world (`data/moddyarena_place.dat`), so a restart
builds nothing. **Sizes changed after that change nothing that stands**: the world keeps the place it was built with
(and wishes keep its reach), and the server log says so. A new world gets the new sizes.

A world made with the World Type uses this file from the jar. A dedicated server may name its own with
`-Dmoddingfromamod.world.rules=<file>`; it then replaces the jar's rules as soon as it is read.

## Play it locally

```bash
./gradlew -p arena test jar       # arena/build/libs/moddyarena-<version>+mc1.21.8.jar, and its unit tests
python3 tools/check-level-jar.py arena/build/libs/*.jar --level arena
./gradlew -p arena runClient      # a dev client with the core and the arena: Create World, World Type Moddy Arena
```

**A dedicated server** selects it in `server.properties`, read only when the world is first created:

```properties
level-type=moddyarena\:arena
initial-enabled-packs=vanilla,mod/moddyarena\:datapacks/level_marker
```

`./gradlew -p arena runServer -Plevel=on` also passes the world id, the rules file and the module by properties, as a
server's launcher does. The log says `plugin loaded: arena`, `this world was made as Moddy Arena`, `Moddy Arena: built
the place (arena 31, prep room 9, wall 4)`, `world spawn set to the prep room` and `world module arena started with
arenas`; a second start says `the place stands … nothing to build`. On a dedicated server, vanilla's spawn protection
(`spawn-protection` in `server.properties`) covers the prep room and much of the arena for players who are not
operators; it does not apply in single player.

**Playtests** (`playtests/`, a real client): `tools/play.sh arena --build`. They switch the arena on by properties in
the carrier's flat world (`playtests/play.props` names the module), where the place is built in the air above it:
`1-arrive` (the prep room, facing the arena), `2-wish-in-the-arena` (an Apprentice make aimed at the arena floor lands)
and `3-wish-outside-refused` (the same make aimed at the prep room floor is refused).

## Code

All of it in `com.moddingfromamod.levels.arena`:

| class | what it does |
|---|---|
| `ArenaMod` | the `@Mod` class: the World Type and the built-in marker pack (shared code) |
| `ArenaPlugin` | the plug-in: the `arena` world module and the `arena_place` rule type |
| `ArenaPlace` | **pure**: the sizes and their checks, every box of the place, the arrival spot, the reach, the start decision |
| `ArenaRule` | `arena_place` as a `WorldRuleType`, and reading the place back from a world's rules |
| `ArenaModule` | the world module: builds once, sets the spawn, the first arrival, the bases, the fallback rules |
| `ArenaBuilder` | each kind of box as a block, placed |
| `ArenaBases` | the arena as the core's `Bases`: every player's base, home in the prep room |
| `ArenaState` | the world's saved record: the built sizes and who has arrived |

## Not built yet

- **Waves**: mobs that come in rounds, and their spawning.
- **Mob spawning on the arena floor**: the floor is unlit, so hostile mobs can spawn on it at night as on any dark
  ground. Not solved here (no game rule, no lights); the waves step decides how the arena's mobs come.
- **Unlocks**: what a player earns between battles.
- **Hotbar battles**: fighting with what is on the hotbar.
- **Reset**: putting the arena back as it was after a battle.
