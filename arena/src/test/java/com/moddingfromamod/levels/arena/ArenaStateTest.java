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
        assertTrue(saved.getAsJsonObject().get("waveSize") == null, "no monsters size is saved any more");
        assertEquals("normal", saved.getAsJsonObject().get("difficulty").getAsString());
    }

    @Test
    void theSettingsSurviveASaveAndALoad() {
        ArenaState s = read(BEFORE_THE_PANEL);
        s.settings(new ArenaSettings(ArenaDifficulty.EXTREME, 15));
        assertTrue(s.isDirty(), "a change is saved");
        ArenaState again = read(write(s).toString());
        assertEquals(new ArenaSettings(ArenaDifficulty.EXTREME, 15), again.settings());
        assertEquals(ArenaState.VERSION, again.readVersion());
        assertEquals(s.upgrades(), again.upgrades());
        assertEquals(s.built(), again.built());
        assertNull(again.unknownDifficulty());
    }

    @Test
    void aDifficultyThisJarDoesNotKnowReadsAsTheDefaultAndIsReported() {
        ArenaState s = read("{\"v\":2,\"waveSize\":3,\"difficulty\":\"nightmare\"}");
        assertEquals(new ArenaSettings(ArenaDifficulty.NORMAL), s.settings());
        assertEquals("nightmare", s.unknownDifficulty());
        assertEquals(ArenaSettings.DEFAULT, read("{\"v\":2,\"waveSize\":40}").settings(),
                "an old saved size is not read: the difficulty brings its own");
    }

    @Test
    void aVersionTwoRecordReadsFiveWavesAndNoChampions() {
        ArenaState s = read("{\"v\":2,\"waveSize\":7,\"difficulty\":\"hard\",\"upgrades\":[\"control-panel\"]}");
        assertEquals(2, s.readVersion());
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD, 5), s.settings());
        assertTrue(s.champions().isEmpty());
        assertTrue(ArenaPlace.pendingUpgrades(s.upgrades()).contains(ArenaPlace.UPGRADE_CHAMPIONS),
                "it gets its Champions board once");
    }

    @Test
    void theWavesAndTheChampionsSurviveASaveAndALoad() {
        ArenaState s = read(BEFORE_THE_PANEL);
        s.settings(new ArenaSettings(ArenaDifficulty.EASY, 0));
        ArenaChampions.Key k = ArenaChampions.Key.of(s.settings());
        String id = "00000000-0000-0000-0000-000000000001";
        assertEquals(1, s.record(id, "Steve", k, new ArenaChampions.Reach(12, false, false, 2400)));
        assertEquals(0, s.record(id, "Steve", k, new ArenaChampions.Reach(0, true, false, 0)), "no wave: no run");
        ArenaState again = read(write(s).toString());
        assertEquals(ArenaState.VERSION, again.readVersion());
        assertEquals(new ArenaSettings(ArenaDifficulty.EASY, 0), again.settings());
        assertEquals(s.champions(), again.champions());
        ArenaChampions.Entry e = ArenaChampions.bestOf(again.champions(), id, k);
        assertEquals("Steve", e.name());
        assertEquals(12, e.wave());
        assertEquals(1, again.record(id, "Steve", k, new ArenaChampions.Reach(13, false, false, 3000)));
        assertEquals(13, ArenaChampions.bestOf(again.champions(), id, k).wave(), "the sequence goes on after a load");
        assertTrue(again.champions().get(0).seq() > e.seq());
        JsonElement saved = write(again);
        assertEquals(0, saved.getAsJsonObject().get("waveCount").getAsInt(), "Endless is saved as 0");
        assertTrue(saved.toString().matches("[^\\x00-\\x1f]*"), "plain");
        assertTrue(!saved.toString().contains("text"), "nothing a player typed: ids, names and scores only");
    }

    /** A version 3 record (0.1.5): its monsters size is dropped, and its champions are kept by difficulty and waves. */
    @Test
    void aVersionThreeRecordDropsTheSizeAndMigratesItsChampions() {
        String steve = "00000000-0000-0000-0000-000000000001";
        String old = "{\"v\":3,\"waveSize\":8,\"difficulty\":\"hard\",\"waveCount\":10,\"champions\":["
                + "{\"id\":\"" + steve + "\",\"name\":\"Steve\",\"difficulty\":\"hard\",\"waves\":10,\"size\":5,"
                + "\"wave\":6,\"ticks\":4000,\"seq\":1},"
                + "{\"id\":\"" + steve + "\",\"name\":\"Steve\",\"difficulty\":\"hard\",\"waves\":10,\"size\":8,"
                + "\"wave\":9,\"cleared\":true,\"ticks\":9000,\"seq\":2},"
                + "{\"id\":\"" + steve + "\",\"name\":\"Steve\",\"difficulty\":\"easy\",\"waves\":5,\"size\":1,"
                + "\"wave\":5,\"won\":true,\"cleared\":true,\"ticks\":3000,\"seq\":3}],\"runs\":3}";
        ArenaState s = read(old);
        assertEquals(3, s.readVersion());
        assertEquals(new ArenaSettings(ArenaDifficulty.HARD, 10), s.settings(), "the size is gone, nothing else");
        assertEquals(2, s.champions().size(), "his best at Hard 10, and his Easy 5");
        ArenaChampions.Entry hard = ArenaChampions.bestOf(s.champions(), steve, new ArenaChampions.Key("hard", 10));
        assertEquals(9, hard.wave(), "the furthest wave of the two");
        assertTrue(ArenaChampions.bestOf(s.champions(), steve, new ArenaChampions.Key("easy", 5)).won());
        JsonElement saved = write(s);
        assertEquals(ArenaState.VERSION, saved.getAsJsonObject().get("v").getAsInt());
        assertTrue(!saved.toString().contains("waveSize") && !saved.toString().contains("\"size\""),
                "a save has no size any more: " + saved);
        assertEquals(s.champions(), read(saved.toString()).champions());
    }

    /** A version 5 record (0.1.7, before a place could stand anywhere): its place is centred on (0, 0) (version 6). */
    @Test
    void aVersionFiveRecordIsAPlaceAtTheOriginAndAMovedPlaceIsKept() {
        String v5 = "{\"v\":5,\"built\":{\"arenaSize\":31,\"prepSize\":9,\"wallHeight\":4,\"floorY\":64}}";
        ArenaState s = read(v5);
        assertEquals(5, s.readVersion());
        assertEquals(0, s.built().ox());
        assertEquals(0, s.built().oz());
        assertEquals(ArenaPlace.DEFAULT, s.built());
        JsonElement saved = write(s);
        assertEquals(6, saved.getAsJsonObject().get("v").getAsInt());
        assertTrue(!saved.toString().contains("\"ox\""), "a place at (0, 0) saves what it saved before: " + saved);
        ArenaPlace moved = ArenaPlace.DEFAULT.at(1000, -2000);
        s.markBuilt(moved);
        ArenaState again = read(write(s).toString());
        assertEquals(moved, again.built());
        assertEquals(1000, again.built().ox());
        assertEquals(-2000, again.built().oz());
    }
}
