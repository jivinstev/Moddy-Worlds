package com.moddingfromamod.levels.shared;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.LoggerFactory;

/**
 * <b>Is THIS world one of the level's?</b> A level is switched on per world, never per instance, so one game can hold
 * the level's world and a plain world side by side. Its {@code WorldModuleType.claims(server)} calls {@link #claims}.
 *
 * <p>A world is the level's when it was created with the level's marker pack on ({@link Level#MARKER}: World Type ticks
 * it; a dedicated server names it in {@code initial-enabled-packs}): it is then in the world's own {@code level.dat}
 * data configuration. The
 * first start records it in the world's saved data ({@code data/<modid>_level.dat}), so the world stays the level's
 * whatever happens to its pack list later.
 *
 * <p>SHARED SOURCE (see {@link Level}).
 */
public final class LevelClaim {

    private static final Map<String, SavedDataType<Marker>> TYPES = new ConcurrentHashMap<>();

    private LevelClaim() {
    }

    /** True when this world is the level's: its saved data says so, or its packs mark it (then recorded, once). */
    public static boolean claims(MinecraftServer server, Level level) {
        if (server == null) {
            return false;
        }
        var storage = server.overworld().getDataStorage();
        SavedDataType<Marker> type = TYPES.computeIfAbsent(level.markerName(),
                n -> new SavedDataType<>(n, () -> new Marker(""), Marker.CODEC));
        Marker m = storage.get(type);
        if (m != null && level.id().equals(m.level)) {
            return true;
        }
        if (level.marks(server.getWorldData().getDataConfiguration().dataPacks().getEnabled())) {
            Marker mark = new Marker(level.id());
            mark.setDirty();
            storage.set(type, mark);
            LoggerFactory.getLogger(level.loggerName()).info(
                    "this world was made as {} (its packs are on): recorded in its saved data", level.typeName());
            return true;
        }
        return false;
    }

    /** The world's record of which level it was made as. */
    static final class Marker extends SavedData {
        static final Codec<Marker> CODEC = Codec.STRING.fieldOf("level").codec().xmap(Marker::new, m -> m.level);

        final String level;

        Marker(String level) {
            this.level = level == null ? "" : level;
        }
    }
}
