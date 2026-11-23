package com.moddingfromamod.levels.shared;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * <b>A level's built-in packs, off by default.</b> Its data packs ship inside its jar ({@code datapacks/<folder>/}),
 * with its marker pack ({@link Level#MARKER}), and
 * are offered on {@link AddPackFindersEvent} as {@code mod/<modid>:datapacks/<folder>}, with a {@link PackSource} that
 * never adds them to a world by itself: a plain world in the same game never gets them. A world gets them when it is
 * created with the level's World Type ({@link LevelWorldType}) or a server names them in {@code initial-enabled-packs}.
 *
 * <p>SHARED SOURCE (see {@link Level}).
 */
public final class LevelPacks {

    private LevelPacks() {
    }

    /** Call from the level's {@code @Mod} constructor with its mod bus. */
    public static void register(IEventBus modBus, Level level) {
        PackSource source = PackSource.create(
                name -> Component.translatable("pack.nameAndSource", name, Component.literal(level.typeName())), false);
        modBus.addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() != PackType.SERVER_DATA) {
                return;
            }
            for (String folder : level.allPacks()) {
                event.addPackFinders(ResourceLocation.fromNamespaceAndPath(level.modid(), "datapacks/" + folder),
                        PackType.SERVER_DATA, Component.literal(level.typeName() + ": " + folder.replace('_', ' ')),
                        source, false, Pack.Position.TOP);
            }
        });
    }
}
