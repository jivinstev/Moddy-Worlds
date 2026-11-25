package com.moddingfromamod.levels.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>The numbers of the wished creatures' battle roles</b> ({@link ArenaRoles}): which creature fights, which one is a
 * tank, and how much a healer heals and a tank holds. Read from the world's rules file by {@link ArenaCreaturesRule}
 * ({@code "type": "arena_creatures"}); every key is optional, and a world whose rules carry none gets {@link #DEFAULT}.
 * Pure: no Minecraft here.
 *
 * @param fighterAttack  a creature whose own attack (its attack damage, in health points) is at least this is a
 *                       Fighter (0.5 to 50)
 * @param tankHealth     a creature that does not fight and has at least this much most health is a Tank (10 to 1000)
 * @param tankWidth      a creature that does not fight and is at least this wide, in blocks, is a Tank whatever its
 *                       health (0.5 to 5)
 * @param healAmount     what one heal gives a fighter, in health points (0.5 to 4; 2 is one heart)
 * @param healSeconds    how often a fighter can be healed, in seconds, by all his healers together (2 to 20)
 * @param healRadius     how near, in blocks, a fighter must be to a healer to be healed (2 to 16)
 * @param tauntRadius    how near, in blocks, a wave's monster must be to a tank to turn to it (3 to 16)
 * @param tankResistance the Resistance level a tank has while it holds monsters (0 none to 3; each level takes a fifth
 *                       off the damage it takes)
 */
public record ArenaCreatures(double fighterAttack, double tankHealth, double tankWidth, double healAmount,
                             int healSeconds, double healRadius, double tauntRadius, int tankResistance) {

    /**
     * At most this much health a second, all a player's healers together: a little under half of what the weakest
     * monster of a wave does to him (a husk on Easy, 2.5 health a second), so a healer keeps him going and never makes
     * him unbeatable.
     */
    public static final double MAX_HEAL_PER_SECOND = 1.0;

    /** The rule's keys, in order. */
    static final List<String> KEYS = List.of("fighterAttack", "tankHealth", "tankWidth", "healAmount", "healSeconds",
            "healRadius", "tauntRadius", "tankResistance");

    private static final double[] MIN = {0.5, 10, 0.5, 0.5, 2, 2, 3, 0};
    private static final double[] MAX = {50, 1000, 5, 4, 20, 16, 16, 3};
    /** The keys that are whole numbers. */
    private static final boolean[] WHOLE = {false, false, false, false, true, false, false, true};

    /**
     * The first values, guesses to be tuned by playing, each chosen against the game's own numbers:
     * <ul>
     *   <li>{@code fighterAttack} 3: what a zombie's punch takes; a creature that hits at least as hard as the monsters
     *       it meets fights them (a wolf, a cat, a bear, a golem), one that hits softer (a bee, a goat) helps another
     *       way;</li>
     *   <li>{@code tankHealth} 40: twice a player's health and twice a husk's; {@code tankWidth} 1.3: as wide as a
     *       horse, a panda or a golem, the creatures a child sees as big;</li>
     *   <li>a heal of 2 (one heart) every 4 seconds within 6 blocks: half a health a second, a fifth of what one husk on
     *       Easy does to a player who stands still, so a fighter must still fight and dodge;</li>
     *   <li>monsters within 8 blocks of a tank turn to it, and a tank holding them has Resistance I (a fifth less
     *       damage), so it stands a little longer than its health alone would let it.</li>
     * </ul>
     */
    public static final ArenaCreatures DEFAULT = new ArenaCreatures(3.0, 40.0, 1.3, 2.0, 4, 6.0, 8.0, 1);

    public ArenaCreatures {
        String why = problem(fighterAttack, tankHealth, tankWidth, healAmount, healSeconds, healRadius, tauntRadius,
                tankResistance);
        if (why != null) {
            throw new IllegalArgumentException(why);
        }
    }

    /** What is wrong with these values, in words, or null. Pure. */
    public static String problem(double fighterAttack, double tankHealth, double tankWidth, double healAmount,
                                 int healSeconds, double healRadius, double tauntRadius, int tankResistance) {
        double[] v = {fighterAttack, tankHealth, tankWidth, healAmount, healSeconds, healRadius, tauntRadius,
                tankResistance};
        for (int i = 0; i < v.length; i++) {
            if (!(v[i] >= MIN[i] && v[i] <= MAX[i])) {
                return KEYS.get(i) + " must be from " + num(MIN[i]) + " to " + num(MAX[i]) + ", not " + num(v[i]);
            }
        }
        if (healAmount / healSeconds > MAX_HEAL_PER_SECOND + 1e-9) {
            return "healAmount " + num(healAmount) + " every " + healSeconds + " seconds heals more than "
                    + num(MAX_HEAL_PER_SECOND) + " health a second; a healer must never make a player unbeatable";
        }
        return null;
    }

    /** At most how much health a second a fighter gets from all his healers together. */
    public double healPerSecond() {
        return healAmount / healSeconds;
    }

    /** How often a fighter can be healed, in game ticks. */
    public int healTicks() {
        return healSeconds * ArenaMatch.TICKS_PER_SECOND;
    }

    /** A parse's result: the values, or why there are none. */
    public record Parsed(ArenaCreatures creatures, String problem) {
        public boolean ok() {
            return creatures != null;
        }
    }

    /** The values a rule's params describe (each key optional, the default's when missing), or the problem with them. */
    public static Parsed parse(Map<String, ?> params) {
        Map<String, ?> p = params == null ? Map.of() : params;
        for (String key : p.keySet()) {
            if (!key.startsWith("_") && !KEYS.contains(key)) {
                return new Parsed(null, "unknown param '" + key + "' (" + String.join(", ", KEYS) + ")");
            }
        }
        double[] v = DEFAULT.values();
        for (int i = 0; i < KEYS.size(); i++) {
            Object o = p.get(KEYS.get(i));
            if (o == null) {
                continue;
            }
            if (!(o instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                return new Parsed(null, KEYS.get(i) + " must be a number, not " + o);
            }
            if (WHOLE[i] && n.doubleValue() != Math.rint(n.doubleValue())) {
                return new Parsed(null, KEYS.get(i) + " must be a whole number, not " + o);
            }
            v[i] = n.doubleValue();
        }
        String why = problem(v[0], v[1], v[2], v[3], (int) v[4], v[5], v[6], (int) v[7]);
        if (why != null) {
            return new Parsed(null, why);
        }
        return new Parsed(new ArenaCreatures(v[0], v[1], v[2], v[3], (int) v[4], v[5], v[6], (int) v[7]), null);
    }

    private double[] values() {
        return new double[] {fighterAttack, tankHealth, tankWidth, healAmount, healSeconds, healRadius, tauntRadius,
                tankResistance};
    }

    /** As a rule's params: an ordinary (mutable) map, never {@code Map.of}, which the core cannot take. */
    public Map<String, Object> toParams() {
        Map<String, Object> out = new LinkedHashMap<>();
        double[] v = values();
        for (int i = 0; i < KEYS.size(); i++) {
            out.put(KEYS.get(i), WHOLE[i] ? (Object) (int) v[i] : (Object) v[i]);
        }
        return out;
    }

    private static String num(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) : String.valueOf(v);
    }
}
