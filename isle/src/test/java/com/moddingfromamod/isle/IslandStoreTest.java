package com.moddingfromamod.isle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** the island registry on disk, under the core's three store rules. */
class IslandStoreTest {

    @TempDir
    Path dir;

    @Test
    void aRoundTripKeepsEveryIslandAndTheEnvelope() throws Exception {
        Path f = dir.resolve("wishes").resolve(IslandStore.FILE);
        IslandStore store = new IslandStore(f);
        IslandStore.Loaded empty = store.load(IslandGrid.of(1024));
        assertNull(empty.refused(), "an absent store is the one empty that is right");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Island ia = empty.registry().claim(a, 1_700_000_000_000L).island();
        Island ib = empty.registry().claim(b, 1_700_000_001_000L).island();
        assertTrue(store.save(empty.registry()));

        JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        assertEquals("islands", root.get("store").getAsString());
        assertEquals(IslandStore.SCHEMA, root.get("v").getAsInt());
        assertEquals(1024, root.get("spacing").getAsInt());
        assertTrue(root.getAsJsonArray("islands").get(0).getAsJsonObject().has("createdAt"));

        IslandStore.Loaded back = new IslandStore(f).load(IslandGrid.of(1024));
        assertEquals(ia, back.registry().islandOf(a));
        assertEquals(ib, back.registry().islandOf(b));
        assertFalse(back.registry().claim(a, 5).isNew(), "a restart must not make him a new island");
    }

    @Test
    void anUnreadableStoreIsRefusedAndNeverWrittenOver() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        Files.writeString(f, "{not json");
        IslandStore store = new IslandStore(f);
        IslandStore.Loaded l = store.load(IslandGrid.of(1024));
        assertNotNull(l.refused(), "a damaged registry must be a REFUSAL, never an empty one");
        assertFalse(store.mayWrite());
        l.registry().claim(UUID.randomUUID(), 0);
        assertFalse(store.save(l.registry()), "the latch refuses the write");
        assertEquals("{not json", Files.readString(f), "the damaged bytes survive for a grown-up");
    }

    @Test
    void aFileFromANewerModdyIsRefused() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        Files.writeString(f, "{\"store\":\"islands\",\"v\":99,\"islands\":[]}");
        IslandStore store = new IslandStore(f);
        assertNotNull(store.load(IslandGrid.of(1024)).refused());
        assertFalse(store.save(new IslandRegistry(IslandGrid.of(1024))));
    }

    @Test
    void anotherStoresFileIsRefused() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        Files.writeString(f, "{\"store\":\"paintings\",\"v\":1,\"paintings\":[]}");
        assertNotNull(new IslandStore(f).load(IslandGrid.of(1024)).refused());
    }

    @Test
    void theRecordedGridWinsOverALaterSpacing() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        IslandStore store = new IslandStore(f);
        IslandStore.Loaded l = store.load(IslandGrid.of(1024));
        l.registry().claim(UUID.randomUUID(), 0);
        store.save(l.registry());
        IslandStore.Loaded later = new IslandStore(f).load(IslandGrid.of(512));
        assertTrue(later.gridFromFile());
        assertEquals(1024, later.registry().grid().spacing(),
                "a spacing changed after islands exist would put the next child on top of one");
        Island next = later.registry().claim(UUID.randomUUID(), 0).island();
        assertEquals(0, Math.floorMod(next.x(), 1024));
    }

    @Test
    void oneDamagedRecordCostsThatRecordOnly() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        UUID a = UUID.randomUUID();
        Files.writeString(f, "{\"store\":\"islands\",\"v\":1,\"spacing\":1024,\"margin\":96,\"islands\":["
                + "{\"ownerId\":\"" + a + "\",\"cellX\":0,\"cellZ\":-1,\"x\":0,\"y\":63,\"z\":-1024},"
                + "{\"ownerId\":\"not-a-uuid\",\"cellX\":1,\"cellZ\":-1,\"x\":1024,\"y\":63,\"z\":-1024},"
                + "{\"cellX\":1}]}");
        IslandStore.Loaded l = new IslandStore(f).load(IslandGrid.of(1024));
        assertNull(l.refused());
        assertEquals(1, l.registry().size());
        assertNotNull(l.registry().islandOf(a));
    }

    /**
     * (#1164): an erased child's island, as an erase tool leaves it — its
     * place under a version-5 owner id that is nobody's (a synthetic one here), with the epoch
     * for a time. The jar must read it, keep the cell TAKEN (the next child is never handed a cell with
     * the erased child's blocks still on it) and write it back byte for byte, so an upgrade
     * keeps the record identical.
     */
    @Test
    void anErasedIslandsTombstoneIsReadAndKept() throws Exception {
        Path f = dir.resolve(IslandStore.FILE);
        String tomb = "{\"ownerId\":\"00000000-0000-5000-8000-000000000001\",\"cellX\":1,\"cellZ\":0,"
                + "\"x\":1024,\"y\":63,\"z\":0,\"createdAt\":\"1970-01-01T00:00:00Z\"}";
        int[] first = IslandGrid.cellAt(1);
        String record = tomb.replace("\"cellX\":1,\"cellZ\":0", "\"cellX\":" + first[0] + ",\"cellZ\":" + first[1]);
        Files.writeString(f, "{\"store\":\"islands\",\"v\":1,\"spacing\":1024,\"margin\":96,\"islands\":["
                + record + "]}");
        IslandStore store = new IslandStore(f);
        IslandStore.Loaded l = store.load(IslandGrid.of(1024));
        assertNull(l.refused());
        assertEquals(1, l.registry().size(), "the tombstone is a record like any other");
        assertEquals(5, l.registry().all().get(0).owner().version(), "an owner id no player can have");
        Island next = l.registry().claim(UUID.randomUUID(), 5).island();
        assertFalse(next.cellX() == first[0] && next.cellZ() == first[1],
                "the erased child's cell is never handed to the next child");
        assertTrue(store.save(l.registry()));
        JsonObject back = JsonParser.parseString(Files.readString(f)).getAsJsonObject()
                .getAsJsonArray("islands").get(0).getAsJsonObject();
        assertEquals(JsonParser.parseString(record), back, "written back exactly as the eraser left it");
    }
}
