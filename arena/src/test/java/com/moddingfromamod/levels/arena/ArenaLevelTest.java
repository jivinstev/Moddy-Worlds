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

    /** The first-join words a child reads (the manifest's intro): plain words, no symbols, the module named. */
    @Test
    void itsIntroIsPlainWords() throws java.io.IOException {
        String text;
        try (var in = ArenaLevelTest.class.getResourceAsStream("/worlds/arena.json")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var o = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
        assertEquals(ArenaModule.ID, o.get("module").getAsString());
        var intro = o.getAsJsonArray("intro");
        assertTrue(intro.size() >= 1 && intro.size() <= 3);
        for (var line : intro) {
            String l = line.getAsString();
            assertTrue(l.chars().allMatch(c -> c >= 32 && c < 127), "plain characters only: " + l);
            assertTrue(!l.toLowerCase(java.util.Locale.ROOT).contains("wizard"), l);
        }
    }
}
