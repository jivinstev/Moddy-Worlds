package com.moddingfromamod.isle;

import com.moddingfromamod.plugin.ModdyPlugin;
import com.moddingfromamod.plugin.Plugins;
import com.moddingfromamod.wish.ChildBranch;
import com.moddingfromamod.wish.ChildCommands;
import com.moddingfromamod.world.WorldModule;
import com.moddingfromamod.world.WorldModuleType;
import net.minecraft.commands.Commands;

/**
 * <b>Scarcity Isle, plugged into the core</b> (#2358, epic #2307). The core names nothing in {@code isle/}: this
 * plug-in, listed in {@code META-INF/services/com.moddingfromamod.plugin.ModdyPlugin}, hands it
 * <ul>
 *   <li>the {@code isle} {@link WorldModuleType} ({@link IsleModule}), which runs where the manifest names it or,
 *       with no module named, where islands are switched on ({@code islands.spacing});</li>
 *   <li>(its module also says "Want to start over?" to a player with an island, {@link IsleModule#helpLines},
 *       #2179);</li>
 *   <li>and {@code /moddy island new [confirm]} as a {@link ChildBranch} (#2179).</li>
 * </ul>
 */
public final class IslePlugin implements ModdyPlugin {

    @Override
    public String name() {
        return "isle";
    }

    @Override
    public void register(Plugins plugins) {
        plugins.add(WorldModuleType.class, new WorldModuleType() {
            @Override
            public String id() {
                return IsleModule.ID;
            }

            @Override
            public WorldModule create() {
                return new IsleModule();
            }

            @Override
            public boolean impliedWhenUnnamed() {
                return IslandsConfig.spacing() > 0;
            }

            /** #2750: a world made with World Type "Moddy Isle" (its packs on, or its saved data says so). */
            @Override
            public boolean claims(net.minecraft.server.MinecraftServer server) {
                return IsleWorld.claims(server);
            }
        });
        plugins.add(ChildBranch.class, IslePlugin::islandBranch);
    }

    /** START OVER (#2179): a fresh island. Bare asks first (a dialog); its button runs `confirm`. */
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> islandBranch() {
        return Commands.literal(ChildCommands.ISLAND)
                .then(Commands.literal(IslandFresh.NEW)
                        .executes(ctx -> IslandFresh.ask(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal(IslandFresh.CONFIRM)
                                .executes(ctx -> IslandFresh.confirm(ctx.getSource().getPlayerOrException()))));
    }
}
