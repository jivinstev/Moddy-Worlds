package com.moddingfromamod.levels.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

/**
 * <b>The arena's settings command</b> (Moddy-Worlds#59): {@code /moddy arena config [<scope> [<key> [<value>|reset]]]}.
 * Pure: the keys, the layers, the guard and the words.
 *
 * <p><b>Layers</b>, lowest to highest; the highest that sets a key wins:
 * <ol>
 *   <li>the world's rules file ({@code arena_waves}, or its built-in default), {@link Source#RULES};</li>
 *   <li>{@code global}: every arena in this world, {@link Source#GLOBAL};</li>
 *   <li>one arena, by its name: {@code /moddy arena config <arena> …}, {@link Source#ARENA}.</li>
 * </ol>
 * Values are read once, at a battle's start, and a change is refused while a battle it would touch is running
 * ({@link #guard}). The grammar, {@code /moddy <world> config <scope> [<key> [<value>|reset]]}, is meant for every
 * Moddy World.
 *
 * <p><b>A key is one entry in {@link #KEYS}</b>: its name, type, range, help line, where the rules file keeps it, and
 * (for a battle timing) how it goes into the battle's table. Nothing else names a key. A stored value is checked again
 * whenever it is read, so a range a later version narrows clamps an old world's value instead of breaking its battle.
 */
public record ArenaConfig(Map<String, Integer> global, Map<String, Map<String, Integer>> perArena) {

    public ArenaConfig {
        global = Collections.unmodifiableMap(new TreeMap<>(global));
        Map<String, Map<String, Integer>> p = new TreeMap<>();
        perArena.forEach((a, m) -> {
            if (!m.isEmpty()) {
                p.put(a, Collections.unmodifiableMap(new TreeMap<>(m)));
            }
        });
        perArena = Collections.unmodifiableMap(p);
    }

    public static final ArenaConfig EMPTY = new ArenaConfig(Map.of(), Map.of());

    /** The scope word for every arena in the world. */
    public static final String GLOBAL = "global";

    /** The word that drops a layer's own value. */
    public static final String RESET = "reset";

    /** Where a key's value came from. */
    public enum Source {
        RULES("the rules file"), GLOBAL("all arenas"), ARENA("this arena");

        public final String words;

        Source(String words) {
            this.words = words;
        }
    }

    /** What a key's value is: a whole number in a range, or on/off (kept as 1/0). */
    public enum Kind { NUMBER, SWITCH }

    /** How a battle-timing key goes into the battle's table. */
    @FunctionalInterface
    public interface Into {
        ArenaWaves set(ArenaWaves waves, int value);
    }

    /**
     * A setting. {@code rules} is the value the rules file (or its default) gives it; {@code into} puts a value into
     * the battle's table (null for a key the battle reads itself, {@link #value}). Every key applies at the next battle.
     */
    public record Key(String name, Kind kind, int min, int max, String unit, String help,
                      ToIntFunction<ArenaWaves> rules, Into into) {

        static Key number(String name, int min, int max, String unit, String help, ToIntFunction<ArenaWaves> rules,
                          Into into) {
            return new Key(name, Kind.NUMBER, min, max, unit, help, rules, into);
        }

        static Key onOff(String name, boolean byDefault, String help) {
            return new Key(name, Kind.SWITCH, 0, 1, "", help, w -> byDefault ? 1 : 0, null);
        }

        /** {@code text} as this key's value, or null with no value; {@link #problem} says why. */
        Integer parse(String text) {
            String t = text.strip().toLowerCase(Locale.ROOT);
            if (kind == Kind.SWITCH) {
                return switch (t) {
                    case "on", "true", "yes", "1" -> 1;
                    case "off", "false", "no", "0" -> 0;
                    default -> null;
                };
            }
            try {
                int v = Integer.parseInt(t);
                return v < min || v > max ? null : v;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String problem(String text) {
            return kind == Kind.SWITCH ? name + " is on or off, not '" + text + "'"
                    : name + " must be a whole number of " + unit + " from " + min + " to " + max + ", not '" + text + "'";
        }

        /** A stored value, kept inside this key's range (a later version may have narrowed it). */
        int clamp(int v) {
            return Math.max(min, Math.min(max, v));
        }

        /** A value in words: "20 seconds", "on". */
        String say(int v) {
            return kind == Kind.SWITCH ? (v != 0 ? "on" : "off") : v + " " + unit;
        }
    }

    /** Every setting, in the order the list shows them. Adding one is one entry here. */
    public static final List<Key> KEYS = List.of(
            Key.number("breatherSeconds", ArenaWaves.MIN_BREATHER, ArenaWaves.MAX_BREATHER, "seconds",
                    "the wait between rounds: the break after a wave", ArenaWaves::breatherSeconds,
                    (w, v) -> new ArenaWaves(w.countdownSeconds(), v, w.waveSeconds(), w.waves(), w.limits(),
                            w.bossEvery(), w.generator(), w.lives(), w.bossBreatherSeconds())),
            Key.number("bossBreatherSeconds", ArenaWaves.MIN_BREATHER, ArenaWaves.MAX_BOSS_BREATHER, "seconds",
                    "the longer break after a boss wave", ArenaWaves::bossBreatherSeconds,
                    (w, v) -> new ArenaWaves(w.countdownSeconds(), w.breatherSeconds(), w.waveSeconds(), w.waves(),
                            w.limits(), w.bossEvery(), w.generator(), w.lives(), v)),
            Key.number("countdownSeconds", ArenaWaves.MIN_COUNTDOWN, ArenaWaves.MAX_COUNTDOWN, "seconds",
                    "the countdown before the first wave", ArenaWaves::countdownSeconds,
                    (w, v) -> new ArenaWaves(v, w.breatherSeconds(), w.waveSeconds(), w.waves(), w.limits(),
                            w.bossEvery(), w.generator(), w.lives(), w.bossBreatherSeconds())),
            Key.number("waveSeconds", ArenaWaves.MIN_WAVE_SECONDS, ArenaWaves.MAX_WAVE_SECONDS, "seconds",
                    "how long a wave may last before the battle is lost", ArenaWaves::waveSeconds,
                    (w, v) -> new ArenaWaves(w.countdownSeconds(), w.breatherSeconds(), v, w.waves(), w.limits(),
                            w.bossEvery(), w.generator(), w.lives(), w.bossBreatherSeconds())),
            Key.onOff(ArenaWishedWaves.CONFIG_KEY, true,
                    "the arena wishes its monsters (pillars, a ramp, champions); off: they are plainly spawned. Unset,"
                            + " the rules file's arena_wished_waves decides"),
            Key.onOff(ArenaRespawn.KEY, true,
                    "a fighter knocked out in a battle comes back in this arena's prep room, not at his bed or the"
                            + " world spawn"));

    /** The key called {@code name} (any case), or null. */
    public static Key key(String name) {
        for (Key k : KEYS) {
            if (k.name().equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }

    /** One key's value for an arena, and where it came from. */
    public record Effective(int value, Source source) {
    }

    /**
     * {@code key}'s value for {@code arena} (null: as "all arenas" sees it): arena over global over {@code rules} (the
     * rules file's value). A stored value is clamped to the key's range; a stored key no longer known is ignored.
     */
    public Effective effective(String key, String arena, int rules) {
        Key k = key(key);
        Integer a = arena == null ? null : perArena.getOrDefault(arena, Map.of()).get(key);
        if (a != null) {
            return new Effective(k == null ? a : k.clamp(a), Source.ARENA);
        }
        Integer g = global.get(key);
        return g != null ? new Effective(k == null ? g : k.clamp(g), Source.GLOBAL) : new Effective(rules, Source.RULES);
    }

    /** {@code key}'s value for {@code arena} over the rules' {@code waves} (null waves: the key's default). */
    public int value(Key key, String arena, ArenaWaves waves) {
        return effective(key.name(), arena, rulesValue(key, waves)).value();
    }

    /** What the rules file gives {@code key}; with no table, its lowest allowed value (a switch: its default). */
    static int rulesValue(Key key, ArenaWaves waves) {
        if (waves == null && key.into() != null) {
            return key.min();
        }
        return key.rules().applyAsInt(waves);
    }

    /** {@code waves} with this config's timings for {@code arena} on top: what a battle there starts with. */
    public ArenaWaves apply(ArenaWaves waves, String arena) {
        if (waves == null) {
            return null;
        }
        ArenaWaves out = waves;
        for (Key k : KEYS) {
            if (k.into() != null) {
                int v = effective(k.name(), arena, k.rules().applyAsInt(waves)).value();
                if (v != k.rules().applyAsInt(out)) {
                    out = k.into().set(out, v);
                }
            }
        }
        return out;
    }

    /** This config with {@code key} set to {@code value} at {@code scope} ({@link #GLOBAL} or an arena's name). */
    public ArenaConfig with(String scope, String key, int value) {
        if (GLOBAL.equals(scope)) {
            Map<String, Integer> g = new TreeMap<>(global);
            g.put(key, value);
            return new ArenaConfig(g, perArena);
        }
        Map<String, Map<String, Integer>> p = new TreeMap<>(perArena);
        Map<String, Integer> m = new TreeMap<>(p.getOrDefault(scope, Map.of()));
        m.put(key, value);
        p.put(scope, m);
        return new ArenaConfig(global, p);
    }

    /** This config with {@code scope}'s own value for {@code key} dropped. */
    public ArenaConfig without(String scope, String key) {
        if (GLOBAL.equals(scope)) {
            Map<String, Integer> g = new TreeMap<>(global);
            g.remove(key);
            return new ArenaConfig(g, perArena);
        }
        Map<String, Map<String, Integer>> p = new TreeMap<>(perArena);
        Map<String, Integer> m = new TreeMap<>(p.getOrDefault(scope, Map.of()));
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
            return "A battle is on in " + String.join(", ", new TreeSet<>(battlesOn))
                    + ". Change the settings for all arenas when no battle is on.";
        }
        return battlesOn.contains(scope) ? "A battle is on in " + scope + ". Change its settings when it is over."
                : null;
    }

    /**
     * What a {@code config} command does, as pure words: {@code args} is what followed the scope (empty, a key, a key
     * and a value, or a key and {@code reset}); {@code waves} is the rules' table (null: none in this world). The
     * caller stores {@link Outcome#config} when it {@link Outcome#changed}. {@code mayChange} false: an ask to change
     * is refused (not an operator). Anyone may read, during a battle too.
     */
    public Outcome run(String scope, List<String> args, ArenaWaves waves, Set<String> battlesOn, boolean mayChange) {
        String arena = GLOBAL.equals(scope) ? null : scope;
        String who = arena == null ? "all arenas" : scope;
        if (args.isEmpty()) {
            StringBuilder sb = new StringBuilder("Settings for " + who + " (each applies at the next battle):");
            for (Key k : KEYS) {
                Effective e = effective(k.name(), arena, rulesValue(k, waves));
                sb.append("\n  ").append(k.name()).append(" = ").append(k.say(e.value()))
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
            Effective e = effective(k.name(), arena, rulesValue(k, waves));
            return Outcome.said(this, k.name() + " for " + who + " is " + k.say(e.value()) + " (from "
                    + e.source().words + "): " + k.help() + ".");
        }
        if (!mayChange) {
            return Outcome.refused(this, "Only an operator may change the arena's settings.");
        }
        String why = guard(scope, battlesOn);
        if (why != null) {
            return Outcome.refused(this, why);
        }
        String v = args.get(1);
        if (v.strip().equalsIgnoreCase(RESET)) {
            ArenaConfig next = without(scope, k.name());
            Effective e = next.effective(k.name(), arena, rulesValue(k, waves));
            return Outcome.changed(next, k.name() + " for " + who + " is back to " + k.say(e.value()) + " (from "
                    + e.source().words + "), from the next battle.");
        }
        Integer value = k.parse(v);
        if (value == null) {
            return Outcome.refused(this, k.problem(v) + ".");
        }
        return Outcome.changed(with(scope, k.name(), value), k.name() + " for " + who + " is now " + k.say(value)
                + ", from the next battle.");
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
