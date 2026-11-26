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
        // a player's dropped things are his: kept by the arena's tidying (in an arena world only; ArenaModule)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArenaModule::onDrops);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArenaModule::onToss);
        // a wished creature is on the players' side in the arena: it never hurts or targets one there
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArenaModule::onIncomingDamage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArenaModule::onChangeTarget);
        // arenas built with /moddy arena create, in any world (Moddy-Worlds#58): opened, ticked and closed here
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStartedEvent e) -> ArenaSites.started(e.getServer()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> ArenaSites.tick());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> ArenaSites.stopped());
        if (FMLEnvironment.dist.isClient()) {
            LevelWorldType.register(LEVEL);
        }
    }
}
