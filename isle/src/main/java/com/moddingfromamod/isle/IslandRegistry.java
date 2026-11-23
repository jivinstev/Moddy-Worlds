package com.moddingfromamod.isle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Child → island, and the next free cell</b>. Minecraft-free: the allocation rule is a
 * Gate A test, and the game-facing {@code IslandService} only asks it.
 *
 * <p>A child's first {@link #claim} takes the lowest spiral index ({@link IslandGrid#cellAt}) whose
 * cell nobody holds, skipping index 0 (the spawn). Every later claim returns the SAME island, which
 * is what "re-joining finds the same island" means. An instance per world: a GameTest makes its own,
 * because GameTests in one batch share every static.
 */
public final class IslandRegistry {

    /**
     * #1637: THE SERVICE CELL — the canary's island, one fixed cell far outside the spiral every
     * child's island comes from. 48 cells out on both axes: at the Isle's 1024-block spacing that is
     * about 49 000 blocks each way (some 6 100 in the Nether, against the 128 between neighbours), and
     * the spiral would reach it only after ~9 400 children. Never handed to a child: {@link #claim}
     * skips it even before the canary has claimed it. A player who gets there anyway is sent home
     * ({@code IslandService.guardServiceIsland}).
     */
    public static final int SERVICE_CELL_X = -48;
    public static final int SERVICE_CELL_Z = -48;

    private final IslandGrid grid;
    private final Map<UUID, Island> byOwner = new LinkedHashMap<>();

    public IslandRegistry(IslandGrid grid) {
        this.grid = grid;
    }

    public IslandGrid grid() {
        return grid;
    }

    /** The result of a claim: the island, and whether it was made just now (and so must be built). */
    public record Claim(Island island, boolean isNew) {
    }

    /** #1785 step 7: when each owner was last seen (join or leave), epoch ms. */
    private final java.util.Map<UUID, Long> seen = new java.util.HashMap<>();

    /** #1785 step 7: islands that RESTED — their owners let go, their cells never handed out again. */
    private final java.util.Map<Long, Island> retired = new java.util.LinkedHashMap<>();

    /** #1785 step 7: when each rested island rested, by cell. */
    private final java.util.Map<Long, Long> retiredAt = new java.util.HashMap<>();

    /**
     * Step 8 (#1766, D23): his island — active or rested — and when he was last seen move from an old id
     * to his key. Only into an empty slot (identity/Rekey's rule): if his key already has an island, both
     * are kept. Returns whether anything moved; the caller saves.
     */
    /** Step 8: does this id own an island (active or rested) or a last-seen time here? */
    public synchronized boolean holdsAnythingOf(UUID id) {
        if (id == null) {
            return false;
        }
        if (byOwner.containsKey(id) || seen.containsKey(id)) {
            return true;
        }
        for (Island r : retired.values()) {
            if (id.equals(r.owner())) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean rekey(UUID old, UUID neu) {
        if (old == null || neu == null || old.equals(neu)) {
            return false;
        }
        boolean moved = false;
        Island mine = byOwner.get(old);
        if (mine != null && !byOwner.containsKey(neu)) {
            byOwner.remove(old);
            byOwner.put(neu, new Island(neu, mine.cellX(), mine.cellZ(), mine.x(), mine.y(), mine.z(), mine.createdAtMs()));
            moved = true;
        }
        for (var e : retired.entrySet()) {
            Island r = e.getValue();
            if (old.equals(r.owner())) {
                e.setValue(new Island(neu, r.cellX(), r.cellZ(), r.x(), r.y(), r.z(), r.createdAtMs()));
                moved = true;
            }
        }
        Long at = seen.remove(old);
        if (at != null) {
            seen.merge(neu, at, Math::max);
            moved = true;
        }
        return moved;
    }

    public synchronized void seen(UUID owner, long atMs) {
        if (owner != null) {
            seen.merge(owner, atMs, Math::max);
        }
    }

    public synchronized java.util.Map<UUID, Long> seen() {
        return new java.util.HashMap<>(seen);
    }

    public synchronized Long seenAt(UUID owner) {
        return seen.get(owner);
    }

    /**
     * #1785 step 7: let this owner's island rest — he no longer holds it, and its cell is never claimed
     * again. Returns the island, or null when he holds none (or it is the service island).
     */
    public synchronized Island retire(UUID owner, long atMs) {
        Island i = owner == null ? null : byOwner.get(owner);
        if (i == null || isService(i)) {
            return null;
        }
        byOwner.remove(owner);
        long k = key(i.cellX(), i.cellZ());
        retired.put(k, i);
        retiredAt.put(k, atMs);
        return i;
    }

    /** Whether an island of this owner's ever rested (so a fresh one is explained). */
    public synchronized boolean everRetired(UUID owner) {
        for (Island i : retired.values()) {
            if (i.owner().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    /** #2179: when this owner's newest rested island rested, or null when none ever did. */
    public synchronized Long lastRetiredAt(UUID owner) {
        Long last = null;
        for (var e : retired.entrySet()) {
            if (e.getValue().owner().equals(owner)) {
                long at = retiredAt.getOrDefault(e.getKey(), 0L);
                last = last == null ? at : Math.max(last, at);
            }
        }
        return last;
    }

    public synchronized java.util.List<Island> retired() {
        return new ArrayList<>(retired.values());
    }

    public synchronized long retiredAt(Island i) {
        return retiredAt.getOrDefault(key(i.cellX(), i.cellZ()), 0L);
    }

    /** Put back a rested island read from the store. */
    synchronized void restoreRetired(Island island, long atMs) {
        if (island != null) {
            long k = key(island.cellX(), island.cellZ());
            retired.put(k, island);
            retiredAt.put(k, atMs);
        }
    }

    public synchronized Island islandOf(UUID owner) {
        return owner == null ? null : byOwner.get(owner);
    }

    /** This child's island, allocating the next free cell the first time. */
    public synchronized Claim claim(UUID owner, long nowMs) {
        if (owner == null) {
            throw new IllegalArgumentException("an island needs an owner");
        }
        Island have = byOwner.get(owner);
        if (have != null) {
            return new Claim(have, false);
        }
        Set<Long> taken = taken();
        for (int index = 1; ; index++) {
            int[] c = IslandGrid.cellAt(index);
            if (!taken.contains(key(c[0], c[1]))) {
                Island made = new Island(owner, c[0], c[1], grid.originX(c[0]), grid.y(),
                        grid.originZ(c[1]), nowMs);
                byOwner.put(owner, made);
                return new Claim(made, true);
            }
        }
    }

    /** Every cell {@link #claim} must skip: the service cell, every held cell, every rested one. */
    private Set<Long> taken() {
        Set<Long> taken = new HashSet<>();
        taken.add(key(SERVICE_CELL_X, SERVICE_CELL_Z));          // #1637: never a child's
        for (Island i : byOwner.values()) {
            taken.add(key(i.cellX(), i.cellZ()));
        }
        taken.addAll(retired.keySet());                         // #1785: a rested cell is never reused
        return taken;
    }

    /**
     * #2542: the next {@code n} cells {@link #claim} would hand out, in the order it would hand them out —
     * the cells the idle pre-generation ({@link IslandPregenPlan}) works on. The same rule as {@code claim}
     * (one {@link #taken} set, the same spiral walk), so the cell generated first is the one the next new
     * child gets; asking claims nothing.
     */
    public synchronized List<int[]> upcoming(int n) {
        List<int[]> out = new ArrayList<>(Math.max(0, n));
        if (n <= 0) {
            return out;
        }
        Set<Long> taken = taken();
        for (int index = 1; out.size() < n; index++) {
            int[] c = IslandGrid.cellAt(index);
            if (!taken.contains(key(c[0], c[1]))) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * #1637: the SERVICE island (the canary's) for this owner — the fixed {@link #SERVICE_CELL_X},
     * {@link #SERVICE_CELL_Z}, never a cell from the spiral. The same island every time; refused if a
     * different owner already holds that cell.
     */
    public synchronized Claim claimService(UUID owner, long nowMs) {
        if (owner == null) {
            throw new IllegalArgumentException("an island needs an owner");
        }
        Island have = byOwner.get(owner);
        if (have != null) {
            return new Claim(have, false);
        }
        for (Island i : byOwner.values()) {
            if (isService(i)) {
                throw new IllegalStateException("the service cell is already " + i.owner() + "'s");
            }
        }
        Island made = new Island(owner, SERVICE_CELL_X, SERVICE_CELL_Z, grid.originX(SERVICE_CELL_X),
                grid.y(), grid.originZ(SERVICE_CELL_Z), nowMs);
        byOwner.put(owner, made);
        return new Claim(made, true);
    }

    /** #1637: is this the service (canary's) island, not a child's? */
    public static boolean isService(Island i) {
        return i != null && i.cellX() == SERVICE_CELL_X && i.cellZ() == SERVICE_CELL_Z;
    }

    /** #1637: how many CHILDREN have an island — the service island is not one. */
    public synchronized int childCount() {
        int n = 0;
        for (Island i : byOwner.values()) {
            if (!isService(i)) {
                n++;
            }
        }
        return n;
    }

    /** Whose island's cell holds this column, or null (the spawn, or unclaimed sky). */
    public synchronized Island at(double x, double z) {
        int[] c = grid.cellOf(x, z);
        for (Island i : byOwner.values()) {
            if (i.cellX() == c[0] && i.cellZ() == c[1]) {
                return i;
            }
        }
        return null;
    }

    /** Put back a record read from the store. A second record for one owner or one cell is dropped. */
    synchronized boolean restore(Island island) {
        if (island == null || byOwner.containsKey(island.owner())) {
            return false;
        }
        for (Island i : byOwner.values()) {
            if (i.cellX() == island.cellX() && i.cellZ() == island.cellZ()) {
                return false;
            }
        }
        byOwner.put(island.owner(), island);
        return true;
    }

    public synchronized List<Island> all() {
        return new ArrayList<>(byOwner.values());
    }

    public synchronized int size() {
        return byOwner.size();
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
