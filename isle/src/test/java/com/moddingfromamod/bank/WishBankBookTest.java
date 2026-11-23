package com.moddingfromamod.bank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * the bank's store and the moments of a wish's life — charged once at pickup, free on a
 * re-wish of the same id, refunded once, and restored after a restart.
 */
class WishBankBookTest {

    static final WishBank.Params P = WishBank.Params.LAUNCH;
    static final long MIN = 60_000L;
    static final long T0 = 1_759_300_000_000L;
    static final UUID KID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @TempDir
    Path dir;

    WishBankBook book() {
        return new WishBankBook(new WishBankStore(dir.resolve("wishes").resolve(WishBankStore.FILE)));
    }

    @Test
    void aFirstVisitOpensTheAccountWithTenAndSaysSo() {
        WishBankBook b = book();
        WishBankBook.Joined j = b.join(P, KID, T0);
        assertEquals(WishBankBook.Arrival.FIRST_VISIT, j.arrival());
        assertEquals(20, j.status().halves());
        assertEquals(WishBankBook.Arrival.NOTHING, b.join(P, KID, T0 + MIN).arrival());
    }

    @Test
    void chargedOnceAtPickupAndAReWishOfTheSameIdIsFree() {
        WishBankBook b = book();
        UUID wish = UUID.randomUUID();
        WishBankBook.Pickup first = b.pickup(P, KID, wish, T0);
        assertEquals(WishBankBook.Paid.CHARGED, first.paid());
        assertEquals(18, first.status().halves());
        WishBankBook.Pickup again = b.pickup(P, KID, wish, T0 + MIN);
        assertEquals(WishBankBook.Paid.ALREADY_PAID, again.paid(), "/moddy no reuses the wish id: free");
        assertEquals(18, again.status().halves());
        assertTrue(b.charged(KID, wish));
    }

    @Test
    void anEmptyBankRefusesAtPickupAndChargesNothing() {
        WishBankBook b = book();
        for (int i = 0; i < 10; i++) {
            assertEquals(WishBankBook.Paid.CHARGED, b.pickup(P, KID, UUID.randomUUID(), T0).paid());
        }
        assertFalse(b.affordable(P, KID, T0 + MIN));
        WishBankBook.Pickup p = b.pickup(P, KID, UUID.randomUUID(), T0 + MIN);
        assertTrue(p.refuses());
        assertEquals(0, p.status().halves());
        assertEquals(29 * MIN, p.status().untilNext());
        assertTrue(b.affordable(P, KID, T0 + 30 * MIN), "one more arrives 30 minutes after the first charge");
    }

    @Test
    void aFailureIsRefundedOnceAndAnUnchargedWishGetsNothing() {
        WishBankBook b = book();
        UUID wish = UUID.randomUUID();
        b.pickup(P, KID, wish, T0);
        WishBank.Status back = b.refund(P, KID, wish, T0 + MIN);
        assertEquals(20, back.halves());
        assertNull(b.refund(P, KID, wish, T0 + 2 * MIN), "a refund happens once");
        assertNull(b.refund(P, KID, UUID.randomUUID(), T0), "a wish never charged (a refusal) gets nothing");
        assertEquals(WishBankBook.Paid.CHARGED, b.pickup(P, KID, wish, T0 + 3 * MIN).paid(),
                "re-opened after a refund (a re-aim): charged again — one charge in all");
    }

    @Test
    void aWaivedWishIsNeverCharged() {
        WishBankBook b = book();
        for (int i = 0; i < 10; i++) {
            b.pickup(P, KID, UUID.randomUUID(), T0);
        }
        UUID wish = UUID.randomUUID();
        b.waive(P, KID, wish, T0);
        assertEquals(WishBankBook.Paid.WAIVED, b.pickup(P, KID, wish, T0).paid());
    }

    @Test
    void aRestartRestoresTheBankAndTheRefillWhileAwayIsSaidOnJoin() throws Exception {
        WishBankBook b = book();
        b.join(P, KID, T0);
        UUID w = UUID.randomUUID();
        b.pickup(P, KID, w, T0);
        b.pickup(P, KID, UUID.randomUUID(), T0);
        b.leave(P, KID, T0 + 10 * MIN);
        WishBankBook after = book();       // a fresh read of the same file: the server restarted
        assertEquals(16, after.status(P, KID, T0 + 10 * MIN).halves());
        assertTrue(after.charged(KID, w), "the ledger survives, so a refund after a restart still happens");
        WishBankBook.Joined j = after.join(P, KID, T0 + 70 * MIN);
        assertEquals(WishBankBook.Arrival.REFILLED, j.arrival());
        assertEquals(20, j.status().halves());
        String text = Files.readString(dir.resolve("wishes").resolve(WishBankStore.FILE));
        assertTrue(text.contains("\"store\":\"wish-bank\"") && text.contains("\"v\":1")
                && text.contains("\"ownerId\":\"" + KID + "\"") && text.contains("\"params\""), text);
    }

    @Test
    void anUnreadableStoreIsRefusedNeverOverwrittenAndWishesAreFree() throws Exception {
        Path f = dir.resolve("wishes").resolve(WishBankStore.FILE);
        Files.createDirectories(f.getParent());
        String damaged = "{\"store\":\"wish-bank\",\"v\":99,\"players\":[]}";
        Files.writeString(f, damaged);
        WishBankBook b = book();
        assertTrue(b.affordable(P, KID, T0), "fail open: a file he cannot see never refuses him");
        assertEquals(WishBankBook.Paid.UNREADABLE, b.pickup(P, KID, UUID.randomUUID(), T0).paid());
        b.join(P, KID, T0);
        assertEquals(damaged, Files.readString(f), "a refused store is never written over");
    }

    @Test
    void aBonusGoesAboveTheCap() {
        WishBankBook b = book();
        assertEquals(26, b.bonus(P, KID, 3, T0).halves());
    }

    /** a question costs nothing — "what do I do now?" must never be priced. */
    @Test
    void aQuestionIsFree_aRequestIsNot() {
        for (String q : new String[]{"what do I do now?", "how do I get sand?", "How do I make a cobblestone generator",
                "what is the void", "tell me how to get iron", "where do I find water?"}) {
            assertTrue(WishBankBook.free(q), "a question was priced: " + q);
        }
        for (String w : new String[]{"make me a castle", "can you give me diamonds?", "help me build a cobblestone generator",
                "help me find food on my island", "", "   "}) {
            assertFalse(WishBankBook.free(w), "a request went free: " + w);
        }
        assertFalse(WishBankBook.free(null));
    }

    // ── #1785: half a charge, and a file an OLD jar can still read ───────────────────────────────

    @Test
    void aRecipeIsChargedHalfAndTheHalfIsSavedBesideTheWholeWishes() throws Exception {
        WishBankBook b = book();
        b.join(P, KID, T0);
        UUID recipe = UUID.randomUUID();
        WishBankBook.Pickup paid = b.pickup(P, KID, recipe, T0, false);
        assertEquals(WishBankBook.Paid.CHARGED, paid.paid());
        assertEquals(1, paid.price(), "half a wish");
        assertEquals(19, paid.status().halves());
        String text = Files.readString(dir.resolve("wishes").resolve(WishBankStore.FILE));
        // balance stays WHOLE wishes so a pre-#1785 jar reads 9; the half rides beside it
        assertTrue(text.contains("\"balance\":9") && text.contains("\"half\":true"), text);
        assertTrue(text.contains("\"price\":0") && text.contains("\"halves\":1"), text);
        assertTrue(text.contains("\"v\":1"), "no schema bump: an old reader still reads it — " + text);
        WishBankBook after = book();
        assertEquals(19, after.status(P, KID, T0).halves(), "the half survives a restart");
        assertNotNull(after.refund(P, KID, recipe, T0), "and a half charge is refunded");
        assertEquals(20, after.status(P, KID, T0).halves(), "…by half");
    }

    @Test
    void aFileWrittenBeforeHalvesReadsAsItDid() throws Exception {
        Path f = dir.resolve("wishes").resolve(WishBankStore.FILE);
        Files.createDirectories(f.getParent());
        UUID w = UUID.randomUUID();
        Files.writeString(f, "{\"store\":\"wish-bank\",\"v\":1,\"players\":[{\"ownerId\":\"" + KID
                + "\",\"balance\":7,\"lastRefillAt\":" + T0 + ",\"ledger\":[{\"wish\":\"" + w
                + "\",\"price\":1,\"state\":\"charged\",\"at\":" + T0 + "}]}]}");
        WishBankBook b = book();
        assertEquals(14, b.status(P, KID, T0).halves(), "7 whole wishes");
        assertEquals(16, b.refund(P, KID, w, T0).halves(), "an old whole charge refunds a whole wish");
    }

    @Test
    void halfAWishLeftStillPaysForARecipeButNotTheAgent() {
        WishBankBook b = book();
        b.join(P, KID, T0);
        for (int i = 0; i < 19; i++) {
            b.pickup(P, KID, UUID.randomUUID(), T0, false);
        }
        assertEquals(1, b.status(P, KID, T0).halves());
        assertTrue(b.affordable(P, KID, T0, false));
        assertFalse(b.affordable(P, KID, T0, true));
        assertEquals(WishBankBook.Paid.EMPTY, b.pickup(P, KID, UUID.randomUUID(), T0, true).paid());
        assertEquals(WishBankBook.Paid.CHARGED, b.pickup(P, KID, UUID.randomUUID(), T0, false).paid());
    }
}
