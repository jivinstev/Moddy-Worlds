package com.moddingfromamod.isle;

import com.moddingfromamod.world.Base;
import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Where the islands of an islands world go</b> — Minecraft-free, so every rule here is
 * a Gate A test.
 *
 * <p>The world is a square grid of cells {@link #spacing()} blocks apart, centred on the world
 * spawn. Cell {@code (0,0)} is the SPAWN: it holds the SkyBlock pack's own starter island, which is
 * the arrival platform every new child lands on before being moved to their own, and it is never
 * anyone's island — so the pack's island cannot become a shared island by accident, and a wish
 * aimed at it belongs to nobody's region and is refused. Children get the other cells in a fixed
 * spiral order ({@link #cellAt(int)}), nearest ring first, so the grid is the same whoever joins
 * first.
 *
 * <p>An island's <b>origin</b> is the block the pack's template marks with its jigsaw (bedrock in
 * the placed island), at {@link #y()} — 63, where Standard SkyBlock puts its own. Two regions
 * belong to an island:
 * <ul>
 *   <li>the <b>cell</b>, the whole square {@code spacing} wide around the origin: nothing a wish
 *       does may reach outside it;</li>
 *   <li>the <b>reach</b>, the cell less {@link #margin()} on every side: where a wish's ANCHOR may
 *       be. The margin is {@code BuildShape.MAX_DIM} (96), the widest thing a build tool will make,
 *       so a building anchored anywhere in the reach cannot overhang into a neighbour's cell.</li>
 * </ul>
 *
 * <p>The spacing is a knob (the world manifest's {@code islands.spacing}), but it is fixed when the
 * first island is made: the store records it, and a later change to the manifest does not move the
 * grid under islands that already exist ({@link IslandStore}).
 */
public record IslandGrid(int spacing, int margin, int baseX, int y, int baseZ) {

    /** The shipped spacing: 64 chunks, eight times what a view distance of 8 needs. */
    public static final int DEFAULT_SPACING = 1024;
    /** {@code BuildShape.MAX_DIM}: the widest build a tool makes, so a build fits inside its cell. */
    public static final int MARGIN = 96;
    /** Standard SkyBlock places its starter island's bedrock at y=63. */
    public static final int ISLAND_Y = 63;

    public IslandGrid {
        if (margin < 0) {
            throw new IllegalArgumentException("margin must be >= 0: " + margin);
        }
        if (spacing < 2 * margin + 16) {
            throw new IllegalArgumentException("spacing " + spacing + " leaves no room for an island "
                    + "inside a margin of " + margin + " on each side (needs >= " + (2 * margin + 16) + ")");
        }
    }

    /** The production grid: centred on the world spawn's column, at the pack's island height. */
    public static IslandGrid of(int spacing) {
        return new IslandGrid(spacing, MARGIN, 0, ISLAND_Y, 0);
    }

    /**
     * The smallest spacing at which a child standing anywhere on their island never has a
     * neighbour's island inside the server's view distance: a client is sent the chunks within
     * {@code viewDistance} of its own chunk, so two islands must be more than twice that apart,
     * plus one chunk each for the chunk the island sits in.
     */
    public static int minSpacing(int viewDistanceChunks) {
        return 32 * (viewDistanceChunks + 1);
    }

    /**
     * The {@code index}-th cell of the spiral: 0 is the spawn {@code (0,0)}; 1..8 the ring around
     * it; 9..24 the next ring; and so on. Deterministic, so a world's grid never depends on who
     * joined first — only on how many had.
     */
    public static int[] cellAt(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index " + index);
        }
        if (index == 0) {
            return new int[]{0, 0};
        }
        int ring = 1;
        int before = 1;                               // cells in rings < ring
        while (before + 8 * ring <= index) {
            before += 8 * ring;
            ring++;
        }
        List<int[]> cells = ring(ring);
        return cells.get(index - before);
    }

    /** The 8r cells of ring r, in a fixed order: top edge, right, bottom, left. */
    static List<int[]> ring(int r) {
        List<int[]> out = new ArrayList<>(8 * r);
        for (int x = -r; x <= r; x++) {
            out.add(new int[]{x, -r});
        }
        for (int z = -r + 1; z <= r; z++) {
            out.add(new int[]{r, z});
        }
        for (int x = r - 1; x >= -r; x--) {
            out.add(new int[]{x, r});
        }
        for (int z = r - 1; z >= -r + 1; z--) {
            out.add(new int[]{-r, z});
        }
        return out;
    }

    /** The origin block's x of cell {@code cx}. */
    public int originX(int cx) {
        return baseX + cx * spacing;
    }

    /** The origin block's z of cell {@code cz}. */
    public int originZ(int cz) {
        return baseZ + cz * spacing;
    }

    /** The whole cell around an origin: nothing a wish does may reach outside it. */
    public Region cell(int originX, int originZ) {
        int lo = spacing / 2;
        int hi = spacing - lo - 1;
        return new Region(originX - lo, originX + hi, originZ - lo, originZ + hi);
    }

    /** The cell less the margin: where a wish's anchor may be. */
    public Region reach(int originX, int originZ) {
        return cell(originX, originZ).shrink(margin);
    }

    /**
     * An island as a {@link Base}: its cell is the area nothing may reach past, the cell
     * less the margin is the reach an anchor must be in, and home is where he stands on the grass.
     * Null for no island.
     */
    public Base base(Island i) {
        if (i == null) {
            return null;
        }
        return new Base(i.owner(), Bases.OVERWORLD, i.x(), i.y(), i.z(), i.standY(),
                cell(i.x(), i.z()), reach(i.x(), i.z()));
    }

    /**
     * The overworld column a position stands for, for the grid (#1785 step 4): the overworld as it is,
     * the Nether ×8 (where neighbours are only 128 blocks apart — the easy way to reach someone), and
     * {@code null} for any other dimension. Pure.
     */
    public static double[] overworldColumn(String dimension, double x, double z) {
        if ("minecraft:overworld".equals(dimension)) {
            return new double[]{x, z};
        }
        if ("minecraft:the_nether".equals(dimension)) {
            return new double[]{x * 8.0, z * 8.0};
        }
        return null;
    }

    /** Which cell a column is in: {@code {cx, cz}}. */
    public int[] cellOf(double x, double z) {
        return new int[]{Math.floorDiv((int) Math.floor(x) - baseX + spacing / 2, spacing),
                Math.floorDiv((int) Math.floor(z) - baseZ + spacing / 2, spacing)};
    }
}
