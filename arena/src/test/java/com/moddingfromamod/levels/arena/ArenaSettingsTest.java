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

/** The control panel's difficulty (and the wave size it brings): how they change the waves, and what the rules may fix or limit. */
class ArenaSettingsTest {

    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(null),
            new ArenaWishesRule(), new ArenaCreaturesRule(), new ArenaLadderRule(), new ArenaWishedWavesRule());

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
        assertEquals(new ArenaSettings(ArenaDifficulty.NORMAL), ArenaSettings.DEFAULT);
        assertEquals(5, ArenaSettings.DEFAULT.size(base.limits()), "Normal brings wave size 5");
        assertEquals(base, ArenaSettings.DEFAULT.apply(base), "Normal changes nothing");
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
    void eachDifficultyBringsItsOwnWaveSizeAndTheReadmesTable() throws IOException {
        ArenaWaves base = bundled();
        List<Integer> sizes = new ArrayList<>();
        List<Integer> first = new ArrayList<>();
        List<Integer> last = new ArrayList<>();
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            ArenaSettings s = new ArenaSettings(d);
            sizes.add(s.size(base.limits()));
            int[] fl = s.apply(base).firstAndLastSize();
            first.add(fl[0]);
            last.add(fl[1]);
        }
        assertEquals(List.of(3, 5, 6, 8), sizes, "Easy, Normal, Hard, Extreme");
        assertEquals(List.of(2, 3, 4, 5), first, "one more in the first wave each step");
        assertEquals(List.of(6, 10, 12, 16), last);
        for (int i = 1; i < ArenaDifficulty.values().length; i++) {
            assertTrue(ArenaDifficulty.values()[i].size() > ArenaDifficulty.values()[i - 1].size(),
                    "a harder difficulty always brings more");
        }
        // every wave of a battle of 20, and of 100 Endless waves, at the hardest: never more than a wave may bring
        ArenaRun extreme = new ArenaSettings(ArenaDifficulty.EXTREME, ArenaSettings.ENDLESS).run(base);
        for (int n = 1; n <= 100; n++) {
            assertTrue(extreme.size(n) <= ArenaWaves.MAX_PER_WAVE + 1, "wave " + n);
        }
    }

    @Test
    void theRulesMaySetTheWaveSizes() {
        Limits own = Limits.parse(Map.of("waveSizes", Map.of("easy", 2, "hard", 9))).limits();
        assertEquals(2, own.sizeFor(ArenaDifficulty.EASY));
        assertEquals(5, own.sizeFor(ArenaDifficulty.NORMAL), "a difficulty not named keeps its own");
        assertEquals(9, own.sizeFor(ArenaDifficulty.HARD));
        assertEquals(8, own.sizeFor(ArenaDifficulty.EXTREME));
        assertEquals(Map.of("waveSizes", Map.of("easy", 2, "hard", 9)), own.toParams());
        assertEquals(own, Limits.parse(own.toParams()).limits(), "its params read back the same");
        // the old keys still read: waveSize is every difficulty's, maxWaveSize caps every difficulty's
        Limits fixed = Limits.parse(Map.of("waveSize", 4)).limits();
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            assertEquals(4, fixed.sizeFor(d));
        }
        Limits capped = Limits.parse(Map.of("maxWaveSize", 5)).limits();
        assertEquals(List.of(3, 5, 5, 5), List.of(capped.sizeFor(ArenaDifficulty.EASY),
                capped.sizeFor(ArenaDifficulty.NORMAL), capped.sizeFor(ArenaDifficulty.HARD),
                capped.sizeFor(ArenaDifficulty.EXTREME)));
        assertEquals(Map.of("maxWaveSize", 5), capped.toParams());
        // and what is refused, in words
        assertTrue(Limits.parse(Map.of("waveSize", 4, "waveSizes", Map.of("easy", 2))).problem().contains("not both"));
        assertTrue(Limits.parse(Map.of("waveSizes", Map.of("nightmare", 2))).problem().contains("nightmare"));
        assertTrue(Limits.parse(Map.of("waveSizes", Map.of("easy", 11))).problem().contains("1 to 10"));
        assertTrue(Limits.parse(Map.of("waveSizes", 3)).problem().contains("object"));
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
        assertEquals(new ArenaSettings.Step(new ArenaSettings(ArenaDifficulty.HARD), Result.CHANGED),
                s.stepDifficulty(true, Limits.NONE));
        assertEquals(ArenaDifficulty.EASY, s.stepDifficulty(false, null).settings().difficulty());
        assertEquals(Result.AT_END, new ArenaSettings(ArenaDifficulty.EASY).stepDifficulty(false, null).result());
        assertEquals(Result.AT_END, new ArenaSettings(ArenaDifficulty.EXTREME).stepDifficulty(true, null).result());
        assertEquals(6, s.stepDifficulty(true, null).settings().size(null), "a step harder brings more monsters");
        assertEquals(5, s.stepDifficulty(true, null).settings().waves(), "a difficulty step keeps the waves");
        assertEquals(ArenaSettings.DEFAULT, new ArenaSettings(null), "no difficulty is the default");
    }

    @Test
    void theRulesMayFixOrLimitEach() {
        Limits fixed = new Limits(null, 10, ArenaDifficulty.HARD, ArenaDifficulty.EXTREME);
        ArenaSettings s = new ArenaSettings(ArenaDifficulty.EASY);
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD), s.within(fixed));
        assertEquals(Result.FIXED, s.stepDifficulty(false, fixed).result());
        Limits capped = new Limits(null, 10, null, ArenaDifficulty.HARD);
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD), new ArenaSettings(ArenaDifficulty.EXTREME).within(capped));
        assertEquals(Result.AT_END, new ArenaSettings(ArenaDifficulty.HARD).stepDifficulty(true, capped).result());
        assertEquals(Result.CHANGED, new ArenaSettings(ArenaDifficulty.HARD).stepDifficulty(false, capped).result());
        assertEquals(ArenaSettings.DEFAULT, ArenaSettings.DEFAULT.within(null));
    }

    @Test
    void savedValuesReadBackAndUnknownOnesAreTheDefaults() {
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD, 10), ArenaSettings.of("hard", 10));
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD), ArenaSettings.of(" HARD ", null));
        assertEquals(ArenaSettings.DEFAULT, ArenaSettings.of(null, null), "a world saved before the panel");
        assertEquals(ArenaSettings.DEFAULT, ArenaSettings.of("nightmare", null));
        assertNull(ArenaDifficulty.byId("nightmare"));
        assertSame(ArenaDifficulty.EXTREME, ArenaDifficulty.byId("extreme"));
    }
}
