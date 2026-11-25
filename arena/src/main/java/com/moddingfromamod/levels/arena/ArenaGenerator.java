package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * <b>The waves past the ones a rules file writes</b>, made from the wave's number alone, so a battle can have any
 * number of waves (or go on without end) with no hand-written list. Pure: no Minecraft.
 *
 * <p>For wave {@code n} after the rules file's last wave {@code L} (whose size is {@code S}):
 * <ul>
 *   <li><b>how many</b>: {@code round(S + addPerWave * (n - L))}, at most {@link ArenaWaves#MAX_PER_WAVE};</li>
 *   <li><b>which</b>: every kind whose {@code from} wave has come, shared out by weight (largest remainder, the
 *       first of equals first), each at least 1 while there are enough monsters for all (the newest kinds go first
 *       when there are not);</li>
 *   <li><b>how tough</b>: their most health times {@code min(maxHealth, 1 + healthPerWave * (n - L))}, so it rises
 *       slowly and stops (the rules file's own waves are times 1).</li>
 * </ul>
 * The control panel's wave size and difficulty, and the boss waves, are applied on top ({@link ArenaRun}).
 *
 * @param addPerWave    monsters added a wave (0 to 5)
 * @param healthPerWave the health added a wave, as a part of a mob's own (0 to 0.5)
 * @param maxHealth     the most a generated wave's mobs' health is multiplied by (1 to 5)
 * @param kinds         the kinds a generated wave brings (at least one)
 */
public record ArenaGenerator(double addPerWave, double healthPerWave, double maxHealth, List<Kind> kinds) {

    /** A kind of mob: from wave {@code from} on, with its share {@code weight}. */
    public record Kind(String mob, int from, int weight) {
    }

    /**
     * The default: half a monster more each wave, 4% more health each wave up to 2.5 times; husks, pillagers from wave
     * 3, a witch and vindicators from wave 4, piglin brutes from wave 7 (they never turn to zombies in the arena) and
     * ravagers from wave 12. None of them burns in daylight, climbs, flies or teleports, and they do not fight each
     * other.
     */
    public static final ArenaGenerator DEFAULT = new ArenaGenerator(0.5, 0.04, 2.5, List.of(
            new Kind("minecraft:husk", 1, 4),
            new Kind("minecraft:pillager", 3, 3),
            new Kind("minecraft:witch", 4, 1),
            new Kind("minecraft:vindicator", 4, 2),
            new Kind("minecraft:piglin_brute", 7, 2),
            new Kind("minecraft:ravager", 12, 1)));

    public ArenaGenerator {
        kinds = List.copyOf(kinds);
    }

    /** Wave {@code n}'s monsters, after {@code authored} written waves (n greater than their number). Pure. */
    public ArenaWaves.Wave wave(int n, List<ArenaWaves.Wave> authored) {
        int last = authored.size();
        int base = last == 0 ? 3 : authored.get(last - 1).size();
        int total = (int) Math.round(base + addPerWave * Math.max(0, n - last));
        total = Math.max(1, Math.min(ArenaWaves.MAX_PER_WAVE, total));
        List<Kind> open = new ArrayList<>();
        for (Kind k : kinds) {
            if (k.from() <= n) {
                open.add(k);
            }
        }
        if (open.isEmpty()) {
            open.add(kinds.get(0));
        }
        while (open.size() > total) {
            open.remove(open.size() - 1);   // too few for every kind: the newest wait
        }
        int[] c = new int[open.size()];
        int sum = 0;
        for (Kind k : open) {
            sum += k.weight();
        }
        int left = total - open.size();     // each kind has 1 first
        double[] rem = new double[c.length];
        int given = 0;
        for (int i = 0; i < c.length; i++) {
            double share = left * (double) open.get(i).weight() / sum;
            c[i] = 1 + (int) Math.floor(share);
            rem[i] = share - Math.floor(share);
            given += c[i] - 1;
        }
        while (given < left) {
            int best = 0;
            for (int i = 1; i < c.length; i++) {
                if (rem[i] > rem[best] + 1e-9) {
                    best = i;
                }
            }
            c[best]++;
            rem[best] = -1;
            given++;
        }
        List<ArenaWaves.Group> groups = new ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            groups.add(new ArenaWaves.Group(open.get(i).mob(), c[i]));
        }
        return new ArenaWaves.Wave(groups);
    }

    /** What wave {@code n}'s mobs' most health is multiplied by, after {@code authored} written waves. Pure. */
    public double health(int n, int authored) {
        if (n <= authored) {
            return 1.0;
        }
        return Math.min(maxHealth, 1.0 + healthPerWave * (n - authored));
    }

    // ── the rules file ──────────────────────────────────────────────────────────────────────────────────────────────

    private static final Set<String> KEYS = Set.of("addPerWave", "healthPerWave", "maxHealth", "kinds");

    /** {@link #parse}'s answer. */
    public record Parsed(ArenaGenerator generator, String problem) {
    }

    /**
     * The generator an {@code arena_waves} rule's {@code generator} object names (each key optional, the default's
     * otherwise), or the problem with it, in words. A kind is checked as a wave's mob is.
     */
    public static Parsed parse(Object raw, Predicate<String> living) {
        if (raw == null) {
            return new Parsed(DEFAULT, null);
        }
        if (!(raw instanceof Map<?, ?> m)) {
            return fail("generator must be an object such as {\"addPerWave\": 0.5}, not " + raw);
        }
        for (Object k : m.keySet()) {
            if (!String.valueOf(k).startsWith("_") && !KEYS.contains(String.valueOf(k))) {
                return fail("generator: unknown key '" + k + "' (addPerWave, healthPerWave, maxHealth, kinds)");
            }
        }
        double[] v = {DEFAULT.addPerWave, DEFAULT.healthPerWave, DEFAULT.maxHealth};
        String[] names = {"addPerWave", "healthPerWave", "maxHealth"};
        double[][] range = {{0, 5}, {0, 0.5}, {1, 5}};
        for (int i = 0; i < 3; i++) {
            Object o = m.get(names[i]);
            if (o == null) {
                continue;
            }
            if (!(o instanceof Number n) || n.doubleValue() < range[i][0] || n.doubleValue() > range[i][1]) {
                return fail("generator: " + names[i] + " must be a number from " + num(range[i][0]) + " to "
                        + num(range[i][1]) + ", not " + o);
            }
            v[i] = n.doubleValue();
        }
        List<Kind> kinds = DEFAULT.kinds;
        Object rk = m.get("kinds");
        if (rk != null) {
            if (!(rk instanceof List<?> list) || list.isEmpty() || list.size() > ArenaWaves.MAX_GROUPS) {
                return fail("generator: kinds must be a list of 1 to " + ArenaWaves.MAX_GROUPS
                        + " {\"mob\": id, \"from\": wave, \"weight\": n}");
            }
            kinds = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> km) || !(km.get("mob") instanceof String id) || id.isBlank()) {
                    return fail("generator: each kind needs a \"mob\", such as {\"mob\": \"husk\", \"from\": 1,"
                            + " \"weight\": 4}");
                }
                for (Object k : km.keySet()) {
                    String key = String.valueOf(k);
                    if (!key.startsWith("_") && !Set.of("mob", "from", "weight").contains(key)) {
                        return fail("generator: unknown key '" + k + "' in a kind (mob, from, weight)");
                    }
                }
                String mob = ArenaWaves.normalId(id);
                String why = ArenaWaves.mobProblem(mob, living);
                if (why != null) {
                    return fail("generator: " + why);
                }
                Integer from = km.get("from") == null ? Integer.valueOf(1) : whole(km.get("from"));
                Integer weight = km.get("weight") == null ? Integer.valueOf(1) : whole(km.get("weight"));
                if (from == null || from < 1 || from > ArenaSettings.MAX_WAVE_COUNT) {
                    return fail("generator: " + mob + "'s from must be a wave number from 1 to "
                            + ArenaSettings.MAX_WAVE_COUNT + ", not " + km.get("from"));
                }
                if (weight == null || weight < 1 || weight > 20) {
                    return fail("generator: " + mob + "'s weight must be a whole number from 1 to 20, not "
                            + km.get("weight"));
                }
                kinds.add(new Kind(mob, from, weight));
            }
        }
        return new Parsed(new ArenaGenerator(v[0], v[1], v[2], kinds), null);
    }

    /** As a rule's params (only when it is not the default). */
    Map<String, Object> toParams() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("addPerWave", addPerWave);
        out.put("healthPerWave", healthPerWave);
        out.put("maxHealth", maxHealth);
        List<Object> ks = new ArrayList<>();
        for (Kind k : kinds) {
            Map<String, Object> km = new LinkedHashMap<>();
            km.put("mob", k.mob());
            km.put("from", k.from());
            km.put("weight", k.weight());
            ks.add(km);
        }
        out.put("kinds", ks);
        return out;
    }

    private static Parsed fail(String why) {
        return new Parsed(null, why);
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static Integer whole(Object o) {
        return o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && Math.abs(n.doubleValue()) < 1e6
                ? n.intValue() : null;
    }
}
