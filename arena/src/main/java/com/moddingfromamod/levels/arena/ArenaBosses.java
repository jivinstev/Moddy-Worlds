package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The arena's bosses</b>: who comes on a boss wave (every 5th wave by default: 5, 10, 15, ...), as data. Pure: no
 * Minecraft. Each boss is a vanilla 1.21.8 mob, named, and made tougher only through its attributes (its most health,
 * its attack, its size and how hard it is to knock back), so a stock client draws it as it is.
 *
 * <p>The table escalates: the first boss wave brings the first boss, the next the second, and so on; past the table's
 * end it starts again with more health each time round ({@link #boss}). The difficulty multiplies a boss's health as
 * it does every mob's ({@link ArenaRun}). A boss wave also brings a small escort: a third of the wave's own monsters
 * ({@link #escort}).
 *
 * <p>Not here, on purpose: the wither (it flies over the walls and blows the arena up) and the warden (too
 * frightening for a child, it blinds with darkness and digs). Every boss walks, stays in the arena and is none of
 * {@link ArenaWaves#REFUSED}; the piglin brute is kept from turning into a zombie in the Overworld.
 */
public final class ArenaBosses {

    private ArenaBosses() {
    }

    /**
     * One boss: its name over its head, its mob, its most health (half-hearts, before the difficulty), what its attack
     * is multiplied by, how big it is (1 is its own size) and its knockback resistance (0 to 1).
     */
    public record Boss(String name, String mob, double health, double damage, double scale, double knockback) {
    }

    /** The most health a mob may have in Minecraft (the attribute's limit). */
    public static final double MAX_HEALTH = 1024;

    /** The bosses, in the order they come. */
    public static final List<Boss> TABLE = List.of(
            new Boss("Rumble the Ravager", "minecraft:ravager", 150, 1.0, 1.2, 0.8),
            new Boss("Captain Chop", "minecraft:vindicator", 200, 1.5, 1.6, 0.6),
            new Boss("Bruno the Brute", "minecraft:piglin_brute", 250, 1.3, 1.6, 0.7),
            new Boss("King Husk", "minecraft:husk", 300, 2.0, 2.0, 0.8),
            new Boss("Thunderhoof", "minecraft:ravager", 400, 1.5, 1.5, 1.0));

    /** True when wave {@code n} is a boss wave, with a boss every {@code every} waves (0: never). */
    public static boolean isBossWave(int n, int every) {
        return every > 0 && n > 0 && n % every == 0;
    }

    /**
     * Wave {@code n}'s boss, or null when it is not a boss wave. The k-th boss wave (counting from 0) brings
     * {@code TABLE[k % 5]}; each time round the table again its health is times one more (x2, x3, ...), never above
     * {@link #MAX_HEALTH}.
     */
    public static Boss boss(int n, int every) {
        if (!isBossWave(n, every)) {
            return null;
        }
        int k = n / every - 1;
        Boss b = TABLE.get(k % TABLE.size());
        int round = k / TABLE.size();
        if (round == 0) {
            return b;
        }
        return new Boss(b.name(), b.mob(), Math.min(MAX_HEALTH, b.health() * (1 + round)), b.damage(), b.scale(),
                b.knockback());
    }

    /** The most monsters a boss's escort brings. */
    public static final int MAX_ESCORT = 10;

    /**
     * A boss wave's escort: each kind of the wave's monsters a third as many (rounded, at least 1), at most
     * {@link #MAX_ESCORT} in all (the biggest group gives one back until it fits).
     */
    public static ArenaWaves.Wave escort(ArenaWaves.Wave wave) {
        int[] c = new int[wave.groups().size()];
        int total = 0;
        for (int i = 0; i < c.length; i++) {
            c[i] = Math.max(1, (int) Math.round(wave.groups().get(i).count() / 3.0));
            total += c[i];
        }
        while (total > MAX_ESCORT) {
            int big = 0;
            for (int i = 1; i < c.length; i++) {
                if (c[i] > c[big]) {
                    big = i;
                }
            }
            if (c[big] <= 1) {
                break;
            }
            c[big]--;
            total--;
        }
        List<ArenaWaves.Group> groups = new ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            groups.add(new ArenaWaves.Group(wave.groups().get(i).mob(), c[i]));
        }
        return new ArenaWaves.Wave(groups);
    }
}
