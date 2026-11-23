package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import com.moddingfromamod.levels.arena.ArenaPlace.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The arena's place: its sizes, its boxes, where a player arrives and where a wish may land. */
class ArenaPlaceTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;

    /** The block each position ends up as, after every fill in order (later fills win). */
    private static Map<List<Integer>, Kind> built(ArenaPlace place) {
        Map<List<Integer>, Kind> out = new HashMap<>();
        for (Fill f : place.fills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        out.put(List.of(x, y, z), f.kind());
                    }
                }
            }
        }
        out.values().removeIf(k -> k == Kind.AIR);
        return out;
    }

    private static boolean solid(Map<List<Integer>, Kind> m, int x, int y, int z) {
        Kind k = m.get(List.of(x, y, z));
        return k != null && k != Kind.GATE;
    }

    @Test
    void theDefaultIsAThirtyOneArenaAndANinePrepRoom() {
        assertEquals(new Box(-15, 64, -15, 15, 67, 15), P.arenaInside());
        assertEquals(new Box(-4, 64, 17, 4, 67, 25), P.prepInside());
        assertEquals(16, P.sharedWallZ());
        assertEquals(31 * 31, (P.arenaInside().x1() - P.arenaInside().x0() + 1)
                * (P.arenaInside().z1() - P.arenaInside().z0() + 1));
    }

    @Test
    void aPlayerArrivesInTheMiddleOfThePrepRoomOnItsFloor() {
        int[] a = P.arrival();
        assertTrue(P.prepInside().contains(a[0], a[1], a[2]));
        assertFalse(P.arenaInside().contains(a[0], a[1], a[2]));
        assertEquals(List.of(0, 64, 21), List.of(a[0], a[1], a[2]));
        Map<List<Integer>, Kind> m = built(P);
        assertEquals(Kind.PREP_FLOOR, m.get(List.of(a[0], a[1] - 1, a[2])), "he stands on the floor");
        assertNull(m.get(List.of(a[0], a[1], a[2])));
        assertNull(m.get(List.of(a[0], a[1] + 1, a[2])));
    }

    @Test
    void everyFloorColumnInsideBothRoomsIsFloored() {
        Map<List<Integer>, Kind> m = built(P);
        for (Box b : List.of(P.arenaInside(), P.prepInside())) {
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int z = b.z0(); z <= b.z1(); z++) {
                    assertNotNull(m.get(List.of(x, b.y0() - 1, z)), "no hole to the void at " + x + "," + z);
                    assertNull(m.get(List.of(x, b.y0(), z)), "the inside is clear at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void theArenaIsClosedAllRoundExceptByTheGate() {
        Map<List<Integer>, Kind> m = built(P);
        Box a = P.arenaInside();
        for (int y = a.y0(); y <= a.y1(); y++) {
            for (int i = a.x0(); i <= a.x1(); i++) {
                assertTrue(solid(m, i, y, a.z0() - 1), "north wall " + i + "," + y);
                assertTrue(solid(m, a.x0() - 1, y, i), "west wall " + i + "," + y);
                assertTrue(solid(m, a.x1() + 1, y, i), "east wall " + i + "," + y);
                boolean gateColumn = i == 0 && y <= a.y0() + 1;
                assertEquals(!gateColumn, solid(m, i, y, a.z1() + 1), "south wall " + i + "," + y);
            }
        }
        int[] g = P.gate();
        assertEquals(Kind.GATE, m.get(List.of(g[0], g[1], g[2])));
        assertEquals(Kind.ARENA_RIM, m.get(List.of(a.x0(), a.y1() + 1, 0)), "the rim overhangs inward");
    }

    @Test
    void thePrepRoomIsClosedRoofedAndLit() {
        Map<List<Integer>, Kind> m = built(P);
        Box p = P.prepInside();
        for (int y = p.y0(); y <= p.y1(); y++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                assertTrue(solid(m, p.x0() - 1, y, z));
                assertTrue(solid(m, p.x1() + 1, y, z));
            }
            for (int x = p.x0(); x <= p.x1(); x++) {
                assertTrue(solid(m, x, y, p.z1() + 1));
                assertTrue(m.containsKey(List.of(x, y, p.z0() - 1)) || (x == 0 && y <= p.y0() + 1));
            }
        }
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                assertEquals(Kind.PREP_ROOF, m.get(List.of(x, p.y1() + 1, z)));
            }
        }
        long lights = m.values().stream().filter(k -> k == Kind.PREP_LIGHT).count();
        assertEquals(4, lights);
        assertEquals(Kind.GLASS, m.get(List.of(-3, 65, 16)), "a window onto the arena");
    }

    @Test
    void aWishLandsInsideTheArenaOrThePrepRoomAndNowhereElse() {
        assertTrue(P.inReach(0, 64, 0), "the arena's middle");
        assertTrue(P.inReach(15, 64, -15), "the arena's corner");
        assertTrue(P.inReach(0, 64 + 20, 0), "above the arena floor");
        assertTrue(P.inReach(0, 64, 21), "the prep room");
        assertFalse(P.inReach(16, 64, 0), "the wall");
        assertFalse(P.inReach(40, 64, 0), "beyond the wall");
        assertFalse(P.inReach(0, 64, 30), "behind the prep room");
        assertFalse(P.inReach(0, 64 + ArenaPlace.REACH_UP + 1, 0), "too high");
        assertFalse(P.inReach(0, 0, 0), "the void below");
        Box f = P.footprint();
        for (Box b : P.reach()) {
            assertTrue(f.containsColumn(b.x0(), b.z0()) && f.containsColumn(b.x1(), b.z1()));
        }
    }

    @Test
    void sizesOutsideTheirRangeAreRefusedInWords() {
        assertNull(ArenaPlace.problem(31, 9, 4));
        assertNotNull(ArenaPlace.problem(30, 9, 4), "even");
        assertNotNull(ArenaPlace.problem(13, 9, 4), "too small");
        assertNotNull(ArenaPlace.problem(65, 9, 4), "too big");
        assertNotNull(ArenaPlace.problem(31, 4, 4));
        assertNotNull(ArenaPlace.problem(15, 15, 4), "the prep room is smaller than the arena");
        assertNotNull(ArenaPlace.problem(31, 9, 2));
        assertNotNull(ArenaPlace.problem(31, 9, 9));
        assertThrows(IllegalArgumentException.class, () -> new ArenaPlace(30, 9, 4, 64));
    }

    @Test
    void aRulesParamsParseOrSayWhatIsWrong() {
        assertEquals(P, ArenaPlace.parse(Map.of()).place());
        assertEquals(P, ArenaPlace.parse(null).place());
        assertEquals(new ArenaPlace(21, 7, 5, 64),
                ArenaPlace.parse(Map.of("arenaSize", 21.0, "prepSize", 7, "wallHeight", 5L)).place());
        ArenaPlace.Parsed bad = ArenaPlace.parse(Map.of("arenaSize", 20));
        assertFalse(bad.ok());
        assertTrue(bad.problem().contains("arenaSize"));
        assertFalse(ArenaPlace.parse(Map.of("arenaSize", 21.5)).ok());
        assertFalse(ArenaPlace.parse(Map.of("arenaSize", "big")).ok());
        assertTrue(ArenaPlace.parse(Map.of("size", 21)).problem().contains("unknown"));
    }

    @Test
    void everySizeInRangeStillBuildsAClosedPlace() {
        for (int a = ArenaPlace.MIN_ARENA; a <= ArenaPlace.MAX_ARENA; a += 2) {
            for (int p = ArenaPlace.MIN_PREP; p <= ArenaPlace.MAX_PREP && p < a; p += 2) {
                ArenaPlace place = new ArenaPlace(a, p, ArenaPlace.MIN_WALL, 64);
                int[] arr = place.arrival();
                assertTrue(place.prepInside().contains(arr[0], arr[1], arr[2]));
                assertTrue(place.inReach(arr[0], arr[1], arr[2]));
                assertTrue(place.footprint().containsColumn(place.prepInside().x1() + 1, place.prepInside().z1() + 1));
            }
        }
    }
}
