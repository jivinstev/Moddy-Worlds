package com.moddingfromamod.levels.arena;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Which wishes the arena lets through, and when</b>: the two phases and their rules, decided here and only here.
 * Pure: no Minecraft. {@link ArenaCharge} asks it at the core's wish-charge hooks and carries out the answer (says the
 * words, refuses or lets the wish go on).
 *
 * <ul>
 *   <li><b>PREP</b> (no battle on): Builder and Apprentice makes are free and unlimited, and so is firing a kept thing
 *       again. A typed wish for the Magician is allowed while the player has some of this prep's
 *       {@link ArenaWishes#magicianWishes} left; it is COUNTED when the Magician picks it up (so a wish the library
 *       answers with no model costs nothing), and given back when it fails, is stopped, or is undone.</li>
 *   <li><b>BATTLE</b> (the countdown, the waves and breathers, and the few seconds the arena takes to win, lose and be
 *       tidied): only INSTANT wishes, a kept thing fired again (its hotbar proxy, or "Make another one"). Each fire costs
 *       one of the player's {@link ArenaWishes#wishesPerWave} for this wave, and the thing then rests
 *       {@link ArenaWishes#cooldownSeconds} for that player before it fires again. The wave's count starts again when a
 *       wave is cleared ({@link #newWave}), so the breather before a wave and the wave itself share one count. A typed
 *       wish and a new Builder make are refused.</li>
 *   <li>When a battle is over ({@link #battleEnded}), every player's Magician count starts again and every cooldown and
 *       wave count is cleared.</li>
 * </ul>
 *
 * <p>Asking ({@link #check}) changes nothing, as the core's {@code refuses} hook must not; a fire is written down only
 * when it really starts ({@link #fired}, from the core's {@code charge}, after its rate limiter).
 *
 * <p>Thread-safe: the Magician's pickup comes on an agent thread, everything else on the server thread.
 */
public final class ArenaWishPolicy {

    /** The arena's two phases. */
    public enum Phase { PREP, BATTLE }

    /** The kinds of wish the arena tells apart. */
    public enum Kind {
        /**
         * a new wish answered with no model: a make from the Builder's menus, an Apprentice tile or the Apprentice's
         * page for typed words, a typed change the Builder carries out, typed words a kept recipe answers
         */
        BUILDER,
        /** a kept thing made again from its own recipe: its hotbar proxy, or "Make another one": no model, instant */
        REPLAY,
        /** words typed for the Magician (an AI model, when he is on) */
        TYPED
    }

    /** Why a wish was refused. */
    public enum Reason {
        /** a typed wish in prep, with this prep's Magician wishes used up */
        MAGICIAN_USED_UP,
        /** a typed wish or a new make during a battle */
        BATTLE_INSTANT_ONLY,
        /** a kept thing fired again during a battle before its cooldown is over */
        RESTING,
        /** a kept thing fired again during a battle with this wave's hotbar wishes used up */
        OUT_OF_WISHES
    }

    /**
     * An answer. {@code refused} is null when the wish may go on. {@code used} and {@code budget} are the player's
     * Magician wishes this prep; {@code secondsLeft} is a resting thing's wait, rounded up; {@code counted} is true when
     * this answer just counted a Magician wish; {@code firesLeft} is the player's hotbar wishes left this wave (in a
     * battle; the whole {@link ArenaWishes#wishesPerWave} in prep).
     */
    public record Decision(Reason refused, int used, int budget, int secondsLeft, boolean counted, int firesLeft) {
        Decision(Reason refused, int used, int budget, int secondsLeft) {
            this(refused, used, budget, secondsLeft, false, 0);
        }

        public boolean allowed() {
            return refused == null;
        }

        /** Magician wishes left this prep. */
        public int left() {
            return Math.max(0, budget - used);
        }
    }

    /**
     * Which kind a wish is, from what the core's {@code WishAsk} says of it: a thing made again from a recipe its start
     * named (a hotbar fire, "Make another one", a recipe button) is a {@link Kind#REPLAY}; a Builder make, an
     * Apprentice tile or page, and typed words a kept recipe answers are new wishes with no model
     * ({@link Kind#BUILDER}); typed words for the Magician's model are {@link Kind#TYPED}. Pure.
     *
     * @param magic       the ask's kind of magic, by name: {@code BUILDER}, {@code APPRENTICE}, {@code REPLAY},
     *                    {@code MAGICIAN}
     * @param namedRecipe the ask's {@code namedRecipe()}: its recipe was named by what started it
     */
    public static Kind kindOf(String magic, boolean namedRecipe) {
        return switch (magic == null ? "" : magic) {
            case "REPLAY" -> namedRecipe ? Kind.REPLAY : Kind.BUILDER;
            case "BUILDER", "APPRENTICE" -> Kind.BUILDER;
            default -> Kind.TYPED;
        };
    }

    private final Map<UUID, Integer> used = new HashMap<>();
    /** The Magician wishes counted this prep, by wish id, and whose: a refund gives back exactly these, once. */
    private final Map<UUID, UUID> counted = new HashMap<>();
    /** player → kept thing → the tick it may fire again. */
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();
    /** player → hotbar wishes fired this wave. */
    private final Map<UUID, Integer> firedThisWave = new HashMap<>();

    /**
     * May this wish start? Changes nothing (the core may ask it more than once for one wish, and while a menu draws).
     * {@code thing} names a kept thing (its recipe), for its cooldown; {@code tick} is the server's tick count.
     */
    public synchronized Decision check(UUID player, Phase phase, Kind kind, String thing, long tick, ArenaWishes w) {
        int u = used.getOrDefault(player, 0);
        int fires = firesLeft(player, w);
        if (phase == Phase.BATTLE) {
            if (kind != Kind.REPLAY) {
                return new Decision(Reason.BATTLE_INSTANT_ONLY, u, w.magicianWishes(), 0, false, fires);
            }
            long rest = restTicks(player, thing, tick);
            if (rest > 0) {
                return new Decision(Reason.RESTING, u, w.magicianWishes(), secondsUntil(tick, tick + rest), false,
                        fires);
            }
            if (fires <= 0) {
                return new Decision(Reason.OUT_OF_WISHES, u, w.magicianWishes(), 0, false, 0);
            }
            return new Decision(null, u, w.magicianWishes(), 0, false, fires);
        }
        if (kind == Kind.TYPED && u >= w.magicianWishes()) {
            return new Decision(Reason.MAGICIAN_USED_UP, u, w.magicianWishes(), 0, false, fires);
        }
        return new Decision(null, u, w.magicianWishes(), 0, false, fires);
    }

    /**
     * A kept thing really is being fired in a battle (the core's charge, after its rate limiter): it costs one of this
     * wave's hotbar wishes and starts its rest. Nothing happens in prep, or for any other kind.
     * The hotbar wishes left this wave after it; -1 when it could not be paid (none left, or still resting: the caller
     * then starts nothing).
     */
    public synchronized int fired(UUID player, Phase phase, Kind kind, String thing, long tick, ArenaWishes w) {
        if (phase != Phase.BATTLE || kind != Kind.REPLAY) {
            return firesLeft(player, w);
        }
        if (restTicks(player, thing, tick) > 0 || firesLeft(player, w) <= 0) {
            return -1;
        }
        readyAt.computeIfAbsent(player, k -> new HashMap<>()).put(key(thing), tick + w.cooldownTicks());
        firedThisWave.merge(player, 1, Integer::sum);
        return firesLeft(player, w);
    }

    /** How many ticks this kept thing still rests for this player from {@code tick}; 0 when it is ready. */
    public synchronized long restTicks(UUID player, String thing, long tick) {
        Map<String, Long> mine = readyAt.get(player);
        Long ready = mine == null ? null : mine.get(key(thing));
        return ready == null ? 0 : Math.max(0, ready - tick);
    }

    /** This player's hotbar wishes left this wave. */
    public synchronized int firesLeft(UUID player, ArenaWishes w) {
        return Math.max(0, w.wishesPerWave() - firedThisWave.getOrDefault(player, 0));
    }

    /** This player's hotbar wishes fired this wave. */
    public synchronized int firedThisWave(UUID player) {
        return firedThisWave.getOrDefault(player, 0);
    }

    /** A wave was cleared: every player's hotbar wishes for the next wave start again (rests go on). */
    public synchronized void newWave() {
        firedThisWave.clear();
    }

    private static String key(String thing) {
        return thing == null ? "" : thing;
    }


    /**
     * The Magician picks a typed wish up: it starts now, and costs one of this prep's Magician wishes. Refused when
     * they are used up (two wishes sent at once on the last one), or when a battle began after it was sent. The same
     * wish picked up again (a re-aim) is never counted twice.
     */
    public synchronized Decision atPickup(UUID player, UUID wish, Phase phase, ArenaWishes w) {
        int u = used.getOrDefault(player, 0);
        if (wish != null && counted.containsKey(wish)) {
            return new Decision(null, u, w.magicianWishes(), 0);
        }
        if (phase == Phase.BATTLE) {
            return new Decision(Reason.BATTLE_INSTANT_ONLY, u, w.magicianWishes(), 0);
        }
        if (u >= w.magicianWishes()) {
            return new Decision(Reason.MAGICIAN_USED_UP, u, w.magicianWishes(), 0);
        }
        used.put(player, u + 1);
        if (wish != null) {
            counted.put(wish, player);
        }
        return new Decision(null, u + 1, w.magicianWishes(), 0, true, 0);
    }

    /**
     * A counted Magician wish did not happen (failed, stopped, timed out, refused after its pickup) or was undone: its
     * count comes back, once. False when it was not counted this prep (nothing to give back).
     */
    public synchronized boolean giveBack(UUID wish) {
        UUID player = wish == null ? null : counted.remove(wish);
        if (player == null) {
            return false;
        }
        used.merge(player, -1, Integer::sum);
        if (used.getOrDefault(player, 0) <= 0) {
            used.remove(player);
        }
        return true;
    }

    /**
     * A battle is over (won, lost, or ended by a restart) and the arena is ready: counts start again, cooldowns end,
     * and so do the waves' hotbar counts.
     */
    public synchronized void battleEnded() {
        used.clear();
        counted.clear();
        readyAt.clear();
        firedThisWave.clear();
    }

    /** This player's Magician wishes used this prep. */
    public synchronized int used(UUID player) {
        return used.getOrDefault(player, 0);
    }

    /** This player's Magician wishes left this prep. */
    public synchronized int left(UUID player, ArenaWishes w) {
        return Math.max(0, w.magicianWishes() - used(player));
    }

    static int secondsUntil(long tick, long ready) {
        long t = Math.max(0, ready - tick);
        return (int) ((t + ArenaMatch.TICKS_PER_SECOND - 1) / ArenaMatch.TICKS_PER_SECOND);
    }

    /** The phase a battle in this state means for wishes: anything but IDLE is a battle (countdown and tidying too). */
    public static Phase phaseOf(ArenaMatch.Phase match) {
        return match == null || match == ArenaMatch.Phase.IDLE ? Phase.PREP : Phase.BATTLE;
    }
}
