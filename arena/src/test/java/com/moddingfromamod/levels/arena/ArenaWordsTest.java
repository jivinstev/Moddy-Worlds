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
    void theSignFitsOnASign() {
        assertEquals(4, ArenaWords.SIGN.size());
        for (String l : ArenaWords.SIGN) {
            assertTrue(l.length() <= 15, l);
        }
    }

    @Test
    void numbersReadNaturally() {
        assertEquals("Wave 2 of 5 in 1 second", ArenaWords.barNext(2, 5, 1));
        assertEquals("Wave 2 of 5: 1 monster left", ArenaWords.barWave(2, 5, 1));
        assertEquals("A battle is starting! 1 wave of monsters. The first comes in 5 seconds.", ArenaWords.started(1, 5));
    }
}
