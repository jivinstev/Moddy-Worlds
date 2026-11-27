package com.moddingfromamod.isle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.UUID;

import com.moddingfromamod.plugin.Plugins;
import com.moddingfromamod.world.Region;
import com.moddingfromamod.world.WorldRegion;
import com.moddingfromamod.world.WorldRegions;
import org.junit.jupiter.api.Test;

/**
 * <b>A new island never goes where another world feature has a region</b> (core #182). A battle arena built with
 * {@code /moddy arena create} in an island world is one. The first free cell has a region in it, so the next child's
 * island skips to the cell after, and the pre-generation plan skips it too. An island already held is never moved,
 * and with no region anywhere the first cell is handed out exactly as before.
 */
class IslandRegionsTest {

    private static WorldRegions over(Region area) {
        WorldRegion r = new WorldRegion() {
            @Override public String id() { return "test/arena"; }
            @Override public Region area() { return area; }
        };
        return new WorldRegions() {
            @Override public WorldRegion at(String d, double x, double y, double z) { return null; }
            @Override public List<WorldRegion> all() { return List.of(r); }
        };
    }

    @Test
    void aNewIslandSkipsACellARegionStandsIn() {
        IslandGrid grid = IslandGrid.of(IslandGrid.DEFAULT_SPACING);
        int[] first = IslandGrid.cellAt(1);
        Region inFirst = new Region(grid.originX(first[0]) - 20, grid.originX(first[0]) + 20,
                grid.originZ(first[1]) - 20, grid.originZ(first[1]) + 20);
        IslandRegistry before = new IslandRegistry(grid);
        UUID held = UUID.randomUUID();
        Island kept = before.claim(held, 0).island();
        assertEquals(first[0], kept.cellX(), "with no region, the first cell is handed out as before");
        try {
            Plugins.shared().replaceForTest(WorldRegions.class, over(inFirst));
            IslandRegistry r = new IslandRegistry(grid);
            Island made = r.claim(UUID.randomUUID(), 0).island();
            int[] second = IslandGrid.cellAt(2);
            assertArrayEquals(second, new int[] {made.cellX(), made.cellZ()},
                    "the island went into the cell the region stands in");
            assertNotEquals(first[0] * 1000 + first[1], r.upcoming(1).get(0)[0] * 1000 + r.upcoming(1).get(0)[1],
                    "pre-generation plans the blocked cell");
            assertEquals(kept.cellX(), before.claim(held, 1).island().cellX(), "a held island never moves");
        } finally {
            Plugins.shared().replaceForTest(WorldRegions.class, null);
        }
    }
}
