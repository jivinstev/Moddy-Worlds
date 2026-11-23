package com.moddingfromamod.levels.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The shared level description: pack ids as NeoForge names a mod's built-in packs, and the marker that claims a world. */
class LevelTest {

    private static final Level L = new Level("moddyexample", "example", "Moddy Example", List.of("sky", "extras"));

    @Test
    void packIdsAreNeoForgesNamesForAModsBuiltInPacksWithTheMarkerLast() {
        assertEquals("mod/moddyexample:datapacks/", L.packPrefix());
        assertEquals(List.of("sky", "extras", "level_marker"), L.allPacks());
        assertEquals(List.of("mod/moddyexample:datapacks/sky", "mod/moddyexample:datapacks/extras",
                "mod/moddyexample:datapacks/level_marker"), L.packIds());
        assertEquals("mod/moddyexample:datapacks/level_marker", L.markPackId());
        assertEquals("moddyexample_level", L.markerName());
    }

    @Test
    void onlyTheMarkerMarksAWorld() {
        assertTrue(L.marks(List.of("vanilla", "mod_data", "mod/moddyexample:datapacks/level_marker")));
        assertFalse(L.marks(List.of("vanilla", "mod/moddyexample:datapacks/sky")), "a third-party pack alone is not it");
        assertFalse(L.marks(List.of("vanilla", "mod/moddyother:datapacks/level_marker")), "another level's marker");
        assertFalse(L.marks(null));
    }

    @Test
    void aLevelWithNoPacksClaimsTheSameWay() {
        Level bare = new Level("moddyarena", "arena", "Moddy Arena", List.of());
        assertEquals(List.of("mod/moddyarena:datapacks/level_marker"), bare.packIds());
        assertTrue(bare.marks(List.of("vanilla", "mod/moddyarena:datapacks/level_marker")));
    }

    @Test
    void theMarkerIsNotALevelsOwnPack() {
        assertThrows(IllegalArgumentException.class, () -> new Level("m", "e", "E", List.of("level_marker")));
    }
}
