package com.moddingfromamod.bank;

import java.util.ArrayList;
import java.util.List;

import com.moddingfromamod.safety.SafetyLexicon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** every sentence the wish bank says — the owner asked for the refill to be CLEAR. */
class WishBankWordsTest {

    static final long MIN = 60_000L;
    static final long NOW = 1_759_300_000_000L;

    static WishBank.Status at(int balance, long untilNextMin) {
        WishBank.Params p = WishBank.Params.LAUNCH;
        long anchor = NOW - (30 - untilNextMin) * MIN;
        return WishBank.status(p, new WishBank.Account(balance * WishBank.HALVES, anchor), NOW);
    }

    @Test
    void theWatchSaysTheBalanceAndWhenTheNextComes() {
        assertEquals("Moddy · ✨ 7/10 · +1 in 12m", WishBankWords.watchName(at(7, 12)));
        assertEquals("Moddy · ✨ 10/10", WishBankWords.watchName(at(10, 30)), "full: nothing is coming");
        assertTrue(WishBankWords.watchName(at(7, 12)).startsWith(WishBankService.BANK_LABEL_PREFIX));
    }

    @Test
    void theStandingBarAndThePanelTitleSayTheCount() {
        assertEquals("✨ 7/10 wishes available · +1 in 12m", WishBankWords.standing(at(7, 12)));
        assertEquals("✨ 10/10 wishes available", WishBankWords.standing(at(10, 30)), "full: nothing is promised");
        assertEquals("✨ 0/10 wishes available · +1 in 30m", WishBankWords.standing(at(0, 30)));
        assertEquals("✨ Moddy · ✨ 7/10", WishBankWords.panelTitle("✨ Moddy ✨", at(7, 12)));
        assertEquals("✨ Moddy ✨", WishBankWords.panelTitle("✨ Moddy ✨", null), "a world with no bank keeps its title");
    }

    @Test
    void afterAWishItSaysWhatIsLeftTheRateAndFullAgain() {
        String s = WishBankWords.afterWish(at(6, 30), "island");
        assertTrue(s.startsWith("✨ 6 wishes left — one more every 30 minutes, full again in about 2 hours"), s);
        String last = WishBankWords.afterWish(at(0, 30), "island");
        assertTrue(last.contains("last wish") && last.contains("30 minutes") && last.contains("explore your island"), last);
        assertTrue(WishBankWords.afterWish(at(10, 30), "island").contains("full"));
    }

    @Test
    void atEmptyItSaysWhenAndOffersSomethingToDo() {
        assertEquals("Your next wish arrives in 18 minutes. Want to explore your island meanwhile?",
                WishBankWords.empty(at(0, 18), "island"));
        assertTrue(WishBankWords.empty(at(0, 18), "").contains("your world"));
    }

    @Test
    void onJoinAndOnTheCommand() {
        assertEquals("Welcome back! ✨ Your wishes refilled while you were away — 10/10.",
                WishBankWords.welcomeBack(at(10, 30)));
        assertTrue(WishBankWords.firstVisit(at(10, 30)).contains("10 wishes")
                && WishBankWords.firstVisit(at(10, 30)).contains("every 30 minutes"));
        List<String> cmd = WishBankWords.command(at(7, 12));
        assertEquals("✨ Your wishes: 7/10  ■■■■■■■□□□", cmd.get(0));
        assertTrue(cmd.get(1).contains("next in 12 minutes"), cmd.toString());
        assertTrue(cmd.get(2).startsWith("Full again in"), cmd.toString());
        assertEquals(2, WishBankWords.command(at(10, 30)).size());
    }

    @Test
    void timesReadLikeAPersonSaysThem() {
        assertEquals("1 minute", WishBankWords.span(10_000));
        assertEquals("18 minutes", WishBankWords.span(17 * MIN + 1));
        assertEquals("about an hour", WishBankWords.span(70 * MIN));
        assertEquals("about an hour and a half", WishBankWords.span(90 * MIN));
        assertEquals("about 4 and a half hours", WishBankWords.span(270 * MIN));
        assertEquals("about 5 hours", WishBankWords.span(300 * MIN));
        assertEquals("2h 30m", WishBankWords.compact(150 * MIN));
        assertEquals("1h", WishBankWords.compact(60 * MIN));
        assertEquals("1m", WishBankWords.compact(1));
        assertEquals("hour", WishBankWords.minutes(60));
        assertEquals("half a wish", WishBankWords.wishes(1), "#1785: the bank counts halves");
        assertEquals("1 wish", WishBankWords.wishes(2));
        assertEquals("1½ wishes", WishBankWords.wishes(3));
    }

    @Test
    void everyLineAChildReadsPassesTheLexicon() {
        SafetyLexicon lex = SafetyLexicon.shared();
        List<String> lines = new ArrayList<>();
        for (int b : new int[] {0, 1, 6, 10, 12}) {
            WishBank.Status s = at(b, 12);
            lines.add(WishBankWords.watchName(s));
            lines.add(WishBankWords.afterWish(s, "island"));
            lines.add(WishBankWords.empty(s, "island"));
            lines.add(WishBankWords.welcomeBack(s));
            lines.add(WishBankWords.firstVisit(s));
            lines.add(WishBankWords.refunded(s, true));
            lines.add(WishBankWords.refunded(s, false));
            lines.add(WishBankWords.composeTitle("✨ Make a Wish!", s));
            lines.add(WishBankWords.standing(s));                       // the boss bar
            lines.add(WishBankWords.panelTitle("✨ Moddy ✨", s));       // the panel's title
            lines.addAll(WishBankWords.command(s));
        }
        lines.add(WishBankWords.noBank());
        lines.add(WishBankWords.barCost(1));
        lines.add(WishBankWords.barCost(2));
        lines.add(WishBankWords.balance(at(7, 12)));                   // #1546: the 👎 line's tail
        assertEquals("✨ 7/10", WishBankWords.balance(at(7, 12)));
        for (String l : lines) {
            assertFalse(l.isBlank());
            assertTrue(lex.check(l).isEmpty(), "the lexicon flags a bank line: " + l);
        }
    }

    // ── #1785: half a wish, said the way a child reads it ────────────────────────────────────────

    static WishBank.Status halves(int halves) {
        return WishBank.status(WishBank.Params.LAUNCH, new WishBank.Account(halves, NOW - 18 * MIN), NOW);
    }

    @Test
    void aHalfIsSaidAsAHalf() {
        assertEquals("Moddy · ✨ 7½/10 · +1 in 12m", WishBankWords.watchName(halves(15)));
        assertEquals("✨ Your wishes: 7½/10  ■■■■■■■◧□□", WishBankWords.command(halves(15)).get(0));
        assertTrue(WishBankWords.afterWish(halves(13), "island").startsWith("✨ 6½ wishes left"));
        assertEquals("½", WishBankWords.amount(1));
        assertEquals(" · uses ½ ✨", WishBankWords.barCost(1));
    }

    @Test
    void halfAWishLeftIsNotTheLastWishBecauseARecipeStillFits() {
        String s = WishBankWords.afterWish(halves(1), "island");
        assertTrue(s.startsWith("✨ half a wish left"), s);
        assertTrue(WishBankWords.afterWish(halves(0), "island").contains("last wish"));
    }

    @Test
    void theFirstVisitSaysBothPrices() {
        String s = WishBankWords.firstVisit(halves(20));
        assertTrue(s.contains("A new wish uses 1 wish") && s.contains("a ready-made one half a wish"), s);
    }
}
