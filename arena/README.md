# Moddy Arena

A Moddy world (mod id `moddyarena`) that runs on the public Moddy core: a lit prep room beside a walled arena, in an
empty sky. The battle arena control panel on the prep room's wall sets how big the waves are and how hard, and its
button starts a battle: waves of monsters that start easy and get harder, never player against player. Wishes are made in the prep room (or the arena) before a battle and put on the hotbar; in a battle only
the hotbar's wishes work, one click each, with a rest between fires. What is not built yet is listed at the end.

## What a player gets

Make a world with **Create World → World tab → World Type → Moddy Arena**. On the first start the world builds its
place once, around x 0, z 0, standing at y 64:

- **The prep room**: 9 by 9 inside, an oak plank floor with four sea lanterns set in it, glass walls, a spruce roof.
  A player arrives here on his first join, in the middle of the room, facing north through the glass at the arena, and
  a few seconds later reads three lines (`worlds/arena.json`'s `intro`, which the core says once per player per world):
  > Welcome to Moddy Arena! You are in the prep room. The arena is past the gate.
  >
  > Get ready in the prep room: make things, then put them on your hotbar. Open one in My wishes and press Add to hotbar.
  >
  > The battle arena control panel is on the wall: pick the wave size and difficulty, then press START BATTLE. In a
  > battle, only the wishes on your hotbar work.
- **The arena**: 31 by 31 inside, a smooth sandstone floor, stone brick walls 4 high with a polished andesite rim on top
  that overhangs one block inward, so nothing climbs out.
- **The gate**: an oak fence gate in the middle of the wall the two share (a player opens it; a mob cannot).
- **The battle arena control panel**: the rest of that wall, across the prep room's width, is a black board facing the
  prep room (below). The arena is seen through the gate.

The world spawn is the arrival spot, facing the arena, so a respawn lands there too. Game mode is never changed.
Everything stands on nothing: there is no ground below, and both rooms are closed, so nothing falls out of them.

**No mob comes on its own.** When an arena world is first built, its game rule `doMobSpawning` is set to false, once
(nothing else spawns in a void world, and the arena floor is dark at night, so without it monsters, phantoms and
patrols would turn up). It is this world's rule only, and a player may set it back; the arena never sets it again.
Mob spawners, spawn eggs, `/summon`, breeding and wished creatures are not affected.

## The battle arena control panel

The wall between the prep room and the arena is a board of black concrete, 9 wide and 4 high in the default place (the
whole window but the gate and the opening above it), with its words in big floating letters (vanilla text displays, so
a stock client draws them). Seen from the prep room:

```
  [                BATTLE ARENA CONTROL PANEL                 ]     the title, in gold, over the whole board
  [-]   WAVE SIZE    [+]  .  |  .      START BATTLE                 [-] [+]: stone buttons on blue blocks
          5                  |     Ready! Press START.              (o): the start button, on green blocks 3 by 2
  [-]   DIFFICULTY   [+]  .  gate  .   [  (o)  ]
         Normal              |         [       ]
   First wave: 3 monsters  gate
   Last wave: 10 monsters
```

- **Wave size**: a number from 1 to 10, **5 by default**, with a down button left of it and an up button right of it.
  Under the two selectors the board says what it means: how many monsters the first wave and the last wave bring.
- **Difficulty**: Easy, Normal, Hard or Extreme, **Normal by default**, with its own down and up buttons.
- **START BATTLE**: the start button, on its green blocks, at eye height to the right of the gate, with the battle's
  state under the label: "Ready! Press START.", "Battle on: wave 2 of 5", "Battle on: wave 3 next", "You won!",
  "Battle lost", "Tidying the arena...".

Pressing a selector changes the number or the word on the board at once, plays a note (rising with the value; a low
one when nothing changed) and says one line on the presser's action bar, such as "Wave size 6: the first wave has 4
monsters, the last has 12." or "Difficulty: Hard. Tougher monsters, and strong ones sooner."; at either end it says
so ("The wave size is already the biggest.", "Easy is already the easiest."). **During a battle** (from the button to
the tidy) the selectors change nothing and say "A battle is on. Change the wave size and difficulty when it is over."
A battle keeps the settings it started with.

The settings are saved with the world (`data/moddyarena_place.dat`, below) and survive a restart; a world saved before
the panel reads as wave size 5 and Normal.

### What the wave size does

The rules file's `arena_waves` are the base waves. The wave size multiplies every group's count by size / 5, rounded
(half up), keeping at least 1 of each kind a wave has, and never more than 40 monsters a wave (the biggest group gives
one back until it fits). So **5 is the rules file's waves exactly**. With the default waves:

| wave size | 1 | 2 | 3 | 4 | **5** | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| first wave | 1 | 1 | 2 | 2 | **3** | 4 | 4 | 5 | 5 | 6 |
| last wave | 4 | 5 | 6 | 8 | **10** | 12 | 14 | 16 | 18 | 20 |

### What the difficulty does

A difficulty changes **which** mobs come and **how tough** they are, never how many (the size does that). Each is a row
of data in `ArenaDifficulty`: a health multiplier for every wave mob (an attribute modifier on its most health) and
swaps, "this kind comes as that kind from wave N on", the first that fits (a group is swapped once, never twice). The
count stays: one husk becomes one vindicator.

| difficulty | health | swaps |
|---|---|---|
| Easy | x0.6 | pillager, witch and vindicator come as husks |
| **Normal** | x1 | none: the rules file's waves exactly |
| Hard | x1.25 | from wave 4, husks come as vindicators |
| Extreme | x1.5 | from wave 3, husks come as vindicators; in wave 2, husks come as pillagers; from wave 5, vindicators come as ravagers |

With the default waves at size 5: Easy is husks only (3, 5, 6, 8, 10); Hard's waves 4 and 5 are vindicators, pillagers
and a witch; Extreme is 3 husks, then 5 pillagers, then vindicators and pillagers, and its last wave brings 2 ravagers.
Every mob a swap brings walks, stays in the arena and is none of the refused ones (a unit test reads them all). A wave
table that names none of a difficulty's mobs gets only its health.

## A battle

1. **Press the start button** on the control panel (or type `/moddy arena start`). A battle needs someone in the place (the arena or the
   prep room), a difficulty above Peaceful (on Peaceful monsters vanish), and a wave table in the rules. Only one
   battle at a time: pressing during one says "A battle is already on. Wait for it to end."
2. **The countdown**: "A battle is starting! 5 waves of monsters. The first comes in 5 seconds." A boss bar,
   "Wave 1 of 5 in 5 seconds", shows to everyone in the place, and the seconds count down above the hotbar.
   The battle brings the base waves at the panel's wave size and difficulty.
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
   board, gate; a panel button knocked off is put back), anything standing in it is cleared to the sky (including whatever was wished into it during the
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
- **The hotbar**: the core puts a thing with a recipe on the hotbar from its page (My wishes, press the thing, "Add to
  hotbar"; a ready-made wish's page opens on a right click). Using it makes the thing again, with no typing and no menu:
  the same replay as "Make another one". A thing has a recipe when it came from one: every Builder and Apprentice make
  he keeps, the starter library's wishes (the watch's ready-made ones, such as the Lightning Sword) and the Magician's
  wishes he keeps as recipes. A hotbar wish stays his when he is knocked out: it is back in its slot when he comes back
  in the prep room. The core's own one-second sweep shows on the slot; the arena's longer rest is checked when it is
  used, and a fire during it is refused on the action bar: "That wish is resting. It is ready again in 7 seconds."
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
three are instant wishes in a battle). A kept Builder or Apprentice make's recipe (`builder:<make id>`) reaches the hook
with the make's own name as its text and no prefix, so its hotbar wish is a kept thing made again too, while a new make
of the same thing still carries the prefix and is refused in a battle. The Magician's pickup (`atPickup`) is the only place a wish reaches a model. The
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

Nothing a wish does may reach past the place. A wish aimed anywhere else is refused. A creature from the Builder or the
Apprentice goes by the same rule: made in the arena it lands there (made in the prep room it lands beside him), and
made outside the place it is refused. The rules also carry a clamp
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
These are the base waves: the control panel's wave size and difficulty are applied on top of them (5 and Normal are
these waves exactly).

Four optional keys let a world fix or limit the control panel; without them **the player chooses**:

| key | value | what it does |
|---|---|---|
| `waveSize` | 1 to 10 | fixes the wave size: the size buttons change nothing and say "This arena's rules keep the wave size at 3." |
| `maxWaveSize` | 1 to 10 | the biggest wave size the player may pick ("This arena's rules let the wave size go up to 6.") |
| `difficulty` | `easy`, `normal`, `hard`, `extreme` | fixes the difficulty, the same way |
| `maxDifficulty` | `easy`, `normal`, `hard`, `extreme` | the hardest difficulty the player may pick |

A key and its `max` are not given together (the file is refused, saying so). A saved choice above a later limit is
brought down to it.

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
player kept safe), `5-battle-lost` (knocked out in wave 1, lost, back in the prep room with his sword, the arena
tidied), `6-prep-and-hotbar` (in prep, a make from the prep room lands in the arena ahead of it and one made in the arena
lands there, and an Apprentice Fire Sword goes on the hotbar from its own page in My wishes, by "Add to hotbar"; its
hotbar wish is used in the battle and makes it again, a second fire at once is refused with the seconds left, a new make
is refused with the arena's words; after the battle the hotbar wish is still there and new makes work),
`7-creature-in-the-arena` (an Apprentice cat made in the arena lands there; made standing outside the place, it is
refused and nothing is left), `8-knocked-out-keeps-hotbar` (knocked out in a battle holding a hotbar wish, he comes
back in the prep room with it in its slot, and it still makes the sword) and `9-control-panel` (the board's blocks and
twelve lines of words; he walks up to it and presses each selector, looking at it and using it: down and up to each
end, each press on the board and his action bar; at wave size 10 and Extreme the battle's waves are 6 husks with 30
health, then 10 pillagers, and ravagers in the last; in a battle a selector is refused and the board says "Battle on:
wave 1 of 5"; after it the board keeps his settings). A playtest cannot restart its game, so the settings surviving a
restart are `ArenaStateTest` (the saved record) and a dedicated server's restart.

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
| `ArenaWaves` | **pure**: the wave table and its checks, and what the rule fixes or limits on the control panel |
| `ArenaSettings` | **pure**: the panel's wave size and difficulty, how the size scales the waves, the rules' limits, a selector press |
| `ArenaDifficulty` | **pure**: the four difficulties as data (health, swaps) and how each changes the waves |
| `ArenaPanel` | **pure**: the control panel's layout: its blocks, buttons and where each line of words floats and how big |
| `ArenaBoard` | carries the panel out: reads its buttons, a selector press, keeps its words (text displays) |
| `ArenaWavesRule` | `arena_waves` as a `WorldRuleType`, and reading the table back from a world's rules |
| `ArenaMatch` | **pure**: a battle as a state machine (IDLE, COUNTDOWN, WAVE, BREATHER, WON, LOST, RESETTING) and its boss bar |
| `ArenaSpawns` | **pure**: where a wave's monsters appear |
| `ArenaWords` | **pure**: every word a player reads about a battle and on the control panel |
| `ArenaBattle` | carries a battle out: the button, the fighters, spawning and counting the monsters, the boss bar and titles, the reset |
| `ArenaRule` | `arena_place` as a `WorldRuleType`, and reading the place back from a world's rules |
| `ArenaModule` | the world module: builds once, gives a built place its upgrades, sets the spawn, the first arrival, the bases, the fallback rules, ticks the battle |
| `ArenaBuilder` | each kind of box as a block, placed; the control panel added to (or mended in) a place; the reset |
| `ArenaBases` | the arena as the core's `Bases`: every player's base, home in the prep room |
| `ArenaWishPolicy` | **pure**: the two phases, which kinds of wish each lets through, the Magician count, the cooldowns |
| `ArenaWishes` | **pure**: the wish tunables and their checks |
| `ArenaWishesRule` | `arena_wishes` as a `WorldRuleType`, and reading it back from a world's rules |
| `ArenaCharge` | the core's `WishCharge` for arena worlds: sorts each wish, refuses or counts it, gives counts back, says what is left |
| `ArenaState` | the world's saved record (versioned): the built sizes, who has arrived, the upgrades it has had, whether a battle is on, the panel's wave size and difficulty |

**A place built before the control panel existed** gains it on its next start, once (the upgrade `control-panel`,
recorded in `data/moddyarena_place.dat` beside `start-control` and `no-natural-spawns`): over the window's glass (and
the first start control's chiseled stone brick block, whose button and sign are taken down), and only where those
blocks and the prep room's air are still as built, so nothing a player put there is replaced; otherwise the log says
so and `/moddy arena start` still works. After that, every start puts back a panel block that is missing (a button
knocked off), by the same path, which sets nothing when the panel stands whole. The panel's words are text displays
tagged `moddyarena_panel` (and one role tag each); whenever the board's chunk has its entities loaded they are made
if missing, a second copy is removed and changed words are rewritten, so a world gets them on load and never twice.
The saved record carries `v` 2 since the panel; an older record (no `v`) reads its settings as the defaults and keeps
everything else.

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
- **"Give me one"** (a copy of a kept item) is not a wish, and the core's hook for it does not say which thing it is,
  so the arena lets it through in a battle with no rest.
