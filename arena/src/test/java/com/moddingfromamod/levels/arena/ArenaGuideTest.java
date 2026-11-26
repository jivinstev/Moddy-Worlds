package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.moddingfromamod.world.GuideBook;
import com.moddingfromamod.world.WorldGuide;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Arena Guide book: it fits the core's book pages, every number on a page comes from the rules in force (a
 * placeholder the arena's charge fills), and a page is left out only when a rule turns its thing off.
 */
class ArenaGuideTest {

    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(null),
            new ArenaWishesRule(), new ArenaCreaturesRule(), new ArenaLadderRule(), new ArenaWishedWavesRule());
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    private static String read(String path) throws IOException {
        try (InputStream in = ArenaGuideTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static WorldGuide.Book book() throws IOException {
        var o = JsonParser.parseString(read("/worlds/arena.json")).getAsJsonObject();
        WorldGuide g = WorldGuide.parse(o.get("guide"));
        assertTrue(g.hasBook(), "arena.json carries a guide book");
        return g.book();
    }

    private static WorldRules bundledRules() throws IOException {
        WorldRules.Parsed p = WorldRules.parse(read("/worlds/rules/arena.rules.json"), "arena", TYPES);
        assertTrue(p.ok(), String.valueOf(p.errors()));
        return p.rules();
    }

    private static Map<String, String> bundledValues() throws IOException {
        WorldRules r = bundledRules();
        return ArenaGuide.values(ArenaWavesRule.wavesOf(r), ArenaWishesRule.wishesOf(r), ArenaLadderRule.ladderOf(r));
    }

    @Test
    void itIsTheArenaGuideByModdy() throws IOException {
        WorldGuide.Book b = book();
        assertEquals("Arena Guide", b.title());
        assertEquals("Moddy", b.author());
        assertEquals(ArenaGuide.TITLE, b.title(), "the arena knows its own book by this title");
        assertEquals(ArenaGuide.AUTHOR, b.author());
    }

    /** The book the core gives on his first arrival lands on the hotbar; it goes into the first empty bag slot. */
    @Test
    void theGuideGoesFromTheHotbarIntoTheBag() {
        boolean[] empty = new boolean[36];
        java.util.Arrays.fill(empty, true);
        empty[0] = false;   // the watch
        empty[1] = false;   // the wish chest
        empty[2] = false;   // the book itself
        assertEquals(9, ArenaGuide.bagSlot(2, empty));
        empty[9] = false;
        empty[10] = false;
        assertEquals(11, ArenaGuide.bagSlot(2, empty));
        assertEquals(-1, ArenaGuide.bagSlot(12, empty), "already in the bag: left where it is");
        assertEquals(-1, ArenaGuide.bagSlot(-1, empty), "no book");
        boolean[] full = new boolean[36];
        java.util.Arrays.fill(full, 0, 9, true);
        assertEquals(-1, ArenaGuide.bagSlot(2, full), "a full bag: it stays on the hotbar");
    }

    /** The core's own check: the title fits, at most 16 pages, and every page shows whole on a book page. */
    @Test
    void everyPageFitsABookPage() throws IOException {
        WorldGuide.Book b = book();
        assertEquals(List.of(), GuideBook.problems(b), "as written");
        List<String> filled = GuideBook.pages(b, bundledValues());
        WorldGuide.Book given = new WorldGuide.Book(b.title(), b.author(), filled);
        assertEquals(List.of(), GuideBook.problems(given), "as given, with the rules' numbers in");
        // the widest numbers a rule may set still fit
        Map<String, String> widest = Map.of("arena_lives", "10", "arena_boss_every", "50", "arena_break_seconds", "120",
                "arena_boss_break_seconds", "300", "arena_magician_wishes", "20", "arena_rest_seconds", "120",
                "arena_wishes_per_wave", "50", "arena_ladder_health", "500000", "arena_ladder_hits", "500000",
                "arena_ladder_events_open", ArenaWords.opensAt(ArenaLadder.Count.BOSSES, ArenaLadder.MAX_AFTER));
        assertEquals(List.of(), GuideBook.problems(new WorldGuide.Book(b.title(), b.author(),
                GuideBook.pages(b, widest))), "with the largest numbers the rules allow");
    }

    /** Every placeholder on a page is one the arena's charge fills, and with the bundled rules no page is left out. */
    @Test
    void everyPlaceholderHasAValue() throws IOException {
        WorldGuide.Book b = book();
        Map<String, String> values = bundledValues();
        Set<String> named = new HashSet<>();
        for (String page : b.pages()) {
            Matcher m = PLACEHOLDER.matcher(page);
            while (m.find()) {
                named.add(m.group(1));
                assertTrue(ArenaGuide.KEYS.contains(m.group(1)), "no value is ever given for {" + m.group(1) + "}");
                assertTrue(values.containsKey(m.group(1)), "the bundled rules give no {" + m.group(1) + "}");
            }
        }
        assertEquals(Set.copyOf(ArenaGuide.KEYS), named, "every value the charge gives is read on some page");
        List<String> given = GuideBook.pages(b, values);
        assertEquals(b.pages().size(), given.size(), "with the bundled rules every page is in the book");
        for (String page : given) {
            assertFalse(GuideBook.unfilled(page), page);
        }
    }

    /** No page types a number of its own: a rules edit could make it wrong. Every number is a placeholder. */
    @Test
    void noPageTypesANumber() throws IOException {
        for (String page : book().pages()) {
            assertFalse(page.chars().anyMatch(Character::isDigit), "a number typed into a page: " + page);
        }
    }

    @Test
    void theNumbersAreTheRules() throws IOException {
        Map<String, String> v = bundledValues();
        WorldRules r = bundledRules();
        ArenaWaves waves = ArenaWavesRule.wavesOf(r);
        ArenaWishes wishes = ArenaWishesRule.wishesOf(r);
        assertEquals(String.valueOf(waves.lives()), v.get("arena_lives"));
        assertEquals(String.valueOf(waves.bossEvery()), v.get("arena_boss_every"));
        assertEquals(String.valueOf(waves.breatherSeconds()), v.get("arena_break_seconds"));
        assertEquals(String.valueOf(waves.bossBreatherSeconds()), v.get("arena_boss_break_seconds"));
        assertEquals(String.valueOf(wishes.magicianWishes()), v.get("arena_magician_wishes"));
        assertEquals(String.valueOf(wishes.cooldownSeconds()), v.get("arena_rest_seconds"));
        assertEquals(String.valueOf(wishes.wishesPerWave()), v.get("arena_wishes_per_wave"));
        ArenaLadder ladder = ArenaLadderRule.ladderOf(r);
        assertEquals(ArenaGuide.heartsNumber(ladder.limits(0).get("healthHalfHearts")), v.get("arena_ladder_health"));
        assertEquals(ArenaGuide.heartsNumber(ladder.limits(0).get("attackDamageHalfHearts")), v.get("arena_ladder_hits"));
        assertEquals("wave " + ladder.opensAfter("event", false), v.get("arena_ladder_events_open"));
    }

    /** A ladder that limits nothing at first, or locks no events, leaves those pages out. */
    @Test
    void aLadderWithoutFirstLimitsOrLockedEventsLeavesItsPagesOut() throws IOException {
        WorldRules r = bundledRules();
        ArenaLadder open = new ArenaLadder(ArenaLadder.Count.WAVES,
                List.of(new ArenaLadder.Step(0, Map.of(), null)), null);
        Map<String, String> v = ArenaGuide.values(ArenaWavesRule.wavesOf(r), ArenaWishesRule.wishesOf(r), open);
        assertFalse(v.containsKey("arena_ladder_health"));
        assertFalse(v.containsKey("arena_ladder_events_open"));
        assertEquals(book().pages().size() - 2, GuideBook.pages(book(), v).size());
    }

    /** A rule that turns a thing off leaves its page out, never a page that says "every 0 waves". */
    @Test
    void aRuleThatTurnsAThingOffLeavesItsPageOut() throws IOException {
        WorldRules r = bundledRules();
        ArenaWaves noBoss = ArenaWavesRule.wavesOf(r).withBossEvery(0);
        Map<String, String> v = ArenaGuide.values(noBoss, new ArenaWishes(0, 10, 5));
        assertFalse(v.containsKey("arena_boss_every"));
        assertFalse(v.containsKey("arena_magician_wishes"));
        List<String> given = GuideBook.pages(book(), v);
        assertEquals(book().pages().size() - 2, given.size());
        for (String page : given) {
            assertFalse(page.contains("BOSS WAVE") || page.contains("Magician wishes"), page);
        }
        Map<String, String> none = ArenaGuide.values(null, null);
        assertEquals(String.valueOf(ArenaWaves.DEFAULT_LIVES), none.get("arena_lives"));
        assertEquals(String.valueOf(ArenaWishes.DEFAULT.wishesPerWave()), none.get("arena_wishes_per_wave"));
    }

    /** Plain words a child reads: plain characters, no wizard, and it names what the arena has. */
    @Test
    void plainWordsAboutWhatIsBuilt() throws IOException {
        String all = String.join("\n", book().pages());
        assertTrue(all.chars().allMatch(c -> c == '\n' || (c >= 32 && c < 127)), "plain characters only");
        assertFalse(all.toLowerCase(Locale.ROOT).contains("wizard"));
        assertFalse(all.contains("MONSTERS"), "no MONSTERS selector any more: the difficulty sets how many");
        for (String word : List.of("prep room", "gate", "bars", "how many monsters", "DIFFICULTY", "WAVES", "START BATTLE", "READY",
                "BOSS WAVE", "lives", "break", "mends", "Builder", "Apprentice", "Magician", "My wishes",
                "Add to hotbar", "rests", "each wave", "Fighter", "Healer", "Tank", "Champions", "WISH LADDER",
                "hearts", "events open", "gold spot", "real item")) {
            assertTrue(all.contains(word), "the guide says nothing about " + word);
        }
    }
}
