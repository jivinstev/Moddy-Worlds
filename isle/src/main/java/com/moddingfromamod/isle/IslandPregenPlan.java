package com.moddingfromamod.isle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * <b>Generate the next islands' terrain before anyone needs it</b> (#2542) — the whole decision, Minecraft-free,
 * so every rule is a Gate A test; {@link IslandPregen} only executes it.
 *
 * <p>The cells are the ones {@link IslandRegistry#upcoming} names — the next {@code N} a new child would be
 * given, in that order — and the chunks of each are taken in RINGS out from the island's centre chunk
 * ({@link #offset}), so a cell half done is the half that matters: his own view first, the camera's
 * reach last. One cell at a time, at most {@code admit} new chunks a tick (the {@link PregenThrottle}'s
 * answer), each asked for with a ticket and counted done when it is FULL.
 *
 * <p><b>Resumable.</b> Per cell it records {@code next} — every chunk before that index in the ring order is
 * done — and {@code ahead}, the done chunks past it (chunks finish out of order). A restart starts there, so
 * nothing recorded done is asked for again ({@link #toJson}/{@link #fromJson}). Because the ring order of a
 * smaller radius is a prefix of a larger one's, the progress needs no radius: a raised view distance just
 * makes a finished cell unfinished again from where it stopped.
 *
 * <p><b>Overlap.</b> Cells are 64 chunks apart and the radius is wider than half that, so neighbouring cells
 * share chunks. A chunk inside the rings a cell already finished counts as done for the next cell and is
 * never asked for twice.
 *
 * <p><b>A finished ring is held until the ring outside it is done</b> (#2542 follow-up). Releasing each chunk the
 * moment it was FULL let the outer ring's half-generated neighbours unload; vanilla then blocks the server thread
 * on a disk read for each one it saves ({@code ChunkMap.save → isExistingChunkFull → join}, behind the IO
 * worker's write queue) and reads it back for the next ring. JFR, fast mode on a 4-vCPU server: 9.5 s and 4.4 s
 * ticks, every long park in {@code isExistingChunkFull}. So a done chunk keeps its ticket ({@link #heldCount()})
 * until its cell's frontier is {@link #HOLD_RINGS} rings past it, or the work moves to another cell — and then at
 * most {@link #RELEASE_PER_TICK} a tick, oldest first. Letting a whole cell go in one tick unloaded thousands of
 * chunks at once (each FULL ticket keeps a 23×23 patch of lower-status neighbours loaded), and vanilla saves an
 * unload queue over 2,000 regardless of the tick's time: a 5–14 s tick at READY, measured with and without the
 * hold.
 *
 * <p><b>What it never does</b>: claim a cell, place an island, or work past {@code N} cells and the radius.
 * It is handed the cells; it only asks for terrain.
 */
public final class IslandPregenPlan {

    /** How the plan reaches the world: the live one asks the chunk source, a test a map. */
    public interface World {
        /** Is this chunk FULL and loaded right now (never loads it)? */
        boolean ready(int chunkX, int chunkZ);

        /** Ask for it to be generated (a ticket; generated off the server thread). */
        void request(int chunkX, int chunkZ);

        /** It is done (or no longer wanted): let it unload, and so be saved. */
        void release(int chunkX, int chunkZ);
    }

    /** One upcoming cell: its grid cell and its centre chunk. */
    public record Cell(int cellX, int cellZ, int centreX, int centreZ) {
        public static Cell of(IslandGrid grid, int[] cell) {
            return new Cell(cell[0], cell[1], grid.originX(cell[0]) >> 4, grid.originZ(cell[1]) >> 4);
        }
    }

    /** What a tick did, for the report. */
    public record Step(int requested, int finished, Cell working, boolean ready) {
    }

    /** Per cell: every ring-order index below {@code next} is done; {@code ahead} are done ones past it. */
    static final class Progress {
        final Cell cell;
        int next;
        final TreeSet<Integer> ahead = new TreeSet<>();
        long completedAtMs;

        Progress(Cell cell) {
            this.cell = cell;
        }

        /** Whole rings finished: ring k is done once next >= (2k+1)^2. */
        int ringsDone() {
            int r = 0;
            while ((long) (2 * r + 1) * (2 * r + 1) <= next) {
                r++;
            }
            return r;
        }
    }

    private final int radius;
    private final int total;
    private final int[] dx;
    private final int[] dz;
    private final Map<Long, Progress> cells = new LinkedHashMap<>();
    /** chunk key → the cell it was asked for and its index there. */
    private final Map<Long, long[]> inFlight = new LinkedHashMap<>();
    /** Rings a done chunk keeps its ticket past: its outward neighbours stay loaded while the next ring is made. */
    static final int HOLD_RINGS = 1;
    /** Held chunks let go per tick at most, so their neighbours unload a few at a time. */
    static final int RELEASE_PER_TICK = 4;
    /** Done chunks still holding a ticket: chunk key → {cell key, ring}. */
    private final Map<Long, long[]> held = new LinkedHashMap<>();
    private long requestedTotal;
    private long finishedTotal;
    private long lastProgressMs;

    public IslandPregenPlan(int radiusChunks) {
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("radius " + radiusChunks);
        }
        this.radius = radiusChunks;
        this.total = (int) IslandPregenRadius.chunksPerCell(radiusChunks);
        this.dx = new int[total];
        this.dz = new int[total];
        int i = 1;                                   // index 0 is the centre: (0, 0)
        for (int r = 1; r <= radiusChunks; r++) {
            for (int[] o : IslandGrid.ring(r)) {
                dx[i] = o[0];
                dz[i] = o[1];
                i++;
            }
        }
    }

    public int radius() {
        return radius;
    }

    /** Chunks one cell asks for at this radius. */
    public int chunksPerCell() {
        return total;
    }

    /**
     * The {@code i}-th chunk of the ring order, as an offset from the centre chunk: 0 is the centre, then
     * ring 1's eight in {@link IslandGrid#ring}'s order, then ring 2's sixteen, and so on.
     */
    public static int[] offset(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("index " + i);
        }
        if (i == 0) {
            return new int[]{0, 0};
        }
        int r = 1;
        while ((long) (2 * r + 1) * (2 * r + 1) <= i) {
            r++;
        }
        int before = (2 * r - 1) * (2 * r - 1);
        return IslandGrid.ring(r).get(i - before);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private Progress progress(Cell c) {
        return cells.computeIfAbsent(key(c.cellX(), c.cellZ()), k -> new Progress(c));
    }

    /** Is this cell's whole square done at this radius? */
    public boolean complete(Cell c) {
        Progress p = cells.get(key(c.cellX(), c.cellZ()));
        return p != null && p.next >= total;
    }

    /** Chunks of this cell done (in order, plus those done ahead), capped at the cell's total. */
    public int done(Cell c) {
        Progress p = cells.get(key(c.cellX(), c.cellZ()));
        if (p == null) {
            return 0;
        }
        return Math.min(total, p.next + (int) p.ahead.stream().filter(i -> i < total).count());
    }

    /** Is this chunk inside rings some OTHER cell already finished? Then it is generated already. */
    private boolean covered(Cell self, int chunkX, int chunkZ) {
        for (Progress p : cells.values()) {
            if (p.cell.cellX() == self.cellX() && p.cell.cellZ() == self.cellZ()) {
                continue;
            }
            int rings = p.ringsDone();
            if (rings > 0 && Math.max(Math.abs(chunkX - p.cell.centreX()), Math.abs(chunkZ - p.cell.centreZ())) < rings) {
                return true;
            }
        }
        return false;
    }

    private void advance(Progress p) {
        while (p.next < total) {
            if (p.ahead.remove(p.next)
                    || covered(p.cell, p.cell.centreX() + dx[p.next], p.cell.centreZ() + dz[p.next])) {
                p.next++;
                continue;
            }
            break;
        }
    }

    /**
     * One tick. Collects the chunks that finished, then asks for up to {@code admit} more of the first
     * upcoming cell that is not done. {@code upcoming} is {@link IslandRegistry#upcoming}'s list, so a cell
     * a child has just been given drops out of it and the work moves to the next.
     */
    public Step tick(List<Cell> upcoming, int admit, World world, long nowMs) {
        int finished = 0;
        var it = inFlight.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            long k = e.getKey();
            int x = (int) (k >> 32);
            int z = (int) k;
            if (!world.ready(x, z)) {
                continue;
            }
            it.remove();
            held.put(k, new long[]{e.getValue()[0], ring((int) e.getValue()[1])});
            finished++;
            finishedTotal++;
            lastProgressMs = nowMs;
            Progress p = cells.get(e.getValue()[0]);
            int idx = (int) e.getValue()[1];
            if (p != null && idx >= p.next) {
                p.ahead.add(idx);
            }
        }
        for (Progress p : cells.values()) {
            boolean was = p.next >= total;
            advance(p);
            if (!was && p.next >= total && p.completedAtMs == 0) {
                p.completedAtMs = nowMs;
            }
        }
        Cell working = null;
        for (Cell c : upcoming) {
            Progress p = progress(c);
            advance(p);
            if (p.next < total) {
                working = c;
                break;
            }
            if (p.completedAtMs == 0) {
                p.completedAtMs = nowMs;
            }
        }
        if (working == null) {
            releaseHeld(world, Long.MIN_VALUE, Integer.MAX_VALUE);
            return new Step(0, finished, null, true);
        }
        int requested = 0;
        Progress p = progress(working);
        long ck = key(working.cellX(), working.cellZ());
        releaseHeld(world, ck, ring(p.next) - HOLD_RINGS);
        // A bounded scan: the next chunks in ring order that are not done, in flight or covered.
        int scanEnd = Math.min(total, p.next + 4 * Math.max(1, admit) + inFlight.size() + 64);
        for (int i = p.next; i < scanEnd && requested < admit; i++) {
            if (p.ahead.contains(i)) {
                continue;
            }
            int x = working.centreX() + dx[i];
            int z = working.centreZ() + dz[i];
            long k = key(x, z);
            if (inFlight.containsKey(k) || covered(working, x, z)) {
                continue;
            }
            inFlight.put(k, new long[]{ck, i});
            world.request(x, z);
            requested++;
            requestedTotal++;
        }
        return new Step(requested, finished, working, false);
    }

    /** The ring of the {@code i}-th chunk of the ring order (0 is the centre). */
    static int ring(int i) {
        int r = 0;
        while ((long) (2 * r + 1) * (2 * r + 1) <= i) {
            r++;
        }
        return r;
    }

    /** Let go of up to {@link #RELEASE_PER_TICK} held chunks of other cells, or of this cell's rings below {@code belowRing}. */
    private void releaseHeld(World world, long cellKey, int belowRing) {
        releaseHeld(world, cellKey, belowRing, RELEASE_PER_TICK);
    }

    private void releaseHeld(World world, long cellKey, int belowRing, int max) {
        var it = held.entrySet().iterator();
        int n = 0;
        while (it.hasNext() && n < max) {
            var e = it.next();
            long[] v = e.getValue();
            if (v[0] != cellKey || v[1] < belowRing) {
                world.release((int) (e.getKey() >> 32), (int) (long) e.getKey());
                it.remove();
                n++;
            }
        }
    }

    /** Ask the world again for every chunk still in flight or held (its ticket lapsed: a restart of the ask). */
    public void renew(World world) {
        for (long k : inFlight.keySet()) {
            world.request((int) (k >> 32), (int) k);
        }
        for (long k : held.keySet()) {
            world.request((int) (k >> 32), (int) k);
        }
    }

    /** Done chunks still holding their ticket ({@link #HOLD_RINGS}). */
    public int heldCount() {
        return held.size();
    }

    /**
     * Let go of the newest in-flight chunks past {@code keep} (someone arrived while it was running fast): they
     * are simply not done, and are asked for again later. Returns how many it let go.
     */
    public int shed(int keep, World world) {
        int excess = inFlight.size() - Math.max(0, keep);
        if (excess <= 0) {
            return 0;
        }
        List<Long> keys = new ArrayList<>(inFlight.keySet());
        int n = 0;
        for (int i = keys.size() - 1; i >= 0 && n < excess; i--, n++) {
            long k = keys.get(i);
            world.release((int) (k >> 32), (int) k);
            inFlight.remove(k);
        }
        return n;
    }

    /** Let go of everything in flight (the server is stopping); progress keeps what was done. */
    public void releaseAll(World world) {
        for (long k : inFlight.keySet()) {
            world.release((int) (k >> 32), (int) k);
        }
        inFlight.clear();
        releaseHeld(world, Long.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public int inFlight() {
        return inFlight.size();
    }

    public long requestedTotal() {
        return requestedTotal;
    }

    public long finishedTotal() {
        return finishedTotal;
    }

    public long lastProgressMs() {
        return lastProgressMs;
    }

    /** How many of these cells are done. */
    public int readyCount(List<Cell> upcoming) {
        int n = 0;
        for (Cell c : upcoming) {
            if (complete(c)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Is the first of these cells generated at least as far as a child's own view ({@code viewRadius}
     * chunks round its centre)? The bar a reset waits for before letting anyone in; the camera's reach
     * keeps generating after.
     */
    public boolean firstViewReady(List<Cell> upcoming, int viewRadius) {
        if (upcoming.isEmpty()) {
            return true;
        }
        Cell c = upcoming.get(0);
        Progress p = cells.get(key(c.cellX(), c.cellZ()));
        long need = IslandPregenRadius.chunksPerCell(Math.min(viewRadius, radius));
        if (p != null && p.next >= need) {
            return true;
        }
        // ...or every chunk of that view square is covered by another cell's finished rings
        for (int i = 0; i < need; i++) {
            int x = c.centreX() + dx[i];
            int z = c.centreZ() + dz[i];
            boolean done = p != null && (i < p.next || p.ahead.contains(i));
            if (!done && !covered(c, x, z)) {
                return false;
            }
        }
        return true;
    }

    // ── the record on disk (IslandPregenStore) ──────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("radius", radius);
        JsonArray arr = new JsonArray();
        for (Progress p : cells.values()) {
            JsonObject c = new JsonObject();
            c.addProperty("cellX", p.cell.cellX());
            c.addProperty("cellZ", p.cell.cellZ());
            c.addProperty("centreX", p.cell.centreX());
            c.addProperty("centreZ", p.cell.centreZ());
            c.addProperty("next", p.next);
            JsonArray ahead = new JsonArray();
            p.ahead.forEach(ahead::add);
            c.add("ahead", ahead);
            if (p.completedAtMs > 0) {
                c.addProperty("completedAt", java.time.Instant.ofEpochMilli(p.completedAtMs).toString());
            }
            arr.add(c);
        }
        o.add("cells", arr);
        o.addProperty("chunksGenerated", finishedTotal);
        return o;
    }

    /** Read back what {@link #toJson} wrote, at the radius in force now (which may differ). */
    public static IslandPregenPlan fromJson(JsonObject o, int radiusChunks) {
        IslandPregenPlan plan = new IslandPregenPlan(radiusChunks);
        if (o == null) {
            return plan;
        }
        JsonElement arr = o.get("cells");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement el : arr.getAsJsonArray()) {
                try {
                    JsonObject c = el.getAsJsonObject();
                    Progress p = plan.progress(new Cell(c.get("cellX").getAsInt(), c.get("cellZ").getAsInt(),
                            c.get("centreX").getAsInt(), c.get("centreZ").getAsInt()));
                    p.next = Math.max(0, c.get("next").getAsInt());
                    if (c.has("ahead")) {
                        for (JsonElement a : c.getAsJsonArray("ahead")) {
                            p.ahead.add(a.getAsInt());
                        }
                    }
                    if (c.has("completedAt")) {
                        p.completedAtMs = java.time.Instant.parse(c.get("completedAt").getAsString()).toEpochMilli();
                    }
                } catch (RuntimeException e) {
                    // a damaged row is work to redo, never a refusal: regenerating terrain loses nothing
                }
            }
        }
        if (o.has("chunksGenerated")) {
            plan.finishedTotal = o.get("chunksGenerated").getAsLong();
        }
        return plan;
    }

    /** The cells this plan has a record of (for the report). */
    public List<Cell> recorded() {
        List<Cell> out = new ArrayList<>();
        for (Progress p : cells.values()) {
            out.add(p.cell);
        }
        return out;
    }
}
