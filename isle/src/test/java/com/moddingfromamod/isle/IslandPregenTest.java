package com.moddingfromamod.isle;

import com.google.gson.JsonParser;
import com.moddingfromamod.builder.BuildShape;
import com.moddingfromamod.event.shape.FallShape;
import com.moddingfromamod.runtime.EventFraming;
import com.moddingfromamod.runtime.Framing;
import com.moddingfromamod.runtime.OrbitCamera;
import com.moddingfromamod.wish.WishAim;
import com.moddingfromamod.wish.WishSpotShot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #2542: the idle pre-generation of the next island cells, Minecraft-free. Where it RUNS (tickets, FULL
 * chunks, an island placed on top) is {@code IslandPregenGameTest}.
 */
class IslandPregenTest {

    // ── the radius: derived from the framing constants, and pinned ────────────────────────────────

    @Test
    void theRadiusCoversTheFarthestTheCameraCanBeSentFromAnIslandsCentre() {
        int vd = 8;
        double reach = IslandPregenRadius.cameraReachBlocks();
        // every term, from its own constant: where he can aim, how far a build reaches, how far back
        // each camera stands
        double standoff = Math.max(Math.max(Framing.MAX_DISTANCE, EventFraming.MAX_DISTANCE),
                Math.max(OrbitCamera.MAX_BUILD_DISTANCE, WishSpotShot.MAX_FROM));
        double camera = Math.max(WishAim.REACH, FallShape.MAX_AIM) + BuildShape.MAX_OFFSET + BuildShape.MAX_DIM
                + standoff;
        assertTrue(reach >= camera, "the reach " + reach + " is short of the camera's " + camera);
        for (double each : new double[]{Framing.MAX_DISTANCE, EventFraming.MAX_DISTANCE,
                OrbitCamera.MAX_BUILD_DISTANCE, WishSpotShot.MAX_FROM}) {
            assertTrue(reach >= each, "a camera standoff of " + each + " is past the reach " + reach);
        }
        int r = IslandPregenRadius.radiusChunks(vd);
        // the camera's chunk, then every chunk the server sends a client standing there
        assertTrue((r - vd) * 16 >= reach, "radius " + r + " less view " + vd + " does not reach " + reach);
        // and always the child's own view
        for (int v = 2; v <= 32; v++) {
            assertTrue(IslandPregenRadius.radiusChunks(v) > v, "the radius must cover view distance " + v);
        }
    }

    /**
     * THE PIN. A framing constant raised (EventFraming.MAX_DISTANCE, OrbitCamera.MAX_BUILD_DISTANCE …) or a
     * build allowed further moves this, and this fails BY NAME rather than the radius quietly growing the
     * disk, or a radius typed somewhere else quietly letting the camera fly into ungenerated terrain. Check
     * the new disk cost (the message says it), then move the pin.
     */
    @Test
    void thePinnedRadiusAtTheIslesViewDistance() {
        int r = IslandPregenRadius.radiusChunks(8);
        assertEquals(596, Math.round(IslandPregenRadius.cameraReachBlocks()),
                "the camera's reach from an island's centre changed (a framing or build constant moved): "
                        + "it is now " + IslandPregenRadius.cameraReachBlocks() + " blocks, " + r + " chunks round "
                        + "each cell, ~" + Math.round(IslandPregenRadius.estimatedMiBPerCell(r)) + " MiB a cell — "
                        + "check the disk and move this pin (#2542)");
        assertEquals(47, r);
        assertEquals(95L * 95L, IslandPregenRadius.chunksPerCell(r));
    }

    // ── the cells: the order IslandService claims them in ────────────────────────────────────────

    private static IslandRegistry registry() {
        return new IslandRegistry(IslandGrid.of(1024));
    }

    @Test
    void theUpcomingCellsAreExactlyTheCellsTheNextChildrenAreGiven() {
        IslandRegistry reg = registry();
        List<int[]> upcoming = reg.upcoming(5);
        assertEquals(5, upcoming.size());
        for (int k = 0; k < 5; k++) {
            Island got = reg.claim(UUID.randomUUID(), 1L).island();
            assertArrayEquals(upcoming.get(k), new int[]{got.cellX(), got.cellZ()}, "child " + k);
        }
        // and with the canary's service island claimed, nothing moves
        IslandRegistry svc = registry();
        svc.claimService(UUID.randomUUID(), 1L);
        List<int[]> next = svc.upcoming(3);
        for (int[] c : next) {
            Island got = svc.claim(UUID.randomUUID(), 1L).island();
            assertArrayEquals(c, new int[]{got.cellX(), got.cellZ()});
        }
    }

    @Test
    void afterAFreshStartRetiresACellTheUpcomingOrderStillMatchesTheClaims() {
        IslandRegistry reg = registry();
        UUID kid = UUID.randomUUID();
        reg.claim(kid, 1L);
        reg.claim(UUID.randomUUID(), 1L);
        reg.retire(kid, 2L);                           // /moddy island new: his cell rests, never reused
        List<int[]> upcoming = reg.upcoming(3);
        Island fresh = reg.claim(kid, 3L).island();
        assertArrayEquals(upcoming.get(0), new int[]{fresh.cellX(), fresh.cellZ()});
        for (int k = 1; k < 3; k++) {
            Island got = reg.claim(UUID.randomUUID(), 4L).island();
            assertArrayEquals(upcoming.get(k), new int[]{got.cellX(), got.cellZ()});
        }
    }

    @Test
    void askingForTheUpcomingCellsClaimsNothing() {
        IslandRegistry reg = registry();
        reg.claim(UUID.randomUUID(), 1L);
        int before = reg.size();
        reg.upcoming(10);
        assertEquals(before, reg.size());
        assertEquals(0, reg.upcoming(0).size());
    }

    // ── the plan: rings out from the centre, bounded, resumable, never twice ─────────────────────

    /** A world whose chunks become FULL a fixed number of ticks after they are asked for, kept "on disk". */
    static final class FakeWorld implements IslandPregenPlan.World {
        final Map<Long, Integer> askedAt = new HashMap<>();
        final Map<Long, Integer> requests = new HashMap<>();
        final Set<Long> generated;           // survives a "restart": it is the disk
        final List<long[]> order = new ArrayList<>();
        int now;
        final int delay;

        FakeWorld(Set<Long> disk, int delay) {
            this.generated = disk;
            this.delay = delay;
        }

        static long k(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        @Override
        public boolean ready(int x, int z) {
            long key = k(x, z);
            if (generated.contains(key)) {
                return askedAt.containsKey(key);
            }
            Integer at = askedAt.get(key);
            if (at != null && now - at >= delay) {
                generated.add(key);
                return true;
            }
            return false;
        }

        @Override
        public void request(int x, int z) {
            long key = k(x, z);
            askedAt.putIfAbsent(key, now);
            requests.merge(key, 1, Integer::sum);
            order.add(new long[]{x, z});
        }

        @Override
        public void release(int x, int z) {
            askedAt.remove(k(x, z));
        }
    }

    private static List<IslandPregenPlan.Cell> cells(IslandRegistry reg, int n) {
        List<IslandPregenPlan.Cell> out = new ArrayList<>();
        for (int[] c : reg.upcoming(n)) {
            out.add(IslandPregenPlan.Cell.of(reg.grid(), c));
        }
        return out;
    }

    private static int run(IslandPregenPlan plan, List<IslandPregenPlan.Cell> up, FakeWorld w, int ticks) {
        int t = 0;
        for (; t < ticks; t++) {
            w.now++;
            IslandPregenPlan.Step s = plan.tick(up, PregenThrottle.admit(0, 0, false, plan.inFlight()), w, w.now);
            if (s.ready()) {
                break;
            }
        }
        return t;
    }

    @Test
    void theRingOrderStartsAtTheCentreAndGrowsOutward() {
        assertArrayEquals(new int[]{0, 0}, IslandPregenPlan.offset(0));
        for (int i = 1; i < 9; i++) {
            int[] o = IslandPregenPlan.offset(i);
            assertEquals(1, Math.max(Math.abs(o[0]), Math.abs(o[1])), "ring 1 at " + i);
        }
        int last = 0;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 25 * 25; i++) {
            int[] o = IslandPregenPlan.offset(i);
            int ring = Math.max(Math.abs(o[0]), Math.abs(o[1]));
            assertTrue(ring >= last, "rings never go back in: " + i);
            last = ring;
            assertTrue(seen.add(FakeWorld.k(o[0], o[1])), "no chunk twice in the order: " + i);
        }
    }

    @Test
    void itGeneratesNCellsAtTheRadiusAndThenStops() {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 3);
        IslandPregenPlan plan = new IslandPregenPlan(2);
        FakeWorld w = new FakeWorld(new HashSet<>(), 3);
        run(plan, up, w, 10_000);
        assertEquals(3, plan.readyCount(up));
        // every chunk asked for is inside one of the three cells' squares, and every square is whole
        Set<Long> want = new HashSet<>();
        for (IslandPregenPlan.Cell c : up) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    want.add(FakeWorld.k(c.centreX() + dx, c.centreZ() + dz));
                }
            }
        }
        assertEquals(want, w.requests.keySet(), "it asks for the N squares and nothing else");
        long asked = plan.requestedTotal();
        // a thousand more ticks: nothing more is asked for — it stops at N cells
        for (int t = 0; t < 1000; t++) {
            w.now++;
            assertTrue(plan.tick(up, PregenThrottle.MAX_NEW_PER_TICK, w, w.now).ready());
        }
        assertEquals(asked, plan.requestedTotal(), "it must not generate without bound");
        assertEquals(0, plan.inFlight());
        assertTrue(w.askedAt.isEmpty(), "every chunk let go once it was FULL");
    }

    @Test
    void itNeverClaimsACellOrPlacesAnIsland() {
        IslandRegistry reg = registry();
        reg.claim(UUID.randomUUID(), 1L);
        List<int[]> before = reg.upcoming(3);
        int size = reg.size();
        IslandPregenPlan plan = new IslandPregenPlan(1);
        run(plan, cells(reg, 3), new FakeWorld(new HashSet<>(), 1), 10_000);
        assertEquals(size, reg.size(), "pre-generating gave a cell an owner");
        List<int[]> after = reg.upcoming(3);
        for (int k = 0; k < 3; k++) {
            assertArrayEquals(before.get(k), after.get(k));
        }
    }

    @Test
    void sharedChunksOfNeighbouringCellsAreAskedForOnce() {
        // a grid whose cells overlap at this radius: spacing 64 blocks = 4 chunks, radius 3
        IslandRegistry reg = new IslandRegistry(new IslandGrid(64, 16, 0, 63, 0));
        List<IslandPregenPlan.Cell> up = cells(reg, 3);
        IslandPregenPlan plan = new IslandPregenPlan(3);
        FakeWorld w = new FakeWorld(new HashSet<>(), 2);
        run(plan, up, w, 100_000);
        assertEquals(3, plan.readyCount(up));
        for (var e : w.requests.entrySet()) {
            assertEquals(1, e.getValue(), "a shared chunk was asked for twice");
        }
    }

    @Test
    void aRestartResumesWhereItStoppedAndAsksForNothingAlreadyDone() throws Exception {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 2);
        Set<Long> disk = new HashSet<>();
        IslandPregenPlan plan = new IslandPregenPlan(4);
        FakeWorld w = new FakeWorld(disk, 2);
        run(plan, up, w, 20);                        // part of the first cell (81 chunks)
        assertFalse(plan.complete(up.get(0)), "rig: the first cell must still be unfinished");
        Set<Long> doneBefore = new HashSet<>(disk);
        // the record goes through the file, as a server stop and start would
        Path f = Files.createTempDirectory("pregen").resolve(IslandPregenStore.FILE);
        IslandPregenStore store = new IslandPregenStore(f);
        plan.releaseAll(w);
        assertTrue(store.save(plan, new com.google.gson.JsonObject()));
        assertEquals("island-pregen", JsonParser.parseString(Files.readString(f)).getAsJsonObject().get("store").getAsString());
        IslandPregenPlan resumed = new IslandPregenStore(f).load(4);
        assertEquals(plan.done(up.get(0)), resumed.done(up.get(0)));
        FakeWorld w2 = new FakeWorld(disk, 2);
        run(resumed, up, w2, 100_000);
        assertEquals(2, resumed.readyCount(up));
        for (long k : doneBefore) {
            assertFalse(w2.requests.containsKey(k), "a chunk recorded done was asked for again after the restart");
        }
        // and together the two runs covered both squares exactly
        Set<Long> all = new HashSet<>(w.requests.keySet());
        all.addAll(w2.requests.keySet());
        assertEquals(2 * IslandPregenRadius.chunksPerCell(4), all.size());
    }

    @Test
    void aDamagedRecordIsWorkToRedoNeverACrash() throws Exception {
        Path f = Files.createTempDirectory("pregen").resolve(IslandPregenStore.FILE);
        Files.writeString(f, "{not json");
        IslandPregenPlan plan = new IslandPregenStore(f).load(2);
        assertEquals(0, plan.finishedTotal());
    }

    @Test
    void aWiderRadiusLaterReopensAFinishedCellFromWhereItStopped() {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 1);
        IslandPregenPlan small = new IslandPregenPlan(1);
        FakeWorld w = new FakeWorld(new HashSet<>(), 1);
        run(small, up, w, 1000);
        assertTrue(small.complete(up.get(0)));
        IslandPregenPlan wide = IslandPregenPlan.fromJson(small.toJson(), 2);
        assertFalse(wide.complete(up.get(0)));
        assertEquals(9, wide.done(up.get(0)), "the 3x3 already done counts");
    }

    @Test
    void firstViewReadyIsTheBarAResetWaitsFor() {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 1);
        IslandPregenPlan plan = new IslandPregenPlan(4);
        FakeWorld w = new FakeWorld(new HashSet<>(), 1);
        assertFalse(plan.firstViewReady(up, 2));
        int t = 0;
        while (!plan.firstViewReady(up, 2) && t++ < 10_000) {
            w.now++;
            plan.tick(up, PregenThrottle.MAX_NEW_PER_TICK, w, w.now);
        }
        assertTrue(plan.firstViewReady(up, 2));
        assertFalse(plan.complete(up.get(0)), "the view is ready long before the camera's reach is");
    }

    // ── the throttle: never into a full tick, always behind a player ─────────────────────────────

    @Test
    void theThrottleNeverSchedulesIntoATickAlreadyOverBudget() {
        assertEquals(0, PregenThrottle.admit(PregenThrottle.BUDGET_MS, 0, false, 0));
        assertEquals(0, PregenThrottle.admit(PregenThrottle.BUDGET_MS + 0.1, 0, false, 0));
        assertEquals(0, PregenThrottle.admit(5100, 0, false, 0));
        assertTrue(PregenThrottle.admit(PregenThrottle.BUDGET_MS - 0.1, 0, false, 0) > 0);
    }

    @Test
    void theThrottleGivesWayToAPlayersChunksAndToAnArrival() {
        assertEquals(0, PregenThrottle.admit(1, 1, false, 0), "a player waiting for one chunk comes first");
        assertEquals(0, PregenThrottle.admit(1, 76, false, 0));
        assertEquals(0, PregenThrottle.admit(1, 0, true, 0), "an island arrival comes first");
        assertTrue(PregenThrottle.yieldReason(1, 3, false).contains("player"));
        assertEquals(null, PregenThrottle.yieldReason(1, 0, false));
    }

    @Test
    void theThrottleKeepsABoundedNumberInFlight() {
        assertEquals(PregenThrottle.MAX_NEW_PER_TICK, PregenThrottle.admit(1, 0, false, 0));
        assertEquals(1, PregenThrottle.admit(1, 0, false, PregenThrottle.MAX_IN_FLIGHT - 1));
        assertEquals(0, PregenThrottle.admit(1, 0, false, PregenThrottle.MAX_IN_FLIGHT));
        assertEquals(0, PregenThrottle.admit(1, 0, false, PregenThrottle.MAX_IN_FLIGHT + 5));
    }

    @Test
    void whileThrottledThePlanAsksForNothing() {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 1);
        IslandPregenPlan plan = new IslandPregenPlan(2);
        FakeWorld w = new FakeWorld(new HashSet<>(), 1);
        for (int t = 0; t < 100; t++) {
            w.now++;
            plan.tick(up, PregenThrottle.admit(40, 0, false, plan.inFlight()), w, w.now);
            plan.tick(up, PregenThrottle.admit(1, 12, false, plan.inFlight()), w, w.now);
        }
        assertEquals(0, plan.requestedTotal());
    }

    // ── #2542 follow-up: fast with nobody on, gentle the moment anyone is ───────────────────────────

    @Test
    void theFastLimitsRunOnlyWithNobodyOn() {
        // SHOULD: alone, many more at once and per tick
        assertEquals(PregenThrottle.FAST_MAX_NEW_PER_TICK, PregenThrottle.admit(1, 0, false, 0, true));
        assertEquals(1, PregenThrottle.admit(1, 0, false, PregenThrottle.FAST_MAX_IN_FLIGHT - 1, true));
        assertEquals(0, PregenThrottle.admit(1, 0, false, PregenThrottle.FAST_MAX_IN_FLIGHT, true));
        assertTrue(PregenThrottle.FAST_MAX_IN_FLIGHT > PregenThrottle.MAX_IN_FLIGHT);
        assertTrue(PregenThrottle.FAST_MAX_NEW_PER_TICK > PregenThrottle.MAX_NEW_PER_TICK);
        // SHOULD NOT: the fast rate with someone on (or the old four-argument call, which is "someone may be on")
        assertEquals(PregenThrottle.MAX_NEW_PER_TICK, PregenThrottle.admit(1, 0, false, 0, false));
        assertEquals(0, PregenThrottle.admit(1, 0, false, PregenThrottle.MAX_IN_FLIGHT, false));
        assertEquals(PregenThrottle.admit(1, 0, false, 3), PregenThrottle.admit(1, 0, false, 3, false));
        // and alone is still never into a full tick, nor ahead of a waiting player or arrival
        assertEquals(0, PregenThrottle.admit(PregenThrottle.BUDGET_MS, 0, false, 0, true));
        assertEquals(0, PregenThrottle.admit(1, 4, false, 0, true));
        assertEquals(0, PregenThrottle.admit(1, 0, true, 0, true));
    }

    @Test
    void whenSomeoneArrivesWhatIsInFlightPastTheGentleLimitIsLetGoAndAskedForAgainLater() {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 1);
        IslandPregenPlan plan = new IslandPregenPlan(6);
        FakeWorld w = new FakeWorld(new HashSet<>(), 1_000_000);   // nothing finishes: all stays in flight
        for (int t = 0; t < 20; t++) {
            w.now++;
            plan.tick(up, PregenThrottle.admit(1, 0, false, plan.inFlight(), true), w, w.now);
        }
        assertEquals(PregenThrottle.FAST_MAX_IN_FLIGHT, plan.inFlight(), "alone, it fills the fast limit");
        int shed = plan.shed(PregenThrottle.keep(false), w);
        assertEquals(PregenThrottle.FAST_MAX_IN_FLIGHT - PregenThrottle.MAX_IN_FLIGHT, shed);
        assertEquals(PregenThrottle.MAX_IN_FLIGHT, plan.inFlight(), "a joining child's chunks wait behind at most the gentle 8");
        assertEquals(PregenThrottle.MAX_IN_FLIGHT, w.askedAt.size(), "the let-go tickets are really released");
        assertEquals(0, plan.shed(PregenThrottle.keep(true), w), "alone keeps everything");
        // gentle now: nothing new while the 8 are in flight
        w.now++;
        assertEquals(0, plan.tick(up, PregenThrottle.admit(1, 0, false, plan.inFlight(), false), w, w.now).requested());
        // and nothing is lost: run it to the end, every chunk of the cell done exactly once on disk
        FakeWorld fast = new FakeWorld(w.generated, 1);
        fast.askedAt.putAll(w.askedAt);
        fast.now = w.now;
        for (int t = 0; t < 10_000 && !plan.complete(up.get(0)); t++) {
            fast.now++;
            plan.tick(up, PregenThrottle.admit(1, 0, false, plan.inFlight(), true), fast, fast.now);
        }
        assertTrue(plan.complete(up.get(0)));
        assertEquals(plan.chunksPerCell(), fast.generated.size());
    }

    @Test
    void aFinishedRingKeepsItsTicketUntilTheRingOutsideItIsDoneThenAllAreLetGo() {
        IslandRegistry reg = registry();
        List<IslandPregenPlan.Cell> up = cells(reg, 1);
        IslandPregenPlan.Cell c = up.get(0);
        IslandPregenPlan plan = new IslandPregenPlan(5);
        FakeWorld w = new FakeWorld(new HashSet<>(), 1);
        boolean sawRing1HeldWhileRing2Worked = false;
        boolean ring1LetGoOnceRing3Worked = false;
        int maxTicketed = 0;
        for (int t = 0; t < 10_000 && !plan.complete(c); t++) {
            w.now++;
            plan.tick(up, PregenThrottle.admit(1, 0, false, plan.inFlight(), true), w, w.now);
            maxTicketed = Math.max(maxTicketed, w.askedAt.size());
            int done = plan.done(c);
            boolean ring1Ticketed = false;
            for (int i = 1; i < 9; i++) {
                int[] o = IslandPregenPlan.offset(i);
                ring1Ticketed |= w.askedAt.containsKey(FakeWorld.k(c.centreX() + o[0], c.centreZ() + o[1]));
            }
            if (done >= 9 && done < 25 && ring1Ticketed) {
                sawRing1HeldWhileRing2Worked = true;     // ring 1 done, ring 2 in progress: ring 1 still held
            }
            if (done >= 25 + 1 && done < 49 && !ring1Ticketed) {
                ring1LetGoOnceRing3Worked = true;
            }
        }
        assertTrue(plan.complete(c));
        assertTrue(sawRing1HeldWhileRing2Worked, "a finished ring must keep its tickets while the next ring is made");
        assertTrue(ring1LetGoOnceRing3Worked, "and let them go once the frontier is two rings out");
        assertTrue(maxTicketed <= PregenThrottle.FAST_MAX_IN_FLIGHT + 2 * 8 * 5 + 1,
                "bounded: in flight plus about two rings, never the whole square (" + maxTicketed + ")");
        int heldAtReady = plan.heldCount();
        int ticks = 0;
        while (plan.heldCount() > 0 && ticks++ < 1000) {
            int before = plan.heldCount();
            w.now++;
            plan.tick(up, 0, w, w.now);
            assertTrue(before - plan.heldCount() <= IslandPregenPlan.RELEASE_PER_TICK,
                    "held chunks are let go a few a tick, never a whole cell in one tick");
        }
        assertTrue(heldAtReady > IslandPregenPlan.RELEASE_PER_TICK, "rig: enough held at READY to need several ticks");
        assertEquals(0, plan.heldCount());
        assertTrue(w.askedAt.isEmpty(), "every ticket let go once the cells are ready");
    }

    // ── the knob ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void theKnobDefaultsToThreeAndIsBounded() {
        assertEquals(3, IslandPregen.parse(null));
        assertEquals(3, IslandPregen.parse(""));
        assertEquals(0, IslandPregen.parse("0"));
        assertEquals(0, IslandPregen.parse("off"));
        assertEquals(5, IslandPregen.parse("5"));
        assertEquals(IslandPregen.MAX_CELLS, IslandPregen.parse("100000"));
        assertEquals(3, IslandPregen.parse("lots"));
    }

    // ── #2542 follow-up: the progress line and the heartbeat are counted from the START ─────────────

    @Test
    void theProgressLineAndTheHeartbeatComeAtOnceThenOnTheirPeriod() {
        // SHOULD: the first tick says where it got to (a server stopped 32 s in still prints one)…
        assertTrue(IslandPregen.due(0, IslandPregen.REPORT_EVERY_TICKS));
        assertTrue(IslandPregen.due(0, IslandPregen.HEARTBEAT_EVERY_TICKS));
        // …then each minute, and the heartbeat each 10 s — well inside a reader's freshness window (180 s)
        assertTrue(IslandPregen.due(IslandPregen.REPORT_EVERY_TICKS, IslandPregen.REPORT_EVERY_TICKS));
        assertTrue(IslandPregen.due(3 * IslandPregen.HEARTBEAT_EVERY_TICKS, IslandPregen.HEARTBEAT_EVERY_TICKS));
        assertTrue(IslandPregen.HEARTBEAT_EVERY_TICKS * 50L / 1000 < 180,
                "the heartbeat must beat well inside a 180 s freshness window");
        // SHOULD NOT: every tick in between, or before the start
        assertFalse(IslandPregen.due(1, IslandPregen.REPORT_EVERY_TICKS));
        assertFalse(IslandPregen.due(IslandPregen.REPORT_EVERY_TICKS - 1, IslandPregen.REPORT_EVERY_TICKS));
        assertFalse(IslandPregen.due(-1, IslandPregen.REPORT_EVERY_TICKS));
    }
}
