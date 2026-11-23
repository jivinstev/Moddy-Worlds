package com.moddingfromamod.isle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.moddingfromamod.marshal.PlayerKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import com.moddingfromamod.world.Base;
import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.WorldModuleHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>One island per child, on a world that has islands on</b> — the game-facing half.
 *
 * <p>On a child's first join the pure {@link IslandRegistry} hands out the next free grid cell; this
 * places a copy of the starter island there ({@link IslandPlacer}), records it in the world store
 * ({@link IslandStore}), sets the child's respawn point on it and moves him onto it. A re-join finds
 * the same record and changes nothing. A respawn that lands anywhere but his own cell (a broken bed
 * sends a player to the world spawn, which is the arrival island and nobody's) is put back on his
 * island, so falling into the void always returns him home.
 *
 * <p><b>Why arrival waits for the pack.</b> Standard SkyBlock places the spawn island when the FIRST
 * player joins, and then runs {@code tp @a ~0.5 66 ~0.5} — which would pull the
 * first child straight back off his own island. So in a world with the pack an arrival is queued until
 * the pack's {@code $initialized} score says it has run, plus a few ticks; without the pack (a GameTest,
 * a dev world) it is immediate. The wait is capped, so a broken pack cannot strand a child.
 *
 * <p>An INSTANCE, with the one the running world uses in {@link #live()}: GameTests make their own on
 * their own store file and grid, because GameTests in one batch share every static. Off (a
 * world that is not an Isle), {@link #live()} is null and nothing here runs.
 *
 * <p><b>Scarcity Isle's provider of {@link Bases}</b>: an island is the Isle's base. The
 * reach rule and respawn-home ask {@link Bases} and never this class, and the server's
 * lifecycle reaches it through {@link IsleModule} — the {@code WorldModule} the Isle's manifest
 * names — rather than through event handlers of its own.
 */
public final class IslandService implements Bases {

    private static final Logger LOGGER = LoggerFactory.getLogger("moddingfromamod/islands");

    /** Ticks to wait after the pack says it initialised, for its scheduled tp @a to have run. */
    static final int AFTER_PACK_TICKS = 10;
    /** The longest an arrival waits for the pack before going anyway. */
    static final int MAX_WAIT_TICKS = 400;
    // There is deliberately NO longest wait for an island's chunks. The old cap, 600 ticks,
    // placed the island anyway and loaded its chunks INLINE: measured a 40-player burst freeze
    // every player 6x (7.6 s) on 8 cores and the watchdog crash the server on 4 (measured
    // §4.1). He waits on the arrival island instead, and is told so (IslandWait).

    /**
     * Our own ticket: it expires after the wait and is never saved. Since 1.21.5 a ticket type is a plain
     * record that needs no registry id unless it is persisted (#1786; mc-mod-version-upgrade's Tickets).
     */
    private static final net.minecraft.server.level.TicketType ISLAND_TICKET =
            new net.minecraft.server.level.TicketType(IslandWait.TICKET_LIFESPAN_TICKS, false,
                    net.minecraft.server.level.TicketType.TicketUse.LOADING_AND_SIMULATION);
    private static final ResourceLocation PACK_LOAD =
            ResourceLocation.fromNamespaceAndPath("skyvoid_worldgen", "v2.0/load");

    private static volatile IslandService live;

    private final MinecraftServer server;
    private final IslandStore store;
    private final IslandRegistry registry;
    private final boolean waitForPack;
    private final List<Pending> pending = new ArrayList<>();
    /** The template's footprint, read once (the jar's file never changes under a running server). */
    private net.minecraft.core.Vec3i templateSize;
    /**
     * Islands placed while a chunk they touch was NOT loaded — i.e. a placement that loaded chunks
     * inline on the server thread. Zero by construction; a GameTest and the capacity
     * harness hold it there.
     */
    private int placedUnready;

    /** A first join waiting for its island's chunks, then (in a pack world) for the pack. */
    private static final class Pending {
        final UUID player;
        final int queuedAt;
        boolean placed;
        /** Holds the head-of-queue ticket ({@link IslandWait#escalate}). */
        boolean escalated;

        Pending(UUID player, int queuedAt) {
            this.player = player;
            this.queuedAt = queuedAt;
        }
    }

    private IslandService(MinecraftServer server, IslandStore store, IslandRegistry registry,
                          boolean waitForPack) {
        this.server = server;
        this.store = store;
        this.registry = registry;
        this.waitForPack = waitForPack;
    }

    /** The running world's service, or null when islands are off here. */
    public static IslandService live() {
        return live;
    }

    /** Set by {@link IsleModule} when the running world's islands open and close. */
    static void setLive(IslandService s) {
        live = s;
    }

    /**
     * Rest ONE island by its owner id, now — the operator's tool for an island nobody can reach (step 8
     * found one: a login that was not forwarded claimed an island under a name's old id, #1766). The same
     * retire an idle Crafter's island gets (its cell is never handed out again), with the same guards:
     * never anyone online, seated (on the list) or an operator. Returns "rested", "absent" or the refusal.
     */
    public String restOne(UUID owner, long nowMs) {
        if (owner == null) {
            return "absent";
        }
        var list = server.getPlayerList();
        var profile = new com.mojang.authlib.GameProfile(owner, "");
        if (list.getPlayer(owner) != null) {
            return "refused: that player is online";
        }
        if (list.getWhiteList().isWhiteListed(profile) || list.isOp(profile)) {
            return "refused: that id holds a seat or is an operator";
        }
        Island i = registry.retire(owner, nowMs);
        if (i == null) {
            return "absent";
        }
        store.save(registry);
        LOGGER.info("island guard: the operator rested island cell {}, {} (owner {}); the cell is never reused",
                i.cellX(), i.cellZ(), owner);
        return "rested";
    }

    // ── #2179: a fresh island when he asks for one ─────────────────────────────────────────────

    /** The least time between two fresh starts, by default a day. */
    public static final long FRESH_GAP_MS = 24L * 3600_000L;

    /** What {@link #startFresh} answers. */
    public enum Fresh { STARTED, NONE, TOO_SOON }

    /**
     * #2179: he asked to start again. His island RESTS, exactly as an idle one does (its cell is never
     * handed to anyone, it still holds what he built), and a fresh one is claimed in a new cell and placed by
     * the ordinary tick, which moves him onto it and sets his respawn point there. Once per
     * {@link #FRESH_GAP_MS}, counted from his last island's rest. What else is cleared (his bag, his goals)
     * is the caller's ({@code IslandFresh}); this is the island.
     */
    public Fresh startFresh(ServerPlayer p, long nowMs) {
        UUID id = p.getUUID();
        Island old = registry.islandOf(id);
        if (old == null || IslandRegistry.isService(old) || !PlayerKind.isChild(id)) {
            return Fresh.NONE;
        }
        Long last = registry.lastRetiredAt(id);
        if (last != null && nowMs - last < FRESH_GAP_MS) {
            return Fresh.TOO_SOON;
        }
        registry.retire(id, nowMs);
        store.save(registry);
        LOGGER.info("{} asked for a fresh island: cell {}, {} rests (never reused)", p.getScoreboardName(),
                old.cellX(), old.cellZ());
        arrive(p, false);
        return Fresh.STARTED;
    }

    /** When his newest rested island rested, or null. */
    public Long lastRetiredFor(UUID id) {
        return registry.lastRetiredAt(id);
    }

    /** Hours until he may start fresh again (rounded up), 0 when he may now. */
    public int freshInHours(UUID id, long nowMs) {
        Long last = registry.lastRetiredAt(id);
        if (last == null || nowMs - last >= FRESH_GAP_MS) {
            return 0;
        }
        return (int) Math.ceil((FRESH_GAP_MS - (nowMs - last)) / 3600_000.0);
    }

    /** Step 8 (#1766): does this id own an island or a last-seen time on this world? */
    public boolean holdsAnythingOf(UUID id) {
        return registry.holdsAnythingOf(id);
    }

    /** Step 8 (#1766): move his island to his key, in memory and on disk. Whether anything moved. */
    public boolean rekey(UUID old, UUID neu) {
        if (!registry.rekey(old, neu)) {
            return false;
        }
        store.save(registry);
        return true;
    }

    /** The world's store file. */
    public static Path storeFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("wishes").resolve(IslandStore.FILE);
    }

    /**
     * Open islands over a store. Returns null when the store refuses (it could not be read): handing
     * out islands from an empty registry would give every child a second island on a cell someone
     * else already has.
     */
    public static IslandService open(MinecraftServer server, IslandGrid grid, Path file, boolean waitForPack) {
        IslandStore store = new IslandStore(file);
        IslandStore.Loaded loaded = store.load(grid);
        if (loaded.refused() != null) {
            return null;
        }
        return new IslandService(server, store, loaded.registry(), waitForPack);
    }

    // ── the running world (reached through IsleModule since ) ─────────────────────────────

    /**
     * Open the running world's islands, or return null when they are off here (no spacing, a bad
     * spacing, a store that refused). What {@link IsleModule#start} does at server start.
     */
    static IslandService startLive(MinecraftServer server) {
        live = null;
        int spacing = IslandsConfig.spacing();
        if (spacing <= 0) {
            return null;
        }
        IslandGrid grid;
        try {
            grid = IslandGrid.of(spacing);
        } catch (IllegalArgumentException e) {
            LOGGER.error("islands are ON but {}={} is not a usable spacing ({}); no islands will be "
                    + "handed out", IslandsConfig.PROPERTY, spacing, e.getMessage());
            return null;
        }
        int vd = server.getPlayerList().getViewDistance();
        if (spacing < IslandGrid.minSpacing(vd)) {
            LOGGER.warn("islands {} blocks apart can be SEEN from each other at view distance {} (needs "
                    + ">= {})", spacing, vd, IslandGrid.minSpacing(vd));
        }
        boolean pack = server.getFunctions().get(PACK_LOAD).isPresent();
        live = open(server, grid, storeFile(server), pack);
        if (live != null) {
            // Load (and DataFixer-upgrade) the template now: measured ~1.1 s the first time, which
            // would otherwise be paid on the server thread by the first child to arrive.
            long t0 = System.nanoTime();
            boolean ok = server.overworld().getStructureManager().get(IslandPlacer.TEMPLATE).isPresent();
            LOGGER.info("starter island template {} in {} ms", ok ? "loaded" : "MISSING",
                    (System.nanoTime() - t0) / 1_000_000);
            LOGGER.info("islands ON: {} island(s), {} blocks apart{}", live.registry.childCount(),
                    live.registry.grid().spacing(), pack ? ", arrivals wait for Standard SkyBlock" : "");
        }
        return live;
    }

    // ── the work ─────────────────────────────────────────────────────────────────────────────────

    /**
     * A player joined. A child gets his island; a camera or other service client gets nothing.
     * Returns the island (for a first join, recorded now and BUILT a few ticks later — see
     * {@link #tick()}), or null for no island.
     *
     * <p>⚠ Nothing here generates a chunk. The first version placed the island right here and
     * measured <b>7.7 s on the server thread</b> (a stock client):
     * placing blocks in chunks nobody has visited generates them synchronously, so every new child
     * froze every other child. The chunks are now requested by ticket, generated off-thread, and
     * the island is placed on the first tick they are all loaded.
     */
    public Island arrive(ServerPlayer p) {
        return arrive(p, true);
    }

    /** {@link #arrive}; {@code sayRested} false for a fresh start he asked for (#2179), which has its own words. */
    private Island arrive(ServerPlayer p, boolean sayRested) {
        if (!PlayerKind.isChild(p.getUUID())) {
            return null;
        }
        long now = System.currentTimeMillis();
        boolean rested = sayRested && registry.islandOf(p.getUUID()) == null && registry.everRetired(p.getUUID());
        IslandRegistry.Claim claim = registry.claim(p.getUUID(), now);
        Island island = claim.island();
        registry.seen(p.getUUID(), now);                 // #1785 step 7: the reclaim's clock
        if (rested && claim.isNew()) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    IslandReclaim.welcomeBack(reclaimDays())).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        if (!claim.isNew()) {
            store.save(registry);
            // A returning child is left where he logged out — unless that is the ARRIVAL island
            // (the spawn cell), which is nobody's: then he goes home.
            int[] c = registry.grid().cellOf(p.getX(), p.getZ());
            if (p.level().dimension() == Level.OVERWORLD && c[0] == 0 && c[1] == 0) {
                moveHome(p, island);
            }
            return island;
        }
        store.save(registry);
        // His respawn point moves to his island WHEN HE DOES (moveHome), not now: until the island
        // stands, a respawn there would put him in the void — and reading that spot loads its chunk.
        requestChunks(island);
        synchronized (pending) {
            pending.add(new Pending(p.getUUID(), server.getTickCount()));
        }
        LOGGER.info("{} gets island #{} at {} (cell {}, {})", p.getScoreboardName(), registry.childCount(),
                origin(island), island.cellX(), island.cellZ());
        return island;
    }

    /** #2542: is any arrival still waiting for its island (the pre-generation gives way to it)? */
    public boolean arrivalsWaiting() {
        synchronized (pending) {
            return !pending.isEmpty();
        }
    }

    /** True once this island's blocks are in the world. */
    public boolean built(UUID child) {
        synchronized (pending) {
            for (Pending q : pending) {
                if (q.player.equals(child) && !q.placed) {
                    return false;
                }
            }
        }
        return registry.islandOf(child) != null;
    }

    /** True once this child has been moved onto his island (or had nowhere to be moved). */
    public boolean settled(UUID child) {
        synchronized (pending) {
            for (Pending q : pending) {
                if (q.player.equals(child)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * After a respawn: anywhere but his own cell (the world spawn, say) is put right. Since * the rule is the general one — {@link WorldModuleHost#sendHomeIfAway} asks {@link Bases} — and
     * this is the island's name for it, which the GameTest drives.
     */
    public void respawned(ServerPlayer p) {
        WorldModuleHost.sendHomeIfAway(p, this);
        WorldModuleHost.fallHomeIfUnsafe(p, this);
    }

    /**
     * One server tick: place the islands whose chunks are ALREADY loaded (at most
     * {@link IslandWait#PLACEMENTS_PER_TICK}), then move each child whose island stands (and, in a pack
     * world, once the pack's first-join tp has run). Public so a GameTest drives the SAME path the
     * running world's tick does.
     *
     * <p>⚠ Never places an island whose chunks are not loaded, however long it has waited:
     * that loads them inline on the server thread, and every player freezes while it does. A child
     * whose island is slow waits on the arrival island, is told so, and his chunks' ticket is kept
     * alive: the island's own chunks, widened to rank ahead of every view distance for the oldest
     * {@link IslandWait#HEAD} only ({@link IslandWait#TICKET_DISTANCE} says why not for all).
     */
    public void tick() {
        if (server.getTickCount() % GUARD_EVERY_TICKS == 0) {
            guardServiceIsland();
            guardForeignIslands(com.moddingfromamod.world.WorldPeople.current().islandGuard());
        }
        if (!reclaimedOnce || server.getTickCount() % RECLAIM_EVERY_TICKS == 0) {
            reclaimedOnce = true;
            reclaimIdle(System.currentTimeMillis(), com.moddingfromamod.world.WorldCrafters.current().open());
        }
        List<Pending> due = new ArrayList<>();
        List<UUID> nudge = new ArrayList<>();
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            ServerLevel level = server.overworld();
            boolean ready = !waitForPack || packReady();
            int budget = IslandWait.PLACEMENTS_PER_TICK;
            int rank = 0;                               // among the islands still waiting, oldest first
            Iterator<Pending> it = pending.iterator();
            while (it.hasNext()) {
                Pending q = it.next();
                Island island = registry.islandOf(q.player);
                if (island == null) {
                    it.remove();
                    continue;
                }
                int waited = server.getTickCount() - q.queuedAt;
                if (!q.placed) {
                    if (budget <= 0 || !chunksReady(level, island)) {
                        boolean head = IslandWait.escalate(rank++, waited);
                        if (IslandWait.refreshTicket(waited) || (head && !q.escalated)) {
                            // the ticket expires; keep it alive (and widen it at the head of the queue)
                            requestChunks(island, head ? IslandWait.HEAD_TICKET_DISTANCE : IslandWait.TICKET_DISTANCE);
                        }
                        q.escalated |= head;
                        if (IslandWait.nudge(waited)) {
                            nudge.add(q.player);
                        }
                        continue;
                    }
                    budget--;
                    place(level, island, waited);
                    q.placed = true;
                }
                if (ready || waited >= MAX_WAIT_TICKS) {
                    due.add(q);
                    it.remove();
                }
            }
        }
        for (UUID id : nudge) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Your very own island is still being made. Hang on here, it won't be long!")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        for (Pending q : due) {
            ServerPlayer p = server.getPlayerList().getPlayer(q.player);
            Island island = registry.islandOf(q.player);
            if (p != null && island != null) {
                com.moddingfromamod.compat.Respawn.set(p, Level.OVERWORLD, stand(island), 0f, true, false);
                moveHome(p, island);
            }
        }
    }

    private void place(ServerLevel level, Island island, int waited) {
        if (!chunksReady(level, island)) {
            placedUnready++;                            // unreachable: tick() checks first
        }
        long t0 = System.nanoTime();
        if (!IslandPlacer.place(level, origin(island), IslandRegistry.isService(island))) {
            LOGGER.error("the starter island template {} is missing from the jar; the island at {} "
                    + "was recorded but NOT built", IslandPlacer.TEMPLATE, origin(island));
        }
        // "chunks loaded" tells this from an older forced
        // placement, which logged the same line without it.
        LOGGER.info("island at {} placed in {} ms, {} ticks after the claim (chunks loaded)", origin(island),
                (System.nanoTime() - t0) / 1_000_000, waited);
    }

    /** Islands placed while a chunk they touch was not loaded. Zero by construction; GameTests assert it. */
    public int placedUnready() {
        return placedUnready;
    }

    /** True while this child's island is still waiting for its chunks (nothing is placed for it yet). */
    public boolean waitingForChunks(UUID child) {
        synchronized (pending) {
            for (Pending q : pending) {
                if (q.player.equals(child) && !q.placed) {
                    return true;
                }
            }
        }
        return false;
    }

    /** For a GameTest: pretend every waiting arrival was queued {@code ticks} earlier. */
    void ageForTest(int ticks) {
        synchronized (pending) {
            for (int k = 0; k < pending.size(); k++) {
                Pending q = pending.get(k);
                Pending older = new Pending(q.player, q.queuedAt - ticks);
                older.placed = q.placed;
                pending.set(k, older);
            }
        }
    }

    /**
     * The chunks the template touches, asked for by a short-lived ticket: generated off-thread. Its
     * radius is {@link IslandWait#TICKET_DISTANCE}, the island's own chunks and no more (measured).
     */
    private void requestChunks(Island island) {
        requestChunks(island, IslandWait.TICKET_DISTANCE);
    }

    private void requestChunks(Island island, int distance) {
        ServerLevel level = server.overworld();
        int[] span = span(level, island);
        for (int cx = span[0]; cx <= span[2]; cx++) {
            for (int cz = span[1]; cz <= span[3]; cz++) {
                var pos = new net.minecraft.world.level.ChunkPos(cx, cz);
                level.getChunkSource().addTicketWithRadius(ISLAND_TICKET, pos, distance);
            }
        }
    }

    /** Every chunk the placement reads is loaded NOW — asked without loading anything. */
    boolean chunksReady(ServerLevel level, Island island) {
        int[] span = span(level, island);
        for (int cx = span[0]; cx <= span[2]; cx++) {
            for (int cz = span[1]; cz <= span[3]; cz++) {
                // getChunkNow, not hasChunk: hasChunk only asks the ticket level, so it says yes the
                // tick the ticket is added — measured, and the placement then generated inline.
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The chunks an island's placement touches (template box plus its edge ring). */
    int[] span(ServerLevel level, Island island) {
        if (templateSize == null) {
            templateSize = IslandPlacer.size(level);
        }
        BlockPos corner = IslandPlacer.corner(origin(island));
        // A missing template places nothing; 16 x 16 still asks for the chunks it would have touched.
        int sx = templateSize == null ? 16 : templateSize.getX();
        int sz = templateSize == null ? 16 : templateSize.getZ();
        return IslandWait.chunkSpan(corner.getX(), corner.getZ(), sx, sz);
    }

    /** The pack has placed the spawn island and run its tp @a, a few ticks ago. */
    private int readySince = -1;

    private boolean packReady() {
        var board = server.getScoreboard();
        var obj = board.getObjective("skyvoid_worldgen");
        if (obj == null) {
            return false;
        }
        var info = board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("$initialized"), obj);
        if (info == null || info.value() < 1) {
            return false;
        }
        if (readySince < 0) {
            readySince = server.getTickCount();
        }
        return server.getTickCount() - readySince >= AFTER_PACK_TICKS;
    }

    private void moveHome(ServerPlayer p, Island island) {
        BlockPos at = stand(island);
        com.moddingfromamod.world.WorldModuleHost.goHome(p, this, server.overworld(), at.getX() + 0.5, at.getY(),
                at.getZ() + 0.5);
    }

    // ── #1637: the canary's island (the SERVICE island) ─────────────────────────────────────────

    /** How often the service island is checked for a player who should not be on it. */
    static final int GUARD_EVERY_TICKS = 20;

    /** Players already told and reported, so one stay on it is ONE page, not one a second. */
    private final java.util.Set<UUID> reportedOnService = new java.util.HashSet<>();

    /**
     * The canary's island: claimed once in the fixed far cell ({@link IslandRegistry#claimService})
     * and placed BARE (an empty chest, no sign) by the ordinary tick — chunks by ticket, nothing
     * generated inline. The canary never joins, so this is called at server start
     * ({@link IsleModule#start}), not from {@link #arrive}. Idempotent.
     */
    public Island ensureServiceIsland(UUID owner) {
        IslandRegistry.Claim claim;
        try {
            claim = registry.claimService(owner, System.currentTimeMillis());
        } catch (IllegalStateException e) {
            LOGGER.error("the canary's island cannot be made: {}", e.getMessage());
            return null;
        }
        if (claim.isNew()) {
            store.save(registry);
            requestChunks(claim.island());
            synchronized (pending) {
                pending.add(new Pending(owner, server.getTickCount()));
            }
            LOGGER.info("the canary's island is at {} (the service cell {}, {}) — no child's",
                    origin(claim.island()), claim.island().cellX(), claim.island().cellZ());
        }
        return claim.island();
    }

    /** The service island, or null when there is none. */
    public Island serviceIsland() {
        for (Island i : registry.all()) {
            if (IslandRegistry.isService(i)) {
                return i;
            }
        }
        return null;
    }

    /**
     * Keep the service island's chunks loaded while the canary's wish runs: nobody stands within view
     * of a cell 49 000 blocks out, and a tool placing a block in an unloaded chunk fails. A ticket
     * like an arrival's (it expires by itself), renewed by its caller while the wish is lent.
     */
    public void holdServiceChunks() {
        Island i = serviceIsland();
        if (i != null) {
            requestChunks(i);
        }
    }

    /**
     * Put the service island back to its bare starting state before a canary wish, so nothing piles
     * up there week after week: the template's footprint, four blocks around it and twelve above,
     * cleared to air, then the template placed bare again. Only when its chunks are loaded — never
     * generated inline; a reset skipped is a torch left, nothing worse. Returns whether it ran.
     */
    public boolean resetServiceIsland() {
        Island i = serviceIsland();
        ServerLevel level = server.overworld();
        if (i == null || !chunksReady(level, i)) {
            return false;
        }
        if (templateSize == null) {
            templateSize = IslandPlacer.size(level);
        }
        if (templateSize == null) {
            return false;
        }
        BlockPos corner = IslandPlacer.corner(origin(i));
        for (int x = -4; x < templateSize.getX() + 4; x++) {
            for (int z = -4; z < templateSize.getZ() + 4; z++) {
                for (int y = 0; y < templateSize.getY() + 12; y++) {
                    BlockPos at = corner.offset(x, y, z);
                    if (!level.getBlockState(at).isAir()) {
                        level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
        IslandPlacer.place(level, origin(i), true);
        return true;
    }

    /**
     * THE GUARD: no child stays on the service island, however he got there. Islands are kept apart
     * only by 1 024 blocks of void and nothing stops a player on another island (protection is for
     * WISHES — BaseReach); the far cell makes the trip long (some 6 100 blocks of Nether bridge), and
     * this makes it pointless: within a second he is sent back to his own island, told why, and a
     * {@code MODDY-ALERT canary island entered} line is for an operator's log watcher. By WHERE he is,
     * not how he came — a portal, pearls, an elytra and a teleport all end the same way. Not a child
     * (a camera client photographing the canary's wish) is never touched; an operator is reported
     * and left there, so the owner can look for himself.
     */
    void guardServiceIsland() {
        Island svc = serviceIsland();
        if (svc == null) {
            return;
        }
        var cell = registry.grid().cell(svc.x(), svc.z());
        java.util.Set<UUID> onItNow = new java.util.HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != Level.OVERWORLD || !cell.contains(p.getX(), p.getZ())
                    || !PlayerKind.isChild(p.getUUID())) {
                continue;
            }
            onItNow.add(p.getUUID());
            boolean op = p.hasPermissions(2);
            if (reportedOnService.add(p.getUUID())) {
                LOGGER.warn("MODDY-ALERT canary island entered: {} at {} {} {} — {}", p.getScoreboardName(),
                        (int) p.getX(), (int) p.getY(), (int) p.getZ(),
                        op ? "an operator, left there" : "sent home");
            }
            if (op) {
                continue;
            }
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "That's Moddy's own test island, not one to visit — back to your island you go!")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            Island home = registry.islandOf(p.getUUID());
            if (home != null && !IslandRegistry.isService(home) && built(p.getUUID())) {
                moveHome(p, home);
            } else {
                BlockPos spawn = server.overworld().getSharedSpawnPos();
                com.moddingfromamod.compat.Teleports.player(p, server.overworld(), spawn.getX() + 0.5,
                        spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
            }
        }
        reportedOnService.retainAll(onItNow);     // a later visit is reported again
    }

    // ── #1785 step 4: every island is private ──────────────────

    /** Players already told this stay, so one visit is one line, not one a second. */
    private final java.util.Set<UUID> toldForeign = new java.util.HashSet<>();

    /**
     * THE PRIVATE-ISLAND GUARD (#1785 step 4): on a world whose {@code people.islandGuard} is on, a child
     * standing in ANOTHER child's island cell — in the overworld, or the Nether mapped back — is sent
     * home within a second and told why. By WHERE he is, not how he came (a bridge, a portal, pearls).
     * The spawn cell and unclaimed sky are nobody's; the canary's cell has its own guard; an operator is
     * left alone. Public so a GameTest drives the same path the tick does.
     */
    public void guardForeignIslands(boolean on) {
        if (!on) {
            toldForeign.clear();
            return;
        }
        java.util.Set<UUID> awayNow = new java.util.HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!PlayerKind.isChild(p.getUUID()) || p.hasPermissions(2)) {
                continue;
            }
            double[] col = IslandGrid.overworldColumn(p.level().dimension().location().toString(), p.getX(), p.getZ());
            if (col == null) {
                continue;
            }
            Island at = registry.at(col[0], col[1]);
            if (at == null || IslandRegistry.isService(at) || p.getUUID().equals(at.owner())) {
                continue;
            }
            awayNow.add(p.getUUID());
            if (toldForeign.add(p.getUUID())) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "That's someone else's island — every island here is private. Back to yours you go!")
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
                LOGGER.info("island guard: {} was in {}'s cell — sent home", p.getScoreboardName(), at.owner());
            }
            Island home = registry.islandOf(p.getUUID());
            if (home != null && !IslandRegistry.isService(home) && built(p.getUUID())) {
                moveHome(p, home);
            } else {
                BlockPos spawn = server.overworld().getSharedSpawnPos();
                com.moddingfromamod.compat.Teleports.player(p, server.overworld(), spawn.getX() + 0.5,
                        spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
            }
        }
        toldForeign.retainAll(awayNow);
    }

    // ── #1785 step 7: an idle Wish Crafter's island rests ─────────────────────────────────────────

    /** How often the reclaim looks: every six hours of server time, and once at the first tick. */
    static final int RECLAIM_EVERY_TICKS = 20 * 3600 * 6;

    private boolean reclaimedOnce;

    static int reclaimDays() {
        return com.moddingfromamod.world.WorldCrafters.current().islandDays();
    }

    /** A player's leave is "seen" too: the reclaim counts from the LAST time he was here. */
    public void left(ServerPlayer p) {
        if (p != null && registry.islandOf(p.getUUID()) != null) {
            registry.seen(p.getUUID(), System.currentTimeMillis());
            store.save(registry);
        }
    }

    /**
     * Rest every idle CRAFTER's island (IslandReclaim). Only where the world's door is open to Wish
     * Crafters: on a world where everyone is on the list, nothing here ever rests. Never a Magician's, an
     * operator's or anyone online. Returns how many rested. Public so a GameTest drives the tick's path.
     */
    public int reclaimIdle(long nowMs, boolean open) {
        if (!open) {
            return 0;
        }
        var list = server.getPlayerList();
        java.util.function.Predicate<UUID> keep = id -> {
            var profile = new com.mojang.authlib.GameProfile(id, "");
            return list.getPlayer(id) != null || list.getWhiteList().isWhiteListed(profile) || list.isOp(profile);
        };
        int n = 0;
        for (UUID owner : IslandReclaim.due(registry.all(), registry.seen(), keep, nowMs, reclaimDays())) {
            Island i = registry.retire(owner, nowMs);
            if (i != null) {
                n++;
                // #2464: his standalone packs and scoped rules go with his island (attached ones go with their things)
                com.moddingfromamod.world.StandalonePackService.reclaimed(server, owner);
                LOGGER.info("island guard: a Wish Crafter's island rests after {} days unseen — cell {}, {} "
                        + "(owner {}); the cell is never reused", reclaimDays(), i.cellX(), i.cellZ(), owner);
            }
        }
        if (n > 0) {
            store.save(registry);
        }
        return n;
    }

    public static BlockPos origin(Island i) {
        return new BlockPos(i.x(), i.y(), i.z());
    }

    /** Where he stands: on the grass above the origin column. */
    public static BlockPos stand(Island i) {
        return new BlockPos(i.x(), i.standY(), i.z());
    }

    public IslandRegistry registry() {
        return registry;
    }

    public IslandStore store() {
        return store;
    }

    // ── Bases: an island is the Isle's base ─────────────────────────────────────────────

    public Island islandOf(UUID child) {
        return registry.islandOf(child);
    }

    public IslandGrid grid() {
        return registry.grid();
    }

    @Override
    public String noun() {
        return "island";
    }

    @Override
    public Base baseOf(UUID player) {
        return registry.grid().base(registry.islandOf(player));
    }

    @Override
    public boolean standsYet(UUID player) {
        return built(player);
    }

    @Override
    public UUID ownerAt(String dimension, double x, double z) {
        if (dimension != null && !dimension.isEmpty() && !OVERWORLD.equals(dimension)) {
            return null;
        }
        Island i = registry.at(x, z);
        return i == null ? null : i.owner();
    }

    @Override
    public Bases.Pos positionOf(UUID child) {
        ServerPlayer p = child == null ? null : server.getPlayerList().getPlayer(child);
        if (p == null && child != null) {
            // #1637: the canary is a FakePlayer, never in the player list; it is the stand-in while
            // its wish runs, and the island rule needs to know it stands on its own island.
            ServerPlayer standIn = com.moddingfromamod.agent.ActingChild.currentStandIn();
            p = standIn != null && child.equals(standIn.getUUID()) ? standIn : null;
        }
        if (p == null) {
            return null;
        }
        return new Bases.Pos(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ());
    }
}
