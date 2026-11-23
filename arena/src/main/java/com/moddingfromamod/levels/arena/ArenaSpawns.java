package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * <b>Where a wave's mobs appear</b>: spots on the arena floor, spread out from each other and away from the players.
 * Pure: the players' positions come in as numbers, and a seed makes it repeatable.
 *
 * <p>The spots are on a grid every {@link #STEP} blocks, at least {@link #INSET} blocks in from the walls. Spots closer
 * than {@link #MIN_FROM_PLAYER} to any player are left out while there are others; then the spots are chosen one at a
 * time, each the farthest from those already chosen (ties broken by the seed). A wave larger than the grid reuses spots
 * in the same order.
 */
public final class ArenaSpawns {

    public static final int STEP = 2;
    public static final int INSET = 2;
    public static final double MIN_FROM_PLAYER = 8.0;

    private ArenaSpawns() {
    }

    /** Every candidate spot (x, z) on the arena floor. */
    static List<int[]> grid(ArenaPlace place) {
        ArenaPlace.Box a = place.arenaInside();
        List<int[]> out = new ArrayList<>();
        for (int x = a.x0() + INSET; x <= a.x1() - INSET; x += STEP) {
            for (int z = a.z0() + INSET; z <= a.z1() - INSET; z += STEP) {
                out.add(new int[] {x, z});
            }
        }
        return out;
    }

    /**
     * {@code n} spots (x, z), in the order to use them. {@code players} holds each player's (x, z) near the place.
     */
    public static List<int[]> pick(ArenaPlace place, List<double[]> players, int n, long seed) {
        List<int[]> out = new ArrayList<>();
        if (n <= 0) {
            return out;
        }
        List<int[]> all = grid(place);
        Collections.shuffle(all, new Random(seed));
        List<int[]> far = new ArrayList<>();
        for (int[] c : all) {
            if (nearestPlayer(c, players) >= MIN_FROM_PLAYER) {
                far.add(c);
            }
        }
        List<int[]> pool = far.isEmpty() ? all : far;
        if (far.isEmpty()) {
            // nowhere far enough: the spots farthest from the players first
            pool.sort((p, q) -> Double.compare(nearestPlayer(q, players), nearestPlayer(p, players)));
        }
        List<int[]> chosen = new ArrayList<>();
        List<int[]> left = new ArrayList<>(pool);
        chosen.add(left.remove(0));
        while (chosen.size() < Math.min(n, pool.size())) {
            int best = 0;
            double bestD = -1;
            for (int i = 0; i < left.size(); i++) {
                double d = nearest(left.get(i), chosen);
                if (d > bestD) {
                    bestD = d;
                    best = i;
                }
            }
            chosen.add(left.remove(best));
        }
        for (int i = 0; out.size() < n; i++) {
            out.add(chosen.get(i % chosen.size()));
        }
        return out;
    }

    static double nearestPlayer(int[] c, List<double[]> players) {
        double best = Double.MAX_VALUE;
        for (double[] p : players) {
            best = Math.min(best, Math.hypot(c[0] + 0.5 - p[0], c[1] + 0.5 - p[1]));
        }
        return best;
    }

    private static double nearest(int[] c, List<int[]> spots) {
        double best = Double.MAX_VALUE;
        for (int[] s : spots) {
            best = Math.min(best, Math.hypot(c[0] - s[0], c[1] - s[1]));
        }
        return best;
    }
}
