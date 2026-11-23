package com.moddingfromamod.levels.arena;

import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.WorldModule;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Moddy Arena's world module: its state, its players' arrivals, and (later) its bases and its wish charge. */
public final class ArenaModule implements WorldModule {

    public static final String ID = "arena";

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Bases start(MinecraftServer server) {
        LOG.info("Moddy Arena: world module started");
        return null;                 // no bases yet
    }
}
