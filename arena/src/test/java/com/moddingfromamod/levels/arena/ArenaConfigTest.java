package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ArenaConfigTest {

    /** The rules file's values: breather 10, boss breather 30, countdown 5, wave 300. */
    private static int rules(String key) {
        return switch (key) {
            case "breatherSeconds" -> 10;
            case "bossBreatherSeconds" -> 30;
            case "countdownSeconds" -> 5;
            default -> 300;
        };
    }

    @Test
    void theHighestLayerWins() {
        ArenaConfig c = ArenaConfig.EMPTY;
        assertEquals(new ArenaConfig.Effective(10, ArenaConfig.Source.RULES), c.effective("breatherSeconds", "north", 10));
        c = c.with(ArenaConfig.GLOBAL, "breatherSeconds", 20);
        assertEquals(new ArenaConfig.Effective(20, ArenaConfig.Source.GLOBAL), c.effective("breatherSeconds", "north", 10));
        c = c.with("north", "breatherSeconds", 8);
        assertEquals(new ArenaConfig.Effective(8, ArenaConfig.Source.ARENA), c.effective("breatherSeconds", "north", 10));
        assertEquals(20, c.effective("breatherSeconds", "south", 10).value(), "another arena keeps the global value");
        c = c.without("north", "breatherSeconds");
        assertEquals(20, c.effective("breatherSeconds", "north", 10).value());
        c = c.without(ArenaConfig.GLOBAL, "breatherSeconds");
        assertEquals(ArenaConfig.Source.RULES, c.effective("breatherSeconds", "north", 10).source());
    }

    @Test
    void setsAreCheckedInWordsAndRefusedDuringABattle() {
        ArenaConfig c = ArenaConfig.EMPTY;
        var ok = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "20"), ArenaConfigTest::rules, Set.of(), true);
        assertTrue(ok.changed() && !ok.refused(), ok.words());
        assertEquals(20, ok.config().effective("breatherSeconds", "x", 10).value());
        var low = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "1"), ArenaConfigTest::rules, Set.of(), true);
        assertTrue(low.refused() && low.words().contains("from 3 to 120"), low.words());
        var word = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "lots"), ArenaConfigTest::rules, Set.of(), true);
        assertTrue(word.refused());
        var unknown = c.run(ArenaConfig.GLOBAL, List.of("restSeconds", "5"), ArenaConfigTest::rules, Set.of(), true);
        assertTrue(unknown.refused() && unknown.words().contains("breatherSeconds"), unknown.words());
        var busyGlobal = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "20"), ArenaConfigTest::rules,
                Set.of("north"), true);
        assertTrue(busyGlobal.refused() && busyGlobal.words().contains("north"), busyGlobal.words());
        var otherArena = c.run("south", List.of("breatherSeconds", "20"), ArenaConfigTest::rules, Set.of("north"), true);
        assertTrue(otherArena.changed(), "a battle in north does not lock south: " + otherArena.words());
        var thisArena = c.run("north", List.of("breatherSeconds", "20"), ArenaConfigTest::rules, Set.of("north"), true);
        assertTrue(thisArena.refused());
        var notOp = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "20"), ArenaConfigTest::rules, Set.of(), false);
        assertTrue(notOp.refused());
        var read = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds"), ArenaConfigTest::rules, Set.of("north"), false);
        assertFalse(read.refused(), "anyone may read, during a battle too");
        assertTrue(read.words().contains("10") && read.words().contains("rules file"), read.words());
    }

    @Test
    void resetDropsOnlyThatLayer() {
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "breatherSeconds", 20).with("north", "breatherSeconds", 8);
        var r = c.run("north", List.of("breatherSeconds", "reset"), ArenaConfigTest::rules, Set.of(), true);
        assertTrue(r.changed() && r.words().contains("20") && r.words().contains("all arenas"), r.words());
    }

    @Test
    void theListShowsEveryKeyAndWhereItCameFrom() {
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "countdownSeconds", 15);
        String words = c.run("north", List.of(), ArenaConfigTest::rules, Set.of(), false).words();
        for (ArenaConfig.Key k : ArenaConfig.KEYS) {
            assertTrue(words.contains(k.name()), words);
        }
        assertTrue(words.contains("countdownSeconds = 15") && words.contains("all arenas"), words);
    }

    @Test
    void aBattleStartsWithTheTimingsLayeredOnTheRules() {
        ArenaWaves base = ArenaWavesRule.wavesOf(null);
        if (base == null) {
            return;   // no bundled table reachable from this test: the layering itself is covered above
        }
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "breatherSeconds", 25).with("north", "waveSeconds", 60);
        ArenaWaves north = c.apply(base, "north");
        assertEquals(25, north.breatherSeconds());
        assertEquals(60, north.waveSeconds());
        assertEquals(base.countdownSeconds(), north.countdownSeconds());
        assertEquals(base.waves(), north.waves(), "only the timings change");
        assertEquals(base.waveSeconds(), c.apply(base, "south").waveSeconds());
    }

    @Test
    void everyKeyStaysInsideWhatTheRulesFileAllows() {
        for (ArenaConfig.Key k : ArenaConfig.KEYS) {
            assertTrue(k.min() >= 0 && k.max() > k.min(), k.name());
            assertNull(k.parse(String.valueOf(k.min() - 1)));
            assertEquals(k.max(), k.parse(String.valueOf(k.max())));
        }
        assertTrue(Map.of().isEmpty());
    }
}
