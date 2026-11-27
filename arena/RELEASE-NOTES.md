## What's new in Moddy Arena 0.1.8

- **The arena wishes its monsters.** Each wave's monsters rise from red wish pillars and grow with the battle: bigger, stronger and faster as you clear waves, never stronger than the strongest creature you may wish at that point. One in four is a glowing shadow champion (a rock golem, an ogre, a yeti, a raptor or a T-Rex, different each battle), and champions fight on the monsters' side. Bosses are wished too. It is all in the world's rules file (`arena_wished_waves`), and can be turned off there.
- **Arenas in any world.** An operator can build an arena in a world you already have, several in one world: `/moddy arena create <name>`, then `list`, `go`, `start` and `remove`. Building never goes over a player's things: it refuses, and says what is in the way, over a chest, a bed or another block that holds things, another player, a base or island, or another arena. Inside a built arena its wishes follow the arena's rules, the prep room included: the wish ladder's limits, the hand-over, the hotbar rule in a battle. Removing an arena leaves its blocks standing.
- **Settings in game.** `/moddy arena config` shows and changes the wait between rounds, the countdown, the break after a boss and a wave's time limit, for all arenas or one; an operator changes them, never during a battle they would change.
- **Knocked out, back in the prep room.** A fighter knocked out in a battle comes back in that arena's prep room, even with a bed elsewhere, and keeps his place in the battle; your bed is never changed.
- Built for Moddy core 1.0.12.

## What's new in Moddy Arena 0.1.7

- **What you make in the prep room comes to you.** A make finished in the prep room (Builder, Apprentice or Magician) no longer appears beside you: it shows for a moment on the gold hand-over spot in the prep room's floor, then flies to you. A thing you hold, like a sword, is a real item in your inventory; a creature, a ride, a building or an event is a wish on your hotbar, to use in the arena. One line says what it is and which slot it went to (or your Wish Bag, when the hotbar is full), and the first time a longer line explains the difference. A make you start while standing in the arena lands there, as before. A world made with an earlier version gets the hand-over spot the first time it is loaded with 0.1.7.
- **Strong wishes are never refused.** A creature fired from your hotbar in a battle is made, held to the battle's limits at first (smaller, weaker) and growing as you clear waves. The Builder's "How strong" is never limited: what it makes is held by health and hits instead.
- **Honest hotbar notices.** When you put a wish on your hotbar, the line about it being held back at first only promises what really comes back as you clear waves.
- Built for Moddy core 1.0.11.

## What's new in Moddy Arena 0.1.6

- **The difficulty alone sets the monsters.** The control panel's MONSTERS selector is gone: pick the difficulty and the number of waves, and the difficulty decides how many monsters each wave brings. The Champions board keeps its runs per difficulty and number of waves. A world saved by 0.1.5 keeps its champions, each player's best at each difficulty and number of waves.
- **The gate shuts during a wave.** While a wave is on, the gateway is iron bars: nobody gets in or out, and bars broken in a wave are put back within a second. When a wave starts, every fighter who is not in the arena is taken just inside the gate ("The gate is shut. Fight!"). Between waves, and when no battle is on, it is the fence gate again, so you can go back to the prep room in a break to change your hotbar.
- **Solid ground under the arena.** The arena always stands on its own base of stone and bedrock, so a blast through the floor drops you 2 blocks at most, never out of the world. A world made with an earlier version gets the base the first time it is loaded with 0.1.6.

**Known issue:** a player in Creative who is in the place when a battle starts still counts as one of its fighters, and their run still appears on the Champions board. Play the Arena in Survival.
