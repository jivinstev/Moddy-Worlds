package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.wish.WishAsk;
import com.moddingfromamod.wish.WishSource;
import com.moddingfromamod.wishbuilder.MakeFacts;
import com.moddingfromamod.world.MakeView;
import com.moddingfromamod.world.WorldLimits;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The unlock ladder: limits that rise as a battle goes on, in the core's fact vocabulary, the same for every fighter and
 * back to the first step for every battle; what stays locked until a step opens it (by kind and tag, never a Builder
 * id); and what a player reads about it.
 */
class ArenaLadderTest {

    private static final UUID HIM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final List<WorldRuleType> TYPES = List.of(new ArenaRule(), new ArenaWavesRule(null),
            new ArenaWishesRule(), new ArenaCreaturesRule(), new ArenaLadderRule());

    private static WorldRules bundled() throws IOException {
        try (InputStream in = ArenaLadderTest.class.getResourceAsStream("/worlds/rules/arena.rules.json")) {
            WorldRules.Parsed p = WorldRules.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "arena",
                    TYPES);
            assertTrue(p.ok(), String.valueOf(p.errors()));
            return p.rules();
        }
    }

    private static MakeFacts facts(String kind, Set<String> tags, Map<String, Double> numbers) {
        return new MakeFacts(kind, numbers, tags, true);
    }

    /** A rules file's params as the core hands them on: plain maps and lists. */
    private static Map<String, Object> params() {
        return ArenaLadder.DEFAULT.toParams();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> p) {
        return (List<Map<String, Object>>) p.get("steps");
    }

    // ── the data ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theBundledRulesCarryTheDefaultLadder() throws IOException {
        assertEquals(ArenaLadder.DEFAULT, ArenaLadderRule.ladderOf(bundled()));
        assertEquals(ArenaLadder.DEFAULT, ArenaLadderRule.ladderOf(null), "no rules: the default");
        assertEquals(ArenaLadder.DEFAULT, ArenaLadder.parse(params()).ladder(), "its params read back as itself");
    }

    @Test
    void theDefaultIsModestFirstRisesInAFiveWaveBattleAndAllowsEverythingAtTheTop() {
        ArenaLadder l = ArenaLadder.DEFAULT;
        assertEquals(ArenaLadder.Count.WAVES, l.progress(), "counted in waves cleared");
        Map<String, Double> first = l.limits(0);
        assertEquals(40.0, first.get(MakeFacts.HEALTH_HALF_HEARTS), "20 hearts, a zombie's twice");
        assertEquals(5.0, first.get(MakeFacts.ATTACK_DAMAGE_HALF_HEARTS), "a stone sword's hit");
        assertEquals(2.0, first.get(MakeFacts.COUNT_MADE));
        // the default five-wave battle (its only boss the last wave) meets that boss with stronger wishes: two steps
        assertTrue(l.stepIndex(4) >= 2, "two steps up before wave 5");
        assertTrue(l.limits(4).get(MakeFacts.HEALTH_HALF_HEARTS) > first.get(MakeFacts.HEALTH_HALF_HEARTS));
        // a bigger step after each boss wave (5, 10, 15, 20)
        for (int boss : List.of(5, 10, 15)) {
            double before = l.limits(boss - 1).get(MakeFacts.HEALTH_HALF_HEARTS);
            double after = l.limits(boss).get(MakeFacts.HEALTH_HALF_HEARTS);
            assertTrue(after >= 1.4 * before, "a boss wave cleared is a big step: " + boss);
        }
        assertEquals(Map.of(), l.limits(20), "nothing limited from wave 20");
        assertEquals(Map.of(), l.limits(500), "and beyond it");
        assertNull(l.next(20));
        for (int b = 1; b <= 20; b++) {
            Map<String, Double> was = l.limits(b - 1);
            Map<String, Double> now = l.limits(b);
            for (String k : now.keySet()) {
                assertTrue(now.get(k) >= was.get(k), k + " falls at " + b);
            }
        }
        assertEquals(5, l.opensAfter("event", false), "events after the first boss wave");
        assertEquals(10, l.opensAfter(MakeFacts.WORLD_RULE, true), "world rules after the second");
        assertEquals(Map.of(), l.stillLocked(20));
    }

    /** The default ladder keeps pace with the wave generator: wishes grow at least as fast as the monsters' health. */
    @Test
    void theDefaultKeepsPaceWithTheMonsters() {
        ArenaLadder l = ArenaLadder.DEFAULT;
        double h0 = l.limits(0).get(MakeFacts.HEALTH_HALF_HEARTS);
        double d0 = l.limits(0).get(MakeFacts.ATTACK_DAMAGE_HALF_HEARTS);
        for (int wave = 6; wave <= 19; wave++) {
            double monsters = Math.min(2.5, 1 + 0.04 * (wave - 5));   // the generator's health per wave (default knobs)
            assertTrue(l.limits(wave - 1).get(MakeFacts.HEALTH_HALF_HEARTS) / h0 >= monsters, "health at " + wave);
            assertTrue(l.limits(wave - 1).get(MakeFacts.ATTACK_DAMAGE_HALF_HEARTS) / d0 >= monsters, "hits at " + wave);
        }
    }

    @Test
    void aFactTheLadderDoesNotNameIsUnlimited() {
        Map<String, Double> first = ArenaLadder.DEFAULT.limits(0);
        assertFalse(first.containsKey(MakeFacts.MOVEMENT_SPEED_ATTRIBUTE), "never limited: the core cannot lower it");
        assertFalse(first.containsKey(MakeFacts.EXTENT_BLOCKS));
        assertFalse(ArenaLadder.DEFAULT.limits(8).containsKey(MakeFacts.BUILDER_SIZE_STEP), "unlimited from wave 8");
    }

    // ── refused files, in words ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    void anUnknownFactIsAnErrorNamingIt() {
        Map<String, Object> p = params();
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) steps(p).get(0).get("limits");
        limits.put("hitPoints", 10);
        ArenaLadder.Parsed r = ArenaLadder.parse(p);
        assertFalse(r.ok());
        assertTrue(r.problem().contains("unknown fact 'hitPoints'"), r.problem());
    }

    @Test
    void anUnknownKindOrTagOrKeyIsAnError() {
        Map<String, Object> p = params();
        p.put("locked", new LinkedHashMap<>(Map.of("kinds", new ArrayList<>(List.of("tornado")))));
        assertTrue(ArenaLadder.parse(p).problem().contains("unknown kind 'tornado'"), ArenaLadder.parse(p).problem());
        p = params();
        p.put("locked", new LinkedHashMap<>(Map.of("tags", new ArrayList<>(List.of("scary")))));
        assertTrue(ArenaLadder.parse(p).problem().contains("unknown tag 'scary'"));
        p = params();
        p.put("tiers", 3);
        assertTrue(ArenaLadder.parse(p).problem().contains("unknown param 'tiers'"));
        p = params();
        p.put("progress", "fastest");
        assertTrue(ArenaLadder.parse(p).problem().contains("progress must be waves or bosses"));
        p.put("progress", "bosses");
        assertEquals(ArenaLadder.Count.BOSSES, ArenaLadder.parse(p).ladder().progress());
    }

    @Test
    void limitsOnlyRiseAndStepsGoUp() {
        Map<String, Object> p = params();
        @SuppressWarnings("unchecked")
        Map<String, Object> l1 = (Map<String, Object>) steps(p).get(1).get("limits");
        l1.put(MakeFacts.HEALTH_HALF_HEARTS, 10);
        assertTrue(ArenaLadder.parse(p).problem().contains("less than the step before"),
                ArenaLadder.parse(p).problem());
        p = params();
        @SuppressWarnings("unchecked")
        Map<String, Object> l2 = (Map<String, Object>) steps(p).get(3).get("limits");
        l2.put(MakeFacts.BUILDER_SIZE_STEP, 4);
        assertTrue(ArenaLadder.parse(p).ok(), "a limit kept at a higher number is fine");
        @SuppressWarnings("unchecked")
        Map<String, Object> l3 = (Map<String, Object>) steps(p).get(6).get("limits");
        l3.put(MakeFacts.AMOUNT_ITEMS, 99);
        assertTrue(ArenaLadder.parse(p).problem().contains("limited here but not in the step before"));
        p = params();
        steps(p).get(1).put("after", 0);
        assertTrue(ArenaLadder.parse(p).problem().contains("after more than the one before"));
        p = params();
        steps(p).get(0).put("after", 1);
        assertTrue(ArenaLadder.parse(p).problem().contains("the first step must be after 0"));
        p = params();
        steps(p).clear();
        assertTrue(ArenaLadder.parse(p).problem().contains("at least one step"));
        p = params();
        @SuppressWarnings("unchecked")
        Map<String, Object> l0 = (Map<String, Object>) steps(p).get(0).get("limits");
        l0.put(MakeFacts.COUNT_MADE, 0);
        assertTrue(ArenaLadder.parse(p).problem().contains("must be a number above 0"));
    }

    @Test
    void everyLockSaysWhenItOpens() {
        Map<String, Object> p = params();
        steps(p).get(3).remove("opens");
        assertTrue(ArenaLadder.parse(p).problem().contains("'event' is locked and no step opens it"),
                ArenaLadder.parse(p).problem());
        p = params();
        steps(p).get(1).put("opens", new LinkedHashMap<>(Map.of("kinds", new ArrayList<>(List.of("building")))));
        assertTrue(ArenaLadder.parse(p).problem().contains("which locked does not name"));
        p = params();
        steps(p).get(0).put("opens", new LinkedHashMap<>(Map.of("kinds", new ArrayList<>(List.of("event")))));
        steps(p).get(3).remove("opens");
        assertTrue(ArenaLadder.parse(p).problem().contains("the first step cannot open anything"));
    }

    @Test
    void aLadderWithNothingLockedAndOneStepIsFine() {
        Map<String, Object> p = new HashMap<>();
        p.put("steps", List.of(Map.of("after", 0, "limits", Map.of(MakeFacts.COUNT_MADE, 1))));
        ArenaLadder l = ArenaLadder.parse(p).ladder();
        assertEquals(ArenaLadder.Count.WAVES, l.progress());
        assertEquals(Map.of(MakeFacts.COUNT_MADE, 1.0), l.limits(99), "one step: forever");
        assertFalse(l.locked("event", Set.of(), true, 0));
    }

    // ── a player's step ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theStepIsTheLastReachedAndItsStampChangesWithIt() {
        ArenaLadder l = ArenaLadder.DEFAULT;
        assertEquals(0, l.stepIndex(0));
        assertEquals(0, l.stepIndex(1));
        assertEquals(1, l.stepIndex(2));
        assertEquals(4, l.stepIndex(9));
        assertEquals(l.stamp(0), l.stamp(1), "same step, same stamp");
        assertNotEquals(l.stamp(1), l.stamp(2));
        assertEquals(3, l.reached(3, 0), "waves cleared");
        assertEquals(1, new ArenaLadder(ArenaLadder.Count.BOSSES, l.steps(), l.locked()).reached(7, 1), "bosses beaten");
        Map<String, Object> p = params();
        @SuppressWarnings("unchecked")
        Map<String, Object> l0 = (Map<String, Object>) steps(p).get(0).get("limits");
        l0.put(MakeFacts.COUNT_MADE, 1);
        assertNotEquals(l.stamp(0), ArenaLadder.parse(p).ladder().stamp(0), "a rules edit changes the stamp");
    }

    @Test
    void locksAreByKindAndTagFromTheFactsAlone() {
        ArenaLadder l = ArenaLadder.DEFAULT;
        assertEquals(5, l.locksUntil("event", Set.of(MakeFacts.CHANGES_BLOCKS), true), "an event: wave 5");
        assertTrue(l.locked("event", Set.of(), true, 4));
        assertFalse(l.locked("event", Set.of(), true, 5));
        assertEquals(10, l.locksUntil("rule", Set.of(MakeFacts.WORLD_RULE), true), "a world rule: wave 10");
        assertEquals(10, l.locksUntil("event", Set.of(MakeFacts.WORLD_RULE), true), "both: the later");
        assertEquals(0, l.locksUntil("creature", Set.of(MakeFacts.FIGHTS, MakeFacts.HOSTILE), true),
                "a fighting creature is fitted, never locked");
        assertEquals(0, l.locksUntil("item", Set.of("a_tag_from_a_newer_core"), true), "an unknown tag: allowed");
        assertEquals(0, l.locksUntil("a_newer_kind", Set.of(), true), "an unknown kind: allowed");
        // cautious facts carry every tag a thing could: judged by kind alone
        MakeFacts cautious = MakeFacts.cautious("creature");
        assertFalse(cautious.known());
        assertEquals(0, l.locksUntil(cautious.kind(), cautious.tags(), cautious.known()));
        assertEquals(5, l.locksUntil("event", MakeFacts.cautious("event").tags(), false));
    }

    @Test
    void whatAStepRaisesAndOpens() {
        ArenaLadder l = ArenaLadder.DEFAULT;
        ArenaLadder.Rise r = l.rise(0, 2);
        assertFalse(r.isEmpty());
        assertEquals(List.of(40.0, 60.0), List.of(r.rose().get(MakeFacts.HEALTH_HALF_HEARTS)[0],
                r.rose().get(MakeFacts.HEALTH_HALF_HEARTS)[1]));
        assertEquals(List.of(), r.opened());
        assertTrue(l.rise(0, 1).isEmpty(), "wave 1 cleared: the same step, nothing to say");
        assertEquals(List.of("kind:event"), l.rise(4, 5).opened());
        assertTrue(Double.isNaN(l.rise(5, 8).rose().get(MakeFacts.BUILDER_SIZE_STEP)[1]), "size is unlimited now");
        assertTrue(l.rise(15, 20).top());
        assertTrue(l.rise(20, 21).isEmpty(), "past the top: nothing to say");
        assertEquals(List.of("kind:event", "tag:world_rule"), l.rise(4, 10).opened(), "two opened at once");
    }

    // ── the hook ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theHookLocksByTheLadderAndStatesTheBattlesLimitsForEveryone() {
        int[] reached = {0};
        boolean[] on = {true};
        ArenaMakes m = new ArenaMakes(() -> ArenaLadder.DEFAULT, () -> reached[0], () -> on[0]);
        WishAsk ask = WishAsk.of(HIM, WishSource.BUILDER, WishAsk.Magic.BUILDER);
        MakeFacts tornado = facts("event", Set.of(MakeFacts.CHANGES_BLOCKS, MakeFacts.FIGHTS),
                Map.of(MakeFacts.BUILDER_SIZE_STEP, 4.0));
        MakeView v = m.view(tornado, ask);
        assertEquals(MakeView.State.LOCKED, v.state(), "no tornado on day 1");
        assertEquals("Locked: opens in a battle, after wave 5.", v.words());
        MakeFacts dragon = facts("creature", Set.of(MakeFacts.FIGHTS),
                Map.of(MakeFacts.HEALTH_HALF_HEARTS, 300.0, MakeFacts.SCALE_TIMES, 3.0));
        assertEquals(MakeView.ALLOWED, m.view(dragon, ask), "a strong creature is fitted by the limits, not locked");
        WorldLimits w = m.limits(HIM);
        assertEquals(ArenaLadder.DEFAULT.limits(0), w.limits());
        assertEquals(ArenaLadder.DEFAULT.stamp(0), w.stamp());
        assertEquals(w, m.limits(UUID.randomUUID()), "the same for every player: the battle's");
        reached[0] = 5;
        assertEquals(MakeView.ALLOWED, m.view(tornado, ask), "after wave 5 the event opens");
        assertNotEquals(w.stamp(), m.limits(HIM).stamp());
        assertTrue(m.limits(HIM).limits().get(MakeFacts.HEALTH_HALF_HEARTS) > 80);
        reached[0] = 20;
        assertEquals(Map.of(), m.limits(HIM).limits(), "the top: everything");
        reached[0] = 0;   // the next battle (and prep): the first step again
        assertEquals(w, m.limits(HIM));
        assertEquals(MakeView.State.LOCKED, m.view(tornado, ask).state(), "the next battle: locked again");
        on[0] = false;    // prep: made at the first step's limits, so it can go on his hotbar; it is locked when fired
        assertEquals(MakeView.ALLOWED, m.view(tornado, ask), "in prep a locked kind is made (fitted), never locked");
        assertEquals(WorldLimits.NONE, m.limits(null));
        assertEquals(MakeView.ALLOWED, m.view(tornado, null));
    }

    @Test
    void theLimitsAreAllTheCoresOwnFacts() {
        for (ArenaLadder.Step s : ArenaLadder.DEFAULT.steps()) {
            assertTrue(MakeFacts.NUMBERS.containsAll(s.limits().keySet()), s.toString());
        }
    }

    // ── what he reads ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theWordsUseRealNumbers() {
        assertEquals("Health up to 20 hearts", ArenaWords.limit(MakeFacts.HEALTH_HALF_HEARTS, 40));
        assertEquals("Hits up to 2.5 hearts", ArenaWords.limit(MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 5));
        assertEquals("Hits up to 1 heart", ArenaWords.limit(MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 2));
        assertEquals("Up to 2 at once", ArenaWords.limit(MakeFacts.COUNT_MADE, 2));
        assertEquals("Size up to 1.5 times", ArenaWords.limit(MakeFacts.SCALE_TIMES, 1.5));
        assertEquals("Powers last up to 10 seconds", ArenaWords.limit(MakeFacts.DURATION_TICKS, 200));
        assertEquals("brandNewFact up to 3", ArenaWords.limit("brandNewFact", 3), "a newer core's number still shows");
        assertEquals("Locked: opens in a battle, after wave 5.", ArenaWords.ladderLocked(ArenaLadder.Count.WAVES, 5));
        assertEquals("Locked: opens in a battle, after you beat boss 1.",
                ArenaWords.ladderLocked(ArenaLadder.Count.BOSSES, 1));
    }

    @Test
    void theBoardSaysTheLimitsTheLocksAndWhatIsNext() {
        List<String> b = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, false, 7);
        assertEquals(ArenaWords.LADDER_TITLE, b.get(0));
        assertEquals("Before a battle: the first step", b.get(1), "in prep: the first step, whatever was reached");
        assertTrue(b.contains("Health up to 20 hearts"), b.toString());
        assertTrue(b.contains("Hits up to 2.5 hearts"), b.toString());
        assertTrue(b.contains("In a battle, locked: Events (until wave 5)"), b.toString());
        assertTrue(b.contains("In a battle, locked: World rules (until wave 10)"), b.toString());
        assertTrue(ArenaWords.ladderBoard(ArenaLadder.DEFAULT, true, 1).contains("Locked: Events (until wave 5)"));
        assertTrue(b.contains("After wave 2:"), b.toString());
        assertTrue(b.contains("Health up to 30 hearts, hits up to 3 hearts and more"), b.toString());
        List<String> mid = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, true, 3);
        assertEquals("This battle: 3 waves cleared", mid.get(1));
        assertTrue(mid.contains("Health up to 30 hearts"), mid.toString());
        List<String> top = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, true, 20);
        assertTrue(top.contains("No limits: wish for anything!"), top.toString());
        assertFalse(String.join("\n", top).contains("Locked"));
        assertTrue(ArenaWords.ladderBoard(ArenaLadder.DEFAULT, true, 4).contains("Events, health up to 60 hearts and more"));
        assertTrue(b.size() <= 16, "it fits the wall: " + b.size() + " lines");
    }

    @Test
    void theMomentIsOneOrTwoShortLines() {
        List<String> one = ArenaWords.ladderRose(ArenaLadder.DEFAULT.rise(0, 2));
        assertEquals(List.of("Your wishes can be stronger now: health up to 30 hearts, hits up to 3 hearts, size up"
                + " to 1.75 times, and more."), one);
        List<String> two = ArenaWords.ladderRose(ArenaLadder.DEFAULT.rise(4, 5));
        assertEquals(2, two.size());
        assertEquals("Events are open now!", two.get(1));
        List<String> top = ArenaWords.ladderRose(ArenaLadder.DEFAULT.rise(15, 20));
        assertEquals("Your wishes can be as strong as you like now: no more limits!", top.get(0));
        assertEquals(List.of(), ArenaWords.ladderRose(ArenaLadder.DEFAULT.rise(20, 21)));
        assertEquals("Wave 4 cleared", ArenaWords.ladderSubtitle(ArenaLadder.Count.WAVES, 4));
        assertEquals("You beat boss 1", ArenaWords.ladderSubtitle(ArenaLadder.Count.BOSSES, 1));
        List<String> prep = ArenaWords.ladderWishes(ArenaLadder.DEFAULT, false, 9);
        assertTrue(prep.get(0).startsWith("Wish limits (Before a battle: the first step): health up to 20 hearts"),
                prep.get(0));
        assertEquals("Every battle starts at the first step, and wishes grow stronger as it goes on.", prep.get(2));
    }

    @Test
    void everyLadderLineIsPlain() {
        List<String> lines = new ArrayList<>();
        for (ArenaLadder.Count pr : ArenaLadder.Count.values()) {
            ArenaLadder l = new ArenaLadder(pr, ArenaLadder.DEFAULT.steps(), ArenaLadder.DEFAULT.locked());
            for (int b = 0; b <= 21; b++) {
                lines.addAll(ArenaWords.ladderBoard(l, true, b));
                lines.addAll(ArenaWords.ladderBoard(l, false, b));
                lines.addAll(ArenaWords.ladderWishes(l, true, b));
                lines.addAll(ArenaWords.ladderWishes(l, false, b));
                lines.addAll(ArenaWords.ladderRose(l.rise(b, b + 1)));
                lines.add(ArenaWords.ladderSubtitle(pr, b + 1));
                lines.add(ArenaWords.ladderLocked(pr, b + 1));
            }
        }
        for (String kind : MakeFacts.KINDS) {
            lines.add(ArenaWords.lockName("kind:" + kind));
        }
        for (String tag : MakeFacts.TAGS) {
            lines.add(ArenaWords.lockName("tag:" + tag));
        }
        for (String l : lines) {
            assertTrue(l.chars().allMatch(c -> c >= 32 && c < 127), "plain characters only: " + l);
            assertFalse(l.toLowerCase(java.util.Locale.ROOT).contains("tier"), "no tiers or scores: " + l);
            assertFalse(l.contains("_"), "no ids: " + l);
        }
    }

    @Test
    void theBoardHangsInsideThePrepRoomBesideThePanel() {
        ArenaPlace place = ArenaPlace.DEFAULT;
        ArenaPanel.LadderSign s = place.panel().ladderSign();
        ArenaPlace.Box prep = place.prepInside();
        assertEquals(prep.x0() + ArenaPanel.IN_FRONT, s.x(), 1e-9, "on the west wall's inside face");
        double half = ArenaPanel.LADDER_LINE_WIDTH / 40.0 * s.scale() / 2;
        assertTrue(s.z() - half >= prep.z0() && s.z() + half <= prep.z1() + 1, "within the room's depth");
        assertTrue(s.y() >= place.floorY() && s.y() < place.floorY() + 1, "starting low enough to read");
        assertEquals(-90f, s.yaw(), "facing east, into the room");
        ArenaPlace.Box board = place.panel().ladderBoard();
        assertEquals(prep.x0() - 1, board.x0(), "the board is the west wall");
        assertTrue(s.z() - half >= board.z0() && s.z() + half <= board.z1() + 1, "the words are on the board");
        int lines = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, false, 0).size() + 2;   // a long line wraps
        assertTrue(s.y() + lines * ArenaPanel.LINE * s.scale() < place.floorY() + ArenaPlace.PREP_HEIGHT,
                "under the roof");
    }
}
