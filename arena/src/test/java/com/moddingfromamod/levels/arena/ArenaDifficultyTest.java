package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The four difficulties: which mobs come and how tough they are, never how many. */
class ArenaDifficultyTest {

    /** Mobs that walk, stay in the arena and are hostile: what a swap may bring in. */
    private static final Set<String> WALKERS = Set.of("minecraft:husk", "minecraft:pillager", "minecraft:witch",
            "minecraft:vindicator", "minecraft:ravager");

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
    void theTable() {
        assertEquals(List.of("easy", "normal", "hard", "extreme"),
                java.util.Arrays.stream(ArenaDifficulty.values()).map(ArenaDifficulty::id).toList());
        assertEquals(List.of("Easy", "Normal", "Hard", "Extreme"),
                java.util.Arrays.stream(ArenaDifficulty.values()).map(ArenaDifficulty::word).toList());
        assertEquals(1.0, ArenaDifficulty.NORMAL.health());
        assertTrue(ArenaDifficulty.NORMAL.swaps().isEmpty());
        double last = 0;
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            assertTrue(d.health() > last, "each step is tougher than the one before");
            last = d.health();
            for (ArenaDifficulty.Swap s : d.swaps()) {
                assertTrue(WALKERS.contains(s.to()), s.to() + " walks and stays in the arena");
                assertFalse(ArenaWaves.REFUSED.containsKey(s.to()), s.to() + " is not refused as a wave mob");
                assertTrue(s.fromWave() >= 1);
            }
        }
    }

    @Test
    void normalIsTodaysWavesAndNoStepChangesHowMany() throws IOException {
        ArenaWaves base = ArenaSettingsTest.bundled();
        assertEquals(base, ArenaDifficulty.NORMAL.apply(base));
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            for (int size = ArenaSettings.MIN_SIZE; size <= ArenaSettings.MAX_SIZE; size++) {
                ArenaWaves scaled = ArenaSettings.scale(base, size);
                ArenaWaves at = d.apply(scaled);
                assertEquals(scaled.count(), at.count());
                for (int i = 1; i <= at.count(); i++) {
                    assertEquals(scaled.wave(i).size(), at.wave(i).size(), d + " keeps wave " + i + "'s count");
                }
                assertEquals(new ArenaSettings(size, d).apply(base), at, "size first, then difficulty");
            }
        }
    }

    @Test
    void whichMobsComeAtEachStep() throws IOException {
        ArenaWaves base = ArenaSettingsTest.bundled();
        assertEquals(List.of(Map.of("husk", 3), Map.of("husk", 5), Map.of("husk", 6), Map.of("husk", 8),
                Map.of("husk", 10)), counts(ArenaDifficulty.EASY.apply(base)), "Easy: husks only");
        assertEquals(List.of(Map.of("husk", 3), Map.of("husk", 5), Map.of("husk", 4, "pillager", 2),
                        Map.of("vindicator", 5, "pillager", 2, "witch", 1),
                        Map.of("vindicator", 6, "pillager", 3, "witch", 1)),
                counts(ArenaDifficulty.HARD.apply(base)), "Hard: vindicators from wave 4");
        assertEquals(List.of(Map.of("husk", 3), Map.of("pillager", 5), Map.of("vindicator", 4, "pillager", 2),
                        Map.of("vindicator", 5, "pillager", 2, "witch", 1),
                        Map.of("vindicator", 4, "pillager", 3, "witch", 1, "ravager", 2)),
                counts(ArenaDifficulty.EXTREME.apply(base)),
                "Extreme: pillagers in wave 2, vindicators from wave 3, ravagers in wave 5; never swapped twice");
    }

    @Test
    void aTableWithNoneOfAStepsMobsGetsOnlyItsHealth() {
        ArenaWaves zombies = new ArenaWaves(5, 10, 180, List.of(new ArenaWaves.Wave(List.of(
                new ArenaWaves.Group("minecraft:zombie", 4)))));
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            assertEquals(zombies, d.apply(zombies).withWaves(zombies.waves()));
            assertEquals(zombies.waves(), d.apply(zombies).waves());
        }
    }
}
