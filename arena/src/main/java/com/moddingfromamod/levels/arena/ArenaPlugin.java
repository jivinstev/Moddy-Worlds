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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * Moddy Arena, plugged into the core: its world module, which runs in the worlds made as Moddy Arena (and supplies
 * the arena's wish charge, {@link ArenaCharge}, and what may be made there, {@link ArenaMakes}); its five rule types, {@code arena_place} ({@link ArenaRule}), which
 * sizes the place, {@code arena_waves} ({@link ArenaWavesRule}), the battle's waves, and {@code arena_wishes}
 * ({@link ArenaWishesRule}), the Magician wishes a prep and a hotbar wish's cooldown in a battle, and
 * {@code arena_creatures} ({@link ArenaCreaturesRule}), the numbers of the wished creatures' battle roles, and
 * {@code arena_ladder} ({@link ArenaLadderRule}), the unlock ladder: limits that rise as bosses fall; and
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
        plugins.add(WorldRuleType.class, new ArenaCreaturesRule());
        plugins.add(WorldRuleType.class, new ArenaLadderRule());
        plugins.add(WorldRuleType.class, new ArenaWishedWavesRule());   // Moddy-Worlds#55
        plugins.add(ChildBranch.class, ArenaPlugin::arenaBranch);
    }

    /**
     * {@code /moddy arena}: {@code start}, and {@code config [<scope> [<key> [<value>|reset]]]} (Moddy-Worlds#59), where
     * a scope is {@code global} (every arena) or an arena's name. Anyone may read the settings; the command itself
     * refuses a change from anyone but an operator.
     */
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> arenaBranch() {
        var word = com.mojang.brigadier.arguments.StringArgumentType.word();
        return Commands.literal("arena")
                .then(Commands.literal("start").executes(ctx -> ArenaModule.startCommand(ctx.getSource())))
                .then(Commands.literal("config")
                        .executes(ctx -> ArenaModule.configCommand(ctx.getSource(), null, java.util.List.of()))
                        .then(Commands.argument("scope", word)
                                .executes(ctx -> ArenaModule.configCommand(ctx.getSource(), arg(ctx, "scope"),
                                        java.util.List.of()))
                                .then(Commands.argument("key", word)
                                        .executes(ctx -> ArenaModule.configCommand(ctx.getSource(), arg(ctx, "scope"),
                                                java.util.List.of(arg(ctx, "key"))))
                                        .then(Commands.argument("value", word)
                                                .executes(ctx -> ArenaModule.configCommand(ctx.getSource(),
                                                        arg(ctx, "scope"),
                                                        java.util.List.of(arg(ctx, "key"), arg(ctx, "value"))))))));
    }

    private static String arg(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                              String name) {
        return com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, name);
    }

    /** True when the id names a living entity this game knows (one with attributes: every living entity has them). */
    static boolean livingEntity(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return false;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(DefaultAttributes::hasSupplier).orElse(false);
    }
}
