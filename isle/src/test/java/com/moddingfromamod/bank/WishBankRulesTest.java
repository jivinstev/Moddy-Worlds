package com.moddingfromamod.bank;

import com.moddingfromamod.world.WorldRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** only a world whose rules file declares a {@code wish_bank} has a bank, and it is schema-checked. */
class WishBankRulesTest {

    private static String with(String rule) {
        return "{\"store\":\"world-rules\",\"v\":1,\"world\":\"isle\",\"rules\":[" + rule + "]}";
    }

    @Test
    void aDeclaredBankIsReadWithItsParameters() {
        WorldRules r = WorldRules.parse(with("{\"id\":\"wish-bank\",\"type\":\"wish_bank\",\"params\":"
                + "{\"start\":5,\"refillMinutes\":20,\"cap\":8,\"pricePerWish\":2}}"), "isle").rules();
        assertEquals(new WishBank.Params(5, 20, 8, 2), WishBank.Params.of(r));
        assertFalse(r.deniesAnything(), "a bank denies nothing, so it never wakes the world-rules grader");
        assertEquals(1, WishBank.Params.of(WorldRules.parse(with("{\"id\":\"b\",\"type\":\"wish_bank\",\"params\":"
                + "{\"start\":10,\"refillMinutes\":30,\"cap\":10}}"), "isle").rules()).price(),
                "pricePerWish defaults to 1");
    }

    @Test
    void noRuleNoBank() {
        assertNull(WishBank.Params.of(WorldRules.parse(with(""), "isle").rules()));
        assertNull(WishBank.Params.of(WorldRules.ALLOW_ALL));
        assertNull(WishBank.Params.of(WorldRules.CLOSED), "a bases world before its file arrives has no bank yet");
    }

    @Test
    void badParametersAreRefused() {
        for (String p : new String[] {
                "{\"start\":11,\"refillMinutes\":30,\"cap\":10}",
                "{\"start\":1,\"refillMinutes\":0,\"cap\":10}",
                "{\"start\":1,\"refillMinutes\":30,\"cap\":0}",
                "{\"start\":1,\"refillMinutes\":30,\"cap\":10,\"pricePerWish\":0}",
                "{\"start\":1,\"refillMinutes\":30,\"cap\":10,\"price\":1}"}) {
            assertFalse(WorldRules.parse(with("{\"id\":\"b\",\"type\":\"wish_bank\",\"params\":" + p + "}"), "isle").ok(), p);
        }
        String two = "{\"id\":\"a\",\"type\":\"wish_bank\",\"params\":{\"start\":1,\"refillMinutes\":30,\"cap\":10}},"
                + "{\"id\":\"b\",\"type\":\"wish_bank\",\"params\":{\"start\":1,\"refillMinutes\":30,\"cap\":10}}";
        assertFalse(WorldRules.parse(with(two), "isle").ok(), "one bank per world");
    }

    @Test
    void theIsleHasTheLaunchBank() throws Exception {
        String text;
        try (var in = WishBankRulesTest.class.getResourceAsStream("/worlds/rules/isle.rules.json")) {
            assertNotNull(in, "isle.rules.json is not on the classpath");
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        WorldRules isle = WorldRules.parse(text, "isle").rules();
        assertEquals(WishBank.Params.LAUNCH, WishBank.Params.of(isle), "10 to start, +1 every 30 minutes, cap 10, 1 a wish");
        assertTrue(isle.rules().stream().anyMatch(r -> r.enforced() && r.type().key().equals("wish_bank")));
    }
}
