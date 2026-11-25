package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>What the player picked on the control panel</b>: the difficulty ({@link ArenaDifficulty}) and the number of waves
 * (WAVES on the board: 5, 10, 15, 20 or Endless), and what a world's rules may fix or limit about them
 * ({@link Limits}). Pure: no Minecraft.
 *
 * <p>How many monsters a wave brings is not picked: it comes with the difficulty, its <b>wave size</b> (a whole number,
 * {@value #MIN_SIZE} to {@value #MAX_SIZE}; {@link ArenaDifficulty#size}, or a rules file's {@code waveSizes}). The
 * rules file's {@code arena_waves} are the base waves. The wave size scales every wave's mob counts ({@link #scale}): a
 * count times size / {@value #DEFAULT_SIZE}, rounded, at least 1 of each kind a wave has, and never more than
 * {@link ArenaWaves#MAX_PER_WAVE} a wave. So size {@value #DEFAULT_SIZE} (Normal's) is the rules file's waves exactly.
 * The difficulty then swaps kinds and sets their health. Size first, then the swaps: a kind the difficulty swaps in
 * keeps the count the size gave.
 *
 * <p>The number of waves: a battle brings the rules file's waves in order, and past their end waves made by its
 * generator ({@link ArenaGenerator}); {@value #ENDLESS} means Endless (waves until the fighters lose).
 *
 * @param difficulty the difficulty
 * @param waves      how many waves a battle has (a multiple of 5 on the panel), or {@value #ENDLESS} for Endless
 */
public record ArenaSettings(ArenaDifficulty difficulty, int waves) {

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 10;
    /** The wave size at which the waves are the rules file's, exactly (Normal's). */
    public static final int DEFAULT_SIZE = 5;

    /** The number of waves meaning Endless: waves come until the fighters lose. */
    public static final int ENDLESS = 0;
    /** The most waves a battle may be set to (a rules file's fixed or largest count). */
    public static final int MAX_WAVE_COUNT = 995;
    /** Every number of waves is a multiple of this (the panel's steps, and a rules file's fixed or largest count). */
    public static final int WAVE_STEP = 5;
    /** The number of waves a new arena starts with: the rules file's five, as before the WAVES selector. */
    public static final int DEFAULT_WAVES = 5;
    /** The WAVES selector's steps, in order, before Endless (the last step). */
    public static final List<Integer> WAVE_STEPS = List.of(5, 10, 15, 20);

    /** What a new arena starts with: Normal (the rules file's waves, exactly) and 5 waves. */
    public static final ArenaSettings DEFAULT = new ArenaSettings(ArenaDifficulty.DEFAULT, DEFAULT_WAVES);

    public ArenaSettings {
        difficulty = difficulty == null ? ArenaDifficulty.DEFAULT : difficulty;
        waves = waves <= ENDLESS ? ENDLESS : Math.min(MAX_WAVE_COUNT, waves);
    }

    /** These settings with the default number of waves (five). */
    public ArenaSettings(ArenaDifficulty difficulty) {
        this(difficulty, DEFAULT_WAVES);
    }

    /** True when the battle is Endless. */
    public boolean endless() {
        return waves == ENDLESS;
    }

    /** The settings saved as these values (an unknown or missing one is the default's; waves 0 is Endless). */
    public static ArenaSettings of(String difficulty, Integer waves) {
        ArenaDifficulty d = ArenaDifficulty.byId(difficulty);
        return new ArenaSettings(d == null ? ArenaDifficulty.DEFAULT : d, waves == null ? DEFAULT_WAVES : waves);
    }

    /** The wave size these settings bring under these rules: the difficulty's, or what the rules say. */
    public int size(Limits limits) {
        return (limits == null ? Limits.NONE : limits).sizeFor(difficulty);
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
            out.add(scaleWave(w, size));
        }
        return waves.withWaves(out);
    }

    /** One wave at wave size {@code size} ({@link #scale}'s rule for each wave). Pure. */
    public static ArenaWaves.Wave scaleWave(ArenaWaves.Wave w, int size) {
        if (size == DEFAULT_SIZE) {
            return w;
        }
        {
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
            return new ArenaWaves.Wave(groups);
        }
    }

    /**
     * The rules file's own waves at these settings' wave size and difficulty (the waves it authors, no more; the
     * number of waves, the generator and the bosses are {@link #run}'s).
     */
    public ArenaWaves apply(ArenaWaves base) {
        return base == null ? null : difficulty.apply(scale(base, size(base.limits())));
    }

    /** The battle these settings bring with these base waves ({@link ArenaRun}), or null when there are none. */
    public ArenaRun run(ArenaWaves base) {
        return base == null ? null : ArenaRun.of(base, within(base.limits()));
    }

    // ── what a world's rules may fix or limit ───────────────────────────────────────────────────────────────────────

    /**
     * What a world's {@code arena_waves} rule says about the panel and the wave sizes (each optional; by default the
     * player chooses, and each difficulty brings its own wave size):
     * <ul>
     *   <li>{@code waveSizes}: an object giving some difficulties another wave size ({@code {"easy": 2, "hard": 7}},
     *       each {@value #MIN_SIZE} to {@value #MAX_SIZE});</li>
     *   <li>{@code waveSize} and {@code maxWaveSize}, read as they were when the panel had its own MONSTERS selector:
     *       {@code waveSize} is now the wave size of every difficulty, {@code maxWaveSize} caps every difficulty's;
     *       {@code waveSize} and {@code waveSizes} are not given together;</li>
     *   <li>{@code difficulty} fixes the difficulty, {@code maxDifficulty} limits it;</li>
     *   <li>{@code waveCount} fixes the number of waves (a number, or {@code "endless"}), {@code maxWaveCount} limits it
     *       (and then there is no Endless).</li>
     * </ul>
     * A rule names a fixed value or a limit for each, not both.
     *
     * @param size          the wave size of every difficulty, or null when each has its own
     * @param maxSize       the biggest wave size any difficulty brings
     * @param sizes         difficulties whose wave size the rules set (the others bring {@link ArenaDifficulty#size})
     * @param difficulty    the fixed difficulty, or null when the player chooses
     * @param maxDifficulty the hardest difficulty the player may choose
     * @param count         the fixed number of waves ({@value #ENDLESS}: Endless), or null when the player chooses
     * @param maxCount      the most waves the player may choose, or {@value #ENDLESS} for no limit (Endless allowed)
     */
    public record Limits(Integer size, int maxSize, Map<ArenaDifficulty, Integer> sizes, ArenaDifficulty difficulty,
                         ArenaDifficulty maxDifficulty, Integer count, int maxCount) {

        /** The player chooses everything, and each difficulty brings its own wave size. */
        public static final Limits NONE = new Limits(null, MAX_SIZE, Map.of(), null, ArenaDifficulty.EXTREME, null,
                ENDLESS);

        /** The params' keys this reads. */
        static final List<String> KEYS = List.of("waveSize", "maxWaveSize", "waveSizes", "difficulty", "maxDifficulty",
                "waveCount", "maxWaveCount");

        public Limits {
            sizes = sizes == null ? Map.of() : Map.copyOf(sizes);
        }

        /** Limits with no wave sizes of the rules' own. */
        public Limits(Integer size, int maxSize, ArenaDifficulty difficulty, ArenaDifficulty maxDifficulty,
                      Integer count, int maxCount) {
            this(size, maxSize, Map.of(), difficulty, maxDifficulty, count, maxCount);
        }

        /** Limits on the size and the difficulty only (the player chooses the number of waves). */
        public Limits(Integer size, int maxSize, ArenaDifficulty difficulty, ArenaDifficulty maxDifficulty) {
            this(size, maxSize, Map.of(), difficulty, maxDifficulty, null, ENDLESS);
        }

        /**
         * The wave size difficulty {@code d} brings under these rules: {@code waveSize} when given, else the rules'
         * {@code waveSizes} for it, else its own ({@link ArenaDifficulty#size}); never above {@code maxWaveSize}.
         */
        public int sizeFor(ArenaDifficulty d) {
            ArenaDifficulty dd = d == null ? ArenaDifficulty.DEFAULT : d;
            int v = size != null ? size : sizes.getOrDefault(dd, dd.size());
            return Math.max(MIN_SIZE, Math.min(maxSize, v));
        }

        public boolean difficultyFixed() {
            return difficulty != null;
        }

        public boolean countFixed() {
            return count != null;
        }

        /**
         * The numbers of waves the WAVES selector steps through, in order: a fixed count alone; else 5, 10, 15, 20 and
         * Endless, those over {@code maxCount} left out and {@code maxCount} itself added (a limit of 10 steps 5, 10; a
         * limit of 30 steps 5, 10, 15, 20, 30). Endless ({@value #ENDLESS}) is last when it is allowed.
         */
        public List<Integer> waveOptions() {
            if (count != null) {
                return List.of(count);
            }
            List<Integer> out = new ArrayList<>();
            for (int n : WAVE_STEPS) {
                if (maxCount == ENDLESS || n <= maxCount) {
                    out.add(n);
                }
            }
            if (maxCount == ENDLESS) {
                out.add(ENDLESS);
            } else if (!out.contains(maxCount)) {
                out.add(maxCount);
            }
            return out;
        }

        /** The limits a rule's params name, or the problem with them (other keys are not looked at). */
        static LimitsParsed parse(Map<String, ?> p) {
            Integer fixed = null;
            int max = MAX_SIZE;
            for (String[] pair : new String[][] {{"waveSize", "maxWaveSize", "it is the wave size of every difficulty",
                    "every difficulty's wave size is at most it"}, {"waveSize", "waveSizes",
                    "it is the wave size of every difficulty", "a wave size for some difficulties"},
                    {"difficulty", "maxDifficulty", "it is fixed", "the player chooses up to it"},
                    {"waveCount", "maxWaveCount", "it is fixed", "the player chooses up to it"}}) {
                if (p.get(pair[0]) != null && p.get(pair[1]) != null) {
                    return new LimitsParsed(null, "give " + pair[0] + " (" + pair[2] + ") or " + pair[1]
                            + " (" + pair[3] + "), not both");
                }
            }
            for (String key : List.of("waveSize", "maxWaveSize")) {
                Object o = p.get(key);
                if (o == null) {
                    continue;
                }
                Integer v = whole(o);
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
            Map<ArenaDifficulty, Integer> sizes = new java.util.EnumMap<>(ArenaDifficulty.class);
            Object ws = p.get("waveSizes");
            if (ws != null) {
                if (!(ws instanceof Map<?, ?> m)) {
                    return new LimitsParsed(null, "waveSizes must be an object such as {\"easy\": 3, \"hard\": 7},"
                            + " not " + ws);
                }
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    if (k.startsWith("_")) {
                        continue;
                    }
                    ArenaDifficulty d = ArenaDifficulty.byId(k);
                    if (d == null) {
                        return new LimitsParsed(null, "waveSizes names '" + k + "'; it may name "
                                + ArenaDifficulty.ids());
                    }
                    Integer v = whole(e.getValue());
                    if (v == null || v < MIN_SIZE || v > MAX_SIZE) {
                        return new LimitsParsed(null, "waveSizes." + d.id() + " must be a whole number from " + MIN_SIZE
                                + " to " + MAX_SIZE + " (" + DEFAULT_SIZE + " is the waves exactly), not "
                                + e.getValue());
                    }
                    sizes.put(d, v);
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
            Integer fixedCount = null;
            int maxCount = ENDLESS;
            Object c = p.get("waveCount");
            if (c != null) {
                Integer v = c instanceof String s && s.trim().equalsIgnoreCase("endless") ? Integer.valueOf(ENDLESS)
                        : whole(c);
                if (v == null || (v != ENDLESS && (v < WAVE_STEP || v > MAX_WAVE_COUNT || v % WAVE_STEP != 0))) {
                    return new LimitsParsed(null, "waveCount must be a multiple of " + WAVE_STEP + " from " + WAVE_STEP
                            + " to " + MAX_WAVE_COUNT + " (a boss comes every 5th wave), or \"endless\", not " + c);
                }
                fixedCount = v;
            }
            Object mc = p.get("maxWaveCount");
            if (mc != null) {
                Integer v = whole(mc);
                if (v == null || v < WAVE_STEP || v > MAX_WAVE_COUNT || v % WAVE_STEP != 0) {
                    return new LimitsParsed(null, "maxWaveCount must be a multiple of " + WAVE_STEP + " from "
                            + WAVE_STEP + " to " + MAX_WAVE_COUNT + " (a boss comes every 5th wave), not " + mc);
                }
                maxCount = v;
            }
            return new LimitsParsed(new Limits(fixed, max, sizes, fixedD, maxD, fixedCount, maxCount), null);
        }

        private static Integer whole(Object o) {
            return o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    && Math.abs(n.doubleValue()) < 1e6 ? n.intValue() : null;
        }

        /** The keys that differ from {@link #NONE}, as a rule's params. */
        Map<String, Object> toParams() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (size != null) {
                out.put("waveSize", size);
            }
            if (maxSize != MAX_SIZE) {
                out.put("maxWaveSize", maxSize);
            }
            if (!sizes.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (ArenaDifficulty d : ArenaDifficulty.values()) {
                    if (sizes.containsKey(d)) {
                        m.put(d.id(), sizes.get(d));
                    }
                }
                out.put("waveSizes", m);
            }
            if (difficulty != null) {
                out.put("difficulty", difficulty.id());
            } else if (maxDifficulty != ArenaDifficulty.EXTREME) {
                out.put("maxDifficulty", maxDifficulty.id());
            }
            if (count != null) {
                out.put("waveCount", count == ENDLESS ? "endless" : count);
            } else if (maxCount != ENDLESS) {
                out.put("maxWaveCount", maxCount);
            }
            return out;
        }
    }

    /** {@link Limits#parse}'s result. */
    record LimitsParsed(Limits limits, String problem) {
    }

    /**
     * These settings as the rules allow them: a fixed value wins, a limit caps. A number of waves that is not one of
     * {@link Limits#waveOptions} becomes the largest option under it (Endless counts as the most), or the smallest.
     */
    public ArenaSettings within(Limits limits) {
        Limits l = limits == null ? Limits.NONE : limits;
        ArenaDifficulty d = l.difficulty() != null ? l.difficulty()
                : difficulty.ordinal() > l.maxDifficulty().ordinal() ? l.maxDifficulty() : difficulty;
        List<Integer> options = l.waveOptions();
        int w = options.get(0);
        for (int o : options) {
            if (rank(o) <= rank(waves)) {
                w = o;
            }
        }
        return new ArenaSettings(d, w);
    }

    /** A number of waves as a size to compare: Endless is the most. */
    private static long rank(int waves) {
        return waves == ENDLESS ? Long.MAX_VALUE : waves;
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
        return new Step(new ArenaSettings(ArenaDifficulty.values()[next], now.waves), Result.CHANGED);
    }

    /** The number of waves one step up ({@code up}: 5, 10, 15, 20, Endless) or down, as the rules allow. Pure. */
    public Step stepWaves(boolean up, Limits limits) {
        Limits l = limits == null ? Limits.NONE : limits;
        ArenaSettings now = within(l);
        if (l.countFixed()) {
            return new Step(now, Result.FIXED);
        }
        List<Integer> options = l.waveOptions();
        int next = options.indexOf(now.waves) + (up ? 1 : -1);
        if (next < 0 || next >= options.size()) {
            return new Step(now, Result.AT_END);
        }
        return new Step(new ArenaSettings(now.difficulty, options.get(next)), Result.CHANGED);
    }
}
