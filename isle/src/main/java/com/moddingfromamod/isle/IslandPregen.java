package com.moddingfromamod.isle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Generate the next island cells while the server is quiet</b> (#2542) — the game-facing half of
 * {@link IslandPregenPlan}.
 *
 * <p>Why: without it a new player can spend a long time floating on invisible ground. His island is placed in
 * milliseconds, but a measured server sent 6.8–7.3 chunks a second while his client asked for 8–20 a tick,
 * because every chunk round a NEW cell was being generated as he waited ({@code ChunkMap.isExistingChunkFull}
 * filled the slow ticks). Each new child
 * gets a cell nobody has visited, so each first join paid for a whole area. This pays for it beforehand.
 *
 * <p>Each tick it asks {@link PregenThrottle} whether there is room (the last tick under budget, no player
 * waiting for chunks, no island arrival waiting) and hands the answer to the plan, which asks for chunks of
 * the first of the next {@link #cellsWanted()} cells by a LOADING-only ticket at the lowest loading level —
 * generated off the server thread, behind every player's own chunks — and lets each go once it is FULL, so
 * it unloads and is saved. The radius is {@link IslandPregenRadius#radiusChunks}: the camera's reach plus the
 * view distance.
 *
 * <p>Progress and a status block go to {@link IslandPregenStore} ({@code <world>/wishes/island-pregen.json})
 * every {@link #SAVE_EVERY_TICKS} ticks it changed and when the server stops; a restart resumes there. An
 * operator's tooling can read the status there, and the log says {@code [pregen]} each minute it works and once
 * when it is ready.
 *
 * <p>Only on an islands world (it is made by {@link IsleModule}) and only when {@value #PROPERTY} is not 0. It
 * never claims a cell, places an island, restarts anything or kicks anyone.
 */
public final class IslandPregen {

    private static final Logger LOGGER = LoggerFactory.getLogger("moddingfromamod/islands");

    /** The JVM property ({@code islands.pregen} in the world manifest). */
    public static final String PROPERTY = "moddingfromamod.islands.pregen";
    /** The next three new children's cells. */
    public static final int DEFAULT_CELLS = 3;
    /** The most it will ever be asked for: a bound, so a typo cannot generate the world. */
    public static final int MAX_CELLS = 16;

    static final int SAVE_EVERY_TICKS = 200;
    static final int REPORT_EVERY_TICKS = 1200;
    static final int SIGNALS_EVERY_TICKS = 10;
    static final int TICKET_LIFESPAN_TICKS = 1200;
    static final int RENEW_EVERY_TICKS = 1000;
    /**
     * #2542 follow-up: while cells are pending the status is written at least this often, changed or not. The
     * reader that trusts only a FRESH record (so a crashed or switched-off pre-generation cannot mislead it)
     * found none: the record was written only when a chunk had finished, so a server stopped 32 s after the boot
     * had no record at all.
     */
    static final int HEARTBEAT_EVERY_TICKS = 200;

    /**
     * Ours: it loads but never simulates (nothing ticks in a chunk only we hold), times out by itself and
     * is never saved. A plain record needs no registry id unless persisted (1.21.5+, as {@code IslandService}'s).
     */
    private static final class Ticket {      // a holder: the knob's parse is a Gate A test with no Minecraft booted
        static final TicketType PREGEN = new TicketType(TICKET_LIFESPAN_TICKS, TicketType.FLAG_LOADING);
    }

    /** What the throttle reads; the live server's, or a GameTest's. */
    public interface Signals {
        double lastTickMs();

        int playerChunksWaiting();

        boolean arrivalsWaiting();

        /**
         * Nobody on, nobody logging in and no wish being granted: the fast limits may run ({@link PregenThrottle}).
         * Default false, the gentle limits.
         */
        default boolean alone() {
            return false;
        }
    }

    private final MinecraftServer server;
    private final IslandService islands;
    private final IslandPregenStore store;
    private final IslandPregenPlan plan;
    private final Signals signals;
    private final int wanted;
    private final int viewRadius;
    private final IslandPregenPlan.World world;
    private boolean dirty;
    private boolean saidReady;
    private String yieldWhy;
    private int waiting;
    private long windowStartChunks;
    private long windowStartMs = System.currentTimeMillis();
    private int startTick = -1;
    private boolean alone;
    private double windowMaxTickMs;
    /** Why an empty server must keep ticking ({@link #keepTicking}); null once the cells are ready. */
    private volatile String pendingWhy;

    IslandPregen(MinecraftServer server, IslandService islands, Path file, int wanted, int radius, int viewRadius,
                 Signals signals) {
        this.server = server;
        this.islands = islands;
        this.store = new IslandPregenStore(file);
        this.plan = store.load(radius);
        this.wanted = wanted;
        this.viewRadius = viewRadius;
        this.signals = signals;
        ServerLevel level = server.overworld();
        this.world = new IslandPregenPlan.World() {
            @Override
            public boolean ready(int x, int z) {
                return level.getChunkSource().getChunkNow(x, z) != null;
            }

            @Override
            public void request(int x, int z) {
                level.getChunkSource().addTicketWithRadius(Ticket.PREGEN, new ChunkPos(x, z), 0);
            }

            @Override
            public void release(int x, int z) {
                level.getChunkSource().removeTicketWithRadius(Ticket.PREGEN, new ChunkPos(x, z), 0);
            }
        };
        this.windowStartChunks = plan.finishedTotal();
        List<IslandPregenPlan.Cell> up = upcoming();
        int ready = plan.readyCount(up);
        this.pendingWhy = ready < up.size() ? why(ready, up.size()) : null;
    }

    private static String why(int ready, int wanted) {
        return "island pre-generation (" + ready + " of " + wanted + " cells ready)";
    }

    /**
     * Why an empty server must keep ticking: the cells are not all generated yet (#2542 follow-up), or null once
     * they are. Read every tick by {@code PauseWhileEmptyMixin} through {@link IsleModule#keepTicking}; a field
     * read, set by {@link #tick}.
     */
    public String keepTicking() {
        return pendingWhy;
    }

    /** {@value #PROPERTY}: absent → {@link #DEFAULT_CELLS}; 0 or "off" → off; clamped to {@link #MAX_CELLS}. */
    public static int cellsWanted() {
        return parse(System.getProperty(PROPERTY));
    }

    static int parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CELLS;
        }
        String s = raw.strip();
        if (s.equalsIgnoreCase("off")) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(MAX_CELLS, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return DEFAULT_CELLS;
        }
    }

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("wishes").resolve(IslandPregenStore.FILE);
    }

    /** The running world's, or null when it is off. Called by {@link IsleModule#start}. */
    static IslandPregen startLive(MinecraftServer server, IslandService islands) {
        int n = cellsWanted();
        if (islands == null) {
            return null;
        }
        if (n <= 0) {
            LOGGER.info("[pregen] off ({}=0): new island cells are generated as each child arrives", PROPERTY);
            return null;
        }
        int vd = server.getPlayerList().getViewDistance();
        int radius = IslandPregenRadius.radiusChunks(vd);
        IslandPregen p = new IslandPregen(server, islands, file(server), n, radius, vd, live(server, islands));
        LOGGER.info("[pregen] on: the next {} island cell(s), {} chunks round each centre (the camera's reach, {} "
                        + "blocks, plus view distance {}) — about {} MiB a cell; {} of {} ready",
                n, radius, Math.round(IslandPregenRadius.cameraReachBlocks()), vd,
                Math.round(IslandPregenRadius.estimatedMiBPerCell(radius)), p.plan.readyCount(p.upcoming()), n);
        return p;
    }

    /** The live signals: the server's own last tick, every player's unloaded view chunks, the island queue. */
    static Signals live(MinecraftServer server, IslandService islands) {
        return new Signals() {
            @Override
            public double lastTickMs() {
                long[] t = server.getTickTimesNanos();
                int tc = server.getTickCount();
                if (t == null || t.length == 0) {
                    return 0;
                }
                long a = t[Math.floorMod(tc - 1, t.length)];
                long b = t[Math.floorMod(tc - 2, t.length)];
                return Math.max(a, b) / 1_000_000.0;
            }

            @Override
            public int playerChunksWaiting() {
                int n = 0;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    var source = p.level().getChunkSource();
                    int[] count = {0};
                    p.getChunkTrackingView().forEach(pos -> {
                        if (source.getChunkNow(pos.x, pos.z) == null) {
                            count[0]++;
                        }
                    });
                    n += count[0];
                }
                return n;
            }

            @Override
            public boolean arrivalsWaiting() {
                return islands.arrivalsWaiting();
            }

            @Override
            public boolean alone() {
                if (server.getPlayerList().getPlayerCount() > 0) {
                    return false;
                }
                if (com.moddingfromamod.marshal.Marshal.global().anyLive()) {
                    return false;                       // a canary or a logged-off child's wish: its camera comes first
                }
                return !anyoneConnecting(server);
            }
        };
    }

    /**
     * Is anyone past the handshake (logging in, configuring, playing)? A server-list ping (status) is not a
     * join and does not count, or server-list pings would flap the fast mode.
     */
    static boolean anyoneConnecting(MinecraftServer server) {
        var listener = server.getConnection();
        if (listener == null) {
            return false;
        }
        List<net.minecraft.network.Connection> conns = listener.getConnections();
        synchronized (conns) {
            for (net.minecraft.network.Connection c : conns) {
                var pl = c.getPacketListener();
                if (pl == null) {
                    continue;
                }
                var proto = pl.protocol();
                if (proto == net.minecraft.network.ConnectionProtocol.LOGIN
                        || proto == net.minecraft.network.ConnectionProtocol.CONFIGURATION
                        || proto == net.minecraft.network.ConnectionProtocol.PLAY) {
                    return true;
                }
            }
        }
        return false;
    }

    List<IslandPregenPlan.Cell> upcoming() {
        List<IslandPregenPlan.Cell> out = new ArrayList<>();
        IslandGrid g = islands.grid();
        for (int[] c : islands.registry().upcoming(wanted)) {
            out.add(IslandPregenPlan.Cell.of(g, c));
        }
        return out;
    }

    public IslandPregenPlan plan() {
        return plan;
    }

    /**
     * Whether a progress line (and, while pending, a status heartbeat) is due {@code sinceStart} ticks after
     * this pre-generation's first tick: at once, then every {@code every}. Counted from the START, not from the
     * server's tick count: the per-minute line keyed on {@code tickCount % 1200} first fell up to a minute
     * after the boot, and a server stopped 32 s in never printed one. Pure.
     */
    static boolean due(int sinceStart, int every) {
        return sinceStart >= 0 && every > 0 && sinceStart % every == 0;
    }

    /** One server tick (from {@link IsleModule#onTick}); public so a GameTest drives the same path. */
    public void tick() {
        int tc = server.getTickCount();
        if (startTick < 0) {
            startTick = tc;
        }
        int since = tc - startTick;
        if (tc % SIGNALS_EVERY_TICKS == 0) {
            waiting = signals.playerChunksWaiting();
        }
        List<IslandPregenPlan.Cell> up = upcoming();
        double last = signals.lastTickMs();
        boolean arrivals = signals.arrivalsWaiting();
        windowMaxTickMs = Math.max(windowMaxTickMs, last);
        boolean wasAlone = alone;
        alone = signals.alone();
        if (wasAlone && !alone) {
            int shed = plan.shed(PregenThrottle.keep(false), world);   // someone is coming: back to gentle, now
            if (shed > 0) {
                LOGGER.info("[pregen] someone is on or joining: back to the gentle rate, let go of {} chunk(s) in flight",
                        shed);
            }
        }
        yieldWhy = PregenThrottle.yieldReason(last, waiting, arrivals);
        int admit = PregenThrottle.admit(last, waiting, arrivals, plan.inFlight(), alone);
        int readyBefore = plan.readyCount(up);
        IslandPregenPlan.Step step = plan.tick(up, admit, world, System.currentTimeMillis());
        if (step.finished() > 0 || step.requested() > 0) {
            dirty = true;
        }
        if (tc % RENEW_EVERY_TICKS == 0 && plan.inFlight() > 0) {
            plan.renew(world);                  // a ticket that lapsed before its chunk finished
        }
        int readyNow = plan.readyCount(up);
        pendingWhy = step.ready() ? null : why(readyNow, up.size());
        if (readyNow > readyBefore && !step.ready()) {
            LOGGER.info("[pregen] a cell is generated: {} of {} upcoming cells ready", readyNow, wanted);
            dirty = true;
        }
        if (step.ready() && !saidReady) {
            saidReady = true;
            dirty = true;
            LOGGER.info("[pregen] READY: the next {} island cell(s) are generated, {} chunks round each centre · {} "
                    + "chunks generated in all · region {} MiB", wanted, plan.radius(), plan.finishedTotal(),
                    regionMiB());
        } else if (!step.ready()) {
            saidReady = false;
        }
        if (!step.ready() && due(since, REPORT_EVERY_TICKS)) {
            IslandPregenPlan.Cell w = step.working();
            LOGGER.info("[pregen] cell {}, {}: {}/{} chunks · {} of {} cells ready · {} chunks/s · {} · slowest tick {} ms{}",
                    w.cellX(), w.cellZ(), plan.done(w), plan.chunksPerCell(), readyNow, wanted,
                    String.format("%.1f", rate()), mode(), Math.round(windowMaxTickMs),
                    yieldWhy == null ? "" : " · giving way: " + yieldWhy);
            windowMaxTickMs = 0;
        }
        if ((dirty && tc % SAVE_EVERY_TICKS == 0) || (!step.ready() && due(since, HEARTBEAT_EVERY_TICKS))) {
            save(up);                           // pending: a heartbeat, so a reader sees it is still working
        }
    }

    /** "fast" while nobody is on (the {@link PregenThrottle} fast limits), else "gentle". */
    String mode() {
        return alone ? "fast" : "gentle";
    }

    private double rate() {
        long now = System.currentTimeMillis();
        double s = Math.max(1, now - windowStartMs) / 1000.0;
        double r = (plan.finishedTotal() - windowStartChunks) / s;
        if (now - windowStartMs > 60_000) {
            windowStartMs = now;
            windowStartChunks = plan.finishedTotal();
        }
        return r;
    }

    /** The server is stopping: let go of what is in flight and record where it got to. */
    public void stop() {
        // At ServerStopped the levels are closed: the tickets go with them (ours are never saved), so
        // nothing is asked of the chunk source — only the record is written.
        List<IslandPregenPlan.Cell> up = upcoming();
        plan.releaseAll(new IslandPregenPlan.World() {
            @Override
            public boolean ready(int x, int z) {
                return false;
            }

            @Override
            public void request(int x, int z) {
            }

            @Override
            public void release(int x, int z) {
            }
        });
        save(up);
    }

    private void save(List<IslandPregenPlan.Cell> up) {
        dirty = false;
        store.save(plan, status(up));
    }

    /** What a status reader reads. Every key always present. */
    JsonObject status(List<IslandPregenPlan.Cell> up) {
        JsonObject s = new JsonObject();
        s.addProperty("at", java.time.Instant.now().toString());
        int ready = plan.readyCount(up);
        boolean allReady = ready >= up.size();
        s.addProperty("state", allReady ? "ready" : yieldWhy != null ? "yielding" : "working");
        s.addProperty("why", yieldWhy);
        s.addProperty("mode", mode());
        s.addProperty("cellsWanted", wanted);
        s.addProperty("cellsReady", ready);
        s.addProperty("radius", plan.radius());
        s.addProperty("viewDistance", viewRadius);
        s.addProperty("cameraReachBlocks", Math.round(IslandPregenRadius.cameraReachBlocks()));
        s.addProperty("chunksPerCell", plan.chunksPerCell());
        s.addProperty("firstViewReady", plan.firstViewReady(up, viewRadius));
        JsonArray cells = new JsonArray();
        for (IslandPregenPlan.Cell c : up) {
            JsonObject o = new JsonObject();
            o.addProperty("cellX", c.cellX());
            o.addProperty("cellZ", c.cellZ());
            o.addProperty("centreChunkX", c.centreX());
            o.addProperty("centreChunkZ", c.centreZ());
            o.addProperty("done", plan.done(c));
            o.addProperty("total", plan.chunksPerCell());
            cells.add(o);
        }
        s.add("upcoming", cells);
        s.addProperty("chunksGenerated", plan.finishedTotal());
        s.addProperty("inFlight", plan.inFlight());
        s.addProperty("held", plan.heldCount());
        s.addProperty("loadedChunks", server.overworld().getChunkSource().getLoadedChunksCount());
        s.addProperty("lastProgressAt", plan.lastProgressMs() > 0
                ? java.time.Instant.ofEpochMilli(plan.lastProgressMs()).toString() : null);
        s.addProperty("regionMiB", regionMiB());
        s.addProperty("estMiBPerCell", Math.round(IslandPregenRadius.estimatedMiBPerCell(plan.radius())));
        return s;
    }

    /** The overworld's region files, in MiB: the disk the terrain really takes. -1 if unreadable. */
    long regionMiB() {
        Path region = server.getWorldPath(LevelResource.ROOT).resolve("region");
        if (!Files.isDirectory(region)) {
            return 0;
        }
        try (var files = Files.list(region)) {
            long bytes = files.filter(f -> f.getFileName().toString().endsWith(".mca")).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (java.io.IOException e) {
                    return 0L;
                }
            }).sum();
            return bytes / (1024 * 1024);
        } catch (java.io.IOException | RuntimeException e) {
            return -1;
        }
    }
}
