package com.moddingfromamod.levels.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>The arena's wish tunables, as numbers</b>: how many Magician wishes a player may make in one prep (the time between
 * battles), how long a kept thing rests after it is fired from the hotbar in a battle, and how many hotbar wishes a
 * player may fire in one wave. Read from the world's rules file by {@link ArenaWishesRule}
 * ({@code "type": "arena_wishes"}). Pure: no Minecraft here.
 *
 * @param magicianWishes  Magician wishes each player may make in one prep (0 to {@link #MAX_MAGICIAN})
 * @param cooldownSeconds how long one kept thing rests after it is fired in a battle ({@link #MIN_COOLDOWN} to
 *                        {@link #MAX_COOLDOWN})
 * @param wishesPerWave   hotbar wishes each player may fire in one wave of a battle ({@link #MIN_PER_WAVE} to
 *                        {@link #MAX_PER_WAVE}); the count starts again when a wave is cleared
 */
public record ArenaWishes(int magicianWishes, int cooldownSeconds, int wishesPerWave) {

    public static final int MAX_MAGICIAN = 20;
    public static final int MIN_COOLDOWN = 1;
    public static final int MAX_COOLDOWN = 120;
    public static final int MIN_PER_WAVE = 1;
    public static final int MAX_PER_WAVE = 50;

    /**
     * The first hotbar wishes a wave, a guess to be tuned by playing: a wave lasts about a minute when it is fought
     * (3 minutes at most) and one wish rests 10 seconds, so firing one thing whenever it is ready would be about 6 a
     * wave; 5 is a little less, so a player picks his moments (and which wish) rather than pressing whatever is ready.
     */
    public static final int DEFAULT_PER_WAVE = 5;

    /**
     * The first values, guesses to be tuned by playing: 3 Magician wishes a prep, 10 seconds between fires, 5 hotbar
     * wishes a wave.
     */
    public static final ArenaWishes DEFAULT = new ArenaWishes(3, 10, DEFAULT_PER_WAVE);

    public ArenaWishes {
        String why = problem(magicianWishes, cooldownSeconds, wishesPerWave);
        if (why != null) {
            throw new IllegalArgumentException(why);
        }
    }

    /** The default number of hotbar wishes a wave. */
    public ArenaWishes(int magicianWishes, int cooldownSeconds) {
        this(magicianWishes, cooldownSeconds, DEFAULT_PER_WAVE);
    }

    /** What is wrong with these values, in words, or null. Pure. */
    public static String problem(int magicianWishes, int cooldownSeconds, int wishesPerWave) {
        if (magicianWishes < 0 || magicianWishes > MAX_MAGICIAN) {
            return "magicianWishes must be from 0 to " + MAX_MAGICIAN + ", not " + magicianWishes;
        }
        if (cooldownSeconds < MIN_COOLDOWN || cooldownSeconds > MAX_COOLDOWN) {
            return "cooldownSeconds must be from " + MIN_COOLDOWN + " to " + MAX_COOLDOWN + ", not " + cooldownSeconds;
        }
        if (wishesPerWave < MIN_PER_WAVE || wishesPerWave > MAX_PER_WAVE) {
            return "wishesPerWave must be from " + MIN_PER_WAVE + " to " + MAX_PER_WAVE + ", not " + wishesPerWave;
        }
        return null;
    }

    /** The cooldown in game ticks. */
    public int cooldownTicks() {
        return cooldownSeconds * ArenaMatch.TICKS_PER_SECOND;
    }

    /** A parse's result: the values, or why there are none. */
    public record Parsed(ArenaWishes wishes, String problem) {
        public boolean ok() {
            return wishes != null;
        }
    }

    /** The rule's keys, in order. */
    private static final List<String> KEYS = List.of("magicianWishes", "cooldownSeconds", "wishesPerWave");

    /**
     * The values a rule's params describe ({@code magicianWishes}, {@code cooldownSeconds}, {@code wishesPerWave}; each
     * optional, the default's when missing), or the problem with them. Notes ({@code _…}) are skipped.
     */
    public static Parsed parse(Map<String, ?> params) {
        Map<String, ?> p = params == null ? Map.of() : params;
        for (String key : p.keySet()) {
            if (!key.startsWith("_") && !KEYS.contains(key)) {
                return new Parsed(null, "unknown param '" + key + "' (" + String.join(", ", KEYS) + ")");
            }
        }
        String[] keys = KEYS.toArray(new String[0]);
        int[] v = {DEFAULT.magicianWishes, DEFAULT.cooldownSeconds, DEFAULT.wishesPerWave};
        for (int i = 0; i < keys.length; i++) {
            Object o = p.get(keys[i]);
            if (o == null) {
                continue;
            }
            if (o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    && Math.abs(n.doubleValue()) < 1_000_000) {
                v[i] = n.intValue();
            } else {
                return new Parsed(null, keys[i] + " must be a whole number, not " + o);
            }
        }
        String why = problem(v[0], v[1], v[2]);
        return why != null ? new Parsed(null, why) : new Parsed(new ArenaWishes(v[0], v[1], v[2]), null);
    }

    /** As a rule's params: an ordinary (mutable) map, never {@code Map.of}, which the core cannot take. */
    public Map<String, Object> toParams() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("magicianWishes", magicianWishes);
        out.put("cooldownSeconds", cooldownSeconds);
        out.put("wishesPerWave", wishesPerWave);
        return out;
    }
}
