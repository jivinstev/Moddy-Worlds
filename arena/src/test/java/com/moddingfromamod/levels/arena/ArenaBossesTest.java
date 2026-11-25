package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The bosses: every 5th wave, escalating, then round again with more health. */
class ArenaBossesTest {

    @Test
    void aBossEveryFifthWave() {
        assertFalse(ArenaBosses.isBossWave(4, 5));
        assertTrue(ArenaBosses.isBossWave(5, 5));
        assertTrue(ArenaBosses.isBossWave(25, 5));
        assertFalse(ArenaBosses.isBossWave(5, 0), "0: no boss waves");
        assertNull(ArenaBosses.boss(6, 5));
    }

    @Test
    void theyEscalateThenComeRoundAgainTougher() {
        double last = 0;
        for (int k = 0; k < ArenaBosses.TABLE.size(); k++) {
            ArenaBosses.Boss b = ArenaBosses.boss(5 * (k + 1), 5);
            assertEquals(ArenaBosses.TABLE.get(k), b);
            assertTrue(b.health() > last, "each tougher than the last");
            last = b.health();
        }
        ArenaBosses.Boss again = ArenaBosses.boss(30, 5);
        assertEquals("Rumble the Ravager", again.name());
        assertEquals(300, again.health());
        assertEquals(450, ArenaBosses.boss(55, 5).health(), "the third time round: x3");
        assertEquals(ArenaBosses.MAX_HEALTH, ArenaBosses.boss(995, 5).health());
    }

    @Test
    void anEscortIsAThirdAtMostTen() {
        ArenaWaves.Wave w = new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 20),
                new ArenaWaves.Group("minecraft:pillager", 12), new ArenaWaves.Group("minecraft:witch", 1)));
        ArenaWaves.Wave e = ArenaBosses.escort(w);
        assertEquals(10, e.size());
        assertEquals(1, e.groups().get(2).count(), "each kind at least 1");
    }
}
