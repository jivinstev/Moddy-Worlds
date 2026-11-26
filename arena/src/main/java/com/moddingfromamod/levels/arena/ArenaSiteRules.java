package com.moddingfromamod.levels.arena;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <b>Where an arena may be built, and what it may be called</b> (Moddy-Worlds#58). Pure. A site is a whole place (its
 * prep room, its arena and their walls) standing at an origin in a dimension. Two sites may not overlap, nor come
 * within {@link #GAP} blocks of each other, so one's battle never spills into the other's floor.
 */
public final class ArenaSiteRules {

    /** The site the arena world type builds on its first start, at x 0, z 0. */
    public static final String MAIN = "main";
    /** Blocks kept clear between two sites' footprints. */
    public static final int GAP = 8;
    /** The most arenas one world may hold. */
    public static final int MAX_SITES = 16;

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,23}");

    private ArenaSiteRules() {
    }

    /** One site as the rules see it: its name, dimension and footprint (x0..x1, z0..z1, inclusive). */
    public record Site(String name, String dimension, int x0, int z0, int x1, int z1) {
        boolean near(Site o, int gap) {
            return dimension.equals(o.dimension) && x0 - gap <= o.x1 && o.x0 <= x1 + gap && z0 - gap <= o.z1
                    && o.z0 <= z1 + gap;
        }
    }

    /** {@code raw} as a site name (lower case), or null when it cannot be one. */
    public static String name(String raw) {
        String n = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return NAME.matcher(n).matches() && !n.equals(ArenaConfig.GLOBAL) ? n : null;
    }

    /** Why {@code wanted} may not be built among {@code existing}, or null when it may. */
    public static String problem(Site wanted, List<Site> existing) {
        if (wanted.name() == null) {
            return "An arena's name is 1 to 24 letters, digits, - or _, starting with a letter (and not 'global').";
        }
        if (existing.size() >= MAX_SITES) {
            return "This world already has " + MAX_SITES + " arenas, the most it may hold.";
        }
        for (Site s : existing) {
            if (s.name().equals(wanted.name())) {
                return "There is already an arena called " + wanted.name() + ".";
            }
            if (s.near(wanted, GAP)) {
                return "That is too close to the arena " + s.name() + ": arenas need " + GAP
                        + " blocks between them. Move away and try again.";
            }
        }
        return null;
    }

    /** The site whose footprint holds (x, z) in {@code dimension}, or null. */
    public static Site at(List<Site> sites, String dimension, double x, double z) {
        for (Site s : sites) {
            if (s.dimension().equals(dimension) && x >= s.x0() && x <= s.x1() + 1 && z >= s.z0() && z <= s.z1() + 1) {
                return s;
            }
        }
        return null;
    }

    /**
     * The ground a place at {@code place} takes: in x and z its base ({@link ArenaPlace#baseBox}, the footprint, prep room
     * included, and the base's margin round it), the widest thing it builds; in y from its base's bedrock up to the
     * world's top, because a battle's end clears the arena to the sky.
     */
    public static Site claim(String name, ArenaPlace place) {
        ArenaPlace.Box b = place.baseBox();
        return new Site(name, "minecraft:overworld", b.x0(), b.z0(), b.x1(), b.z1());
    }

    /**
     * Why a place whose base reaches down to {@code bottom} and whose walls up to {@code top} cannot stand in a world
     * built from {@code minY} to {@code maxY} (inclusive), or null.
     */
    public static String heightProblem(int bottom, int top, int minY, int maxY) {
        if (bottom < minY) {
            return "That is too low: the arena's base would go under the bottom of the world (y " + minY
                    + "). Stand higher and try again.";
        }
        if (top > maxY) {
            return "That is too high: the arena's walls would go over the top of the world (y " + maxY
                    + "). Stand lower and try again.";
        }
        return null;
    }

    /** The words for something that stands where an arena would be built: what it is and where. */
    public static String inTheWay(String what, int x, int y, int z) {
        return "That would build over " + what + " at " + x + " " + y + " " + z
                + ". Nothing is replaced: build the arena somewhere clear.";
    }
}
