package com.moddingfromamod.bank;

/**
 * <b>What getting something AGAIN costs</b> (#1350, owner) — pure, so the rule is a
 * Gate A test ({@code RewishPriceTest}).
 *
 * <p>On a world with a wish bank (Scarcity Isle) each of the three "again" buttons costs ONE wish —
 * the world's price for a wish, {@link WishBank.Params#price()}:
 * <ul>
 *   <li>{@link Kind#GIVE_ONE} — {@code [🎁 Give me one]} / {@code /moddy give}: a copy of a thing he
 *       already has. Free on a world with no bank (#480); on the Isle it was an unlimited free copy of
 *       anything he had ever wished for, which bypassed the bank;</li>
 *   <li>{@link Kind#PART} — re-wishing one part of a wish ({@code /moddy again <wish>@<part>});</li>
 *   <li>{@link Kind#WHOLE} — re-wishing the whole wish ({@code /moddy again <wish>}): also ONE, so
 *       re-wishing the whole thing is the better deal.</li>
 * </ul>
 *
 * <p>The price is per BUTTON PRESSED, never per part counted: which {@code made} entries count as
 * parts is a separate question (#1359), and a price that depended on it would move whenever that
 * filtering did. With no bank every price is {@code 0} and nothing is said.
 */
public final class RewishPrice {

    private RewishPrice() {
    }

    /** The three "again" buttons. */
    public enum Kind { GIVE_ONE, PART, WHOLE }

    /**
     * The price of pressing {@code kind}, in HALVES: half a wish on a world with a bank (#1785 — an
     * "again" is served with no model), {@code 0} without.
     */
    public static int price(WishBank.Params bank, Kind kind) {
        return bank == null || kind == null ? 0 : bank.priceHalves(false);
    }

    /** Which kind a {@code /moddy again} recipe id is: a {@code <wish>@<part>} id is a PART. */
    public static Kind ofRecipe(boolean isPart) {
        return isPart ? Kind.PART : Kind.WHOLE;
    }

    /**
     * The price shown BEFORE he presses, in the bar's own words ({@link WishBankWords#barCost}:
     * {@code " · uses 1 ✨"}), or {@code ""} when it is free (no bank).
     */
    public static String preview(WishBank.Params bank, Kind kind) {
        int p = price(bank, kind);
        return p <= 0 ? "" : WishBankWords.barCost(p);
    }

    /** The same price as a tooltip line of its own ({@code "Uses 1 ✨"}), or {@code ""} when free. */
    public static String loreLine(WishBank.Params bank, Kind kind) {
        String p = preview(bank, kind);
        return p.isEmpty() ? "" : "U" + p.substring(" · u".length());
    }
}
