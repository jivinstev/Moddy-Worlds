package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaGate.Act;
import com.moddingfromamod.levels.arena.ArenaGate.MobAct;
import com.moddingfromamod.levels.arena.ArenaMatch.Phase;
import com.moddingfromamod.levels.arena.ArenaPlace.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gate in a battle: shut only while a wave is on, who is moved in and when, and the monsters kept in. */
class ArenaGateTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;

    @Test
    void theGateIsShutOnlyWhileAWaveIsOn() {
        for (Phase ph : Phase.values()) {
            assertEquals(ph == Phase.WAVE, ArenaGate.shut(ph), ph.name());
        }
    }

    @Test
    void theGatewayIsBarsWhenShutAndTheFenceGateWhenOpen() {
        assertEquals(2, ArenaGate.gateway(P).size());
        assertArrayEquals(new int[] {0, 64, 16}, ArenaGate.gateway(P).get(0));
        assertArrayEquals(new int[] {0, 65, 16}, ArenaGate.gateway(P).get(1));
        assertTrue(ArenaGate.isGateway(P, 0, 64, 16) && ArenaGate.isGateway(P, 0, 65, 16));
        assertFalse(ArenaGate.isGateway(P, 0, 66, 16) || ArenaGate.isGateway(P, 1, 64, 16));
        assertEquals(Kind.GATE_BARS, ArenaGate.kind(P, 64, true));
        assertEquals(Kind.GATE_BARS, ArenaGate.kind(P, 65, true));
        assertEquals(Kind.GATE, ArenaGate.kind(P, 64, false), "open: the fence gate, as built");
        assertEquals(Kind.AIR, ArenaGate.kind(P, 65, false));
        // the gateway is what the place builds there, and never a block of the control panel's
        assertEquals(Kind.GATE, P.reset().kindAt(0, 64, 16));
        assertEquals(Kind.AIR, P.reset().kindAt(0, 65, 16));
        for (ArenaPanel.Placed b : P.panel().blocks()) {
            assertFalse(ArenaGate.isGateway(P, b.x(), b.y(), b.z()), "the panel never touches the gateway: " + b);
        }
    }

    @Test
    void whereAFighterIsMovedIn() {
        double[] in = ArenaGate.inside(P);
        assertArrayEquals(new double[] {0.5, 64, 15.5}, in);
        assertTrue(ArenaGate.inArena(P, in[0], in[1], in[2]), "just inside the gate is inside the arena");
        assertTrue(P.arenaInside().contains(0, 64, 15));
        double[] out = ArenaGate.outside(P);
        assertTrue(P.prepInside().contains((int) Math.floor(out[0]), (int) out[1], (int) Math.floor(out[2])));
        assertFalse(ArenaGate.inArena(P, out[0], out[1], out[2]));
        int[] a = P.arrival();
        assertFalse(ArenaGate.inArena(P, a[0] + 0.5, a[1], a[2] + 0.5), "the prep room is not the arena");
        assertFalse(ArenaGate.inArena(P, 0.5, 64, 16.5), "nor is the gateway");
        assertTrue(ArenaGate.inArena(P, 3.5, 62, 3.5), "standing in a hole a blast made is still in it");
        assertTrue(ArenaGate.inGateway(P, 0.5, 16.5, 0.3));
        assertTrue(ArenaGate.inGateway(P, 1.1, 16.5, 0.3), "a body overlapping the gateway's column");
        assertFalse(ArenaGate.inGateway(P, 0.5, 17.5, 0.3));
    }

    @Test
    void whoIsMovedInAndWhen() {
        // when a wave starts: everyone on his feet outside the arena, at once
        assertEquals(Act.MOVE, ArenaGate.act(true, true, true, false, -1));
        assertEquals(Act.NONE, ArenaGate.act(true, true, true, true, -1), "already inside");
        assertEquals(Act.NONE, ArenaGate.act(true, true, false, false, -1), "not on his feet");
        // back on his feet in the prep room during a wave: a count, then in
        assertEquals(Act.START_COUNT, ArenaGate.act(true, false, true, false, -1));
        assertEquals(Act.COUNT, ArenaGate.act(true, false, true, false, 40));
        assertEquals(Act.MOVE, ArenaGate.act(true, false, true, false, 0));
        // the count stops when the wave is over, he goes in himself, or he is knocked out again
        assertEquals(Act.STOP_COUNT, ArenaGate.act(false, false, true, false, 30));
        assertEquals(Act.STOP_COUNT, ArenaGate.act(true, false, true, true, 30));
        assertEquals(Act.STOP_COUNT, ArenaGate.act(true, false, false, false, 30));
        // in a break, and with no battle, nobody is moved
        assertEquals(Act.NONE, ArenaGate.act(false, false, true, false, -1));
        // a whole count: 3 seconds, shown 3, 2, 1
        int left = -1;
        List<Integer> shown = new java.util.ArrayList<>();
        int ticks = 0;
        Act a;
        do {
            a = ArenaGate.act(true, false, true, false, left);
            if (a == Act.START_COUNT) {
                left = ArenaGate.BACK_IN_TICKS;
                shown.add(ArenaGate.secondsLeft(left));
            } else if (a == Act.COUNT) {
                left--;
                if (left > 0 && left % ArenaMatch.TICKS_PER_SECOND == 0) {
                    shown.add(ArenaGate.secondsLeft(left));
                }
            }
            ticks++;
        } while (a != Act.MOVE);
        assertEquals(List.of(3, 2, 1), shown);
        assertEquals(ArenaGate.BACK_IN_TICKS + 2, ticks, "moved in three seconds after he is on his feet");
    }

    @Test
    void aMonsterOutsideTheArenaIsPutBackAndOneFarAwayIsRemoved() {
        assertEquals(MobAct.KEEP, ArenaGate.mob(P, 0.5, 64, 0.5));
        assertEquals(MobAct.KEEP, ArenaGate.mob(P, 5.5, 62, 5.5), "in a hole a blast made");
        assertEquals(MobAct.PUT_BACK, ArenaGate.mob(P, 0.5, 64, 20.5), "in the prep room");
        assertEquals(MobAct.PUT_BACK, ArenaGate.mob(P, 0.5, 64, 16.5), "in the gateway");
        assertEquals(MobAct.PUT_BACK, ArenaGate.mob(P, 0.5, 30, 0.5), "fell out under the arena");
        assertEquals(MobAct.REMOVE, ArenaGate.mob(P, 200.5, 64, 0.5), "nowhere near the place");
    }

    @Test
    void theWordsArePlainAndShort() {
        for (String l : List.of(ArenaWords.gateShut(), ArenaWords.backIn(), ArenaWords.backInCount(2, 3),
                ArenaWords.backInCount(1, 1), ArenaWords.fellOut())) {
            assertTrue(l.length() <= 60 && l.chars().allMatch(c -> c >= 32 && c < 127), l);
        }
        assertEquals("The gate is shut. Fight!", ArenaWords.gateShut());
        assertEquals("2 lives left. Back into the arena in 3...", ArenaWords.backInCount(2, 3));
    }
}
