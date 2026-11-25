package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** What a child reads about a battle: plain words, plain characters, short sign lines. */
class ArenaWordsTest {

    @Test
    void everyLineIsPlainCharactersAndNeverSaysWizard() {
        for (String l : ArenaWords.all()) {
            assertTrue(l.chars().allMatch(c -> c >= 32 && c < 127), "plain characters only: " + l);
            assertTrue(!l.toLowerCase(Locale.ROOT).contains("wizard"), l);
            assertTrue(!l.isBlank());
        }
    }

    @Test
    void thePanelsWordsArePlainShortAndSayWhatTheyDo() throws java.io.IOException {
        ArenaWaves base = ArenaSettingsTest.bundled();
        for (String l : ArenaWords.allPanel(base)) {
            assertTrue(l.chars().allMatch(c -> c >= 32 && c < 127), "plain characters only: " + l);
            assertTrue(!l.toLowerCase(Locale.ROOT).contains("wizard"), l);
            assertTrue(!l.isBlank());
            assertTrue(l.length() <= 80, "one short action-bar line: " + l);
        }
        ArenaRun def = ArenaSettings.DEFAULT.run(base);
        assertEquals(java.util.List.of("First wave: 3 monsters", "Boss wave: 5"), ArenaWords.sizeMeans(def));
        assertEquals(java.util.List.of("First wave: 3 monsters", "Boss waves: 5, 10, 15, 20"),
                ArenaWords.sizeMeans(new ArenaSettings(5, ArenaDifficulty.NORMAL, 20).run(base)));
        assertEquals(java.util.List.of("First wave: 3 monsters", "A boss every 5 waves"),
                ArenaWords.sizeMeans(new ArenaSettings(5, ArenaDifficulty.NORMAL, 0).run(base)));
        assertEquals("Monsters: 10. The first wave has 6 monsters.",
                ArenaWords.sizeChanged(10, new ArenaSettings(10, ArenaDifficulty.NORMAL).run(base)));
        assertEquals("Waves: Endless. They keep coming until you lose!", ArenaWords.wavesChanged(0));
        assertEquals("Waves: 10. A boss comes every 5th wave.", ArenaWords.wavesChanged(10));
        assertEquals("Difficulty: Hard. Tougher monsters, and strong ones sooner.",
                ArenaWords.difficultyChanged(ArenaDifficulty.HARD));
        assertEquals("Battle on: wave 2 of 5", ArenaWords.panelState(ArenaMatch.Phase.WAVE, 2, 5));
        assertEquals("BOSS wave 5 of 5!", ArenaWords.panelState(ArenaMatch.Phase.WAVE, 5, 5, true));
        assertEquals("Battle on: BOSS wave 5 next", ArenaWords.panelState(ArenaMatch.Phase.BREATHER, 4, 5, true));
        assertEquals("Battle on: wave 12", ArenaWords.panelState(ArenaMatch.Phase.WAVE, 12, 0));
        assertEquals("Ready! Press START.", ArenaWords.panelState(ArenaMatch.Phase.IDLE, 0, 5));
        assertEquals("Battle on: wave 3 next", ArenaWords.panelState(ArenaMatch.Phase.BREATHER, 2, 5));
        for (ArenaPanel.Words w : ArenaPanel.Words.values()) {
            for (ArenaMatch.Phase ph : ArenaMatch.Phase.values()) {
                for (String line : ArenaWords.panel(w, ArenaSettings.DEFAULT, def, ph, 1, 5, false, null)) {
                    assertTrue(line.length() <= 32, "a board line stays short: " + line);
                    assertTrue(line.chars().allMatch(c -> c >= 32 && c < 127), line);
                }
            }
        }
        assertEquals("Endless", ArenaWords.panel(ArenaPanel.Words.WAVES_VALUE,
                new ArenaSettings(5, ArenaDifficulty.NORMAL, 0), def, ArenaMatch.Phase.IDLE, 0, 0, false, null).get(0));
    }

    @Test
    void numbersReadNaturally() {
        assertEquals("Wave 2 of 5 in 1 second", ArenaWords.barNext(2, 5, 1));
        assertEquals("Wave 2 of 5: 1 monster left", ArenaWords.barWave(2, 5, 1));
        assertEquals("A battle is starting! 1 wave of monsters. The first comes in 5 seconds. You each have 1 life.",
                ArenaWords.started(1, 5, 1));
        assertEquals("Wave 12: 4 monsters left", ArenaWords.barWave(12, 0, 4));
        assertEquals("BOSS: King Husk (wave 20 of 20)", ArenaWords.barBoss("King Husk", 20, 20));
        assertEquals("Knocked out! 2 lives left. You are back in the prep room: go get them!",
                ArenaWords.knockedOut(2));
    }
}
