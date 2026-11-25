package com.moddingfromamod.bank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moddingfromamod.plugin.ModdyPlugin;
import com.moddingfromamod.plugin.Plugins;
import com.moddingfromamod.wish.Wish;
import com.moddingfromamod.wish.WishCharge;
import net.minecraft.server.level.ServerPlayer;

/**
 * The wish bank plugged into the core's {@link WishCharge} hook (#2360). Listed in
 * {@code META-INF/services/com.moddingfromamod.plugin.ModdyPlugin}; the public mod charges nothing.
 *
 * <p>Every method is the call the core made directly before #2360, moved here word for word, so a world whose
 * rules declare a {@code wish_bank} (Scarcity Isle) is priced exactly as it was, and one that does not is as free and as quiet as it was.
 */
public final class WishBankPlugin implements ModdyPlugin {

    @Override
    public String name() {
        return "wish-bank";
    }

    private static final Charge CHARGE = new Charge();

    @Override
    public void register(Plugins plugins) {
        plugins.add(WishCharge.class, CHARGE);
    }

    /** The bank's charge, for the Isle's world module to hand out as its own ({@code WorldModule.charge}, #2750). */
    public static WishCharge charge() {
        return CHARGE;
    }

    static RewishPrice.Kind kind(WishCharge.Again a) {
        return a == null ? null : RewishPrice.Kind.valueOf(a.name());
    }

    static WishCharge.Paid paid(WishBankBook.Pickup p) {
        if (p == null || !p.refuses()) {
            return WishCharge.Paid.FREE;
        }
        return new WishCharge.Paid(true, () -> WishBankService.emptyLine(p.status()));
    }

    /** The hook, answered by the bank's own services. */
    static final class Charge implements WishCharge {

        @Override
        public boolean priced(UUID player) {
            return WishBankService.status(player) != null;
        }

        @Override
        public int wishPrice() {
            WishBank.Params p = WishBankService.params();
            return p == null ? 0 : p.price();
        }

        @Override
        public Map<String, String> guideValues() {
            WishBank.Params p = WishBankService.params();
            return p == null ? Map.of()
                    : com.moddingfromamod.world.GuideBook.bankValues(true, p.start(), p.refillMinutes(), p.cap(),
                            p.price());
        }

        @Override
        public String panelTitle(String base, UUID player) {
            return WishBankWords.panelTitle(base, WishBankService.status(player));
        }

        @Override
        public String composeTitle(String base, UUID player) {
            WishBank.Status bank = WishBankService.status(player);
            return bank == null ? base : WishBankWords.composeTitle(base, bank);
        }

        @Override
        public List<String> wishesCommand(UUID player) {
            WishBank.Status bank = WishBankService.status(player);
            return bank == null ? List.of(WishBankWords.noBank()) : WishBankWords.command(bank);
        }

        @Override
        public void showBalance(ServerPlayer player) {
            WishBankService.label(player, WishBankService.status(player.getUUID()));
        }

        @Override
        public String preview(Again kind) {
            return WishBankService.preview(kind(kind));
        }

        @Override
        public String loreLine(Again kind) {
            return RewishPrice.loreLine(WishBankService.params(), kind(kind));
        }

        @Override
        public boolean refusesAtSubmit(ServerPlayer player, boolean distress, String text, boolean recipe) {
            return WishBankService.refusesAtSubmit(player, distress, text, recipe);
        }

        @Override
        public Paid atPickup(Wish wish) {
            return paid(WishBankService.atPickup(wish));
        }

        @Override
        public Paid charge(UUID player, UUID wishId, boolean agent) {
            return paid(WishBankService.charge(player, wishId, agent));
        }

        @Override
        public AgainPaid chargeAgain(ServerPlayer player, Again kind) {
            WishBankService.Again a = WishBankService.chargeAgain(player, kind(kind));
            return new AgainPaid(a.refused(), a);
        }

        @Override
        public void afterAgain(ServerPlayer player, AgainPaid paid) {
            WishBankService.afterAgain(player, paid == null ? null : (WishBankService.Again) paid.token());
        }

        @Override
        public void refundAgain(ServerPlayer player, AgainPaid paid) {
            WishBankService.refundAgain(player, paid == null ? null : (WishBankService.Again) paid.token());
        }

        @Override
        public String refundUndone(ServerPlayer player, Wish wish) {
            WishBank.Status back = WishBankService.refundUndone(player, wish);
            return back == null ? null : WishBankWords.balance(back);
        }

        @Override
        public void onStatus(Wish before, Wish after) {
            WishBankService.onStatus(before, after);
        }
    }
}
