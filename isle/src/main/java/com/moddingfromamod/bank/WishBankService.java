package com.moddingfromamod.bank;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.moddingfromamod.wish.Wish;
import com.moddingfromamod.wish.WishStatus;
import com.moddingfromamod.world.WorldRulesService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The wish bank, in the game</b> — the thin
 * Minecraft half around {@link WishBankBook}. Everything here asks one question first:
 * <b>does the world's rules file declare a {@code wish_bank}?</b> ({@link #params()}, read from the
 * hot-reloaded rules on every call). When it does not — a plain world — every
 * method returns at once and wishing is exactly what it was.
 *
 * <p>Where each part runs, and why there:
 * <ul>
 *   <li><b>submit</b> ({@code WishService}): a balance below the price refuses kindly, before the
 *       rate limiter, the graders and the book — so an empty bank costs no token, no model call and
 *       no worker. A wellbeing-flagged wish is never refused (CHILD_SAFETY.md §1): it is WAIVED.</li>
 *   <li><b>pickup</b> ({@code AwaitWishTool}, after the safety and world-rules gates, so a refusal by
 *       either costs nothing): the charge; a re-wish of the same wish id is free.</li>
 *   <li><b>settle</b> ({@code WishBook.complete}, the one place a status moves): a charged wish that
 *       ends without being granted is refunded once; a granted one says what is left.</li>
 *   <li><b>join / leave / tick</b>: the welcome line, the balance written at leaving, the watch's name.</li>
 * </ul>
 * Every child-visible line is server-side chat, an item name, a chest title or a boss bar ('s
 * standing display, {@link #bar}), so a STOCK client sees all of it.
 */
// #2750: no modid, so FML binds it to the mod whose file holds it: moddyisle in the level jar.
// A modid naming the core leaves it unregistered in the level jar (the bank never opens its book: "unlimited").
@EventBusSubscriber
public final class WishBankService {

    private static final Logger LOGGER = LoggerFactory.getLogger("moddingfromamod/wish-bank");

    /** How often the watch's name is brought up to date (once a second at 20 tps). */
    static final int LABEL_TICKS = 20;

    private static volatile WishBankBook live;
    private static volatile WishBankBook testBook;
    private static int ticks;

    private WishBankService() {
    }

    // ── what is in force ──────────────────────────────────────────────────────────────────────

    /** This world's bank, or null when it has none (read from the hot-reloaded rules each call). */
    public static WishBank.Params params() {
        try {
            return WishBank.Params.of(WorldRulesService.current());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The bank's book for the running world, or null. */
    public static WishBankBook book() {
        WishBankBook t = testBook;
        return t != null ? t : live;
    }

    /** Pin a book of a test's own (null restores). Use a batch of its own: statics are shared. */
    public static void forTest(WishBankBook book) {
        testBook = book;
    }

    /** The world's store file. */
    public static java.nio.file.Path storeFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("wishes").resolve(WishBankStore.FILE);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        live = new WishBankBook(new WishBankStore(storeFile(event.getServer())));
        WishBank.Params p = params();
        if (p != null) {
            LOGGER.info("wish bank: {} to start, +1 every {} min, cap {}, {} a wish ({})", p.start(),
                    p.refillMinutes(), p.cap(), p.price(), live.store().file());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        live = null;
        for (UUID id : new java.util.ArrayList<>(BARS.keySet())) {
            drop(id);
        }
    }

    /** What the place is called, for "explore your island meanwhile". */
    static String place() {
        var b = com.moddingfromamod.world.WorldModuleHost.liveBases();
        return b == null ? "world" : b.noun();
    }

    /** The bank as this player sees it now, or null when the world has none. */
    public static WishBank.Status status(UUID player) {
        WishBank.Params p = params();
        WishBankBook b = book();
        return p == null || b == null || player == null ? null : b.status(p, player, System.currentTimeMillis());
    }

    // ── submit ────────────────────────────────────────────────────────────────────────────────

    /**
     * At submit: refuse kindly when there is nothing to pay with, and say when the next wish
     * arrives. Server thread. False on a world with no bank, and for a child in distress.
     */
    public static boolean refusesAtSubmit(ServerPlayer player, boolean distress) {
        return refusesAtSubmit(player, distress, null);
    }

    /**
     * {@link #refusesAtSubmit(ServerPlayer, boolean)}, knowing the words: a question is never refused
     * ({@link WishBankBook#free}) — it will cost nothing at pickup either.
     */
    public static boolean refusesAtSubmit(ServerPlayer player, boolean distress, String text) {
        return refusesAtSubmit(player, distress, text, false);
    }

    /**
     * As above, with what the wish will cost: a NAMED recipe ({@code recipe}: a button, an "again")
     * is served with no model and costs half (#1785), so ½ left still pays for it.
     */
    public static boolean refusesAtSubmit(ServerPlayer player, boolean distress, String text, boolean recipe) {
        if (WishBankBook.free(text)) {
            return false;
        }
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (b.affordable(p, player.getUUID(), now, !recipe) || distress) {
            return false;
        }
        tell(player, WishBankWords.empty(b.status(p, player.getUUID(), now), place()));
        LOGGER.info("wish bank: {} has nothing to pay with — refused at submit, nothing spent",
                player.getScoreboardName());
        return true;
    }

    /** At submit, a wish that went through on an empty bank (distress): never charged. */
    public static void waiveIfEmpty(ServerPlayer player, UUID wishId) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || player == null || wishId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!b.affordable(p, player.getUUID(), now)) {
            b.waive(p, player.getUUID(), wishId, now);
        }
    }

    // ── pickup ────────────────────────────────────────────────────────────────────────────────

    /**
     * At pickup (any thread; the book is synchronized): take the charge. Null on a world with no
     * bank. A wish the cache or a template serves is charged here too, through {@link #charge}.
     */
    public static WishBankBook.Pickup atPickup(Wish wish) {
        if (wish == null) {
            return null;
        }
        // a question is answered, not granted, and costs nothing (WishBankBook.free).
        if (WishBankBook.free(wish.text())) {
            WishBank.Params p = params();
            WishBankBook b = book();
            if (p == null || b == null || wish.playerId() == null) {
                return null;
            }
            LOGGER.info("wish bank: wish {} is a question — free", wish.id().toString().substring(0, 8));
            return new WishBankBook.Pickup(WishBankBook.Paid.QUESTION, 0,
                    b.status(p, wish.playerId(), System.currentTimeMillis()));
        }
        return charge(wish.playerId(), wish.id(), true);
    }

    /**
     * Charge a wish: {@code agent} true for one the agent grants (the full price), false for one
     * served with no model — a recipe, a Builder make, an "again" (half, #1785).
     */
    public static WishBankBook.Pickup charge(UUID player, UUID wishId, boolean agent) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || player == null || wishId == null) {
            return null;
        }
        WishBankBook.Pickup paid = b.pickup(p, player, wishId, System.currentTimeMillis(), agent);
        switch (paid.paid()) {
            case CHARGED -> {
                if (paid.price() > 0) {
                    com.moddingfromamod.wish.WishProgress.cost(wishId, WishBankWords.barCost(paid.price()));
                }
                // a size price would be logged here in SHADOW; there is no deterministic size
                // before the plan exists, so it is not (NEEDS).
                LOGGER.info("wish bank: charged {} half-wish(es) for wish {} — {} halves left", paid.price(),
                        wishId.toString().substring(0, 8), paid.status().halves());
            }
            case UNREADABLE -> LOGGER.error("MODDY-ALERT wish bank unreadable ({}): wish {} is FREE until a "
                    + "grown-up looks at it", b.store().refusalReason(), wishId.toString().substring(0, 8));
            case EMPTY -> LOGGER.info("wish bank: wish {} refused at pickup — the bank emptied since submit",
                    wishId.toString().substring(0, 8));
            default -> {
            }
        }
        return paid;
    }

    // ── "again" without a wish: [🎁 Give me one] (#1350) ────────────────────────────────────────

    /**
     * What an "again" charge did. {@code ledgerId} is the bank ledger's key for it (a give has no
     * wish id of its own), so a hand-over that landed nothing can be given back ({@link #refundAgain}).
     */
    public record Again(boolean refused, int price, UUID ledgerId, WishBank.Status status) {

        /** No bank, or a store that could not be read (fail open, as at pickup): free. */
        static final Again FREE = new Again(false, 0, null, null);
    }

    /**
     * Charge one "again" press that is not a wish ({@link RewishPrice.Kind#GIVE_ONE}), BEFORE anything
     * is handed over. On an empty bank it says the usual empty line and refuses: nothing is spent and
     * the caller must hand nothing over. Free with no bank. Server thread.
     */
    public static Again chargeAgain(ServerPlayer player, RewishPrice.Kind kind) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || player == null || RewishPrice.price(p, kind) <= 0) {
            return Again.FREE;
        }
        UUID id = UUID.randomUUID();
        WishBankBook.Pickup paid = charge(player.getUUID(), id, false);
        if (paid == null) {
            return Again.FREE;
        }
        if (paid.refuses()) {
            tell(player, WishBankWords.empty(paid.status(), place()));
            LOGGER.info("wish bank: {} has nothing to pay with — {} refused, nothing handed over",
                    player.getScoreboardName(), kind);
            return new Again(true, 0, null, paid.status());
        }
        if (paid.paid() == WishBankBook.Paid.CHARGED) {
            label(player, paid.status());
            return new Again(false, paid.price(), id, paid.status());
        }
        return Again.FREE;
    }

    /** After the hand-over: what is left, in the words a granted wish ends with. */
    public static void afterAgain(ServerPlayer player, Again charged) {
        if (player != null && charged != null && charged.price() > 0 && charged.status() != null) {
            tell(player, WishBankWords.afterWish(charged.status(), place()));
        }
    }

    /** Give an "again" charge back — the hand-over landed nothing. */
    public static void refundAgain(ServerPlayer player, Again charged) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || player == null || charged == null || charged.ledgerId() == null) {
            return;
        }
        WishBank.Status back = b.refund(p, player.getUUID(), charged.ledgerId(), System.currentTimeMillis());
        if (back != null) {
            tell(player, WishBankWords.refunded(back, false));
            label(player, back);
        }
    }

    /**
     * <b>👎 gives the wish back</b> (#1546, the owner). The one refund {@link #onStatus}
     * deliberately does not make — a wish that was DONE is "granted" there — made on purpose here,
     * because the child has just said it was not what he wanted and it has been taken away.
     *
     * <p><b>At most once per wish, by the ledger</b>: {@link WishBankBook#refund} pays only an entry
     * still {@code CHARGED} and turns it {@code REFUNDED}, so a wish that already timed out or
     * failed (refunded at its settle), one never charged (a question, a waived wish, a template
     * served before pickup) and a second 👎 all pay nothing. No bank on this world → null.
     *
     * <p>Call it BEFORE the book moves the wish, so the settle's own refund finds nothing left to
     * pay and the child hears one line, not two. The caller says it (inside the undo line).
     *
     * @return the bank after the refund, or null when nothing was given back
     */
    public static WishBank.Status refundUndone(ServerPlayer player, Wish wish) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || player == null || wish == null) {
            return null;
        }
        WishBank.Status back = b.refund(p, player.getUUID(), wish.id(), System.currentTimeMillis());
        if (back != null) {
            LOGGER.info("wish bank: refunded wish {} (UNDONE by the child) — {} halves now", wish.shortId(), back.halves());
            label(player, back);
        }
        return back;
    }

    /** The price shown before {@code kind} is pressed ({@code ""} with no bank). */
    public static String preview(RewishPrice.Kind kind) {
        return RewishPrice.preview(params(), kind);
    }

    /** The empty line, for a refusal at pickup. */
    public static String emptyLine(WishBank.Status s) {
        return WishBankWords.empty(s, place());
    }

    // ── settle ────────────────────────────────────────────────────────────────────────────────

    /**
     * From {@code WishBook.complete}, the one place a status moves (server thread): refund a charged
     * wish that ends without being granted, and tell a granted one what is left. A wish that was
     * already DONE and is then stopped is NOT refunded here — it was granted. (A 👎 / {@code /moddy no}
     * that undoes it IS refunded, once, by {@link #refundUndone} before the book moves — #1546.)
     */
    public static void onStatus(Wish before, Wish after) {
        if (before == null || after == null || before.status() == after.status()) {
            return;
        }
        WishBank.Params p = params();
        WishBankBook b = book();
        UUID player = after.playerId();
        if (p == null || b == null || player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        WishStatus s = after.status();
        if (s == WishStatus.DONE) {
            if (b.charged(player, after.id())) {
                say(player, WishBankWords.afterWish(b.status(p, player, now), place()));
            }
            return;
        }
        if (s.isFinished() && before.status() != WishStatus.DONE) {
            WishBank.Status back = b.refund(p, player, after.id(), now);
            if (back != null) {
                LOGGER.info("wish bank: refunded wish {} ({}) — {} halves now", after.shortId(), s, back.halves());
                // A RE-AIM stops the wish only to re-open it in new words, and the pickup that follows
                // charges again: the money is right either way, and "you keep it" would be a lie.
                if (!com.moddingfromamod.wish.WishCancel.REAIMED.equals(after.result())) {
                    say(player, WishBankWords.refunded(back, s == WishStatus.STOPPED));
                }
            }
        }
    }

    // ── join, leave, the watch ────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (p == null || b == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        WishBankBook.Joined j = b.join(p, player.getUUID(), System.currentTimeMillis());
        switch (j.arrival()) {
            case FIRST_VISIT -> {
                // on his first arrival the world intro and "how to use Moddy" come first, and
                // this line follows them (JoinIntroService) — said here only when no intro is coming.
                String line = WishBankWords.firstVisit(j.status());
                if (!com.moddingfromamod.world.JoinIntroService.afterIntro(player, line)) {
                    tell(player, line);
                }
            }
            case REFILLED -> tell(player, WishBankWords.welcomeBack(j.status()));
            default -> {
            }
        }
        label(player, j.status());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WishBank.Params p = params();
        WishBankBook b = book();
        if (event.getEntity() instanceof ServerPlayer player) {
            drop(player.getUUID());                         // the bar goes with him
            if (p != null && b != null) {
                b.leave(p, player.getUUID(), System.currentTimeMillis());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % LABEL_TICKS != 0) {
            return;
        }
        WishBank.Params p = params();
        WishBankBook b = book();
        long now = System.currentTimeMillis();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            label(player, p == null || b == null ? null : b.status(p, player.getUUID(), now));
        }
    }

    /**
     * <b>The watch says the balance</b>: {@code Moddy · ✨ 7/10 · +1 in 12m}. Server-side item name,
     * so a stock client shows it in the hotbar. With no bank (or after a rules edit removed it) a
     * watch that still carries a bank label goes back to plain {@code Moddy}; a watch that never had
     * one is never touched, so a world with no bank sees no change at all.
     */
    public static void label(ServerPlayer player, WishBank.Status s) {
        var inv = player.getInventory();
        boolean changed = false;
        for (var list : java.util.List.of(inv.getNonEquipmentItems(), java.util.List.of(inv.player.getOffhandItem()))) {
            for (ItemStack st : list) {
                if (!com.moddingfromamod.wish.VanillaWatchItem.isWatch(st)) {
                    continue;
                }
                Component now = st.get(DataComponents.CUSTOM_NAME);
                String has = now == null ? "" : now.getString();
                String want = s == null ? (has.startsWith(BANK_LABEL_PREFIX) || has.startsWith("Moddy · ") ? "Moddy" : has)
                        : WishBankWords.watchName(s, com.moddingfromamod.wish.CrafterService.title(player));
                if (!want.equals(has)) {
                    st.set(DataComponents.CUSTOM_NAME, Component.literal(want)
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false)));
                    changed = true;
                }
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
        bar(player, s);
    }

    // ── the standing display: one quiet boss bar per child ─────────────────────────────

    /** The bar a child is shown, and the ServerPlayer object it was shown to (a respawn replaces it). */
    private record Held(ServerBossEvent event, ServerPlayer shownTo) {
    }

    private static final Map<UUID, Held> BARS = new ConcurrentHashMap<>();

    /**
     * <b>The bank on screen the whole time</b>: a per-player boss bar, {@code ✨ 7/10 wishes
     * available · +1 in 12m}, filled to balance/cap in ten notches — what {@link WishBankBar} says, executed.
     * Called with every {@link #label} (join, the once-a-second tick, a charge, a refund), so the bar
     * and the watch's name can never disagree. {@code s == null} (no bank, or a rules
     * edit that removed it) takes the bar down; so does a service client. Server thread.
     */
    public static void bar(ServerPlayer player, WishBank.Status s) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        if (s == null || !WishBankBar.shows(true, com.moddingfromamod.marshal.PlayerKind.isChild(id))) {
            drop(id);
            return;
        }
        WishBankBar.Shape shape = WishBankBar.of(s);
        Held held = BARS.get(id);
        if (held == null) {
            ServerBossEvent e = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal(shape.name()),
                    BossEvent.BossBarColor.YELLOW, overlay(shape.notches()));
            e.setProgress(shape.progress());
            e.addPlayer(player);
            BARS.put(id, new Held(e, player));
            return;
        }
        if (held.shownTo() != player) {
            // A respawn (or a dimension change) hands us a NEW ServerPlayer for the same child: the
            // bar's player set still holds the old one, so move it across.
            held.event().removePlayer(held.shownTo());
            held.event().addPlayer(player);
            BARS.put(id, new Held(held.event(), player));
        }
        if (!held.event().getName().getString().equals(shape.name())) {
            held.event().setName(Component.literal(shape.name()));
        }
        if (held.event().getProgress() != shape.progress()) {
            held.event().setProgress(shape.progress());
        }
    }

    /** The bar this child is shown now, or null — for a gate. */
    public static ServerBossEvent barOf(UUID player) {
        Held h = player == null ? null : BARS.get(player);
        return h == null ? null : h.event();
    }

    private static void drop(UUID player) {
        Held h = BARS.remove(player);
        if (h != null) {
            h.event().removeAllPlayers();
            h.event().setVisible(false);
        }
    }

    private static BossEvent.BossBarOverlay overlay(int notches) {
        return switch (notches) {
            case 6 -> BossEvent.BossBarOverlay.NOTCHED_6;
            case 10 -> BossEvent.BossBarOverlay.NOTCHED_10;
            case 12 -> BossEvent.BossBarOverlay.NOTCHED_12;
            case 20 -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }

    /** Every bank label starts so; a plain watch is called just "Moddy". */
    static final String BANK_LABEL_PREFIX = "Moddy · ✨";

    // ── saying it ─────────────────────────────────────────────────────────────────────────────

    private static void say(UUID player, String line) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
        if (p != null) {
            tell(p, line);
        }
    }

    private static void tell(ServerPlayer p, String line) {
        com.moddingfromamod.wish.WishFeedback.bank(p, line);
    }
}
