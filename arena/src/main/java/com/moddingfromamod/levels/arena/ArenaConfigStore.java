package com.moddingfromamod.levels.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * <b>The arena's settings, kept in the world</b> ({@code data/moddyarena_config.dat}, Moddy-Worlds#59): the values
 * {@code /moddy arena config} set for all arenas and for each one ({@link ArenaConfig}). On a dedicated server the world
 * is the server, so these are the server's settings. Server thread.
 */
public final class ArenaConfigStore extends SavedData {

    private static final Codec<Map<String, Integer>> VALUES = Codec.unboundedMap(Codec.STRING, Codec.INT);

    static final Codec<ArenaConfigStore> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("v", 1).forGetter(s -> 1),
            VALUES.optionalFieldOf("global", Map.of()).forGetter(s -> s.config.global()),
            Codec.unboundedMap(Codec.STRING, VALUES).optionalFieldOf("arenas", Map.of())
                    .forGetter(s -> s.config.perArena()))
            .apply(i, (v, g, a) -> {
                ArenaConfigStore s = new ArenaConfigStore();
                s.config = new ArenaConfig(g, a);
                return s;
            }));

    static final SavedDataType<ArenaConfigStore> TYPE =
            new SavedDataType<>(ArenaMod.MODID + "_config", ArenaConfigStore::new, CODEC);

    private ArenaConfig config = ArenaConfig.EMPTY;

    private ArenaConfigStore() {
    }

    /** This world's store (the overworld's data, as every arena record). */
    static ArenaConfigStore of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    ArenaConfig config() {
        return config;
    }

    void set(ArenaConfig next) {
        if (!next.equals(config)) {
            config = next;
            setDirty();
        }
    }
}
