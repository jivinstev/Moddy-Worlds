package com.moddingfromamod.levels.arena;

import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.WorldModule;
import com.moddingfromamod.world.WorldModuleHost;
import com.moddingfromamod.world.WorldRules;
import com.moddingfromamod.world.WorldRulesService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Moddy Arena's world module</b>: on start it builds the place once (the prep room and the walled arena,
 * {@link ArenaPlace}, sized by the world's {@code arena_place} rule), sets the world spawn in the prep room facing the
 * arena, and returns {@link ArenaBases} so the core's {@code base_reach} rule keeps every wish inside the arena. A
 * player's first join puts him in the prep room, facing the arena; the words he reads then are the manifest's
 * {@code intro}, which the core says once per player per world. Named by
 * {@code worlds/arena.json}'s {@code "module": "arena"}.
 */
public final class ArenaModule implements WorldModule {

    public static final String ID = "arena";

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    private MinecraftServer server;
    private ArenaBases bases;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Bases start(MinecraftServer server) {
        this.server = server;
        ServerLevel level = server.overworld();
        ArenaState state = ArenaState.of(server);
        ArenaPlace.Start s = ArenaPlace.onStart(state.built(), ArenaRule.placeOf(rulesAtStart()));
        ArenaPlace place = s.place();
        if (s.build()) {
            long t0 = System.nanoTime();
            int set = ArenaBuilder.build(level, place);
            state.markBuilt(place);
            LOG.info("Moddy Arena: built the place ({}): {} blocks in {} ms", place.describe(), set,
                    (System.nanoTime() - t0) / 1_000_000);
        } else {
            LOG.info("Moddy Arena: the place stands ({}); nothing to build", place.describe());
        }
        if (!s.note().isEmpty()) {
            LOG.warn("Moddy Arena: {}", s.note());
        }
        int[] a = place.arrival();
        BlockPos spawn = new BlockPos(a[0], a[1], a[2]);
        if (!level.getSharedSpawnPos().equals(spawn) || level.getSharedSpawnAngle() != ArenaPlace.ARRIVAL_YAW) {
            level.setDefaultSpawnPos(spawn, ArenaPlace.ARRIVAL_YAW);
            LOG.info("Moddy Arena: world spawn set to the prep room, {} {} {}", a[0], a[1], a[2]);
        }
        bases = new ArenaBases(place, id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            return p == null ? null : new Bases.Pos(p.level().dimension().location().toString(), p.getX(),
                    p.getY(), p.getZ());
        });
        return bases;
    }

    @Override
    public void stop() {
        bases = null;
        server = null;
    }

    /** A first join: into the prep room, facing the arena (the core then says the manifest's intro). A returning player stays put. */
    @Override
    public void onLogin(ServerPlayer player) {
        MinecraftServer s = server;
        ArenaBases b = bases;
        if (s == null || b == null || !ArenaState.of(s).arrive(player.getUUID())) {
            return;
        }
        int[] a = b.place().arrival();
        ServerLevel level = s.overworld();
        player.teleportTo(level, a[0] + 0.5, a[1], a[2] + 0.5, Set.of(), ArenaPlace.ARRIVAL_YAW, 0f, true);
        player.fallDistance = 0;
        LOG.info("Moddy Arena: {} arrived in the prep room", player.getScoreboardName());
    }

    /**
     * Until (or unless) a rules file is read, the arena's own rules from this jar (worlds/rules/arena.rules.json): its
     * place, the reach rule that keeps wishes in the arena, and its clamp. A server that names a rules file replaces
     * them as soon as it is read.
     */
    @Override
    public WorldRules fallbackRules(Bases bases) {
        WorldRules bundled = parse(bundledRules());
        return bundled != null ? bundled : WorldModule.super.fallbackRules(bases);
    }

    /**
     * The rules this world will run with, read now: the core starts its rules only after the module has started, so
     * this reads the same source it will (the rules file it watches when it parses, else this jar's own rules).
     */
    private static WorldRules rulesAtStart() {
        Path file = WorldRulesService.defaultFile();
        if (Files.isRegularFile(file)) {
            try {
                WorldRules r = parse(Files.readString(file, StandardCharsets.UTF_8));
                if (r != null) {
                    return r;
                }
                LOG.warn("Moddy Arena: the rules file {} cannot be read; the arena's own rules size the place", file);
            } catch (IOException e) {
                LOG.warn("Moddy Arena: the rules file {} cannot be read ({}); the arena's own rules size the place",
                        file, e.toString());
            }
        }
        return parse(bundledRules());
    }

    private static WorldRules parse(String text) {
        if (text == null) {
            return null;
        }
        String world = WorldModuleHost.worldId();
        WorldRules.Parsed p = WorldRules.parse(text, world.isEmpty() ? ID : world);
        if (!p.ok()) {
            LOG.warn("Moddy Arena: rules refused: {}", p.errors());
            return null;
        }
        return p.rules();
    }

    /** The arena's own rules file text (worlds/rules/arena.rules.json, in this jar), or null. */
    static String bundledRules() {
        try (InputStream in = ArenaModule.class.getResourceAsStream("/worlds/rules/" + ID + ".rules.json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
