# Moddy Arena

A Moddy world (mod id `moddyarena`) that runs on the public Moddy core: a lit prep room beside a walled arena, in an
empty sky. A button in the prep room starts a battle: waves of monsters that start easy and get harder, never player
against player. Wishes are made in the prep room (or the arena) before a battle and put on the hotbar; in a battle only
the hotbar's wishes work, one click each, with a rest between fires. What is not built yet is listed at the end.

## What a player gets

Make a world with **Create World → World tab → World Type → Moddy Arena**. On the first start the world builds its
place once, around x 0, z 0, standing at y 64:

- **The prep room**: 9 by 9 inside, an oak plank floor with four sea lanterns set in it, glass walls, a spruce roof.
  A player arrives here on his first join, in the middle of the room, facing north through the glass at the arena, and
  a few seconds later reads three lines (`worlds/arena.json`'s `intro`, which the core says once per player per world):
  > Welcome to Moddy Arena! You are in the prep room. The arena is through the glass, past the gate.
  >
  > Make your wishes in the prep room or in the arena, then add them to your hotbar from My wishes.
  >
  > Press the button beside the gate to start a battle. In a battle, only the wishes on your hotbar work.
- **The arena**: 31 by 31 inside, a smooth sandstone floor, stone brick walls 4 high with a polished andesite rim on top
  that overhangs one block inward, so nothing climbs out.
- **The gate**: an oak fence gate in the middle of the wall the two share (a player opens it; a mob cannot), with the
  rest of that wall glass across the prep room's width, so the arena is in view.
- **The start button**: a stone button on the prep room's side of that wall, two blocks east of the gate, on a
  chiseled stone brick block, with a sign above it: "Press the button to start a battle!".

The world spawn is the arrival spot, facing the arena, so a respawn lands there too. Game mode is never changed.
Everything stands on nothing: there is no ground below, and both rooms are closed, so nothing falls out of them.

**No mob comes on its own.** When an arena world is first built, its game rule `doMobSpawning` is set to false, once
(nothing else spawns in a void world, and the arena floor is dark at night, so without it monsters, phantoms and
patrols would turn up). It is this world's rule only, and a player may set it back; the arena never sets it again.
Mob spawners, spawn eggs, `/summon`, breeding and wished creatures are not affected.

## A battle

1. **Press the start button** (or type `/moddy arena start`). A battle needs someone in the place (the arena or the
   prep room), a difficulty above Peaceful (on Peaceful monsters vanish), and a wave table in the rules. Only one
   battle at a time: pressing during one says "A battle is already on. Wait for it to end."
2. **The countdown**: "A battle is starting! 5 waves of monsters. The first comes in 5 seconds." A boss bar,
   "Wave 1 of 5 in 5 seconds", shows to everyone in the place, and the seconds count down above the hotbar.
3. **A wave**: its monsters appear at once on the arena floor, spread out and at least 8 blocks from any player. The
   title says "Wave 1 of 5", "Here they come!", and the boss bar "Wave 1 of 5: 3 monsters left", emptying as they fall.
   The players who are in the place when the countdown ends are the battle's **fighters**.
4. **A wave is cleared** when none of its monsters is left: "Wave 1 cleared! The next wave comes in 10 seconds.", then
   the breather, then the next wave. A monster that gets out of the place (past the walls, or below the floor) is
   removed and counts as cleared, so a battle can always finish. A monster that follows a player into the prep room
   through an open gate is still in the battle.
5. **Won**: the last wave cleared. "You won!", "All 5 waves cleared.", and in chat "You won the battle! All 5 waves are
   cleared. The arena will be tidied for the next battle."
6. **Lost**, in either of two ways, with "Battle lost" and a line in chat ending "Press the button in the prep room to
   try again.":
   - **every fighter is out**: knocked out, logged out, gone from the place (or a spectator). On your own, being
     knocked out loses the battle at once; with friends, the battle goes on while one fighter is still in. A fighter
     who is out stays out of that battle. You come back in the prep room (the arena's home), as always.
   - **time ran out**: a wave lasted longer than its limit (3 minutes by default), so a monster stuck somewhere can
     never hold a battle up forever.
7. **The arena is tidied** 5 seconds later, then "The arena is tidy again. Press the button in the prep room to start a
   battle." **A battle's end clears the arena**: every block in it is put back as it was built (floor, walls, rim,
   window, gate), anything standing in it is cleared to the sky (including whatever was wished into it during the
   battle), and every thing in it that is not a player goes: monsters, drops, experience, arrows, wished creatures and
   things. Kept: players, a pet someone owns, anything carrying a player, and **a player's own things**: what a
   fighter dropped when he was knocked out, and what a player threw, are carried to the prep room's middle (where he
   comes back) rather than cleared. The prep room and everything in it, and players' inventories, are never touched.

A server that stops in the middle of a battle ends it: on the next start the arena is tidied (after 5 seconds, so
everything in it has loaded) and no battle is on.

Each wave's monsters carry the tag `moddyarena_wave`, so a battle counts only its own. They never despawn while a
battle is on. Monsters with that tag found in the place when no battle is on (after a crash, say) are removed.

## Wishes: the prep phase and a battle

There are two phases, decided by `ArenaWishPolicy` (pure, unit-tested) and carried out by the arena's own wish charge
(`ArenaCharge`, the core's `WishCharge` hook, handed to the core by the arena's world module, so it is in force in arena
worlds only):

| | **prep** (no battle on) | **battle** (from the button to the tidy) |
|---|---|---|
| a Builder make, or an Apprentice tile | free, unlimited | refused |
| a kept thing made again (its hotbar wish, or "Make another one") | free, unlimited | **allowed**, then it rests `cooldownSeconds` (10) for that player |
| a typed wish for the Magician | allowed while he has some of this prep's `magicianWishes` (3) left | refused |

- **Prep** is whenever no battle is on. **A battle** starts when the button is pressed (the countdown counts as the
  battle: a slow wish made then would land in the middle of a wave) and ends when the arena is tidy again after a win or
  a loss; then every player's Magician count starts again and every rest ends.
- **The hotbar**: the core puts a thing with a recipe on the hotbar from its page (My wishes, right-click the thing,
  "Add to hotbar"). Using it makes the thing again, with no typing and no menu: the same replay as "Make another one".
  In core 1.0.3 a thing has a recipe when it came from one: the starter library's wishes (the watch's ready-made
  ones, such as the Lightning Sword) and the Magician's wishes he keeps as recipes. **A Builder or Apprentice make has no
  recipe in 1.0.3, so its page has no "Add to hotbar"** (it has "Change it in the Builder" instead). The core's own
  one-second sweep shows on the slot; the arena's longer rest is checked when it is used, and a fire during it is
  refused on the action bar: "That wish is resting. It is ready again in 7 seconds."
- **A Magician wish is counted when the Magician picks it up**, not when it is typed: a typed wish the wish library
  answers with no model costs nothing. A counted wish that fails, is stopped, times out or is undone gives its count
  back. A wish refused for the count is never charged: "You have used all 3 Magician wishes for this prep. You get 3
  more after the next battle. Builder and Apprentice wishes are still free."
- **In a battle** a typed wish or a new make is refused: "A battle is on, so only the wishes on your hotbar work now.
  New wishes are made in the prep room before a battle." With the Magician off (single player's default), typed words go
  to the Apprentice, whose page still opens; pressing one of its tiles is the make that is refused.
- **A child in distress** (the core's safety layer passes the flag) is never refused and never counted.
- `/moddy wishes` says, in prep, "Magician wishes left before the next battle: 2 of 3." and that Builder and Apprentice
  wishes are free; in a battle, that wishes are fired from the hotbar and how long each rests. The watch's and the wish
  box's titles add " - 2 Magician wishes left", or " - Battle: use your hotbar".

How the arena tells the kinds apart, at the core's submit hook (`refusesAtSubmit(player, distress, text, recipe)`):
`recipe` false is typed words; a recipe whose text starts with the core's Builder prefix (`"Builder: "`) is a new make
(the Builder's menus, an Apprentice tile, a typed change the Builder carries out); any other recipe is a kept thing made
again (the hotbar, "Make another one", and a left click on a ready-made wish in My wishes all submit its recipe, so all
three are instant wishes in a battle). The Magician's pickup (`atPickup`) is the only place a wish reaches a model. The
hook is told the wish's text, not its recipe id: a kept thing's rest is keyed by that text, which is the recipe id from
the hotbar and "Make another one" but the tile's own words from My wishes, so those two rest separately.

**Wishes stay in the arena and its prep room.** The arena is every player's base (`ArenaBases`), and the world's rules
turn on the core's `base_reach` rule ("a wish stays inside the arena and its prep room"). Where a wish may be aimed
depends on where he stands, because the core's base has one reach rectangle and one rectangle round both rooms would
also take in the open sky beside the prep room:

- **standing in the prep room**: the prep room's inside, and the arena's floor straight ahead of it through the gate,
  in the prep room's width (x -4 to 4, from the prep room's back wall to the arena's far wall, in the default place).
  A wish aimed at the prep room's floor lands there; a Builder or Apprentice make, which the core puts 12 blocks ahead
  of the player when he aims at nothing, lands on the arena's floor beyond the gate when he faces the arena;
- **standing anywhere else** (the arena included): the arena's inside, its floor or the air above it, wall to wall.

Nothing a wish does may reach past the place. A wish aimed anywhere else is refused. The rules also carry a clamp
(powers at most 60 seconds, one stack of each item, 2 kinds and 2 XP levels a wish).

## The rules file

`src/level/resources/worlds/rules/arena.rules.json`, the core's rules format, plus the arena's own three rule types,
registered by `ArenaPlugin`: `arena_place` (`ArenaRule`), `arena_waves` (`ArenaWavesRule`) and `arena_wishes`
(`ArenaWishesRule`).

### `arena_place`, the place's sizes


```json
{"id": "the-arena-place", "type": "arena_place", "params": {"arenaSize": 31, "prepSize": 9, "wallHeight": 4}}
```

The arena's inside width (odd, 15 to 63), the prep room's (odd, 5 to 15, smaller than the arena) and the wall's height
(3 to 8). Each is optional. A file with a wrong size is refused whole, in words, as the core refuses any bad rule.

The sizes are read when the place is first built and saved with the world (`data/moddyarena_place.dat`), so a restart
builds nothing. **Sizes changed after that change nothing that stands**: the world keeps the place it was built with
(and wishes keep its reach), and the server log says so. A new world gets the new sizes.

### `arena_waves`, the battle

```json
{"id": "the-waves", "type": "arena_waves", "params": {
  "countdownSeconds": 5, "breatherSeconds": 10, "waveSeconds": 180,
  "waves": [
    [{"mob": "minecraft:husk", "count": 3}],
    [{"mob": "minecraft:husk", "count": 5}],
    [{"mob": "minecraft:husk", "count": 4}, {"mob": "minecraft:pillager", "count": 2}],
    [{"mob": "minecraft:husk", "count": 4}, {"mob": "minecraft:pillager", "count": 2}, {"mob": "minecraft:witch", "count": 1}, {"mob": "minecraft:vindicator", "count": 1}],
    [{"mob": "minecraft:husk", "count": 4}, {"mob": "minecraft:pillager", "count": 3}, {"mob": "minecraft:witch", "count": 1}, {"mob": "minecraft:vindicator", "count": 2}]
  ]}}
```

Everything here is tunable, and **these first values are guesses** to be tuned by playing: the waves in order (1 to
20), each a list of `{mob, count}` spawned at once (a count 1 to 20, at most 40 monsters a wave; `husk` alone means
`minecraft:husk`), the countdown before the first wave (3 to 60 s), the breather between waves (3 to 120 s) and how long
one wave may last before the battle is lost (30 to 1800 s). A wrong table refuses the file whole, in words; a change
applies from the next battle. No rule, no battle ("This arena has no waves in its rules, so a battle cannot start.").

Why these mobs: the arena is open to the sky in daylight, its walls are 4 high with a rim. **Husks** (a zombie that
does not burn in daylight) are the easy start; **pillagers** shoot crossbows from a distance; a **witch** throws
potions; **vindicators** hit hard with an axe, so they come last. None of them burns in daylight, climbs, flies or
teleports, and none can open the fence gate. They are spawned as an event (never as a patrol leader, so no Bad Omen),
so pillagers and vindicators come with their weapons.

Any living mob is accepted, the table's author decides, except those that cannot stay in the arena, which refuse the
file saying why: spiders and cave spiders (climb the walls), endermen and shulkers (teleport), silverfish (hide in the
stone brick walls), creepers (blow holes in the floor), breezes (jump over the walls), evokers (their vexes fly through
walls), and fliers (ghasts, phantoms, vexes, blazes, bats, allays, bees, parrots, the wither, the ender dragon), plus
the player and the armor stand. Zombies, skeletons, strays, drowned and bogged are accepted but burn in daylight, so
they make an easy wave by day.

### `arena_wishes`, the wishes of the two phases

```json
{"id": "prep-and-battle-wishes", "type": "arena_wishes", "params": {"magicianWishes": 3, "cooldownSeconds": 10}}
```

`magicianWishes`: the Magician wishes each player may make between two battles (0 to 20; 0 means none in this arena).
`cooldownSeconds`: how long a kept thing rests, for that player, after it is fired in a battle (1 to 120). Both are
optional, and **both first values are guesses** to be tuned by playing. A wrong value refuses the file whole, in words;
a file with no `arena_wishes` rule gets these values.

### Where the file comes from

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
`1-arrive` (the prep room, facing the arena, the start button beside the gate), `2-wish-in-the-arena` (an Apprentice
make aimed at the arena floor lands), `3-wish-outside-refused` (the same make made standing on the ground outside the
place is refused),
`4-battle-won` (the button pressed, five waves cleared, won, the arena tidied: the monsters are killed by command, the
player kept safe) and `5-battle-lost` (knocked out in wave 1, lost, back in the prep room with his sword, the arena
tidied) and `6-prep-and-hotbar` (in prep, a make from the prep room lands in the arena ahead of it and one made in the
arena lands there, and the starter Lightning Sword is made by its recipe; its hotbar wish is used in the battle and
makes it again, a second fire at once is refused with the seconds left, a new make is refused with the arena's words;
after the battle the hotbar wish is still there and new makes work; knocked out, the hotbar wish is gone). The
carrier clicks with the left button only, and a ready-made wish's page opens on a right click, so that script puts the
hotbar wish in place with the data the core's "Add to hotbar" writes.

**A battle on a server with nobody on** (a test): `-PmoddyProps=arena.allowEmptyBattle=true` (the property
`moddingfromamod.arena.allowEmptyBattle`) lets `/moddy arena start` start a battle from the console and carry it on with
no fighter; kill each wave with `/kill @e[tag=moddyarena_wave]`. The public core does not keep an empty dedicated
server ticking for a world's work, so set `pause-when-empty-seconds=0` in that server's `server.properties`, or the
battle stops when the server pauses after 60 seconds.

## Code

All of it in `com.moddingfromamod.levels.arena`:

| class | what it does |
|---|---|
| `ArenaMod` | the `@Mod` class: the World Type and the built-in marker pack (shared code) |
| `ArenaPlugin` | the plug-in: the `arena` world module, the `arena_place` and `arena_waves` rule types, `/moddy arena start` |
| `ArenaPlace` | **pure**: the sizes and their checks, every box of the place, the start button, the arrival spot, the reach, the start decision, the upgrades, what a reset puts back, whether a mob is still in the place |
| `ArenaWaves` | **pure**: the wave table and its checks |
| `ArenaWavesRule` | `arena_waves` as a `WorldRuleType`, and reading the table back from a world's rules |
| `ArenaMatch` | **pure**: a battle as a state machine (IDLE, COUNTDOWN, WAVE, BREATHER, WON, LOST, RESETTING) and its boss bar |
| `ArenaSpawns` | **pure**: where a wave's monsters appear |
| `ArenaWords` | **pure**: every word a player reads about a battle |
| `ArenaBattle` | carries a battle out: the button, the fighters, spawning and counting the monsters, the boss bar and titles, the reset |
| `ArenaRule` | `arena_place` as a `WorldRuleType`, and reading the place back from a world's rules |
| `ArenaModule` | the world module: builds once, gives a built place its upgrades, sets the spawn, the first arrival, the bases, the fallback rules, ticks the battle |
| `ArenaBuilder` | each kind of box as a block, placed; the start control added to an older place; the reset |
| `ArenaBases` | the arena as the core's `Bases`: every player's base, home in the prep room |
| `ArenaWishPolicy` | **pure**: the two phases, which kinds of wish each lets through, the Magician count, the cooldowns |
| `ArenaWishes` | **pure**: the wish tunables and their checks |
| `ArenaWishesRule` | `arena_wishes` as a `WorldRuleType`, and reading it back from a world's rules |
| `ArenaCharge` | the core's `WishCharge` for arena worlds: sorts each wish, refuses or counts it, gives counts back, says what is left |
| `ArenaState` | the world's saved record: the built sizes, who has arrived, the upgrades it has had, whether a battle is on |

**A place built before the start button existed** gains it on its next start, once (the upgrade `start-control`,
recorded in `data/moddyarena_place.dat` beside `no-natural-spawns`): only where the window's glass and the prep room's
air are still as built, so nothing a player put there is replaced; otherwise the log says so and `/moddy arena start`
still works.

## Not built yet

- **Unlocks**: wishes unlocked in tiers as a player wins battles.
- **Spectators**: watching a battle without being in it (today a player in the prep room during the countdown is a
  fighter).
- **More than one arena**, or more than one battle at a time.

What the public core does not let a world do yet (so the arena does not do it either):

- **An Apprentice page cannot be refused.** Typed words that go to the Apprentice open its page before the core asks
  the world's charge, so in a battle the page opens and pressing a tile is what is refused.
- **A refused Builder make also reads "Your wish bank is empty. It fills up again soon."** The core's Builder menus say
  that fixed line whenever the charge refuses a make; the arena's own line is said after it.
- **The hotbar's sweep shows one second**: a world cannot set the cooldown the slot draws, so the arena's rest is told
  in words when it is pressed.
- **A Builder or Apprentice make cannot go on the hotbar**: in core 1.0.3 it has no recipe, so the hotbar holds only
  ready-made and Magician wishes kept as recipes.
- **A hotbar wish is lost when the player is knocked out**: the core takes it out of what he drops, and nothing puts it
  back. It is still in My wishes, and goes back on the hotbar from
  its page.
- **A creature from the Builder or the Apprentice is refused here** (seen from the prep room: "Pal the Cat didn't work
  this time"): the core checks that what its `modify_mob` step touches, 64 blocks round the player, stays inside the
  base's area, and the whole place is smaller than that.
- **"Give me one"** (a copy of a kept item) is not a wish, and the core's hook for it does not say which thing it is,
  so the arena lets it through in a battle with no rest.
