package com.moddingfromamod.isle;

/**
 * <b>When the idle pre-generation may ask for a chunk</b> (#2542) — Minecraft-free.
 *
 * <p>Pre-generation is only worth having if no join gets slower for it, so it gives way to everything a
 * player is waiting for:
 * <ul>
 *   <li>a tick already over {@link #BUDGET_MS} — the main thread is where a FULL chunk is finished and
 *       where a slow tick is felt, so nothing new is asked for into a tick that has no room;</li>
 *   <li>any player (a child or the camera) with chunks of his view not yet loaded — his chunks always
 *       come first;</li>
 *   <li>any island arrival still waiting ({@link IslandService}): its own chunks are the ones that matter.</li>
 * </ul>
 * Otherwise it keeps at most {@link #MAX_IN_FLIGHT} chunks asked for at once, adding at most
 * {@link #MAX_NEW_PER_TICK} a tick. Its tickets also sit at the lowest loading level (33), behind every
 * player's view chunks in the generation queue, so even what is in flight when a player arrives waits for
 * him.
 *
 * <p><b>Fast when nobody is on</b> (#2542 follow-up). With the gentle limits an empty Isle generated ~6 chunks a
 * second (measured: 123 chunks in 20 s), ~25 minutes a cell, too slow for a server that is not up all day. With nobody on,
 * nobody joining and no wish being granted ({@code alone}) it keeps up to {@link #FAST_MAX_IN_FLIGHT} in flight and
 * adds up to {@link #FAST_MAX_NEW_PER_TICK} a tick — still never into a tick over {@link #BUDGET_MS}, so the tick a
 * player's join lands in has room. The moment anyone is on or connecting it is gentle again, and
 * {@link #keep} tells the caller to let go of what is in flight past the gentle limit, so a joining child's
 * chunks queue behind at most {@link #MAX_IN_FLIGHT} of ours.
 */
public final class PregenThrottle {

    /** Half a tick: a tick that took longer has no room for more. */
    public static final double BUDGET_MS = 25.0;
    public static final int MAX_IN_FLIGHT = 8;
    public static final int MAX_NEW_PER_TICK = 2;
    /** With nobody on: measured on a 4-vCPU dedicated server (#2542 follow-up), see the PR. */
    public static final int FAST_MAX_IN_FLIGHT = 64;
    public static final int FAST_MAX_NEW_PER_TICK = 16;

    private PregenThrottle() {
    }

    /** Why it is not asking for more, or null when it may. */
    public static String yieldReason(double lastTickMs, int playerChunksWaiting, boolean arrivalsWaiting) {
        if (lastTickMs >= BUDGET_MS) {
            return "the last tick took " + Math.round(lastTickMs) + " ms";
        }
        if (playerChunksWaiting > 0) {
            return playerChunksWaiting + " chunk(s) a player is waiting for";
        }
        if (arrivalsWaiting) {
            return "an island arrival is waiting";
        }
        return null;
    }

    /** How many new chunks to ask for this tick, gently (someone may be on). */
    public static int admit(double lastTickMs, int playerChunksWaiting, boolean arrivalsWaiting, int inFlight) {
        return admit(lastTickMs, playerChunksWaiting, arrivalsWaiting, inFlight, false);
    }

    /** How many new chunks to ask for this tick; {@code alone}: nobody on, connecting or being granted a wish. */
    public static int admit(double lastTickMs, int playerChunksWaiting, boolean arrivalsWaiting, int inFlight,
                            boolean alone) {
        if (yieldReason(lastTickMs, playerChunksWaiting, arrivalsWaiting) != null) {
            return 0;
        }
        int cap = alone ? FAST_MAX_IN_FLIGHT : MAX_IN_FLIGHT;
        int perTick = alone ? FAST_MAX_NEW_PER_TICK : MAX_NEW_PER_TICK;
        return Math.max(0, Math.min(perTick, cap - inFlight));
    }

    /** How many in-flight chunks may stay asked for: all of them alone, else at most the gentle limit. */
    public static int keep(boolean alone) {
        return alone ? Integer.MAX_VALUE : MAX_IN_FLIGHT;
    }
}
