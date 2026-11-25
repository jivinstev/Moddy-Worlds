package com.moddingfromamod.levels.arena;

import java.util.List;
import java.util.UUID;

/**
 * <b>The players' side in a battle</b>: every creature wished into the arena fights the wave. Pure decisions, no
 * Minecraft: {@link ArenaBattle} asks them once a tick for each creature in the arena that is not one of the wave's and
 * carries them out (its target, its walk to it, its strike), so what a wished creature does in a battle never rests on
 * its own nature: a cat fights as surely as a wolf, and a creature that would go for players goes for the wave instead.
 *
 * <p>Deterministic: the same creatures and foes in the same places give the same choices, whatever order the world
 * lists them in.
 */
final class ArenaAllies {

    /** How often a creature strikes the foe it stands beside: once a second. */
    static final int STRIKE_COOLDOWN_TICKS = 20;
    /** How often its walk to its foe is set again (the foe moves): twice a second. */
    static final int REPATH_TICKS = 10;
    /** What a strike takes from a creature with no attack of its own (a cat, a sheep): one and a half hearts. */
    static final float NO_ATTACK_DAMAGE = 3.0f;
    /** The gap between the two that a strike reaches across, past their touching sides: a block and a half. */
    static final double REACH = 1.5;
    /** The walk's speed, as a multiple of the creature's own. */
    static final double SPEED = 1.25;

    private ArenaAllies() {
    }

    /** One of the wave's mobs still standing, where it is. */
    record Foe(UUID id, double x, double y, double z) {
    }

    /**
     * The foe a creature at (x, y, z) fights: the one it already fights while that one still stands, else the nearest,
     * the lower id between two as near. Null when the wave has none left.
     */
    static UUID pick(double x, double y, double z, UUID current, List<Foe> foes) {
        if (current != null) {
            for (Foe f : foes) {
                if (f.id().equals(current)) {
                    return current;
                }
            }
        }
        Foe best = null;
        double bestD = Double.MAX_VALUE;
        for (Foe f : foes) {
            double dx = f.x() - x;
            double dy = f.y() - y;
            double dz = f.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD || (d == bestD && best != null && f.id().compareTo(best.id()) < 0)) {
                best = f;
                bestD = d;
            }
        }
        return best == null ? null : best.id();
    }

    /** True when two bodies (their widths) whose centres are {@code distSq} apart are close enough to strike. */
    static boolean inReach(double distSq, double width, double foeWidth) {
        double r = (width + foeWidth) / 2 + REACH;
        return distSq <= r * r;
    }

    /** True when a creature that last struck at {@code lastStrike} (or never, -1) may strike again at tick {@code now}. */
    static boolean mayStrike(long lastStrike, long now) {
        return lastStrike < 0 || now - lastStrike >= STRIKE_COOLDOWN_TICKS;
    }

    /** What a strike takes: the creature's own attack when it has one above nothing, else {@link #NO_ATTACK_DAMAGE}. */
    static float damage(double ownAttack) {
        return ownAttack > 0 ? (float) ownAttack : NO_ATTACK_DAMAGE;
    }
}
