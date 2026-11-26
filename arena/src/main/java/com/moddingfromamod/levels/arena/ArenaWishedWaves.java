package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.moddingfromamod.wishbuilder.BuilderCreatureStrength;
import com.moddingfromamod.wishbuilder.BuilderMobSizes;
import com.moddingfromamod.wishbuilder.BuilderSpec;

/**
 * <b>Wished waves</b> (Moddy-Worlds#55), the {@code arena_wished_waves} rule ({@link ArenaWishedWavesRule}): the arena
 * WISHES its wave monsters through the Builder (core's {@code WorldWishes}, #178) instead of plainly spawning them,
 * and its wishes grow each round. Pure: the numbers, their parsing, and the plan.
 *
 * <p>Every monster is a <b>Builder spec</b>, made by the Builder's own code:
 * <ul>
 *   <li>the wave's mobs are Builder <b>real mobs</b> ({@code mob:minecraft:husk}) at the {@link #ramp}'s size,
 *       How strong and speed for how far the battle has climbed;</li>
 *   <li>one in {@link #championEvery} is a <b>champion</b>: a Builder <b>design</b>, a canned body in the
 *       {@link #scheme} with a monster's powers, made by the world at the battle's start and wished as copies. Which
 *       body each tier of {@link #bodies} uses is drawn at random for each battle ({@link #pick}), so no two battles
 *       need look alike. A champion whose design is not made yet arrives last, up to {@link #championWaitSeconds}
 *       after its wave, as the wave's mob one rung up when it never is;</li>
 *   <li>the boss is a real mob, wished UNCAPPED when {@link #bossUncapped} (boss fights are meant to be hard).</li>
 * </ul>
 *
 * <p><b>Power is the ladder's, mirrored.</b> The core holds every capped monster at the limits a PLAYER may wish at
 * this point of the ladder, and never below the plain mob it stands in for. The wave COUNTS are the arena's.
 */
public record ArenaWishedWaves(boolean on, List<Rung> ramp, int championEvery, int maxDesignChampions,
                               int maxFullPillars, String scheme, List<Bodies> bodies, int championWaitSeconds,
                               boolean bossUncapped) {

    public ArenaWishedWaves {
        ramp = List.copyOf(ramp);
        bodies = List.copyOf(bodies);
    }

    /** From {@code after} waves cleared: a real mob's Builder size id ("" its own), How strong ("" its own) and speed. */
    public record Rung(int after, String mobsize, String strength, String speed) {
    }

    /** From {@code after} waves cleared, a champion is one of these canned Builder bodies (drawn once a battle). */
    public record Bodies(int after, List<String> pick) {
        public Bodies {
            pick = List.copyOf(pick);
        }
    }

    /**
     * The shipped values. The ramp never weakens in health or damage (the Builder's own table: Huge at How strong 2 hits
     * for x1.6, under Big at 3's x1.875, so each size step from Big comes with How strong 3). Rung 0 is the plain mob.
     * First values are guesses, to be tuned by playing.
     */
    public static final ArenaWishedWaves DEFAULT = new ArenaWishedWaves(true, List.of(
            new Rung(0, "", "", ""),
            new Rung(2, "", "2", "3"),
            new Rung(4, "5", "2", "3"),
            new Rung(5, "5", "3", "3"),
            new Rung(8, "6", "3", "3"),
            new Rung(10, "7", "3", "3"),
            new Rung(15, "8", "3", "3")),
            4, 4, 8, "shadow", List.of(
                    new Bodies(0, List.of("rock_golem", "ogre", "yeti")),
                    new Bodies(5, List.of("ogre", "yeti", "raptor", "rock_golem")),
                    new Bodies(10, List.of("t_rex", "raptor", "yeti"))),
            10, true);

    /** The A/B seam for a test or a dev run: {@code -Dmoddyarena.wishedWaves=false} turns it off whatever the rule says. */
    public boolean active() {
        return on && !"false".equalsIgnoreCase(System.getProperty("moddyarena.wishedWaves", "true"));
    }

    // ── the plan ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** One monster: its real-mob spec, and, for a champion with a design this battle, that design's spec. */
    public record Plan(BuilderSpec mob, BuilderSpec design, boolean champion, boolean slim) {
    }

    /** This battle's champion bodies, one per {@link #bodies} tier, drawn at its start. */
    public record Picks(List<String> perTier) {
        public Picks {
            perTier = List.copyOf(perTier);
        }
    }

    /** Draw this battle's champion bodies. */
    public Picks pick(Random random) {
        List<String> out = new ArrayList<>();
        for (Bodies b : bodies) {
            out.add(b.pick().get(random.nextInt(b.pick().size())));
        }
        return new Picks(out);
    }

    /** The rung index for {@code cleared} waves cleared. */
    int rungOf(int cleared) {
        int r = 0;
        for (int i = 0; i < ramp.size(); i++) {
            if (cleared >= ramp.get(i).after()) {
                r = i;
            }
        }
        return r;
    }

    /** {@code mobId} as a Builder real mob at rung {@code r} (clamped to the ramp). */
    BuilderSpec mob(String mobId, int r) {
        Rung g = ramp.get(Math.max(0, Math.min(ramp.size() - 1, r)));
        Map<String, String> opts = new LinkedHashMap<>();
        put(opts, "mobsize", g.mobsize());
        put(opts, "strength", g.strength());
        put(opts, "speed", g.speed());
        return new BuilderSpec(BuilderSpec.CURRENT, BuilderSpec.Kind.CREATURE, "mob:" + mobId, "", 2, List.of(), "", 1,
                "", "", opts);
    }

    /**
     * The champion's Builder design at rung {@code r} with this battle's {@code picks}: its tier's body in the scheme,
     * hostile and glowing, fireproof from rung 3 and leaping from rung 4, How strong 3 from rung 3.
     */
    BuilderSpec champion(int r, Picks picks) {
        int after = ramp.get(Math.max(0, Math.min(ramp.size() - 1, r))).after();
        int tier = 0;
        for (int i = 0; i < bodies.size(); i++) {
            if (after >= bodies.get(i).after()) {
                tier = i;
            }
        }
        String body = picks == null || tier >= picks.perTier().size() ? bodies.get(tier).pick().get(0)
                : picks.perTier().get(tier);
        List<String> powers = new ArrayList<>(List.of("power:hostile", "power:glowing"));
        if (r >= 3) {
            powers.add("power:fireproof");
        }
        if (r >= 4) {
            powers.add("power:leaps");
        }
        // "wild", not "": an empty temperament is friendly, and a champion must be on the monsters' side
        // (Moddy-Worlds#61: a friendly Shadow Rock golem, a guardian by its body, killed Rumble the Ravager)
        return new BuilderSpec(BuilderSpec.CURRENT, BuilderSpec.Kind.CREATURE, "preset:bodies/" + body, scheme, 2, powers,
                "wild", 1, "", title(scheme) + " " + title(body), Map.of("strength", r >= 3 ? "3" : "2"));
    }

    /** Every monster of {@code wave}, in spawn order, with {@code cleared} waves cleared and this battle's picks. */
    public List<Plan> plan(ArenaWaves.Wave wave, int cleared, Picks picks) {
        int r = rungOf(cleared);
        List<Plan> out = new ArrayList<>();
        int i = 0;
        int full = 0;
        int designs = 0;
        for (ArenaWaves.Group g : wave.groups()) {
            for (int k = 0; k < g.count(); k++, i++) {
                boolean champion = i % championEvery == 0;
                BuilderSpec design = null;
                if (champion && designs < maxDesignChampions && !bodies.isEmpty()) {
                    design = champion(r, picks);
                    designs++;
                }
                boolean slim = !champion || full >= maxFullPillars;
                if (!slim) {
                    full++;
                }
                out.add(new Plan(mob(g.mob(), champion ? r + 1 : r), design, champion, slim));
            }
        }
        return out;
    }

    /** The designs a battle at {@code cleared} waves cleared will want next: this rung's and the next one's. */
    public List<BuilderSpec> designsAhead(int cleared, Picks picks) {
        if (bodies.isEmpty()) {
            return List.of();
        }
        int r = rungOf(cleared);
        return r + 1 < ramp.size() ? List.of(champion(r, picks), champion(r + 1, picks)) : List.of(champion(r, picks));
    }

    // ── the rule's params ──────────────────────────────────────────────────────────────────────────────────────────

    public static final List<String> KEYS = List.of("on", "ramp", "championEvery", "maxDesignChampions",
            "maxFullPillars", "scheme", "bodies", "championWaitSeconds", "bossUncapped");

    /** A size by its Builder word ("normal", "big", "huge", "giant", "colossal", …) or id ("4"…"8"). */
    static String sizeId(String word) {
        if (word == null || word.isBlank() || word.equalsIgnoreCase("own")) {
            return "";
        }
        for (BuilderMobSizes.Level l : BuilderMobSizes.ALL) {
            if (l.id().equals(word) || l.name().equalsIgnoreCase(word)) {
                return l.id();
            }
        }
        return null;
    }

    static String speedId(String word) {
        if (word == null || word.isBlank() || word.equalsIgnoreCase("own")) {
            return "";
        }
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "fast", "3" -> "3";
            case "slow", "1" -> "1";
            default -> null;
        };
    }

    /** Parsed params, or the problem in words. */
    public record Parsed(ArenaWishedWaves waves, String problem) {
        public boolean ok() {
            return problem == null;
        }
    }

    /**
     * Every key optional (a missing one is {@link #DEFAULT}'s). A body or scheme the installed Builder does not have is
     * KEPT here, never a reason to refuse the file: a core that renames or drops one must not stop a world's rules from
     * loading. {@link #usable} leaves such a name out, with a warning, when a battle starts. {@code knownBody} and
     * {@code knownScheme} are kept for that.
     */
    public static Parsed parse(Map<String, ?> params, java.util.function.Predicate<String> knownBody,
                               java.util.function.Predicate<String> knownScheme) {
        Map<String, ?> p = params == null ? Map.of() : params;
        for (String key : p.keySet()) {
            if (!key.startsWith("_") && !KEYS.contains(key)) {
                return fail("unknown param '" + key + "' (" + String.join(", ", KEYS) + ")");
            }
        }
        ArenaWishedWaves d = DEFAULT;
        Boolean on = bool(p.get("on"), d.on());
        Boolean bossUncapped = bool(p.get("bossUncapped"), d.bossUncapped());
        if (on == null || bossUncapped == null) {
            return fail("on and bossUncapped must be true or false");
        }
        int[] n = {d.championEvery(), d.maxDesignChampions(), d.maxFullPillars(), d.championWaitSeconds()};
        String[] names = {"championEvery", "maxDesignChampions", "maxFullPillars", "championWaitSeconds"};
        int[][] range = {{2, 20}, {0, 8}, {0, 40}, {0, 60}};
        for (int i = 0; i < names.length; i++) {
            Object o = p.get(names[i]);
            if (o == null) {
                continue;
            }
            Integer v = whole(o);
            if (v == null || v < range[i][0] || v > range[i][1]) {
                return fail(names[i] + " must be a whole number from " + range[i][0] + " to " + range[i][1] + ", not "
                        + o);
            }
            n[i] = v;
        }
        String scheme = p.get("scheme") == null ? d.scheme() : String.valueOf(p.get("scheme"));
        if (scheme.isBlank() || !scheme.matches("[a-z0-9_]{1,40}")) {
            return fail("scheme must be a Builder colour scheme's id (shadow, …), not '" + scheme + "'");
        }
        List<Rung> ramp = d.ramp();
        if (p.get("ramp") != null) {
            if (!(p.get("ramp") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 20) {
                return fail("ramp must be a list of 1 to 20 rungs");
            }
            ramp = new ArrayList<>();
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> m)) {
                    return fail("each ramp rung must be {after, size, strength, speed}, not " + o);
                }
                for (Object k : m.keySet()) {
                    if (!List.of("after", "size", "strength", "speed").contains(String.valueOf(k))) {
                        return fail("unknown rung key '" + k + "' (after, size, strength, speed)");
                    }
                }
                Integer after = whole(m.get("after"));
                String size = sizeId(m.get("size") == null ? "" : String.valueOf(m.get("size")));
                Integer st = m.get("strength") == null ? Integer.valueOf(0) : whole(m.get("strength"));
                String speed = speedId(m.get("speed") == null ? "" : String.valueOf(m.get("speed")));
                if (after == null || after < 0 || after > 1000) {
                    return fail("a rung's after must be a whole number of waves cleared, 0 to 1000, not " + m.get("after"));
                }
                if (size == null) {
                    return fail("a rung's size must be a Builder size (normal, big, huge, giant, colossal…), not "
                            + m.get("size"));
                }
                if (st == null || st < 0 || st > 3) {
                    return fail("a rung's strength must be 1 to 3 (How strong), not " + m.get("strength"));
                }
                if (speed == null) {
                    return fail("a rung's speed must be fast or slow, not " + m.get("speed"));
                }
                ramp.add(new Rung(after, size, st == 0 ? "" : String.valueOf(st), speed));
            }
            if (ramp.get(0).after() != 0) {
                return fail("the first rung must be after 0");
            }
            for (int i = 1; i < ramp.size(); i++) {
                if (ramp.get(i).after() <= ramp.get(i - 1).after()) {
                    return fail("each rung must come after more waves than the one before");
                }
                if (weaker(ramp.get(i), ramp.get(i - 1))) {
                    return fail("the rung after " + ramp.get(i).after() + " is weaker than the one before it (the"
                            + " Builder's health or damage for its size and strength fell)");
                }
            }
        }
        List<Bodies> bodies = d.bodies();
        if (p.get("bodies") != null) {
            if (!(p.get("bodies") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 10) {
                return fail("bodies must be a list of 1 to 10 tiers {after, pick}");
            }
            bodies = new ArrayList<>();
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("pick") instanceof List<?> pick) || pick.isEmpty()) {
                    return fail("each bodies tier must be {after, pick: [body, …]}, not " + o);
                }
                Integer after = whole(m.get("after"));
                if (after == null || after < 0) {
                    return fail("a bodies tier's after must be a whole number of waves cleared, not " + m.get("after"));
                }
                List<String> names2 = new ArrayList<>();
                for (Object b : pick) {
                    String body = String.valueOf(b);
                    if (!body.matches("[a-z0-9_]{1,40}")) {
                        return fail("'" + body + "' is no Builder body's name (rock_golem, ogre, yeti, raptor, t_rex, …)");
                    }
                    names2.add(body);
                }
                bodies.add(new Bodies(after, names2));
            }
            if (bodies.get(0).after() != 0) {
                return fail("the first bodies tier must be after 0");
            }
        }
        return new Parsed(new ArenaWishedWaves(on, ramp, n[0], n[1], n[2], scheme, bodies, n[3], bossUncapped), null);
    }

    /** These wished waves with only what the installed Builder has, and a warning for each name left out. */
    public record Usable(ArenaWishedWaves waves, List<String> skipped) {
        public Usable {
            skipped = List.copyOf(skipped);
        }
    }

    /**
     * What a battle can use of these wished waves with the installed Builder: a scheme it does not have falls back to
     * {@link #DEFAULT}'s (or the Builder's own colours when that is gone too), a body it does not have is left out of
     * its tier, and a tier left with none is dropped. With no body left, every champion is its stand-in (the wave's mob
     * one rung up). Never refuses: a core upgrade that renames a body changes the champions, not whether there is a
     * battle.
     */
    public Usable usable(java.util.function.Predicate<String> knownBody, java.util.function.Predicate<String> knownScheme) {
        List<String> skipped = new ArrayList<>();
        String s = scheme;
        if (!knownScheme.test(s)) {
            String instead = knownScheme.test(DEFAULT.scheme()) ? DEFAULT.scheme() : "";
            skipped.add("scheme '" + s + "' is not in this Builder; " + (instead.isEmpty() ? "the Builder's own colours"
                    : "'" + instead + "'") + " instead");
            s = instead;
        }
        List<Bodies> kept = new ArrayList<>();
        for (Bodies b : bodies) {
            List<String> pick = new ArrayList<>();
            for (String body : b.pick()) {
                if (knownBody.test(body)) {
                    pick.add(body);
                } else {
                    skipped.add("body '" + body + "' (after " + b.after() + ") is not in this Builder; left out");
                }
            }
            if (!pick.isEmpty()) {
                kept.add(new Bodies(kept.isEmpty() ? 0 : b.after(), pick));
            }
        }
        if (kept.isEmpty() && !bodies.isEmpty()) {
            skipped.add("no champion body is left; every champion is its stand-in");
        }
        return new Usable(new ArenaWishedWaves(on, ramp, championEvery, maxDesignChampions, maxFullPillars, s, kept,
                championWaitSeconds, bossUncapped), skipped);
    }

    /** Back to the rule's own params (what the core stores and hands back). */
    public Map<String, Object> toParams() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("on", on);
        List<Object> r = new ArrayList<>();
        for (Rung g : ramp) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("after", g.after());
            if (!g.mobsize().isEmpty()) {
                m.put("size", BuilderMobSizes.of(g.mobsize()).name().toLowerCase(Locale.ROOT));
            }
            if (!g.strength().isEmpty()) {
                m.put("strength", Integer.parseInt(g.strength()));
            }
            if (!g.speed().isEmpty()) {
                m.put("speed", g.speed().equals("3") ? "fast" : "slow");
            }
            r.add(m);
        }
        out.put("ramp", r);
        out.put("championEvery", championEvery);
        out.put("maxDesignChampions", maxDesignChampions);
        out.put("maxFullPillars", maxFullPillars);
        out.put("scheme", scheme);
        List<Object> b = new ArrayList<>();
        for (Bodies t : bodies) {
            b.add(Map.of("after", t.after(), "pick", t.pick()));
        }
        out.put("bodies", b);
        out.put("championWaitSeconds", championWaitSeconds);
        out.put("bossUncapped", bossUncapped);
        return out;
    }

    /** Health and damage of a rung by the Builder's own table, on a 10/10 body (a ratio, never clamped). */
    static double[] power(Rung g) {
        BuilderMobSizes.Level size = BuilderMobSizes.of(g.mobsize().isEmpty() ? BuilderMobSizes.NORMAL : g.mobsize());
        int st = g.strength().isEmpty() ? BuilderCreatureStrength.OWN : Integer.parseInt(g.strength());
        return new double[] {BuilderCreatureStrength.health(10, size, st),
                BuilderCreatureStrength.damage(10, size, st, false, 0, false)};
    }

    static boolean weaker(Rung now, Rung before) {
        double[] a = power(now), b = power(before);
        return a[0] < b[0] || a[1] < b[1];
    }

    private static Boolean bool(Object o, boolean dflt) {
        if (o == null) {
            return dflt;
        }
        return o instanceof Boolean b ? b : null;
    }

    private static Integer whole(Object o) {
        if (o instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() == Math.rint(n.doubleValue())) {
            return (int) n.doubleValue();
        }
        return null;
    }

    private static Parsed fail(String why) {
        return new Parsed(null, why);
    }

    private static void put(Map<String, String> opts, String key, String v) {
        if (v != null && !v.isEmpty()) {
            opts.put(key, v);
        }
    }

    private static String title(String id) {
        String s = id.replace('_', ' ');
        if (s.equals("t rex")) {
            return "T-Rex";
        }
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The installed Builder bodies' names (for {@link #parse}), read from the core's catalogue. */
    public static boolean builderBody(String name) {
        return com.moddingfromamod.wishbuilder.Catalogue.presets().contains("bodies/" + name);
    }

    /** Is {@code id} a Builder colour scheme? */
    public static boolean builderScheme(String id) {
        return com.moddingfromamod.wishbuilder.Schemes.byId(id) != null;
    }
}
