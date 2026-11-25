package com.moddingfromamod.levels.arena;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * <b>Every wished creature helps in a battle</b>: during a wave each creature a player wished into the arena takes one
 * battle role, on top of whatever it is, so no wish for a creature ends with it standing about while the monsters come.
 * Pure decisions, no Minecraft: {@link ArenaRoleKeeper} reads a few facts off each creature, asks these, and carries out
 * the answer. A role changes nothing about the creature itself: outside a wave, and outside the arena, it is what it was
 * wished as.
 *
 * <ul>
 *   <li><b>Fighter</b>: a creature that can fight (its own attack is at least {@link ArenaCreatures#fighterAttack}, or
 *       it is a monster) goes for the wave's monsters ({@link ArenaAllies});</li>
 *   <li><b>Tank</b>: a big or tough creature that does not fight (most health at least
 *       {@link ArenaCreatures#tankHealth}, or at least {@link ArenaCreatures#tankWidth} wide) draws the monsters near it
 *       and stands up to them;</li>
 *   <li><b>Healer</b>: any other creature that can walk stays by its player and heals him;</li>
 *   <li>and any other creature that cannot walk is a Tank too: it cannot follow anyone to heal him, so it draws the
 *       monsters near it, where it stands.</li>
 * </ul>
 *
 * <p>Chosen only from what the game itself says about the creature (its attack and health, its size, whether it is a
 * monster, whether it can walk), never from its name, its kind or what it was made from, so a creature nobody has
 * wished before gets a role by the same rule. There is always one: {@link #choose} never answers null.
 */
final class ArenaRoles {

    private ArenaRoles() {
    }

    /** A wished creature's battle role. */
    enum Role {
        FIGHTER("Fighter", "red"),
        TANK("Tank", "aqua"),
        HEALER("Healer", "green");

        private final String word;
        private final String colour;

        Role(String word, String colour) {
            this.word = word;
            this.colour = colour;
        }

        /** The role's name as a player reads it ("Healer"). */
        String word() {
            return word;
        }

        /** The colour its sign above the creature is written in. */
        String colour() {
            return colour;
        }

        /** True for the role that draws the monsters to itself. */
        boolean draws() {
            return this == TANK;
        }
    }

    /**
     * What the game says about one creature, all a role is chosen from.
     *
     * @param attack    its own attack damage, in health points (0 when it has none)
     * @param maxHealth its most health
     * @param width     how wide it is, in blocks
     * @param monster   whether the game counts it a monster (a zombie wished up fights)
     * @param canWalk   whether it moves of its own accord (false: it is held still, or cannot move at all)
     */
    record Facts(double attack, double maxHealth, double width, boolean monster, boolean canWalk) {
    }

    /**
     * The role a creature with these facts takes. Never null: a creature nothing else fits heals, or, when it cannot
     * walk to its player, is a Tank where it stands.
     */
    static Role choose(Facts f, ArenaCreatures n) {
        if (f.monster() || f.attack() >= n.fighterAttack()) {
            return Role.FIGHTER;
        }
        if (f.maxHealth() >= n.tankHealth() || f.width() >= n.tankWidth()) {
            return Role.TANK;
        }
        return f.canWalk() ? Role.HEALER : Role.TANK;
    }

    // ── which creatures ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The tag the Moddy core puts on every creature a wish makes (a Builder or Apprentice make, a Magician's sculpt, a
     * spawn egg), on the body that stands in the world: its public handle for "the things I made", which the core's own
     * docs tell a datapack to select creatures by ({@code @e[tag=wishmob]}).
     */
    static final String WISHED_CREATURE = "wishmob";

    /**
     * The tag the core puts on a plain game mob a wish summoned (a Magician's {@code summon}, an event, a datapack the
     * wish made): the creatures its wish-made-mob limit counts. Not one of the core's documented handles (only its
     * source names it), so this is the best approximation the arena has for "a creature the player's wish summoned";
     * see the arena's README.
     */
    static final String WISH_SUMMONED = "moddy_wished";

    /**
     * True for a creature a player wished for, by its tags: the core's {@link #WISHED_CREATURE} or
     * {@link #WISH_SUMMONED}, and none of the arena's own (a wave's monster, the control panel's or the Champions
     * board's words, a role's sign). A mob that wandered in, or was put there by a command, has neither and is left
     * alone.
     */
    static boolean wished(Collection<String> tags, Collection<String> arenasOwn) {
        if (tags == null || !(tags.contains(WISHED_CREATURE) || tags.contains(WISH_SUMMONED))) {
            return false;
        }
        for (String t : arenasOwn) {
            if (tags.contains(t)) {
                return false;
            }
        }
        return true;
    }

    // ── whose it is ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** A fighter in the battle, where he is. */
    record Spot(UUID id, double x, double y, double z) {
    }

    /**
     * The fighter a creature at (x, y, z) helps: the one it already helps while he is still in, else the player who
     * owns it when the game says so (a tamed pet) and he is a fighter, else the nearest fighter, the lower id between
     * two as near. Null when nobody is in.
     */
    static UUID owner(double x, double y, double z, UUID current, UUID tamedBy, List<Spot> fighters) {
        UUID[] keep = {current, tamedBy};
        for (UUID want : keep) {
            if (want == null) {
                continue;
            }
            for (Spot s : fighters) {
                if (s.id().equals(want)) {
                    return want;
                }
            }
        }
        Spot best = null;
        double bestD = Double.MAX_VALUE;
        for (Spot s : fighters) {
            double d = sq(s.x() - x) + sq(s.y() - y) + sq(s.z() - z);
            if (d < bestD || (d == bestD && best != null && s.id().compareTo(best.id()) < 0)) {
                best = s;
                bestD = d;
            }
        }
        return best == null ? null : best.id();
    }

    // ── what each role does ─────────────────────────────────────────────────────────────────────────────────────────

    /** A healer walks back to its player when it is farther than this from him, in blocks. */
    static final double FOLLOW = 3.0;
    /** How often a healer's walk to its player is set again, in ticks. */
    static final int REPATH_TICKS = 10;
    /** A monster a tank holds lets go of it when it is farther from it than this many times the taunt's reach. */
    static final double LET_GO = 1.5;
    /** How long a tank's Resistance lasts each time it is given, in ticks: it runs out by itself soon after the taunt. */
    static final int RESISTANCE_TICKS = 40;
    /** How often the tank's taunt looks for monsters, in ticks (once a second); a held one is held every tick. */
    static final int TAUNT_TICKS = 20;

    /** True when a healer this far from its player ({@code distSq}) should walk back to him. */
    static boolean walkBack(double distSq) {
        return distSq > FOLLOW * FOLLOW;
    }

    /**
     * How much a heal gives a fighter: {@code amount}, no more than he is missing; 0 when his health is full (no hearts
     * are shown for a heal that gives nothing).
     */
    static float heal(float health, float maxHealth, double amount) {
        if (!(health > 0) || health >= maxHealth) {
            return 0f;
        }
        return (float) Math.min(amount, maxHealth - health);
    }

    /**
     * True when a fighter last healed at tick {@code last} (or never, -1) may be healed again at {@code now}: one heal
     * every {@code everyTicks} whatever the number of his healers, so two healers do not heal twice as fast.
     */
    static boolean mayHeal(long last, long now, int everyTicks) {
        return last < 0 || now - last >= everyTicks;
    }

    /** True when a fighter {@code distSq} from a healer is near enough to be healed by it. */
    static boolean inHealReach(double distSq, ArenaCreatures n) {
        return distSq <= n.healRadius() * n.healRadius();
    }

    /** True when a monster {@code distSq} from a tank is near enough to turn to it. */
    static boolean taunts(double distSq, ArenaCreatures n) {
        return distSq <= n.tauntRadius() * n.tauntRadius();
    }

    /** True when a monster a tank holds, now {@code distSq} from it, has got far enough away to let go of it. */
    static boolean letsGo(double distSq, ArenaCreatures n) {
        double r = n.tauntRadius() * LET_GO;
        return distSq > r * r;
    }

    /** The effect amplifier of a Resistance level (level 1 is amplifier 0); -1 for none. */
    static int resistanceAmplifier(int level) {
        return level - 1;
    }

    // ── what the player reads ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * A creature's name as a chat line can carry it: its colour codes and anything that is not a plain character taken
     * out, spaces kept single, at most 32 characters; "creature" when nothing is left.
     */
    static String plainName(String raw) {
        if (raw == null) {
            return "creature";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++;   // a colour code is the sign and the character after it
            } else if (c >= 32 && c < 127) {
                sb.append(c);
            }
        }
        String s = sb.toString().trim().replaceAll(" {2,}", " ");
        if (s.length() > 32) {
            s = s.substring(0, 32).trim();
        }
        return s.isEmpty() ? "creature" : s;
    }

    /** The tag a creature wears while it has a role (so a playtest or an operator can see it); taken off with it. */
    static String roleTag(Role r) {
        return ArenaMod.MODID + "_" + r.name().toLowerCase(Locale.ROOT);
    }

    private static double sq(double v) {
        return v * v;
    }
}
