package com.moddingfromamod.bank;

/**
 * <b>The wish bank's arithmetic</b> — pure, no Minecraft, no clock of its own: every
 * method takes {@code now}, so every edge (a refill landing on the millisecond, a server that was
 * down for a day, a clock that stepped backwards) is a Gate A test ({@code WishBankTest}).
 *
 * <h2>The rule (the owner's decision)</h2>
 * One balance per player per world-with-a-bank: <b>{@code start} to begin with, +1 every
 * {@code refillMinutes} of REAL time (played or not), capped at {@code cap}</b>; a wish costs
 * {@code price}. A bonus (milestones, advancements) may take the balance ABOVE the cap; while
 * it is at or above the cap the refill clock does not run, and it starts again from the moment a
 * wish takes it below.
 *
 * <h2>Lazy, so there is no tick work</h2>
 * What is stored is an {@link Account} — a balance and the moment the refill clock was last
 * anchored. Everything else ({@link #settle}, {@link #nextRefillAt}, {@link #fullAt}) is computed
 * from it on demand, so a server with a thousand banked players does nothing per tick, and a
 * restart loses nothing: the stored account, settled against the real clock, is exactly what it
 * would have been had the server never stopped.
 */
public final class WishBank {

    private WishBank() {
    }

    /**
     * HALF A CHARGE (#1785, the owner's decision): the bank counts in HALVES.
     * A wish the AGENT grants costs {@code price}; a wish served with no model — a pressed recipe, a
     * Builder make, every "again" — costs half of it. The rules file still speaks in whole charges
     * ({@code start}, {@code cap}, {@code price}); only the arithmetic is in halves.
     */
    public static final int HALVES = 2;

    /** The world's bank, as its rules file declares it ({@code wish_bank}). */
    public record Params(int start, int refillMinutes, int cap, int price) {

        /** The launch values: 10 to start, +1 every 30 minutes, cap 10, 1 a wish. */
        public static final Params LAUNCH = new Params(10, 30, 10, 1);

        /**
         * This world's bank as its rules declare it ({@code wish_bank}), or null when it has none — a plain world,
         * and every world whose file declares no {@code wish_bank}, where wishing is exactly what it was before.
         * (Read here, not in the core's {@code WorldRules}, since #2360.)
         */
        public static Params of(com.moddingfromamod.world.WorldRules rules) {
            com.moddingfromamod.world.WorldRule r = rules == null ? null
                    : rules.first(com.moddingfromamod.world.WorldRule.Type.WISH_BANK);
            return r == null ? null : new Params(r.integer("start", 10), r.integer("refillMinutes", 30),
                    r.integer("cap", 10), r.integer("pricePerWish", 1));
        }

        public Params {
            if (cap < 1 || start < 0 || start > cap || refillMinutes < 1 || price < 1) {
                throw new IllegalArgumentException("a wish bank needs cap ≥ 1, 0 ≤ start ≤ cap, "
                        + "refillMinutes ≥ 1 and price ≥ 1: " + start + "/" + refillMinutes + "/" + cap + "/" + price);
            }
        }

        public long periodMs() {
            return refillMinutes * 60_000L;
        }

        public int capHalves() {
            return cap * HALVES;
        }

        public int startHalves() {
            return start * HALVES;
        }

        /** What a wish costs, in halves: the full price when the agent grants it, half when no model does. */
        public int priceHalves(boolean agent) {
            return agent ? price * HALVES : price;
        }
    }

    /**
     * What is stored: the balance, and when the refill clock was last anchored. The anchor means
     * "the next wish arrives one period after this", and is meaningless (and kept at the last
     * settle) while the balance is at or above the cap.
     */
    public record Account(int halves, long lastRefillAt) {
    }

    /** A new player's account: {@code start} wishes, the clock anchored now. */
    public static Account fresh(Params p, long now) {
        return new Account(p.startHalves(), now);
    }

    /**
     * The account as it stands at {@code now}: every whole period since the anchor has added one
     * wish, up to the cap (a full account is returned as it is — its clock is not running). A clock that went BACKWARDS (an NTP step, a restored VM) adds nothing and
     * re-anchors at {@code now} — it may delay a wish by the size of the step, and never mints one.
     */
    public static Account settle(Params p, Account a, long now) {
        if (a.halves() >= p.capHalves()) {
            return a;       // full: the clock is not running, and its anchor is re-set by the next charge
        }
        if (now < a.lastRefillAt()) {
            return new Account(a.halves(), now);
        }
        long period = p.periodMs();
        long n = (now - a.lastRefillAt()) / period;
        if (n <= 0) {
            return a;
        }
        long b = a.halves() + n * HALVES;      // one whole wish a period, whatever half is left over
        if (b >= p.capHalves()) {
            return new Account(p.capHalves(), now);
        }
        return new Account((int) b, a.lastRefillAt() + n * period);
    }

    /** The balance at {@code now}, in halves. */
    public static int halves(Params p, Account a, long now) {
        return settle(p, a, now).halves();
    }

    /** When the next wish arrives, or {@code -1} when the bank is full (nothing is coming). */
    public static long nextRefillAt(Params p, Account a, long now) {
        Account s = settle(p, a, now);
        return s.halves() >= p.capHalves() ? -1L : s.lastRefillAt() + p.periodMs();
    }

    /** When the bank is full again ({@code now} when it already is). */
    public static long fullAt(Params p, Account a, long now) {
        Account s = settle(p, a, now);
        if (s.halves() >= p.capHalves()) {
            return now;
        }
        long periods = (p.capHalves() - s.halves() + HALVES - 1) / HALVES;
        return s.lastRefillAt() + periods * p.periodMs();
    }

    /** Can a wish costing {@code priceHalves} be paid for now? */
    public static boolean canAfford(Params p, Account a, long now, int priceHalves) {
        return settle(p, a, now).halves() >= priceHalves;
    }

    /**
     * Take {@code price}. Settled first, so a charge from a full bank anchors the clock at the
     * charge: the next wish arrives one period after it. Throws when it cannot be afforded — the
     * caller asks {@link #canAfford} and refuses kindly instead.
     */
    public static Account charge(Params p, Account a, int priceHalves, long now) {
        Account s = settle(p, a, now);
        if (s.halves() < priceHalves) {
            throw new IllegalStateException("cannot charge " + priceHalves + " halves from " + s.halves());
        }
        // From a full bank the clock STARTS at the charge: its stored anchor is stale by design.
        long anchor = s.halves() >= p.capHalves() ? now : s.lastRefillAt();
        return new Account(s.halves() - priceHalves, anchor);
    }

    /**
     * Give a charge back (the wish failed or was stopped). Never above the cap: had the wish never
     * been charged, the refill would have stopped AT the cap, so a refund that went over it would
     * be a wish the child never had. A balance already above the cap (a bonus) keeps what it had.
     */
    public static Account refund(Params p, Account a, int amountHalves, long now) {
        Account s = settle(p, a, now);
        int b = Math.max(s.halves(), Math.min(p.capHalves(), s.halves() + Math.max(0, amountHalves)));
        return new Account(b, s.lastRefillAt());
    }

    /** A bonus (a milestone, an advancement), in whole wishes: may take the balance ABOVE the cap. */
    public static Account bonus(Params p, Account a, int wishes, long now) {
        Account s = settle(p, a, now);
        return new Account(s.halves() + Math.max(0, wishes) * HALVES, s.lastRefillAt());
    }

    /**
     * Everything a sentence about the bank needs, at {@code now}. {@code halves} is the balance and
     * {@code priceHalves} an AGENT wish's price, both in halves; {@code cap} is in whole wishes, as said.
     */
    public record Status(int halves, int cap, int priceHalves, int refillMinutes, long now, long nextAt,
                         long fullAt) {

        public boolean full() {
            return halves >= cap * HALVES;
        }

        /** Not enough for a wish the agent grants. */
        public boolean empty() {
            return halves < priceHalves;
        }

        /** Not enough even for a wish with no model (a recipe, an "again") — half the price. */
        public boolean emptyEvenForARecipe() {
            return halves < Math.max(1, priceHalves / HALVES);
        }

        /** Milliseconds until the next wish ({@code 0} when full). */
        public long untilNext() {
            return nextAt < 0 ? 0L : Math.max(0L, nextAt - now);
        }

        /** Milliseconds until full ({@code 0} when full). */
        public long untilFull() {
            return Math.max(0L, fullAt - now);
        }
    }

    public static Status status(Params p, Account a, long now) {
        Account s = settle(p, a, now);
        return new Status(s.halves(), p.cap(), p.priceHalves(true), p.refillMinutes(), now,
                nextRefillAt(p, s, now), fullAt(p, s, now));
    }
}
