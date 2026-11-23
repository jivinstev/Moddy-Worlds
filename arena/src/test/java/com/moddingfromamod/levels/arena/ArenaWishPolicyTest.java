package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaWishPolicy.Decision;
import com.moddingfromamod.levels.arena.ArenaWishPolicy.Kind;
import com.moddingfromamod.levels.arena.ArenaWishPolicy.Phase;
import com.moddingfromamod.levels.arena.ArenaWishPolicy.Reason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link ArenaWishPolicy}: which wishes go through in prep and in a battle, the Magician budget and the cooldowns. */
class ArenaWishPolicyTest {

    private static final ArenaWishes W = ArenaWishes.DEFAULT;   // 3 Magician wishes, 10 s
    private static final UUID HIM = UUID.randomUUID();
    private static final UUID HER = UUID.randomUUID();

    @Test
    void theKindOfAWishIsReadFromWhatTheSubmitHookIsTold() {
        assertEquals(Kind.TYPED, ArenaWishPolicy.kindOf("a dragon that breathes ice", false));
        assertEquals(Kind.TYPED, ArenaWishPolicy.kindOf("Builder: a house (building)", false),
                "typed words are typed, whatever they say");
        assertEquals(Kind.BUILDER, ArenaWishPolicy.kindOf("Builder: Cozy House (building)", true));
        assertEquals(Kind.REPLAY, ArenaWishPolicy.kindOf("apprentice_cozy_house", true), "a recipe id: a kept thing");
        assertEquals(Kind.REPLAY, ArenaWishPolicy.kindOf("cozy house", true), "a part's name: a kept thing");
        assertEquals(Kind.REPLAY, ArenaWishPolicy.kindOf(null, true));
    }

    /**
     * A kept Builder or Apprentice make has a recipe ({@code builder:<make id>}). Its hotbar wish and its "Make another
     * one" reach the submit hook with the make's own name as the text (the core resolves the recipe to the make's
     * label, with no "Builder: " in front) and {@code recipe} true, or with the recipe id itself if the make has no
     * name; the Builder make itself still arrives as "Builder: <name> (<kind>)". So the first two are a kept thing made
     * again, let through in a battle with a rest, and the third is a new make, refused.
     */
    @Test
    void aBuilderMakeFiredFromItsHotbarWishIsAKeptThingMadeAgain() {
        String recipe = com.moddingfromamod.wishbuilder.BuilderAgain.recipeId("cozy_house_1");
        assertTrue(com.moddingfromamod.wishbuilder.BuilderAgain.is(recipe), "the core's own recipe form: " + recipe);
        assertEquals(Kind.REPLAY, ArenaWishPolicy.kindOf("Cozy House", true), "the make's name: its hotbar wish");
        assertEquals(Kind.REPLAY, ArenaWishPolicy.kindOf(recipe, true), "the recipe id, when the make has no name");
        assertEquals(Kind.BUILDER, ArenaWishPolicy.kindOf(
                com.moddingfromamod.wishbuilder.BuilderMake.TEXT_PREFIX + "Cozy House (building)", true));

        ArenaWishPolicy p = new ArenaWishPolicy();
        assertTrue(p.atSubmit(HIM, Phase.PREP, ArenaWishPolicy.kindOf("Cozy House", true), "Cozy House", false, 0, W)
                .allowed(), "in prep: free");
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, ArenaWishPolicy.kindOf("Cozy House", true), "Cozy House", false, 10, W)
                .allowed(), "in a battle: its hotbar wish fires");
        assertEquals(Reason.RESTING, p.atSubmit(HIM, Phase.BATTLE, ArenaWishPolicy.kindOf("Cozy House", true),
                "Cozy House", false, 50, W).refused(), "and then rests");
        assertEquals(Reason.BATTLE_INSTANT_ONLY, p.atSubmit(HIM, Phase.BATTLE,
                ArenaWishPolicy.kindOf("Builder: Cozy House (building)", true), "Builder: Cozy House (building)", false,
                60, W).refused(), "a new make of the same thing is still refused in a battle");
        assertEquals(0, p.used(HIM), "none of it is a Magician wish");
    }

    @Test
    void theBuilderPrefixIsTheCoresOwn() {
        assertEquals(com.moddingfromamod.wishbuilder.BuilderMake.TEXT_PREFIX, ArenaWishPolicy.BUILDER_TEXT_PREFIX);
    }

    @Test
    void phasesFollowTheMatch() {
        assertEquals(Phase.PREP, ArenaWishPolicy.phaseOf(ArenaMatch.Phase.IDLE));
        assertEquals(Phase.PREP, ArenaWishPolicy.phaseOf(null));
        for (ArenaMatch.Phase p : ArenaMatch.Phase.values()) {
            if (p != ArenaMatch.Phase.IDLE) {
                assertEquals(Phase.BATTLE, ArenaWishPolicy.phaseOf(p), "a battle from the countdown to the tidy: " + p);
            }
        }
    }

    @Test
    void inPrepBuilderApprenticeAndReplaysAreFreeAndUnlimited() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        for (int i = 0; i < 50; i++) {
            assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.BUILDER, "Builder: x", false, i, W).allowed());
            assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.REPLAY, "house", false, i, W).allowed(), "no cooldown in prep");
        }
        assertEquals(0, p.used(HIM));
    }

    @Test
    void inPrepAMagicianWishIsCountedAtPickupUpToTheBudget() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        for (int i = 1; i <= 3; i++) {
            assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.TYPED, "", false, 0, W).allowed());
            Decision d = p.atPickup(HIM, UUID.randomUUID(), Phase.PREP, W);
            assertTrue(d.allowed() && d.counted());
            assertEquals(i, d.used());
            assertEquals(3 - i, d.left());
        }
        Decision refused = p.atSubmit(HIM, Phase.PREP, Kind.TYPED, "", false, 0, W);
        assertEquals(Reason.MAGICIAN_USED_UP, refused.refused());
        assertEquals(3, refused.used());
        assertEquals(3, refused.budget());
        assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.BUILDER, "Builder: x", false, 0, W).allowed(),
                "the Builder is still free");
        assertTrue(p.atSubmit(HER, Phase.PREP, Kind.TYPED, "", false, 0, W).allowed(), "each player has his own");
        assertEquals(3, p.left(HER, W));
    }

    @Test
    void aSubmitDoesNotCountButTwoWishesOnTheLastOneAreSortedAtPickup() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        ArenaWishes one = new ArenaWishes(1, 10);
        assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.TYPED, "", false, 0, one).allowed());
        assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.TYPED, "", false, 0, one).allowed(), "nothing counted yet");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(p.atPickup(HIM, a, Phase.PREP, one).allowed());
        assertEquals(Reason.MAGICIAN_USED_UP, p.atPickup(HIM, b, Phase.PREP, one).refused());
        assertTrue(p.atPickup(HIM, a, Phase.PREP, one).allowed(), "the same wish picked up again is not counted twice");
        assertFalse(p.atPickup(HIM, a, Phase.PREP, one).counted());
        assertEquals(1, p.used(HIM));
    }

    @Test
    void aWishThatDidNotHappenGivesItsCountBackOnce() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        UUID w = UUID.randomUUID();
        p.atPickup(HIM, w, Phase.PREP, W);
        assertEquals(1, p.used(HIM));
        assertTrue(p.giveBack(w));
        assertEquals(0, p.used(HIM));
        assertFalse(p.giveBack(w), "once");
        assertFalse(p.giveBack(UUID.randomUUID()), "a wish never counted gives nothing back");
        assertFalse(p.giveBack(null));
    }

    @Test
    void aWishCountedBeforeABattleEndedGivesNothingBackAfterIt() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        UUID w = UUID.randomUUID();
        p.atPickup(HIM, w, Phase.PREP, W);
        p.battleEnded();
        assertEquals(0, p.used(HIM), "a new prep");
        assertFalse(p.giveBack(w), "never below zero");
        assertEquals(3, p.left(HIM, W));
    }

    @Test
    void aChildInDistressIsNeverRefusedNorCounted() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        ArenaWishes none = new ArenaWishes(0, 10);
        assertTrue(p.atSubmit(HIM, Phase.PREP, Kind.TYPED, "", true, 0, none).allowed());
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.TYPED, "", true, 0, none).allowed(), "not even in a battle");
        UUID w = UUID.randomUUID();
        p.waive(w);
        Decision d = p.atPickup(HIM, w, Phase.BATTLE, none);
        assertTrue(d.allowed());
        assertFalse(d.counted());
        assertEquals(0, p.used(HIM));
    }

    @Test
    void inABattleOnlyAKeptThingFiredAgainGoesThrough() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        assertEquals(Reason.BATTLE_INSTANT_ONLY, p.atSubmit(HIM, Phase.BATTLE, Kind.TYPED, "", false, 0, W).refused());
        assertEquals(Reason.BATTLE_INSTANT_ONLY,
                p.atSubmit(HIM, Phase.BATTLE, Kind.BUILDER, "Builder: x", false, 0, W).refused());
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 0, W).allowed());
        assertEquals(Reason.BATTLE_INSTANT_ONLY, p.atPickup(HIM, UUID.randomUUID(), Phase.BATTLE, W).refused(),
                "a typed wish sent in prep and picked up in a battle waits for the next prep");
        assertEquals(0, p.used(HIM));
    }

    @Test
    void aKeptThingRestsBetweenFiresInABattleCountedInTicks() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        long t = 1000;
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t, W).allowed());
        Decision again = p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 40, W);
        assertEquals(Reason.RESTING, again.refused());
        assertEquals(8, again.secondsLeft(), "200 ticks minus 40: 8 seconds left");
        assertEquals(1, p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 199, W).secondsLeft(),
                "rounded up: never 0 while it rests");
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "sword", false, t + 41, W).allowed(),
                "another kept thing has its own");
        assertTrue(p.atSubmit(HER, Phase.BATTLE, Kind.REPLAY, "house", false, t + 42, W).allowed(),
                "another player has his own");
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 200, W).allowed(), "rested");
        assertEquals(Reason.RESTING, p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 201, W).refused(),
                "and rests again");
        p.battleEnded();
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 202, W).allowed(),
                "a battle's end clears every cooldown");
    }

    @Test
    void aRefusedFireDoesNotRestartTheCooldown() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 0, W);
        for (int t = 1; t < 200; t += 7) {
            p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t, W);
        }
        assertTrue(p.atSubmit(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 200, W).allowed());
    }

    @Test
    void decisionsSayWhatIsLeft() {
        Decision d = new Decision(null, 5, 3, 0);
        assertEquals(0, d.left());
        assertTrue(d.allowed());
        assertNull(d.refused());
        assertEquals(1, ArenaWishPolicy.secondsUntil(0, 1));
        assertEquals(0, ArenaWishPolicy.secondsUntil(10, 5));
    }
}
