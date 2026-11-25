package com.moddingfromamod.levels.arena;

import com.moddingfromamod.wishbuilder.MakeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * <b>What a player is told when he puts a wish on his hotbar in the arena</b>: whether, at the step the battle is on (a
 * battle starts at the first, and prep is the first), that wish would be LOCKED or made smaller or weaker (FITTED), and
 * when that changes. Decided from the make's facts and the ladder alone, as {@link ArenaMakes} decides a fire, so the
 * line says what pressing it will really do. Pure: no Minecraft; {@link ArenaBattle} finds the new hotbar wishes and
 * says the line ({@link ArenaWords#pinNotice}).
 */
final class ArenaPinNotice {

    private ArenaPinNotice() {
    }

    /** What the line is about. */
    enum Type {
        /** nothing to say: it comes out as it is */
        NONE,
        /** its kind or a tag is locked in a battle until a step opens it ({@code after}) */
        LOCKED,
        /**
         * a number the core cannot lower is over the step's limit: locked (in prep too) until a step allows it
         * ({@code after}), or for every step ({@code after} 0)
         */
        TOO_MUCH,
        /** a number over the step's limit that the core lowers: made smaller or weaker now, more as steps open */
        FITTED
    }

    /** How it is held back, in the words' order: smaller, weaker, fewer, gentler. */
    enum Held { SMALLER, WEAKER, FEWER, GENTLER }

    /**
     * What of a fitted wish comes back as the battle goes on, so the line promises only that. The core refits a living
     * creature's health and hits when a step rises, and fits how many a fire makes at each fire; nothing re-fits a
     * held or hotbar item, or a creature's size, as steps open (the core keeps them as they were fitted).
     */
    enum Later {
        /** everything held back comes back as steps open */
        ALL,
        /** some of it comes back (a creature's strength), the rest stays as it is */
        SOME,
        /** none of it comes back in this arena */
        NONE
    }

    /** The numbers a step's rise gives back to a creature: its strength (refitted live) and how many (fitted per fire). */
    static final Set<Held> CREATURE_GROWS = Set.of(Held.WEAKER, Held.FEWER);

    /**
     * The answer: what, after how far (waves or bosses, as the ladder counts) for a lock, how it is held, and what of
     * that comes back later.
     */
    record Notice(Type type, int after, List<Held> held, Later later) {
        static final Notice NONE = new Notice(Type.NONE, 0, List.of(), Later.NONE);

        Notice {
            held = held == null ? List.of() : List.copyOf(held);
            later = later == null ? Later.NONE : later;
        }

        Notice(Type type, int after, List<Held> held) {
            this(type, after, held, Later.NONE);
        }
    }

    /** What of {@code held} comes back for a make of {@code kind} ({@link Later}). */
    static Later later(String kind, List<Held> held) {
        boolean creature = "creature".equals(kind) || "ride".equals(kind);
        if (!creature || held.isEmpty()) {
            return Later.NONE;
        }
        long back = held.stream().filter(CREATURE_GROWS::contains).count();
        return back == held.size() ? Later.ALL : back > 0 ? Later.SOME : Later.NONE;
    }

    /** The numbers no knob of a make lowers: over the limit, the core locks the make rather than fit it. */
    static final Set<String> NOT_FITTED = Set.of(MakeFacts.MOVEMENT_SPEED_ATTRIBUTE, MakeFacts.EXTENT_BLOCKS);

    /** How each fitted number holds a make back. */
    static Held heldBy(String number) {
        return switch (number) {
            case MakeFacts.SCALE_TIMES, MakeFacts.BUILDER_SIZE_STEP -> Held.SMALLER;
            case MakeFacts.HEALTH_HALF_HEARTS, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, MakeFacts.ARMOR_POINTS,
                 MakeFacts.KNOCKBACK_RESISTANCE_FRACTION -> Held.WEAKER;
            case MakeFacts.COUNT_MADE, MakeFacts.AMOUNT_ITEMS -> Held.FEWER;
            default -> Held.GENTLER;
        };
    }

    /**
     * What this wish does at the step the battle has reached ({@code reached}; 0 in prep, where a battle will start).
     * Null facts (a recipe the core could not find) say nothing. A kind or tag lock comes first; then a number the core
     * cannot lower; then the numbers it lowers.
     */
    static Notice of(ArenaLadder ladder, int reached, MakeFacts facts) {
        if (ladder == null || facts == null) {
            return Notice.NONE;
        }
        int until = ladder.locksUntil(facts.kind(), facts.tags(), facts.known());
        if (until > 0 && reached < until) {
            return new Notice(Type.LOCKED, until, List.of());
        }
        Map<String, Double> now = ladder.limits(reached);
        List<Held> held = new ArrayList<>();
        int tooMuch = -1;
        for (Map.Entry<String, Double> lim : now.entrySet()) {
            OptionalDouble v = facts.number(lim.getKey());
            if (v.isEmpty() || v.getAsDouble() <= lim.getValue() + 1e-9) {
                continue;
            }
            if (NOT_FITTED.contains(lim.getKey())) {
                tooMuch = Math.max(tooMuch, opensAt(ladder, reached, lim.getKey(), v.getAsDouble()));
            } else {
                Held h = heldBy(lim.getKey());
                if (!held.contains(h)) {
                    held.add(h);
                }
            }
        }
        if (tooMuch >= 0) {
            return new Notice(Type.TOO_MUCH, tooMuch, List.of());
        }
        if (held.isEmpty()) {
            return Notice.NONE;
        }
        held.sort(null);
        return new Notice(Type.FITTED, 0, held, later(facts.kind(), held));
    }

    /** The first step after {@code reached} whose limit on {@code number} allows {@code value}: its after, or 0 if none. */
    private static int opensAt(ArenaLadder ladder, int reached, String number, double value) {
        for (ArenaLadder.Step s : ladder.steps()) {
            if (s.after() <= reached) {
                continue;
            }
            Double lim = ladder.limits(s.after()).get(number);
            if (lim == null || value <= lim + 1e-9) {
                return s.after();
            }
        }
        return 0;
    }

    /** The most words a hotbar wish's label may have to be read as its name in "Your ... is locked". */
    static final int NAME_WORDS = 4;

    /**
     * The name a hotbar wish shows without the core's "✨ Wish: " in front ("Dragon", "Buddy the Golem"); empty (the
     * line then says "This wish") when it has none, or when its label is a sentence rather than a name (a ready-made's
     * "A meteor that makes a crater!": more than {@value #NAME_WORDS} words, or a sentence's mark in it).
     */
    static String nameOf(String hoverName) {
        String n = hoverName == null ? "" : hoverName.replace("\u2728", "").strip();
        if (n.startsWith("Wish:")) {
            n = n.substring("Wish:".length()).strip();
        }
        if (n.isEmpty() || n.split("\\s+").length > NAME_WORDS || n.matches(".*[!?.].*")) {
            return "";
        }
        return n;
    }
}
