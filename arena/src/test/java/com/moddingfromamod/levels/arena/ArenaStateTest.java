package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** The arena's saved record: the panel's settings survive a save and a load, and an older record loses nothing. */
class ArenaStateTest {

    private static ArenaState read(String json) {
        return ArenaState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static JsonElement write(ArenaState s) {
        return ArenaState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
    }

    /** A record as the arena saved it before the control panel (no version, no settings). */
    private static final String BEFORE_THE_PANEL = "{\"built\":{\"arenaSize\":31,\"prepSize\":9,\"wallHeight\":4,"
            + "\"floorY\":64},\"arrived\":[\"00000000-0000-0000-0000-000000000001\"],"
            + "\"upgrades\":[\"start-control\",\"no-natural-spawns\"],\"battleOn\":false}";

    @Test
    void aRecordFromBeforeThePanelReadsAsTheDefaultsAndKeepsEverythingElse() {
        ArenaState s = read(BEFORE_THE_PANEL);
        assertEquals(1, s.readVersion());
        assertEquals(ArenaSettings.DEFAULT, s.settings());
        assertEquals(ArenaPlace.DEFAULT, s.built());
        assertEquals(java.util.Set.of("start-control", "no-natural-spawns"), s.upgrades());
        assertTrue(ArenaPlace.pendingUpgrades(s.upgrades()).contains(ArenaPlace.UPGRADE_CONTROL_PANEL),
                "it gets its control panel once");
        JsonElement saved = write(s);
        assertEquals(ArenaState.VERSION, saved.getAsJsonObject().get("v").getAsInt(), "saved again as the new version");
        assertEquals(5, saved.getAsJsonObject().get("waveSize").getAsInt());
        assertEquals("normal", saved.getAsJsonObject().get("difficulty").getAsString());
    }

    @Test
    void theSettingsSurviveASaveAndALoad() {
        ArenaState s = read(BEFORE_THE_PANEL);
        s.settings(new ArenaSettings(9, ArenaDifficulty.EXTREME));
        assertTrue(s.isDirty(), "a change is saved");
        ArenaState again = read(write(s).toString());
        assertEquals(new ArenaSettings(9, ArenaDifficulty.EXTREME), again.settings());
        assertEquals(ArenaState.VERSION, again.readVersion());
        assertEquals(s.upgrades(), again.upgrades());
        assertEquals(s.built(), again.built());
        assertNull(again.unknownDifficulty());
    }

    @Test
    void aDifficultyThisJarDoesNotKnowReadsAsTheDefaultAndIsReported() {
        ArenaState s = read("{\"v\":2,\"waveSize\":3,\"difficulty\":\"nightmare\"}");
        assertEquals(new ArenaSettings(3, ArenaDifficulty.NORMAL), s.settings());
        assertEquals("nightmare", s.unknownDifficulty());
        assertEquals(new ArenaSettings(10, ArenaDifficulty.NORMAL), read("{\"v\":2,\"waveSize\":40}").settings(),
                "a size out of range is brought into it");
    }
}
