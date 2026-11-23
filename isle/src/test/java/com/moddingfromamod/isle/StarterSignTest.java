package com.moddingfromamod.isle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.safety.SafetyLexicon;
import org.junit.jupiter.api.Test;

/** the Isle's starter sign, as words a child reads (moved out of world/GuideBookTest, #2358). */
class StarterSignTest {

    @Test
    void theStarterSignFitsASign() {
        assertEquals(4, StarterSign.LINES.size());
        for (String l : StarterSign.LINES) {
            assertTrue(!l.isBlank() && l.length() <= StarterSign.MAX_CHARS, "too wide for a sign: " + l);
        }
    }

    @Test
    void everyLineOfTheSignPassesTheLexicon() {
        SafetyLexicon lex = SafetyLexicon.shared();
        for (String l : StarterSign.LINES) {
            assertTrue(lex.check(l).isEmpty(), "the lexicon flags: " + l + " → " + lex.check(l));
        }
    }
}
