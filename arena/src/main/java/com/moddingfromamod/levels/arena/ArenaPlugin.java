package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.shared.LevelClaim;
import com.moddingfromamod.plugin.ModdyPlugin;
import com.moddingfromamod.plugin.Plugins;
import com.moddingfromamod.world.WorldModule;
import com.moddingfromamod.world.WorldModuleType;
import com.moddingfromamod.world.WorldRuleType;
import net.minecraft.server.MinecraftServer;

/**
 * Moddy Arena, plugged into the core: its world module, which runs in the worlds made as Moddy Arena, and its rule
 * type, {@code arena_place} ({@link ArenaRule}), which sizes the place in a world's rules file.
 */
public final class ArenaPlugin implements ModdyPlugin {

    @Override
    public String name() {
        return "arena";
    }

    @Override
    public void register(Plugins plugins) {
        plugins.add(WorldModuleType.class, new WorldModuleType() {
            @Override
            public String id() {
                return ArenaModule.ID;
            }

            @Override
            public WorldModule create() {
                return new ArenaModule();
            }

            /** A world made with World Type "Moddy Arena" (its marker pack on, or its saved data says so). */
            @Override
            public boolean claims(MinecraftServer server) {
                return LevelClaim.claims(server, ArenaMod.LEVEL);
            }
        });
        plugins.add(WorldRuleType.class, new ArenaRule());
    }
}
