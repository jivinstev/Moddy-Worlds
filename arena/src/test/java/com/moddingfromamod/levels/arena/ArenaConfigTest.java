package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The settings command's keys, layers, guard and words (Moddy-Worlds#59). */
class ArenaConfigTest {

    /** A rules table: countdown 5, breather 10, wave 300, boss breather 30. */
    private static final ArenaWaves RULES = new ArenaWaves(5, 10, 300,
            List.of(new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:zombie", 2)))),
            ArenaSettings.Limits.NONE, ArenaWaves.DEFAULT_BOSS_EVERY, ArenaGenerator.DEFAULT, ArenaWaves.DEFAULT_LIVES, 30);

    @Test
    void theHighestLayerWins() {
        ArenaConfig c = ArenaConfig.EMPTY;
        assertEquals(new ArenaConfig.Effective(10, ArenaConfig.Source.RULES), c.effective("breatherSeconds", "north", 10));
        c = c.with(ArenaConfig.GLOBAL, "breatherSeconds", 20);
        assertEquals(new ArenaConfig.Effective(20, ArenaConfig.Source.GLOBAL), c.effective("breatherSeconds", "north", 10));
        c = c.with("north", "breatherSeconds", 8);
        assertEquals(new ArenaConfig.Effective(8, ArenaConfig.Source.ARENA), c.effective("breatherSeconds", "north", 10));
        assertEquals(20, c.effective("breatherSeconds", "south", 10).value(), "another arena keeps the global value");
        assertEquals(20, c.effective("breatherSeconds", null, 10).value(), "all arenas sees the global value");
        c = c.without("north", "breatherSeconds");
        assertEquals(20, c.effective("breatherSeconds", "north", 10).value());
        c = c.without(ArenaConfig.GLOBAL, "breatherSeconds");
        assertEquals(ArenaConfig.Source.RULES, c.effective("breatherSeconds", "north", 10).source());
        assertEquals(ArenaConfig.EMPTY, c, "dropping every value leaves nothing stored");
    }

    @Test
    void setsAreCheckedInWordsAndRefusedDuringABattle() {
        ArenaConfig c = ArenaConfig.EMPTY;
        var ok = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "20"), RULES, Set.of(), true);
        assertTrue(ok.changed() && !ok.refused(), ok.words());
        assertEquals(20, ok.config().effective("breatherSeconds", "x", 10).value());
        var low = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "1"), RULES, Set.of(), true);
        assertTrue(low.refused() && low.words().contains("from 3 to 120"), low.words());
        var word = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "lots"), RULES, Set.of(), true);
        assertTrue(word.refused());
        var unknown = c.run(ArenaConfig.GLOBAL, List.of("restSeconds", "5"), RULES, Set.of(), true);
        assertTrue(unknown.refused() && unknown.words().contains("breatherSeconds"), unknown.words());
        var busyGlobal = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "20"), RULES, Set.of("north"), true);
        assertTrue(busyGlobal.refused() && busyGlobal.words().contains("north"), busyGlobal.words());
        var otherArena = c.run("south", List.of("breatherSeconds", "20"), RULES, Set.of("north"), true);
        assertTrue(otherArena.changed(), "a battle in north does not lock south: " + otherArena.words());
        var thisArena = c.run("north", List.of("breatherSeconds", "20"), RULES, Set.of("north"), true);
        assertTrue(thisArena.refused());
        var resetInBattle = c.run("north", List.of("breatherSeconds", "reset"), RULES, Set.of("north"), true);
        assertTrue(resetInBattle.refused(), "a reset is a change too");
        var notOp = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds", "20"), RULES, Set.of(), false);
        assertTrue(notOp.refused() && notOp.words().contains("operator"), notOp.words());
        var read = c.run(ArenaConfig.GLOBAL, List.of("breatherSeconds"), RULES, Set.of("north"), false);
        assertFalse(read.refused(), "anyone may read, during a battle too");
        assertTrue(read.words().contains("10 seconds") && read.words().contains("rules file"), read.words());
    }

    @Test
    void keysAreMatchedInAnyCase() {
        var ok = ArenaConfig.EMPTY.run(ArenaConfig.GLOBAL, List.of("BREATHERSECONDS", "20"), RULES, Set.of(), true);
        assertEquals(20, ok.config().global().get("breatherSeconds"), "stored under the key's own name");
    }

    @Test
    void resetDropsOnlyThatLayer() {
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "breatherSeconds", 20).with("north", "breatherSeconds", 8);
        var r = c.run("north", List.of("breatherSeconds", "reset"), RULES, Set.of(), true);
        assertTrue(r.changed() && r.words().contains("20") && r.words().contains("all arenas"), r.words());
        assertEquals(20, r.config().global().get("breatherSeconds"));
    }

    @Test
    void theListShowsEveryKeyAndWhereItCameFrom() {
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "countdownSeconds", 15);
        String words = c.run("north", List.of(), RULES, Set.of(), false).words();
        for (ArenaConfig.Key k : ArenaConfig.KEYS) {
            assertTrue(words.contains(k.name()), words);
        }
        assertTrue(words.contains("countdownSeconds = 15 seconds") && words.contains("all arenas"), words);
        assertTrue(words.contains("breatherSeconds = 10 seconds  (from the rules file"), words);
        assertTrue(words.contains("respawnInPrep = on"), words);
    }

    @Test
    void aBattleStartsWithTheTimingsLayeredOnTheRules() {
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "breatherSeconds", 25).with("north", "waveSeconds", 60)
                .with("north", "bossBreatherSeconds", 90).with("north", "countdownSeconds", 4);
        ArenaWaves north = c.apply(RULES, "north");
        assertEquals(25, north.breatherSeconds());
        assertEquals(60, north.waveSeconds());
        assertEquals(90, north.bossBreatherSeconds());
        assertEquals(4, north.countdownSeconds());
        assertEquals(RULES.waves(), north.waves(), "only the timings change");
        assertEquals(RULES.lives(), north.lives());
        assertEquals(RULES.bossEvery(), north.bossEvery());
        assertEquals(RULES.limits(), north.limits());
        ArenaWaves south = c.apply(RULES, "south");
        assertEquals(RULES.waveSeconds(), south.waveSeconds());
        assertEquals(25, south.breatherSeconds());
        assertEquals(RULES, ArenaConfig.EMPTY.apply(RULES, "north"), "nothing set: the rules exactly");
        assertNull(c.apply(null, "north"));
    }

    @Test
    void aStoredValueOutsideItsRangeIsClampedWhenRead() {
        // an old world's value, from before a version narrowed the range (or a hand-edited save)
        ArenaConfig c = new ArenaConfig(Map.of("breatherSeconds", 999, "noLongerAKey", 5), Map.of("north",
                Map.of("countdownSeconds", 0)));
        assertEquals(ArenaWaves.MAX_BREATHER, c.apply(RULES, "north").breatherSeconds());
        assertEquals(ArenaWaves.MIN_COUNTDOWN, c.apply(RULES, "north").countdownSeconds());
        assertTrue(c.run("north", List.of(), RULES, Set.of(), false).words().contains("breatherSeconds"),
                "an unknown stored key does not break the list");
    }

    @Test
    void anOnOffKeyTakesWords() {
        ArenaConfig.Key k = ArenaConfig.key(ArenaRespawn.KEY);
        assertNotNull(k);
        assertEquals(1, ArenaConfig.EMPTY.value(k, "north", RULES), "on by default");
        assertEquals(1, ArenaConfig.EMPTY.value(k, "north", null), "on with no rules table too");
        var off = ArenaConfig.EMPTY.run("north", List.of(ArenaRespawn.KEY, "off"), RULES, Set.of(), true);
        assertTrue(off.changed(), off.words());
        assertEquals(0, off.config().value(k, "north", RULES));
        assertEquals(1, off.config().value(k, "south", RULES));
        assertTrue(off.words().contains("is now off"), off.words());
        var bad = ArenaConfig.EMPTY.run("north", List.of(ArenaRespawn.KEY, "maybe"), RULES, Set.of(), true);
        assertTrue(bad.refused() && bad.words().contains("on or off"), bad.words());
        assertEquals(RULES, off.config().apply(RULES, "north"), "a switch the battle reads itself leaves the table alone");
    }

    @Test
    void everyKeyStaysInsideWhatTheRulesFileAllowsAndRoundTripsThroughTheTable() {
        for (ArenaConfig.Key k : ArenaConfig.KEYS) {
            assertTrue(k.min() >= 0 && k.max() > k.min(), k.name());
            if (k.kind() == ArenaConfig.Kind.NUMBER) {
                assertNull(k.parse(String.valueOf(k.min() - 1)), k.name());
                assertNull(k.parse(String.valueOf(k.max() + 1)), k.name());
                assertEquals(k.max(), k.parse(String.valueOf(k.max())), k.name());
            }
            if (k.into() != null) {
                int v = k.min() == k.rules().applyAsInt(RULES) ? k.max() : k.min();
                ArenaWaves set = k.into().set(RULES, v);
                assertEquals(v, k.rules().applyAsInt(set), k.name() + " is read back from where it is put");
                for (ArenaConfig.Key other : ArenaConfig.KEYS) {
                    if (other != k && other.into() != null) {
                        assertEquals(other.rules().applyAsInt(RULES), other.rules().applyAsInt(set),
                                k.name() + " changes " + other.name());
                    }
                }
            }
        }
        assertEquals(ArenaConfig.KEYS.size(), ArenaConfig.KEYS.stream().map(ArenaConfig.Key::name).distinct().count());
    }

    @Test
    void theGuardNamesTheBusyArenas() {
        assertNull(ArenaConfig.guard(ArenaConfig.GLOBAL, Set.of()));
        assertTrue(ArenaConfig.guard(ArenaConfig.GLOBAL, Set.of("south", "north")).contains("north, south"));
        assertNull(ArenaConfig.guard("east", Set.of("north")));
        assertTrue(ArenaConfig.guard("north", Set.of("north")).contains("north"));
    }

    @Test
    void forgettingAnArenaKeepsTheRest() {
        ArenaConfig c = ArenaConfig.EMPTY.with(ArenaConfig.GLOBAL, "breatherSeconds", 20).with("north", "waveSeconds", 60)
                .with("south", "waveSeconds", 90).forget("north");
        assertEquals(Set.of("south"), c.perArena().keySet());
        assertEquals(20, c.global().get("breatherSeconds"));
    }
}
