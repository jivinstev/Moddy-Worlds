package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A creature wished into the arena fights the wave: which foe, when it reaches, how often and how hard it strikes. */
class ArenaAlliesTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void itFightsTheNearestFoe() {
        List<ArenaAllies.Foe> foes = List.of(new ArenaAllies.Foe(A, 10, 64, 0), new ArenaAllies.Foe(B, 3, 64, 0),
                new ArenaAllies.Foe(C, 0, 64, -7));
        assertEquals(B, ArenaAllies.pick(0, 64, 0, null, foes));
    }

    @Test
    void itKeepsTheFoeItFightsWhileThatOneStands() {
        List<ArenaAllies.Foe> foes = List.of(new ArenaAllies.Foe(A, 10, 64, 0), new ArenaAllies.Foe(B, 3, 64, 0));
        assertEquals(A, ArenaAllies.pick(0, 64, 0, A, foes), "a nearer one does not pull it away");
        assertEquals(B, ArenaAllies.pick(0, 64, 0, C, foes), "its foe is gone: the nearest one left");
    }

    @Test
    void theChoiceIsTheSameWhateverOrderTheWorldListsThem() {
        List<ArenaAllies.Foe> foes = new ArrayList<>(List.of(new ArenaAllies.Foe(C, 5, 64, 0),
                new ArenaAllies.Foe(A, -5, 64, 0), new ArenaAllies.Foe(B, 0, 64, 5)));
        UUID first = ArenaAllies.pick(0, 64, 0, null, foes);
        assertEquals(A, first, "three as near: the lowest id");
        for (int i = 0; i < 6; i++) {
            Collections.rotate(foes, 1);
            assertEquals(first, ArenaAllies.pick(0, 64, 0, null, foes));
            Collections.reverse(foes);
            assertEquals(first, ArenaAllies.pick(0, 64, 0, null, foes));
        }
    }

    @Test
    void noFoeLeftNoTarget() {
        assertNull(ArenaAllies.pick(0, 64, 0, A, List.of()));
        assertNull(ArenaAllies.pick(0, 64, 0, null, List.of()));
    }

    @Test
    void itStrikesFromBesideItsFoeNotFromAcrossTheArena() {
        // a cat (0.6 wide) beside a zombie (0.6 wide): centres 2 blocks apart reach, 3 do not
        assertTrue(ArenaAllies.inReach(2 * 2, 0.6, 0.6));
        assertFalse(ArenaAllies.inReach(3 * 3, 0.6, 0.6));
        // a wide creature reaches further
        assertTrue(ArenaAllies.inReach(3 * 3, 3.0, 0.6));
    }

    @Test
    void itStrikesOnceASecond() {
        assertTrue(ArenaAllies.mayStrike(-1, 0), "never struck: it may");
        assertFalse(ArenaAllies.mayStrike(100, 100 + ArenaAllies.STRIKE_COOLDOWN_TICKS - 1));
        assertTrue(ArenaAllies.mayStrike(100, 100 + ArenaAllies.STRIKE_COOLDOWN_TICKS));
    }

    @Test
    void aCreatureWithNoAttackStillHurts() {
        assertEquals(ArenaAllies.NO_ATTACK_DAMAGE, ArenaAllies.damage(0));
        assertEquals(7.0f, ArenaAllies.damage(7.0));
        assertTrue(ArenaAllies.NO_ATTACK_DAMAGE > 0);
    }
}
