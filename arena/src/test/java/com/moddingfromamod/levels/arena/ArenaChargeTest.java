package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.wish.Wish;
import com.moddingfromamod.wish.WishAsk;
import com.moddingfromamod.wish.WishCharge;
import com.moddingfromamod.wish.WishSource;
import com.moddingfromamod.wish.WishStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The arena's charge through the core's own {@link WishCharge} methods, with stand-in wishes of the Magician kind (a
 * typed wish, as the core hands it to {@code atPickup} when the Magician picks it up). No model is called and no real
 * Magician wish is made: this drives the hooks the core calls, in the order it calls them, with the core's own
 * {@link Wish} records, and the core's own {@link WishAsk} at {@code refuses}, {@code charge} and {@code restLeft}, as the
 * core builds it for each kind of wish (the playtests prove the core really asks so).
 */
class ArenaChargeTest {

    private static final UUID HIM = UUID.randomUUID();

    private static ArenaCharge charge(ArenaWishes w) {
        return new ArenaCharge(null, new ArenaWishPolicy(), () -> w);
    }

    private static Wish typed() {
        return Wish.create("a dragon that breathes ice", WishSource.TYPED, 0L, HIM, "Him", "minecraft:overworld",
                0.5, 64, 21.5);
    }

    @Test
    void itIsAWishCharge() {
        WishCharge c = charge(ArenaWishes.DEFAULT);
        assertFalse(c.priced(HIM), "the arena prices nothing");
        assertEquals(WishCharge.Paid.FREE, c.charge(HIM, UUID.randomUUID(), false), "no-model makes are free");
    }

    @Test
    void aMagicianWishIsCountedAtPickupAndTheFourthIsRefusedInWords() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        for (int i = 0; i < 3; i++) {
            WishCharge.Paid p = c.atPickup(typed());
            assertFalse(p.refused());
        }
        assertEquals(3, c.policy().used(HIM));
        WishCharge.Paid fourth = c.atPickup(typed());
        assertTrue(fourth.refused());
        assertEquals("You have used all 3 Magician wishes for this prep. You get 3 more after the next battle."
                + " Builder and Apprentice wishes are still free.", fourth.line());
        assertEquals(3, c.policy().used(HIM), "a refused wish is not counted");
        // the core then marks it REJECTED: nothing to give back
        Wish w = typed();
        c.atPickup(w);   // refused again
        c.onStatus(w, w.withStatus(WishStatus.REJECTED, "not started"));
        assertEquals(3, c.policy().used(HIM));
    }

    @Test
    void aMagicianWishThatFailsOrIsUndoneGivesItsCountBack() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        Wish failed = typed();
        Wish undone = typed();
        Wish done = typed();
        c.atPickup(failed);
        c.atPickup(undone);
        c.atPickup(done);
        assertEquals(3, c.policy().used(HIM));

        Wish running = failed.withStatus(WishStatus.RUNNING, "");
        c.onStatus(failed, running);
        assertEquals(3, c.policy().used(HIM), "a status that is not an end changes nothing");
        c.onStatus(running, running.withStatus(WishStatus.FAILED, "it broke"));
        assertEquals(2, c.policy().used(HIM), "failed: given back");

        c.onStatus(done, done.withStatus(WishStatus.DONE, "made"));
        assertEquals(2, c.policy().used(HIM), "done: it counts");

        Wish undoneDone = undone.withStatus(WishStatus.DONE, "made");
        String line = c.refundUndone(null, undoneDone);
        assertEquals("It does not count. Magician wishes left before the next battle: 2 of 3.", line);
        assertEquals(1, c.policy().used(HIM));
        c.onStatus(undoneDone, undoneDone.withStatus(WishStatus.STOPPED, "undone"));
        assertEquals(1, c.policy().used(HIM), "an undone wish is given back once, not twice");
        assertNull(c.refundUndone(null, undoneDone), "nothing more to give back");
        assertNull(c.refundUndone(null, typed()), "a wish never counted gives nothing back");
    }

    @Test
    void inABattleATypedWishIsRefusedAtPickupAndCountsNothing() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        c.phase(ArenaWishPolicy.Phase.BATTLE);
        WishCharge.Paid p = c.atPickup(typed());
        assertTrue(p.refused());
        assertEquals(ArenaWords.battleInstantOnly(), p.line());
        assertEquals(0, c.policy().used(HIM));
    }

    @Test
    void theCountStartsAgainWhenABattleEnds() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        Wish w = typed();
        c.atPickup(w);
        c.atPickup(typed());
        c.battleEnded();
        assertEquals(0, c.policy().used(HIM));
        c.onStatus(w, w.withStatus(WishStatus.FAILED, "late"));
        assertEquals(0, c.policy().used(HIM), "a wish from the last prep gives nothing back in this one");
    }

    @Test
    void theMenusAndWishesCommandSayWhatIsLeftOrThatItIsABattle() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        c.atPickup(typed());
        assertEquals(List.of("Magician wishes left before the next battle: 2 of 3.",
                "Builder and Apprentice wishes are free. A wish with Add to hotbar on its page in My wishes can be fired"
                        + " in a battle, 5 each wave."), c.wishesCommand(HIM));
        assertEquals("Moddy - 2 Magician wishes left", c.panelTitle("Moddy", HIM));
        assertEquals("Wish - 3 Magician wishes left", c.composeTitle("Wish", UUID.randomUUID()));
        c.phase(ArenaWishPolicy.Phase.BATTLE);
        assertEquals(List.of("A battle is on: fire your wishes from your hotbar. You have 5 of 5 left this wave. Each one"
                + " rests 10 seconds after you use it.", "New wishes are made in the prep room before a battle."),
                c.wishesCommand(HIM));
        assertEquals("Moddy - Battle: use your hotbar", c.panelTitle("Moddy", HIM));
    }

    // ── the core's WishAsk hooks ────────────────────────────────────────────────────────────────────────────────────

    /** A hotbar fire (or its landing in his inventory), as the core asks about it. */
    private static WishAsk fire(String recipe) {
        return WishAsk.replay(HIM, recipe).withText(recipe);
    }

    private static WishAsk make() {
        return WishAsk.of(HIM, WishSource.BUILDER, WishAsk.Magic.BUILDER).withText("Builder: Cozy House (building)");
    }

    private static WishAsk apprenticePage() {
        return WishAsk.of(HIM, WishSource.TYPED, WishAsk.Magic.APPRENTICE).withText("a fire sword");
    }

    private static WishAsk magician() {
        return WishAsk.of(HIM, WishSource.TYPED, WishAsk.Magic.MAGICIAN).withText("a dragon that breathes ice");
    }

    @Test
    void inPrepEveryNoModelWishIsFreeAndTheRestIsTheCores() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        for (WishAsk a : List.of(fire("apprentice_fire_sword"), make(), apprenticePage(), magician())) {
            assertEquals(Optional.empty(), c.refuses(a), String.valueOf(a));
            assertFalse(c.charge(a, UUID.randomUUID()).refused());
        }
        assertEquals(-1, c.restLeft(fire("apprentice_fire_sword")), "in prep the core keeps its own second");
        assertEquals(5, c.firesLeft(HIM), "prep fires cost nothing");
    }

    @Test
    void inABattleOneLineInTheArenasWordsRefusesANewWishBeforeAnythingOpens() {
        ArenaCharge c = charge(ArenaWishes.DEFAULT);
        c.phase(ArenaWishPolicy.Phase.BATTLE);
        for (WishAsk a : List.of(make(), apprenticePage(), magician(),
                WishAsk.of(HIM, WishSource.TYPED, WishAsk.Magic.REPLAY).withRecipe("apprentice_cozy_house"))) {
            assertEquals(Optional.of(ArenaWords.battleInstantOnly()), c.refuses(a), String.valueOf(a));
        }
    }

    @Test
    void aFireInABattleCostsOneRestsAndTheSweepShowsTheArenasRest() {
        ArenaCharge c = charge(new ArenaWishes(3, 10, 2));
        c.phase(ArenaWishPolicy.Phase.BATTLE);
        WishAsk sword = fire("apprentice_fire_sword");
        assertEquals(0, c.restLeft(sword), "ready before its first fire");
        for (int i = 0; i < 3; i++) {
            assertEquals(Optional.empty(), c.refuses(sword), "asking changes nothing");
        }
        assertEquals(2, c.firesLeft(HIM));
        assertFalse(c.charge(sword, UUID.randomUUID()).refused());
        assertEquals(1, c.firesLeft(HIM));
        assertEquals(200, c.restLeft(sword), "the sweep lasts the arena's 10 seconds (no server: the tick stands)");
        assertEquals(Optional.of(ArenaWords.resting(10)), c.refuses(sword));
        assertEquals(200, c.restLeft(sword), "a refused fire never restarts the rest");

        WishAsk house = fire("builder:cozy_house_1");
        assertEquals(0, c.restLeft(house), "another wish has its own rest");
        assertFalse(c.charge(house, UUID.randomUUID()).refused());
        assertEquals(0, c.firesLeft(HIM));
        WishAsk cat = fire("apprentice_cat");
        assertEquals(Optional.of(ArenaWords.outOfWishes(2)), c.refuses(cat));
        assertEquals("You have used your 2 hotbar wishes for this wave. You get 2 more when the next wave comes.",
                c.refuses(cat).orElseThrow());
        assertEquals(0, c.restLeft(cat), "refused for its count, not its rest: the sweep is cleared");
        assertTrue(c.charge(cat, UUID.randomUUID()).refused(), "two fires at once on the last wish: the second waits");

        c.newWave();
        assertEquals(2, c.firesLeft(HIM), "the next wave");
        assertEquals(Optional.empty(), c.refuses(cat));
        c.battleEnded();
        c.phase(ArenaWishPolicy.Phase.PREP);
        assertEquals(-1, c.restLeft(sword));
    }
}
