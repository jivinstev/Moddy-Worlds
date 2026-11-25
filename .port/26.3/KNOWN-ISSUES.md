# Known issues at Minecraft 26.3

## KI-26.3-1 (S2, upgrade-only, open): Scarcity Isle is not released on 26.3, blocked on its third-party packs

The Isle's world is made by three third-party MIT data packs (BPR02's SkyVoid collection, `isle/datapacks.lock.json`).
Their upstream has no 26.3 build yet; the newest is 26.2 (format 107.1), and the Isle on this branch pins those.

Measured 2026-11-24 in a real 26.3 client (NeoForge 26.3.0.117, core `moddingfromamod-1.0.8-mc26.3`, the Isle jar of
`neoforge-26.3` @ 6e92f71), each pack enabled in-game with `/datapack enable`:

| pack | 26.2 build | on 26.3 |
|---|---|---|
| Standard SkyBlock (void world, starter island) | 2.1.8 | loads, no error |
| Sky Void Additions | 1.6.0 | registry loading errors: `No key type` in its advancement conditions, predicates and loot conditions (the 26.3 condition syntax), then its wandering-trader tags fail |
| SkyBlock Advancements (read by the Isle's challenge ladder) | 1.0.13 | registry loading errors: `minecraft:nether/find_bastion` fails to parse |

So an Isle world on 26.3 would fail its data-pack load. The Isle jar builds and passes its unit tests here (129/0), but
it is not released on 26.3. The Arena does not depend on these packs and is released on 26.3.

**It closes when** BPR02 publishes 26.3 builds (re-pin `isle/datapacks.lock.json` and release), or when the owner decides
to ship converted copies of the two packs that fail (their MIT licence allows it; this repository's rule that packs are
unpacked unmodified would change).

## KI-26.3-2 (S2, upgrade-only, open): on 26.3, the "My wishes" button in the Make a Wish dialog opens nothing in playtests

Arena playtest 8 (`8-knocked-out-keeps-hotbar`) fails at step 2 on 26.3, the same way in three runs (2026-11-25, real
26.3 client, NeoForge 26.3.0.117, `arena-0.1.4-mc26.3` with `moddingfromamod-1.0.9-mc26.3`): the harness opens the
core's Make a Wish dialog (`MultiButtonDialogScreen`), clicks "📚 My wishes" (mouseClicked + mouseReleased), and the
"My wishes" page never opens; the server logs no dialog action. The same step passes on 1.21.8 to 26.2.

The dialog and its button belong to the core, not the Arena, so the cause is in the core's dialog on 26.3 or in the
playtest harness's dialog click on 26.3. Not yet checked by hand in a client with a person clicking.

**It closes when** the core or the harness is fixed on 26.3 and playtest 8 passes there.

## KI-26.3-3 (S4, environment, open): playtests 4 and 9 cannot pass in this test container on 26.3

On 26.3 the client's authlib logs a `[WARN] RetryableFetch Failed to connect` to `discovery.minecraftservices.com`
in the middle of a battle step. The container's network does not allow that host, and the harness counts the line as a
new error, so `4-battle-won` (step 5) and `9-control-panel` (step 4) fail in all three runs. Every other line of those
steps passes. This is the test container, not the Arena; the test text is unchanged.

**It closes when** the playtests run where that host is reachable, or the harness ignores authlib's offline warnings.

## KI-26.1-1 (S3, upgrade-only, inherited from neoforge-26.1.2)

A world made before 26.1 keeps its claim record and the Arena's place record at `data/<modid>_*.dat`, which 26.1 no
longer reads. The claim still holds through the marker pack, and the Arena's place record starts fresh.
