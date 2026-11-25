# Moddy Arena

## How to play

1. **Install** the Moddy core jar and the Moddy Arena jar in a NeoForge 1.21.8 game (the arena's
   [release notes](../../../releases) name the core it was built for).
2. **Create World**, open the **World** tab and pick **World Type: Moddy Arena first**, then choose your game mode on
   the **Game** tab. (Either order makes the same world; this one is simply the easy one to remember.)
3. **Play it in Survival.** Creative is not much fun here: the monsters do not go for a player in Creative and he
   cannot be knocked out, so the waves, the lives and the boss fights stop meaning anything. (A Creative player who is in
   the place when a battle starts still counts as one of its fighters, and his run still goes on the Champions board
   today.) A player in Spectator is never a fighter, and a fighter who turns to Spectator is out of that battle.
4. Keep the game's own difficulty above **Peaceful**: on Peaceful monsters vanish as soon as they come, so the button
   says "Battles need the game's difficulty above Peaceful: on Peaceful, monsters vanish as soon as they come." and no
   battle starts. (The arena's own Easy, Normal, Hard and Extreme are picked on its control panel.)
5. In the prep room, make wishes with the Builder or the Apprentice and put them on your hotbar (My wishes, a wish's
   page, **Add to hotbar**). On the control panel pick the **difficulty** (it sets how many monsters come) and the
   **waves**, then press **START BATTLE**. When a wave comes the gate shuts and you are taken into the arena; fight with
   your hotbar wishes, rest in the breaks, and try to get on the Champions board.

The rest of this page is how it all works.

## What it is

A Moddy world (mod id `moddyarena`) that runs on the public Moddy core: a lit prep room beside a walled arena, standing
on the ground. The battle arena control panel on the prep room's wall sets how hard the monsters are (the difficulty,
which also sets how many a wave brings) and how many waves there are (5, 10, 15, 20 or Endless), and its button starts
a battle: waves of monsters that start easy and get harder, a boss every 5th wave, never player against player. While a
wave is on the gate is shut with iron bars: nobody gets in or out. Each player has 3 lives in a battle.
The Champions board on the prep room's back wall shows the best runs at the settings on the panel. Wishes are made in the prep room (or the arena) before a battle and put on the hotbar; in a battle only
the hotbar's wishes work, one click each, with a rest between fires and 5 fires a wave. What is not built yet is listed at the end.

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
  > The battle arena control panel is on the wall: pick the difficulty and the waves, then press START BATTLE. When a
  > wave comes the gate shuts and you are taken into the arena. Only your hotbar wishes work in a battle, and you have
  > 3 lives. The Champions board is on the back wall.
- **The arena**: 31 by 31 inside, a smooth sandstone floor, stone brick walls 4 high with a polished andesite rim on top
  that overhangs one block inward, so nothing climbs out.
- **The gate**: an oak fence gate in the middle of the wall the two share (a player opens it; a mob cannot). **While a
  wave is on it is iron bars** (below, "The gate in a battle"): nobody can open them, and everyone can see through.
- **The battle arena control panel**: the rest of that wall, across the prep room's width, is a black board facing the
  prep room (below). The arena is seen through the gate.
- **The Champions board**: the prep room's back wall (behind a player who arrives) is a black board facing into the
  room (below).

The world spawn is the arrival spot, facing the arena, so a respawn lands there too. Game mode is never changed.

**It stands on the ground.** A world made with the World Type is flat grassland (bedrock, stone, dirt and grass, its
top at y 63, the floors' level; the void biome, no structures, no features), so the place stands on real ground. Under
the place itself, and 2 blocks round it, the arena always builds its own **base** (`ArenaPlace.baseFills`): the floors
(and grass round them) on a layer of stone, on a layer of **bedrock**. So a blast that breaks the floor drops a player
2 blocks at most, onto bedrock, never out of the world; this holds in any world the arena is switched on in (a
server's flat or void world too), and a world built before the base gains it on its next start (the upgrade
`ground-base`). The walls and floors are not unbreakable: a wish may still tear the arena up, and it is mended (below).
**Nobody is lost under it**: the floor under the world spawn is put back within a second whenever it is missing (and
the prep room's floor is mended where it has holes, with the arena), and a player who still falls out under the place
(more than a few blocks below the base) is put back in the prep room unharmed, with "Whoops, you fell out! Back to the
prep room." on his action bar; it never costs a life. A wave's monster that falls out is put back in the arena.

**No mob comes on its own.** When an arena world is first built, its game rule `doMobSpawning` is set to false, once
(the arena floor and the ground round it are dark at night, so without it monsters, phantoms and patrols would turn
up). It is this world's rule only, and a player may set it back; the arena never sets it again.
Mob spawners, spawn eggs, `/summon`, breeding and wished creatures are not affected.

## The battle arena control panel

The wall between the prep room and the arena is a board of black concrete, 9 wide and 4 high in the default place (the
whole window but the gate and the opening above it), with its words in big floating letters (vanilla text displays, so
a stock client draws them). Seen from the prep room:

```
  [                BATTLE ARENA CONTROL PANEL                 ]     the title, in gold, over the whole board
  [          Best for Steve: wave 7 in 3:12                   ]     the best of whoever pressed a button last
  [-]   DIFFICULTY   [+]  .  |  .      START BATTLE                 [-] [+]: stone buttons on blue blocks
         Normal              |     Ready! Press START.
  [-]     WAVES      [+]    bars   [  (o)  ]                        (o): the start button, on green blocks 3 by 1
           5               gate
     First wave: 3 monsters gate    Boss wave: 5
                                    Hotbar wishes: 5 a wave
```

- **Difficulty**: Easy, Normal, Hard or Extreme, **Normal by default**, with a down button left of it and an up button
  right of it. It sets how hard the monsters are **and how many come** (below); there is no separate monsters setting.
- **Waves**: how many waves a battle has: 5, 10, 15, 20 or **Endless** (waves until the fighters lose), **5 by
  default**, with its own down and up buttons.
- **First wave**, under the two selectors, in big letters: how many monsters the first wave brings at this difficulty
  ("First wave: 3 monsters"). Under the start button the board says the boss waves ("Boss waves: 5, 10", or "A boss
  every 5 waves" for Endless) and the hotbar wishes a wave.
- **START BATTLE**: the start button, on its green blocks, at eye height to the right of the gate, with the battle's
  state under the label: "Ready! Press START.", "Battle on: wave 2 of 5", "Break: wave 3 in 12s", "Break: BOSS wave 5
  in 9s", "BOSS wave 5 of 5!", "Battle on: wave 12" (Endless), "You won!", "Battle lost", "Tidying the arena...". In a
  break between waves its label reads **READY** (below).
- **The best line**, under the title: the best run, at the settings on the panel now, of whoever pressed a button on
  the panel last ("Best for Steve: won in 4:12", "Best for Steve: wave 7 in 3:12", "Steve: no best at these settings
  yet"); before anyone has pressed one, the only player in the world's.

Pressing a selector changes the number or the word on the board at once, plays a note (rising with the value; a low
one when nothing changed) and says one line on the presser's action bar, such as "Difficulty: Hard. More monsters, and
tougher. First wave: 4 monsters." or "Waves: Endless. They keep coming until you lose!"; at either end it says so
("Easy is already the easiest.", "Endless is already the most."). **During a battle** (from the button to the tidy) the selectors change nothing and say "A battle
is on. Change the settings when it is over." A battle keeps the settings it started with.

The settings are saved with the world (`data/moddyarena_place.dat`, below) and survive a restart; a world saved before
the panel reads as Normal and 5 waves, and one saved before the WAVES selector as 5 waves. Until 0.1.6 the panel had a
third selector, MONSTERS (1 to 10); a world saved then keeps its difficulty and waves, its monsters number is not read,
and on its next start its panel loses the MONSTERS buttons and is laid out as above.

### How many monsters: the difficulty decides

The rules file's `arena_waves` are the base waves. Each difficulty carries its own **wave size**, on the scale the old
MONSTERS selector had (1 to 10, where 5 is the rules file's waves exactly): every group's count is multiplied by wave
size / 5, rounded (half up), keeping at least 1 of each kind a wave has, and never more than 40 monsters a wave (the
biggest group gives one back until it fits). The sizes go with each difficulty's health, and the first wave grows by
one monster each step; the old scale's ends (1: one of each kind, a wave that is hardly a fight; 10: twice the rules
file's) are left for a rules file to pick (`waveSizes`, below). With the default waves:

| difficulty | wave size | x the rules file's counts | first wave | 5th wave's monsters (before its boss escort is taken) |
|---|---|---|---|---|
| Easy | 3 | x0.6 | 2 | 6 |
| **Normal** | **5** | **x1** | **3** | **10** |
| Hard | 6 | x1.2 | 4 | 12 |
| Extreme | 8 | x1.6 | 5 | 16 |

### What else the difficulty does

A difficulty also changes **which** mobs come and **how tough** they are.
Each is a row of data in `ArenaDifficulty`: a health multiplier for every wave mob and boss (an attribute modifier on
its most health) and swaps, "this kind comes as that kind from wave N on", the first that fits (a group is swapped
once, never twice). The count stays: one husk becomes one vindicator.

| difficulty | health | swaps |
|---|---|---|
| Easy | x0.6 | pillager, witch, vindicator, piglin brute and ravager come as husks |
| **Normal** | x1 | none: the rules file's waves exactly |
| Hard | x1.25 | from wave 4, husks come as vindicators |
| Extreme | x1.5 | from wave 3, husks come as vindicators; in wave 2, husks come as pillagers; from wave 5, vindicators come as ravagers |

Every mob a swap brings walks, stays in the arena and is none of the refused ones (a unit test reads them all). A wave
table that names none of a difficulty's mobs gets only its health.

### The number of waves, and the waves past the rules file's

A battle brings the rules file's waves in order; past their end (wave 6 on, with the default rules), each wave is
**made from its number** by the generator (`ArenaGenerator`, pure), so any number of waves, or Endless, needs no
hand-written list. For wave `n` after the rules file's last wave `L` (which brings `S` monsters; 5 and 10 by default):

- **how many**: `round(S + 0.5 * (n - L))`, at most 40 (wave 6: 11, wave 10: 13, wave 20: 18, wave 65 on: 40);
- **which**: every kind whose first wave has come, shared out by weight (largest remainder, each kind at least 1 while
  there are enough): husks (from wave 1, weight 4), pillagers (3, weight 3), a witch (4, weight 1), vindicators (4,
  weight 2), **piglin brutes from wave 7** (weight 2; they never turn to zombies in the arena) and **ravagers from
  wave 12** (weight 1). None of them burns in daylight, climbs, flies, teleports or fights the others;
- **how tough**: their most health times `min(2.5, 1 + 0.04 * (n - L))` (wave 10: x1.2, wave 20: x1.6, from wave 43:
  x2.5), so it rises slowly and stops.

The difficulty's wave size and its swaps then apply as to any wave, and a wave never brings more than 40, so an
Endless battle never has more than 41 monsters alive (a boss wave's boss and its escort are far fewer). A rules file
can author up to 100 waves itself, and can tune the generator (`generator`, below).

### Boss waves

**Every 5th wave is a boss wave** (5, 10, 15, 20, 25, ...): a named boss and a small escort (a third of that wave's
monsters, each kind at least 1, at most 10 in all). The title says **BOSS WAVE** and the boss's name ("Rumble the
Ravager is here!"), a raid horn sounds, the chat says "Wave 5 is a boss wave: Rumble the Ravager is here!", the board's
state says "BOSS wave 5 of 10!", and the boss bar shows **the boss's health**, in purple ("BOSS: Rumble the Ravager
(wave 5 of 10)"): the one thing it shows while the boss stands. When the boss is beaten ("You beat Rumble the
Ravager!") the bar goes back to counting the wave's monsters. A boss wave may last twice as long as another. Beating
the last wave of a battle with a number of waves wins it, boss wave or not.

The bosses (`ArenaBosses`, pure), each a vanilla mob made tougher only through its attributes and named over its head,
so a stock client draws it; the difficulty multiplies its health as it does every mob's:

| boss wave | boss | mob | health | attack | size | knockback resistance |
|---|---|---|---|---|---|---|
| 1st (wave 5) | Rumble the Ravager | ravager | 150 | x1 | x1.2 | 0.8 |
| 2nd (wave 10) | Captain Chop | vindicator | 200 | x1.5 | x1.6 | 0.6 |
| 3rd (wave 15) | Bruno the Brute | piglin brute | 250 | x1.3 | x1.6 | 0.7 |
| 4th (wave 20) | King Husk | husk | 300 | x2 | x2 | 0.8 |
| 5th (wave 25) | Thunderhoof | ravager | 400 | x1.5 | x1.5 | 1.0 |

Past the table it starts again with more health each time round (x2 the second time: Rumble the Ravager has 300 at
wave 30; x3 the third), never above 1024 (Minecraft's limit). A boss stands in the arena's middle when no player is
near it (the most room for a big mob), else as far from the players as the wave's other spots. **Not bosses, on
purpose**: the wither (it flies over the walls and blows the arena apart) and the warden (too frightening for a child:
it blinds with darkness and digs).

## The Champions board

The prep room's back wall is a black board (9 wide and 4 high in the default place) facing into the room. Seen from
the middle of the room, turned round:

```
  [                   ARENA CHAMPIONS                     ]   the title, in gold
  [                   Normal, 5 waves                     ]   the settings these champions played at
  [ 1.  (face)  Steve              Won all 5      3:12    ]
  [ 2.  (face)  Alex               Wave 4         2:40    ]
  [ 3. ... 4. ... 5. ...                                  ]
```

It shows the top 5 **at the settings on the control panel now** (the difficulty and the number of waves; the
difficulty brings its own number of monsters), so players race at the same game: fastest to beat 5 waves on Normal is a race of its own, and so is Easy;
pressing a selector switches the board at once ("No champions here yet. Be the first!" at settings nobody has played).
Each row: the place, the player's **face** (a vanilla player head on an item display, carrying his profile, so a stock
client draws his skin; a profile with no skin, as in an offline game, draws a default one), his name, how far he got
("Won all 5", or "Wave 7") and his time (minutes:seconds from the first wave's start to when he got there).

**How runs rank, at one settings** (`ArenaChampions`, pure):

1. a run that beat every wave ranks above one that did not, the fastest first;
2. then the furthest wave reached;
3. then a wave cleared above a wave he fell in (he ran out of lives in the breather after clearing it, or in it);
4. then the fastest to get there (for a wave he fell in: to that wave's start; cleared: to when it was cleared);
5. then who got there first.

For Endless there is no beating every wave: the furthest wave, then cleared, then the fastest. **One best entry per
player at each settings**: a run replaces his entry only when it ranks above it (his name is kept up to date either
way); each settings keeps its 20 best. A run is recorded when the battle ends, won or lost, for every player who
fought in it: how far the battle got for those still in at the end, and for one out before (out of lives, logged out,
gone from the place) how far it had got when he went out. He is told in chat: "Your run: Wave 3 in 1:10. You are number
1 on the Champions board!". Lives are not on the board. Only a player's id, his name and his scores are kept (with the
arena's record, below); nothing he typed. The board's words and faces are tagged `moddyarena_champions` and kept like
the control panel's (made when missing, a second copy removed, changed words rewritten), so a restart never doubles
them.

## A battle

1. **Press the start button** on the control panel (or type `/moddy arena start`). A battle needs someone in the place (the arena or the
   prep room), a difficulty above Peaceful (on Peaceful monsters vanish), and a wave table in the rules. Only one
   battle at a time: pressing during one says "A battle is already on. Wait for it to end."
2. **The countdown**: "A battle is starting! 5 waves of monsters. The first comes in 5 seconds. You each have 3
   lives." A boss bar, "Wave 1 of 5 in 5 seconds", shows to everyone in the place, and the seconds count down above
   the hotbar, and "When a wave comes, the gate shuts and everyone fighting is taken into the arena. It opens again in
   the break." The battle brings the waves the panel's difficulty and waves make of the rules file's.
3. **A wave**: the players who are in the place when the countdown ends are the battle's **fighters**. Every fighter
   who is not in the arena (waiting in the prep room, say) is moved just inside the gate, facing in ("The gate is shut.
   Fight!" on his action bar), **the gate shuts** (below), and the wave's monsters appear at once on the arena floor,
   spread out and at least 8 blocks from any player. The title says "Wave 1 of 5", "Here they come!", and the boss bar
   "Wave 1 of 5: 3 monsters left", emptying as they fall.
4. **A wave is cleared** when none of its monsters is left, and **a break** comes before the next wave, with the gate
   open again: "Wave 1 cleared! Break time: the gate is open, and the next wave comes in 15 seconds. Change your hotbar
   in the prep room, or press READY on the board." (below). A wave's monster that is somehow outside the arena (in the
   prep room, the gateway, or fallen out under the place) is put back in the arena; one nowhere near the place is
   removed and counts as cleared, so a battle can always finish.
5. **Won**: the last wave cleared (an Endless battle is never won: it goes on until the fighters lose). "You won!", "All 5 waves cleared.", and in chat "You won the battle! All 5 waves are
   cleared. The arena will be tidied for the next battle."
6. **Lost**, in either of two ways, with "Battle lost" and a line in chat ending "Press the button in the prep room to
   try again.":
   - **every fighter is out**: out of lives, logged out, gone from the place (or a spectator). **Each fighter has 3
     lives** (the rules' `lives`): knocked out with lives left, he comes back in the prep room (the arena's home) with
     his hotbar wishes, the chat says "Knocked out! 2 lives left. You are back in the prep room for a moment, then back
     in the arena: go get them!", and **the battle goes on**: on his feet, his action bar counts "2 lives left. Back
     into the arena in 3...", 2, 1, and he is moved back just inside the gate ("Back in the arena. Fight!") while the
     wave is still on (in a break he simply walks in). Knocked out of his last life ("Knocked out, and no lives left.
     You are out of this battle."), he is out of that battle: he stays in the prep room and can watch through the bars. On your own, that loses it; with friends, the battle goes on while one
     fighter is still in.
   - **time ran out**: a wave lasted longer than its limit (3 minutes by default), so a monster stuck somewhere can
     never hold a battle up forever.
7. **The arena is tidied** 5 seconds later, then "The arena is tidy again. Press the button in the prep room to start a
   battle." **A battle's end clears the arena**: every block in it is put back as it was built (the base under it,
   floor, walls, rim, board, gate; a panel button knocked off is put back; holes in the prep room's floor filled), anything standing in it is cleared to the sky (including whatever was wished into it during the
   battle), and every thing in it that is not a player goes: monsters, drops, experience, arrows, wished creatures and
   things. Kept: players, a pet someone owns, anything carrying a player, and **a player's own things**: what a
   fighter dropped when he was knocked out, and what a player threw, are carried to the prep room's middle (where he
   comes back) rather than cleared. The prep room and everything in it, and players' inventories, are never touched.

### The gate in a battle

`ArenaGate` (pure, unit-tested) decides; the battle carries it out.

- **While a wave is on the gate is shut**: the gateway (the gate's block and the opening above it) is **iron bars**,
  which nobody can open (a fence gate opens by hand) and everyone can see through. No monster gets out and no fighter
  either. In the countdown, in a break between waves and when no battle is on it is the oak fence gate, as built (put
  back closed after a wave), so he may go to the prep room in a break to change his hotbar. Bars broken during a wave
  are put back within a second.
- **When a wave starts** (the first, and after each break), every fighter on his feet who is not in the arena is moved
  just inside the gate, facing in, with "The gate is shut. Fight!". So pressing START in the prep room and waiting
  there is fine: he is taken in. A player who is not fighting is never moved, unless he stands in the gateway itself
  as it shuts: then he steps to the prep room's side.
- **Knocked out with lives left**, he is back in the prep room; after a count of 3 seconds on his action bar he is
  moved back in, if the wave is still on. **Out of lives**, he stays in the prep room behind the bars.
- **Monsters** outside the arena during a wave are put back inside; a wished creature outside it loses its battle role,
  as before. Spectators and players in the prep room who are not fighting are untouched.
- The gateway is the gate again when the battle ends (won, lost, or ended by a restart in the middle of one: the
  arena's reset puts back the fence gate), and the mend between waves and the battle's reset both ask `ArenaGate`
  what the gateway should be, so they never disagree.

### The break between two waves

- **How long**: 15 seconds after a wave (`breatherSeconds`), **45 after a boss wave** (`bossBreatherSeconds`), the
  hardest fight. Its timer is in sight the whole time: the boss bar ("Wave 6 of 10 in 44 seconds", emptying), the
  seconds above the hotbar ("Wave 6 in 3..."), and the control panel's state line ("Break: wave 6 in 44s").
- **READY**: in a break the start button's label reads **READY**. A fighter who presses it is ready ("You are ready!
  Waiting for 1 more fighter." when friends are not yet); once every fighter still in is ready, "Everyone is ready! The
  next wave comes in 3 seconds." and the break ends then, so nobody waits. A player who is not fighting is told the
  fighters press it. Outside a break the button starts a battle as before.
- **His hotbar**: in the break he may walk back into the prep room and change what is on his hotbar: in My wishes, a
  thing's page, "Add to hotbar" (or "Take off hotbar"). New wishes are still made only between battles; a hotbar wish
  fires in the break and counts toward the next wave's hotbar wishes.
- **The arena mends itself** as the break begins, so a wish that tore it up does not spoil the next wave: every block
  of it is put back as it was built, by the same reset as the battle's end (a hole in the floor filled down to the
  base's bedrock, holes in the prep room's floor filled, a house or a
  wall wished into it cleared to the sky, a panel button knocked off put back), and "The arena is mended for the next
  wave." is said when anything was. Of the things in it, the break tidies what a fight leaves flying or falling: arrows
  and other thrown things, lit TNT, falling blocks, lingering potion clouds, evoker fangs. It **keeps** what the
  battle's end waits for: every living creature that is not a wave monster (a player's own wished creatures and pets
  fight on with him; the battle's end clears the ones nobody owns), the displays a wished creature is drawn with, and
  dropped things and experience, which a fighter may pick up in the break (the battle's end clears or carries them as
  before).

### Wished creatures help in a battle

So a creature a player wishes into the arena never just stands about, **in a wave every wished creature in the arena
takes one battle role**, automatically, on top of whatever it is (`ArenaRoles` decides, pure and unit-tested;
`ArenaRoleKeeper` carries it out):

- **Fighter**: a creature whose own attack is at least `fighterAttack` (3, a zombie's punch), or that the game counts a
  monster, goes for the wave's nearest monster, walks to it and strikes it once a second with its own attack (a
  crit spark and a strong-hit sound on each strike). A wished monster never goes for a player.
- **Tank**: a creature that does not fight and has at least `tankHealth` (40) most health or is at least `tankWidth`
  (1.3) blocks wide, and any creature that neither fights nor walks: once a second every wave monster within
  `tauntRadius` (8) blocks that no other tank holds turns to it (an angry cloud over the monster, a shield clang), and
  while it holds any it has Resistance `tankResistance` (I, a fifth less damage) for two seconds at a time. A monster
  that gets farther than one and a half times that reach is let go.
- **Healer**: any other creature (it walks, it does not fight, it is small and soft) stays by its player and heals every
  fighter within `healRadius` (6) blocks who is hurt, by `healAmount` (2, one heart), at most once every `healSeconds`
  (4) per fighter **whatever the number of healers**: hearts over him and over it, and a soft chime.

The choice reads only what the game says about the creature (its attack damage and most health, its width, whether it
is a monster, whether it moves of its own accord): never its name, kind or what it was made from, so a creature nobody
has wished before gets a role by the same rule, and there is always one (a property test over 20,000 random creatures
and rules finds none without). Chosen the first time it is seen in a battle's wave and kept to the battle's end.

**What he sees.** When a battle starts: "Creatures you wished into the arena help you: each one is a Fighter, a Healer
or a Tank." The first time each creature takes its role in a battle, one line to the player it helps (its tamer when
the game says so, else the nearest fighter): "Your Puff is a Healer! It will keep you healthy." Above the creature, its
role's word in its colour (Fighter red, Tank aqua, Healer green), a text display that follows it, the game's own kind
of floating words a stock client draws; the creature's own name is never changed.

**Nothing lasts past the wave.** At the wave's end, in a break, when the battle ends, when the server stops, and
whenever a creature is outside the arena (in the prep room, say), its role stops completely: its sign and role tags
go, its walk stops, the Resistance it was given is taken off, and every monster a tank held is let go (its target
cleared). A role touches nothing else: no attribute, no name, nothing saved.

**Which creatures.** A creature carrying the core's tag `wishmob`: the tag the core puts on the body of every creature
a wish makes (a Builder or Apprentice make, a Magician's sculpt), which the core's own lessons tell a wish's datapack to
select creatures by, and which its client-validation playtests look for. A plain game mob that a Magician's wish
summoned carries the core's `moddy_wished` tag instead; that tag is **not** a documented handle (only the core's source
names it, as the mobs its wish-made-mob limit counts), so reading it is an approximation, labelled so in
`ArenaRoles.WISH_SUMMONED`. What would make it exact is one documented core marker for "a creature a player's wish
made", on every such body, whatever made it. A mob nobody wished (one that wandered in, or a `/summon`) has neither tag
and gets nothing; nor do the arena's own (a wave's monster, a board's words, a role's sign).

**A healer never makes a player unbeatable.** `arena_creatures` refuses heal numbers above 1 health a second for one
player from all his healers together (`healAmount / healSeconds`), and the default is half that: 0.5 a second, while one
zombie on Normal takes 3 a second from a player who stands still, so a fighter must still fight and dodge
(`ArenaCreaturesTest.aHealerNeverMakesAPlayerUnbeatable`). A heal never gives more than he is missing, and one heal at a
time per fighter whatever the number of healers.

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
| a kept thing made again (its hotbar wish, or "Make another one") | free, unlimited | **allowed** while he has some of this wave's `wishesPerWave` (5) left: each fire costs one, then that thing rests `cooldownSeconds` (10) for that player |
| a typed wish for the Magician | allowed while he has some of this prep's `magicianWishes` (3) left | refused |

- **Prep** is whenever no battle is on. **A battle** starts when the button is pressed (the countdown counts as the
  battle: a slow wish made then would land in the middle of a wave) and ends when the arena is tidy again after a win or
  a loss; then every player's Magician count starts again and every rest ends.
- **Hotbar wishes a wave**: in a battle each player may fire `wishesPerWave` (5) hotbar wishes in each wave, whichever
  wishes they are; every fire costs one (a real item he holds, such as the sword a wish made, is not a fire). The count
  starts again when a wave is cleared ("Your 5 hotbar wishes for the next wave are ready."), so the breather before a
  wave and the wave share one count. **His own bar** at the top of the screen, under the wave's, says how many he has
  left ("Your wishes this wave: 3 of 5", green, emptying as he fires; white at 0), from the countdown to the battle's
  last wave; a boss bar is what a stock client draws, always in sight, so it never fights the action bar's lines. Out of
  wishes, a fire is refused with one line in chat, once per try: "You have used your 5 hotbar wishes for this wave. You
  get 5 more when the next wave comes." The wish stays on his hotbar for the next wave. The control panel says the
  number under the start button ("Hotbar wishes: 5 a wave").
- **The hotbar**: the core puts a thing with a recipe on the hotbar from its page (My wishes, press the thing, "Add to
  hotbar"; a ready-made wish's page opens on a right click). Using it makes the thing again, with no typing and no menu:
  the same replay as "Make another one". A thing has a recipe when it came from one: every Builder and Apprentice make
  he keeps, the starter library's wishes (the watch's ready-made ones, such as the Lightning Sword) and the Magician's
  wishes he keeps as recipes. A hotbar wish stays his when he is knocked out: it is back in its slot when he comes back
  in the prep room. **The slot's sweep lasts the arena's rest**: the arena tells the core how long each hotbar wish still
  rests (`restLeft`), so after a fire in a battle the slot sweeps for the whole 10 seconds, and a click while it sweeps
  makes nothing; after a knock-out or a re-add it sweeps for what is left. In prep the core's own one-second sweep
  stands. "Make another one" pressed while its thing rests is refused in chat: "That wish is resting. It is ready again
  in 7 seconds."
- **A Magician wish is counted when the Magician picks it up**, not when it is typed: a typed wish the wish library
  answers with no model costs nothing. A counted wish that fails, is stopped, times out or is undone gives its count
  back. A wish refused for the count is never charged: "You have used all 3 Magician wishes for this prep. You get 3
  more after the next battle. Builder and Apprentice wishes are still free."
- **In a battle** a typed wish or a new make is refused: "A battle is on, so only the wishes on your hotbar work now.
  New wishes are made in the prep room before a battle." It is the one line he reads (a refused Builder make says no
  other line with it), and typed words that would go to the Apprentice are refused before its page opens.
- **A child in distress** (the core's safety layer passes the flag) is never refused and never counted.
- `/moddy wishes` says, in prep, "Magician wishes left before the next battle: 2 of 3." and that Builder and Apprentice
  wishes are free and how many hotbar wishes a wave allows; in a battle, how many he has left this wave and how long
  each rests. The watch's and the wish
  box's titles add " - 2 Magician wishes left", or " - Battle: use your hotbar".

How the arena tells the kinds apart, at the core's charge hooks, each told the core's `WishAsk` (whose wish, its kind
of magic, its recipe id, its words, distress): a **REPLAY** whose recipe its start named (a hotbar fire, "Make another
one", a recipe button, a left click on a ready-made wish in My wishes) is a kept thing made again, an instant wish in a
battle; a **Builder** or **Apprentice** make (the Builder's menus, an Apprentice tile, the Apprentice's page for typed
words, a typed change the Builder carries out) and typed words a kept recipe answers are new wishes with no model,
refused in a battle; typed words for the **Magician** are his wishes. A kept Builder or Apprentice make's hotbar wish
is a REPLAY of its recipe (`builder:<make id>`), so it fires in a battle, while a new make of the same thing is still
refused. Three hooks carry it out:

- `refuses(ask)`, asked as any wish starts, before anything runs or opens: let it through, or refuse it with the one
  line the core then says. Asking changes nothing (the core may ask again, and a menu asks while it draws).
- `charge(ask, wishId)`, once a wish served with no model really starts (after the core's rate limiter): a fire in a
  battle costs one of the wave's hotbar wishes and starts that thing's rest.
- `restLeft(ask)`, asked by the core's hotbar after every fire and whenever a hotbar wish is put in his inventory: how
  long that wish still rests, so its sweep shows the arena's rest.

A kept thing's rest is keyed by its recipe id, which a hotbar fire and "Make another one" share. The Magician's pickup
(`atPickup`) is the only place a wish reaches a model, and where a Magician wish is counted.

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
  "countdownSeconds": 5, "breatherSeconds": 15, "bossBreatherSeconds": 45, "waveSeconds": 180,
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
`minecraft:husk`), the countdown before the first wave (3 to 60 s), the break after a wave (3 to 120 s), the longer break after a boss
wave (`bossBreatherSeconds`, 3 to 300 s; without it 30 s, or the other break if that is longer) and how long
one wave may last before the battle is lost (30 to 1800 s). A wrong table refuses the file whole, in words; a change
applies from the next battle. No rule, no battle ("This arena has no waves in its rules, so a battle cannot start.").
These are the base waves: the difficulty's wave size and swaps are applied on top of them (Normal is these waves
exactly).

Optional keys set the difficulties' wave sizes, and fix or limit the control panel; without them each difficulty brings
its own wave size and **the player chooses**:

| key | value | what it does |
|---|---|---|
| `waveSizes` | an object, `{"easy": 2, "hard": 7}`: any of the four difficulties, each 1 to 10 | that difficulty's wave size (5 is these waves exactly); a difficulty not named keeps its own (3, 5, 6, 8) |
| `waveSize` | 1 to 10 | the wave size of **every** difficulty (the key of the old MONSTERS selector, read as before: it fixed the monsters, and since there is no selector now it is every difficulty's size); not with `waveSizes` |
| `maxWaveSize` | 1 to 10 | the biggest wave size any difficulty brings (the old selector's limit, read the same way) |
| `difficulty` | `easy`, `normal`, `hard`, `extreme` | fixes the difficulty, the same way |
| `maxDifficulty` | `easy`, `normal`, `hard`, `extreme` | the hardest difficulty the player may pick |
| `waveCount` | a multiple of 5 from 5 to 995, or `"endless"` | fixes the number of waves |
| `maxWaveCount` | a multiple of 5 from 5 to 995 | the most waves the player may pick (no Endless then); over 20 it is the last step (30: 5, 10, 15, 20, 30) |

A key and its `max` are not given together, nor `waveSize` and `waveSizes` (the file is refused, saying so; so is a
`waveSizes` that names something other than a difficulty or a size outside 1 to 10). The two old keys are kept rather
than refused because they are cheap to read and a world's rules file written for 0.1.5 then still works and means
nearly what it meant. A number of waves that is not a multiple
of 5 is refused ("a boss comes every 5th wave"). A saved choice above a later limit is brought down to it.

Three more optional keys:

| key | value | default | what it does |
|---|---|---|---|
| `bossEvery` | 0 (no boss waves), or 2 to 50 | 5 | a boss wave every that many waves |
| `lives` | 1 to 10 | 3 | how many times each player may be knocked out in a battle and come back |
| `generator` | `{"addPerWave": 0 to 5, "healthPerWave": 0 to 0.5, "maxHealth": 1 to 5, "kinds": [{"mob": id, "from": wave, "weight": 1 to 20}, ...]}`, each optional | 0.5, 0.04, 2.5 and the kinds above | the waves past the rules file's; a kind is checked as a wave's mob is |

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
{"id": "prep-and-battle-wishes", "type": "arena_wishes", "params": {"magicianWishes": 3, "cooldownSeconds": 10, "wishesPerWave": 5}}
```

`magicianWishes`: the Magician wishes each player may make between two battles (0 to 20; 0 means none in this arena).
`cooldownSeconds`: how long a kept thing rests, for that player, after it is fired in a battle (1 to 120).
`wishesPerWave`: how many hotbar wishes each player may fire in one wave of a battle (1 to 50); every fire costs one,
and the count starts again when a wave is cleared. Its first value, 5, is a little under what firing one wish whenever
it is ready would give in a wave of about a minute (6, at a 10-second rest), so a player picks his moments and which
wish to fire. All three are optional, and **all three first values are guesses** to be tuned by playing. A wrong value refuses the file whole, in words;
a file with no `arena_wishes` rule gets these values.

### `arena_creatures`, the wished creatures' battle roles

```json
{"id": "wished-creatures-help", "type": "arena_creatures", "params": {"fighterAttack": 3, "tankHealth": 40, "tankWidth": 1.3, "healAmount": 2, "healSeconds": 4, "healRadius": 6, "tauntRadius": 8, "tankResistance": 1}}
```

Every key is optional: `fighterAttack` (0.5 to 50), `tankHealth` (10 to 1000), `tankWidth` (0.5 to 5 blocks),
`healAmount` (0.5 to 4), `healSeconds` (2 to 20, whole), `healRadius` (2 to 16 blocks), `tauntRadius` (3 to 16 blocks),
`tankResistance` (0 to 3, whole; 0 none). A heal faster than 1 health a second (`healAmount / healSeconds`) refuses the
file. First values are guesses, to be tuned by playing.

### Where the file comes from

A world made with the World Type uses this file from the jar. A dedicated server may name its own with
`-Dmoddingfromamod.world.rules=<file>`; it then replaces the jar's rules as soon as it is read.

## The Arena Guide

`worlds/arena.json` carries a `guide.book`: the **Arena Guide**, sixteen short pages by Moddy, one idea a page (the
prep room and the gate; the control panel; START; waves and boss waves; lives; the breaks; READY; the arena mending;
making wishes; Magician wishes; the hotbar; firing a wish and its rest; wishes a wave; the creatures' roles; the
Champions board). The core turns it into a vanilla written book (a stock client reads it) and gives it on a player's
first arrival and again on `/moddy guide` (the watch's guide page). **No page types a number**: a rules number is a
placeholder the core fills in when it gives the book, from the arena's charge (`ArenaCharge.guideValues`, `ArenaGuide`):
`{arena_lives}`, `{arena_boss_every}`, `{arena_break_seconds}`, `{arena_boss_break_seconds}`,
`{arena_magician_wishes}`, `{arena_rest_seconds}`, `{arena_wishes_per_wave}`. A rule that turns a thing off (no boss
waves, no Magician wishes) leaves its key out, and the core then leaves that page out. The book given on the first
arrival lands on the hotbar; the arena moves that one copy into his bag, so the hotbar stays for his wishes.
`ArenaGuideTest` holds every page to the core's book page (14 lines of 19 characters, 16 pages, a 32-character title),
also with the largest numbers the rules allow, and fails a placeholder with no value or a page with a digit in it.

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
the carrier's flat world (`playtests/play.props` names the module), where the place is built on its base high above
the flat ground:
`1-arrive` (the prep room, facing the arena, the start button beside the gate), `2-wish-in-the-arena` (an Apprentice
make aimed at the arena floor lands), `3-wish-outside-refused` (the same make made standing on the ground outside the
place is refused),
`4-battle-won` (the button pressed, taken into the arena behind the shut gate as wave 1 comes, five waves cleared with
the sword, the fifth a boss wave, won, the arena tidied: the player kept safe), `5-battle-lost` (knocked out in wave 1
with lives left, back in the prep room while the wave goes on, taken back into the arena after a count of 3, then out
of lives: lost, back in the prep room with his sword, the arena tidied), `6-prep-and-hotbar` (in prep, a
make from the prep room lands in the arena ahead of it and one made in the arena lands there, and an Apprentice Fire
Sword goes on the hotbar from its own page in My wishes, by "Add to hotbar"; its hotbar wish is used in the battle and
makes it again, a click while its slot sweeps makes nothing, a new make is refused with the arena's words before the
Apprentice's page opens;
after the battle the hotbar wish is still there and new makes work), `7-creature-in-the-arena` (an Apprentice cat made
in the arena lands there; made standing outside the place, it is refused and nothing is left),
`8-knocked-out-keeps-hotbar` (knocked out in a battle holding a hotbar wish, with lives left, he comes back in the prep
room with it in its slot and it still fires in the battle that goes on; out of lives, lost, and it makes the sword
again in prep), `9-control-panel` (the board's blocks, two selectors and fourteen lines of words, no MONSTERS; he walks
up to it and presses each selector, looking at it and using it: DIFFICULTY down and up to each end with the first
wave's monsters following it (2, 3, 4, 5), WAVES through 5, 10, 15, 20, Endless and back, each press on the board and
his action bar; on Easy a battle's first wave is 2 husks with 12 health, on Hard 4 husks with 25, more than Easy's, then
Hard's waves to its boss wave with Rumble the Ravager; in a battle a selector is refused and the board says "Battle on:
wave 1 of 5"; after it the board keeps his settings), `10-boss-waves` (WAVES 10: boss waves at 5 and 10,
each with its named boss, its health on the boss bar and the BOSS WAVE title, generated waves between, and the win)
`11-champions` (a run lost on wave 3 puts his face, name, wave and time on the Champions board; a better run
replaces it and a worse one does not; changing the difficulty switches the board), `12-wishes-per-wave` (the panel
and `/moddy wishes` say 5 hotbar wishes a wave; in the battle his own bar says 5 of 5 and empties with each fire, the
slot sweeps for the arena's 10 seconds and a click while it sweeps makes nothing, the sixth fire is refused in one line
with the wish still on his hotbar, and when the wave is cleared the count is 5 again and the next fire costs one),
`13-intermission` (WAVES 10: after a wave a 15-second break counted down on the boss bar, READY on the board and the
arena mended, a house from prep gone, a hole in the floor filled, an arrow tidied and a creature kept; READY ends the
break in 3 seconds; after the boss wave a 45-second break, in which he puts another of his makes on his hotbar from My
wishes, presses READY, and fires it in the next wave), `14-creature-roles` (an Apprentice cat and three wished
stand-ins take their roles in wave 1, each told once and shown above it; the wolf goes for the monsters, the camel's
held monsters target it and it has Resistance, a cow nobody wished gets nothing; in the break every role, sign and
Resistance is gone; in wave 2 the sheep heals him when he is near it and not when he is ten blocks away, and taken into
the prep room it loses its role at once) and `15-arena-guide` (the Arena Guide on his first arrival, in his bag and not
on his hotbar; a copy opened and read, its lives page showing the rules' number), `16-gate-in-a-wave` (START pressed
and he waits in the prep room: the wave takes him inside, the gate is iron bars he cannot open or pass, a monster put in
the prep room goes back into the arena, the shut gate seen from the prep room; in the break the fence gate, he walks
through it and back; the next wave takes him in again; knocked out with lives left he counts 3 and is taken back in;
out of lives he stays in the prep room, and after the battle the gate is the fence gate) and `17-on-solid-ground`
(stone and bedrock under the place and grass round it; a hole torn in the arena's floor and a real TNT blast leave him
on the bedrock two blocks down, unhurt; a hole under the spawn is put back and a respawn lands on it; teleported far
below the arena he is put back in the prep room; the place seen from outside, on its base). A playtest cannot restart its game,
so the settings and the champions surviving a restart are `ArenaStateTest` (the saved record) and a dedicated
server's restart, and so is a world from 0.1.5 upgrading (its panel and its base).

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
| `ArenaPlace` | **pure**: the sizes and their checks, every box of the place and the base it stands on, the start button, the arrival spot and the floor under it, the reach, the start decision, the upgrades, what a reset puts back, whether a body is still in the place or fell out under it |
| `ArenaGate` | **pure**: the gate in a battle: shut (iron bars) only while a wave is on, who is moved in and when (at a wave's start, and a knocked-out fighter after a count), where, and what happens to a monster outside the arena |
| `ArenaWaves` | **pure**: the wave table and its checks, and what the rule fixes or limits on the control panel |
| `ArenaSettings` | **pure**: the panel's difficulty and number of waves, how a wave size scales the waves, the rules' wave sizes and limits, a selector press |
| `ArenaGenerator` | **pure**: the waves past the rules file's, made from the wave's number, and its knobs |
| `ArenaBosses` | **pure**: the boss table, which waves are boss waves, a boss wave's escort |
| `ArenaRun` | **pure**: one battle's waves as the panel set them (the rules file's, the generator's, the boss waves), wave by wave |
| `ArenaChampions` | **pure**: the champions' entries per settings (difficulty and waves), how runs rank, merging a run, migrating 0.1.5's entries, their words |
| `ArenaChampionsLayout` | **pure**: the Champions board's layout: its blocks and where each word and face floats |
| `ArenaChampionsBoard` | carries the Champions board out: its words (text displays) and faces (item displays with player heads) |
| `ArenaDifficulty` | **pure**: the four difficulties as data (wave size, health, swaps) and how each changes the waves |
| `ArenaPanel` | **pure**: the control panel's layout: its blocks, buttons and where each line of words floats and how big |
| `ArenaBoard` | carries the panel out: reads its buttons, a selector press, keeps its words (text displays) |
| `ArenaWavesRule` | `arena_waves` as a `WorldRuleType`, and reading the table back from a world's rules |
| `ArenaMatch` | **pure**: a battle as a state machine (IDLE, COUNTDOWN, WAVE, BREATHER, WON, LOST, RESETTING), the break between waves (longer after a boss wave, ended early when every fighter is READY), its boss bar (a boss's health on a boss wave) and how far it got |
| `ArenaSpawns` | **pure**: where a wave's monsters appear |
| `ArenaWords` | **pure**: every word a player reads about a battle and on the control panel |
| `ArenaBattle` | carries a battle out: the button, the fighters and their lives, spawning and counting the monsters and the boss, the boss bar and titles, recording the runs, READY in a break, the arena mended between waves, each fighter's own bar of hotbar wishes, the reset |
| `ArenaRule` | `arena_place` as a `WorldRuleType`, and reading the place back from a world's rules |
| `ArenaModule` | the world module: builds once, gives a built place its upgrades, sets the spawn, the first arrival (and the first guide book into the bag), the bases, the fallback rules, ticks the battle |
| `ArenaBuilder` | each kind of box as a block, placed; the control panel added to (or mended in) a place, and the base; the gate shut or opened; the prep room's floor and the spawn's floor mended; the reset |
| `ArenaBases` | the arena as the core's `Bases`: every player's base, home in the prep room |
| `ArenaWishPolicy` | **pure**: the two phases, which kinds of wish each lets through, the Magician count, the cooldowns, the hotbar wishes a wave |
| `ArenaWishes` | **pure**: the wish tunables and their checks |
| `ArenaWishesRule` | `arena_wishes` as a `WorldRuleType`, and reading it back from a world's rules |
| `ArenaCharge` | the core's `WishCharge` for arena worlds: sorts each wish, refuses or counts it, gives counts back, says what is left, the guide's numbers |
| `ArenaRoles` | **pure**: the wished creatures' battle roles: which creature is wished, the role its facts give (never none), whose it is, a heal, a taunt's reach |
| `ArenaAllies` | **pure**: how a Fighter picks its monster, its reach, how often and how hard it strikes |
| `ArenaCreatures` | **pure**: the roles' numbers and their checks (a heal never faster than 1 health a second) |
| `ArenaCreaturesRule` | `arena_creatures` as a `WorldRuleType`, and reading it back from a world's rules |
| `ArenaRoleKeeper` | carries the roles out in a wave: each creature's sign and one line, the Fighter's strikes, the Healer's heals, the Tank's held monsters and Resistance, and stopping all of it |
| `ArenaGuide` | **pure**: the Arena Guide's rules numbers (its placeholders), and where the first copy goes in the bag |
| `ArenaState` | the world's saved record (versioned): the built sizes, who has arrived, the upgrades it has had, whether a battle is on, the panel's settings, the champions |

**A place built before the control panel existed** gains it on its next start, once (the upgrade `control-panel`,
recorded in `data/moddyarena_place.dat` beside `start-control` and `no-natural-spawns`): over the window's glass (and
the first start control's chiseled stone brick block, whose button and sign are taken down), and only where those
blocks and the prep room's air are still as built, so nothing a player put there is replaced; otherwise the log says
so and `/moddy arena start` still works. After that, every start puts back a panel block that is missing (a button
knocked off), by the same path, which sets nothing when the panel stands whole. The panel's words are text displays
tagged `moddyarena_panel` (and one role tag each); whenever the board's chunk has its entities loaded they are made
if missing, a second copy is removed and changed words are rewritten, so a world gets them on load and never twice.
**A place built before the Champions board** gains it on its next start, once (the upgrade `champions-board`): over
the prep room's back wall, only where it is still its glass, so nothing a player put there is replaced. **A place built
before the base** (0.1.5 and earlier, in the sky) gains it on its next start, once (the upgrade `ground-base`): the
bedrock and the stone under the whole place and 2 blocks round it, and grass round the floors only where nothing
stands. **A panel built with the MONSTERS selector** (0.1.5 and earlier) loses its two unused buttons on its next start
and is laid out with two selectors, by the same path that mends a panel (its MONSTERS words are removed with the
other panel words it no longer has).

The saved record carries `v` 4 since 0.1.6: the panel's difficulty and `waveCount` (0 for Endless), and `champions`,
each entry a player's id, name, the settings key (difficulty and waves), the wave, cleared, won, ticks and a sequence
number. `v` 3 (the WAVES selector and the champions) and `v` 2 (the panel) also had the panel's monsters
(`waveSize`), and each champion's key had its monsters (`size`): reading such a record drops them, nothing else is
lost, and its champions are kept by difficulty and waves, **each player's best at each** (the best by the ranking above;
a tie keeps the first run), under the name of his latest run, each settings keeping its 20 best
(`ArenaChampions.migrate`). An older record reads what it lacks as the defaults (5 waves, no champions) and keeps
everything else.

## Not built yet

- **Unlocks**: wishes unlocked in tiers as a player wins battles.
- **Spectators**: watching a battle without being in it (today a player in the prep room during the countdown is a
  fighter).
- **More than one arena**, or more than one battle at a time.
- **"Give me one"** (a copy of a kept item) is not a wish: the arena lets it through in a battle with no rest and no
  count. (The core now tells a world which thing it is, `chargeAgain(player, kind, ask)`, so a later change can.)
- **A stronger wish costing more than one** of a wave's hotbar wishes, if play shows it is needed.

What the public core does not let a world do yet (so the arena does not do it either):

- **The hand-over: a make in the prep room going to the loadout instead of appearing.** The plan (#22): a make
  finished in the prep room (Builder, Apprentice or Magician) whose result is an item gives the real item, and a
  creature, event or building becomes a wish on his hotbar instead of appearing beside him, shown for a moment at a
  hand-over spot in the prep room. Core 1.0.9 gives a world no say in whether or where a finished make appears: a
  make's placing tools run as soon as its plan is known, and a world's hooks (its charge, its bases and reach rule)
  can only let it start or refuse it. Doing it in the arena alone would mean tearing down a thing after it appeared (an
  event cannot be taken back at all), so it waits for a small core hook: a world module answering, before a make's
  first placing tool runs, whether it lands in the world or is kept and handed over as a hotbar wish, and being told
  when one is handed over (its picture, its name, the slot or the wish chest) so the world can show it. Today a make in
  the prep room appears beside him, and he puts it on his hotbar from its page in My wishes.
