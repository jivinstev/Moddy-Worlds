package com.moddingfromamod.levels.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * <b>The arena's settings command</b> (Moddy-Worlds#59): {@code /moddy arena config <scope> [<key> [<value>|reset]]}.
 * Pure: the keys, the layers, the guard and the words.
 *
 * <p><b>Layers</b>, lowest to highest; the highest that sets a key wins:
 * <ol>
 *   <li>the world's rules file ({@code arena_waves}, or its built-in default), {@link Source#RULES};</li>
 *   <li>{@code global}: every arena in this world, {@link Source#GLOBAL};</li>
 *   <li>one arena: {@code /moddy arena config <arena> …}, {@link Source#ARENA}.</li>
 * </ol>
 * Values are read once, at a battle's start, and a change is refused while a battle it would touch is running
 * ({@link #guard}). The grammar, {@code /moddy <world> config <scope> [<key> [<value>|reset]]}, is meant for every
 * Moddy World: a key is one {@link Key} in {@link #KEYS}.
 */
public record ArenaConfig(Map<String, Integer> global, Map<String, Map<String, Integer>> perArena) {

    public ArenaConfig {
        global = Map.copyOf(global);
        Map<String, Map<String, Integer>> p = new TreeMap<>();
        perArena.forEach((a, m) -> {
            if (!m.isEmpty()) {
                p.put(a, Map.copyOf(m));
            }
        });
        perArena = java.util.Collections.unmodifiableMap(p);
    }

    public static final ArenaConfig EMPTY = new ArenaConfig(Map.of(), Map.of());

    /** The scope word for every arena in the world. */
    public static final String GLOBAL = "global";

    /** Where a key's value came from. */
    public enum Source {
        RULES("the rules file"), GLOBAL("all arenas"), ARENA("this arena");

        public final String words;

        Source(String words) {
            this.words = words;
        }
    }

    /** A setting: its name, its whole-number range, and one line saying what it does. Every key applies at the next battle. */
    public record Key(String name, int min, int max, String unit, String help) {
        /** {@code text} as this key's value, or null with no value; {@link #problem} says why. */
        Integer parse(String text) {
            try {
                int v = Integer.parseInt(text.strip());
                return v < min || v > max ? null : v;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String problem(String text) {
            return name + " must be a whole number of " + unit + " from " + min + " to " + max + ", not '" + text + "'";
        }
    }

    /** Every setting, in the order the list shows them. Adding one is an entry here and a line in {@link #apply}. */
    public static final List<Key> KEYS = List.of(
            new Key("breatherSeconds", ArenaWaves.MIN_BREATHER, ArenaWaves.MAX_BREATHER, "seconds",
                    "the wait between rounds: the break after a wave"),
            new Key("bossBreatherSeconds", ArenaWaves.MIN_BREATHER, ArenaWaves.MAX_BOSS_BREATHER, "seconds",
                    "the longer break after a boss wave"),
            new Key("countdownSeconds", ArenaWaves.MIN_COUNTDOWN, ArenaWaves.MAX_COUNTDOWN, "seconds",
                    "the countdown before the first wave"),
            new Key("waveSeconds", ArenaWaves.MIN_WAVE_SECONDS, ArenaWaves.MAX_WAVE_SECONDS, "seconds",
                    "how long a wave may last before the battle is lost"));

    /** The key called {@code name} (any case), or null. */
    public static Key key(String name) {
        for (Key k : KEYS) {
            if (k.name().equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }

    /** One key's value for {@code arena}, and where it came from: arena over global over the rules' {@code rules}. */
    public record Effective(int value, Source source) {
    }

    public Effective effective(String key, String arena, int rules) {
        Integer a = arena == null ? null : perArena.getOrDefault(arena, Map.of()).get(key);
        if (a != null) {
            return new Effective(a, Source.ARENA);
        }
        Integer g = global.get(key);
        return g != null ? new Effective(g, Source.GLOBAL) : new Effective(rules, Source.RULES);
    }

    /** {@code waves} with this config's timings for {@code arena} on top: what a battle there starts with. */
    public ArenaWaves apply(ArenaWaves waves, String arena) {
        if (waves == null) {
            return null;
        }
        return new ArenaWaves(
                effective("countdownSeconds", arena, waves.countdownSeconds()).value(),
                effective("breatherSeconds", arena, waves.breatherSeconds()).value(),
                effective("waveSeconds", arena, waves.waveSeconds()).value(),
                waves.waves(), waves.limits(), waves.bossEvery(), waves.generator(), waves.lives(),
                effective("bossBreatherSeconds", arena, waves.bossBreatherSeconds()).value());
    }

    /** The value {@code waves} (the rules) gives {@code key}. */
    static int rulesValue(ArenaWaves waves, String key) {
        return switch (key) {
            case "breatherSeconds" -> waves.breatherSeconds();
            case "bossBreatherSeconds" -> waves.bossBreatherSeconds();
            case "countdownSeconds" -> waves.countdownSeconds();
            case "waveSeconds" -> waves.waveSeconds();
            default -> throw new IllegalArgumentException("no such key " + key);
        };
    }

    /** This config with {@code key} set to {@code value} at {@code scope} ({@link #GLOBAL} or an arena's name). */
    public ArenaConfig with(String scope, String key, int value) {
        if (GLOBAL.equals(scope)) {
            Map<String, Integer> g = new LinkedHashMap<>(global);
            g.put(key, value);
            return new ArenaConfig(g, perArena);
        }
        Map<String, Map<String, Integer>> p = new TreeMap<>(perArena);
        Map<String, Integer> m = new LinkedHashMap<>(p.getOrDefault(scope, Map.of()));
        m.put(key, value);
        p.put(scope, m);
        return new ArenaConfig(global, p);
    }

    /** This config with {@code scope}'s own value for {@code key} dropped. */
    public ArenaConfig without(String scope, String key) {
        if (GLOBAL.equals(scope)) {
            Map<String, Integer> g = new LinkedHashMap<>(global);
            g.remove(key);
            return new ArenaConfig(g, perArena);
        }
        Map<String, Map<String, Integer>> p = new TreeMap<>(perArena);
        Map<String, Integer> m = new LinkedHashMap<>(p.getOrDefault(scope, Map.of()));
        m.remove(key);
        p.put(scope, m);
        return new ArenaConfig(global, p);
    }

    /** Everything an arena that is gone had set, dropped (the arena was removed). */
    public ArenaConfig forget(String arena) {
        Map<String, Map<String, Integer>> p = new TreeMap<>(perArena);
        p.remove(arena);
        return new ArenaConfig(global, p);
    }

    /**
     * Why {@code scope} may not change now, or null when it may: a global change touches every arena, so none may have
     * a battle on; an arena's touches only it.
     */
    public static String guard(String scope, Set<String> battlesOn) {
        if (battlesOn.isEmpty()) {
            return null;
        }
        if (GLOBAL.equals(scope)) {
            return "A battle is on in " + String.join(", ", new java.util.TreeSet<>(battlesOn))
                    + ". Change the settings for all arenas when no battle is on.";
        }
        return battlesOn.contains(scope) ? "A battle is on in " + scope + ". Change its settings when it is over."
                : null;
    }

    /**
     * What a {@code config} command does, as pure words: {@code args} is what followed the scope (empty, a key, a key
     * and a value, or a key and {@code reset}). {@code rules} gives a key's rules-file value. The caller stores
     * {@link Outcome#config} when it {@link Outcome#changed}. {@code mayChange} false: an ask to change is refused (not an
     * operator).
     */
    public Outcome run(String scope, List<String> args, java.util.function.ToIntFunction<String> rules,
                       Set<String> battlesOn, boolean mayChange) {
        String arena = GLOBAL.equals(scope) ? null : scope;
        if (args.isEmpty()) {
            StringBuilder sb = new StringBuilder("Settings for " + (arena == null ? "all arenas" : scope)
                    + " (each applies at the next battle):");
            for (Key k : KEYS) {
                Effective e = arena == null ? globalEffective(k.name(), rules.applyAsInt(k.name()))
                        : effective(k.name(), arena, rules.applyAsInt(k.name()));
                sb.append("\n  ").append(k.name()).append(" = ").append(e.value()).append(' ').append(k.unit())
                        .append("  (from ").append(e.source().words).append(": ").append(k.help()).append(')');
            }
            return Outcome.said(this, sb.toString());
        }
        Key k = key(args.get(0));
        if (k == null) {
            return Outcome.refused(this, "There is no setting '" + args.get(0) + "'. The settings are: "
                    + String.join(", ", KEYS.stream().map(Key::name).toList()) + ".");
        }
        if (args.size() == 1) {
            Effective e = arena == null ? globalEffective(k.name(), rules.applyAsInt(k.name()))
                    : effective(k.name(), arena, rules.applyAsInt(k.name()));
            return Outcome.said(this, k.name() + " for " + (arena == null ? "all arenas" : scope) + " is " + e.value()
                    + " " + k.unit() + " (from " + e.source().words + "): " + k.help() + ".");
        }
        if (!mayChange) {
            return Outcome.refused(this, "Only an operator may change the arena's settings.");
        }
        String why = guard(scope, battlesOn);
        if (why != null) {
            return Outcome.refused(this, why);
        }
        String v = args.get(1);
        if (v.toLowerCase(Locale.ROOT).equals("reset")) {
            ArenaConfig next = without(scope, k.name());
            Effective e = arena == null ? next.globalEffective(k.name(), rules.applyAsInt(k.name()))
                    : next.effective(k.name(), arena, rules.applyAsInt(k.name()));
            return Outcome.changed(next, k.name() + " for " + (arena == null ? "all arenas" : scope)
                    + " is back to " + e.value() + " " + k.unit() + " (from " + e.source().words + "), from the next battle.");
        }
        Integer value = k.parse(v);
        if (value == null) {
            return Outcome.refused(this, k.problem(v) + ".");
        }
        return Outcome.changed(with(scope, k.name(), value), k.name() + " for " + (arena == null ? "all arenas" : scope)
                + " is now " + value + " " + k.unit() + ", from the next battle.");
    }

    /** A key as "all arenas" sees it: global over the rules. */
    Effective globalEffective(String key, int rules) {
        return effective(key, null, rules);
    }

    /** What a command did: the config after it, whether it changed (store it), whether it was refused, and the words. */
    public record Outcome(ArenaConfig config, boolean changed, boolean refused, String words) {
        static Outcome said(ArenaConfig c, String w) {
            return new Outcome(c, false, false, w);
        }

        static Outcome refused(ArenaConfig c, String w) {
            return new Outcome(c, false, true, w);
        }

        static Outcome changed(ArenaConfig c, String w) {
            return new Outcome(c, true, false, w);
        }
    }

    /** The stored shape: {@code {"global": {key: n}, "arenas": {name: {key: n}}}}. */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("global", global);
        out.put("arenas", perArena);
        return out;
    }
}
