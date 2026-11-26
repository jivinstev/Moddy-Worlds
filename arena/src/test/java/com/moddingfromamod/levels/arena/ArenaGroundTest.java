package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import com.moddingfromamod.levels.arena.ArenaPlace.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The base the place stands on: nobody falls out of it, and the spawn always has a floor. */
class ArenaGroundTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;

    /** What the place's fills leave in each block, a later fill winning. */
    private static Map<List<Integer>, Kind> built(ArenaPlace p) {
        Map<List<Integer>, Kind> m = new HashMap<>();
        for (Fill f : p.fills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        m.put(List.of(x, y, z), f.kind());
                    }
                }
            }
        }
        return m;
    }

    @Test
    void theBaseIsUnderTheWholePlaceAndRoundIt() {
        Box f = P.footprint();
        Box b = P.baseBox();
        assertEquals(new Box(f.x0() - 2, 61, f.z0() - 2, f.x1() + 2, 63, f.z1() + 2), b);
        assertEquals(61, P.bedrockY());
        Map<List<Integer>, Kind> m = built(P);
        for (int x = b.x0(); x <= b.x1(); x++) {
            for (int z = b.z0(); z <= b.z1(); z++) {
                assertEquals(Kind.BASE_BEDROCK, m.get(List.of(x, 61, z)), "bedrock under every column: " + x + "," + z);
                assertEquals(Kind.BASE_FILL, m.get(List.of(x, 62, z)));
                Kind top = m.get(List.of(x, 63, z));
                assertTrue(top == Kind.GROUND || top == Kind.ARENA_FLOOR || top == Kind.PREP_FLOOR
                        || top == Kind.PREP_LIGHT || top == Kind.HAND_OVER_SPOT, "the floors' level is a floor or ground: " + top);
            }
        }
        assertEquals(Kind.ARENA_FLOOR, m.get(List.of(0, 63, 0)), "the arena's floor covers the ground");
        assertEquals(Kind.PREP_FLOOR, m.get(List.of(P.spawnFloor()[0], 63, P.spawnFloor()[2])),
                "the spawn stands on the prep room's floor");
        assertEquals(Kind.GROUND, m.get(List.of(b.x0(), 63, b.z0())), "ground round the place");
        assertEquals(P.baseFills(), P.fills().subList(0, 3), "the base goes in first; everything stands on it");
    }

    @Test
    void aBlastThatBreaksTheFloorDropsYouTwoBlocksAtMost() {
        // the floor and the fill layer broken: he stands on the bedrock, two blocks down, still in the arena
        int standOn = P.bedrockY() + 1;
        assertEquals(P.floorY() - 2, standOn);
        assertTrue(P.keeps(3.5, standOn, 3.5), "in the place");
        assertTrue(ArenaGate.inArena(P, 3.5, standOn, 3.5), "and in the arena");
        assertFalse(P.fellOut(3.5, standOn, 3.5), "not fallen out");
    }

    @Test
    void whoHasFallenOut() {
        assertTrue(P.fellOut(0.5, 40, 0.5), "under the arena");
        assertTrue(P.fellOut(0.5, -70, 20.5), "in the void under the prep room");
        assertTrue(P.fellOut(30.5, 10, 0.5), "beside the place, far below it");
        assertFalse(P.fellOut(0.5, 64, 0.5), "on the floor");
        assertFalse(P.fellOut(0.5, 61, 0.5), "on the bedrock's level");
        assertFalse(P.fellOut(500.5, 10, 0.5), "somewhere else in the world: not the arena's business");
        int[] a = P.arrival();
        assertFalse(P.fellOut(a[0] + 0.5, a[1], a[2] + 0.5), "the arrival spot is safe");
    }

    @Test
    void theResetMendsTheBaseUnderTheArenaAndNeverTheGround() {
        ArenaPlace.Reset r = P.reset();
        assertEquals(61, P.resetBox(400).y0(), "the reset reaches down to the bedrock");
        assertEquals(Kind.BASE_BEDROCK, r.kindAt(3, 61, 3));
        assertEquals(Kind.BASE_FILL, r.kindAt(3, 62, 3), "a blast's hole is filled back");
        assertEquals(Kind.ARENA_FLOOR, r.kindAt(3, 63, 3));
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = 61; y <= 63; y++) {
                    Kind k = r.kindAt(x, y, z);
                    assertTrue(k != Kind.AIR, "a reset never leaves a hole in the floor or under it: " + x + "," + y
                            + "," + z);
                    assertTrue(k != Kind.GROUND);
                }
            }
        }
    }

    @Test
    void theSpawnFloorAndThePrepFloor() {
        int[] a = P.arrival();
        assertEquals(List.of(a[0], a[1] - 1, a[2]), List.of(P.spawnFloor()[0], P.spawnFloor()[1], P.spawnFloor()[2]));
        Box f = P.prepFloor();
        assertTrue(f.contains(P.spawnFloor()[0], P.spawnFloor()[1], P.spawnFloor()[2]));
        Map<List<Integer>, Kind> m = built(P);
        for (int x = f.x0(); x <= f.x1(); x++) {
            for (int z = f.z0(); z <= f.z1(); z++) {
                Kind k = m.get(List.of(x, f.y0(), z));
                int[] h = P.handOverSpot();
                Kind want = P.isPrepLight(x, z) ? Kind.PREP_LIGHT
                        : x == h[0] && z == h[2] ? Kind.HAND_OVER_SPOT : Kind.PREP_FLOOR;
                assertEquals(want, k, x + "," + z);
            }
        }
    }

    @Test
    void anOldPlaceGetsItsBaseOnce() {
        assertTrue(ArenaPlace.UPGRADES.contains(ArenaPlace.UPGRADE_BASE));
        assertTrue(ArenaPlace.pendingUpgrades(java.util.Set.of(ArenaPlace.UPGRADE_START_CONTROL,
                ArenaPlace.UPGRADE_NO_NATURAL_SPAWNS, ArenaPlace.UPGRADE_CONTROL_PANEL, ArenaPlace.UPGRADE_CHAMPIONS))
                .equals(List.of(ArenaPlace.UPGRADE_BASE, ArenaPlace.UPGRADE_LADDER_BOARD,
                        ArenaPlace.UPGRADE_HAND_OVER_SPOT)));
    }
}
