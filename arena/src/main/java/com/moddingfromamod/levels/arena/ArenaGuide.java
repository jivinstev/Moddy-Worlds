package com.moddingfromamod.levels.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>The numbers the Arena Guide book reads</b>: the guide's pages ({@code worlds/arena.json}, {@code guide.book}) name
 * no number of their own; a page that says how many lives a player has says {@code {arena_lives}}, and the core fills
 * it in when it gives the book, from the values the world's charge hands it ({@code WishCharge.guideValues()},
 * answered by {@link ArenaCharge}). So a rules edit cannot make the book wrong. Pure: no Minecraft.
 *
 * <p>A key is left out when its rule turns the thing off (no boss waves, no Magician wishes): the core then leaves
 * that page out of the book rather than show a child "{arena_boss_every}" or "every 0th wave".
 */
final class ArenaGuide {

    /** The book's title and author, as {@code worlds/arena.json} names them (a test holds the two together). */
    static final String TITLE = "Arena Guide";
    static final String AUTHOR = "Moddy";

    /** The hotbar's slots, then the bag's: an inventory's slots 0 to 8 are the hotbar, 9 to 35 the bag. */
    static final int HOTBAR = 9;
    static final int BAG_END = 36;

    /** How long after his first arrival the core's gift of the book is looked for, in ticks (a minute). */
    static final int SHELVE_TICKS = 1200;

    /** Every placeholder a page may name, in the order the book reads them. */
    static final List<String> KEYS = List.of("arena_lives", "arena_boss_every", "arena_break_seconds",
            "arena_boss_break_seconds", "arena_magician_wishes", "arena_rest_seconds", "arena_wishes_per_wave",
            "arena_ladder_health", "arena_ladder_hits", "arena_ladder_events_open");

    private ArenaGuide() {
    }

    /** Health points as a number of hearts, "20" or "2.5". */
    static String heartsNumber(double halfHearts) {
        String h = ArenaWords.hearts(halfHearts);
        return h.substring(0, h.indexOf(' '));
    }

    /**
     * Where the guide book the core gave him on his first arrival goes: from the hotbar slot it landed in to the first
     * empty slot of his bag, so his hotbar stays for the wishes he fights with. {@code empty} says which of the
     * inventory's slots are empty; -1 when the book is not on the hotbar or the bag has no room (then it stays).
     */
    static int bagSlot(int bookSlot, boolean[] empty) {
        if (bookSlot < 0 || bookSlot >= HOTBAR || empty == null) {
            return -1;
        }
        for (int i = HOTBAR; i < Math.min(BAG_END, empty.length); i++) {
            if (empty[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The guide's values for these rules. {@code waves} null (a world whose rules name no waves) gives the waves'
     * defaults for lives and boss waves, and no break lengths; {@code wishes} null gives {@link ArenaWishes#DEFAULT}.
     */
    static Map<String, String> values(ArenaWaves waves, ArenaWishes wishes) {
        return values(waves, wishes, ArenaLadder.DEFAULT);
    }

    /**
     * The guide's values for these rules and this unlock ladder: its first step's health and hits, in hearts (each left
     * out when the first step does not limit it), and when events open (left out when the ladder does not lock them).
     * {@code ladder} null gives {@link ArenaLadder#DEFAULT}.
     */
    static Map<String, String> values(ArenaWaves waves, ArenaWishes wishes, ArenaLadder ladder) {
        ArenaWishes w = wishes == null ? ArenaWishes.DEFAULT : wishes;
        Map<String, String> out = new LinkedHashMap<>();
        out.put("arena_lives", Integer.toString(waves == null ? ArenaWaves.DEFAULT_LIVES : waves.lives()));
        int bossEvery = waves == null ? ArenaWaves.DEFAULT_BOSS_EVERY : waves.bossEvery();
        if (bossEvery > 0) {
            out.put("arena_boss_every", Integer.toString(bossEvery));
        }
        if (waves != null) {
            out.put("arena_break_seconds", Integer.toString(waves.breatherSeconds()));
            out.put("arena_boss_break_seconds", Integer.toString(waves.bossBreatherSeconds()));
        }
        if (w.magicianWishes() > 0) {
            out.put("arena_magician_wishes", Integer.toString(w.magicianWishes()));
        }
        out.put("arena_rest_seconds", Integer.toString(w.cooldownSeconds()));
        out.put("arena_wishes_per_wave", Integer.toString(w.wishesPerWave()));
        ArenaLadder l = ladder == null ? ArenaLadder.DEFAULT : ladder;
        Map<String, Double> first = l.limits(0);
        if (first.containsKey(com.moddingfromamod.wishbuilder.MakeFacts.HEALTH_HALF_HEARTS)) {
            out.put("arena_ladder_health", heartsNumber(first.get(com.moddingfromamod.wishbuilder.MakeFacts.HEALTH_HALF_HEARTS)));
        }
        if (first.containsKey(com.moddingfromamod.wishbuilder.MakeFacts.ATTACK_DAMAGE_HALF_HEARTS)) {
            out.put("arena_ladder_hits",
                    heartsNumber(first.get(com.moddingfromamod.wishbuilder.MakeFacts.ATTACK_DAMAGE_HALF_HEARTS)));
        }
        int events = l.opensAfter("event", false);
        if (events > 0) {
            out.put("arena_ladder_events_open", ArenaWords.opensAt(l.progress(), events));
        }
        return out;
    }
}
