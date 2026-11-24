package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>What the player picked on the control panel</b>: the wave size (a whole number, 1 to 10) and the difficulty
 * ({@link ArenaDifficulty}), and what a world's rules may fix or limit about them ({@link Limits}). Pure: no Minecraft.
 *
 * <p>The rules file's {@code arena_waves} are the base waves. The wave size scales every wave's mob counts
 * ({@link #scale}): a count times size / {@value #DEFAULT_SIZE}, rounded, at least 1 of each kind a wave has, and never
 * more than {@link ArenaWaves#MAX_PER_WAVE} a wave. So the default, {@value #DEFAULT_SIZE}, is the rules file's waves
 * exactly. The difficulty then swaps kinds and sets their health. Size first, then difficulty: a kind the difficulty
 * swaps in keeps the count the size gave.
 *
 * @param size       the wave size, {@value #MIN_SIZE} to {@value #MAX_SIZE}
 * @param difficulty the difficulty
 */
public record ArenaSettings(int size, ArenaDifficulty difficulty) {

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 10;
    /** The wave size at which the waves are the rules file's, exactly. */
    public static final int DEFAULT_SIZE = 5;

    /** What a new arena starts with: the rules file's waves, exactly. */
    public static final ArenaSettings DEFAULT = new ArenaSettings(DEFAULT_SIZE, ArenaDifficulty.DEFAULT);

    public ArenaSettings {
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        difficulty = difficulty == null ? ArenaDifficulty.DEFAULT : difficulty;
    }

    /** The settings saved as these values (an unknown or missing one is the default's). */
    public static ArenaSettings of(Integer size, String difficulty) {
        ArenaDifficulty d = ArenaDifficulty.byId(difficulty);
        return new ArenaSettings(size == null ? DEFAULT_SIZE : size, d == null ? ArenaDifficulty.DEFAULT : d);
    }

    /** A wave's count at this wave size: count times size / 5, rounded (half up), at least 1. Pure. */
    public static int scaledCount(int count, int size) {
        return Math.max(1, (int) Math.round(count * (double) size / DEFAULT_SIZE));
    }

    /**
     * The waves at wave size {@code size}: every group's count scaled ({@link #scaledCount}); a wave that would then
     * bring more than {@link ArenaWaves#MAX_PER_WAVE} loses one from its biggest group (the first of equals) until it
     * does not. The timings are unchanged. Pure.
     */
    public static ArenaWaves scale(ArenaWaves waves, int size) {
        if (size == DEFAULT_SIZE) {
            return waves;
        }
        List<ArenaWaves.Wave> out = new ArrayList<>();
        for (ArenaWaves.Wave w : waves.waves()) {
            int[] c = new int[w.groups().size()];
            int total = 0;
            for (int i = 0; i < c.length; i++) {
                c[i] = scaledCount(w.groups().get(i).count(), size);
                total += c[i];
            }
            while (total > ArenaWaves.MAX_PER_WAVE) {
                int big = 0;
                for (int i = 1; i < c.length; i++) {
                    if (c[i] > c[big]) {
                        big = i;
                    }
                }
                if (c[big] <= 1) {
                    break;
                }
                c[big]--;
                total--;
            }
            List<ArenaWaves.Group> groups = new ArrayList<>();
            for (int i = 0; i < c.length; i++) {
                groups.add(new ArenaWaves.Group(w.groups().get(i).mob(), c[i]));
            }
            out.add(new ArenaWaves.Wave(groups));
        }
        return waves.withWaves(out);
    }

    /** The waves a battle with these settings brings: the base waves scaled to the size, then at the difficulty. */
    public ArenaWaves apply(ArenaWaves base) {
        return base == null ? null : difficulty.apply(scale(base, size));
    }

    // ── what a world's rules may fix or limit ───────────────────────────────────────────────────────────────────────

    /**
     * What a world's {@code arena_waves} rule says about the panel (each optional; by default the player chooses):
     * {@code waveSize} fixes the size (the panel's size buttons then do nothing), {@code maxWaveSize} limits it;
     * {@code difficulty} fixes the difficulty, {@code maxDifficulty} limits it. A rule names a fixed value or a limit
     * for each, not both.
     *
     * @param size          the fixed size, or null when the player chooses
     * @param maxSize       the biggest size the player may choose
     * @param difficulty    the fixed difficulty, or null when the player chooses
     * @param maxDifficulty the hardest difficulty the player may choose
     */
    public record Limits(Integer size, int maxSize, ArenaDifficulty difficulty, ArenaDifficulty maxDifficulty) {

        /** The player chooses everything. */
        public static final Limits NONE = new Limits(null, MAX_SIZE, null, ArenaDifficulty.EXTREME);

        /** The params' keys this reads. */
        static final List<String> KEYS = List.of("waveSize", "maxWaveSize", "difficulty", "maxDifficulty");

        public boolean sizeFixed() {
            return size != null;
        }

        public boolean difficultyFixed() {
            return difficulty != null;
        }

        /** The limits a rule's params name, or the problem with them (other keys are not looked at). */
        static LimitsParsed parse(Map<String, ?> p) {
            Integer fixed = null;
            int max = MAX_SIZE;
            if (p.get("waveSize") != null && p.get("maxWaveSize") != null) {
                return new LimitsParsed(null, "give waveSize (the size is fixed) or maxWaveSize (the player chooses up"
                        + " to it), not both");
            }
            if (p.get("difficulty") != null && p.get("maxDifficulty") != null) {
                return new LimitsParsed(null, "give difficulty (it is fixed) or maxDifficulty (the player chooses up"
                        + " to it), not both");
            }
            for (String key : List.of("waveSize", "maxWaveSize")) {
                Object o = p.get(key);
                if (o == null) {
                    continue;
                }
                Integer v = o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                        && Math.abs(n.doubleValue()) < 1e6 ? n.intValue() : null;
                if (v == null || v < MIN_SIZE || v > MAX_SIZE) {
                    return new LimitsParsed(null, key + " must be a whole number from " + MIN_SIZE + " to " + MAX_SIZE
                            + ", not " + o);
                }
                if (key.equals("waveSize")) {
                    fixed = v;
                } else {
                    max = v;
                }
            }
            ArenaDifficulty fixedD = null;
            ArenaDifficulty maxD = ArenaDifficulty.EXTREME;
            for (String key : List.of("difficulty", "maxDifficulty")) {
                Object o = p.get(key);
                if (o == null) {
                    continue;
                }
                ArenaDifficulty d = o instanceof String s ? ArenaDifficulty.byId(s) : null;
                if (d == null) {
                    return new LimitsParsed(null, key + " must be one of " + ArenaDifficulty.ids() + ", not " + o);
                }
                if (key.equals("difficulty")) {
                    fixedD = d;
                } else {
                    maxD = d;
                }
            }
            return new LimitsParsed(new Limits(fixed, max, fixedD, maxD), null);
        }

        /** The keys that differ from {@link #NONE}, as a rule's params. */
        Map<String, Object> toParams() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (size != null) {
                out.put("waveSize", size);
            } else if (maxSize != MAX_SIZE) {
                out.put("maxWaveSize", maxSize);
            }
            if (difficulty != null) {
                out.put("difficulty", difficulty.id());
            } else if (maxDifficulty != ArenaDifficulty.EXTREME) {
                out.put("maxDifficulty", maxDifficulty.id());
            }
            return out;
        }
    }

    /** {@link Limits#parse}'s result. */
    record LimitsParsed(Limits limits, String problem) {
    }

    /** These settings as the rules allow them: a fixed value wins, a limit caps. */
    public ArenaSettings within(Limits limits) {
        Limits l = limits == null ? Limits.NONE : limits;
        int s = l.size() != null ? l.size() : Math.min(size, l.maxSize());
        ArenaDifficulty d = l.difficulty() != null ? l.difficulty()
                : difficulty.ordinal() > l.maxDifficulty().ordinal() ? l.maxDifficulty() : difficulty;
        return new ArenaSettings(s, d);
    }

    // ── pressing a selector ─────────────────────────────────────────────────────────────────────────────────────────

    /** What pressing a selector did. */
    public enum Result {
        /** the value moved one step */
        CHANGED,
        /** it was already at the end it was pushed toward (the smallest, the biggest, or the rules' limit) */
        AT_END,
        /** the rules fix it, so it does not move */
        FIXED
    }

    /** A press's answer: the settings after it, and what happened. */
    public record Step(ArenaSettings settings, Result result) {
    }

    /** The wave size one step up ({@code up}) or down, as the rules allow. Pure. */
    public Step stepSize(boolean up, Limits limits) {
        Limits l = limits == null ? Limits.NONE : limits;
        ArenaSettings now = within(l);
        if (l.sizeFixed()) {
            return new Step(now, Result.FIXED);
        }
        int next = now.size + (up ? 1 : -1);
        if (next < MIN_SIZE || next > l.maxSize()) {
            return new Step(now, Result.AT_END);
        }
        return new Step(new ArenaSettings(next, now.difficulty), Result.CHANGED);
    }

    /** The difficulty one step harder ({@code up}) or easier, as the rules allow. Pure. */
    public Step stepDifficulty(boolean up, Limits limits) {
        Limits l = limits == null ? Limits.NONE : limits;
        ArenaSettings now = within(l);
        if (l.difficultyFixed()) {
            return new Step(now, Result.FIXED);
        }
        int next = now.difficulty.ordinal() + (up ? 1 : -1);
        if (next < 0 || next > l.maxDifficulty().ordinal()) {
            return new Step(now, Result.AT_END);
        }
        return new Step(new ArenaSettings(now.size, ArenaDifficulty.values()[next]), Result.CHANGED);
    }
}
