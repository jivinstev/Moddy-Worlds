package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.moddingfromamod.wish.WishAsk;
import com.moddingfromamod.wish.WishSource;
import com.moddingfromamod.wishbuilder.MakeFacts;
import com.moddingfromamod.world.HandedOver;
import com.moddingfromamod.world.Landing;
import com.moddingfromamod.world.NotKept;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The prep room's hand-over ({@link ArenaHandOver}): where a make is finished, what the arena answers for each kind
 * there, the hook's answer as the core reads it, the hand-over spot in the place, the words, and the first-time line
 * being remembered.
 */
class ArenaHandOverTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;
    private static final UUID HIM = UUID.fromString("00000000-0000-0000-0000-000000000007");

    /** The middle of the prep room, at its floor: where a player arrives. */
    private static double[] inPrep() {
        int[] a = P.arrival();
        return new double[] {a[0] + 0.5, a[1], a[2] + 0.5};
    }

    // ── where it is finished ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void inThePrepRoomAimingAtThePrepRoomItIsThePrepRoom() {
        double[] at = inPrep();
        int[] floor = P.handOverSpot();
        assertEquals(ArenaHandOver.Where.PREP_ROOM,
                ArenaHandOver.where(P, at[0], at[1], at[2], true, floor[0], floor[2]));
        assertEquals(ArenaHandOver.Where.PREP_ROOM, ArenaHandOver.where(P, at[0], at[1], at[2], false, 0, 0),
                "aiming at nothing he can be read by: where he stands decides");
    }

    @Test
    void inThePrepRoomAimingIntoTheArenaItLandsInTheArena() {
        double[] at = inPrep();
        assertEquals(ArenaHandOver.Where.ARENA, ArenaHandOver.where(P, at[0], at[1], at[2], true, 0, 0),
                "the arena's middle, seen through the gate");
        assertEquals(ArenaHandOver.Where.ARENA, ArenaHandOver.where(P, at[0], at[1], at[2], true, 0, -15),
                "its far side");
        assertEquals(ArenaHandOver.Where.PREP_ROOM,
                ArenaHandOver.where(P, at[0], at[1], at[2], true, 0, P.sharedWallZ()),
                "the shared wall (the glass, the gate) is not the arena's inside: what hits it stays in the prep room");
    }

    @Test
    void theGatewayIsLookedThroughAndNothingElseOfTheSharedWall() {
        int[] g = P.gate();
        assertTrue(P.inGateway(g[0], g[1], g[2]), "the gate (or a wave's bars)");
        assertTrue(P.inGateway(g[0], g[1] + 1, g[2]), "the opening above it");
        assertFalse(P.inGateway(g[0] + 1, g[1], g[2]), "the control panel's board beside it");
        assertFalse(P.inGateway(g[0], g[1] + 2, g[2]), "the board above it");
        assertFalse(P.inGateway(g[0], g[1], g[2] + 1), "the prep room's floor before it");
    }

    @Test
    void inTheArenaAndOutsideThePlace() {
        assertEquals(ArenaHandOver.Where.ARENA, ArenaHandOver.where(P, 0.5, 64, 0.5, true, 4, 21),
                "standing in the arena, wherever he aims");
        assertEquals(ArenaHandOver.Where.OUTSIDE, ArenaHandOver.where(P, 200, 64, 200, true, 0, 0));
        assertEquals(ArenaHandOver.Where.OUTSIDE, ArenaHandOver.where(null, 0.5, 64, 21.5, true, 0, 0));
    }

    // ── the answer ───────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void inThePrepRoomAnItemIsTheRealItemAndEverythingElseAHotbarWish() {
        assertEquals(ArenaHandOver.Answer.KEPT_AS_ITEM, ArenaHandOver.answer(ArenaHandOver.Where.PREP_ROOM, "item", false));
        for (String kind : List.of("creature", "ride", "building", "event")) {
            assertEquals(ArenaHandOver.Answer.KEPT, ArenaHandOver.answer(ArenaHandOver.Where.PREP_ROOM, kind, false), kind);
        }
        assertEquals(ArenaHandOver.Answer.KEPT, ArenaHandOver.answer(ArenaHandOver.Where.PREP_ROOM, "picture", false),
                "asked to keep: the core lands what it cannot keep and says so (notKept)");
    }

    @Test
    void inTheArenaOutsideAndForAFireItLandsAsAlways() {
        for (String kind : List.of("item", "creature", "building", "event")) {
            assertEquals(ArenaHandOver.Answer.LANDS, ArenaHandOver.answer(ArenaHandOver.Where.ARENA, kind, false), kind);
            assertEquals(ArenaHandOver.Answer.LANDS, ArenaHandOver.answer(ArenaHandOver.Where.OUTSIDE, kind, false), kind);
            assertEquals(ArenaHandOver.Answer.LANDS, ArenaHandOver.answer(ArenaHandOver.Where.PREP_ROOM, kind, true),
                    "a hotbar fire makes the thing, never another wish: " + kind);
        }
        assertEquals(ArenaHandOver.Answer.LANDS, ArenaHandOver.answer(null, "creature", false));
    }

    @Test
    void theHookAnswersTheCoreAndPassesWhatItIsToldOn() {
        ArenaHandOver.Where[] where = {ArenaHandOver.Where.PREP_ROOM};
        List<HandedOver> handed = new ArrayList<>();
        List<NotKept> notKept = new ArrayList<>();
        ArenaMakes m = new ArenaMakes(() -> ArenaLadder.DEFAULT, () -> 0, () -> false, id -> where[0], handed::add,
                notKept::add);
        MakeFacts dragon = new MakeFacts("creature", Map.of(), Set.of(MakeFacts.FIGHTS), true);
        MakeFacts sword = new MakeFacts("item", Map.of(), Set.of(MakeFacts.FIGHTS), true);
        WishAsk builder = WishAsk.of(HIM, WishSource.BUILDER, WishAsk.Magic.BUILDER);
        WishAsk apprentice = WishAsk.of(HIM, WishSource.TYPED, WishAsk.Magic.APPRENTICE);
        WishAsk magician = WishAsk.of(HIM, WishSource.TYPED, WishAsk.Magic.MAGICIAN);
        assertEquals(Landing.KEPT, m.landing(dragon, builder));
        assertEquals(Landing.KEPT_AS_ITEM, m.landing(sword, builder));
        assertEquals(Landing.KEPT, m.landing(dragon, apprentice));
        assertEquals(Landing.KEPT_AS_ITEM, m.landing(sword, apprentice));
        assertEquals(Landing.KEPT, m.landing(MakeFacts.cautious("creature"), magician));
        assertEquals(Landing.LANDS, m.landing(dragon, WishAsk.replay(HIM, "builder:dragon_1")), "a fire lands");
        where[0] = ArenaHandOver.Where.ARENA;
        assertEquals(Landing.LANDS, m.landing(dragon, builder), "aimed into the arena: it lands");
        assertEquals(Landing.LANDS, m.landing(sword, builder));
        assertEquals(Landing.LANDS, m.landing(null, builder));
        assertEquals(Landing.LANDS, m.landing(dragon, null));
        HandedOver h = new HandedOver(HIM, "dragon_1", "creature", "builder:dragon_1", dragon, 2, false);
        m.handedOver(h);
        NotKept n = new NotKept(HIM, "picture", Landing.KEPT, NotKept.Why.NO_REPLAY_RECIPE);
        m.notKept(n);
        assertEquals(List.of(h), handed);
        assertEquals(List.of(n), notKept);
        ArenaMakes ladderOnly = new ArenaMakes(() -> ArenaLadder.DEFAULT, () -> 0, () -> false);
        assertEquals(Landing.LANDS, ladderOnly.landing(dragon, builder), "without a hand-over, everything lands");
    }

    @Test
    void whereItWentAndWhyItWasNotKept() {
        assertEquals(ArenaHandOver.Went.HOTBAR, ArenaHandOver.went(0, false));
        assertEquals(ArenaHandOver.Went.HOTBAR, ArenaHandOver.went(8, false));
        assertEquals(ArenaHandOver.Went.WISH_BAG, ArenaHandOver.went(-1, false));
        assertEquals(ArenaHandOver.Went.REAL_ITEM, ArenaHandOver.went(-1, true));
        for (NotKept.Why w : NotKept.Why.values()) {
            assertEquals(w.name(), ArenaHandOver.notKeptWhy(w.name()).name(), "every reason the core has is known");
        }
        assertEquals(ArenaHandOver.NotKeptWhy.OTHER, ArenaHandOver.notKeptWhy("SOMETHING_NEW"));
        assertEquals(ArenaHandOver.NotKeptWhy.OTHER, ArenaHandOver.notKeptWhy(null));
    }

    // ── the spot ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theSpotIsAFloorBlockOfThePrepRoomByItsEastWallAndNeverALight() {
        for (ArenaPlace place : List.of(P, new ArenaPlace(21, 7, 4, 64), new ArenaPlace(41, 13, 5, 100))) {
            int[] h = place.handOverSpot();
            ArenaPlace.Box in = place.prepInside();
            assertEquals(place.floorY() - 1, h[1], "in the floor");
            assertEquals(in.x1(), h[0], "by the east wall");
            assertTrue(h[2] >= in.z0() && h[2] <= in.z1());
            assertTrue(place.inPrepRoom(h[0] + 0.5, h[2] + 0.5));
            assertFalse(place.isPrepLight(h[0], h[2]), "not one of the lights");
            int[] a = place.arrival();
            assertFalse(h[0] == a[0] && h[2] == a[2], "not where he arrives");
            assertTrue(place.fills().contains(place.handOverFills().get(0)), "a new place has it");
            assertNull(place.reset().kindAt(h[0], h[1], h[2]), "a battle's reset never touches the prep room");
        }
    }

    @Test
    void anOlderPlaceGainsTheSpotOnce() {
        assertTrue(ArenaPlace.UPGRADES.contains(ArenaPlace.UPGRADE_HAND_OVER_SPOT));
        Set<String> before = Set.of("start-control", "no-natural-spawns", "control-panel", "champions-board",
                "ground-base", "ladder-board");
        assertEquals(List.of(ArenaPlace.UPGRADE_HAND_OVER_SPOT), ArenaPlace.pendingUpgrades(before));
    }

    // ── the words ────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void theLineNamesItAndWhereItWent() {
        assertEquals("Your Dragon is ready! Slot 3. Use it in the arena.",
                ArenaWords.handOverReady("Dragon", ArenaHandOver.Went.HOTBAR, 2));
        assertEquals("Your wish is ready! Slot 1. Use it in the arena.",
                ArenaWords.handOverReady("", ArenaHandOver.Went.HOTBAR, 0));
        assertEquals("Your Dragon is ready! Your hotbar is full, so it is in your Wish Bag.",
                ArenaWords.handOverReady("Dragon", ArenaHandOver.Went.WISH_BAG, -1));
        assertEquals("Your Fire Sword is ready! It is in your inventory.",
                ArenaWords.handOverReady("Fire Sword", ArenaHandOver.Went.REAL_ITEM, -1));
        assertEquals("Your item is ready! It is in your inventory.",
                ArenaWords.handOverReady(null, ArenaHandOver.Went.REAL_ITEM, -1));
    }

    @Test
    void theLineNeverSaysWhatThePinNoticeSays() {
        for (ArenaHandOver.Went w : ArenaHandOver.Went.values()) {
            String line = ArenaWords.handOverReady("Dragon", w, 4).toLowerCase(java.util.Locale.ROOT);
            for (String pin : List.of("locked", "weaker", "smaller", "at first", "too much")) {
                assertFalse(line.contains(pin), w + ": " + line);
            }
        }
    }

    @Test
    void theFirstTimeLineSaysTheDifferenceAndTheNotKeptLinesAreShort() {
        String first = ArenaWords.handOverFirst();
        assertTrue(first.contains("real item") && first.contains("wish on your hotbar"), first);
        assertTrue(first.contains("one of the wave's hotbar wishes"), "a fire costs one wish of the wave: " + first);
        for (ArenaHandOver.NotKeptWhy w : ArenaHandOver.NotKeptWhy.values()) {
            for (String kind : List.of("picture", "other", "creature")) {
                String line = ArenaWords.handOverNotKept(kind, w);
                assertTrue(line.length() < 100 && line.endsWith("."), line);
            }
        }
        assertEquals("Your picture went up where you aimed: a picture cannot be kept as a wish.",
                ArenaWords.handOverNotKept("picture", ArenaHandOver.NotKeptWhy.NO_REPLAY_RECIPE));
        assertEquals("Your change was made where your thing stands.",
                ArenaWords.handOverNotKept("creature", ArenaHandOver.NotKeptWhy.A_CHANGE));
    }

    @Test
    void everyHandOverWordIsPlainCharacters() {
        List<String> all = new ArrayList<>();
        for (ArenaHandOver.Went w : ArenaHandOver.Went.values()) {
            all.add(ArenaWords.handOverReady("Dragon", w, 2));
        }
        all.add(ArenaWords.handOverFirst());
        for (ArenaHandOver.NotKeptWhy w : ArenaHandOver.NotKeptWhy.values()) {
            all.add(ArenaWords.handOverNotKept("picture", w));
        }
        for (String s : all) {
            assertTrue(s.chars().allMatch(c -> c >= 32 && c < 127), s);
        }
    }

    // ── the first time, remembered ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void theFirstTimeLineIsSaidOncePerPlayerAndSurvivesASave() {
        ArenaState s = ArenaState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"v\":4}")).getOrThrow();
        assertTrue(s.tellHandOver(HIM), "a record from before the hand-over: nobody told yet");
        assertFalse(s.tellHandOver(HIM), "only once");
        var saved = ArenaState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
        assertEquals(5, saved.getAsJsonObject().get("v").getAsInt());
        ArenaState again = ArenaState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertFalse(again.tellHandOver(HIM), "remembered after a restart");
        assertTrue(again.tellHandOver(UUID.randomUUID()), "another player is told his first time");
    }
}
