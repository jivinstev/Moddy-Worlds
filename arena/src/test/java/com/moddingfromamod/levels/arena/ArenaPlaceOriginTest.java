package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Columns;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A place can stand anywhere in a world: every coordinate a place at origin (ox, oz) gives is the (0, 0) place's
 * moved by (ox, oz), and every "is this in it" test agrees once the point is moved too. At (0, 0) nothing changes.
 */
class ArenaPlaceOriginTest {

    private static final int[][] ORIGINS = {{0, 0}, {1000, -2000}, {-37, 513}, {-30_000, 29_999}};

    /** The smallest, the default and the largest place, so every panel and board layout is covered. */
    private static final List<ArenaPlace> SHAPES = List.of(new ArenaPlace(15, 5, 3, 64), ArenaPlace.DEFAULT,
            new ArenaPlace(63, 15, 8, 70), new ArenaPlace(21, 7, 5, -10));

    private static Box shift(Box b, int dx, int dz) {
        return new Box(b.x0() + dx, b.y0(), b.z0() + dz, b.x1() + dx, b.y1(), b.z1() + dz);
    }

    private static Columns shift(Columns c, int dx, int dz) {
        return new Columns(c.minX() + dx, c.maxX() + dx, c.minZ() + dz, c.maxZ() + dz);
    }

    private static int[] shift3(int[] p, int dx, int dz) {
        return new int[] {p[0] + dx, p[1], p[2] + dz};
    }

    private static void eq3(int[] base, int[] moved, int dx, int dz, String what) {
        assertArrayEquals(shift3(base, dx, dz), moved, what);
    }

    private static void eqList3(List<int[]> base, List<int[]> moved, int dx, int dz, String what) {
        assertEquals(base.size(), moved.size(), what);
        for (int i = 0; i < base.size(); i++) {
            eq3(base.get(i), moved.get(i), dx, dz, what + " #" + i);
        }
    }

    private static void eqFills(List<Fill> base, List<Fill> moved, int dx, int dz, String what) {
        assertEquals(base.size(), moved.size(), what);
        for (int i = 0; i < base.size(); i++) {
            assertEquals(base.get(i).kind(), moved.get(i).kind(), what + " #" + i);
            assertEquals(shift(base.get(i).box(), dx, dz), moved.get(i).box(), what + " #" + i);
        }
    }

    private static void near(double want, double got, String what) {
        assertEquals(want, got, 1e-9, what);
    }

    @Test
    void theFourNumberPlaceIsCentredOnZeroAndAtMovesOnlyTheOrigin() {
        ArenaPlace p = new ArenaPlace(31, 9, 4, 64);
        assertEquals(0, p.centreX());
        assertEquals(0, p.centreZ());
        assertEquals(new ArenaPlace(31, 9, 4, 64, 0, 0), p);
        assertEquals(p, p.at(0, 0));
        ArenaPlace m = p.at(1000, -2000);
        assertEquals(1000, m.centreX());
        assertEquals(-2000, m.centreZ());
        assertEquals(1000, m.ox());
        assertEquals(-2000, m.oz());
        assertEquals(p.arenaSize(), m.arenaSize());
        assertEquals(p.prepSize(), m.prepSize());
        assertEquals(p.wallHeight(), m.wallHeight());
        assertEquals(p.floorY(), m.floorY());
        assertTrue(m.sameShape(p) && !m.equals(p));
        assertEquals(p, m.at(0, 0));
        assertEquals("arena 31, prep room 9, wall 4", p.describe(), "a place at (0, 0) reads as it always did");
        assertEquals("arena 31, prep room 9, wall 4 at x 1000, z -2000", m.describe());
    }

    @Test
    void everyBoxAndSpotOfThePlaceMovesWithItsOrigin() {
        for (ArenaPlace base : SHAPES) {
            for (int[] o : ORIGINS) {
                int dx = o[0];
                int dz = o[1];
                ArenaPlace m = base.at(dx, dz);
                String w = base.describe() + " at " + dx + "," + dz + ": ";
                assertEquals(shift(base.arenaInside(), dx, dz), m.arenaInside(), w + "arenaInside");
                assertEquals(shift(base.prepInside(), dx, dz), m.prepInside(), w + "prepInside");
                assertEquals(shift(base.footprint(), dx, dz), m.footprint(), w + "footprint");
                assertEquals(shift(base.baseBox(), dx, dz), m.baseBox(), w + "baseBox");
                assertEquals(shift(base.prepFloor(), dx, dz), m.prepFloor(), w + "prepFloor");
                assertEquals(shift(base.resetBox(320), dx, dz), m.resetBox(320), w + "resetBox");
                assertEquals(base.sharedWallZ() + dz, m.sharedWallZ(), w + "sharedWallZ");
                assertEquals(base.bedrockY(), m.bedrockY(), w + "bedrockY");
                assertEquals(base.windowTop(), m.windowTop(), w + "windowTop");
                eq3(base.gate(), m.gate(), dx, dz, w + "gate");
                eq3(base.arrival(), m.arrival(), dx, dz, w + "arrival");
                eq3(base.spawnFloor(), m.spawnFloor(), dx, dz, w + "spawnFloor");
                eq3(base.handOverSpot(), m.handOverSpot(), dx, dz, w + "handOverSpot");
                eq3(base.startButton(), m.startButton(), dx, dz, w + "startButton");
                eqList3(base.firstStartControl(), m.firstStartControl(), dx, dz, w + "firstStartControl");
                List<int[]> lights = new ArrayList<>();
                for (int[] l : base.lights()) {
                    lights.add(new int[] {l[0] + dx, l[1] + dz});
                }
                assertEquals(lights.size(), m.lights().size());
                for (int i = 0; i < lights.size(); i++) {
                    assertArrayEquals(lights.get(i), m.lights().get(i), w + "light " + i);
                }
                eqFills(base.fills(), m.fills(), dx, dz, w + "fills");
                eqFills(base.baseFills(), m.baseFills(), dx, dz, w + "baseFills");
                eqFills(base.controlFills(), m.controlFills(), dx, dz, w + "controlFills");
                eqFills(base.championsFills(), m.championsFills(), dx, dz, w + "championsFills");
                eqFills(base.ladderFills(), m.ladderFills(), dx, dz, w + "ladderFills");
                eqFills(base.handOverFills(), m.handOverFills(), dx, dz, w + "handOverFills");
                assertEquals(shift(base.arenaReach(), dx, dz), m.arenaReach(), w + "arenaReach");
                assertEquals(shift(base.prepReach(), dx, dz), m.prepReach(), w + "prepReach");
                assertEquals(shift(base.area(), dx, dz), m.area(), w + "area");
            }
        }
    }

    @Test
    void theControlPanelMovesWithThePlace() {
        for (ArenaPlace base : SHAPES) {
            for (int[] o : ORIGINS) {
                int dx = o[0];
                int dz = o[1];
                ArenaPanel b = base.panel();
                ArenaPanel m = base.at(dx, dz).panel();
                String w = base.describe() + " at " + dx + "," + dz + ": ";
                assertEquals(b.rows(), m.rows());
                assertEquals(shift(b.board(), dx, dz), m.board(), w + "board");
                assertEquals(shift(b.ladderBoard(), dx, dz), m.ladderBoard(), w + "ladderBoard");
                assertEquals(shift(b.wordsBox(), dx, dz), m.wordsBox(), w + "wordsBox");
                assertEquals(shift(b.ladderWordsBox(), dx, dz), m.ladderWordsBox(), w + "ladderWordsBox");
                near(b.wordsZ() + dz, m.wordsZ(), w + "wordsZ");
                eqList3(b.oldButtons(), m.oldButtons(), dx, dz, w + "oldButtons");
                eqFills(b.fills(), m.fills(), dx, dz, w + "panel fills");
                List<ArenaPanel.Button> bb = b.buttons();
                List<ArenaPanel.Button> mb = m.buttons();
                assertEquals(bb.size(), mb.size());
                for (int i = 0; i < bb.size(); i++) {
                    assertEquals(new ArenaPanel.Button(bb.get(i).control(), bb.get(i).x() + dx, bb.get(i).y(),
                            bb.get(i).z() + dz), mb.get(i), w + "button " + i);
                }
                List<ArenaPanel.Placed> bp = b.blocks();
                List<ArenaPanel.Placed> mp = m.blocks();
                assertEquals(bp.size(), mp.size());
                for (int i = 0; i < bp.size(); i++) {
                    ArenaPanel.Placed p = bp.get(i);
                    assertEquals(new ArenaPanel.Placed(p.x() + dx, p.y(), p.z() + dz, p.kind()), mp.get(i), w + i);
                    assertEquals(b.isGateway(p.x(), p.y()), m.isGateway(p.x() + dx, p.y()), w + "isGateway " + i);
                }
                for (int y = base.floorY() - 1; y <= base.floorY() + 3; y++) {
                    for (int x = -3; x <= 3; x++) {
                        assertEquals(b.isGateway(x, y), m.isGateway(x + dx, y), w + "isGateway " + x + "," + y);
                    }
                }
                List<ArenaPanel.Slot> bs = b.slots();
                List<ArenaPanel.Slot> ms = m.slots();
                assertEquals(bs.size(), ms.size());
                for (int i = 0; i < bs.size(); i++) {
                    ArenaPanel.Slot s = bs.get(i);
                    ArenaPanel.Slot t = ms.get(i);
                    assertEquals(s.words(), t.words());
                    near(s.x() + dx, t.x(), w + "slot x " + s.words());
                    near(s.y(), t.y(), w + "slot y " + s.words());
                    near(s.z() + dz, t.z(), w + "slot z " + s.words());
                    assertEquals(s.scale(), t.scale(), w + "slot scale " + s.words());
                    assertEquals(s.colour(), t.colour());
                }
                ArenaWords.LadderBoardWords words = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, false, 0);
                List<ArenaPanel.LadderSlot> bl = b.ladderSlots(words);
                List<ArenaPanel.LadderSlot> ml = m.ladderSlots(words);
                assertEquals(bl.size(), ml.size());
                for (int i = 0; i < bl.size(); i++) {
                    near(bl.get(i).x() + dx, ml.get(i).x(), w + "ladder x " + i);
                    near(bl.get(i).y(), ml.get(i).y(), w + "ladder y " + i);
                    near(bl.get(i).z() + dz, ml.get(i).z(), w + "ladder z " + i);
                    assertEquals(bl.get(i).scale(), ml.get(i).scale());
                    assertEquals(bl.get(i).yaw(), ml.get(i).yaw());
                }
            }
        }
    }

    @Test
    void theChampionsBoardMovesWithThePlace() {
        for (ArenaPlace base : SHAPES) {
            for (int[] o : ORIGINS) {
                int dx = o[0];
                int dz = o[1];
                ArenaChampionsLayout b = base.champions();
                ArenaChampionsLayout m = base.at(dx, dz).champions();
                String w = base.describe() + " at " + dx + "," + dz + ": ";
                assertEquals(b.wallZ() + dz, m.wallZ(), w + "wallZ");
                assertEquals(shift(b.board(), dx, dz), m.board(), w + "board");
                assertEquals(shift(b.wordsBox(), dx, dz), m.wordsBox(), w + "wordsBox");
                near(b.wordsZ() + dz, m.wordsZ(), w + "wordsZ");
                near(b.x(2.5) + dx, m.x(2.5), w + "x(u)");
                List<ArenaChampionsLayout.Slot> bs = b.slots();
                List<ArenaChampionsLayout.Slot> ms = m.slots();
                assertEquals(bs.size(), ms.size());
                for (int i = 0; i < bs.size(); i++) {
                    near(bs.get(i).x() + dx, ms.get(i).x(), w + "slot x " + i);
                    near(bs.get(i).y(), ms.get(i).y(), w + "slot y " + i);
                    near(bs.get(i).z() + dz, ms.get(i).z(), w + "slot z " + i);
                    assertEquals(bs.get(i).scale(), ms.get(i).scale());
                }
                List<ArenaChampionsLayout.Face> bf = b.faces();
                List<ArenaChampionsLayout.Face> mf = m.faces();
                assertEquals(bf.size(), mf.size());
                for (int i = 0; i < bf.size(); i++) {
                    near(bf.get(i).x() + dx, mf.get(i).x(), w + "face x " + i);
                    near(bf.get(i).y(), mf.get(i).y(), w + "face y " + i);
                    near(bf.get(i).z() + dz, mf.get(i).z(), w + "face z " + i);
                }
            }
        }
    }

    @Test
    void everyInsideTestAgreesOnceThePointIsMovedToo() {
        for (ArenaPlace base : SHAPES) {
            for (int[] o : ORIGINS) {
                int dx = o[0];
                int dz = o[1];
                ArenaPlace m = base.at(dx, dz);
                Box f = base.footprint();
                String w = base.describe() + " at " + dx + "," + dz + ": ";
                int span = 20;
                double[] ys = {base.floorY(), base.floorY() - 2, base.bedrockY() - 3, base.floorY() + 3};
                for (int x = f.x0() - span; x <= f.x1() + span; x += 3) {
                    for (int z = f.z0() - span; z <= f.z1() + span; z += 3) {
                        double px = x + 0.25;
                        double pz = z + 0.75;
                        double mx = px + dx;
                        double mz = pz + dz;
                        String at = w + x + "," + z;
                        assertEquals(base.inPrepRoom(px, pz), m.inPrepRoom(mx, mz), at + " inPrepRoom");
                        assertEquals(shift(base.reachFor(px, pz), dx, dz), m.reachFor(mx, mz), at + " reachFor");
                        assertEquals(base.inReach(px, pz, x, z), m.inReach(mx, mz, x + dx, z + dz), at + " inReach");
                        assertEquals(base.isPrepLight(x, z), m.isPrepLight(x + dx, z + dz), at + " isPrepLight");
                        assertEquals(base.area().contains(x, z), m.area().contains(x + dx, z + dz), at + " area");
                        assertEquals(ArenaGate.inGateway(base, px, pz, 0.3), ArenaGate.inGateway(m, mx, mz, 0.3),
                                at + " inGateway");
                        for (double y : ys) {
                            assertEquals(base.keeps(px, y, pz), m.keeps(mx, y, mz), at + " keeps " + y);
                            assertEquals(base.fellOut(px, y, pz), m.fellOut(mx, y, mz), at + " fellOut " + y);
                            assertEquals(ArenaGate.inArena(base, px, y, pz), ArenaGate.inArena(m, mx, y, mz),
                                    at + " inArena " + y);
                            assertEquals(ArenaGate.mob(base, px, y, pz), ArenaGate.mob(m, mx, y, mz), at + " mob " + y);
                            assertEquals(ArenaHandOver.where(base, px, y, pz), ArenaHandOver.where(m, mx, y, mz),
                                    at + " where " + y);
                            int iy = (int) y;
                            assertEquals(ArenaGate.isGateway(base, x, iy, z), ArenaGate.isGateway(m, x + dx, iy, z + dz),
                                    at + " isGateway " + y);
                        }
                    }
                }
                // the moved place's own spots are inside it, by its own tests
                int[] a = m.arrival();
                assertTrue(m.inPrepRoom(a[0] + 0.5, a[2] + 0.5) && m.keeps(a[0] + 0.5, a[1], a[2] + 0.5), w + "arrival");
                if (Math.abs(dx) > 100) {
                    int[] a0 = base.arrival();
                    assertFalse(m.keeps(a0[0] + 0.5, a0[1], a0[2] + 0.5), w + "the (0, 0) arrival is not in a far place");
                }
                double[] in = ArenaGate.inside(m);
                assertTrue(ArenaGate.inArena(m, in[0], in[1], in[2]), w + "moved inside the gate is in the arena");
                double[] b0 = ArenaGate.inside(base);
                assertArrayEquals(new double[] {b0[0] + dx, b0[1], b0[2] + dz}, in, 1e-9);
                double[] out = ArenaGate.outside(m);
                double[] o0 = ArenaGate.outside(base);
                assertArrayEquals(new double[] {o0[0] + dx, o0[1], o0[2] + dz}, out, 1e-9);
                assertTrue(m.inPrepRoom(out[0], out[2]), w + "outside the gate is the prep room");
            }
        }
    }

    @Test
    void theResetPutsBackTheSameBlocksMoved() {
        ArenaPlace base = new ArenaPlace(15, 5, 3, 64);
        ArenaPlace.Reset r0 = base.reset();
        int maxY = base.floorY() + 8;
        Box box = base.resetBox(maxY);
        for (int[] o : ORIGINS) {
            ArenaPlace m = base.at(o[0], o[1]);
            ArenaPlace.Reset r = m.reset();
            for (int x = box.x0() - 2; x <= box.x1() + 2; x++) {
                for (int y = box.y0() - 1; y <= box.y1(); y++) {
                    for (int z = box.z0() - 2; z <= box.z1() + 2; z++) {
                        assertEquals(r0.kindAt(x, y, z), r.kindAt(x + o[0], y, z + o[1]),
                                "reset at " + x + "," + y + "," + z + " moved by " + o[0] + "," + o[1]);
                    }
                }
            }
        }
    }

    @Test
    void waveSpotsComeOutInsideTheMovedArena() {
        for (ArenaPlace base : SHAPES) {
            for (int[] o : ORIGINS) {
                int dx = o[0];
                int dz = o[1];
                ArenaPlace m = base.at(dx, dz);
                List<int[]> g0 = ArenaSpawns.grid(base);
                List<int[]> g = ArenaSpawns.grid(m);
                assertEquals(g0.size(), g.size());
                for (int i = 0; i < g0.size(); i++) {
                    assertArrayEquals(new int[] {g0.get(i)[0] + dx, g0.get(i)[1] + dz}, g.get(i));
                }
                int[] a = base.arrival();
                List<double[]> players0 = List.of(new double[] {a[0] + 0.5, a[2] + 0.5}, new double[] {3.25, -4.5});
                List<double[]> players = new ArrayList<>();
                for (double[] p : players0) {
                    players.add(new double[] {p[0] + dx, p[1] + dz});
                }
                for (long seed : new long[] {1L, 42L, -7L}) {
                    List<int[]> s0 = ArenaSpawns.pick(base, players0, 12, seed);
                    List<int[]> s = ArenaSpawns.pick(m, players, 12, seed);
                    assertEquals(s0.size(), s.size());
                    for (int i = 0; i < s0.size(); i++) {
                        assertArrayEquals(new int[] {s0.get(i)[0] + dx, s0.get(i)[1] + dz}, s.get(i),
                                "spot " + i + " seed " + seed);
                        assertTrue(m.arenaInside().containsColumn(s.get(i)[0], s.get(i)[1]),
                                "a spot in the moved arena");
                        assertTrue(ArenaGate.inArena(m, s.get(i)[0] + 0.5, m.floorY(), s.get(i)[1] + 0.5));
                    }
                }
                // the boss's spot: the arena's middle, wherever it stands
                int[] centre = {m.centreX(), m.centreZ()};
                assertTrue(m.arenaInside().containsColumn(centre[0], centre[1]));
                assertEquals(ArenaSpawns.nearestPlayer(new int[] {0, 0}, players0),
                        ArenaSpawns.nearestPlayer(centre, players), 1e-9);
            }
        }
    }

    @Test
    void aBuiltPlaceKeepsItsOriginWhenTheRulesAskForTheSameSizes() {
        ArenaPlace built = ArenaPlace.DEFAULT.at(1000, -2000);
        ArenaPlace.Start s = ArenaPlace.onStart(built, ArenaPlace.DEFAULT);
        assertEquals(built, s.place());
        assertFalse(s.build());
        assertEquals("", s.note());
        ArenaPlace.Start bigger = ArenaPlace.onStart(built, new ArenaPlace(41, 9, 4, 64));
        assertEquals(built, bigger.place(), "new sizes change nothing that stands, wherever it stands");
        assertFalse(bigger.build());
    }

    // --- the saved record (ArenaState's codec is plain DFU + Gson: no Minecraft bootstrap needed) ---

    private static ArenaState read(String json) {
        return ArenaState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static JsonElement write(ArenaState s) {
        return ArenaState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
    }

    @Test
    void anOldRecordWithoutAnOriginReadsAsZeroZero() {
        ArenaState s = read("{\"v\":5,\"built\":{\"arenaSize\":31,\"prepSize\":9,\"wallHeight\":4,\"floorY\":64}}");
        assertEquals(ArenaPlace.DEFAULT, s.built());
        assertEquals(0, s.built().centreX());
        assertEquals(0, s.built().centreZ());
        JsonElement again = write(s).getAsJsonObject().get("built");
        assertNull(again.getAsJsonObject().get("ox"), "a place at (0, 0) saves no origin, as before");
        assertNull(again.getAsJsonObject().get("oz"));
    }

    @Test
    void aBuiltPlaceWithAnOriginSurvivesASaveAndALoad() {
        for (int[] o : ORIGINS) {
            ArenaState s = read("{\"v\":5}");
            assertNull(s.built());
            ArenaPlace moved = new ArenaPlace(41, 11, 6, 80).at(o[0], o[1]);
            s.markBuilt(moved);
            JsonElement saved = write(s);
            ArenaState again = read(saved.toString());
            assertEquals(moved, again.built(), "origin " + o[0] + "," + o[1]);
            if (o[0] != 0) {
                assertEquals(o[0], saved.getAsJsonObject().get("built").getAsJsonObject().get("ox").getAsInt());
            }
        }
    }
}
