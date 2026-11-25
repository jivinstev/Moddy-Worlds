package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   +------[g]--------+   g: the gate (a fence gate; iron bars while a wave is on, ArenaGate); the shared wall
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
            if (key.startsWith("_")) {
                continue;                       // a note, as everywhere in a rules file
            }
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
        /** air: the insides, and the gate's opening above the gate */
        AIR,
        /** the gate between the prep room and the arena (a fence gate, facing the arena) */
        GATE,
        /** the gateway while a wave is on: iron bars in the gate's block and the opening above it ({@link ArenaGate}) */
        GATE_BARS,
        /** the control panel's board: the shared wall's window, on the prep room's side ({@link ArenaPanel}) */
        PANEL_BOARD,
        /** a block of the board that holds a selector button (wave size or difficulty, down or up) */
        PANEL_SELECT,
        /** the blocks of the board around the start button, a colour of their own */
        PANEL_START,
        /** a selector button, on the prep room's side of the board */
        SELECT_BUTTON,
        /** the start button, on the prep room's side of the board */
        START_BUTTON,
        /** the Champions board: the inside of the prep room's back wall ({@link ArenaChampionsLayout}) */
        CHAMPIONS_BOARD,
        /** the ground round the place, level with its floors, on the base ({@link #baseFills()}) */
        GROUND,
        /** the layer of the base under the floors and the ground: a block a blast can break */
        BASE_FILL,
        /** the bottom of the base: bedrock, which nothing breaks, so nobody falls further */
        BASE_BEDROCK,
        /** the wish ladder board's blocks, beside the control panel on the prep room's west wall */
        LADDER_BOARD,
        /** the hand-over spot: one block of the prep room's floor by its east wall ({@link #handOverSpot}) */
        HAND_OVER_SPOT
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

    /**
     * True for a block of the gateway between the prep room and the arena: the gate's block and the opening above it.
     * Looking through it from the prep room, a player looks at the arena ({@link ArenaHandOver}).
     */
    public boolean inGateway(int x, int y, int z) {
        int[] g = gate();
        return x == g[0] && z == g[2] && (y == g[1] || y == g[1] + 1);
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

    /** How far the base reaches past the place's walls, each way, in blocks. */
    public static final int BASE_MARGIN = 2;
    /** How many layers the base has under the floors: a fill layer, then bedrock. */
    public static final int BASE_DEPTH = 2;

    /**
     * The base the place stands on: under the whole footprint and {@value #BASE_MARGIN} blocks round it, from the bedrock
     * layer up to the floors' level. Its top layer (the floors' level) is the place's own floors, and ground round them;
     * under that one layer a blast can break; under that bedrock. So a blast that breaks a floor drops a player
     * {@value #BASE_DEPTH} blocks at most, onto bedrock, and never out of the world.
     */
    public Box baseBox() {
        Box f = footprint();
        return new Box(f.x0() - BASE_MARGIN, bedrockY(), f.z0() - BASE_MARGIN, f.x1() + BASE_MARGIN, floorY - 1,
                f.z1() + BASE_MARGIN);
    }

    /** The y of the base's bedrock layer. */
    public int bedrockY() {
        return floorY - 1 - BASE_DEPTH;
    }

    /**
     * The base's blocks ({@link #baseBox}): bedrock at the bottom, the fill layer over it, and ground at the floors'
     * level, which the place's own floors then cover. Part of {@link #fills()}, first; a world built before the base
     * existed gains exactly these ({@link #UPGRADE_BASE}), the ground only where nothing stands.
     */
    public List<Fill> baseFills() {
        Box b = baseBox();
        return List.of(
                new Fill(new Box(b.x0(), bedrockY(), b.z0(), b.x1(), bedrockY(), b.z1()), Kind.BASE_BEDROCK),
                new Fill(new Box(b.x0(), bedrockY() + 1, b.z0(), b.x1(), floorY - 2, b.z1()), Kind.BASE_FILL),
                new Fill(new Box(b.x0(), floorY - 1, b.z0(), b.x1(), floorY - 1, b.z1()), Kind.GROUND));
    }

    /**
     * True when a body at (x, y, z) has fallen out of the place: over the base's columns (or near them) and more than a
     * few blocks under the floors, where the base would have caught anyone who did not fall past it. Such a player is
     * put back in the prep room.
     */
    public boolean fellOut(double x, double y, double z) {
        Box b = baseBox();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int near = 16;
        return bx >= b.x0() - near && bx <= b.x1() + near && bz >= b.z0() - near && bz <= b.z1() + near
                && y < bedrockY() - 2;
    }

    /** The block under the arrival spot (the world spawn): the prep room's floor there, kept whole. */
    public int[] spawnFloor() {
        int[] a = arrival();
        return new int[] {a[0], floorY - 1, a[2]};
    }

    /** The prep room's floor, inside its walls (what a mend puts back where it is missing). */
    public Box prepFloor() {
        Box p = prepInside();
        return new Box(p.x0(), floorY - 1, p.z0(), p.x1(), floorY - 1, p.z1());
    }

    /** True when the prep room's floor at column (x, z) is one of its lights. */
    public boolean isPrepLight(int x, int z) {
        for (int[] l : lights()) {
            if (l[0] == x && l[1] == z) {
                return true;
            }
        }
        return false;
    }

    /** A column-aligned rectangle (every y), both corners inclusive: the shape the core's bases speak. */
    public record Columns(int minX, int maxX, int minZ, int maxZ) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /**
     * Where a wish may be AIMED, for a player standing at (x, z). The core's base has one reach rectangle, so it follows
     * the room he stands in:
     * <ul>
     *   <li>in the prep room (its columns, walls included): the prep room's inside AND the arena's floor straight ahead
     *       of it, through the gate, wall to wall ({@link #prepReach}): a wish aimed at the prep room's floor lands
     *       there, and a make put in front of him (the core's Builder puts one 12 blocks ahead of a player who aims at
     *       nothing) lands in the arena;</li>
     *   <li>anywhere else: the arena's inside, wall to wall ({@link #arenaReach}).</li>
     * </ul>
     * Every height, as the core's reach rule reads a base. Never one rectangle round both rooms: that would also take in
     * the open sky beside the prep room, where a wish would float outside the walls.
     */
    public Columns reachFor(double x, double z) {
        return inPrepRoom(x, z) ? prepReach() : arenaReach();
    }

    /** The arena's inside, as columns: where a wish lands for a player in the arena (or anywhere but the prep room). */
    public Columns arenaReach() {
        Box a = arenaInside();
        return new Columns(a.x0(), a.x1(), a.z0(), a.z1());
    }

    /**
     * Where a wish lands for a player in the prep room: the prep room's inside, and the arena's floor in the prep room's
     * width from the gate to the arena's far wall (the shared wall between them, glass and the gate, included).
     */
    public Columns prepReach() {
        Box p = prepInside();
        Box a = arenaInside();
        return new Columns(p.x0(), p.x1(), a.z0(), p.z1());
    }

    /** True when (x, z) is in the prep room's columns, its walls included (not the shared wall, which is the arena's). */
    public boolean inPrepRoom(double x, double z) {
        Box p = prepInside();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return bx >= p.x0() - 1 && bx <= p.x1() + 1 && bz >= p.z0() && bz <= p.z1() + 1;
    }

    /** True when a wish aimed at the column (tx, tz) by a player standing at (x, z) lands ({@link #reachFor}). */
    public boolean inReach(double x, double z, int tx, int tz) {
        return reachFor(x, z).contains(tx, tz);
    }

    /**
     * What nothing a wish does may reach past: the whole place, walls and prep room included (the rectangle round
     * both; where a wish runs from, he stands in the prep room).
     */
    public Columns area() {
        Box f = footprint();
        return new Columns(f.x0(), f.x1(), f.z0(), f.z1());
    }

    /** The way a player faces on arrival: north, through the glass at the arena (Minecraft's yaw 180). */
    public static final float ARRIVAL_YAW = 180f;

    /**
     * What a world's start does, given the place it already built (null: none yet) and the place its rules ask for:
     * build the wanted one, or keep what stands. Sizes that change after the place is built change nothing that
     * stands: the built place is kept (and its reach), and {@code note} says so for the log.
     */
    public record Start(ArenaPlace place, boolean build, String note) {
    }

    /** {@link Start}, decided. Pure. */
    public static Start onStart(ArenaPlace built, ArenaPlace wanted) {
        ArenaPlace want = wanted == null ? DEFAULT : wanted;
        if (built == null) {
            return new Start(want, true, "");
        }
        if (built.equals(want)) {
            return new Start(built, false, "");
        }
        return new Start(built, false, "the rules now ask for " + want.describe() + ", but this world's place was built as "
                + built.describe() + " and is kept as it is (a new world gets the new sizes)");
    }

    /** "arena 31, prep room 9, wall 4" */
    public String describe() {
        return "arena " + arenaSize + ", prep room " + prepSize + ", wall " + wallHeight;
    }

    /** The boxes to fill, in order, to build the place on nothing (a void world). */
    public List<Fill> fills() {
        List<Fill> out = new ArrayList<>();
        int a = ah();
        int fy = floorY - 1;
        int top = floorY + wallHeight - 1;
        // the base it stands on: bedrock, a fill layer, ground level with the floors (which cover it where they are)
        out.addAll(baseFills());
        // the insides cleared first (nothing to clear in a void world; in any other, the room is a room)
        out.add(new Fill(arenaInside(), Kind.AIR));
        out.add(new Fill(prepInside(), Kind.AIR));
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
        out.addAll(controlFills());
        out.addAll(championsFills());
        out.addAll(ladderFills());
        out.addAll(handOverFills());
        return out;
    }

    /**
     * The hand-over spot: the block of the prep room's floor in the middle of its east side (the right of a player who
     * arrives), where a make finished in the prep room shows for a moment before it goes to him ({@link ArenaHandOver}).
     * Never one of the floor's lights. Block coordinates of the floor block; the make shows just above it.
     */
    public int[] handOverSpot() {
        Box p = prepInside();
        return new int[] {p.x1(), floorY - 1, (p.z0() + p.z1()) / 2};
    }

    /**
     * The hand-over spot's block. Part of {@link #fills()}; a world built before the spot existed gains exactly this
     * ({@link #UPGRADE_HAND_OVER_SPOT}), only where the floor is still as built.
     */
    public List<Fill> handOverFills() {
        int[] h = handOverSpot();
        return List.of(new Fill(new Box(h[0], h[1], h[2], h[0], h[1], h[2]), Kind.HAND_OVER_SPOT));
    }

    /**
     * The wish ladder board's blocks: over the prep room's west wall's glass, beside the control panel ({@link
     * ArenaPanel#ladderBoard}). Part of {@link #fills()}; a world built before the board existed gains exactly these
     * ({@link #UPGRADE_LADDER_BOARD}).
     */
    public List<Fill> ladderFills() {
        return List.of(new Fill(panel().ladderBoard(), Kind.LADDER_BOARD));
    }

    /** The Champions board on the prep room's back wall ({@link ArenaChampionsLayout}). */
    public ArenaChampionsLayout champions() {
        return new ArenaChampionsLayout(this);
    }

    /**
     * The Champions board's blocks: over the inside of the prep room's back wall's glass. Part of {@link #fills()}; a
     * world built before the board existed gains exactly these ({@link #UPGRADE_CHAMPIONS}).
     */
    public List<Fill> championsFills() {
        return champions().fills();
    }

    /** The y of the shared wall's window's top row (the control panel's top row). */
    public int windowTop() {
        return Math.min(floorY + wallHeight - 1, floorY + PREP_HEIGHT - 1);
    }

    /** The battle arena control panel on the shared wall ({@link ArenaPanel}): where its blocks, buttons and words go. */
    public ArenaPanel panel() {
        return new ArenaPanel(this);
    }

    /**
     * The control panel's blocks: its board over the shared wall's window (beside and above the gate), and its buttons
     * on the prep room's side. Part of {@link #fills()}; a world built before the panel existed gains exactly these
     * ({@link #UPGRADE_CONTROL_PANEL}).
     */
    public List<Fill> controlFills() {
        return panel().fills();
    }

    /** The start button's block: on the panel, in the prep room against the shared wall ({@link ArenaPanel}). */
    public int[] startButton() {
        return panel().button(ArenaPanel.Control.START).pos();
    }

    /**
     * Where the first start control's button and sign stood (before the control panel): in the prep room against the
     * shared wall, two east of the gate, a block and two blocks up. A place that had it loses them to the panel.
     */
    public List<int[]> firstStartControl() {
        return List.of(new int[] {2, floorY + 1, sharedWallZ() + 1}, new int[] {2, floorY + 2, sharedWallZ() + 1});
    }

    /** The upgrade that gave a place built without one its first start control (a button and a sign). */
    public static final String UPGRADE_START_CONTROL = "start-control";
    /** The upgrade that switches natural mob spawning off in an arena world, once. */
    public static final String UPGRADE_NO_NATURAL_SPAWNS = "no-natural-spawns";
    /**
     * The upgrade that gives a place its battle arena control panel ({@link #controlFills()}), in place of the first
     * start control (or of the bare window, in a place that never had one).
     */
    public static final String UPGRADE_CONTROL_PANEL = "control-panel";
    /** The upgrade that gives a place its Champions board ({@link #championsFills()}), over the back wall's glass. */
    public static final String UPGRADE_CHAMPIONS = "champions-board";
    /** The upgrade that puts the base under a place built in the sky ({@link #baseFills()}). */
    public static final String UPGRADE_BASE = "ground-base";
    /** The upgrade that gives a place its wish ladder board ({@link #ladderFills()}), over the west wall's glass. */
    public static final String UPGRADE_LADDER_BOARD = "ladder-board";
    /** Every upgrade, in the order a world gets them. A new world gets them all when it is built. */
    /** The hand-over spot in the prep room's floor ({@link #handOverFills}). */
    public static final String UPGRADE_HAND_OVER_SPOT = "hand-over-spot";
    public static final List<String> UPGRADES = List.of(UPGRADE_START_CONTROL, UPGRADE_NO_NATURAL_SPAWNS,
            UPGRADE_CONTROL_PANEL, UPGRADE_CHAMPIONS, UPGRADE_BASE, UPGRADE_LADDER_BOARD, UPGRADE_HAND_OVER_SPOT);

    /** The upgrades a world that has had {@code done} still needs, in order. Pure. */
    public static List<String> pendingUpgrades(Set<String> done) {
        return UPGRADES.stream().filter(u -> done == null || !done.contains(u)).toList();
    }

    /**
     * True when a body at (x, y, z) is still in the place: inside its walls' footprint (the prep room counts) and not
     * fallen below the base's top (standing in a hole a blast made, on the bedrock, is still in it).
     */
    public boolean keeps(double x, double y, double z) {
        Box f = footprint();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return bx >= f.x0() && bx <= f.x1() && bz >= f.z0() && bz <= f.z1() && y >= bedrockY();
    }

    /**
     * The block box an arena's reset looks through: the arena's footprint (shared wall included), from the base's fill
     * layer (so a blast's hole is filled back to the floor) to {@code maxY}.
     */
    public Box resetBox(int maxY) {
        int a = ah();
        return new Box(-a - 1, bedrockY(), -a - 1, a + 1, Math.max(maxY, floorY + wallHeight + 1), sharedWallZ());
    }

    /**
     * <b>What a reset puts back</b>: the base under the arena (its bedrock and fill layer), the arena's inside, floor,
     * walls, rim, the shared wall's gate and the control panel's board, as {@link #fills()} built them, and air
     * everywhere above the arena's floor and walls. Never the
     * prep room: its floor, roof and lights, where they meet the shared wall, are left as they stand (and the panel's
     * buttons, which are in the prep room, are mended by {@link ArenaBuilder#addControl}).
     */
    public Reset reset() {
        Map<Long, Kind> m = new HashMap<>();
        int wz = sharedWallZ();
        for (Fill f : fills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        if (z <= wz) {
                            m.put(key(x, y, z), f.kind());
                        }
                    }
                }
            }
        }
        m.values().removeIf(k -> k == Kind.PREP_FLOOR || k == Kind.PREP_ROOF || k == Kind.PREP_LIGHT
                || k == Kind.GROUND || k == Kind.HAND_OVER_SPOT);
        // the prep room's own blocks over the shared wall are not the arena's: mark them so nothing above clears them
        Map<Long, Kind> prep = new HashMap<>();
        for (Fill f : fills()) {
            if (f.kind() == Kind.PREP_FLOOR || f.kind() == Kind.PREP_ROOF || f.kind() == Kind.PREP_LIGHT) {
                Box b = f.box();
                for (int x = b.x0(); x <= b.x1(); x++) {
                    for (int y = b.y0(); y <= b.y1(); y++) {
                        for (int z = b.z0(); z <= Math.min(b.z1(), wz); z++) {
                            prep.put(key(x, y, z), f.kind());
                        }
                    }
                }
            }
        }
        // a later prep fill over an arena block keeps the prep block
        for (Long k : prep.keySet()) {
            m.remove(k);
        }
        return new Reset(this, m, prep.keySet());
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** {@link #reset()}'s answer: the kind each block of {@link #resetBox} is put back as, or null for "leave it". */
    public static final class Reset {
        private final ArenaPlace place;
        private final Map<Long, Kind> blocks;
        private final Set<Long> prep;

        private Reset(ArenaPlace place, Map<Long, Kind> blocks, Set<Long> prep) {
            this.place = place;
            this.blocks = blocks;
            this.prep = Set.copyOf(prep);
        }

        public Kind kindAt(int x, int y, int z) {
            long k = key(x, y, z);
            Kind built = blocks.get(k);
            if (built != null) {
                return built;
            }
            if (prep.contains(k) || y < place.floorY()) {
                return null;
            }
            int a = place.ah();
            boolean inArenaColumns = x >= -a - 1 && x <= a + 1 && z >= -a - 1 && z <= place.sharedWallZ();
            return inArenaColumns ? Kind.AIR : null;
        }
    }

    /** The prep room's floor lights, as (x, z): its four corners' diagonal neighbours and its centre's ring. */
    List<int[]> lights() {
        Box p = prepInside();
        int cz = (p.z0() + p.z1()) / 2;
        int d = Math.max(1, ph() - 1);
        return List.of(new int[] {-d, cz - d}, new int[] {d, cz - d}, new int[] {-d, cz + d}, new int[] {d, cz + d});
    }
}
