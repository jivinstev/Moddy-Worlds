package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.shared.Level;
import com.moddingfromamod.levels.arena.shared.LevelPacks;
import com.moddingfromamod.levels.arena.shared.LevelWorldType;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** <b>Moddy Arena as a world jar</b>, loaded beside the core mod. Its hooks are listed in META-INF/services. */
@Mod(ArenaMod.MODID)
public final class ArenaMod {

    public static final String MODID = "moddyarena";

    /** The world as the shared code sees it. Name its third-party pack folders here (datapacks.lock.json). */
    public static final Level LEVEL = new Level(MODID, "arena", "Moddy Arena", List.of());

    public ArenaMod(IEventBus modBus) {
        LevelPacks.register(modBus, LEVEL);
        if (FMLEnvironment.dist.isClient()) {
            LevelWorldType.register(LEVEL);
        }
    }
}
