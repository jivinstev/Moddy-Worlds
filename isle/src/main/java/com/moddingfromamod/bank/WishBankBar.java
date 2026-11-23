package com.moddingfromamod.bank;

/**
 * <b>The wish bank's standing display</b> (a player who joins the Isle should see his wish bank at
 * once) — what the per-player boss bar says and how full it is, and
 * who gets one. Pure, so every rule is a Gate A test ({@code WishBankBarTest}); {@link WishBankService}
 * only executes it.
 *
 * <h2>Why a boss bar</h2>
 * The watch's item NAME is shown by Minecraft only for a moment when the slot is selected, so
 * nothing on screen said the balance. Of the vanilla surfaces a STOCK client draws for a server:
 * <ul>
 *   <li><b>a boss bar</b> is per player (only the players added see it), stays on screen until removed,
 *       carries a line of text AND a fill, and has a NOTCHED style with exactly ten segments — the
 *       launch cap — so "7 of 10" is legible without reading. Bars stack natively, so it sits beside
 *       {@code WishProgress}'s one-bar-per-wish without either touching the other. Chosen.</li>
 *   <li><b>the tab-list header/footer</b> is per player too, but only seen while Tab is held — a child
 *       would have to know to look, which is the problem being fixed.</li>
 *   <li><b>the action bar</b> fades after ~3 s and every other action-bar line overwrites it.</li>
 * </ul>
 * Distinct from a wish's bar: YELLOW (a wish's is PURPLE) and notched; it is created on join, so it is
 * the TOP bar and every wish's bar stacks under it; it is never counted as a wish's bar
 * ({@code WishProgress.hasBar}/{@code BarBook} know nothing of it).
 */
public final class WishBankBar {

    private WishBankBar() {
    }

    /** What the bar shows. {@code notches} is 0 for a plain bar, else 6/10/12/20 (vanilla's styles). */
    public record Shape(String name, float progress, int notches) {
    }

    /**
     * Does this player get the bar? Only on a world with a bank (a plain world never shows one), and
     * only for a child — a service client (the camera) photographs with the GUI hidden and must never
     * carry one.
     */
    public static boolean shows(boolean worldHasBank, boolean isChild) {
        return worldHasBank && isChild;
    }

    /** The bar for this status: its line, its fill (balance over cap, clamped), its notches. */
    public static Shape of(WishBank.Status s) {
        float p = s.cap() <= 0 ? 0f : Math.max(0f, Math.min(1f, s.halves() / (float) (s.cap() * WishBank.HALVES)));
        return new Shape(WishBankWords.standing(s), p, notches(s.cap()));
    }

    /** Vanilla draws notched bars in 6, 10, 12 or 20 segments: one per wish when the cap is one of those. */
    static int notches(int cap) {
        return switch (cap) {
            case 6, 10, 12, 20 -> cap;
            default -> 0;
        };
    }
}
