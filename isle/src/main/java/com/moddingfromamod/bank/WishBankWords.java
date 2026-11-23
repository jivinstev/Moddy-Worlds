package com.moddingfromamod.bank;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Every sentence the wish bank says</b> — pure, so each is a Gate A test, and every one
 * passes the safety lexicon there ({@code WishBankWordsTest}). The owner asked for the refill to be
 * CLEAR, so each line says the three things a child wants: how many he has, when the next comes,
 * and when he is full again.
 *
 * <p>Times are RELATIVE ("in 18 minutes", "in about 2 hours"): a stock client does not tell the
 * server its time zone, and a clock time in the server's zone would be wrong for a stranger.
 */
public final class WishBankWords {

    private WishBankWords() {
    }

    /** The watch's name in the hotbar: {@code Moddy · ✨ 7/10 · +1 in 12m} (#1550: "+1", not "next" — next what?). */
    /**
     * #1785 step 5: with a title — {@code "Moddy · ✦✦ Skilled Crafter · ✨ 4/10 · +1 in 12m"}. A blank
     * title (the door shut, no bank) is exactly {@link #watchName(WishBank.Status)}.
     */
    public static String watchName(WishBank.Status s, String title) {
        String plain = watchName(s);
        return title == null || title.isBlank() ? plain : plain.replaceFirst("^Moddy · ", "Moddy · " + title + " · ");
    }

    public static String watchName(WishBank.Status s) {
        return "Moddy · ✨ " + amount(s.halves()) + "/" + s.cap() + (s.full() ? "" : " · +1 in " + compact(s.untilNext()));
    }

    /**
     * <b>The standing line</b>: the per-player boss bar a child sees the whole time he is on
     * a world with a bank — {@code ✨ 7/10 wishes available · +1 in 12m}, and {@code ✨ 10/10 wishes available} when
     * full (nothing is coming, so nothing is promised).
     */
    public static String standing(WishBank.Status s) {
        return "✨ " + amount(s.halves()) + "/" + s.cap() + " wishes available" + (s.full() ? "" : " · +1 in " + compact(s.untilNext()));
    }

    /**
     * The watch panel's chest title: {@code ✨ Moddy · ✨ 7/10}. Short on purpose — a chest
     * title is ~160 px wide. With no bank the panel keeps its own title.
     */
    public static String panelTitle(String base, WishBank.Status s) {
        return s == null ? base : "✨ Moddy · ✨ " + amount(s.halves()) + "/" + s.cap();
    }

    /** Said after a granted wish. */
    public static String afterWish(WishBank.Status s, String place) {
        if (s.emptyEvenForARecipe()) {
            return "✨ That was your last wish for now. " + empty(s, place);
        }
        if (s.full()) {
            return "✨ " + wishes(s.halves()) + " left — your wish bank is full.";
        }
        return "✨ " + wishes(s.halves()) + " left — one more every " + minutes(s.refillMinutes())
                + ", full again in " + span(s.untilFull()) + ".";
    }

    /** Said when there is nothing to pay with: at submit (no worker spent), or at pickup. */
    public static String empty(WishBank.Status s, String place) {
        return "Your next wish arrives in " + span(s.untilNext()) + ". Want to explore your "
                + (place == null || place.isBlank() ? "world" : place) + " meanwhile?";
    }

    /** Said on join, when the refill gave him something while he was away. */
    public static String welcomeBack(WishBank.Status s) {
        return "Welcome back! ✨ Your wishes refilled while you were away — " + amount(s.halves()) + "/" + s.cap() + ".";
    }

    /** Said on a player's FIRST join to a world with a bank: the rule in one sentence. */
    public static String firstVisit(WishBank.Status s) {
        return "✨ You have " + wishes(s.halves()) + " here! A new wish uses " + wishes(s.priceHalves())
                + ", a ready-made one " + wishes(s.priceHalves() / WishBank.HALVES)
                + ", and a new one arrives every " + minutes(s.refillMinutes()) + " (up to " + s.cap() + ").";
    }

    /** Said when a charged wish ends without being granted. */
    public static String refunded(WishBank.Status s, boolean stopped) {
        return (stopped ? "You stopped that wish, so you keep it" : "That wish didn't work out, so you get it back")
                + " — ✨ " + amount(s.halves()) + "/" + s.cap() + ".";
    }

    /** The bank in two glyphs, {@code ✨ 7/10} — the tail of the 👎 undo line (#1546). */
    public static String balance(WishBank.Status s) {
        return "✨ " + amount(s.halves()) + "/" + s.cap();
    }

    /** The progress bar's suffix while a wish is being granted. */
    /** The price before a press, in HALVES: {@code " · uses ½ ✨"}. */
    public static String barCost(int priceHalves) {
        return " · uses " + amount(priceHalves) + " ✨";
    }

    /** The wish box's title (wish/WishCompose). */
    public static String composeTitle(String base, WishBank.Status s) {
        return base + " · " + amount(s.halves()) + "/" + s.cap() + " left";
    }

    /** {@code /moddy wishes}: a refill bar, the next wish and full-again. */
    public static List<String> command(WishBank.Status s) {
        List<String> out = new ArrayList<>();
        out.add("✨ Your wishes: " + amount(s.halves()) + "/" + s.cap() + "  " + bar(s.halves(), s.cap()));
        if (s.full()) {
            out.add("Your wish bank is full! A new wish uses " + wishes(s.priceHalves()) + ", a ready-made one "
                    + wishes(s.priceHalves() / WishBank.HALVES) + ".");
        } else {
            out.add("One more every " + minutes(s.refillMinutes()) + " — the next in " + span(s.untilNext()) + ".");
            out.add("Full again in " + span(s.untilFull()) + ".");
        }
        return out;
    }

    /** {@code /moddy wishes} on a world with no bank. */
    public static String noBank() {
        return "✨ Wishes are unlimited here — wish away!";
    }

    // ── the pieces ────────────────────────────────────────────────────────────────────────────

    /** {@code ■■■■■■■□□□}; a balance above the cap shows every square full. */
    /** One cell a wish: ■ whole, ◧ half, □ empty. {@code halves} is the balance in halves. */
    static String bar(int halves, int cap) {
        int h = Math.max(0, Math.min(cap * WishBank.HALVES, halves));
        int whole = h / WishBank.HALVES;
        boolean half = h % WishBank.HALVES != 0;
        return "■".repeat(whole) + (half ? "◧" : "") + "□".repeat(Math.max(0, cap - whole - (half ? 1 : 0)));
    }

    /** "half a wish", "1 wish", "1½ wishes", "7 wishes" — from HALVES. */
    static String wishes(int halves) {
        if (halves == 1) {
            return "half a wish";
        }
        if (halves == WishBank.HALVES) {
            return "1 wish";
        }
        return amount(halves) + " wishes";
    }

    /** "½", "7", "7½" — a balance or a price in HALVES, as a number a child reads. */
    public static String amount(int halves) {
        int whole = halves / WishBank.HALVES;
        boolean half = halves % WishBank.HALVES != 0;
        if (!half) {
            return Integer.toString(whole);
        }
        return whole == 0 ? "½" : whole + "½";
    }

    static String minutes(int m) {
        if (m % 60 == 0) {
            int h = m / 60;
            return h == 1 ? "hour" : h + " hours";
        }
        return m == 1 ? "minute" : m + " minutes";
    }

    /**
     * A wait, in a child's words: whole minutes under an hour ROUNDED UP (a wish that arrives in 40
     * seconds is "1 minute", never "0 minutes"), then "about" hours to the nearest half.
     */
    static String span(long ms) {
        long m = Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m < 60) {
            return m == 1 ? "1 minute" : m + " minutes";
        }
        long halves = Math.round(m / 30.0);
        long h = halves / 2;
        boolean half = halves % 2 == 1;
        if (!half) {
            return h == 1 ? "about an hour" : "about " + h + " hours";
        }
        return h == 1 ? "about an hour and a half" : "about " + h + " and a half hours";
    }

    /** The watch's short form: {@code 12m}, {@code 2h 30m}, {@code 1h}. */
    static String compact(long ms) {
        long m = Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m < 60) {
            return m + "m";
        }
        return (m / 60) + "h" + (m % 60 == 0 ? "" : " " + (m % 60) + "m");
    }
}
