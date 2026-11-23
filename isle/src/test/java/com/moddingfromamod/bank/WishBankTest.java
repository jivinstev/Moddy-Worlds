package com.moddingfromamod.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** the wish bank's arithmetic — 10 to start, +1 every 30 minutes of real time, cap 10. */
class WishBankTest {

    static final WishBank.Params P = WishBank.Params.LAUNCH;
    static final long MIN = 60_000L;
    static final long T0 = 1_759_300_000_000L;
    /** What an AGENT wish costs at launch, in halves; a recipe costs {@link #RECIPE}. */
    static final int AGENT = P.priceHalves(true);
    static final int RECIPE = P.priceHalves(false);

    /** Whole wishes, in the halves the bank counts in. */
    static int w(int wishes) {
        return wishes * WishBank.HALVES;
    }

    @Test
    void aNewPlayerStartsFullAndNothingIsComing() {
        WishBank.Account a = WishBank.fresh(P, T0);
        WishBank.Status s = WishBank.status(P, a, T0 + 5 * MIN);
        assertEquals(w(10), s.halves());
        assertTrue(s.full());
        assertEquals(-1L, s.nextAt());
        assertEquals(T0 + 5 * MIN, s.fullAt(), "full now");
    }

    @Test
    void theClockStartsAtTheChargeFromAFullBankEvenIfTheAnchorIsAncient() {
        WishBank.Account a = new WishBank.Account(w(10), T0 - 1000 * MIN);
        WishBank.Account c = WishBank.charge(P, a, AGENT, T0);
        assertEquals(w(9), c.halves());
        assertEquals(T0, c.lastRefillAt(), "the next wish arrives one period after the charge");
        assertEquals(w(9), WishBank.halves(P, c, T0 + 30 * MIN - 1), "one ms early: not yet");
        assertEquals(w(10), WishBank.halves(P, c, T0 + 30 * MIN), "on the dot: here");
    }

    @Test
    void refillIsOneEveryThirtyMinutesOfRealTimeAndCappedAtTen() {
        WishBank.Account a = new WishBank.Account(w(0), T0);
        assertEquals(w(0), WishBank.halves(P, a, T0 + 29 * MIN));
        assertEquals(w(1), WishBank.halves(P, a, T0 + 30 * MIN));
        assertEquals(w(3), WishBank.halves(P, a, T0 + 95 * MIN));
        assertEquals(w(10), WishBank.halves(P, a, T0 + 300 * MIN), "full again 5 h after empty");
        assertEquals(w(10), WishBank.halves(P, a, T0 + 100_000 * MIN), "never above the cap by refill");
        assertEquals(T0 + 300 * MIN, WishBank.fullAt(P, a, T0), "full-at from empty");
        assertEquals(T0 + 30 * MIN, WishBank.nextRefillAt(P, a, T0));
    }

    @Test
    void aPartialPeriodIsKeptAcrossASettle() {
        WishBank.Account a = new WishBank.Account(w(2), T0);
        WishBank.Account s = WishBank.settle(P, a, T0 + 45 * MIN);
        assertEquals(w(3), s.halves());
        assertEquals(T0 + 30 * MIN, s.lastRefillAt(), "the 15 minutes already waited still count");
        assertEquals(T0 + 60 * MIN, WishBank.nextRefillAt(P, s, T0 + 45 * MIN));
    }

    @Test
    void aRestartLosesNothingTheStoredAccountSettledLaterIsTheSame() {
        WishBank.Account a = new WishBank.Account(w(4), T0);
        long later = T0 + 75 * MIN;
        WishBank.Account stepped = WishBank.settle(P, WishBank.settle(P, a, T0 + 40 * MIN), later);
        assertEquals(WishBank.settle(P, a, later), stepped, "settling twice equals settling once");
        assertEquals(w(6), stepped.halves());
    }

    @Test
    void aClockThatGoesBackwardsMintsNothing() {
        WishBank.Account a = new WishBank.Account(w(3), T0);
        WishBank.Account s = WishBank.settle(P, a, T0 - 120 * MIN);
        assertEquals(w(3), s.halves());
        assertEquals(T0 - 120 * MIN, s.lastRefillAt());
        assertEquals(w(4), WishBank.halves(P, s, T0 - 90 * MIN));
    }

    @Test
    void aBonusMayExceedTheCapAndTheClockWaitsWhileItDoes() {
        WishBank.Account a = WishBank.bonus(P, new WishBank.Account(w(10), T0), 3, T0);
        assertEquals(w(13), a.halves());
        assertEquals(w(13), WishBank.halves(P, a, T0 + 600 * MIN), "no refill above the cap, and nothing lost");
        WishBank.Account c = WishBank.charge(P, a, AGENT, T0 + 600 * MIN);
        assertEquals(w(12), c.halves());
        assertEquals(-1L, WishBank.nextRefillAt(P, c, T0 + 600 * MIN), "still at or above the cap");
    }

    @Test
    void aRefundGivesBackButNeverAboveTheCap() {
        WishBank.Account c = WishBank.charge(P, new WishBank.Account(w(10), T0), 1, T0);
        assertEquals(w(10), WishBank.refund(P, c, AGENT, T0 + 5 * MIN).halves());
        assertEquals(w(10), WishBank.refund(P, c, AGENT, T0 + 40 * MIN).halves(),
                "refilled meanwhile: had it never been charged it would be 10, not 11");
        WishBank.Account over = new WishBank.Account(w(12), T0);
        assertEquals(w(12), WishBank.refund(P, over, AGENT, T0).halves(), "a bonus balance keeps what it had");
    }

    @Test
    void anEmptyBankCannotBeChargedAndSaysWhenTheNextArrives() {
        WishBank.Account a = new WishBank.Account(w(0), T0);
        assertFalse(WishBank.canAfford(P, a, T0 + 12 * MIN, AGENT));
        assertThrows(IllegalStateException.class, () -> WishBank.charge(P, a, AGENT, T0));
        WishBank.Status s = WishBank.status(P, a, T0 + 12 * MIN);
        assertTrue(s.empty());
        assertEquals(18 * MIN, s.untilNext());
    }

    @Test
    void paramsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new WishBank.Params(11, 30, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new WishBank.Params(1, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new WishBank.Params(1, 30, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new WishBank.Params(1, 30, 10, 0));
    }

    // ── #1785: HALF A CHARGE — a wish with no model (a recipe, an "again", a Builder make) ────────

    @Test
    void aWishWithNoModelCostsHalfAndAnAgentWishTheWholePrice() {
        assertEquals(2, AGENT);
        assertEquals(1, RECIPE);
        WishBank.Account a = WishBank.charge(P, WishBank.fresh(P, T0), RECIPE, T0);
        assertEquals(19, a.halves(), "9½ left");
        WishBank.Account b = WishBank.charge(P, a, AGENT, T0);
        assertEquals(17, b.halves(), "8½ left");
    }

    @Test
    void halfAWishLeftPaysForARecipeButNotForTheAgent() {
        WishBank.Account half = new WishBank.Account(1, T0);
        assertTrue(WishBank.canAfford(P, half, T0, RECIPE));
        assertFalse(WishBank.canAfford(P, half, T0, AGENT));
        WishBank.Status s = WishBank.status(P, half, T0);
        assertTrue(s.empty(), "empty for a new wish");
        assertFalse(s.emptyEvenForARecipe(), "but a ready-made one still fits");
    }

    @Test
    void theRefillStaysOneWholeWishAPeriodAndKeepsTheHalf() {
        WishBank.Account a = new WishBank.Account(w(7) + 1, T0);           // 7½
        assertEquals(w(8) + 1, WishBank.halves(P, a, T0 + 30 * MIN), "8½ after one period");
        assertEquals(w(10), WishBank.halves(P, a, T0 + 90 * MIN), "never past the cap: 10, not 10½");
        assertEquals(T0 + 90 * MIN, WishBank.fullAt(P, a, T0), "2½ to go is three periods, rounded up");
    }

    @Test
    void aBonusIsInWholeWishes() {
        assertEquals(w(12) + 1, WishBank.bonus(P, new WishBank.Account(w(9) + 1, T0), 3, T0).halves());
    }
}
