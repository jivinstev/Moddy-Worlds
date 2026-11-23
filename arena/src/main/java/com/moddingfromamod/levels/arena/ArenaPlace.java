package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>The arena's place, as numbers</b>: one square ARENA (a fighting floor ringed by a wall) and one PREP room beside it
 * (a smaller walled, lit room where a player arrives), joined by one gate. Pure: no Minecraft here, so every size,
 * box and block of it is a unit test; {@link ArenaBuilder} only carries the list of {@link Fill}s out.
 *
 * <p>Layout, seen from above (north is up, -z), centred on x = 0:
 * <pre>
 *   +-----------------+   the arena: arenaSize x arenaSize inside, its wall wallHeight high,
 *   |                 |   with a rim on top that overhangs inward so nothing climbs out
 *   |      arena      |
 *   |                 |
 *   +------[g]--------+   g: the gate (a fence gate: players open it, mobs cannot); the shared wall
 *       |  prep  |        is glass there, so the arena is in view from the prep room
 *       +--------+        the prep room: prepSize x prepSize inside, glass walls, a lit floor and a roof
 * </pre>
 * The arena's centre is at (0, floorY, 0); the floor is at {@code floorY - 1}, people stand at {@code floorY}.
 *
 * @param arenaSize  the arena floor's inside width (odd, so it has a centre block)
 * @param prepSize   the prep room's inside width (odd)
 * @param wallHeight the arena wall's height above the floor
 * @param floorY     the y a player stands at
 */
public record ArenaPlace(int arenaSize, int prepSize, int wallHeight, int floorY) {

    public static final int MIN_ARENA = 15;
    public static final int MAX_ARENA = 63;
    public static final int MIN_PREP = 5;
    public static final int MAX_PREP = 15;
    public static final int MIN_WALL = 3;
    public static final int MAX_WALL = 8;
    /** The prep room's inside height (and its glass walls'). */
    public static final int PREP_HEIGHT = 4;
    /** How far above the floor a wish may still land: the arena's air, well over the wall. */
    public static final int REACH_UP = 32;

    /** The default place: an arena 31 wide, a prep room 9 wide, a wall 4 high, standing at y 64. */
    public static final ArenaPlace DEFAULT = new ArenaPlace(31, 9, 4, 64);

    public ArenaPlace {
        String why = problem(arenaSize, prepSize, wallHeight);
        if (why != null) {
            throw new IllegalArgumentException(why);
        }
    }

    /** What is wrong with these sizes, in words, or null when they make a place. Pure. */
    public static String problem(int arenaSize, int prepSize, int wallHeight) {
        if (arenaSize < MIN_ARENA || arenaSize > MAX_ARENA || arenaSize % 2 == 0) {
            return "arenaSize must be an odd number from " + MIN_ARENA + " to " + MAX_ARENA + ", not " + arenaSize;
        }
        if (prepSize < MIN_PREP || prepSize > MAX_PREP || prepSize % 2 == 0) {
            return "prepSize must be an odd number from " + MIN_PREP + " to " + MAX_PREP + ", not " + prepSize;
        }
        if (prepSize >= arenaSize) {
            return "prepSize (" + prepSize + ") must be smaller than arenaSize (" + arenaSize + ")";
        }
        if (wallHeight < MIN_WALL || wallHeight > MAX_WALL) {
            return "wallHeight must be from " + MIN_WALL + " to " + MAX_WALL + ", not " + wallHeight;
        }
        return null;
    }

    /**
     * The place a rule's params describe ({@code arenaSize}, {@code prepSize}, {@code wallHeight}; each optional, the
     * default's when missing), or the problem with them. {@code params} holds numbers as the JSON parser gives them.
     */
    public static Parsed parse(Map<String, ?> params) {
        Map<String, ?> p = params == null ? Map.of() : params;
        for (String key : p.keySet()) {
            if (!key.equals("arenaSize") && !key.equals("prepSize") && !key.equals("wallHeight")) {
                return new Parsed(null, "unknown param '" + key + "' (arenaSize, prepSize, wallHeight)");
            }
        }
        int[] v = new int[3];
        String[] keys = {"arenaSize", "prepSize", "wallHeight"};
        int[] defaults = {DEFAULT.arenaSize, DEFAULT.prepSize, DEFAULT.wallHeight};
        for (int i = 0; i < 3; i++) {
            Object o = p.get(keys[i]);
            if (o == null) {
                v[i] = defaults[i];
            } else if (o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    && Math.abs(n.doubleValue()) < 1_000_000) {
                v[i] = n.intValue();
            } else {
                return new Parsed(null, keys[i] + " must be a whole number, not " + o);
            }
        }
        String why = problem(v[0], v[1], v[2]);
        return why != null ? new Parsed(null, why) : new Parsed(new ArenaPlace(v[0], v[1], v[2], DEFAULT.floorY), null);
    }

    /** A parse's result: the place, or why there is none. */
    public record Parsed(ArenaPlace place, String problem) {
        public boolean ok() {
            return place != null;
        }
    }

    /** A box of blocks, both corners inclusive. */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        public Box {
            if (x0 > x1 || y0 > y1 || z0 > z1) {
                throw new IllegalArgumentException("an empty box");
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }

        public boolean containsColumn(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        public long volume() {
            return (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        }
    }

    /** What a box is filled with; {@link ArenaBuilder} picks the block. */
    public enum Kind {
        /** the arena's floor */
        ARENA_FLOOR,
        /** the arena's wall */
        ARENA_WALL,
        /** the arena wall's top, a ring that overhangs one block inward */
        ARENA_RIM,
        /** the prep room's floor */
        PREP_FLOOR,
        /** lights set in the prep room's floor, so nothing spawns there */
        PREP_LIGHT,
        /** the prep room's walls, and the shared wall's window */
        GLASS,
        /** the prep room's roof */
        PREP_ROOF,
        /** air: clears the gate's opening above the gate */
        AIR,
        /** the gate between the prep room and the arena (a fence gate, facing the arena) */
        GATE
    }

    /** One box to fill. Later fills overwrite earlier ones where they overlap. */
    public record Fill(Box box, Kind kind) {
    }

    private int ah() {
        return arenaSize / 2;
    }

    private int ph() {
        return prepSize / 2;
    }

    /** The arena's inside: where mobs and players fight (feet level up to the wall's top). */
    public Box arenaInside() {
        return new Box(-ah(), floorY, -ah(), ah(), floorY + wallHeight - 1, ah());
    }

    /** The z of the wall the arena and the prep room share (the arena's south wall). */
    public int sharedWallZ() {
        return ah() + 1;
    }

    /** The prep room's inside. */
    public Box prepInside() {
        int z0 = sharedWallZ() + 1;
        return new Box(-ph(), floorY, z0, ph(), floorY + PREP_HEIGHT - 1, z0 + prepSize - 1);
    }

    /** The gate's block: in the shared wall, at the middle, at feet height. */
    public int[] gate() {
        return new int[] {0, floorY, sharedWallZ()};
    }

    /** Where a player arrives: the middle of the prep room, facing the arena (north). Block coordinates. */
    public int[] arrival() {
        Box p = prepInside();
        return new int[] {0, floorY, (p.z0() + p.z1()) / 2};
    }

    /** The whole place's footprint, walls included. */
    public Box footprint() {
        Box p = prepInside();
        return new Box(-ah() - 1, floorY - 1, -ah() - 1, ah() + 1, floorY + wallHeight, p.z1() + 1);
    }

    /**
     * Where a wish may land: the arena's inside and the prep room's inside, from the floor up to {@link #REACH_UP}
     * blocks above it, walls excluded. Two boxes; a point is inside when either holds it.
     */
    public List<Box> reach() {
        Box a = arenaInside();
        Box p = prepInside();
        int top = floorY + REACH_UP;
        return List.of(new Box(a.x0(), floorY - 1, a.z0(), a.x1(), top, a.z1()),
                new Box(p.x0(), floorY - 1, p.z0(), p.x1(), p.y1(), p.z1()));
    }

    /** True when (x, y, z) is where a wish may land ({@link #reach}). */
    public boolean inReach(int x, int y, int z) {
        for (Box b : reach()) {
            if (b.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** The boxes to fill, in order, to build the place on nothing (a void world). */
    public List<Fill> fills() {
        List<Fill> out = new ArrayList<>();
        int a = ah();
        int fy = floorY - 1;
        int top = floorY + wallHeight - 1;
        // the arena: floor, then the wall ring (floor to top), then the rim one block wider inward on top
        out.add(new Fill(new Box(-a - 1, fy, -a - 1, a + 1, fy, a + 1), Kind.ARENA_FLOOR));
        out.add(new Fill(new Box(-a - 1, floorY, -a - 1, a + 1, top, -a - 1), Kind.ARENA_WALL));
        out.add(new Fill(new Box(-a - 1, floorY, a + 1, a + 1, top, a + 1), Kind.ARENA_WALL));
        out.add(new Fill(new Box(-a - 1, floorY, -a, -a - 1, top, a), Kind.ARENA_WALL));
        out.add(new Fill(new Box(a + 1, floorY, -a, a + 1, top, a), Kind.ARENA_WALL));
        int ry = top + 1;
        out.add(new Fill(new Box(-a - 1, ry, -a - 1, a + 1, ry, -a), Kind.ARENA_RIM));
        out.add(new Fill(new Box(-a - 1, ry, a, a + 1, ry, a + 1), Kind.ARENA_RIM));
        out.add(new Fill(new Box(-a - 1, ry, -a + 1, -a, ry, a - 1), Kind.ARENA_RIM));
        out.add(new Fill(new Box(a, ry, -a + 1, a + 1, ry, a - 1), Kind.ARENA_RIM));
        // the prep room: floor and walls (one block around its inside), a roof, lights
        Box p = prepInside();
        int pTop = p.y1();
        out.add(new Fill(new Box(p.x0() - 1, fy, p.z0() - 1, p.x1() + 1, fy, p.z1() + 1), Kind.PREP_FLOOR));
        out.add(new Fill(new Box(p.x0() - 1, floorY, p.z1() + 1, p.x1() + 1, pTop, p.z1() + 1), Kind.GLASS));
        out.add(new Fill(new Box(p.x0() - 1, floorY, p.z0(), p.x0() - 1, pTop, p.z1()), Kind.GLASS));
        out.add(new Fill(new Box(p.x1() + 1, floorY, p.z0(), p.x1() + 1, pTop, p.z1()), Kind.GLASS));
        out.add(new Fill(new Box(p.x0() - 1, pTop + 1, p.z0() - 1, p.x1() + 1, pTop + 1, p.z1() + 1), Kind.PREP_ROOF));
        for (int[] l : lights()) {
            out.add(new Fill(new Box(l[0], fy, l[1], l[0], fy, l[1]), Kind.PREP_LIGHT));
        }
        // the shared wall: a window the prep room's width, from one above the floor to the wall's top; then the gate
        int wz = sharedWallZ();
        int winTop = Math.min(top, floorY + PREP_HEIGHT - 1);
        out.add(new Fill(new Box(p.x0(), floorY, wz, p.x1(), winTop, wz), Kind.GLASS));
        out.add(new Fill(new Box(0, floorY + 1, wz, 0, floorY + 1, wz), Kind.AIR));
        out.add(new Fill(new Box(0, floorY, wz, 0, floorY, wz), Kind.GATE));
        return out;
    }

    /** The prep room's floor lights, as (x, z): its four corners' diagonal neighbours and its centre's ring. */
    List<int[]> lights() {
        Box p = prepInside();
        int cz = (p.z0() + p.z1()) / 2;
        int d = Math.max(1, ph() - 1);
        return List.of(new int[] {-d, cz - d}, new int[] {d, cz - d}, new int[] {-d, cz + d}, new int[] {d, cz + d});
    }
}
