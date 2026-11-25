package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaSettings.Limits;
import com.moddingfromamod.levels.arena.ArenaSettings.Result;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The WAVES selector: 5, 10, 15, 20, Endless; what a rules file may fix or limit; the new keys of arena_waves. */
class ArenaWaveCountTest {

    @Test
    void wavesStepThroughFiveTenFifteenTwentyEndless() {
        ArenaSettings s = ArenaSettings.DEFAULT;
        assertEquals(5, s.waves());
        assertEquals(List.of(5, 10, 15, 20, 0), Limits.NONE.waveOptions());
        assertEquals(Result.AT_END, s.stepWaves(false, null).result());
        List<Integer> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ArenaSettings.Step st = s.stepWaves(true, null);
            assertEquals(Result.CHANGED, st.result());
            s = st.settings();
            seen.add(s.waves());
        }
        assertEquals(List.of(10, 15, 20, 0), seen);
        assertTrue(s.endless());
        assertEquals(Result.AT_END, s.stepWaves(true, null).result());
        assertEquals(20, s.stepWaves(false, null).settings().waves());
        assertEquals(ArenaDifficulty.NORMAL, s.difficulty(), "a waves step keeps the difficulty");
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD, 0), ArenaSettings.of("hard", 0));
        assertEquals(10, new ArenaSettings(null, 10).stepDifficulty(true, null).settings().waves());
    }

    private static ArenaWaves parse(Map<String, Object> extra) {
        Map<String, Object> p = new HashMap<>(Map.of("countdownSeconds", 5, "breatherSeconds", 10, "waveSeconds", 180,
                "waves", List.of(List.of(Map.of("mob", "husk", "count", 3)))));
        p.putAll(extra);
        ArenaWaves.Parsed r = ArenaWaves.parse(p, null);
        return r.ok() ? r.waves() : null;
    }

    private static String problem(Map<String, Object> extra) {
        Map<String, Object> p = new HashMap<>(Map.of("countdownSeconds", 5, "breatherSeconds", 10, "waveSeconds", 180,
                "waves", List.of(List.of(Map.of("mob", "husk", "count", 3)))));
        p.putAll(extra);
        return ArenaWaves.parse(p, null).problem();
    }

    @Test
    void aRulesFileMayFixOrLimitTheWavesInMultiplesOfFive() {
        Limits fixed = parse(Map.of("waveCount", 10)).limits();
        assertEquals(List.of(10), fixed.waveOptions());
        assertEquals(Result.FIXED, ArenaSettings.DEFAULT.stepWaves(true, fixed).result());
        assertEquals(10, ArenaSettings.DEFAULT.within(fixed).waves());
        assertEquals(List.of(0), parse(Map.of("waveCount", "endless")).limits().waveOptions());
        Limits max = parse(Map.of("maxWaveCount", 10)).limits();
        assertEquals(List.of(5, 10), max.waveOptions());
        assertEquals(10, new ArenaSettings(null, 0).within(max).waves(), "Endless above a limit comes down to it");
        assertEquals(List.of(5, 10, 15, 20, 30), parse(Map.of("maxWaveCount", 30)).limits().waveOptions());
        assertTrue(problem(Map.of("waveCount", 7)).contains("multiple of 5"));
        assertTrue(problem(Map.of("maxWaveCount", 12)).contains("multiple of 5"));
        assertTrue(problem(Map.of("waveCount", 10, "maxWaveCount", 20)).contains("not both"));
        assertNotNull(parse(Map.of("waveCount", 995)));
        assertEquals(Map.of("waveCount", "endless"),
                parse(Map.of("waveCount", "endless")).limits().toParams());
    }

    @Test
    void bossesLivesAndTheGeneratorAreOptionalCheckedKeys() {
        ArenaWaves w = parse(Map.of());
        assertEquals(5, w.bossEvery());
        assertEquals(3, w.lives());
        assertEquals(ArenaGenerator.DEFAULT, w.generator());
        ArenaWaves set = parse(Map.of("bossEvery", 0, "lives", 1, "generator", Map.of("addPerWave", 2)));
        assertEquals(0, set.bossEvery());
        assertEquals(1, set.lives());
        assertEquals(2.0, set.generator().addPerWave());
        ArenaWaves again = ArenaWaves.parse(set.toParams(), null).waves();
        assertEquals(set, again, "what the rule hands the core reads back the same");
        assertTrue(problem(Map.of("bossEvery", 1)).contains("bossEvery"));
        assertTrue(problem(Map.of("lives", 0)).contains("lives"));
        assertTrue(problem(Map.of("lives", 11)).contains("lives"));
        assertTrue(problem(Map.of("generator", Map.of("maxHealth", 9))).contains("maxHealth"));
        assertNull(problem(Map.of("waveCount", 20)));
    }

    @Test
    void waveSizesIsAnOptionalCheckedKeyThatReadsBack() {
        ArenaWaves set = parse(Map.of("waveSizes", Map.of("easy", 1, "extreme", 10)));
        assertEquals(1, set.limits().sizeFor(ArenaDifficulty.EASY));
        assertEquals(10, set.limits().sizeFor(ArenaDifficulty.EXTREME));
        assertEquals(set, ArenaWaves.parse(set.toParams(), null).waves(), "what the rule hands the core reads back");
        assertEquals(6, new ArenaSettings(ArenaDifficulty.EASY).run(parse(Map.of("waveSize", 10))).size(1),
                "the old waveSize key: every difficulty's wave size (3 husks at 10 is 6)");
        assertTrue(problem(Map.of("waveSizes", Map.of("hard", 0))).contains("waveSizes.hard"));
    }
}
