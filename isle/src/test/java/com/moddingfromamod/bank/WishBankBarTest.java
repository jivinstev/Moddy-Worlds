package com.moddingfromamod.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** the standing boss bar — who sees it, what it says, how full it is. */
class WishBankBarTest {

    static final long MIN = 60_000L;
    static final long NOW = 1_759_300_000_000L;

    static WishBank.Status at(WishBank.Params p, int balance, long untilNextMin) {
        long anchor = NOW - (p.refillMinutes() - untilNextMin) * MIN;
        return WishBank.status(p, new WishBank.Account(balance * WishBank.HALVES, anchor), NOW);
    }

    @Test
    void onlyAChildOnAWorldWithABankSeesIt() {
        assertTrue(WishBankBar.shows(true, true));
        assertFalse(WishBankBar.shows(false, true), "a plain world never shows one");
        assertFalse(WishBankBar.shows(true, false), "a camera never carries one");
    }

    @Test
    void itSaysTheCountAndFillsToBalanceOverCapInTenNotches() {
        WishBankBar.Shape s = WishBankBar.of(at(WishBank.Params.LAUNCH, 7, 12));
        assertEquals("✨ 7/10 wishes available · +1 in 12m", s.name());
        assertEquals(0.7f, s.progress(), 1e-6);
        assertEquals(10, s.notches(), "one notch per wish at the launch cap");
        WishBankBar.Shape full = WishBankBar.of(at(WishBank.Params.LAUNCH, 10, 30));
        assertEquals("✨ 10/10 wishes available", full.name());
        assertEquals(1f, full.progress());
        assertEquals(0f, WishBankBar.of(at(WishBank.Params.LAUNCH, 0, 30)).progress());
    }

    @Test
    void aBonusAboveTheCapIsAFullBarNeverAnOverflow() {
        WishBankBar.Shape s = WishBankBar.of(at(WishBank.Params.LAUNCH, 13, 30));
        assertEquals(1f, s.progress());
        assertEquals("✨ 13/10 wishes available", s.name());
    }

    @Test
    void anOddCapIsAPlainBar() {
        assertEquals(0, WishBankBar.of(at(new WishBank.Params(5, 30, 7, 1), 5, 30)).notches());
        assertEquals(20, WishBankBar.notches(20));
        assertEquals(6, WishBankBar.notches(6));
        assertEquals(12, WishBankBar.notches(12));
    }
}
