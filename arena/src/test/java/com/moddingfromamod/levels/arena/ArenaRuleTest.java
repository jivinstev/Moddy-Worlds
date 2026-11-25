package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code arena_place} through the core's own rules parser, as a server reads the arena's rules file. */
class ArenaRuleTest {

    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(null), new ArenaWishesRule(), new ArenaCreaturesRule());

    private static String bundled() throws IOException {
        try (InputStream in = ArenaRuleTest.class.getResourceAsStream("/worlds/rules/arena.rules.json")) {
            assertNotNull(in, "the jar carries its rules file");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String file(String placeParams) {
        return "{\"store\":\"world-rules\",\"v\":1,\"world\":\"arena\",\"rules\":["
                + "{\"id\":\"the-arena-place\",\"type\":\"arena_place\",\"params\":" + placeParams + "}]}";
    }

    @Test
    void theBundledRulesReadAndNameTheDefaultPlaceAndTheReachRule() throws IOException {
        WorldRules.Parsed p = WorldRules.parse(bundled(), "arena", TYPES);
        assertTrue(p.ok(), String.valueOf(p.errors()));
        WorldRule r = p.rules().first(ArenaRule.KEY);
        assertNotNull(r);
        assertEquals(ArenaRule.KEY, r.pluginType());
        assertEquals(ArenaPlace.DEFAULT, ArenaRule.placeOf(p.rules()));
        assertTrue(p.rules().baseReach(), "a wish stays inside the arena");
    }

    @Test
    void withoutTheArenaJarTheFileIsRefusedNotSkipped() throws IOException {
        assertFalse(WorldRules.parse(bundled(), "arena", List.of()).ok());
    }

    @Test
    void otherSizesAreReadAndWrongOnesRefuseTheFileInWords() {
        WorldRules.Parsed ok = WorldRules.parse(file("{\"arenaSize\":21,\"prepSize\":7,\"wallHeight\":5}"), "arena", TYPES);
        assertTrue(ok.ok(), String.valueOf(ok.errors()));
        assertEquals(new ArenaPlace(21, 7, 5, 64), ArenaRule.placeOf(ok.rules()));
        WorldRules.Parsed bad = WorldRules.parse(file("{\"arenaSize\":20}"), "arena", TYPES);
        assertFalse(bad.ok());
        assertTrue(String.join(" ", bad.errors()).contains("arenaSize must be an odd number"), String.valueOf(bad.errors()));
        assertFalse(WorldRules.parse(file("{\"arenaSize\":\"big\"}"), "arena", TYPES).ok());
        assertFalse(WorldRules.parse(file("{\"size\":21}"), "arena", TYPES).ok());
        WorldRules.Parsed none = WorldRules.parse(file("{}"), "arena", TYPES);
        assertTrue(none.ok(), String.valueOf(none.errors()));
        assertEquals(ArenaPlace.DEFAULT, ArenaRule.placeOf(none.rules()), "every size optional");
        assertEquals(ArenaPlace.DEFAULT, ArenaRule.placeOf(null), "no rules: the default");
    }
}
