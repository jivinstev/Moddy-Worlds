package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaChampions.Entry;
import com.moddingfromamod.levels.arena.ArenaChampions.Key;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The champions: per settings, ranked, one best each, and how they read. */
class ArenaChampionsTest {

    private static final Key NORMAL5 = new Key("normal", 5);
    private static final Key EASY5 = new Key("easy", 5);
    private static final Key ENDLESS = new Key("normal", 0);
    private static long seq;

    private static Entry e(String id, Key k, int wave, boolean cleared, boolean won, long seconds) {
        return new Entry(id, id.toUpperCase(), k, wave, cleared, won, seconds * 20, ++seq);
    }

    @Test
    void aWinRanksFirstFastestFirstThenTheFurthestWaveThenTheFastest() {
        List<Entry> all = new ArrayList<>();
        all = ArenaChampions.merge(all, e("a", NORMAL5, 3, false, false, 60));
        all = ArenaChampions.merge(all, e("b", NORMAL5, 5, true, true, 300));
        all = ArenaChampions.merge(all, e("c", NORMAL5, 5, true, true, 200));
        all = ArenaChampions.merge(all, e("d", NORMAL5, 5, false, false, 100));
        all = ArenaChampions.merge(all, e("f", NORMAL5, 3, true, false, 90));
        all = ArenaChampions.merge(all, e("g", NORMAL5, 3, false, false, 40));
        assertEquals(List.of("c", "b", "d", "f", "g"),
                ArenaChampions.top(all, NORMAL5).stream().map(Entry::id).toList(), "only five shown");
        assertEquals(6, ArenaChampions.at(all, NORMAL5).size());
    }

    @Test
    void endlessIsTheFurthestWaveThenTheFastest() {
        List<Entry> all = new ArrayList<>();
        all = ArenaChampions.merge(all, e("a", ENDLESS, 12, false, false, 600));
        all = ArenaChampions.merge(all, e("b", ENDLESS, 14, false, false, 900));
        all = ArenaChampions.merge(all, e("c", ENDLESS, 12, false, false, 500));
        assertEquals(List.of("b", "c", "a"), ArenaChampions.top(all, ENDLESS).stream().map(Entry::id).toList());
    }

    @Test
    void eachSettingsIsARaceOfItsOwnAndABetterRunReplacesAWorseOneDoesNot() {
        List<Entry> all = new ArrayList<>();
        all = ArenaChampions.merge(all, e("a", NORMAL5, 4, false, false, 100));
        all = ArenaChampions.merge(all, e("a", EASY5, 5, true, true, 120));
        assertEquals(2, all.size(), "one entry per player per settings");
        assertEquals(1, ArenaChampions.top(all, EASY5).size());
        all = ArenaChampions.merge(all, e("a", NORMAL5, 2, false, false, 30));
        assertEquals(4, ArenaChampions.bestOf(all, "a", NORMAL5).wave(), "a worse run does not replace it");
        all = ArenaChampions.merge(all, e("a", NORMAL5, 5, true, true, 400));
        assertTrue(ArenaChampions.bestOf(all, "a", NORMAL5).won(), "a better run does");
        assertEquals(2, all.size());
        Entry same = e("a", NORMAL5, 5, true, true, 400);
        long before = ArenaChampions.bestOf(all, "a", NORMAL5).seq();
        all = ArenaChampions.merge(all, same);
        assertEquals(before, ArenaChampions.bestOf(all, "a", NORMAL5).seq(), "a tie keeps the first");
        all = ArenaChampions.merge(all, new Entry("a", "Renamed", NORMAL5, 1, false, false, 0, ++seq));
        assertEquals("Renamed", ArenaChampions.bestOf(all, "a", EASY5).name(), "his name is kept up to date");
        assertNull(ArenaChampions.bestOf(all, "zz", NORMAL5));
        assertEquals(all, ArenaChampions.merge(all, e("b", NORMAL5, 0, false, false, 0)), "no wave reached: no run");
    }

    @Test
    void eachSettingsKeepsItsBest() {
        List<Entry> all = new ArrayList<>();
        for (int i = 0; i < ArenaChampions.KEPT_PER_KEY + 5; i++) {
            all = ArenaChampions.merge(all, e("p" + i, NORMAL5, 1 + i % 5, false, false, 10));
        }
        assertEquals(ArenaChampions.KEPT_PER_KEY, ArenaChampions.at(all, NORMAL5).size());
    }

    @Test
    void theWords() {
        assertEquals("0:42", ArenaChampions.time(42 * 20));
        assertEquals("12:05", ArenaChampions.time((12 * 60 + 5) * 20));
        assertEquals("Won all 5", ArenaChampions.result(e("a", NORMAL5, 5, true, true, 1)));
        assertEquals("Wave 3", ArenaChampions.result(e("a", NORMAL5, 3, false, false, 1)));
        assertEquals("Normal, 5 waves", ArenaChampions.settingsWords(NORMAL5));
        assertEquals("Hard, Endless", ArenaChampions.settingsWords(new Key("hard", 0)));
        assertEquals("Best for Steve: won in 3:20",
                ArenaChampions.bestLine("Steve", e("s", NORMAL5, 5, true, true, 200)));
        assertEquals("Best for Steve: wave 3 in 1:00",
                ArenaChampions.bestLine("Steve", e("s", NORMAL5, 3, false, false, 60)));
        assertEquals("Steve: no best at these settings yet", ArenaChampions.bestLine("Steve", null));
        assertEquals(Key.of(new ArenaSettings(ArenaDifficulty.HARD, 10)), new Key("hard", 10));
    }

    /** Records from before 0.1.6, kept by difficulty, waves and monsters size: each player keeps his best per new key. */
    @Test
    void oldRecordsMigrateToEachPlayersBestAtDifficultyAndWaves() {
        // as they read from an old save, the size already dropped: Steve played Normal 5 at sizes 3, 5 and 8
        Entry slow = new Entry("s", "Steve", NORMAL5, 5, true, true, 400 * 20, 1);
        Entry fast = new Entry("s", "Steve", NORMAL5, 5, true, true, 250 * 20, 2);
        Entry notWon = new Entry("s", "Steve2", NORMAL5, 4, false, false, 100 * 20, 3);
        Entry alex = new Entry("a", "Alex", NORMAL5, 3, false, false, 60 * 20, 4);
        Entry easy = new Entry("s", "Steve", EASY5, 2, true, false, 30 * 20, 5);
        List<Entry> out = ArenaChampions.migrate(List.of(slow, fast, notWon, alex, easy));
        assertEquals(3, out.size(), "one entry per player per difficulty and waves");
        Entry steve = ArenaChampions.bestOf(out, "s", NORMAL5);
        assertEquals(250 * 20, steve.ticks(), "his fastest win is kept");
        assertEquals(2, steve.seq());
        assertEquals("Steve", ArenaChampions.bestOf(out, "s", EASY5).name(), "under the name of his latest run");
        assertEquals("Steve", steve.name());
        assertEquals(alex, ArenaChampions.bestOf(out, "a", NORMAL5));
        assertEquals(out, ArenaChampions.migrate(out), "migrating again changes nothing");
        // a tie keeps the first run
        Entry first = new Entry("t", "T", NORMAL5, 3, false, false, 20, 7);
        Entry second = new Entry("t", "T", NORMAL5, 3, false, false, 20, 9);
        assertEquals(7, ArenaChampions.migrate(List.of(second, first)).get(0).seq());
        // each key keeps its best few
        List<Entry> many = new ArrayList<>();
        for (int i = 0; i < ArenaChampions.KEPT_PER_KEY + 5; i++) {
            many.add(new Entry("p" + i, "P" + i, NORMAL5, 3, false, false, 100 + i, i));
        }
        assertEquals(ArenaChampions.KEPT_PER_KEY, ArenaChampions.migrate(many).size());
    }
}
