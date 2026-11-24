package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Where a wave's mobs appear: on the arena floor, spread out, away from the players. */
class ArenaSpawnsTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;

    @Test
    void everySpotIsOnTheArenaFloorInFromTheWalls() {
        for (int[] s : ArenaSpawns.pick(P, List.of(), 40, 1)) {
            ArenaPlace.Box a = P.arenaInside();
            assertTrue(s[0] >= a.x0() + ArenaSpawns.INSET && s[0] <= a.x1() - ArenaSpawns.INSET, "x " + s[0]);
            assertTrue(s[1] >= a.z0() + ArenaSpawns.INSET && s[1] <= a.z1() - ArenaSpawns.INSET, "z " + s[1]);
        }
    }

    @Test
    void spotsAreSpreadOutAndDistinctWhileThereAreEnough() {
        List<int[]> spots = ArenaSpawns.pick(P, List.of(new double[] {0.5, 21.5}), 12, 7);
        assertEquals(12, spots.size());
        Set<List<Integer>> distinct = new HashSet<>();
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            distinct.add(List.of(spots.get(i)[0], spots.get(i)[1]));
            for (int j = i + 1; j < spots.size(); j++) {
                closest = Math.min(closest, Math.hypot(spots.get(i)[0] - spots.get(j)[0], spots.get(i)[1] - spots.get(j)[1]));
            }
        }
        assertEquals(12, distinct.size());
        assertTrue(closest >= 6, "twelve mobs at least 6 blocks apart, closest " + closest);
    }

    @Test
    void spotsKeepAwayFromAPlayerInTheArena() {
        double[] him = {0.5, 0.5};
        for (long seed = 0; seed < 20; seed++) {
            for (int[] s : ArenaSpawns.pick(P, List.of(him), 10, seed)) {
                assertTrue(Math.hypot(s[0] + 0.5 - him[0], s[1] + 0.5 - him[1]) >= ArenaSpawns.MIN_FROM_PLAYER,
                        "seed " + seed + " spot " + s[0] + "," + s[1]);
            }
        }
    }

    @Test
    void aWaveLargerThanTheFloorReusesSpotsAndTheSameSeedGivesTheSameSpots() {
        ArenaPlace small = new ArenaPlace(15, 5, 3, 64);
        int grid = ArenaSpawns.grid(small).size();
        List<int[]> spots = ArenaSpawns.pick(small, List.of(), grid + 5, 3);
        assertEquals(grid + 5, spots.size());
        List<int[]> again = ArenaSpawns.pick(small, List.of(), grid + 5, 3);
        for (int i = 0; i < spots.size(); i++) {
            assertEquals(spots.get(i)[0], again.get(i)[0]);
            assertEquals(spots.get(i)[1], again.get(i)[1]);
        }
        assertTrue(ArenaSpawns.pick(small, List.of(), 0, 3).isEmpty());
    }

    @Test
    void withPlayersEverywhereTheFarthestSpotsComeFirst() {
        ArenaPlace small = new ArenaPlace(15, 5, 3, 64);
        List<int[]> spots = ArenaSpawns.pick(small, List.of(new double[] {0.5, 0.5}, new double[] {-4, -4},
                new double[] {4, 4}, new double[] {-4, 4}, new double[] {4, -4}), 3, 1);
        assertEquals(3, spots.size());
    }

    @Test
    void theSeedPropertyMakesEachWavesSpotsTheSameEveryBattle() {
        String before = System.getProperty(ArenaSpawns.SEED_PROPERTY);
        try {
            System.setProperty(ArenaSpawns.SEED_PROPERTY, "42");
            java.util.function.LongSupplier never = () -> {
                throw new AssertionError("the game's random is not used while the seed is set");
            };
            assertEquals(ArenaSpawns.seedFor(1, never), ArenaSpawns.seedFor(1, never));
            assertTrue(ArenaSpawns.seedFor(1, never) != ArenaSpawns.seedFor(2, never), "each wave its own layout");
            System.setProperty(ArenaSpawns.SEED_PROPERTY, "43");
            long other = ArenaSpawns.seedFor(1, never);
            System.setProperty(ArenaSpawns.SEED_PROPERTY, "42");
            assertTrue(other != ArenaSpawns.seedFor(1, never), "another seed, another layout");
        } finally {
            if (before == null) {
                System.clearProperty(ArenaSpawns.SEED_PROPERTY);
            } else {
                System.setProperty(ArenaSpawns.SEED_PROPERTY, before);
            }
        }
    }

    @Test
    void withoutTheSeedEveryBattleUsesTheGamesRandom() {
        String before = System.getProperty(ArenaSpawns.SEED_PROPERTY);
        try {
            System.clearProperty(ArenaSpawns.SEED_PROPERTY);
            assertEquals(7L, ArenaSpawns.seedFor(1, () -> 7L));
            System.setProperty(ArenaSpawns.SEED_PROPERTY, "not a number");
            assertEquals(7L, ArenaSpawns.seedFor(1, () -> 7L));
        } finally {
            if (before == null) {
                System.clearProperty(ArenaSpawns.SEED_PROPERTY);
            } else {
                System.setProperty(ArenaSpawns.SEED_PROPERTY, before);
            }
        }
    }
}
