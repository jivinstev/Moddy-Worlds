package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code arena_creatures}: the numbers of the creatures' battle roles, checked, and a healer never too strong. */
class ArenaCreaturesTest {

    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(null),
            new ArenaWishesRule(), new ArenaCreaturesRule());

    private static String file(String params) {
        return "{\"store\":\"world-rules\",\"v\":1,\"world\":\"arena\",\"rules\":["
                + "{\"id\":\"c\",\"type\":\"arena_creatures\",\"params\":" + params + "}]}";
    }

    @Test
    void everyKeyIsOptional() {
        assertEquals(ArenaCreatures.DEFAULT, ArenaCreatures.parse(Map.of()).creatures());
        assertEquals(5.0, ArenaCreatures.parse(Map.of("fighterAttack", 5)).creatures().fighterAttack());
        assertEquals(ArenaCreatures.DEFAULT, ArenaCreatures.parse(ArenaCreatures.DEFAULT.toParams()).creatures(),
                "its own params read back the same");
        assertTrue(ArenaCreatures.parse(Map.of("_note", "x")).ok());
    }

    @Test
    void wrongValuesAreRefusedInWords() {
        assertEquals("fighterAttack must be from 0.5 to 50, not 0",
                ArenaCreatures.parse(Map.of("fighterAttack", 0)).problem());
        assertFalse(ArenaCreatures.parse(Map.of("tankHealth", 5)).ok());
        assertFalse(ArenaCreatures.parse(Map.of("tankWidth", 9)).ok());
        assertFalse(ArenaCreatures.parse(Map.of("healSeconds", 2.5)).ok(), "a whole number of seconds");
        assertFalse(ArenaCreatures.parse(Map.of("tankResistance", 4)).ok());
        assertFalse(ArenaCreatures.parse(Map.of("tauntRadius", "far")).ok());
        assertTrue(ArenaCreatures.parse(Map.of("healers", 3)).problem().contains("unknown"));
        assertThrows(IllegalArgumentException.class, () -> new ArenaCreatures(3, 40, 1.3, 9, 4, 6, 8, 1));
    }

    /**
     * The unbeatable check: a healer may never heal faster than {@link ArenaCreatures#MAX_HEAL_PER_SECOND}, a little
     * under half of what the weakest monster in a wave does to a player who stands still (a husk on Easy: its punch of
     * 3 is 2.5 on Easy, once a second), so a fighter with every healer in the world beside him can still be knocked
     * out by one husk. The default heals half a health a second.
     */
    @Test
    void aHealerNeverMakesAPlayerUnbeatable() {
        double weakestMonsterPerSecond = 2.5;
        assertEquals(0.5, ArenaCreatures.DEFAULT.healPerSecond(), 1e-9);
        assertTrue(ArenaCreatures.MAX_HEAL_PER_SECOND < weakestMonsterPerSecond / 2);
        assertTrue(ArenaCreatures.parse(Map.of("healAmount", 4, "healSeconds", 4)).ok(), "1 a second: allowed");
        String why = ArenaCreatures.parse(Map.of("healAmount", 4, "healSeconds", 2)).problem();
        assertTrue(why != null && why.contains("unbeatable"), why);
        for (int s = 2; s <= 20; s++) {
            for (double a = 0.5; a <= 4; a += 0.5) {
                ArenaCreatures.Parsed p = ArenaCreatures.parse(Map.of("healAmount", a, "healSeconds", s));
                if (p.ok()) {
                    assertTrue(p.creatures().healPerSecond() <= ArenaCreatures.MAX_HEAL_PER_SECOND + 1e-9);
                }
            }
        }
    }

    @Test
    void theBundledRulesNameTheFirstValues() throws IOException {
        String text;
        try (InputStream in = ArenaCreaturesTest.class.getResourceAsStream("/worlds/rules/arena.rules.json")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        WorldRules.Parsed p = WorldRules.parse(text, "arena", TYPES);
        assertTrue(p.ok(), String.valueOf(p.errors()));
        assertTrue(p.rules().first(ArenaCreaturesRule.KEY) != null, "the arena's rules carry arena_creatures");
        assertEquals(ArenaCreatures.DEFAULT, ArenaCreaturesRule.creaturesOf(p.rules()));
    }

    @Test
    void aWrongValueRefusesTheFile() {
        WorldRules.Parsed p = WorldRules.parse(file("{\"healAmount\": 99}"), "arena", TYPES);
        assertFalse(p.ok());
        assertTrue(String.join(" ", p.errors()).contains("healAmount must be from 0.5 to 4"), String.valueOf(p.errors()));
        WorldRules.Parsed good = WorldRules.parse(file("{\"tankHealth\": 60}"), "arena", TYPES);
        assertTrue(good.ok(), String.valueOf(good.errors()));
        assertEquals(60.0, ArenaCreaturesRule.creaturesOf(good.rules()).tankHealth());
        assertEquals(ArenaCreatures.DEFAULT, ArenaCreaturesRule.creaturesOf(null), "no rule: the first values");
    }
}
