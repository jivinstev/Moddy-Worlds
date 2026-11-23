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
    void aWishIsAimedInsideTheArenaAndNowhereElse() {
        assertTrue(P.inReach(0, 0), "the arena's middle");
        assertTrue(P.inReach(15, -15), "the arena's corner");
        assertFalse(P.inReach(16, 0), "the wall");
        assertFalse(P.inReach(0, 16), "the gate's wall");
        assertFalse(P.inReach(0, 21), "the prep room: a wish stays inside the arena");
        assertFalse(P.inReach(40, 0), "beyond the wall");
        ArenaPlace.Columns area = P.area();
        assertTrue(area.contains(0, 21), "nothing reaches past the place, and the prep room is in it");
        assertTrue(area.contains(-16, -16) && area.contains(16, 26));
        assertFalse(area.contains(0, 27), "behind the prep room");
        ArenaPlace.Columns r = P.reach();
        assertTrue(area.contains(r.minX(), r.minZ()) && area.contains(r.maxX(), r.maxZ()), "the reach is in the area");
    }

    @Test
    void aPlaceIsBuiltOnceAndKeptWhenTheRuleChangesLater() {
        ArenaPlace.Start first = ArenaPlace.onStart(null, P);
        assertTrue(first.build());
        assertEquals(P, first.place());
        ArenaPlace.Start again = ArenaPlace.onStart(P, P);
        assertFalse(again.build(), "a restart builds nothing");
        assertEquals("", again.note());
        ArenaPlace bigger = new ArenaPlace(41, 9, 4, 64);
        ArenaPlace.Start changed = ArenaPlace.onStart(P, bigger);
        assertFalse(changed.build(), "what stands is kept");
        assertEquals(P, changed.place(), "and its reach with it");
        assertTrue(changed.note().contains("arena 41") && changed.note().contains("arena 31"), changed.note());
        assertEquals(P, ArenaPlace.onStart(null, null).place(), "no rule: the default");
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
        assertEquals(P, ArenaPlace.parse(Map.of("_note", "a note")).place(), "a note is not a param");
    }

    @Test
    void everySizeInRangeStillBuildsAClosedPlace() {
        for (int a = ArenaPlace.MIN_ARENA; a <= ArenaPlace.MAX_ARENA; a += 2) {
            for (int p = ArenaPlace.MIN_PREP; p <= ArenaPlace.MAX_PREP && p < a; p += 2) {
                ArenaPlace place = new ArenaPlace(a, p, ArenaPlace.MIN_WALL, 64);
                int[] arr = place.arrival();
                assertTrue(place.prepInside().contains(arr[0], arr[1], arr[2]));
                assertFalse(place.inReach(arr[0], arr[2]), "he arrives outside the reach, and aims into it");
                assertTrue(place.area().contains(arr[0], arr[2]));
                assertTrue(place.inReach(0, 0));
                assertTrue(place.footprint().containsColumn(place.prepInside().x1() + 1, place.prepInside().z1() + 1));
            }
        }
    }
    @Test
    void theStartButtonIsInThePrepRoomBesideTheGateOnTheSharedWall() {
        Map<List<Integer>, Kind> m = built(P);
        int[] b = P.startButton();
        assertEquals(List.of(2, 65, 17), List.of(b[0], b[1], b[2]));
        assertTrue(P.prepInside().contains(b[0], b[1], b[2]), "a player in the prep room reaches it");
        assertEquals(Kind.START_BUTTON, m.get(List.of(b[0], b[1], b[2])));
        assertEquals(Kind.CONTROL, m.get(List.of(b[0], b[1], b[2] - 1)), "it is fixed to the wall north of it");
        assertEquals(Kind.START_SIGN, m.get(List.of(b[0], b[1] + 1, b[2])));
        assertEquals(Kind.CONTROL, m.get(List.of(b[0], b[1] + 1, b[2] - 1)));
        for (ArenaPlace p : List.of(new ArenaPlace(15, 5, 3, 64), new ArenaPlace(63, 15, 8, 64))) {
            int[] c = p.startButton();
            assertTrue(p.prepInside().contains(c[0], c[1], c[2]));
            assertTrue(c[1] + 1 <= p.floorY() + p.wallHeight() - 1, "the sign's block is in the wall's window");
        }
    }

    @Test
    void upgradesAreGivenOnceInOrder() {
        assertEquals(ArenaPlace.UPGRADES, ArenaPlace.pendingUpgrades(java.util.Set.of()));
        assertEquals(List.of(ArenaPlace.UPGRADE_NO_NATURAL_SPAWNS),
                ArenaPlace.pendingUpgrades(java.util.Set.of(ArenaPlace.UPGRADE_START_CONTROL)));
        assertTrue(ArenaPlace.pendingUpgrades(java.util.Set.copyOf(ArenaPlace.UPGRADES)).isEmpty());
    }

    @Test
    void aMobIsKeptInsideThePlaceAndLostBelowTheFloorOrPastTheWalls() {
        assertTrue(P.keeps(0.5, 64, 0.5));
        assertTrue(P.keeps(0.5, 64, 21.5), "in the prep room, through an open gate");
        assertTrue(!P.keeps(0.5, 61.9, 0.5), "fallen below the floor");
        assertTrue(!P.keeps(20.5, 64, 0.5), "past the east wall");
        assertTrue(!P.keeps(0.5, 64, -18.5), "past the north wall");
    }

    @Test
    void aResetPutsBackTheArenaAsBuiltAndLeavesThePrepRoomAlone() {
        Map<List<Integer>, Kind> m = built(P);
        ArenaPlace.Reset r = P.reset();
        Box box = P.resetBox(319);
        for (int x = box.x0(); x <= box.x1(); x++) {
            for (int z = box.z0(); z <= box.z1(); z++) {
                for (int y = box.y0(); y <= box.y1(); y++) {
                    Kind k = r.kindAt(x, y, z);
                    Kind built = m.get(List.of(x, y, z));
                    if (built == Kind.PREP_FLOOR || built == Kind.PREP_ROOF || built == Kind.PREP_LIGHT) {
                        assertEquals(null, k, "the prep room's block at " + x + "," + y + "," + z + " is left");
                    } else if (built != null) {
                        assertEquals(built, k, "put back as built at " + x + "," + y + "," + z);
                    } else if (y >= P.floorY()) {
                        assertEquals(Kind.AIR, k, "cleared at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(Kind.AIR, r.kindAt(0, 300, 0), "a wished tower is cleared to the sky");
        assertEquals(Kind.ARENA_FLOOR, r.kindAt(0, 63, 0));
        assertEquals(Kind.GATE, r.kindAt(0, 64, 16));
        assertEquals(null, r.kindAt(0, 64, 21), "never inside the prep room");
        assertEquals(null, r.kindAt(2, 65, 17), "the button is the prep room's");
        assertEquals(null, r.kindAt(0, 62, 0), "never below the floor");
    }
}
