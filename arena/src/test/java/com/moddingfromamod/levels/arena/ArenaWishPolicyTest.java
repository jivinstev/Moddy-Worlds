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

    /** What the core does with one wish: asks (which changes nothing), then, when it may start, charges it. */
    private static Decision submit(ArenaWishPolicy p, UUID who, Phase phase, Kind kind, String thing, boolean distress,
                                   long tick, ArenaWishes w) {
        Decision d = p.check(who, phase, kind, thing, distress, tick, w);
        if (d.allowed()) {
            p.fired(who, phase, kind, thing, distress, tick, w);
        }
        return d;
    }

    /**
     * A kept Builder or Apprentice make has a recipe ({@code builder:<make id>}): its hotbar wish and its "Make another
     * one" are a REPLAY the core names (let through in a battle, with a rest), while a new make of the same thing is a
     * BUILDER make (refused in a battle).
     */
    @Test
    void aBuilderMakeFiredFromItsHotbarWishIsAKeptThingMadeAgain() {
        String recipe = com.moddingfromamod.wishbuilder.BuilderAgain.recipeId("cozy_house_1");
        assertTrue(com.moddingfromamod.wishbuilder.BuilderAgain.is(recipe), "the core's own recipe form: " + recipe);
        com.moddingfromamod.wish.WishAsk fire = com.moddingfromamod.wish.WishAsk.replay(HIM, recipe);
        assertEquals(Kind.REPLAY, ArenaCharge.kindOf(fire), "its hotbar wish");
        com.moddingfromamod.wish.WishAsk make = com.moddingfromamod.wish.WishAsk.of(HIM,
                com.moddingfromamod.wish.WishSource.BUILDER, com.moddingfromamod.wish.WishAsk.Magic.BUILDER)
                .withText(com.moddingfromamod.wishbuilder.BuilderMake.TEXT_PREFIX + "Cozy House (building)");
        assertEquals(Kind.BUILDER, ArenaCharge.kindOf(make));

        ArenaWishPolicy p = new ArenaWishPolicy();
        assertTrue(submit(p, HIM, Phase.PREP, Kind.REPLAY, recipe, false, 0, W).allowed(), "in prep: free");
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, recipe, false, 10, W).allowed(),
                "in a battle: its hotbar wish fires");
        assertEquals(Reason.RESTING, submit(p, HIM, Phase.BATTLE, Kind.REPLAY, recipe, false, 50, W).refused(),
                "and then rests");
        assertEquals(Reason.BATTLE_INSTANT_ONLY, submit(p, HIM, Phase.BATTLE, Kind.BUILDER, make.text(), false, 60, W)
                .refused(), "a new make of the same thing is still refused in a battle");
        assertEquals(0, p.used(HIM), "none of it is a Magician wish");
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
            assertTrue(submit(p, HIM, Phase.PREP, Kind.BUILDER, "Builder: x", false, i, W).allowed());
            assertTrue(submit(p, HIM, Phase.PREP, Kind.REPLAY, "house", false, i, W).allowed(), "no cooldown in prep");
        }
        assertEquals(0, p.used(HIM));
    }

    @Test
    void inPrepAMagicianWishIsCountedAtPickupUpToTheBudget() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        for (int i = 1; i <= 3; i++) {
            assertTrue(submit(p, HIM, Phase.PREP, Kind.TYPED, "", false, 0, W).allowed());
            Decision d = p.atPickup(HIM, UUID.randomUUID(), Phase.PREP, W);
            assertTrue(d.allowed() && d.counted());
            assertEquals(i, d.used());
            assertEquals(3 - i, d.left());
        }
        Decision refused = submit(p, HIM, Phase.PREP, Kind.TYPED, "", false, 0, W);
        assertEquals(Reason.MAGICIAN_USED_UP, refused.refused());
        assertEquals(3, refused.used());
        assertEquals(3, refused.budget());
        assertTrue(submit(p, HIM, Phase.PREP, Kind.BUILDER, "Builder: x", false, 0, W).allowed(),
                "the Builder is still free");
        assertTrue(submit(p, HER, Phase.PREP, Kind.TYPED, "", false, 0, W).allowed(), "each player has his own");
        assertEquals(3, p.left(HER, W));
    }

    @Test
    void aSubmitDoesNotCountButTwoWishesOnTheLastOneAreSortedAtPickup() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        ArenaWishes one = new ArenaWishes(1, 10);
        assertTrue(submit(p, HIM, Phase.PREP, Kind.TYPED, "", false, 0, one).allowed());
        assertTrue(submit(p, HIM, Phase.PREP, Kind.TYPED, "", false, 0, one).allowed(), "nothing counted yet");
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
        assertTrue(submit(p, HIM, Phase.PREP, Kind.TYPED, "", true, 0, none).allowed());
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.TYPED, "", true, 0, none).allowed(), "not even in a battle");
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
        assertEquals(Reason.BATTLE_INSTANT_ONLY, submit(p, HIM, Phase.BATTLE, Kind.TYPED, "", false, 0, W).refused());
        assertEquals(Reason.BATTLE_INSTANT_ONLY,
                submit(p, HIM, Phase.BATTLE, Kind.BUILDER, "Builder: x", false, 0, W).refused());
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 0, W).allowed());
        assertEquals(Reason.BATTLE_INSTANT_ONLY, p.atPickup(HIM, UUID.randomUUID(), Phase.BATTLE, W).refused(),
                "a typed wish sent in prep and picked up in a battle waits for the next prep");
        assertEquals(0, p.used(HIM));
    }

    @Test
    void aKeptThingRestsBetweenFiresInABattleCountedInTicks() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        long t = 1000;
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t, W).allowed());
        Decision again = submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 40, W);
        assertEquals(Reason.RESTING, again.refused());
        assertEquals(8, again.secondsLeft(), "200 ticks minus 40: 8 seconds left");
        assertEquals(1, submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 199, W).secondsLeft(),
                "rounded up: never 0 while it rests");
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "sword", false, t + 41, W).allowed(),
                "another kept thing has its own");
        assertTrue(submit(p, HER, Phase.BATTLE, Kind.REPLAY, "house", false, t + 42, W).allowed(),
                "another player has his own");
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 200, W).allowed(), "rested");
        assertEquals(Reason.RESTING, submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 201, W).refused(),
                "and rests again");
        p.battleEnded();
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t + 202, W).allowed(),
                "a battle's end clears every cooldown");
    }

    @Test
    void aRefusedFireDoesNotRestartTheCooldown() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 0, W);
        for (int t = 1; t < 200; t += 7) {
            submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, t, W);
        }
        assertTrue(submit(p, HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 200, W).allowed());
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

    @Test
    void theKindOfAWishIsReadFromTheCoresAsk() {
        assertEquals(Kind.REPLAY, ArenaWishPolicy.kindOf("REPLAY", true), "a hotbar fire, Make another one");
        assertEquals(Kind.BUILDER, ArenaWishPolicy.kindOf("REPLAY", false), "typed words a kept recipe answers");
        assertEquals(Kind.BUILDER, ArenaWishPolicy.kindOf("BUILDER", true));
        assertEquals(Kind.BUILDER, ArenaWishPolicy.kindOf("APPRENTICE", false), "the Apprentice's page");
        assertEquals(Kind.BUILDER, ArenaWishPolicy.kindOf("APPRENTICE", true), "an Apprentice tile");
        assertEquals(Kind.TYPED, ArenaWishPolicy.kindOf("MAGICIAN", false));
        for (com.moddingfromamod.wish.WishAsk.Magic m : com.moddingfromamod.wish.WishAsk.Magic.values()) {
            assertTrue(java.util.Set.of("REPLAY", "BUILDER", "APPRENTICE", "MAGICIAN").contains(m.name()),
                    "every kind of magic the core names is one the arena reads: " + m);
        }
    }

    @Test
    void askingChangesNothing() {
        ArenaWishPolicy p = new ArenaWishPolicy();
        for (int i = 0; i < 20; i++) {
            assertTrue(p.check(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, i, W).allowed(),
                    "the core and a menu may ask as often as they like");
        }
        assertEquals(0, p.firedThisWave(HIM));
        assertEquals(0, p.restTicks(HIM, "house", 20));
    }

    @Test
    void eachFireInABattleCostsOneOfTheWavesWishes() {
        ArenaWishes three = new ArenaWishes(3, 1, 3);
        ArenaWishPolicy p = new ArenaWishPolicy();
        assertEquals(3, p.firesLeft(HIM, three));
        assertEquals(2, p.fired(HIM, Phase.BATTLE, Kind.REPLAY, "sword", false, 0, three));
        assertEquals(1, p.fired(HIM, Phase.BATTLE, Kind.REPLAY, "house", false, 0, three), "any wish costs one");
        assertEquals(0, p.fired(HIM, Phase.BATTLE, Kind.REPLAY, "sword", false, 40, three));
        Decision out = p.check(HIM, Phase.BATTLE, Kind.REPLAY, "cat", false, 41, three);
        assertEquals(Reason.OUT_OF_WISHES, out.refused());
        assertEquals(0, out.firesLeft());
        assertEquals(-1, p.fired(HIM, Phase.BATTLE, Kind.REPLAY, "cat", false, 41, three), "never below zero");
        assertEquals(3, p.firesLeft(HER, three), "each player has his own");
        assertEquals(Reason.RESTING, p.check(HIM, Phase.BATTLE, Kind.REPLAY, "sword", false, 41, three).refused(),
                "a resting wish says it rests, which is what its sweep shows");

        p.newWave();
        assertEquals(3, p.firesLeft(HIM, three), "the next wave: the count starts again");
        assertTrue(p.check(HIM, Phase.BATTLE, Kind.REPLAY, "cat", false, 42, three).allowed());
        assertEquals(19, p.restTicks(HIM, "sword", 41), "rests go on across a new wave");
    }

    @Test
    void firesInPrepAndOtherKindsCostNothing() {
        ArenaWishes one = new ArenaWishes(3, 1, 1);
        ArenaWishPolicy p = new ArenaWishPolicy();
        for (int i = 0; i < 5; i++) {
            p.fired(HIM, Phase.PREP, Kind.REPLAY, "sword", false, i * 100L, one);
            p.fired(HIM, Phase.BATTLE, Kind.BUILDER, "Builder: x", false, i * 100L, one);
        }
        p.fired(HIM, Phase.BATTLE, Kind.REPLAY, "sword", true, 0, one);
        assertEquals(0, p.firedThisWave(HIM), "prep, a new make, a child in distress: no fire counted");
        assertEquals(0, p.restTicks(HIM, "sword", 0));
        assertTrue(p.check(HIM, Phase.BATTLE, Kind.REPLAY, "sword", true, 0, one).allowed());
    }

    @Test
    void aBattlesEndStartsTheWavesCountAgain() {
        ArenaWishes one = new ArenaWishes(3, 1, 1);
        ArenaWishPolicy p = new ArenaWishPolicy();
        p.fired(HIM, Phase.BATTLE, Kind.REPLAY, "sword", false, 0, one);
        assertEquals(0, p.firesLeft(HIM, one));
        p.battleEnded();
        assertEquals(1, p.firesLeft(HIM, one));
        assertEquals(0, p.restTicks(HIM, "sword", 1));
    }
}
