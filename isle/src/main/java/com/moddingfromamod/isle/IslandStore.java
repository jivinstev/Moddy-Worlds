package com.moddingfromamod.isle;

import java.io.IOException;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moddingfromamod.store.WishStoreFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The island registry on disk</b> — {@code <world>/wishes/islands.json} (T9's rules).
 *
 * <pre>{"store":"islands","v":1,"spacing":1024,"margin":96,"islands":[{ownerId, cellX, cellZ, x, y, z, createdAt}]}</pre>
 *
 * <p>A WORLD store, not a shelf one: an island is a place in this world, and the shelf follows a
 * child to a world with no islands. It lives with the world's other stores, so
 * a copy of the world directory carries it.
 *
 * <p>The core's three store rules, through {@link WishStoreFile}: an
 * unreadable file is a REFUSAL, never an empty registry (an empty one would hand every child a new
 * island on their next join, on top of their old one's cell being forgotten — and then re-allocate
 * that cell to someone else); a refused file is never written over (the {@link WishStoreFile.Guard}
 * latch, per instance, so a GameTest's damaged fixture blocks only its own store); a file with no
 * version reads as legacy.
 *
 * <p><b>The grid is fixed at creation.</b> The file records the spacing and margin the first island
 * was made with, and a load uses THOSE, whatever the manifest now says: a spacing changed after
 * islands exist would put the next child's cell on top of an existing island. The disagreement is
 * logged, never obeyed. Minecraft-free (only files), so its rules are Gate A tests.
 */
public final class IslandStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("moddingfromamod/islands");

    /** The envelope name, and the newest schema this build reads. */
    public static final String STORE = "islands";
    public static final int SCHEMA = 1;
    public static final String FILE = "islands.json";

    private final Path file;
    private final WishStoreFile.Guard guard = new WishStoreFile.Guard();

    public IslandStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** What a load found. {@code refused} is the reason the file could not be read, or null. */
    public record Loaded(IslandRegistry registry, String refused, boolean gridFromFile) {
    }

    /**
     * Read the registry. {@code wanted} is the grid the configuration asks for; it is used only when
     * the file does not say which grid its islands were made on (absent, or a file with none yet).
     * On a refusal the registry is EMPTY and every later {@link #save} is refused too, so the
     * caller must not hand out islands from it — {@code IslandService} refuses to open.
     */
    public synchronized Loaded load(IslandGrid wanted) {
        guard.reset();
        var read = guard.observe(WishStoreFile.readFile(file, STORE, SCHEMA));
        if (read instanceof WishStoreFile.Read.Refused r) {
            LOGGER.error("could not read the island registry at {}: {}. No island will be handed "
                    + "out and nothing will be saved over it — a grown-up should look at it.",
                    file, r.reason());
            return new Loaded(new IslandRegistry(wanted), r.reason(), false);
        }
        JsonObject root = WishStoreFile.rootOf(read);
        IslandGrid grid = wanted;
        boolean fromFile = false;
        if (root != null && root.has("spacing") && root.has("margin")) {
            try {
                IslandGrid recorded = new IslandGrid(root.get("spacing").getAsInt(),
                        root.get("margin").getAsInt(), wanted.baseX(), wanted.y(), wanted.baseZ());
                if (recorded.spacing() != wanted.spacing() || recorded.margin() != wanted.margin()) {
                    LOGGER.warn("the islands in {} were made {} blocks apart (margin {}); the world's "
                            + "settings now say {} (margin {}). Keeping the recorded grid: a spacing "
                            + "is fixed once islands exist.", file, recorded.spacing(), recorded.margin(),
                            wanted.spacing(), wanted.margin());
                }
                grid = recorded;
                fromFile = true;
            } catch (RuntimeException e) {
                guard.observe(new WishStoreFile.Read.Refused("its grid is unreadable: " + e));
                LOGGER.error("the island registry at {} records a grid this build cannot use ({}); "
                        + "refusing it", file, e.toString());
                return new Loaded(new IslandRegistry(wanted), "its grid is unreadable: " + e, false);
            }
        }
        IslandRegistry registry = new IslandRegistry(grid);
        JsonElement arr = root == null ? null : root.get("islands");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement el : arr.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                try {
                    JsonObject io = el.getAsJsonObject();
                    // #1785 step 7: a rested island keeps its cell and lets its owner go
                    if (io.has("retiredAt")) {
                        registry.restoreRetired(Island.fromJson(io), instant(io.get("retiredAt")));
                        continue;
                    }
                    if (io.has("seenAt") && io.has("ownerId")) {
                        registry.seen(java.util.UUID.fromString(io.get("ownerId").getAsString()),
                                instant(io.get("seenAt")));
                    }
                    if (!registry.restore(Island.fromJson(io))) {
                        LOGGER.warn("skipped an island record that repeats an owner or a cell, or is "
                                + "incomplete: {}", el);
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("skipped a damaged island record: {}", e.toString());
                }
            }
        }
        return new Loaded(registry, null, fromFile);
    }

    private static long instant(JsonElement e) {
        try {
            return java.time.Instant.parse(e.getAsString()).toEpochMilli();
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    /** False when the last load was refused: the file is being left alone on purpose. */
    public boolean mayWrite() {
        return guard.mayWrite();
    }

    /** Write the whole registry, stamped, atomically. Returns false when the latch refused it. */
    public synchronized boolean save(IslandRegistry registry) {
        if (!guard.mayWrite()) {
            LOGGER.error("NOT saving islands — {} could not be read ({}). The file has been left "
                    + "alone on purpose.", file, guard.refusalReason());
            return false;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("spacing", registry.grid().spacing());
        payload.addProperty("margin", registry.grid().margin());
        JsonArray arr = new JsonArray();
        for (Island i : registry.all()) {
            JsonObject o = i.toJson();
            Long seenAt = registry.seenAt(i.owner());
            if (seenAt != null) {
                o.addProperty("seenAt", java.time.Instant.ofEpochMilli(seenAt).toString());
            }
            arr.add(o);
        }
        // #1785 step 7: a rested island is written WITH its record, so a jar from before still sees the
        // cell as taken (it reads the owner and the place, and ignores retiredAt) — never handed out twice.
        for (Island i : registry.retired()) {
            JsonObject o = i.toJson();
            o.addProperty("retiredAt", java.time.Instant.ofEpochMilli(registry.retiredAt(i)).toString());
            arr.add(o);
        }
        payload.add("islands", arr);
        try {
            WishStoreFile.writeFile(file, STORE, SCHEMA, payload);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("could not save islands to {}: {}", file, e.toString());
            return false;
        }
    }
}
