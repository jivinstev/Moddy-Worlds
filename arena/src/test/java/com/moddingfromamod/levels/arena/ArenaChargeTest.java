package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.wish.Wish;
import com.moddingfromamod.wish.WishCharge;
import com.moddingfromamod.wish.WishSource;
import com.moddingfromamod.wish.WishStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The arena's charge through the core's own {@link WishCharge} methods, with stand-in wishes of the Magician kind (a
 * typed wish, as the core hands it to {@code atPickup} when the Magician picks it up). No model is called and no real
 * Magician wish is made: this drives the hooks the core calls, in the order it calls them, with the core's own
 * {@link Wish} records. {@code refusesAtSubmit} takes a real player, so it is proven in the playtests.
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
    void aWishFromAChildInDistressIsNeverRefusedNorCounted() {
        ArenaCharge c = charge(new ArenaWishes(0, 10));
        c.phase(ArenaWishPolicy.Phase.BATTLE);
        Wish w = typed();
        c.waiveIfEmpty(null, w.id());
        assertFalse(c.atPickup(w).refused());
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
                "Builder and Apprentice wishes are free. Add your things to your hotbar from My wishes, then fire them"
                        + " in a battle."), c.wishesCommand(HIM));
        assertEquals("Moddy - 2 Magician wishes left", c.panelTitle("Moddy", HIM));
        assertEquals("Wish - 3 Magician wishes left", c.composeTitle("Wish", UUID.randomUUID()));
        c.phase(ArenaWishPolicy.Phase.BATTLE);
        assertEquals(List.of("A battle is on: fire your wishes from your hotbar. Each one rests 10 seconds after you"
                + " use it.", "New wishes are made in the prep room before a battle."), c.wishesCommand(HIM));
        assertEquals("Moddy - Battle: use your hotbar", c.panelTitle("Moddy", HIM));
    }
}
