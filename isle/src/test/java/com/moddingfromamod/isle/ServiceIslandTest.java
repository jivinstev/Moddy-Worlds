package com.moddingfromamod.isle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1637: the canary's island is one fixed far cell, never a child's, never counted as one. */
class ServiceIslandTest {

    @Test
    @DisplayName("#1637: the service island is the fixed far cell, and claiming it again returns the same one")
    void theServiceIslandIsTheFixedFarCell() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        UUID canary = UUID.randomUUID();
        IslandRegistry.Claim c = r.claimService(canary, 1);
        assertTrue(c.isNew());
        assertEquals(IslandRegistry.SERVICE_CELL_X, c.island().cellX());
        assertEquals(IslandRegistry.SERVICE_CELL_Z, c.island().cellZ());
        assertEquals(-48 * 1024, c.island().x(), "about 49 000 blocks out, not a neighbour's 1024");
        assertTrue(IslandRegistry.isService(c.island()));
        IslandRegistry.Claim again = r.claimService(canary, 99);
        assertFalse(again.isNew());
        assertSame(c.island(), again.island());
    }

    @Test
    @DisplayName("#1637: a child joining after the canary still gets slot 1 — the canary takes no child's place")
    void aChildAfterTheCanaryStillGetsTheFirstSlot() {
        IslandRegistry withCanary = new IslandRegistry(IslandGrid.of(1024));
        IslandRegistry without = new IslandRegistry(IslandGrid.of(1024));
        withCanary.claimService(UUID.randomUUID(), 1);
        for (int k = 0; k < 30; k++) {
            UUID child = UUID.randomUUID();
            Island a = withCanary.claim(child, k).island();
            Island b = without.claim(UUID.randomUUID(), k).island();
            assertEquals(b.cellX(), a.cellX(), "child " + k);
            assertEquals(b.cellZ(), a.cellZ(), "child " + k);
            assertFalse(IslandRegistry.isService(a));
        }
        assertEquals(30, withCanary.childCount(), "the canary is not counted as a child");
        assertEquals(31, withCanary.size());
    }

    @Test
    @DisplayName("#1637: the service cell is never handed to a child, even before the canary has claimed it")
    void theServiceCellIsNeverAChilds() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        // enough children to reach ring 48 would be ~9 400; ask the rule directly instead
        for (int k = 0; k < 2_000; k++) {
            assertFalse(IslandRegistry.isService(r.claim(UUID.randomUUID(), k).island()));
        }
        // a second owner cannot take the service cell from the canary
        UUID canary = UUID.randomUUID();
        r.claimService(canary, 1);
        assertThrows(IllegalStateException.class, () -> r.claimService(UUID.randomUUID(), 2));
        assertSame(r.islandOf(canary), r.at(-48 * 1024 + 5, -48 * 1024 - 5));
    }

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path dir;

    @Test
    @DisplayName("#1637: after a restart the canary still holds the service cell and children's slots are unchanged")
    void theReservationSurvivesARestart() {
        IslandStore store = new IslandStore(dir.resolve(IslandStore.FILE));
        IslandRegistry r = store.load(IslandGrid.of(1024)).registry();
        UUID canary = UUID.randomUUID();
        UUID kid = UUID.randomUUID();
        r.claimService(canary, 1);
        Island kids = r.claim(kid, 2).island();
        store.save(r);
        IslandRegistry back = new IslandStore(dir.resolve(IslandStore.FILE)).load(IslandGrid.of(1024)).registry();
        assertTrue(IslandRegistry.isService(back.islandOf(canary)));
        assertEquals(kids.cellX(), back.islandOf(kid).cellX());
        assertEquals(kids.cellZ(), back.islandOf(kid).cellZ());
        assertEquals(1, back.childCount());
        Island next = back.claim(UUID.randomUUID(), 3).island();
        assertFalse(IslandRegistry.isService(next), "the next child after a restart is not put on the canary's cell");
    }
}
