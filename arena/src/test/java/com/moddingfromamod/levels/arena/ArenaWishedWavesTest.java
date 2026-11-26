package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.moddingfromamod.wishbuilder.BuilderAllowlist;
import com.moddingfromamod.wishbuilder.BuilderSpec;
import org.junit.jupiter.api.Test;

class ArenaWishedWavesTest {

    private static final ArenaWishedWaves D = ArenaWishedWaves.DEFAULT;

    private static ArenaWishedWaves.Parsed parse(Map<String, ?> params) {
        return ArenaWishedWaves.parse(params, ArenaWishedWaves::builderBody);
    }

    private static ArenaWaves.Wave wave(int husks, int pillagers) {
        return new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", husks),
                new ArenaWaves.Group("minecraft:pillager", pillagers)));
    }

    @Test
    void theShippedRulesFileIsTheDefaultAndRoundTrips() throws Exception {
        JsonObject file = JsonParser.parseString(Files.readString(
                Path.of("src/level/resources/worlds/rules/arena.rules.json"))).getAsJsonObject();
        JsonObject params = null;
        for (var e : file.getAsJsonArray("rules")) {
            if (e.getAsJsonObject().get("type").getAsString().equals(ArenaWishedWavesRule.KEY)) {
                params = e.getAsJsonObject().getAsJsonObject("params");
            }
        }
        assertNotNull(params, "the shipped rules carry no " + ArenaWishedWavesRule.KEY);
        JsonObject raw = new JsonObject();
        raw.add("params", params);
        ArenaWishedWaves.Parsed fromFile = parse(ArenaWavesRule.plainMap(params));
        assertTrue(fromFile.ok(), fromFile.problem());
        assertEquals(D, fromFile.waves(), "the file and DEFAULT disagree");
        ArenaWishedWaves.Parsed again = parse(D.toParams());
        assertTrue(again.ok(), again.problem());
        assertEquals(D, again.waves());
        assertEquals(D, parse(Map.of()).waves(), "no keys is the default");
    }

    @Test
    void rungZeroIsThePlainMobAsABuilderRealMob() {
        BuilderSpec s = D.mob("minecraft:husk", 0);
        assertEquals("mob:minecraft:husk", s.from());
        assertTrue(s.opts().isEmpty(), "rung 0 picks nothing: the plain mob, " + s.opts());
        assertTrue(s.powers().isEmpty() && s.scheme().isEmpty(), "a Builder real mob takes no powers or colours");
    }

    @Test
    void aWeakerRungAMisspeltBodyOrAnUnknownKeyIsRefusedInWords() {
        assertFalse(parse(Map.of("ramp", List.of(Map.of("after", 0, "size", "big", "strength", 3),
                Map.of("after", 2, "size", "huge", "strength", 2)))).ok(), "Huge at 2 hits weaker than Big at 3");
        assertFalse(parse(Map.of("ramp", List.of(Map.of("after", 1)))).ok(), "the first rung must be after 0");
        assertFalse(parse(Map.of("bodies", List.of(Map.of("after", 0, "pick", List.of("Not A Body!"))))).ok(),
                "a name that cannot be a body's is a typo");
        assertFalse(parse(Map.of("championEvery", 1)).ok());
        ArenaWishedWaves.Parsed typo = parse(Map.of("champoinEvery", 4));
        assertFalse(typo.ok());
        assertTrue(typo.problem().contains("champoinEvery"), typo.problem());
    }

    @Test
    void eachBattleDrawsItsOwnChampionsFromTheTiers() {
        Set<List<String>> seen = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            ArenaWishedWaves.Picks p = D.pick(new Random(seed));
            assertEquals(D.bodies().size(), p.perTier().size());
            for (int i = 0; i < p.perTier().size(); i++) {
                assertTrue(D.bodies().get(i).pick().contains(p.perTier().get(i)));
            }
            seen.add(p.perTier());
        }
        assertTrue(seen.size() > 3, "forty battles drew only " + seen.size() + " different sets of champions");
        assertEquals(D.pick(new Random(7)), D.pick(new Random(7)), "the same draw for the same seed");
    }

    @Test
    void championsAreBuilderDesignsTheWorldsListAllowsAndNoChildsDoes() {
        ArenaWishedWaves.Picks picks = D.pick(new Random(1));
        for (int r = 0; r < D.ramp().size(); r++) {
            BuilderSpec c = D.champion(r, picks);
            assertEquals("", c.scheme(), "no scheme in the core: the arena's colours go inline");
            assertEquals("#16131A,#B3121B,#FF3B3B", c.opt(com.moddingfromamod.wishbuilder.Schemes.PALETTE_OPT));
            assertTrue(c.name().startsWith("Shadow "), c.name());
            assertTrue(c.from().startsWith("preset:bodies/"), c.from());
            assertTrue(c.powers().contains("power:hostile") && c.powers().contains("power:glowing"));
            // Moddy-Worlds#61: "" lowers to friendly; a champion is on the monsters' side
            assertEquals("wild", c.temper(), "a champion's temperament");
            assertTrue(BuilderAllowlist.FOR_WORLDS.problems(c).isEmpty(),
                    "the world's own list refuses its champion: " + BuilderAllowlist.FOR_WORLDS.problems(c));
            assertFalse(BuilderAllowlist.REVIEWED.problems(c).isEmpty(), "a child's Builder may make a hostile monster");
        }
    }

    @Test
    void oneInFourIsAChampionDesignsAndFullPillarsAreCapped() {
        ArenaWishedWaves.Picks picks = D.pick(new Random(2));
        List<ArenaWishedWaves.Plan> plan = D.plan(wave(20, 20), 5, picks);
        assertEquals(40, plan.size());
        assertEquals(10, plan.stream().filter(ArenaWishedWaves.Plan::champion).count());
        assertEquals(D.maxDesignChampions(), plan.stream().filter(p -> p.design() != null).count());
        assertEquals(D.maxFullPillars(), plan.stream().filter(p -> !p.slim()).count());
        for (ArenaWishedWaves.Plan p : plan) {
            assertNotNull(p.mob(), "every monster has a real-mob spec to stand in");
            if (!p.champion()) {
                assertTrue(p.slim());
                assertNull(p.design());
            }
        }
        List<ArenaWishedWaves.Plan> small = D.plan(wave(1, 0), 0, picks);
        assertTrue(small.get(0).champion() && !small.get(0).slim() && small.get(0).design() != null,
                "a wave of one still has its champion");
    }

    @Test
    void offInTheRuleOrBySwitchIsOff() {
        assertFalse(parse(Map.of("on", false)).waves().active());
        String was = System.getProperty("moddyarena.wishedWaves");
        try {
            System.clearProperty("moddyarena.wishedWaves");
            assertTrue(D.active());
            System.setProperty("moddyarena.wishedWaves", "false");
            assertFalse(D.active());
        } finally {
            if (was == null) {
                System.clearProperty("moddyarena.wishedWaves");
            } else {
                System.setProperty("moddyarena.wishedWaves", was);
            }
        }
    }

    /** A body the installed Builder lacks never refuses the file: a battle leaves it out, with a warning. */
    @Test
    void anUnknownBodyIsSkippedNotRefused() {
        ArenaWishedWaves.Parsed p = parse(Map.of("bodies", List.of(
                Map.of("after", 0, "pick", List.of("unicorn_dragon", "ogre")),
                Map.of("after", 5, "pick", List.of("gone_body")),
                Map.of("after", 10, "pick", List.of("yeti")))));
        assertTrue(p.ok(), String.valueOf(p.problem()));
        ArenaWishedWaves.Usable u = p.waves().usable(b -> List.of("ogre", "yeti").contains(b));
        assertEquals(List.of(new ArenaWishedWaves.Bodies(0, List.of("ogre")), new ArenaWishedWaves.Bodies(10,
                List.of("yeti"))), u.waves().bodies());
        assertEquals(2, u.skipped().size(), u.skipped().toString());
        ArenaWishedWaves.Usable none = p.waves().usable(b -> false);
        assertTrue(none.waves().bodies().isEmpty());
        assertTrue(none.waves().designsAhead(0, none.waves().pick(new java.util.Random(1))).isEmpty());
        ArenaWaves.Wave w = new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 8)));
        for (ArenaWishedWaves.Plan plan : none.waves().plan(w, 6, none.waves().pick(new java.util.Random(1)))) {
            assertTrue(plan.design() == null, "every champion is its stand-in");
        }
        assertEquals(D, D.usable(b -> true).waves(), "nothing missing: unchanged");
    }

    /** Champion colours are the arena's data: a palette by name, three hex colours each; a bad one is skipped. */
    @Test
    void palettesAreDataAndABadOneIsSkipped() {
        ArenaWishedWaves.Parsed p = parse(Map.of("palette", "ember", "palettes", Map.of(
                "ember", "#401000, #FF6A00,#FFD000", "broken", "red,green,blue")));
        assertTrue(p.ok(), String.valueOf(p.problem()));
        assertTrue(p.waves().palettes().containsKey("shadow"), "the shipped palette stays beside a server's own");
        ArenaWishedWaves.Usable u = p.waves().usable(b -> true);
        assertEquals("#401000,#FF6A00,#FFD000", u.waves().colours());
        assertFalse(u.waves().palettes().containsKey("broken"));
        assertEquals(1, u.skipped().size(), u.skipped().toString());
        BuilderSpec c = u.waves().champion(0, u.waves().pick(new Random(1)));
        assertEquals("#401000,#FF6A00,#FFD000", c.opt(com.moddingfromamod.wishbuilder.Schemes.PALETTE_OPT));
        assertTrue(c.name().startsWith("Ember "), c.name());
        assertNotNull(com.moddingfromamod.wishbuilder.Schemes.parsePalette(
                c.opt(com.moddingfromamod.wishbuilder.Schemes.PALETTE_OPT)), "the core reads the arena's palette");
        // the named palette is the broken one: champions keep their bodies' own colours, and say so
        ArenaWishedWaves.Usable bad = parse(Map.of("palette", "broken", "palettes", Map.of("broken", "#12345,#000000,#000000")))
                .waves().usable(b -> true);
        assertEquals("", bad.waves().colours());
        assertEquals(2, bad.skipped().size(), bad.skipped().toString());
        BuilderSpec own = bad.waves().champion(0, bad.waves().pick(new Random(1)));
        assertEquals("", own.opt(com.moddingfromamod.wishbuilder.Schemes.PALETTE_OPT));
        assertFalse(own.name().startsWith("Broken"), own.name());
        assertFalse(parse(Map.of("palettes", List.of("#000000"))).ok(), "palettes is a map");
        assertFalse(parse(Map.of("palette", "Not A Name")).ok());
    }
}
