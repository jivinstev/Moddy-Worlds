package com.moddingfromamod.levels.arena;

import com.moddingfromamod.wishbuilder.MakeFacts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * <b>The arena's unlock ladder, as data</b>: the limits the fighters' makes are fitted to, which rise as a battle goes
 * on, and the kinds and tags of make that stay locked until a step opens them ("no tornado on day 1"). The ladder is
 * a BATTLE's: every battle starts at the first step, the same for every fighter, and what it reached is gone when the
 * battle ends (so later waves get stronger wishes against stronger monsters, and the Champions board compares like with
 * like). Outside a battle (in prep) the first step holds, so what a player prepares is what he starts with. Read from the
 * world's rules file by {@link ArenaLadderRule} ({@code "type": "arena_ladder"}). Pure: no Minecraft, and no Builder id
 * or name anywhere: the ladder speaks only the core's fact vocabulary ({@code MakeFacts.NUMBERS}, {@code MakeFacts.TAGS},
 * {@code MakeFacts.KINDS}), so a core that adds Builder content needs no change here.
 *
 * <p>A step is "after N waves cleared (or N bosses beaten, {@link Count}) in this battle: these maxima". The step in
 * force is the last one whose {@code after} the battle has reached; a number that step does not name is unlimited, so a top step with no limits allows everything. The
 * core fits a make to the limits (smaller, fewer, gentler) and locks only what it cannot fit; the arena itself locks
 * only the kinds and tags this ladder keeps for later, because their strength is not a number the core can lower.
 *
 * @param progress what a step counts: waves cleared or bosses beaten in the battle ({@link Count})
 * @param steps    the steps, {@code after} rising from 0
 * @param locked   the kinds and tags locked until a step opens them
 */
public record ArenaLadder(Count progress, List<Step> steps, Opens locked) {

    /** What a step's {@code after} counts, in the battle on now. */
    public enum Count {
        /**
         * Waves cleared (the default): it rises in every battle, also the default five-wave one whose only boss is its
         * last wave, and a boss wave is just another wave cleared (the default table puts a bigger step there).
         */
        WAVES("waves"),
        /** Bosses beaten: nothing rises until the first boss falls. */
        BOSSES("bosses");

        private final String id;

        Count(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static Count byId(String id) {
            for (Count p : values()) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
            return null;
        }
    }

    /** Kinds and tags of make. */
    public record Opens(Set<String> kinds, Set<String> tags) {
        public static final Opens NONE = new Opens(Set.of(), Set.of());

        public Opens {
            kinds = kinds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(kinds));
            tags = tags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tags));
        }

        public boolean isEmpty() {
            return kinds.isEmpty() && tags.isEmpty();
        }

        Map<String, Object> toParams() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (!kinds.isEmpty()) {
                out.put("kinds", new ArrayList<>(sorted(kinds)));
            }
            if (!tags.isEmpty()) {
                out.put("tags", new ArrayList<>(sorted(tags)));
            }
            return out;
        }
    }

    /**
     * One step: from {@code after} waves cleared (or bosses beaten), these maxima ({@code MakeFacts.NUMBERS} key → its
     * most; a key it does not name is unlimited), and what it opens of the locked kinds and tags.
     */
    public record Step(int after, Map<String, Double> limits, Opens opens) {
        public Step {
            limits = limits == null ? Map.of() : java.util.Collections.unmodifiableMap(new TreeMap<>(limits));
            opens = opens == null ? Opens.NONE : opens;
        }
    }

    /** The most waves (or bosses) a step may wait for. */
    public static final int MAX_AFTER = 1000;
    /** The most steps a ladder may have. */
    public static final int MAX_STEPS = 20;

    /**
     * The first ladder, a guess to be tuned by playing, counted in waves cleared. The battle starts modest (a creature of
     * 20 hearts at most, a hit of two and a half hearts, about a stone sword, two at a time, the gentlest powers); it
     * rises a little after waves 2 and 4, so even the default five-wave battle meets its boss with stronger wishes than
     * it started with; then a bigger step after each boss wave (5, 10, 15, 20), as the monsters grow (the generator adds
     * half a monster and 4% health a wave past the rules file's, and each boss has more health than the last: 150, 200,
     * 250, 300), with one between (wave 8). Events open after the first boss, world rules after the second, and from
     * wave 20 nothing is limited. Numbers in the core's units: health and damage in health points (two to a heart),
     * durations in ticks. The Builder's strength pick (builderStrengthStep) is never limited: a wish is never refused for
     * being strong (no fire re-fits that pick, so a limit on it would lock the wish); what it makes is held by the
     * health, hit, reach and duration limits instead, fitted live on a creature, and comes back as steps open.
     */
    public static final ArenaLadder DEFAULT = new ArenaLadder(Count.WAVES, List.of(
            new Step(0, limits(MakeFacts.HEALTH_HALF_HEARTS, 40, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 5,
                    MakeFacts.SCALE_TIMES, 1.5, MakeFacts.BUILDER_SIZE_STEP, 2,
                    MakeFacts.COUNT_MADE, 2, MakeFacts.AMOUNT_ITEMS, 16, MakeFacts.RADIUS_BLOCKS, 4,
                    MakeFacts.DURATION_TICKS, 200), Opens.NONE),
            new Step(2, limits(MakeFacts.HEALTH_HALF_HEARTS, 60, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 6,
                    MakeFacts.SCALE_TIMES, 1.75, MakeFacts.BUILDER_SIZE_STEP, 2,
                    MakeFacts.COUNT_MADE, 2, MakeFacts.AMOUNT_ITEMS, 24, MakeFacts.RADIUS_BLOCKS, 5,
                    MakeFacts.DURATION_TICKS, 300), Opens.NONE),
            new Step(4, limits(MakeFacts.HEALTH_HALF_HEARTS, 80, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 7,
                    MakeFacts.SCALE_TIMES, 2, MakeFacts.BUILDER_SIZE_STEP, 3,
                    MakeFacts.COUNT_MADE, 3, MakeFacts.AMOUNT_ITEMS, 32, MakeFacts.RADIUS_BLOCKS, 6,
                    MakeFacts.DURATION_TICKS, 400), Opens.NONE),
            new Step(5, limits(MakeFacts.HEALTH_HALF_HEARTS, 120, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 9,
                    MakeFacts.SCALE_TIMES, 2.5, MakeFacts.BUILDER_SIZE_STEP, 3,
                    MakeFacts.COUNT_MADE, 4, MakeFacts.AMOUNT_ITEMS, 48, MakeFacts.RADIUS_BLOCKS, 8,
                    MakeFacts.DURATION_TICKS, 600), new Opens(Set.of("event"), Set.of())),
            new Step(8, limits(MakeFacts.HEALTH_HALF_HEARTS, 160, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 12,
                    MakeFacts.SCALE_TIMES, 3, MakeFacts.COUNT_MADE, 5, MakeFacts.AMOUNT_ITEMS, 64,
                    MakeFacts.RADIUS_BLOCKS, 10, MakeFacts.DURATION_TICKS, 800), Opens.NONE),
            new Step(10, limits(MakeFacts.HEALTH_HALF_HEARTS, 250, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 16,
                    MakeFacts.SCALE_TIMES, 4, MakeFacts.COUNT_MADE, 6, MakeFacts.RADIUS_BLOCKS, 12,
                    MakeFacts.DURATION_TICKS, 1000), new Opens(Set.of(), Set.of(MakeFacts.WORLD_RULE))),
            new Step(15, limits(MakeFacts.HEALTH_HALF_HEARTS, 400, MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 24,
                    MakeFacts.SCALE_TIMES, 5, MakeFacts.COUNT_MADE, 8, MakeFacts.RADIUS_BLOCKS, 16,
                    MakeFacts.DURATION_TICKS, 1200), Opens.NONE),
            new Step(20, Map.of(), Opens.NONE)),
            new Opens(Set.of("event"), Set.of(MakeFacts.WORLD_RULE)));

    public ArenaLadder {
        steps = List.copyOf(steps);
        locked = locked == null ? Opens.NONE : locked;
        progress = progress == null ? Count.WAVES : progress;
        String why = problem(steps, locked);
        if (why != null) {
            throw new IllegalArgumentException(why);
        }
    }

    private static Map<String, Double> limits(Object... kv) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        }
        return out;
    }

    // ── reading it for a player ──────────────────────────────────────────────────────────────────────────────────────

    /** The step in force (its index) for a battle that has reached {@code beaten}, as {@link #progress} counts. */
    public int stepIndex(int beaten) {
        int at = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).after() <= Math.max(0, beaten)) {
                at = i;
            }
        }
        return at;
    }

    /** The limits in force at {@code beaten}: a fact not named is unlimited. */
    public Map<String, Double> limits(int beaten) {
        int i = stepIndex(beaten);
        return i < 0 ? Map.of() : steps.get(i).limits();
    }

    /** The next step the battle can reach, or null at the top. */
    public Step next(int beaten) {
        int i = stepIndex(beaten);
        return i + 1 < steps.size() ? steps.get(i + 1) : null;
    }

    /**
     * A cheap stamp of the limits in force for {@code beaten}: it changes when the step changes, and when the ladder
     * itself does (a rules edit), so the core can tell two sets apart without comparing them.
     */
    public String stamp(int beaten) {
        return "arena-ladder-" + Integer.toHexString(java.util.Objects.hash(progress.id(), steps, locked)) + "-"
                + stepIndex(beaten);
    }

    /** After how far (waves or bosses) a locked kind or tag opens (0: it is not locked). */
    public int opensAfter(String kindOrTag, boolean tag) {
        if (!(tag ? locked.tags() : locked.kinds()).contains(kindOrTag)) {
            return 0;
        }
        for (Step s : steps) {
            if ((tag ? s.opens().tags() : s.opens().kinds()).contains(kindOrTag)) {
                return s.after();
            }
        }
        return 0;   // never happens: every lock is opened by a step (the ladder's check)
    }

    /**
     * After how far into a battle this make opens, from its facts alone: its kind, and its tags when the facts were
     * read from the thing itself ({@code known}; cautious facts carry every tag a thing could, which says nothing about
     * this one, so they are judged by their kind). 0 when nothing about it is locked. A kind or tag the ladder does not
     * name is never locked.
     */
    public int locksUntil(String kind, Collection<String> tags, boolean known) {
        int until = kind == null ? 0 : opensAfter(kind, false);
        if (known && tags != null) {
            for (String t : tags) {
                until = Math.max(until, opensAfter(t, true));
            }
        }
        return until;
    }

    /** True while this make is locked at {@code beaten}. */
    public boolean locked(String kind, Collection<String> tags, boolean known, int beaten) {
        int until = locksUntil(kind, tags, known);
        return until > 0 && beaten < until;
    }

    /** The kinds and tags still locked for {@code beaten}, each with how far it waits for, in the ladder's order. */
    public Map<String, Integer> stillLocked(int beaten) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String k : sorted(locked.kinds())) {
            int a = opensAfter(k, false);
            if (beaten < a) {
                out.put("kind:" + k, a);
            }
        }
        for (String t : sorted(locked.tags())) {
            int a = opensAfter(t, true);
            if (beaten < a) {
                out.put("tag:" + t, a);
            }
        }
        return out;
    }

    /** What rose and what opened going from {@code before} to {@code after} in the battle. */
    public record Rise(Map<String, double[]> rose, List<String> opened, boolean top) {
        /** True when nothing changed (the battle got further but is on the same step). */
        public boolean isEmpty() {
            return rose.isEmpty() && opened.isEmpty();
        }
    }

    /**
     * What changed: each number whose most rose ({@code [before, after]}, after {@code NaN} when it is now unlimited) and
     * each kind or tag opened ({@code kind:<id>} or {@code tag:<id>}); {@code top} when the step reached is the last.
     */
    public Rise rise(int before, int after) {
        int i0 = stepIndex(before);
        int i1 = stepIndex(after);
        if (i1 <= i0) {
            return new Rise(Map.of(), List.of(), false);
        }
        Map<String, Double> a = limits(before);
        Map<String, Double> b = limits(after);
        Map<String, double[]> rose = new LinkedHashMap<>();
        for (String k : MakeFacts.NUMBERS) {
            if (a.containsKey(k) && (!b.containsKey(k) || b.get(k) > a.get(k))) {
                rose.put(k, new double[] {a.get(k), b.containsKey(k) ? b.get(k) : Double.NaN});
            }
        }
        List<String> opened = new ArrayList<>();
        for (Map.Entry<String, Integer> e : stillLocked(before).entrySet()) {
            if (after >= e.getValue()) {
                opened.add(e.getKey());
            }
        }
        return new Rise(rose, opened, i1 == steps.size() - 1);
    }

    /** Where a battle is on this ladder: its waves cleared or its bosses beaten, as {@link #progress} counts. */
    public int reached(int wavesCleared, int bossesBeaten) {
        return progress == Count.BOSSES ? bossesBeaten : wavesCleared;
    }

    // ── checking and parsing ─────────────────────────────────────────────────────────────────────────────────────────

    /** What is wrong with these steps and locks, in words, or null. */
    static String problem(List<Step> steps, Opens locked) {
        if (steps.isEmpty()) {
            return "steps must have at least one step";
        }
        if (steps.size() > MAX_STEPS) {
            return "steps may have at most " + MAX_STEPS + " steps, not " + steps.size();
        }
        if (steps.get(0).after() != 0) {
            return "the first step must be after 0 (what a battle starts with, and prep), not after "
                    + steps.get(0).after();
        }
        for (String k : locked.kinds()) {
            if (!MakeFacts.KINDS.contains(k)) {
                return "locked names an unknown kind '" + k + "' (" + String.join(", ", MakeFacts.KINDS) + ")";
            }
        }
        for (String t : locked.tags()) {
            if (!MakeFacts.TAGS.contains(t)) {
                return "locked names an unknown tag '" + t + "' (" + String.join(", ", MakeFacts.TAGS) + ")";
            }
        }
        Set<String> opened = new LinkedHashSet<>();
        Map<String, Double> prev = null;
        int prevAfter = -1;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String where = "step " + (i + 1) + " (after " + s.after() + ")";
            if (s.after() <= prevAfter) {
                return where + ": each step must come after more than the one before (" + prevAfter + ")";
            }
            if (s.after() > MAX_AFTER) {
                return where + ": after must be at most " + MAX_AFTER;
            }
            for (Map.Entry<String, Double> e : s.limits().entrySet()) {
                if (!MakeFacts.NUMBERS.contains(e.getKey())) {
                    return where + ": unknown fact '" + e.getKey() + "' (" + String.join(", ", MakeFacts.NUMBERS) + ")";
                }
                double v = e.getValue();
                if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0 || v > 1_000_000) {
                    return where + ": " + e.getKey() + " must be a number above 0, not " + e.getValue();
                }
            }
            if (prev != null) {
                for (Map.Entry<String, Double> e : s.limits().entrySet()) {
                    Double was = prev.get(e.getKey());
                    if (was == null) {
                        return where + ": " + e.getKey() + " is limited here but not in the step before; limits only"
                                + " rise as a battle goes on";
                    }
                    if (e.getValue() < was) {
                        return where + ": " + e.getKey() + " is " + num(e.getValue()) + ", less than the step before ("
                                + num(was) + "); limits only rise as a battle goes on";
                    }
                }
            }
            for (String k : s.opens().kinds()) {
                if (!locked.kinds().contains(k)) {
                    return where + ": opens the kind '" + k + "', which locked does not name";
                }
                if (s.after() == 0) {
                    return where + ": the first step cannot open anything (leave it out of locked instead)";
                }
                if (!opened.add("kind:" + k)) {
                    return where + ": opens the kind '" + k + "' a second time";
                }
            }
            for (String t : s.opens().tags()) {
                if (!locked.tags().contains(t)) {
                    return where + ": opens the tag '" + t + "', which locked does not name";
                }
                if (s.after() == 0) {
                    return where + ": the first step cannot open anything (leave it out of locked instead)";
                }
                if (!opened.add("tag:" + t)) {
                    return where + ": opens the tag '" + t + "' a second time";
                }
            }
            prev = s.limits();
            prevAfter = s.after();
        }
        for (String k : locked.kinds()) {
            if (!opened.contains("kind:" + k)) {
                return "the kind '" + k + "' is locked and no step opens it; every lock says when it opens";
            }
        }
        for (String t : locked.tags()) {
            if (!opened.contains("tag:" + t)) {
                return "the tag '" + t + "' is locked and no step opens it; every lock says when it opens";
            }
        }
        return null;
    }

    /** A parse's result: the ladder, or why there is none. */
    public record Parsed(ArenaLadder ladder, String problem) {
        public boolean ok() {
            return ladder != null;
        }
    }

    private static final List<String> KEYS = List.of("progress", "steps", "locked");
    private static final List<String> STEP_KEYS = List.of("after", "limits", "opens");
    private static final List<String> OPENS_KEYS = List.of("kinds", "tags");

    /**
     * The ladder a rule's params describe ({@code progress}, {@code steps}, {@code locked}; {@code progress} optional,
     * {@code waves} when missing; {@code locked} optional, nothing locked when missing), or the problem with it. A
     * fact, kind or tag the core does not know is an error naming it. Notes ({@code _…}) are skipped.
     */
    public static Parsed parse(Map<String, ?> params) {
        Map<String, ?> p = params == null ? Map.of() : params;
        String unknown = unknownKey(p, KEYS);
        if (unknown != null) {
            return new Parsed(null, unknown);
        }
        Count progress = Count.WAVES;
        Object pr = p.get("progress");
        if (pr != null) {
            progress = Count.byId(String.valueOf(pr).toLowerCase(Locale.ROOT));
            if (progress == null) {
                return new Parsed(null, "progress must be waves or bosses, not " + pr);
            }
        }
        Opens locked = Opens.NONE;
        if (p.get("locked") != null) {
            Object o = opens(p.get("locked"), "locked");
            if (o instanceof String why) {
                return new Parsed(null, why);
            }
            locked = (Opens) o;
        }
        if (!(p.get("steps") instanceof List<?> raw)) {
            return new Parsed(null, "steps must be a list of steps, each {\"after\": waves, \"limits\": {...}}");
        }
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String where = "step " + (i + 1);
            if (!(raw.get(i) instanceof Map<?, ?> m)) {
                return new Parsed(null, where + " must be an object {\"after\": waves, \"limits\": {...}}");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> sm = (Map<String, ?>) m;
            unknown = unknownKey(sm, STEP_KEYS);
            if (unknown != null) {
                return new Parsed(null, where + ": " + unknown);
            }
            Object a = sm.get("after");
            if (!(a instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.doubleValue() < 0
                    || n.doubleValue() > MAX_AFTER) {
                return new Parsed(null, where + ": after must be a whole number from 0 to " + MAX_AFTER
                        + ", not " + a);
            }
            Map<String, Double> limits = new LinkedHashMap<>();
            Object l = sm.get("limits");
            if (l != null) {
                if (!(l instanceof Map<?, ?> lm)) {
                    return new Parsed(null, where + ": limits must be an object of fact maxima, not " + l);
                }
                for (Map.Entry<?, ?> e : lm.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    if (k.startsWith("_")) {
                        continue;
                    }
                    if (!MakeFacts.NUMBERS.contains(k)) {
                        return new Parsed(null, where + ": unknown fact '" + k + "' ("
                                + String.join(", ", MakeFacts.NUMBERS) + ")");
                    }
                    if (!(e.getValue() instanceof Number v)) {
                        return new Parsed(null, where + ": " + k + " must be a number, not " + e.getValue());
                    }
                    limits.put(k, v.doubleValue());
                }
            }
            Opens opens = Opens.NONE;
            if (sm.get("opens") != null) {
                Object o = opens(sm.get("opens"), where + ": opens");
                if (o instanceof String why) {
                    return new Parsed(null, why);
                }
                opens = (Opens) o;
            }
            steps.add(new Step(n.intValue(), limits, opens));
        }
        String why = problem(steps, locked);
        return why != null ? new Parsed(null, why) : new Parsed(new ArenaLadder(progress, steps, locked), null);
    }

    /** An {@code {"kinds": [...], "tags": [...]}} object, or the words of what is wrong with it. */
    private static Object opens(Object raw, String where) {
        if (!(raw instanceof Map<?, ?> m)) {
            return where + " must be an object {\"kinds\": [...], \"tags\": [...]}, not " + raw;
        }
        @SuppressWarnings("unchecked")
        Map<String, ?> om = (Map<String, ?>) m;
        String unknown = unknownKey(om, OPENS_KEYS);
        if (unknown != null) {
            return where + ": " + unknown;
        }
        List<Set<String>> out = new ArrayList<>();
        for (String key : OPENS_KEYS) {
            Set<String> s = new LinkedHashSet<>();
            Object v = om.get(key);
            if (v != null) {
                if (!(v instanceof List<?> list)) {
                    return where + ": " + key + " must be a list of names, not " + v;
                }
                List<String> vocab = key.equals("kinds") ? MakeFacts.KINDS : MakeFacts.TAGS;
                for (Object x : list) {
                    String name = String.valueOf(x);
                    if (!vocab.contains(name)) {
                        return where + ": unknown " + key.substring(0, key.length() - 1) + " '" + name + "' ("
                                + String.join(", ", vocab) + ")";
                    }
                    s.add(name);
                }
            }
            out.add(s);
        }
        return new Opens(out.get(0), out.get(1));
    }

    private static String unknownKey(Map<String, ?> p, List<String> keys) {
        for (String key : p.keySet()) {
            if (!key.startsWith("_") && !keys.contains(key)) {
                return "unknown param '" + key + "' (" + String.join(", ", keys) + ")";
            }
        }
        return null;
    }

    /** As a rule's params: ordinary (mutable) maps and lists, never {@code Map.of}, which the core cannot take. */
    public Map<String, Object> toParams() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("progress", progress.id());
        if (!locked.isEmpty()) {
            out.put("locked", locked.toParams());
        }
        List<Object> ss = new ArrayList<>();
        for (Step s : steps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("after", s.after());
            Map<String, Object> l = new LinkedHashMap<>();
            for (String k : MakeFacts.NUMBERS) {
                if (s.limits().containsKey(k)) {
                    l.put(k, s.limits().get(k));
                }
            }
            m.put("limits", l);
            if (!s.opens().isEmpty()) {
                m.put("opens", s.opens().toParams());
            }
            ss.add(m);
        }
        out.put("steps", ss);
        return out;
    }

    private static List<String> sorted(Collection<String> c) {
        List<String> out = new ArrayList<>(c);
        out.sort(null);
        return out;
    }

    static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
