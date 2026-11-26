# Scarcity Isle

A Moddy world (mod id `moddyisle`) that runs on the public Moddy core: **a tiny island in the sky for every player,
where every wish counts.** Your goal is to grow it from almost nothing into a home.

## How to play

1. **Install** it with one command. It installs NeoForge, the Isle, the Moddy core it was built for and a
   launcher profile; it asks which Minecraft version you want and shows its plan before it changes anything.

   Mac and Linux:

   ```bash
   curl -fsSL https://github.com/jivinstev/mc-mod-version-upgrade/releases/download/player-install-v4/install.sh | bash -s -- jivinstev/Moddy-Worlds:isle
   ```

   Windows (PowerShell):

   ```powershell
   & ([scriptblock]::Create((irm https://github.com/jivinstev/mc-mod-version-upgrade/releases/download/player-install-v4/install.ps1))) jivinstev/Moddy-Worlds:isle
   ```

   Or by hand: the Moddy core jar and the Scarcity Isle jar in a NeoForge game (the Isle's
   [release notes](../../../releases) name the core it was built for).
2. **Create World**, open the **World** tab and pick **World Type: Moddy Isle**. That is all: the world switches
   itself on in that world and nowhere else.
3. When you arrive, Moddy tells you what to do first, and you get the **Isle Guide** book. Open your wish watch and
   press **What do I do now?** to see your goals and their hints.

## What it is like

- **Your own island**, with a chest of ice and lava. Melt the ice into water, pour the lava two blocks from it, and dig
  in the gap: where they meet, cobblestone appears, and it comes back every time you mine it.
- **Be careful**: keep lava away from your tree, never pour water on top of the lava, keep your last dirt, and do not
  fall off the edge: below the island is the void.
- **Grow it**: wood and saplings from your tree, grass for animals, and the Isle Guide's sky island tricks for sand,
  iron and amethyst. The Nether has a small island of its own.
- **Wishes are scarce**: you start with a few wishes and get more over time, up to a limit, and a ready-made wish costs
  half (`/moddy wishes` says how many you have; the Isle Guide gives this world's numbers). Moddy keeps gifts small:
  short flights, short powers, a stack at a time. Wishes work on your own island, not in the Nether.

The Isle's built-in data packs and their licences are listed in [`THIRD_PARTY_NOTICES.txt`](THIRD_PARTY_NOTICES.txt);
how a world jar is built, installed and played by script is in the [repository's README](../README.md).
