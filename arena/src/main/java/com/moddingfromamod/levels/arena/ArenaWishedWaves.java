package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.moddingfromamod.wishbuilder.BuilderSpec;

/**
 * <b>Wished waves</b> (Moddy-Worlds#55): the arena WISHES its wave monsters through the Builder (core's
 * {@code WorldWishes}, #178) instead of plainly spawning them, and its wishes grow each round. Pure: the plan only.
 *
 * <p>Every monster is a <b>Builder spec</b>, made by the Builder's own code:
 * <ul>
 *   <li>the wave's mobs are Builder <b>real mobs</b> ({@code mob:minecraft:husk}) with the Builder's size, How strong
 *       and speed picks ({@link #RAMP});</li>
 *   <li>one in {@link #CHAMPION_EVERY} is a <b>champion</b>: a Builder <b>design</b>, a canned body in the
 *       {@code shadow} scheme with the monster's powers ({@link #champion}), made once by the world and wished as a
 *       copy; while a design is still being made, or past {@link #MAX_DESIGN_CHAMPIONS} a wave, a champion is the
 *       wave's mob one rung up;</li>
 *   <li>the boss is a real mob wished UNCAPPED (boss fights are meant to be hard), with its own table's numbers.</li>
 * </ul>
 *
 * <p><b>Power is the ladder's, mirrored.</b> The core holds every capped monster at the limits a PLAYER may wish at this
 * point of the ladder, and never below the plain mob it stands in for. So a monster is never weaker than the one it
 * replaces, and never stronger than the strongest creature a child may wish against it. The wave COUNTS are the
 * arena's as they always were.
 */
public final class ArenaWishedWaves {

    /** A rung: from this many waves cleared, a real mob's Builder size, How strong and speed picks. */
    public record Rung(int after, String mobsize, String strength, String speed) {
    }

    /**
     * Each rung is at least the last in health AND damage (the Builder's own table: Huge at strength 2 hits for x1.6,
     * under Big at 3's x1.875, so each size step from Big comes with How strong 3). Rung 0 names no strength or speed:
     * the plain mob, as the waves always were, under pillars.
     */
    public static final List<Rung> RAMP = List.of(
            new Rung(0, "", "", ""),
            new Rung(2, "", "2", "3"),
            new Rung(4, "5", "2", "3"),
            new Rung(5, "5", "3", "3"),
            new Rung(8, "6", "3", "3"),
            new Rung(10, "7", "3", "3"),
            new Rung(15, "8", "3", "3"));

    /** The canned Builder bodies a champion is made from, toughest last, by rung. */
    public static final List<String> CHAMPION_BODIES = List.of("rock_golem", "ogre", "yeti", "raptor", "ogre",
            "t_rex", "t_rex");

    public static final String SCHEME = "shadow";
    public static final int CHAMPION_EVERY = 4;
    /** Champions that are Builder designs (rig creatures, ~40–60 displays each), at most per wave. */
    public static final int MAX_DESIGN_CHAMPIONS = 4;
    /** Full pillars per wave (~53 displays each); the rest are slim. */
    public static final int MAX_FULL_PILLARS = 8;

    /** The A/B seam: {@code -Dmoddyarena.wishedWaves=false} spawns waves exactly as before. */
    public static boolean on() {
        return !"false".equalsIgnoreCase(System.getProperty("moddyarena.wishedWaves", "true"));
    }

    /**
     * One monster of a wave, as the arena wishes it: {@code mob} is its real-mob spec; {@code design} is the champion's
     * Builder design spec when it is one (wished as a copy once made, else {@code mob} stands in).
     */
    public record Plan(BuilderSpec mob, BuilderSpec design, boolean champion, boolean slim) {
    }

    private ArenaWishedWaves() {
    }

    /** The rung index for {@code cleared} waves cleared. */
    static int rungOf(int cleared) {
        int r = 0;
        for (int i = 0; i < RAMP.size(); i++) {
            if (cleared >= RAMP.get(i).after()) {
                r = i;
            }
        }
        return r;
    }

    /** {@code mobId} as a Builder real mob at rung {@code r}. */
    static BuilderSpec mob(String mobId, int r) {
        Rung g = RAMP.get(Math.max(0, Math.min(RAMP.size() - 1, r)));
        Map<String, String> opts = new LinkedHashMap<>();
        put(opts, "mobsize", g.mobsize());
        put(opts, "strength", g.strength());
        put(opts, "speed", g.speed());
        return new BuilderSpec(BuilderSpec.CURRENT, BuilderSpec.Kind.CREATURE, "mob:" + mobId, "", 2, List.of(), "", 1,
                "", "", opts);
    }

    /**
     * The champion's Builder design at rung {@code r}: a canned body in the shadow scheme, hostile and glowing,
     * fireproof from rung 3 and leaping from rung 4, at the rung's How strong (3 from rung 3).
     */
    static BuilderSpec champion(int r) {
        int i = Math.max(0, Math.min(CHAMPION_BODIES.size() - 1, r));
        List<String> powers = new ArrayList<>(List.of("power:hostile", "power:glowing"));
        if (r >= 3) {
            powers.add("power:fireproof");
        }
        if (r >= 4) {
            powers.add("power:leaps");
        }
        String strength = r >= 3 ? "3" : "2";
        return new BuilderSpec(BuilderSpec.CURRENT, BuilderSpec.Kind.CREATURE, "preset:bodies/" + CHAMPION_BODIES.get(i),
                SCHEME, 2, powers, "", 1, "", "Shadow " + title(CHAMPION_BODIES.get(i)), Map.of("strength", strength));
    }

    /** Every monster of {@code wave}, in spawn order, with {@code cleared} waves cleared so far. */
    public static List<Plan> plan(ArenaWaves.Wave wave, int cleared) {
        int r = rungOf(cleared);
        List<Plan> out = new ArrayList<>();
        int i = 0;
        int full = 0;
        int designs = 0;
        for (ArenaWaves.Group g : wave.groups()) {
            for (int k = 0; k < g.count(); k++, i++) {
                boolean champion = i % CHAMPION_EVERY == 0;
                BuilderSpec design = null;
                if (champion && designs < MAX_DESIGN_CHAMPIONS) {
                    design = champion(r);
                    designs++;
                }
                boolean slim = !champion || full >= MAX_FULL_PILLARS;
                if (!slim) {
                    full++;
                }
                out.add(new Plan(mob(g.mob(), champion ? r + 1 : r), design, champion, slim));
            }
        }
        return out;
    }

    /** The designs a battle at {@code cleared} waves cleared will want next: this rung's and the next one's. */
    public static List<BuilderSpec> designsAhead(int cleared) {
        int r = rungOf(cleared);
        return r + 1 < RAMP.size() ? List.of(champion(r), champion(r + 1)) : List.of(champion(r));
    }

    private static void put(Map<String, String> opts, String key, String v) {
        if (v != null && !v.isEmpty()) {
            opts.put(key, v);
        }
    }

    private static String title(String id) {
        String s = id.replace('_', ' ');
        return s.equals("t rex") ? "T-Rex" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
