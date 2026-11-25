package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The waves past the written ones: made from the wave's number, growing gently. */
class ArenaGeneratorTest {

    private static final List<ArenaWaves.Wave> FIVE = List.of(
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 3))),
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 5))),
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 6))),
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 8))),
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 10))));

    private static Map<String, Integer> counts(ArenaWaves.Wave w) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (ArenaWaves.Group g : w.groups()) {
            m.put(g.mob().replace("minecraft:", ""), g.count());
        }
        return m;
    }

    @Test
    void wavesGrowGentlyAndNewKindsJoin() {
        ArenaGenerator g = ArenaGenerator.DEFAULT;
        assertEquals(Map.of("husk", 4, "pillager", 3, "witch", 2, "vindicator", 2), counts(g.wave(6, FIVE)));
        assertEquals(11, g.wave(6, FIVE).size());
        assertEquals(13, g.wave(10, FIVE).size());
        assertEquals(18, g.wave(20, FIVE).size());
        assertTrue(counts(g.wave(6, FIVE)).get("piglin_brute") == null);
        assertTrue(counts(g.wave(7, FIVE)).get("piglin_brute") >= 1, "brutes from wave 7");
        assertTrue(counts(g.wave(12, FIVE)).get("ravager") >= 1, "ravagers from wave 12");
        int last = 0;
        for (int n = 6; n <= 500; n++) {
            int size = g.wave(n, FIVE).size();
            assertTrue(size >= last && size <= ArenaWaves.MAX_PER_WAVE, "wave " + n + ": " + size);
            last = size;
        }
        assertEquals(ArenaWaves.MAX_PER_WAVE, g.wave(500, FIVE).size(), "capped");
    }

    @Test
    void healthRisesSlowlyAndStops() {
        ArenaGenerator g = ArenaGenerator.DEFAULT;
        assertEquals(1.0, g.health(5, 5));
        assertEquals(1.04, g.health(6, 5), 1e-9);
        assertEquals(1.6, g.health(20, 5), 1e-9);
        assertEquals(2.5, g.health(200, 5), 1e-9);
    }

    @Test
    void aRulesFileMaySetItsKnobsAndIsCheckedInWords() {
        ArenaGenerator.Parsed p = ArenaGenerator.parse(Map.of("addPerWave", 1, "kinds",
                List.of(Map.of("mob", "zombie", "from", 1, "weight", 2), Map.of("mob", "husk", "from", 8))), null);
        assertNull(p.problem());
        assertEquals(2, p.generator().kinds().size());
        assertEquals("minecraft:zombie", p.generator().kinds().get(0).mob());
        assertNotNull(ArenaGenerator.parse(Map.of("addPerWave", 9), null).problem());
        assertTrue(ArenaGenerator.parse(Map.of("kinds", List.of(Map.of("mob", "spider"))), null).problem()
                .contains("climbs the walls"));
        assertTrue(ArenaGenerator.parse(Map.of("bogus", 1), null).problem().contains("unknown key"));
        assertTrue(ArenaGenerator.parse("x", null).problem() != null);
        assertEquals(ArenaGenerator.DEFAULT, ArenaGenerator.parse(null, null).generator());
    }

    @Test
    void everyDefaultKindIsAnArenaMob() {
        for (ArenaGenerator.Kind k : ArenaGenerator.DEFAULT.kinds()) {
            assertNull(ArenaWaves.mobProblem(k.mob(), null), k.mob());
        }
        for (ArenaBosses.Boss b : ArenaBosses.TABLE) {
            assertNull(ArenaWaves.mobProblem(b.mob(), null), b.mob());
            assertTrue(!b.mob().equals("minecraft:wither") && !b.mob().equals("minecraft:warden"));
        }
    }
}
