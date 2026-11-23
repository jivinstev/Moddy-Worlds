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
 *       tidied): only INSTANT wishes, a kept thing fired again (its hotbar proxy, or "Make another one"). Each kept
 *       thing then rests {@link ArenaWishes#cooldownSeconds} per player before it fires again. A typed wish and a new
 *       Builder make are refused.</li>
 *   <li>When a battle is over ({@link #battleEnded}), every player's Magician count starts again and every cooldown is
 *       cleared.</li>
 *   <li>A wish from a child in distress is never refused and never counted.</li>
 * </ul>
 *
 * <p>Thread-safe: the Magician's pickup comes on an agent thread, everything else on the server thread.
 */
public final class ArenaWishPolicy {

    /** The arena's two phases. */
    public enum Phase { PREP, BATTLE }

    /** The kinds of wish the arena tells apart. */
    public enum Kind {
        /** a new make from the Builder's menus, an Apprentice tile, or a typed change the Builder carries out: no model */
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
        RESTING
    }

    /**
     * An answer. {@code refused} is null when the wish may go on. {@code used} and {@code budget} are the player's
     * Magician wishes this prep; {@code secondsLeft} is a resting thing's wait, rounded up; {@code counted} is true when
     * this answer just counted a Magician wish.
     */
    public record Decision(Reason refused, int used, int budget, int secondsLeft, boolean counted) {
        Decision(Reason refused, int used, int budget, int secondsLeft) {
            this(refused, used, budget, secondsLeft, false);
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
     * The text a Builder make's wish carries ({@code BuilderMake.TEXT_PREFIX} in the core, copied: a test checks they
     * agree). A make from the Builder's menus, an Apprentice tile and a typed change the Builder carries out all start
     * with it; a kept thing fired again carries its recipe's id instead.
     */
    public static final String BUILDER_TEXT_PREFIX = "Builder: ";

    /**
     * Which kind a wish is, from what the core's submit hook is told: {@code recipe} false is typed words (on their way
     * to the Magician, or to the library by their words); a recipe whose text is a Builder make's is a new make; any
     * other recipe is a kept thing made again. Pure.
     */
    public static Kind kindOf(String text, boolean recipe) {
        if (!recipe) {
            return Kind.TYPED;
        }
        return text != null && text.startsWith(BUILDER_TEXT_PREFIX) ? Kind.BUILDER : Kind.REPLAY;
    }

    private final Map<UUID, Integer> used = new HashMap<>();
    /** The Magician wishes counted this prep, by wish id, and whose: a refund gives back exactly these, once. */
    private final Map<UUID, UUID> counted = new HashMap<>();
    /** Wishes made by a child in distress: never refused, never counted. */
    private final Set<UUID> waived = new HashSet<>();
    /** player → kept thing → the tick it may fire again. */
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    /**
     * A wish is being submitted. {@code thing} names a kept thing (its recipe), for its cooldown; {@code tick} is the
     * server's tick count. An allowed fire in a battle starts that thing's cooldown.
     */
    public synchronized Decision atSubmit(UUID player, Phase phase, Kind kind, String thing, boolean distress,
                                          long tick, ArenaWishes w) {
        int u = used.getOrDefault(player, 0);
        if (distress) {
            return new Decision(null, u, w.magicianWishes(), 0);
        }
        if (phase == Phase.BATTLE) {
            if (kind != Kind.REPLAY) {
                return new Decision(Reason.BATTLE_INSTANT_ONLY, u, w.magicianWishes(), 0);
            }
            String key = thing == null ? "" : thing;
            Map<String, Long> mine = readyAt.computeIfAbsent(player, k -> new HashMap<>());
            Long ready = mine.get(key);
            if (ready != null && tick < ready) {
                return new Decision(Reason.RESTING, u, w.magicianWishes(), secondsUntil(tick, ready));
            }
            mine.put(key, tick + w.cooldownTicks());
            return new Decision(null, u, w.magicianWishes(), 0);
        }
        if (kind == Kind.TYPED && u >= w.magicianWishes()) {
            return new Decision(Reason.MAGICIAN_USED_UP, u, w.magicianWishes(), 0);
        }
        return new Decision(null, u, w.magicianWishes(), 0);
    }

    /** A wish from a child in distress: whatever happens at pickup, it is neither refused nor counted. */
    public synchronized void waive(UUID wish) {
        if (wish != null) {
            waived.add(wish);
        }
    }

    /**
     * The Magician picks a typed wish up: it starts now, and costs one of this prep's Magician wishes. Refused when
     * they are used up (two wishes sent at once on the last one), or when a battle began after it was sent. The same
     * wish picked up again (a re-aim) is never counted twice.
     */
    public synchronized Decision atPickup(UUID player, UUID wish, Phase phase, ArenaWishes w) {
        int u = used.getOrDefault(player, 0);
        if (wish != null && (waived.contains(wish) || counted.containsKey(wish))) {
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
        return new Decision(null, u + 1, w.magicianWishes(), 0, true);
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

    /** A battle is over (won, lost, or ended by a restart) and the arena is ready: counts start again, cooldowns end. */
    public synchronized void battleEnded() {
        used.clear();
        counted.clear();
        waived.clear();
        readyAt.clear();
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
