package com.moddingfromamod.isle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #2750: what makes a world an Isle, read off its own data configuration, and the spacing its manifest names. */
class IsleWorldTest {

    @Test
    @DisplayName("a world is an Isle when its enabled packs hold the Isle's marker pack, by NeoForge's id for it")
    void packMarksTheWorld() {
        assertEquals("mod/moddyisle:datapacks/level_marker", IsleWorld.MARK_PACK);
        assertTrue(IsleWorld.isIslePackSet(List.of("vanilla", "mod_data", IsleWorld.MARK_PACK)));
        assertFalse(IsleWorld.isIslePackSet(List.of("vanilla", "mod_data")), "a Default world in the same game");
        assertFalse(IsleWorld.isIslePackSet(List.of("vanilla", IsleWorld.PACK_PREFIX + "standard_skyblock")),
                "a SkyBlock pack alone does not make an Isle: the marker does");
        assertFalse(IsleWorld.isIslePackSet(null));
    }

    @Test
    @DisplayName("the islands' spacing comes from the manifest's islands.spacing, else 0 (off)")
    void spacingFromTheManifest() {
        assertEquals(1024, IsleWorld.spacingOf("{\"islands\":{\"spacing\":1024}}"));
        assertEquals(0, IsleWorld.spacingOf("{\"id\":\"workshop\"}"));
        assertEquals(0, IsleWorld.spacingOf("not json"));
        assertEquals(0, IsleWorld.spacingOf(null));
        assertEquals(0, IsleWorld.spacingOf("{\"islands\":{\"spacing\":-5}}"));
    }
}
