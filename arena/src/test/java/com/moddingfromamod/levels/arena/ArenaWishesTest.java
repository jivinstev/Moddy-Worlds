package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code arena_wishes} on its own and through the core's own rules parser, as a server reads the arena's rules file. */
class ArenaWishesTest {

    private static final List<WorldRuleType> TYPES =
            List.of(new ArenaRule(), new ArenaWavesRule(null), new ArenaWishesRule(), new ArenaCreaturesRule(), new ArenaLadderRule(), new ArenaWishedWavesRule());

    private static String file(String params) {
        return "{\"store\":\"world-rules\",\"v\":1,\"world\":\"arena\",\"rules\":["
                + "{\"id\":\"w\",\"type\":\"arena_wishes\",\"params\":" + params + "}]}";
    }

    @Test
    void theValuesAreChecked() {
        assertEquals(ArenaWishes.DEFAULT, ArenaWishes.parse(Map.of()).wishes());
        assertEquals(new ArenaWishes(5, 30),
                ArenaWishes.parse(Map.of("magicianWishes", 5, "cooldownSeconds", 30)).wishes());
        assertEquals(new ArenaWishes(0, 10), ArenaWishes.parse(Map.of("magicianWishes", 0)).wishes(),
                "0: no Magician wishes in this arena");
        assertFalse(ArenaWishes.parse(Map.of("magicianWishes", -1)).ok());
        assertFalse(ArenaWishes.parse(Map.of("magicianWishes", 21)).ok());
        assertFalse(ArenaWishes.parse(Map.of("cooldownSeconds", 0)).ok());
        assertFalse(ArenaWishes.parse(Map.of("cooldownSeconds", 121)).ok());
        assertFalse(ArenaWishes.parse(Map.of("cooldownSeconds", 2.5)).ok());
        assertFalse(ArenaWishes.parse(Map.of("cooldownSeconds", "ten")).ok());
        assertTrue(ArenaWishes.parse(Map.of("budget", 3)).problem().contains("unknown"));
        assertTrue(ArenaWishes.parse(Map.of("_note", "x")).ok(), "a note is not a param");
        assertThrows(IllegalArgumentException.class, () -> new ArenaWishes(3, 0));
        assertEquals(200, ArenaWishes.DEFAULT.cooldownTicks());
        assertEquals(5, ArenaWishes.DEFAULT.wishesPerWave(), "5 hotbar wishes a wave");
        assertEquals(new ArenaWishes(3, 10, 8), ArenaWishes.parse(Map.of("wishesPerWave", 8)).wishes());
        assertTrue(ArenaWishes.parse(Map.of("wishesPerWave", 1)).ok());
        assertTrue(ArenaWishes.parse(Map.of("wishesPerWave", 50)).ok());
        assertFalse(ArenaWishes.parse(Map.of("wishesPerWave", 0)).ok());
        assertFalse(ArenaWishes.parse(Map.of("wishesPerWave", 51)).ok());
        assertFalse(ArenaWishes.parse(Map.of("wishesPerWave", 2.5)).ok());
        assertEquals("wishesPerWave must be from 1 to 50, not 0", ArenaWishes.parse(Map.of("wishesPerWave", 0)).problem());
        assertThrows(IllegalArgumentException.class, () -> new ArenaWishes(3, 10, 0));
    }

    @Test
    void itsParamsAreAnOrdinaryMap() {
        Map<String, Object> m = ArenaWishes.DEFAULT.toParams();
        m.put("x", 1);   // Map.of would throw: the core cannot take one
        assertEquals(3, m.get("magicianWishes"));
        assertEquals(5, m.get("wishesPerWave"));
    }

    @Test
    void theBundledRulesNameTheFirstValues() throws IOException {
        String text;
        try (InputStream in = ArenaWishesTest.class.getResourceAsStream("/worlds/rules/arena.rules.json")) {
            assertNotNull(in);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        WorldRules.Parsed p = WorldRules.parse(text, "arena", TYPES);
        assertTrue(p.ok(), String.valueOf(p.errors()));
        WorldRule r = p.rules().first(ArenaWishesRule.KEY);
        assertNotNull(r, "the arena's rules carry arena_wishes");
        assertEquals(new ArenaWishes(3, 10, 5), ArenaWishesRule.wishesOf(p.rules()));
        assertTrue(text.contains("the arena and its prep room"), "the reach rule's words say where a wish lands");
    }

    @Test
    void aWrongValueRefusesTheFileInWords() {
        WorldRules.Parsed p = WorldRules.parse(file("{\"magicianWishes\": 99}"), "arena", TYPES);
        assertFalse(p.ok());
        assertTrue(String.join(" ", p.errors()).contains("magicianWishes must be from 0 to 20"), String.valueOf(p.errors()));
        WorldRules.Parsed good = WorldRules.parse(file("{\"magicianWishes\": 1, \"cooldownSeconds\": 4}"), "arena", TYPES);
        assertTrue(good.ok(), String.valueOf(good.errors()));
        assertEquals(new ArenaWishes(1, 4), ArenaWishesRule.wishesOf(good.rules()));
    }

    @Test
    void noRuleMeansTheFirstValues() {
        assertEquals(ArenaWishes.DEFAULT, ArenaWishesRule.wishesOf(null));
    }
}
