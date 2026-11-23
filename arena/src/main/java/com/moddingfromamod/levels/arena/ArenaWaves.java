package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * <b>The arena's wave table, as numbers</b>: the waves of a battle, each a list of {mob id, count}, and its timings
 * (the countdown before the first wave, the breather between waves, how long a wave may last). Read from the world's
 * rules file by {@link ArenaWavesRule} ({@code "type": "arena_waves"}); every value lives in that file, none in code.
 * Pure: no Minecraft here. Whether an id names a living entity is asked through a predicate the caller supplies (the
 * game's registry in play, anything in a test).
 *
 * @param countdownSeconds the seconds between pressing the start button and the first wave
 * @param breatherSeconds  the seconds between a wave cleared and the next one
 * @param waveSeconds      how long one wave may last before the battle is lost (so a stuck mob cannot hang it)
 * @param waves            the waves, in order (at least one)
 */
public record ArenaWaves(int countdownSeconds, int breatherSeconds, int waveSeconds, List<Wave> waves) {

    public static final int MIN_COUNTDOWN = 3;
    public static final int MAX_COUNTDOWN = 60;
    public static final int MIN_BREATHER = 3;
    public static final int MAX_BREATHER = 120;
    public static final int MIN_WAVE_SECONDS = 30;
    public static final int MAX_WAVE_SECONDS = 1800;
    public static final int MAX_WAVES = 20;
    public static final int MAX_GROUPS = 10;
    public static final int MAX_COUNT = 20;
    /** The most mobs one wave may bring, all groups together. */
    public static final int MAX_PER_WAVE = 40;

    /** {@code count} mobs of one kind. */
    public record Group(String mob, int count) {
    }

    /** One wave: its groups, all spawned at once. */
    public record Wave(List<Group> groups) {
        public Wave {
            groups = List.copyOf(groups);
        }

        /** How many mobs the wave brings. */
        public int size() {
            return groups.stream().mapToInt(Group::count).sum();
        }
    }

    public ArenaWaves {
        waves = List.copyOf(waves);
    }

    /** How many waves a battle has. */
    public int count() {
        return waves.size();
    }

    /** Wave {@code n}, counting from 1. */
    public Wave wave(int n) {
        return waves.get(n - 1);
    }

    /**
     * Mobs that cannot be arena wave mobs, and why, in words. Anything else that is a living entity is accepted: the
     * table's author decides (a zombie or a skeleton burns in daylight, which makes an easy wave rather than a broken
     * one).
     */
    static final Map<String, String> REFUSED = Map.ofEntries(
            Map.entry("minecraft:player", "is a player"),
            Map.entry("minecraft:armor_stand", "does not fight, so its wave would never be cleared"),
            Map.entry("minecraft:spider", "climbs the walls"),
            Map.entry("minecraft:cave_spider", "climbs the walls"),
            Map.entry("minecraft:enderman", "teleports out of the arena"),
            Map.entry("minecraft:shulker", "teleports and floats out of reach"),
            Map.entry("minecraft:silverfish", "hides in the stone brick walls"),
            Map.entry("minecraft:creeper", "blows holes in the arena floor"),
            Map.entry("minecraft:ghast", "flies over the walls"),
            Map.entry("minecraft:happy_ghast", "flies over the walls"),
            Map.entry("minecraft:phantom", "flies over the walls"),
            Map.entry("minecraft:vex", "flies through the walls"),
            Map.entry("minecraft:blaze", "flies over the walls"),
            Map.entry("minecraft:bat", "flies over the walls"),
            Map.entry("minecraft:allay", "flies over the walls"),
            Map.entry("minecraft:bee", "flies over the walls"),
            Map.entry("minecraft:parrot", "flies over the walls"),
            Map.entry("minecraft:breeze", "jumps over the walls"),
            Map.entry("minecraft:evoker", "summons vexes, which fly through the walls"),
            Map.entry("minecraft:wither", "flies over the walls and breaks blocks"),
            Map.entry("minecraft:ender_dragon", "flies over the walls and breaks blocks"));

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> KEYS = Set.of("countdownSeconds", "breatherSeconds", "waveSeconds", "waves");

    /** A mob id as the table names it: lower case, {@code minecraft:} when no namespace is given. Pure. */
    static String normalId(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.contains(":") ? s : "minecraft:" + s;
    }

    /** A parse's result: the table, or why there is none. */
    public record Parsed(ArenaWaves waves, String problem) {
        public boolean ok() {
            return waves != null;
        }
    }

    /**
     * The table a rule's params describe, or the problem with them, in words. {@code params} holds plain values (numbers,
     * strings, lists, maps), as {@link ArenaWavesRule} reads them out of the file. {@code living} says whether an id
     * names a living entity the game knows.
     */
    public static Parsed parse(Map<String, ?> params, Predicate<String> living) {
        Map<String, ?> p = params == null ? Map.of() : params;
        for (String key : p.keySet()) {
            if (!key.startsWith("_") && !KEYS.contains(key)) {
                return fail("unknown param '" + key + "' (countdownSeconds, breatherSeconds, waveSeconds, waves)");
            }
        }
        int[] t = new int[3];
        String[] names = {"countdownSeconds", "breatherSeconds", "waveSeconds"};
        int[][] range = {{MIN_COUNTDOWN, MAX_COUNTDOWN}, {MIN_BREATHER, MAX_BREATHER}, {MIN_WAVE_SECONDS, MAX_WAVE_SECONDS}};
        for (int i = 0; i < 3; i++) {
            Integer v = whole(p.get(names[i]));
            if (v == null) {
                return fail(names[i] + " must be a whole number of seconds, not " + p.get(names[i]));
            }
            if (v < range[i][0] || v > range[i][1]) {
                return fail(names[i] + " must be from " + range[i][0] + " to " + range[i][1] + ", not " + v);
            }
            t[i] = v;
        }
        if (!(p.get("waves") instanceof List<?> rawWaves) || rawWaves.isEmpty()) {
            return fail("waves must be a list of waves, at least one");
        }
        if (rawWaves.size() > MAX_WAVES) {
            return fail("waves may hold at most " + MAX_WAVES + " waves, not " + rawWaves.size());
        }
        List<Wave> waves = new ArrayList<>();
        for (int w = 0; w < rawWaves.size(); w++) {
            String at = "wave " + (w + 1);
            if (!(rawWaves.get(w) instanceof List<?> rawGroups) || rawGroups.isEmpty()) {
                return fail(at + " must be a list of {\"mob\": id, \"count\": n}, at least one");
            }
            if (rawGroups.size() > MAX_GROUPS) {
                return fail(at + " may name at most " + MAX_GROUPS + " groups, not " + rawGroups.size());
            }
            List<Group> groups = new ArrayList<>();
            for (Object g : rawGroups) {
                if (!(g instanceof Map<?, ?> m)) {
                    return fail(at + ": each entry must be {\"mob\": id, \"count\": n}, not " + g);
                }
                for (Object k : m.keySet()) {
                    if (!"mob".equals(k) && !"count".equals(k) && !String.valueOf(k).startsWith("_")) {
                        return fail(at + ": unknown key '" + k + "' (mob, count)");
                    }
                }
                if (!(m.get("mob") instanceof String rawId) || rawId.isBlank()) {
                    return fail(at + ": each entry needs a \"mob\", a mob id such as minecraft:husk");
                }
                String id = normalId(rawId);
                if (!ID.matcher(id).matches()) {
                    return fail(at + ": '" + rawId + "' is not a mob id (such as minecraft:husk)");
                }
                String why = REFUSED.get(id);
                if (why != null) {
                    return fail(at + ": " + id + " cannot be an arena mob: it " + why);
                }
                if (living != null && !living.test(id)) {
                    return fail(at + ": " + id + " is not a living mob the game knows");
                }
                Integer count = whole(m.get("count"));
                if (count == null || count < 1 || count > MAX_COUNT) {
                    return fail(at + ": " + id + "'s count must be a whole number from 1 to " + MAX_COUNT + ", not "
                            + m.get("count"));
                }
                groups.add(new Group(id, count));
            }
            Wave wave = new Wave(groups);
            if (wave.size() > MAX_PER_WAVE) {
                return fail(at + " brings " + wave.size() + " mobs; a wave may bring at most " + MAX_PER_WAVE);
            }
            waves.add(wave);
        }
        return new Parsed(new ArenaWaves(t[0], t[1], t[2], waves), null);
    }

    /** The table as plain values, the shape {@link #parse} reads (what the rule type hands the core). */
    public Map<String, Object> toParams() {
        List<Object> ws = new ArrayList<>();
        for (Wave w : waves) {
            List<Object> gs = new ArrayList<>();
            for (Group g : w.groups()) {
                Map<String, Object> m = new LinkedHashMap<>();   // a plain map: the core may ask it about any key
                m.put("mob", g.mob());
                m.put("count", g.count());
                gs.add(m);
            }
            ws.add(gs);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("countdownSeconds", countdownSeconds);
        out.put("breatherSeconds", breatherSeconds);
        out.put("waveSeconds", waveSeconds);
        out.put("waves", ws);
        return out;
    }

    private static Parsed fail(String why) {
        return new Parsed(null, why);
    }

    private static Integer whole(Object o) {
        if (o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && Math.abs(n.doubleValue()) < 1e6) {
            return n.intValue();
        }
        return null;
    }
}
