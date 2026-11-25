package com.moddingfromamod.levels.arena;

import java.util.List;

/**
 * <b>The gate between the prep room and the arena, in a battle</b>: when it is shut, who is moved inside, and where.
 * Pure: no Minecraft; {@link ArenaBuilder} sets the blocks and {@link ArenaBattle} moves the players.
 *
 * <p>The rules:
 * <ul>
 *   <li>While a WAVE is on the gateway (the gate's block and the opening above it) is iron bars: nobody can open it
 *       (a fence gate can be opened by hand) and everyone can see through it. No monster gets out and no fighter
 *       either. In the countdown, in a break between waves and when no battle is on it is the oak fence gate, as
 *       built (a player opens it).</li>
 *   <li>When a wave starts (the first, and after each break), every fighter who is not inside the arena is moved just
 *       inside the gate, facing in, and the gate shuts. A player who is not fighting is never moved, unless he stands
 *       in the gateway itself, which is then moved to the prep room's side.</li>
 *   <li>A fighter knocked out with lives left during a wave, back on his feet in the prep room, sees his
 *       lives and a count of {@value #BACK_IN_SECONDS} seconds on his action bar, then is moved inside, while the wave
 *       is still on. A fighter out of lives is not a fighter any more: he stays in the prep room and watches through
 *       the bars.</li>
 *   <li>A wave's monster that is in the place but outside the arena is put back inside; one that left the place
 *       altogether is removed (and counts as cleared), as before.</li>
 * </ul>
 */
public final class ArenaGate {

    private ArenaGate() {
    }

    /** How long a fighter back on his feet in the prep room waits before he is moved inside, in seconds. */
    public static final int BACK_IN_SECONDS = 3;
    /** The same, in ticks. */
    public static final int BACK_IN_TICKS = BACK_IN_SECONDS * ArenaMatch.TICKS_PER_SECOND;

    /** True when the gate is shut in this phase of a battle: only while a wave is on. */
    public static boolean shut(ArenaMatch.Phase phase) {
        return phase == ArenaMatch.Phase.WAVE;
    }

    /** The gateway's blocks, as (x, y, z): the gate's block, then the opening above it. */
    public static List<int[]> gateway(ArenaPlace p) {
        int[] g = p.gate();
        return List.of(g, new int[] {g[0], g[1] + 1, g[2]});
    }

    /** True when (x, y, z) is one of the gateway's blocks. */
    public static boolean isGateway(ArenaPlace p, int x, int y, int z) {
        int[] g = p.gate();
        return x == g[0] && z == g[2] && (y == g[1] || y == g[1] + 1);
    }

    /** What a gateway block is, the gate shut or open: iron bars both, or the fence gate and the air above it. */
    public static ArenaPlace.Kind kind(ArenaPlace p, int y, boolean shut) {
        if (shut) {
            return ArenaPlace.Kind.GATE_BARS;
        }
        return y == p.gate()[1] ? ArenaPlace.Kind.GATE : ArenaPlace.Kind.AIR;
    }

    /** Where a fighter is moved in: the middle of the arena's block just inside the gate, at the floor. */
    public static double[] inside(ArenaPlace p) {
        int[] g = p.gate();
        return new double[] {g[0] + 0.5, g[1], g[2] - 0.5};
    }

    /** The way he faces when he is moved in: north, into the arena (Minecraft's yaw 180). */
    public static final float INSIDE_YAW = 180f;

    /** Where a player standing in the gateway when it shuts, who is not fighting, is moved: the prep room's side. */
    public static double[] outside(ArenaPlace p) {
        int[] g = p.gate();
        return new double[] {g[0] + 0.5, g[1], g[2] + 1.5};
    }

    /**
     * True when (x, y, z) is inside the arena: its floor's columns, inside the walls, from the base's top up (standing
     * in a hole a blast made counts).
     */
    public static boolean inArena(ArenaPlace p, double x, double y, double z) {
        ArenaPlace.Box a = p.arenaInside();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return bx >= a.x0() && bx <= a.x1() && bz >= a.z0() && bz <= a.z1() && y >= p.bedrockY();
    }

    /** True when a body at (x, z), {@code halfWidth} each way, overlaps the gateway's column. */
    public static boolean inGateway(ArenaPlace p, double x, double z, double halfWidth) {
        int[] g = p.gate();
        return x + halfWidth > g[0] && x - halfWidth < g[0] + 1 && z + halfWidth > g[2] && z - halfWidth < g[2] + 1;
    }

    /** What happens to a wave's monster where it stands. */
    public enum MobAct {
        /** it is in the arena: nothing */
        KEEP,
        /**
         * it is in the place but not in the arena (the prep room, the gateway), or it fell out under the place: it is
         * put back inside
         */
        PUT_BACK,
        /** it is nowhere near the place: removed, and counted as cleared */
        REMOVE
    }

    /** {@link MobAct} for a wave's monster at (x, y, z). */
    public static MobAct mob(ArenaPlace p, double x, double y, double z) {
        if (inArena(p, x, y, z)) {
            return MobAct.KEEP;
        }
        return p.keeps(x, y, z) || p.fellOut(x, y, z) ? MobAct.PUT_BACK : MobAct.REMOVE;
    }

    /** What happens to one fighter on a tick of a battle. */
    public enum Act {
        /** nothing */
        NONE,
        /** he is moved inside now */
        MOVE,
        /** his count starts ({@value #BACK_IN_TICKS} ticks) */
        START_COUNT,
        /** his count goes on: one tick less */
        COUNT,
        /** his count stops (the wave is over, he is inside already, or he is not on his feet) */
        STOP_COUNT
    }

    /**
     * What happens to one fighter on this tick. {@code waveOn}: a wave is on; {@code waveStarted}: it began this tick;
     * {@code onFeet}: he is alive and standing (not on his way to a new life); {@code inArena}: he is inside the arena;
     * {@code countLeft}: the ticks left of his count, or below 0 when he has none.
     */
    public static Act act(boolean waveOn, boolean waveStarted, boolean onFeet, boolean inArena, int countLeft) {
        boolean counting = countLeft >= 0;
        if (!waveOn || !onFeet || inArena) {
            return counting ? Act.STOP_COUNT : Act.NONE;
        }
        if (waveStarted || (counting && countLeft == 0)) {
            return Act.MOVE;
        }
        return counting ? Act.COUNT : Act.START_COUNT;
    }

    /** The seconds a count shows with {@code ticksLeft} ticks to go (3, 2, 1). */
    public static int secondsLeft(int ticksLeft) {
        return Math.max(1, (ticksLeft + ArenaMatch.TICKS_PER_SECOND - 1) / ArenaMatch.TICKS_PER_SECOND);
    }
}
