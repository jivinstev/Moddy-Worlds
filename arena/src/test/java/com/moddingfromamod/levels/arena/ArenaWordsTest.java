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
        assertEquals(java.util.List.of("First wave: 3 monsters", "Last wave: 10 monsters"), ArenaWords.sizeMeans(base));
        assertEquals("Wave size 10: the first wave has 6 monsters, the last has 20.",
                ArenaWords.sizeChanged(10, new ArenaSettings(10, ArenaDifficulty.NORMAL).apply(base)));
        assertEquals("Difficulty: Hard. Tougher monsters, and strong ones sooner.",
                ArenaWords.difficultyChanged(ArenaDifficulty.HARD));
        assertEquals("Battle on: wave 2 of 5", ArenaWords.panelState(ArenaMatch.Phase.WAVE, 2, 5));
        assertEquals("Ready! Press START.", ArenaWords.panelState(ArenaMatch.Phase.IDLE, 0, 5));
        assertEquals("Battle on: wave 3 next", ArenaWords.panelState(ArenaMatch.Phase.BREATHER, 2, 5));
        for (ArenaPanel.Words w : ArenaPanel.Words.values()) {
            for (ArenaMatch.Phase ph : ArenaMatch.Phase.values()) {
                for (String line : ArenaWords.panel(w, ArenaSettings.DEFAULT, base, ph, 1, 5)) {
                    assertTrue(line.length() <= 28, "a board line stays short: " + line);
                    assertTrue(line.chars().allMatch(c -> c >= 32 && c < 127), line);
                }
            }
        }
    }

    @Test
    void numbersReadNaturally() {
        assertEquals("Wave 2 of 5 in 1 second", ArenaWords.barNext(2, 5, 1));
        assertEquals("Wave 2 of 5: 1 monster left", ArenaWords.barWave(2, 5, 1));
        assertEquals("A battle is starting! 1 wave of monsters. The first comes in 5 seconds.", ArenaWords.started(1, 5));
    }
}
