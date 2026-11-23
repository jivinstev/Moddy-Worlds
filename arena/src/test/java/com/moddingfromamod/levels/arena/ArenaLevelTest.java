package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Moddy Arena's worlds are claimed by its own marker pack, by NeoForge's id for it. */
class ArenaLevelTest {

    @Test
    void itsMarkerClaimsItsWorlds() {
        assertEquals("mod/moddyarena:datapacks/level_marker", ArenaMod.LEVEL.markPackId());
        assertTrue(ArenaMod.LEVEL.marks(List.of("vanilla", "mod/moddyarena:datapacks/level_marker")));
        assertEquals(ArenaModule.ID, ArenaMod.LEVEL.id());
    }
}
