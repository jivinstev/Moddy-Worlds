package com.moddingfromamod.levels.arena;

import com.moddingfromamod.wish.Wish;
import com.moddingfromamod.wish.WishAsk;
import com.moddingfromamod.wish.WishCharge;
import com.moddingfromamod.wish.WishStatus;
import com.moddingfromamod.world.WorldRulesService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *   <li>{@link #refuses}: every wish asks as it starts (typed words for the Magician or the Apprentice's page, a kept
 *       thing fired again, a Builder make or Apprentice tile); it is sorted by {@link #kindOf} and let through, or
 *       refused with the one line the core then says. Asking changes nothing.</li>
 *   <li>{@link #charge(WishAsk, UUID)}: a kept thing fired again in a battle really starts: it costs one of the wave's
 *       hotbar wishes and starts its rest.</li>
 *   <li>{@link #restLeft}: how long a hotbar wish still rests, so the slot's own sweep shows the arena's rest.</li>
 *   <li>{@link #atPickup}: a Magician wish is counted when the Magician picks it up (agent thread: the count is
 *       thread-safe, and the line is said on the server thread).</li>
 *   <li>{@link #onStatus} and {@link #refundUndone}: a counted wish that fails, is stopped, times out or is undone gives
 *       its count back.</li>
 *   <li>{@link #wishesCommand}, {@link #panelTitle}, {@link #composeTitle}: what {@code /moddy wishes}, the watch and
 *       the wish box say about the arena's wishes.</li>
 *   <li>{@link #guideValues}: the rules numbers the Arena Guide book's pages name ({@link ArenaGuide}).</li>
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
    /** The battle, for where it is on the wish ladder; null in a test. */
    private volatile ArenaBattle battle;

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

    /** The arena's battle, whose wish ladder {@code /moddy wishes} reports. */
    void battle(ArenaBattle b) {
        battle = b;
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

    /** Which kind of wish the arena takes this one for ({@link ArenaWishPolicy#kindOf(String, boolean)}). */
    static ArenaWishPolicy.Kind kindOf(WishAsk ask) {
        return ArenaWishPolicy.kindOf(ask.magic().name(), ask.namedRecipe());
    }

    /** The kept thing a wish makes again, for its rest: its recipe id, or its words when it carries none. */
    static String thingOf(WishAsk ask) {
        return ask.recipe().isEmpty() ? ask.text() : ask.recipe();
    }

    private long tick() {
        return server == null ? 0 : server.getTickCount();
    }

    /**
     * May this wish start? Every wish asks it as it starts, before anything runs or opens (typed words before the
     * Apprentice's page, a make before it is built, a hotbar fire before the rate limiter). The answer is the one line
     * the player then reads, said once by the core; nothing is changed here (the core may ask again, and a menu asks
     * while it draws): a fire is written down in {@link #charge(WishAsk, UUID)}.
     */
    @Override
    public Optional<String> refuses(WishAsk ask) {
        ArenaWishPolicy.Kind kind = kindOf(ask);
        ArenaWishPolicy.Phase now = phase;
        ArenaWishes w = wishes.get();
        ArenaWishPolicy.Decision d = policy.check(ask.player(), now, kind, thingOf(ask), tick(), w);
        if (d.allowed()) {
            return Optional.empty();
        }
        // a recipe's id or a Builder make's label is ours to log; typed words are the player's, and never logged here
        LOG.info("Moddy Arena: wishes: {} {} {} in {}: REFUSED {}", nameOf(ask.player()), kind,
                ask.namedRecipe() ? quoted(thingOf(ask)) : "(typed words)", now, d.refused());
        return Optional.of(switch (d.refused()) {
            case RESTING -> ArenaWords.resting(d.secondsLeft());
            case OUT_OF_WISHES -> ArenaWords.outOfWishes(w.wishesPerWave());
            case MAGICIAN_USED_UP -> ArenaWords.magicianUsedUp(d.budget());
            case BATTLE_INSTANT_ONLY -> ArenaWords.battleInstantOnly();
        });
    }

    /**
     * A wish served with no model really starts (after the core's rate limiter): in a battle, a kept thing fired again
     * costs one of this wave's hotbar wishes and starts its rest. Free otherwise.
     */
    @Override
    public Paid charge(WishAsk ask, UUID wishId) {
        ArenaWishPolicy.Kind kind = kindOf(ask);
        ArenaWishPolicy.Phase now = phase;
        ArenaWishes w = wishes.get();
        int left = policy.fired(ask.player(), now, kind, thingOf(ask), tick(), w);
        if (now != ArenaWishPolicy.Phase.BATTLE || kind != ArenaWishPolicy.Kind.REPLAY) {
            return Paid.FREE;
        }
        if (left < 0) {
            // two fires in one tick on the last wish: the second is refused here, where the first was counted
            ArenaWishPolicy.Decision d = policy.check(ask.player(), now, kind, thingOf(ask), tick(), w);
            String line = d.refused() == ArenaWishPolicy.Reason.RESTING ? ArenaWords.resting(d.secondsLeft())
                    : ArenaWords.outOfWishes(w.wishesPerWave());
            return new Paid(true, () -> line);
        }
        LOG.info("Moddy Arena: wishes: {} fired {} in the battle: {} of {} hotbar wishes left this wave; it rests {} s",
                nameOf(ask.player()), quoted(thingOf(ask)), left, w.wishesPerWave(), w.cooldownSeconds());
        return Paid.FREE;
    }

    /**
     * How long a hotbar wish still rests, so the slot's own sweep shows the arena's rest: in a battle the ticks left of
     * this player's rest of that thing (0 when it is ready); in prep, nothing to say (the core keeps its own second).
     * A query: it changes nothing.
     */
    @Override
    public int restLeft(WishAsk ask) {
        if (phase != ArenaWishPolicy.Phase.BATTLE) {
            return -1;
        }
        return (int) Math.min(Integer.MAX_VALUE, policy.restTicks(ask.player(), thingOf(ask), tick()));
    }

    /** This player's hotbar wishes left this wave. */
    int firesLeft(UUID player) {
        return policy.firesLeft(player, wishes.get());
    }

    /** The arena's wish tunables now. */
    ArenaWishes wishes() {
        return wishes.get();
    }

    /** A wave was cleared: every player's hotbar wishes start again for the next one. */
    void newWave() {
        policy.newWave();
    }

    private String nameOf(UUID id) {
        ServerPlayer p = server == null || id == null ? null : server.getPlayerList().getPlayer(id);
        return p == null ? String.valueOf(id) : p.getScoreboardName();
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

    /** The Arena Guide's numbers, from the rules in force when the core gives the book. */
    @Override
    public Map<String, String> guideValues() {
        return ArenaGuide.values(ArenaWavesRule.wavesOf(WorldRulesService.current()), wishes.get(),
                ArenaLadderRule.ladderOf(WorldRulesService.current()));
    }

    @Override
    public List<String> wishesCommand(UUID player) {
        ArenaWishes w = wishes.get();
        List<String> out = new java.util.ArrayList<>();
        if (phase == ArenaWishPolicy.Phase.BATTLE) {
            out.addAll(ArenaWords.battleWishes(w.cooldownSeconds(), policy.firesLeft(player, w), w.wishesPerWave()));
        } else {
            out.add(ArenaWords.magicianLeft(policy.left(player, w), w.magicianWishes()));
            out.add(ArenaWords.prepWishes(w.wishesPerWave()));
        }
        ArenaBattle b = battle;
        if (b != null) {   // the wish ladder: the limits every fighter's makes are fitted to now, and what is next
            ArenaLadder ladder = ArenaModule.ladder();
            out.addAll(ArenaWords.ladderWishes(ladder, b.ladderOn(), b.reached(ladder)));
        }
        return out;
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
