package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.shared.LevelClaim;
import com.moddingfromamod.plugin.ModdyPlugin;
import com.moddingfromamod.plugin.Plugins;
import com.moddingfromamod.wish.ChildBranch;
import com.moddingfromamod.world.WorldModule;
import com.moddingfromamod.world.WorldModuleType;
import com.moddingfromamod.world.WorldRuleType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * Moddy Arena, plugged into the core: its world module, which runs in the worlds made as Moddy Arena (and supplies
 * the arena's wish charge, {@link ArenaCharge}); its three rule types, {@code arena_place} ({@link ArenaRule}), which
 * sizes the place, {@code arena_waves} ({@link ArenaWavesRule}), the battle's waves, and {@code arena_wishes}
 * ({@link ArenaWishesRule}), the Magician wishes a prep and a hotbar wish's cooldown in a battle; and
 * {@code /moddy arena start} (a branch of {@code /moddy}, never a command of its own), which starts a battle as the
 * prep room's button does.
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
        plugins.add(WorldRuleType.class, new ArenaWavesRule(ArenaPlugin::livingEntity));
        plugins.add(WorldRuleType.class, new ArenaWishesRule());
        plugins.add(ChildBranch.class, () -> Commands.literal("arena")
                .then(Commands.literal("start").executes(ctx -> ArenaModule.startCommand(ctx.getSource()))));
    }

    /** True when the id names a living entity this game knows (one with attributes: every living entity has them). */
    static boolean livingEntity(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            return false;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(DefaultAttributes::hasSupplier).orElse(false);
    }
}
