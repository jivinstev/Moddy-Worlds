package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaSettings.Limits;
import com.moddingfromamod.levels.arena.ArenaSettings.Result;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The control panel's wave size and difficulty: how they change the waves, and what the rules may fix or limit. */
class ArenaSettingsTest {

    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(null),
            new ArenaWishesRule(), new ArenaCreaturesRule());

    /** The bundled rules file's waves: husks 3; husks 5; husks 4 and pillagers 2; ... (10 in the last wave). */
    static ArenaWaves bundled() throws IOException {
        try (InputStream in = ArenaSettingsTest.class.getResourceAsStream("/worlds/rules/arena.rules.json")) {
            assertNotNull(in);
            WorldRules.Parsed p = WorldRules.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "arena",
                    TYPES);
            assertTrue(p.ok(), String.valueOf(p.errors()));
            return ArenaWavesRule.wavesOf(p.rules());
        }
    }

    /** Each wave as {mob: count}. */
    private static List<Map<String, Integer>> counts(ArenaWaves w) {
        List<Map<String, Integer>> out = new ArrayList<>();
        for (ArenaWaves.Wave wave : w.waves()) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (ArenaWaves.Group g : wave.groups()) {
                m.merge(g.mob().replace("minecraft:", ""), g.count(), Integer::sum);
            }
            out.add(m);
        }
        return out;
    }

    @Test
    void theDefaultsAreTodaysWavesExactly() throws IOException {
        ArenaWaves base = bundled();
        assertEquals(new ArenaSettings(5, ArenaDifficulty.NORMAL), ArenaSettings.DEFAULT);
        assertEquals(base, ArenaSettings.DEFAULT.apply(base), "size 5 on Normal changes nothing");
        assertEquals(List.of(3, 10), List.of(base.firstAndLastSize()[0], base.firstAndLastSize()[1]));
    }

    @Test
    void theSizeScalesEveryCountRoundedWithAtLeastOneOfEachKind() throws IOException {
        ArenaWaves base = bundled();
        assertEquals(List.of(Map.of("husk", 1), Map.of("husk", 1), Map.of("husk", 1, "pillager", 1),
                        Map.of("husk", 1, "pillager", 1, "witch", 1, "vindicator", 1),
                        Map.of("husk", 1, "pillager", 1, "witch", 1, "vindicator", 1)),
                counts(ArenaSettings.scale(base, 1)), "size 1: one of each kind a wave has");
        assertEquals(List.of(Map.of("husk", 6), Map.of("husk", 10), Map.of("husk", 8, "pillager", 4),
                        Map.of("husk", 8, "pillager", 4, "witch", 2, "vindicator", 2),
                        Map.of("husk", 8, "pillager", 6, "witch", 2, "vindicator", 4)),
                counts(ArenaSettings.scale(base, 10)), "size 10: twice today's");
        // half up: 3 x 4 / 5 = 2.4 -> 2, 4 x 4 / 5 = 3.2 -> 3, 2 x 4 / 5 = 1.6 -> 2, 1 x 4 / 5 = 0.8 -> 1
        assertEquals(Map.of("husk", 3, "pillager", 2, "witch", 1, "vindicator", 2),
                counts(ArenaSettings.scale(base, 4)).get(4));
        assertEquals(2, ArenaSettings.scaledCount(3, 4));
        assertEquals(3, ArenaSettings.scaledCount(5, 3));
        assertEquals(1, ArenaSettings.scaledCount(1, 1));
        ArenaWaves eight = ArenaSettings.scale(base, 8);
        assertEquals(base.countdownSeconds(), eight.countdownSeconds());
        assertEquals(base.waveSeconds(), eight.waveSeconds());
        for (int s = 1; s <= 10; s++) {
            ArenaWaves w = ArenaSettings.scale(base, s);
            for (int i = 1; i < w.count(); i++) {
                assertTrue(w.wave(i).size() <= w.wave(i + 1).size() + 1, "still ramps up at size " + s);
            }
            if (s > 1) {
                ArenaWaves less = ArenaSettings.scale(base, s - 1);
                for (int i = 1; i <= w.count(); i++) {
                    assertTrue(w.wave(i).size() >= less.wave(i).size(), "a bigger size never brings fewer");
                }
            }
        }
    }

    @Test
    void theReadmesTableOfFirstAndLastWaves() throws IOException {
        ArenaWaves base = bundled();
        List<Integer> first = new ArrayList<>();
        List<Integer> last = new ArrayList<>();
        for (int s = ArenaSettings.MIN_SIZE; s <= ArenaSettings.MAX_SIZE; s++) {
            int[] fl = new ArenaSettings(s, ArenaDifficulty.NORMAL).apply(base).firstAndLastSize();
            first.add(fl[0]);
            last.add(fl[1]);
        }
        assertEquals(List.of(1, 1, 2, 2, 3, 4, 4, 5, 5, 6), first);
        assertEquals(List.of(4, 5, 6, 8, 10, 12, 14, 16, 18, 20), last);
    }

    @Test
    void aScaledWaveNeverBringsMoreThanAWaveMay() {
        ArenaWaves big = new ArenaWaves(5, 10, 180, List.of(new ArenaWaves.Wave(List.of(
                new ArenaWaves.Group("minecraft:husk", 20), new ArenaWaves.Group("minecraft:pillager", 15),
                new ArenaWaves.Group("minecraft:witch", 1)))));
        ArenaWaves w = ArenaSettings.scale(big, 10);
        assertEquals(ArenaWaves.MAX_PER_WAVE, w.wave(1).size());
        assertEquals(2, w.wave(1).groups().get(2).count(), "the smallest group is kept as scaled");
        assertTrue(w.wave(1).groups().get(0).count() >= w.wave(1).groups().get(1).count());
    }

    @Test
    void stepsMoveOneAtATimeAndStopAtTheEnds() {
        ArenaSettings s = ArenaSettings.DEFAULT;
        assertEquals(new ArenaSettings.Step(new ArenaSettings(6, ArenaDifficulty.NORMAL), Result.CHANGED),
                s.stepSize(true, Limits.NONE));
        assertEquals(new ArenaSettings.Step(new ArenaSettings(4, ArenaDifficulty.NORMAL), Result.CHANGED),
                s.stepSize(false, null));
        assertEquals(Result.AT_END, new ArenaSettings(10, ArenaDifficulty.NORMAL).stepSize(true, null).result());
        assertEquals(Result.AT_END, new ArenaSettings(1, ArenaDifficulty.NORMAL).stepSize(false, null).result());
        assertEquals(ArenaDifficulty.HARD, s.stepDifficulty(true, null).settings().difficulty());
        assertEquals(ArenaDifficulty.EASY, s.stepDifficulty(false, null).settings().difficulty());
        assertEquals(Result.AT_END, new ArenaSettings(5, ArenaDifficulty.EASY).stepDifficulty(false, null).result());
        assertEquals(Result.AT_END, new ArenaSettings(5, ArenaDifficulty.EXTREME).stepDifficulty(true, null).result());
        assertEquals(5, s.stepDifficulty(true, null).settings().size(), "a difficulty step keeps the size");
        assertEquals(new ArenaSettings(10, ArenaDifficulty.NORMAL), new ArenaSettings(99, null), "clamped");
    }

    @Test
    void theRulesMayFixOrLimitEach() {
        Limits fixed = new Limits(3, 10, ArenaDifficulty.HARD, ArenaDifficulty.EXTREME);
        ArenaSettings s = new ArenaSettings(8, ArenaDifficulty.EASY);
        assertEquals(new ArenaSettings(3, ArenaDifficulty.HARD), s.within(fixed));
        assertEquals(Result.FIXED, s.stepSize(true, fixed).result());
        assertEquals(3, s.stepSize(true, fixed).settings().size());
        assertEquals(Result.FIXED, s.stepDifficulty(false, fixed).result());
        Limits capped = new Limits(null, 6, null, ArenaDifficulty.HARD);
        assertEquals(new ArenaSettings(6, ArenaDifficulty.HARD),
                new ArenaSettings(9, ArenaDifficulty.EXTREME).within(capped));
        assertEquals(Result.AT_END, new ArenaSettings(6, ArenaDifficulty.HARD).stepSize(true, capped).result());
        assertEquals(Result.AT_END, new ArenaSettings(6, ArenaDifficulty.HARD).stepDifficulty(true, capped).result());
        assertEquals(Result.CHANGED, new ArenaSettings(6, ArenaDifficulty.HARD).stepSize(false, capped).result());
        assertEquals(new ArenaSettings(5, ArenaDifficulty.NORMAL), ArenaSettings.DEFAULT.within(null));
    }

    @Test
    void savedValuesReadBackAndUnknownOnesAreTheDefaults() {
        assertEquals(new ArenaSettings(7, ArenaDifficulty.HARD), ArenaSettings.of(7, "hard"));
        assertEquals(new ArenaSettings(7, ArenaDifficulty.HARD), ArenaSettings.of(7, " HARD "));
        assertEquals(ArenaSettings.DEFAULT, ArenaSettings.of(null, null), "a world saved before the panel");
        assertEquals(ArenaSettings.DEFAULT, ArenaSettings.of(5, "nightmare"));
        assertNull(ArenaDifficulty.byId("nightmare"));
        assertSame(ArenaDifficulty.EXTREME, ArenaDifficulty.byId("extreme"));
    }
}
