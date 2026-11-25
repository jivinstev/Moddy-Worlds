package com.moddingfromamod.bank;

import java.util.UUID;

/**
 * <b>What the bank DOES at each moment of a wish's life</b> — the store plus the arithmetic,
 * still Minecraft-free, so the charge/refund/free-re-wish rules are Gate A tests
 * ({@code WishBankBookTest}) and {@link WishBankService} only carries them out.
 *
 * <pre>
 *   submit   balance &lt; price                         → refuse kindly, nothing spent ({@link #affordable})
 *   submit   a question ({@link #free})                → never refused, never charged
 *   pickup   (after the safety and world-rules gates)
 *            a question                                → QUESTION: free
 *            the wish was already charged              → FREE (a /moddy redirect re-wish:
 *                                                       the same wish id, Moddy fixing his own work)
 *            waived at submit                          → FREE
 *            balance &lt; price (a race since submit)   → EMPTY: refused kindly, nothing spent
 *            else                                      → CHARGED
 *   settle   a charged wish ends not-granted          → REFUNDED, once
 *            (failed, stopped, timed out, rejected after the charge)
 *   undo     👎 / /moddy no on a still-CHARGED wish     → REFUNDED, once (#1546, even after DONE)
 *   join     first visit                               → the account opens with start
 *            the refill gave him something meanwhile  → "welcome back"
 *   leave    the balance at leaving is written, so the next join can tell what refilled
 * </pre>
 *
 * A store that could not be read (its latch is closed) FAILS OPEN: the wish is free and the event is
 * logged as {@code MODDY-ALERT}, because the alternative is every child on the world refused for a
 * file he cannot see; the server-wide spend guard is what bounds a world in that state.
 */
public final class WishBankBook {

    private final WishBankStore store;

    public WishBankBook(WishBankStore store) {
        this.store = store;
    }

    public WishBankStore store() {
        return store;
    }

    private WishBankStore.Entry entry(WishBank.Params p, UUID player, long now) {
        WishBankStore.Entry e = store.get(player);
        return e != null ? e : new WishBankStore.Entry(WishBank.fresh(p, now), java.util.List.of());
    }

    /** The bank as he sees it now (no write). */
    public WishBank.Status status(WishBank.Params p, UUID player, long now) {
        return WishBank.status(p, entry(p, player, now).account(), now);
    }

    /** At submit: can he pay for a wish? True when the store is unreadable (fail open). */
    /** Can this player pay for a wish the AGENT grants ({@code agent}) or one no model does (half)? */
    public boolean affordable(WishBank.Params p, UUID player, long now, boolean agent) {
        return !store.usable() || WishBank.canAfford(p, entry(p, player, now).account(), now,
                p.priceHalves(agent));
    }

    /** {@link #affordable(WishBank.Params, UUID, long, boolean)} for a wish the agent grants. */
    public boolean affordable(WishBank.Params p, UUID player, long now) {
        return affordable(p, player, now, true);
    }

    /** What pickup did. */
    public enum Paid { CHARGED, ALREADY_PAID, WAIVED, EMPTY, UNREADABLE, QUESTION }

    /**
     * <b>A question costs nothing</b> (the owner's decision). "What do I do now?" is the
     * question a child stuck on a sky island most needs answered, and a bank that charged for it
     * would teach him not to ask. A question ({@code WishIntent.isQuestion}) is answered with words
     * only — the agent is told not to build or change the world ({@code WishIntent.noteForAgent}) —
     * so it is never refused at submit for an empty bank and never charged at pickup. A sentence
     * with an action verb in it ("can you GIVE me diamonds?") is an action, not a question, and is
     * priced like any wish. Pure.
     */
    public static boolean free(String wishText) {
        return wishText != null && com.moddingfromamod.wish.WishIntent.isQuestion(wishText);
    }

    public record Pickup(Paid paid, int price, WishBank.Status status) {

        /** True only when the wish must not start. */
        public boolean refuses() {
            return paid == Paid.EMPTY;
        }
    }

    /** At pickup, after the safety and world-rules gates. */
    /** {@link #pickup(WishBank.Params, UUID, UUID, long, boolean)} for a wish the agent grants. */
    public synchronized Pickup pickup(WishBank.Params p, UUID player, UUID wish, long now) {
        return pickup(p, player, wish, now, true);
    }

    /** Charge {@code wish}: the full price when the agent grants it, half when no model does (#1785). */
    public synchronized Pickup pickup(WishBank.Params p, UUID player, UUID wish, long now, boolean agent) {
        int price = p.priceHalves(agent);
        if (!store.usable()) {
            return new Pickup(Paid.UNREADABLE, 0, WishBank.status(p, WishBank.fresh(p, now), now));
        }
        WishBankStore.Entry e = entry(p, player, now);
        WishBankStore.Ledger l = e.find(wish);
        if (l != null && l.state() == WishBankStore.State.CHARGED) {
            return new Pickup(Paid.ALREADY_PAID, 0, WishBank.status(p, e.account(), now));
        }
        if (l != null && l.state() == WishBankStore.State.WAIVED) {
            return new Pickup(Paid.WAIVED, 0, WishBank.status(p, e.account(), now));
        }
        if (!WishBank.canAfford(p, e.account(), now, price)) {
            return new Pickup(Paid.EMPTY, 0, WishBank.status(p, e.account(), now));
        }
        WishBank.Account a = WishBank.charge(p, e.account(), price, now);
        if (!store.put(player, e.with(a, new WishBankStore.Ledger(wish, price, WishBankStore.State.CHARGED, now)), p)) {
            return new Pickup(Paid.UNREADABLE, 0, WishBank.status(p, e.account(), now));
        }
        return new Pickup(Paid.CHARGED, price, WishBank.status(p, a, now));
    }

    /**
     * A wish let through on an empty bank, never charged. Core 1.0.10 no longer asks for one; a WAIVED entry in a
     * saved ledger still reads.
     */
    public synchronized void waive(WishBank.Params p, UUID player, UUID wish, long now) {
        if (!store.usable()) {
            return;
        }
        WishBankStore.Entry e = entry(p, player, now);
        store.put(player, e.with(WishBank.settle(p, e.account(), now),
                new WishBankStore.Ledger(wish, 0, WishBankStore.State.WAIVED, now)), p);
    }

    /** Was this wish charged (and not given back)? */
    public synchronized boolean charged(UUID player, UUID wish) {
        WishBankStore.Entry e = store.get(player);
        WishBankStore.Ledger l = e == null ? null : e.find(wish);
        return l != null && l.state() == WishBankStore.State.CHARGED;
    }

    /**
     * A charged wish ended without being granted: give the charge back, once. Returns the bank
     * after the refund, or null when there was nothing to give back.
     */
    public synchronized WishBank.Status refund(WishBank.Params p, UUID player, UUID wish, long now) {
        WishBankStore.Entry e = store.get(player);
        WishBankStore.Ledger l = e == null ? null : e.find(wish);
        if (l == null || l.state() != WishBankStore.State.CHARGED) {
            return null;
        }
        WishBank.Account a = WishBank.refund(p, e.account(), l.price(), now);
        if (!store.put(player, e.with(a, new WishBankStore.Ledger(wish, l.price(), WishBankStore.State.REFUNDED, now)), p)) {
            return null;
        }
        return WishBank.status(p, a, now);
    }

    /** A bonus above the cap (milestones and advancements call this). */
    public synchronized WishBank.Status bonus(WishBank.Params p, UUID player, int amount, long now) {
        WishBankStore.Entry e = entry(p, player, now);
        WishBank.Account a = WishBank.bonus(p, e.account(), amount, now);
        store.put(player, e.with(a, null), p);
        return WishBank.status(p, a, now);
    }

    /** What a join should say. */
    public enum Arrival { FIRST_VISIT, REFILLED, NOTHING }

    public record Joined(Arrival arrival, WishBank.Status status) {
    }

    /** On join: open the account on a first visit; say so when the refill gave him something. */
    public synchronized Joined join(WishBank.Params p, UUID player, long now) {
        WishBankStore.Entry e = store.get(player);
        if (e == null) {
            WishBankStore.Entry fresh = entry(p, player, now);
            store.put(player, fresh, p);
            return new Joined(Arrival.FIRST_VISIT, WishBank.status(p, fresh.account(), now));
        }
        WishBank.Account s = WishBank.settle(p, e.account(), now);
        boolean refilled = s.halves() > e.account().halves();
        if (refilled) {
            store.put(player, e.with(s, null), p);
        }
        return new Joined(refilled ? Arrival.REFILLED : Arrival.NOTHING, WishBank.status(p, s, now));
    }

    /** On leaving: write the balance as it stands, so the next join can tell what refilled. */
    public synchronized void leave(WishBank.Params p, UUID player, long now) {
        WishBankStore.Entry e = store.get(player);
        if (e == null) {
            return;
        }
        WishBank.Account s = WishBank.settle(p, e.account(), now);
        if (!s.equals(e.account())) {
            store.put(player, e.with(s, null), p);
        }
    }
}
