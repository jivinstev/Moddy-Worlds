package com.moddingfromamod.isle;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** the grid and the allocation, Minecraft-free. Where they RUN is {@code IslandGameTest}. */
class IslandGridTest {

    @Test
    void theSpiralStartsAtSpawnAndNeverRepeatsACell() {
        assertEquals(0, IslandGrid.cellAt(0)[0]);
        assertEquals(0, IslandGrid.cellAt(0)[1]);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int[] c = IslandGrid.cellAt(i);
            assertTrue(seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)), "cell repeated at index " + i);
            int ring = Math.max(Math.abs(c[0]), Math.abs(c[1]));
            // nearest ring first: index i lives in ring r iff (2r-1)^2 <= i < (2r+1)^2
            assertTrue(i < (2 * ring + 1) * (2 * ring + 1) && i >= (2 * ring - 1) * (2 * ring - 1) || i == 0,
                    "index " + i + " is in ring " + ring);
        }
    }

    @Test
    void theSpawnCellIsNeverAnybodysIsland() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        for (int i = 0; i < 30; i++) {
            Island is = r.claim(UUID.randomUUID(), 0).island();
            assertFalse(is.cellX() == 0 && is.cellZ() == 0, "the pack's spawn island was handed out");
        }
        assertNull(r.at(0, 0), "the spawn belongs to nobody, so a wish aimed there is nobody's");
    }

    @Test
    void twoChildrenGetTwoCellsAndAReJoinFindsTheSameOne() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        IslandRegistry.Claim ca = r.claim(a, 1);
        IslandRegistry.Claim cb = r.claim(b, 2);
        assertTrue(ca.isNew() && cb.isNew());
        assertNotEquals(ca.island().x() + "," + ca.island().z(), cb.island().x() + "," + cb.island().z());
        IslandRegistry.Claim again = r.claim(a, 99);
        assertFalse(again.isNew(), "a re-join must not make a second island");
        assertEquals(ca.island(), again.island(), "a re-join finds the same island");
        assertEquals(2, r.size());
        assertSame(r.islandOf(a), r.at(ca.island().x() + 10, ca.island().z() - 10));
    }

    @Test
    void islandsAreSpacingApartAtThePacksHeight() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        Island first = r.claim(UUID.randomUUID(), 0).island();
        assertEquals(IslandGrid.ISLAND_Y, first.y());
        assertEquals(0, Math.floorMod(first.x(), 1024));
        assertEquals(0, Math.floorMod(first.z(), 1024));
        assertEquals(1024, Math.max(Math.abs(first.x()), Math.abs(first.z())));
    }

    @Test
    void aFreedCellIsReusedInSpiralOrder() {
        // restore() models a registry read back with a gap (a record removed by hand): the next
        // child fills the gap rather than walking outwards for ever.
        IslandGrid g = IslandGrid.of(1024);
        IslandRegistry r = new IslandRegistry(g);
        int[] two = IslandGrid.cellAt(2);
        r.restore(new Island(UUID.randomUUID(), two[0], two[1], g.originX(two[0]), g.y(), g.originZ(two[1]), 0));
        Island next = r.claim(UUID.randomUUID(), 0).island();
        int[] one = IslandGrid.cellAt(1);
        assertEquals(one[0], next.cellX());
        assertEquals(one[1], next.cellZ());
    }

    @Test
    void theCellTilesTheWorldAndTheReachSitsInsideIt() {
        IslandGrid g = IslandGrid.of(1024);
        com.moddingfromamod.world.Region cell = g.cell(1024, 0);
        assertEquals(1024, cell.maxX() - cell.minX() + 1);
        com.moddingfromamod.world.Region west = g.cell(0, 0);
        assertEquals(west.maxX() + 1, cell.minX(), "neighbouring cells touch and do not overlap");
        com.moddingfromamod.world.Region reach = g.reach(1024, 0);
        assertTrue(cell.contains(reach.minX(), reach.minZ()) && cell.contains(reach.maxX(), reach.maxZ()));
        // the biggest build anchored at the edge of the reach stays in the cell
        assertTrue(cell.containsBox(reach.maxX(), reach.maxZ(), IslandGrid.MARGIN - 1));
        int[] c = g.cellOf(1024 + 511, 0);
        assertEquals(1, c[0]);
        assertEquals(2, g.cellOf(1024 + 512, 0)[0]);
    }

    @Test
    void theSpacingMustClearTheViewDistance() {
        assertEquals(288, IslandGrid.minSpacing(8), "32 * (8 + 1)");
        assertTrue(IslandGrid.DEFAULT_SPACING >= IslandGrid.minSpacing(8));
        assertThrows(IllegalArgumentException.class, () -> IslandGrid.of(100),
                "a spacing with no room inside its margins is refused");
    }

    @Test
    void theSwitchIsOffUnlessTheManifestSetsASpacing() {
        assertEquals(0, IslandsConfig.parse(null));
        assertEquals(0, IslandsConfig.parse(""));
        assertEquals(0, IslandsConfig.parse("off"));
        assertEquals(0, IslandsConfig.parse("0"));
        assertEquals(1024, IslandsConfig.parse(" 1024 "));
        assertFalse(IslandsConfig.enabled(), "no world has islands unless its manifest says so");
    }
}
