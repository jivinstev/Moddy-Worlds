package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@code arena_waves}: the wave table, checked as the rules file is read. */
class ArenaWavesTest {

    private static final Set<String> KNOWN = Set.of("minecraft:husk", "minecraft:pillager", "minecraft:witch",
            "minecraft:vindicator", "minecraft:zombie");
    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(KNOWN::contains),
            new ArenaWishesRule());

    private static String bundled() throws IOException {
        try (InputStream in = ArenaWavesTest.class.getResourceAsStream("/worlds/rules/arena.rules.json")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String file(String params) {
        return "{\"store\":\"world-rules\",\"v\":1,\"world\":\"arena\",\"rules\":["
                + "{\"id\":\"w\",\"type\":\"arena_waves\",\"params\":" + params + "}]}";
    }

    private static String waves(String waves) {
        return "{\"countdownSeconds\":5,\"breatherSeconds\":10,\"waveSeconds\":180,\"waves\":" + waves + "}";
    }

    private static WorldRules.Parsed read(String params) {
        return WorldRules.parse(file(params), "arena", TYPES);
    }

    private static String errors(WorldRules.Parsed p) {
        return String.join(" ", p.errors());
    }

    @Test
    void theBundledTableReadsAndRampsUp() throws IOException {
        WorldRules.Parsed p = WorldRules.parse(bundled(), "arena", TYPES);
        assertTrue(p.ok(), String.valueOf(p.errors()));
        ArenaWaves w = ArenaWavesRule.wavesOf(p.rules());
        assertNotNull(w);
        assertEquals(5, w.count());
        assertEquals(5, w.countdownSeconds());
        assertEquals(15, w.breatherSeconds());
        assertEquals(45, w.bossBreatherSeconds(), "a longer break after a boss wave");
        assertEquals(180, w.waveSeconds());
        for (int i = 2; i <= w.count(); i++) {
            assertTrue(w.wave(i).size() >= w.wave(i - 1).size(), "wave " + i + " brings no fewer mobs");
            assertTrue(w.wave(i).groups().size() >= w.wave(i - 1).groups().size(), "wave " + i + " no fewer kinds");
        }
        assertTrue(w.wave(5).size() > w.wave(1).size());
        assertEquals(List.of(new ArenaWaves.Group("minecraft:husk", 3)), w.wave(1).groups());
    }

    @Test
    void noTableMeansNoBattle() {
        assertNull(ArenaWavesRule.wavesOf(null));
        assertNull(ArenaWavesRule.wavesOf(WorldRules.parse("{\"store\":\"world-rules\",\"v\":1,\"world\":\"arena\","
                + "\"rules\":[]}", "arena", TYPES).rules()));
    }

    @Test
    void aBareIdMeansMinecraftAndCaseIsIgnored() {
        WorldRules.Parsed p = read(waves("[[{\"mob\":\"Husk\",\"count\":2}]]"));
        assertTrue(p.ok(), errors(p));
        assertEquals("minecraft:husk", ArenaWavesRule.wavesOf(p.rules()).wave(1).groups().get(0).mob());
    }

    @Test
    void mobsThatCannotStayInTheArenaAreRefusedSayingWhy() {
        assertTrue(errors(read(waves("[[{\"mob\":\"minecraft:spider\",\"count\":2}]]"))).contains("climbs the walls"));
        assertTrue(errors(read(waves("[[{\"mob\":\"phantom\",\"count\":1}]]"))).contains("flies over the walls"));
        assertTrue(errors(read(waves("[[{\"mob\":\"minecraft:enderman\",\"count\":1}]]"))).contains("teleports"));
        assertTrue(errors(read(waves("[[{\"mob\":\"minecraft:creeper\",\"count\":1}]]"))).contains("holes"));
        assertTrue(errors(read(waves("[[{\"mob\":\"minecraft:silverfish\",\"count\":1}]]"))).contains("walls"));
    }

    @Test
    void anUnknownOrNotLivingIdIsRefused() {
        assertTrue(errors(read(waves("[[{\"mob\":\"minecraft:husky\",\"count\":1}]]")))
                .contains("not a living mob the game knows"));
        assertTrue(errors(read(waves("[[{\"mob\":\"not an id!\",\"count\":1}]]"))).contains("is not a mob id"));
        // daylight-burning mobs are the author's choice, not refused
        assertTrue(read(waves("[[{\"mob\":\"minecraft:zombie\",\"count\":1}]]")).ok());
    }

    @Test
    void wrongShapesAndNumbersAreRefusedInWords() {
        assertTrue(errors(read(waves("[]"))).contains("at least one"));
        assertTrue(errors(read(waves("[[]]"))).contains("wave 1 must be a list"));
        assertTrue(errors(read(waves("[[{\"mob\":\"husk\",\"count\":0}]]"))).contains("count must be a whole number"));
        assertTrue(errors(read(waves("[[{\"mob\":\"husk\",\"count\":2.5}]]"))).contains("count must be a whole number"));
        assertTrue(errors(read(waves("[[{\"mob\":\"husk\"}]]"))).contains("count"));
        assertTrue(errors(read(waves("[[{\"count\":2}]]"))).contains("each entry needs a"));
        assertTrue(errors(read(waves("[[{\"mob\":\"husk\",\"count\":2,\"speed\":3}]]"))).contains("unknown key 'speed'"));
        assertTrue(errors(read(waves("[[{\"mob\":\"husk\",\"count\":20},{\"mob\":\"witch\",\"count\":20},"
                + "{\"mob\":\"pillager\",\"count\":1}]]"))).contains("at most 40"));
        assertTrue(errors(read("{\"breatherSeconds\":10,\"waveSeconds\":180,\"waves\":[[{\"mob\":\"husk\",\"count\":1}]]}"))
                .contains("countdownSeconds must be a whole number"));
        assertTrue(errors(read("{\"countdownSeconds\":1,\"breatherSeconds\":10,\"waveSeconds\":180,"
                + "\"waves\":[[{\"mob\":\"husk\",\"count\":1}]]}")).contains("countdownSeconds must be from 3"));
        assertTrue(errors(read("{\"countdownSeconds\":5,\"breatherSeconds\":10,\"waveSeconds\":5,"
                + "\"waves\":[[{\"mob\":\"husk\",\"count\":1}]]}")).contains("waveSeconds must be from 30"));
        assertTrue(errors(read(waves("[[{\"mob\":\"husk\",\"count\":1}]]").replace("{\"count", "{\"x\":1,\"count")))
                .contains("unknown param 'x'"));
        // a note beside the params, as anywhere in a rules file, is not a param
        assertTrue(read("{\"_note\":\"easy\"," + waves("[[{\"mob\":\"husk\",\"count\":1}]]").substring(1)).ok());
        assertFalse(read(waves("[[{\"mob\":\"husk\",\"count\":21}]]")).ok());
    }

    @Test
    void aTableReadBackIsTheSameTable() {
        WorldRules.Parsed p = read(waves("[[{\"mob\":\"husk\",\"count\":2}],[{\"mob\":\"pillager\",\"count\":1},"
                + "{\"mob\":\"witch\",\"count\":3}]]"));
        assertTrue(p.ok(), errors(p));
        ArenaWaves w = ArenaWavesRule.wavesOf(p.rules());
        assertEquals(w, ArenaWaves.parse(w.toParams(), KNOWN::contains).waves());
        assertEquals(4, w.wave(2).size());
    }

    @Test
    void theRuleMayFixOrLimitThePanelAndSaysWhatIsWrong() {
        String one = "[[{\"mob\":\"husk\",\"count\":3}]]";
        ArenaWaves free = ArenaWavesRule.wavesOf(read(waves(one)).rules());
        assertEquals(ArenaSettings.Limits.NONE, free.limits(), "by default the player chooses");
        WorldRules.Parsed fixed = read(waves(one).replace("{\"countdownSeconds", "{\"waveSize\":3,\"difficulty\":\"Hard\","
                + "\"countdownSeconds"));
        assertTrue(fixed.ok(), errors(fixed));
        ArenaWaves f = ArenaWavesRule.wavesOf(fixed.rules());
        assertEquals(new ArenaSettings.Limits(3, 10, ArenaDifficulty.HARD, ArenaDifficulty.EXTREME), f.limits());
        assertEquals(f, ArenaWaves.parse(f.toParams(), KNOWN::contains).waves(), "read back the same");
        WorldRules.Parsed capped = read(waves(one).replace("{\"countdownSeconds", "{\"maxWaveSize\":6,"
                + "\"maxDifficulty\":\"normal\",\"countdownSeconds"));
        assertTrue(capped.ok(), errors(capped));
        ArenaWaves c = ArenaWavesRule.wavesOf(capped.rules());
        assertEquals(new ArenaSettings.Limits(null, 6, null, ArenaDifficulty.NORMAL), c.limits());
        assertEquals(c, ArenaWaves.parse(c.toParams(), KNOWN::contains).waves());
        assertTrue(errors(read(waves(one).replace("{\"countdownSeconds", "{\"waveSize\":11,\"countdownSeconds")))
                .contains("waveSize must be a whole number from 1 to 10"));
        assertTrue(errors(read(waves(one).replace("{\"countdownSeconds", "{\"waveSize\":2.5,\"countdownSeconds")))
                .contains("waveSize must be a whole number"));
        assertTrue(errors(read(waves(one).replace("{\"countdownSeconds", "{\"difficulty\":\"nightmare\","
                + "\"countdownSeconds"))).contains("difficulty must be one of easy, normal, hard, extreme"));
        assertTrue(errors(read(waves(one).replace("{\"countdownSeconds", "{\"waveSize\":3,\"maxWaveSize\":5,"
                + "\"countdownSeconds"))).contains("not both"));
        assertTrue(errors(read(waves(one).replace("{\"countdownSeconds", "{\"difficulty\":\"easy\","
                + "\"maxDifficulty\":\"hard\",\"countdownSeconds"))).contains("not both"));
    }

    @Test
    void theBreakAfterABossWaveIsChecked() {
        String rest = "\"waveSeconds\":180,\"waves\":[[{\"mob\":\"husk\",\"count\":1}]]";
        assertEquals(30, ArenaWavesRule.wavesOf(read("{\"countdownSeconds\":5,\"breatherSeconds\":10," + rest + "}")
                .rules()).bossBreatherSeconds(), "not given: 30 seconds");
        assertEquals(60, ArenaWavesRule.wavesOf(read("{\"countdownSeconds\":5,\"breatherSeconds\":60," + rest + "}")
                .rules()).bossBreatherSeconds(), "never shorter than the other break");
        ArenaWaves w = ArenaWavesRule.wavesOf(read("{\"countdownSeconds\":5,\"breatherSeconds\":10,"
                + "\"bossBreatherSeconds\":90," + rest + "}").rules());
        assertEquals(90, w.bossBreatherSeconds());
        assertEquals(w, ArenaWaves.parse(w.toParams(), KNOWN::contains).waves(), "written and read back the same");
        for (String bad : new String[] {"2", "301", "4.5", "\"long\""}) {
            WorldRules.Parsed p = read("{\"countdownSeconds\":5,\"breatherSeconds\":10,\"bossBreatherSeconds\":"
                    + bad + "," + rest + "}");
            assertFalse(p.ok(), bad);
            assertTrue(errors(p).contains("bossBreatherSeconds must be a whole number of seconds from 3 to 300"),
                    errors(p));
        }
    }
}
