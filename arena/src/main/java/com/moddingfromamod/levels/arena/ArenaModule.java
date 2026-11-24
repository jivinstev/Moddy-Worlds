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
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Moddy Arena's world module</b>: on start it builds the place once (the prep room and the walled arena,
 * {@link ArenaPlace}, sized by the world's {@code arena_place} rule), sets the world spawn in the prep room facing the
 * arena, and returns {@link ArenaBases} so the core's {@code base_reach} rule keeps every wish inside the arena. A
 * player's first join puts him in the prep room, facing the arena; the words he reads then are the manifest's
 * {@code intro}, which the core says once per player per world. Its battles are {@link ArenaBattle}, ticked here, and
 * its wishes {@link ArenaCharge} (prep and battle, {@link ArenaWishPolicy}), handed to the core as this world's charge. Named
 * by {@code worlds/arena.json}'s {@code "module": "arena"}.
 */
public final class ArenaModule implements WorldModule {

    public static final String ID = "arena";

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The running arena module, for {@code /moddy arena start}; null in any other world. */
    private static volatile ArenaModule live;

    private MinecraftServer server;
    private ArenaBases bases;
    private ArenaBattle battle;
    /** Read by the core from any thread (a Magician pickup comes on an agent thread). */
    private volatile ArenaCharge charge;

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
            state.markUpgraded(ArenaPlace.UPGRADE_START_CONTROL);   // fills() carries the panel, which replaced it
            state.markUpgraded(ArenaPlace.UPGRADE_CONTROL_PANEL);
            LOG.info("Moddy Arena: built the place ({}): {} blocks in {} ms", place.describe(), set,
                    (System.nanoTime() - t0) / 1_000_000);
        } else {
            LOG.info("Moddy Arena: the place stands ({}); nothing to build", place.describe());
        }
        upgrade(server, level, state, place);
        if (!s.note().isEmpty()) {
            LOG.warn("Moddy Arena: {}", s.note());
        }
        if (state.readVersion() > ArenaState.VERSION) {
            LOG.warn("Moddy Arena: this world's arena record was saved by a newer Moddy Arena (version {}, this one"
                    + " reads {}); what this one knows of it is used", state.readVersion(), ArenaState.VERSION);
        }
        if (state.unknownDifficulty() != null) {
            LOG.warn("Moddy Arena: control panel: the saved difficulty '{}' is not one this arena knows; it is {}",
                    state.unknownDifficulty(), ArenaDifficulty.DEFAULT.word());
        }
        LOG.info("Moddy Arena: control panel: wave size {}, difficulty {}{}", state.settings().size(),
                state.settings().difficulty().word(), state.readVersion() < ArenaState.VERSION
                        ? " (this world was saved before the panel: the defaults)" : "");
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
        ArenaCharge c = ArenaCharge.of(server);
        battle = new ArenaBattle(server, place, state, c::battleEnded);
        c.phase(ArenaWishPolicy.phaseOf(battle.match().phase()));
        charge = c;
        ArenaWishes w = ArenaWishesRule.wishesOf(rulesAtStart());
        LOG.info("Moddy Arena: wishes: the arena's wish charge is in force (in prep, {} Magician wishes a player;"
                + " in a battle, hotbar wishes only, each resting {} s)", w.magicianWishes(), w.cooldownSeconds());
        live = this;
        return bases;
    }

    /**
     * What a place built earlier still needs, each once and recorded ({@link ArenaPlace#pendingUpgrades}): its battle
     * arena control panel (which replaced the first start control, so a place that never had that gets the panel
     * instead), placed only where the blocks are still as built; and natural mob spawning switched off in this world,
     * since nothing should come but a battle's waves (the arena's floor is dark at night). Then, on every start, the
     * panel's blocks are put back where one is missing (a button knocked off, say), by the same path, which sets nothing
     * when the panel stands whole.
     */
    private static void upgrade(MinecraftServer server, ServerLevel level, ArenaState state, ArenaPlace place) {
        for (String u : ArenaPlace.pendingUpgrades(state.upgrades())) {
            switch (u) {
                case ArenaPlace.UPGRADE_START_CONTROL -> {
                    // the first start control is part of the panel now: the panel's own upgrade places it
                }
                case ArenaPlace.UPGRADE_CONTROL_PANEL -> {
                    int set = ArenaBuilder.addControl(level, place);
                    if (set < 0) {
                        LOG.warn("Moddy Arena: the place has no control panel, and something stands where it goes"
                                + " (the wall between the prep room and the arena, or the prep room's first row);"
                                + " it is not added. /moddy arena start still starts a battle.");
                        continue;
                    }
                    LOG.info("Moddy Arena: the place was missing its battle arena control panel; added it on the wall"
                            + " beside the gate ({} blocks)", set);
                }
                case ArenaPlace.UPGRADE_NO_NATURAL_SPAWNS -> {
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    LOG.info("Moddy Arena: natural mob spawning switched off in this world (game rule doMobSpawning"
                            + " false), once: only a battle's waves come to the arena");
                }
                default -> {
                    continue;
                }
            }
            state.markUpgraded(u);
        }
        if (state.upgrades().contains(ArenaPlace.UPGRADE_CONTROL_PANEL)) {
            int set = ArenaBuilder.addControl(level, place);
            if (set > 0) {
                LOG.info("Moddy Arena: the control panel was missing {} of its blocks; put back", set);
            }
        }
    }

    @Override
    public void stop() {
        ArenaBattle b = battle;
        if (b != null) {
            b.stop();
        }
        if (live == this) {
            live = null;
        }
        battle = null;
        charge = null;
        bases = null;
        server = null;
    }

    @Override
    public void onTick() {
        ArenaBattle b = battle;
        if (b != null) {
            b.tick();
            ArenaCharge c = charge;
            if (c != null) {
                c.phase(ArenaWishPolicy.phaseOf(b.match().phase()));
            }
        }
    }

    /**
     * The arena's own wish charge ({@link ArenaCharge}): in an arena world, the wishes of the prep phase and of a
     * battle follow {@link ArenaWishPolicy}. Null before the module starts and after it stops (the core then uses its
     * default, which charges nothing).
     */
    @Override
    public com.moddingfromamod.wish.WishCharge charge() {
        return charge;
    }

    @Override
    public String keepTicking() {
        ArenaBattle b = battle;
        return b == null ? null : b.keepTicking();
    }

    /** {@code /moddy arena start}: starts a battle in an arena world; says why not anywhere else. */
    static int startCommand(net.minecraft.commands.CommandSourceStack source) {
        ArenaModule m = live;
        ArenaBattle b = m == null ? null : m.battle;
        if (b == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(ArenaWords.notAnArena()));
            return 0;
        }
        return b.startFromCommand(source);
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
