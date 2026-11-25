package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A battle's waves as the panel sets them: the rules file's, the generator's past them, the boss waves. */
class ArenaRunTest {

    private static Map<String, Integer> counts(ArenaWaves.Wave w) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (ArenaWaves.Group g : w.groups()) {
            m.merge(g.mob().replace("minecraft:", ""), g.count(), Integer::sum);
        }
        return m;
    }

    @Test
    void theDefaultIsTheRulesFilesWavesWithABossInWaveFive() throws Exception {
        ArenaWaves base = ArenaSettingsTest.bundled();
        ArenaRun run = ArenaSettings.DEFAULT.run(base);
        assertEquals(5, run.count());
        for (int n = 1; n <= 4; n++) {
            assertEquals(base.wave(n), run.wave(n), "wave " + n + " is the rules file's");
            assertFalse(run.isBossWave(n));
            assertEquals(1.0, run.health(n));
        }
        assertTrue(run.isBossWave(5));
        assertEquals("Rumble the Ravager", run.boss(5).name());
        assertEquals(Map.of("husk", 1, "pillager", 1, "witch", 1, "vindicator", 1), counts(run.wave(5)),
                "the escort: a third of wave 5");
        assertEquals(5, run.size(5));
        assertTrue(run.isLast(5));
        assertEquals(360, run.waveSeconds(5), "a boss wave has twice the time");
        assertEquals(180, run.waveSeconds(4));
        assertEquals(3, run.lives());
    }

    @Test
    void longerBattlesHaveABossEveryFifthWaveAndEndlessNeverEnds() throws Exception {
        ArenaWaves base = ArenaSettingsTest.bundled();
        ArenaRun twenty = new ArenaSettings(ArenaDifficulty.NORMAL, 20).run(base);
        assertEquals(List.of(5, 10, 15, 20), twenty.bossWaves(99));
        assertEquals(List.of("Rumble the Ravager", "Captain Chop", "Bruno the Brute", "King Husk"),
                twenty.bossWaves(99).stream().map(n -> twenty.boss(n).name()).toList());
        assertTrue(twenty.isLast(20));
        ArenaRun endless = new ArenaSettings(ArenaDifficulty.NORMAL, 0).run(base);
        assertTrue(endless.endless());
        assertFalse(endless.isLast(10_000));
        for (int n = 1; n <= 300; n++) {
            assertTrue(endless.size(n) <= ArenaWaves.MAX_PER_WAVE + 1, "never more than a wave may bring: " + n);
            assertTrue(endless.health(n) <= ArenaGenerator.DEFAULT.maxHealth() + 1e-9);
            if (endless.isBossWave(n)) {
                assertTrue(endless.boss(n).health() <= ArenaBosses.MAX_HEALTH);
            }
        }
    }

    @Test
    void theDifficultysSizeAndSwapsApplyToGeneratedWavesAndBosses() throws Exception {
        ArenaWaves base = ArenaSettingsTest.bundled();
        ArenaRun easy = new ArenaSettings(ArenaDifficulty.EASY, 0).run(base);
        for (int n = 1; n <= 40; n++) {
            assertEquals(List.of("husk"), List.copyOf(counts(easy.wave(n)).keySet()), "Easy is husks: wave " + n);
        }
        ArenaRun big = new ArenaSettings(ArenaDifficulty.HARD, 20).run(base);
        ArenaRun normal = new ArenaSettings(ArenaDifficulty.NORMAL, 20).run(base);
        assertTrue(big.size(12) > normal.size(12), "Hard's bigger wave size brings more, in generated waves too");
        assertTrue(easy.size(12) < normal.size(12), "Easy's brings fewer");
        assertEquals(1.25 * ArenaGenerator.DEFAULT.health(12, 5), big.health(12), 1e-9);
        assertEquals(1.25, big.bossHealth());
    }

    @Test
    void exactlyIsTheTableAloneWithNoBoss() throws Exception {
        ArenaWaves base = ArenaSettingsTest.bundled();
        ArenaRun r = ArenaRun.exactly(base);
        assertEquals(5, r.count());
        assertNull(r.boss(5));
        assertEquals(base.wave(5), r.wave(5));
    }
}
