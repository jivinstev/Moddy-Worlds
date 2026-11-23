package com.moddingfromamod.isle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #1785 step 7: an idle Wish Crafter's island rests after 14 days; its cell is never handed out again. */
class IslandReclaimTest {

    static final long DAY = 24L * 3600_000L;
    static final long NOW = 1_800_000_000_000L;

    @TempDir
    Path dir;

    @Test
    void onlyACraftersIslandUnseenForTheDaysRests() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        UUID idle = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        UUID magician = UUID.randomUUID();
        r.claim(idle, NOW - 30 * DAY);
        r.claim(recent, NOW - 30 * DAY);
        r.claim(magician, NOW - 30 * DAY);
        r.seen(idle, NOW - 15 * DAY);
        r.seen(recent, NOW - 2 * DAY);
        List<UUID> due = IslandReclaim.due(r.all(), r.seen(), Set.of(magician)::contains, NOW, 14);
        assertEquals(List.of(idle), due);
        assertTrue(IslandReclaim.due(r.all(), r.seen(), id -> false, NOW, 0).isEmpty(), "0 days: never");
    }

    @Test
    void anIslandMadeBeforeSeenWasKeptCountsFromItsMaking() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        UUID old = UUID.randomUUID();
        r.claim(old, NOW - 20 * DAY);
        assertEquals(List.of(old), IslandReclaim.due(r.all(), Map.of(), id -> false, NOW, 14));
    }

    @Test
    void aRestedCellIsNeverHandedOutAgainAndItsOwnerGetsAFreshOne() {
        IslandRegistry r = new IslandRegistry(IslandGrid.of(1024));
        UUID a = UUID.randomUUID();
        Island first = r.claim(a, NOW).island();
        assertEquals(first, r.retire(a, NOW));
        assertNull(r.islandOf(a));
        assertTrue(r.everRetired(a));
        Island next = r.claim(UUID.randomUUID(), NOW).island();
        assertFalse(next.cellX() == first.cellX() && next.cellZ() == first.cellZ(), "a stranger is never put on it");
        Island fresh = r.claim(a, NOW).island();
        assertFalse(fresh.cellX() == first.cellX() && fresh.cellZ() == first.cellZ(), "nor is he, on his old builds");
    }

    @Test
    void theStoreKeepsSeenAndRestedAndAnOldJarStillSeesTheCellTaken() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        IslandStore store = new IslandStore(f);
        IslandRegistry r = store.load(IslandGrid.of(1024)).registry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Island ia = r.claim(a, NOW).island();
        r.claim(b, NOW);
        r.seen(b, NOW + 5);
        r.retire(a, NOW + 10);
        assertTrue(store.save(r));
        String text = Files.readString(f);
        assertTrue(text.contains("\"retiredAt\"") && text.contains("\"seenAt\""), text);
        assertTrue(text.contains(a.toString()), "the rested island stays in the file, owner and place — an old jar "
                + "reading it sees the cell as taken");
        IslandRegistry back = new IslandStore(f).load(IslandGrid.of(1024)).registry();
        assertNull(back.islandOf(a));
        assertEquals(1, back.retired().size());
        assertEquals(ia.cellX(), back.retired().get(0).cellX());
        assertEquals(NOW + 5, back.seenAt(b));
    }

    @Test
    void theWelcomeBackSaysWhyItIsFresh() {
        assertTrue(IslandReclaim.welcomeBack(14).contains("rested after 14 days"));
    }
}
