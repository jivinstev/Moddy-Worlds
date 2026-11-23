package com.moddingfromamod.isle;

/**
 * <b>The rules of an island's wait, Minecraft-free</b>.
 *
 * <p>Until an island whose chunks had not generated in {@code MAX_CHUNK_WAIT_TICKS} was placed
 * ANYWAY, and {@code StructureTemplate.placeInWorld} then loaded its chunks inline on the server
 * thread. A measurement of what that does to a burst of arrivals: 40 players froze every player six
 * times (7.6 s at worst) on 8 cores, and on 4 shared cores the server thread blocked 60 s and the
 * watchdog crashed it. There is no deadline any more: an island is
 * placed only on a tick its chunks — and the ring of blocks just outside it, which the placement's
 * shape updates read — are already loaded, at most {@link #PLACEMENTS_PER_TICK} in one tick, and the
 * child waits on the arrival island and is told so, kindly, if it takes a while.
 */
public final class IslandWait {

    /** Islands placed in one server tick at most, so a burst whose chunks land together is spread out. */
    public static final int PLACEMENTS_PER_TICK = 2;

    /**
     * How far out the template's placement reads: its edge blocks' shape updates look one block past
     * the box ({@code StructureTemplate.updateShapeAtEdge}), so those chunks must be loaded too.
     */
    public static final int EDGE_MARGIN = 1;

    /** Ticks before a waiting child is first told his island is still coming (10 s). */
    public static final int NUDGE_FIRST_TICKS = 200;
    /** Then again every this many ticks (30 s), so a long wait is never silent. */
    public static final int NUDGE_EVERY_TICKS = 600;

    /** How often the island's chunk ticket is renewed while it waits. */
    public static final int TICKET_REFRESH_TICKS = 100;
    /** The ticket's own lifespan, longer than the refresh so it never lapses while he waits. */
    public static final int TICKET_LIFESPAN_TICKS = 300;

    /**
     * The ticket's radius for an island in the queue: 0, its own chunks and nothing more (level 33).
     *
     * <p>A radius buys priority with chunks. A region ticket of distance {@code d} sits at level
     * {@code 33 - d}, a player's view chunks at 31, and the worldgen queue runs lower levels first —
     * so 3 ranks an island ahead of every view distance, and makes about 64 chunks FULL for it where
     * 0 makes four. Given to EVERY island in a burst that is the backlog, not a way past it: 40 bots
     * in a burst on 4 shared cores (one run each) placed island p95 at 40 s with 0 for all,
     * 64 s with 1 (the old portal ticket's radius), and not one island in 60 s with 3. So only the
     * {@link #HEAD} oldest waiting islands get {@link #HEAD_TICKET_DISTANCE} ({@link #escalate}).
     */
    public static final int TICKET_DISTANCE = 0;

    /**
     * The radius for the head of the queue: level 30, ahead of every player's view chunks (31). Without
     * it an island at 33 waits behind every child already moved home — measured: two children moved,
     * 38 islands placed only after the bots LEFT, 115 s after their claims. The extra chunks are inside
     * the view distance he has once moved, so they are not wasted.
     */
    public static final int HEAD_TICKET_DISTANCE = 3;
    /** How many of the oldest waiting islands are escalated at once. */
    public static final int HEAD = 2;
    /** An island is escalated only after it has waited this long (5 s): a quiet server never pays it. */
    public static final int ESCALATE_AFTER_TICKS = 100;

    private IslandWait() {
    }

    /**
     * The chunks an island's placement touches: the template's box from {@code (cornerX, cornerZ)},
     * {@code sizeX} by {@code sizeZ} blocks, widened by {@link #EDGE_MARGIN}. Returns
     * {@code {minChunkX, minChunkZ, maxChunkX, maxChunkZ}}, inclusive.
     */
    public static int[] chunkSpan(int cornerX, int cornerZ, int sizeX, int sizeZ) {
        if (sizeX < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("an island template is at least one block wide: " + sizeX + "x" + sizeZ);
        }
        int x0 = cornerX - EDGE_MARGIN;
        int z0 = cornerZ - EDGE_MARGIN;
        int x1 = cornerX + sizeX - 1 + EDGE_MARGIN;
        int z1 = cornerZ + sizeZ - 1 + EDGE_MARGIN;
        return new int[]{x0 >> 4, z0 >> 4, x1 >> 4, z1 >> 4};
    }

    /** Is this the tick to tell a child who has waited {@code waited} ticks that his island is coming? */
    public static boolean nudge(int waited) {
        if (waited < NUDGE_FIRST_TICKS) {
            return false;
        }
        return (waited - NUDGE_FIRST_TICKS) % NUDGE_EVERY_TICKS == 0;
    }

    /**
     * Should the island {@code rank}-th oldest among those still waiting for chunks (0 = oldest), which
     * has waited {@code waited} ticks, hold the wide, high-priority ticket?
     */
    public static boolean escalate(int rank, int waited) {
        return rank >= 0 && rank < HEAD && waited >= ESCALATE_AFTER_TICKS;
    }

    /** Is this the tick to renew the ticket for an island that has waited {@code waited} ticks? */
    public static boolean refreshTicket(int waited) {
        return waited > 0 && waited % TICKET_REFRESH_TICKS == 0;
    }
}
