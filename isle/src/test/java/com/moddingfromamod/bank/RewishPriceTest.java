package com.moddingfromamod.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #1350: on the Isle every "again" button has a price; on a world with no bank nothing changes. #1785: an
 * "again" is served with no model, so it costs HALF a wish — and {@link RewishPrice#price} is in halves.
 */
class RewishPriceTest {

    static final WishBank.Params ISLE = WishBank.Params.LAUNCH;

    @Test
    void onTheIsleEachAgainButtonCostsHalfAWish() {
        for (RewishPrice.Kind k : RewishPrice.Kind.values()) {
            assertEquals(1, RewishPrice.price(ISLE, k), k.name() + " (one HALF)");
        }
    }

    @Test
    void reWishingTheWholeWishCostsNoMoreThanOnePart() {
        assertEquals(RewishPrice.price(ISLE, RewishPrice.Kind.PART), RewishPrice.price(ISLE, RewishPrice.Kind.WHOLE),
                "the whole wish is one press, so it is the better deal than its parts one by one");
    }

    @Test
    void oneWishIsTheWorldsPriceOfAWish() {
        WishBank.Params dear = new WishBank.Params(10, 30, 10, 2);
        assertEquals(2, RewishPrice.price(dear, RewishPrice.Kind.GIVE_ONE), "half of 2 wishes, in halves");
        assertEquals(" · uses 1 ✨", RewishPrice.preview(dear, RewishPrice.Kind.WHOLE));
    }

    @Test
    void aWorldWithNoBankChargesNothingAndSaysNothing() {
        for (RewishPrice.Kind k : RewishPrice.Kind.values()) {
            assertEquals(0, RewishPrice.price(null, k), k.name());
            assertEquals("", RewishPrice.preview(null, k), k.name());
            assertEquals("", RewishPrice.loreLine(null, k), k.name());
        }
    }

    @Test
    void thePreviewIsTheBarsOwnWords() {
        assertEquals(WishBankWords.barCost(1), RewishPrice.preview(ISLE, RewishPrice.Kind.GIVE_ONE));
        assertEquals(" · uses ½ ✨", RewishPrice.preview(ISLE, RewishPrice.Kind.PART));
        assertEquals("Uses ½ ✨", RewishPrice.loreLine(ISLE, RewishPrice.Kind.PART));
    }

    @Test
    void aPartRecipeIsAPartAndAnythingElseTheWhole() {
        assertEquals(RewishPrice.Kind.PART, RewishPrice.ofRecipe(true));
        assertEquals(RewishPrice.Kind.WHOLE, RewishPrice.ofRecipe(false));
    }
}
