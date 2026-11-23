package com.moddingfromamod.isle;

import com.google.gson.JsonObject;
import com.moddingfromamod.store.WishStoreFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * <b>The pre-generation's progress and its report</b> — {@code <world>/wishes/island-pregen.json} (#2542).
 *
 * <pre>{"store":"island-pregen","v":1,"radius":47,"cells":[{cellX,cellZ,centreX,centreZ,next,ahead,completedAt}],
 *  "chunksGenerated":N,"status":{…what a status reader reads…}}</pre>
 *
 * <p>Unlike every other store here, a file it cannot read is NOT a refusal: it holds no child's thing, only
 * how far a background job got, and redoing that job costs a few minutes of terrain that is already on disk
 * (a re-asked chunk is loaded, not generated). So a damaged or newer file is logged and the work starts
 * over — but a newer file is never written over ({@link WishStoreFile#writeBlocker}'s rule, kept).
 */
public final class IslandPregenStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("moddingfromamod/islands");

    public static final String STORE = "island-pregen";
    public static final int SCHEMA = 1;
    public static final String FILE = "island-pregen.json";

    private final Path file;
    private boolean mayWrite = true;

    public IslandPregenStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** The plan as recorded, at the radius in force now; a fresh plan when there is none or it is unreadable. */
    public IslandPregenPlan load(int radiusChunks) {
        var read = WishStoreFile.readFile(file, STORE, SCHEMA);
        if (read instanceof WishStoreFile.Read.Refused r) {
            LOGGER.warn("[pregen] could not read {} ({}); starting the pre-generation over", file, r.reason());
            mayWrite = WishStoreFile.writeBlocker(file, "v", SCHEMA) == null;
            return new IslandPregenPlan(radiusChunks);
        }
        return IslandPregenPlan.fromJson(WishStoreFile.rootOf(read), radiusChunks);
    }

    public boolean save(IslandPregenPlan plan, JsonObject status) {
        if (!mayWrite) {
            return false;
        }
        JsonObject payload = plan.toJson();
        payload.add("status", status);
        try {
            WishStoreFile.writeFile(file, STORE, SCHEMA, payload);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[pregen] could not save {}: {}", file, e.toString());
            return false;
        }
    }
}
