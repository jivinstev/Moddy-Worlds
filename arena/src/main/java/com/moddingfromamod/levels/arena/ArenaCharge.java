package com.moddingfromamod.levels.arena;

import com.moddingfromamod.wish.Wish;
import com.moddingfromamod.wish.WishCharge;
import com.moddingfromamod.wish.WishStatus;
import com.moddingfromamod.world.WorldRulesService;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The arena's wish charge</b>: the core's {@link WishCharge} hook, supplied by {@link ArenaModule#charge()} so it is
 * in force in arena worlds only. It prices nothing; it carries out {@link ArenaWishPolicy} at the hooks the core asks:
 *
 * <ul>
 *   <li>{@link #refusesAtSubmit}: every wish that reaches the core's submit (typed words for the Magician, a kept thing
 *       fired again, a Builder make or Apprentice tile) is sorted by {@link ArenaWishPolicy#kindOf} and let through or
 *       refused, saying why itself.</li>
 *   <li>{@link #atPickup}: a Magician wish is counted when the Magician picks it up (agent thread: the count is
 *       thread-safe, and the line is said on the server thread).</li>
 *   <li>{@link #onStatus} and {@link #refundUndone}: a counted wish that fails, is stopped, times out or is undone gives
 *       its count back.</li>
 *   <li>{@link #wishesCommand}, {@link #panelTitle}, {@link #composeTitle}: what {@code /moddy wishes}, the watch and
 *       the wish box say about the arena's wishes.</li>
 * </ul>
 *
 * <p>Every decision is the policy's; this only looks at the server (its tick, the battle's phase) and speaks.
 */
final class ArenaCharge implements WishCharge {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    private final MinecraftServer server;
    private final ArenaWishPolicy policy;
    private final Supplier<ArenaWishes> wishes;
    private volatile ArenaWishPolicy.Phase phase = ArenaWishPolicy.Phase.PREP;

    ArenaCharge(MinecraftServer server, ArenaWishPolicy policy, Supplier<ArenaWishes> wishes) {
        this.server = server;
        this.policy = policy;
        this.wishes = wishes;
    }

    /** The arena's charge in a running world: its tunables are the world's {@code arena_wishes} rule. */
    static ArenaCharge of(MinecraftServer server) {
        return new ArenaCharge(server, new ArenaWishPolicy(),
                () -> ArenaWishesRule.wishesOf(WorldRulesService.current()));
    }

    ArenaWishPolicy policy() {
        return policy;
    }

    ArenaWishPolicy.Phase phase() {
        return phase;
    }

    /** The battle's phase now, once a tick (server thread). */
    void phase(ArenaWishPolicy.Phase p) {
        phase = p == null ? ArenaWishPolicy.Phase.PREP : p;
    }

    /** The arena is ready again after a battle: counts start again, cooldowns end. */
    void battleEnded() {
        policy.battleEnded();
        LOG.info("Moddy Arena: wishes: the battle is over; Magician counts and hotbar cooldowns start again");
    }

    // ── submit ──────────────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean refusesAtSubmit(ServerPlayer player, boolean distress, String text, boolean recipe) {
        ArenaWishPolicy.Kind kind = ArenaWishPolicy.kindOf(text, recipe);
        ArenaWishPolicy.Phase now = phase;
        long tick = server == null ? 0 : server.getTickCount();
        ArenaWishPolicy.Decision d = policy.atSubmit(player.getUUID(), now, kind, text, distress, tick, wishes.get());
        // a recipe's id or a Builder make's label is ours to log; typed words are the player's, and never logged here
        LOG.info("Moddy Arena: wishes: {} {} {} in {}{}: {}", player.getScoreboardName(), kind,
                kind == ArenaWishPolicy.Kind.TYPED ? "(typed words)" : quoted(text), now,
                distress ? " (distress: never refused)" : "", d.allowed() ? "allowed" : "REFUSED " + d.refused());
        if (d.allowed()) {
            return false;
        }
        switch (d.refused()) {
            case RESTING -> player.displayClientMessage(Component.literal(ArenaWords.resting(d.secondsLeft())), true);
            case MAGICIAN_USED_UP -> player.sendSystemMessage(Component.literal(ArenaWords.magicianUsedUp(d.budget())));
            case BATTLE_INSTANT_ONLY -> {
                if (kind == ArenaWishPolicy.Kind.BUILDER) {
                    sayLast(player, ArenaWords.battleInstantOnly());
                } else {
                    player.sendSystemMessage(Component.literal(ArenaWords.battleInstantOnly()));
                }
            }
        }
        return true;
    }

    /**
     * Said after whatever the core says next: a refused Builder make is answered by the core's Builder menus with
     * their own fixed line, queued on the server thread, so the arena's words are queued behind it and read last.
     */
    private void sayLast(ServerPlayer player, String words) {
        UUID who = player.getUUID();
        Runnable say = () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(who);
            if (p != null) {
                p.sendSystemMessage(Component.literal(words));
            }
        };
        if (server == null) {
            player.sendSystemMessage(Component.literal(words));
            return;
        }
        server.execute(() -> server.execute(say));
    }

    @Override
    public void waiveIfEmpty(ServerPlayer player, UUID wishId) {
        policy.waive(wishId);
    }

    // ── the Magician's pickup ───────────────────────────────────────────────────────────────────────────────────────

    @Override
    public Paid atPickup(Wish wish) {
        if (wish == null || wish.playerId() == null) {
            return Paid.FREE;
        }
        ArenaWishes w = wishes.get();
        ArenaWishPolicy.Decision d = policy.atPickup(wish.playerId(), wish.id(), phase, w);
        LOG.info("Moddy Arena: wishes: the Magician picks up wish {} of {}: {}", wish.shortId(), wish.playerName(),
                d.allowed() ? (d.counted() ? "counted, " + d.used() + " of " + d.budget() + " used" : "allowed")
                        : "REFUSED " + d.refused());
        if (!d.allowed()) {
            String line = d.refused() == ArenaWishPolicy.Reason.BATTLE_INSTANT_ONLY ? ArenaWords.battleInstantOnly()
                    : ArenaWords.magicianUsedUp(d.budget());
            return new Paid(true, () -> line);
        }
        if (d.counted()) {
            tell(wish.playerId(), ArenaWords.magicianCounted(d.left(), d.budget()), true);
        }
        return Paid.FREE;
    }

    // ── settle ──────────────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public void onStatus(Wish before, Wish after) {
        if (after == null || !after.status().isFinished() || after.status() == WishStatus.DONE) {
            return;
        }
        if (before != null && before.status().isFinished()) {
            return;
        }
        if (policy.giveBack(after.id())) {
            ArenaWishes w = wishes.get();
            int left = after.playerId() == null ? w.magicianWishes() : policy.left(after.playerId(), w);
            LOG.info("Moddy Arena: wishes: wish {} ended {}; its Magician count is given back", after.shortId(),
                    after.status());
            tell(after.playerId(), ArenaWords.magicianGivenBack(left, w.magicianWishes()), false);
        }
    }

    @Override
    public String refundUndone(ServerPlayer player, Wish wish) {
        if (wish == null || !policy.giveBack(wish.id())) {
            return null;
        }
        ArenaWishes w = wishes.get();
        LOG.info("Moddy Arena: wishes: wish {} undone; its Magician count is given back", wish.shortId());
        UUID who = wish.playerId() != null ? wish.playerId() : player == null ? null : player.getUUID();
        return ArenaWords.magicianUndone(who == null ? w.magicianWishes() : policy.left(who, w), w.magicianWishes());
    }

    // ── words on menus and /moddy wishes ────────────────────────────────────────────────────────────────────────────

    @Override
    public List<String> wishesCommand(UUID player) {
        ArenaWishes w = wishes.get();
        if (phase == ArenaWishPolicy.Phase.BATTLE) {
            return ArenaWords.battleWishes(w.cooldownSeconds());
        }
        return List.of(ArenaWords.magicianLeft(policy.left(player, w), w.magicianWishes()), ArenaWords.prepWishes());
    }

    @Override
    public String panelTitle(String base, UUID player) {
        return base + titleSuffix(player);
    }

    @Override
    public String composeTitle(String base, UUID player) {
        return base + titleSuffix(player);
    }

    private String titleSuffix(UUID player) {
        if (phase == ArenaWishPolicy.Phase.BATTLE) {
            return ArenaWords.titleBattle();
        }
        return player == null ? "" : ArenaWords.titlePrep(policy.left(player, wishes.get()));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Says a line to a player on the server thread (from any thread); {@code bar}: the action bar, else chat. */
    private void tell(UUID who, String words, boolean bar) {
        if (server == null || who == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(who);
            if (p != null) {
                p.displayClientMessage(Component.literal(words), bar);
            }
        });
    }

    private static String quoted(String text) {
        String t = text == null ? "" : text;
        return "'" + (t.length() > 60 ? t.substring(0, 60) + "..." : t) + "'";
    }
}
