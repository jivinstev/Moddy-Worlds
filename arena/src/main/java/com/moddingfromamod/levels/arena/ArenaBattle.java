package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The arena's battles, carried out in the world</b>: reads the start button, feeds {@link ArenaMatch} once a tick
 * (how many of the wave's mobs are left, how many fighters are still in) and does what it answers: spawns a wave
 * ({@link ArenaSpawns} picks the spots), shows the boss bar, titles and lines ({@link ArenaWords}), and resets the
 * arena ({@link ArenaBuilder#reset}). Every decision is in those pure classes; this only looks at the world and acts.
 * Server thread only. Owned by {@link ArenaModule}.
 */
final class ArenaBattle {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on every mob a wave brings, so a battle counts only its own. */
    static final String TAG = ArenaMod.MODID + "_wave";

    /**
     * For a server run with nobody on (a test): {@code -Dmoddingfromamod.arena.allowEmptyBattle=true} lets a battle
     * start and go on with no player in the place, so it can be driven from the console.
     */
    static final String ALLOW_EMPTY_PROPERTY = "moddingfromamod.arena.allowEmptyBattle";

    private final MinecraftServer server;
    private final ArenaPlace place;
    private final ArenaState state;
    private final ArenaMatch match = new ArenaMatch();
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);
    private final boolean allowEmpty = Boolean.getBoolean(ALLOW_EMPTY_PROPERTY);
    /** The battle's fighters and their death count when it began; a fighter who is out is removed for good. */
    private final Map<UUID, Integer> fighters = new LinkedHashMap<>();
    /** Everyone who fought in this battle, out or not: they are told how it ends, wherever they respawned. */
    private final Set<UUID> told = new HashSet<>();
    private final List<UUID> waveMobs = new ArrayList<>();
    /** What fighters dropped when they were knocked out: moved to the prep room at the reset, never cleared. */
    private final Set<UUID> fightersDrops = new HashSet<>();
    /** Told when a battle is over and the arena is ready again (the wishes' counts and cooldowns start again). */
    private final Runnable onReady;
    private boolean buttonWasDown;
    private long ticks;

    ArenaBattle(MinecraftServer server, ArenaPlace place, ArenaState state, Runnable onReady) {
        this.server = server;
        this.place = place;
        this.state = state;
        this.onReady = onReady == null ? () -> { } : onReady;
        if (state.battleOn()) {
            match.endedByRestart();
            LOG.info("Moddy Arena: battle: a battle was on when the server stopped; it is over, and the arena will be"
                    + " reset in {} seconds", ArenaMatch.RESTART_WAIT_TICKS / ArenaMatch.TICKS_PER_SECOND);
        }
    }

    ArenaMatch match() {
        return match;
    }

    void stop() {
        bar.removeAllPlayers();
    }

    /** Why an empty server must keep ticking: a battle is on or being cleared away. */
    String keepTicking() {
        return match.busy() ? "a battle in the arena" : null;
    }

    // ── starting ────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Someone asked for a battle (the button, or {@code /moddy arena start}): null when it started, else the words why not. */
    String tryStart(String by) {
        if (match.busy()) {
            return ArenaWords.busy();
        }
        ArenaWaves waves = ArenaWavesRule.wavesOf(com.moddingfromamod.world.WorldRulesService.current());
        if (waves == null) {
            return ArenaWords.noWaves();
        }
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            return ArenaWords.peaceful();
        }
        if (playersInPlace().isEmpty() && !allowEmpty) {
            return ArenaWords.nobody();
        }
        match.start(waves);
        state.battleOn(true);
        fighters.clear();
        told.clear();
        waveMobs.clear();
        LOG.info("Moddy Arena: battle: COUNTDOWN, started by {}: {} waves, the first in {} s", by, waves.count(),
                waves.countdownSeconds());
        sayToPlace(ArenaWords.started(waves.count(), waves.countdownSeconds()));
        updateBar();
        return null;
    }

    /** {@code /moddy arena start}. */
    int startFromCommand(CommandSourceStack source) {
        String why = tryStart(source.getTextName());
        if (why != null) {
            source.sendFailure(Component.literal(why));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Battle started."), false);
        return 1;
    }

    // ── every tick ──────────────────────────────────────────────────────────────────────────────────────────────────

    void tick() {
        ticks++;
        ServerLevel level = level();
        boolean down = ArenaBuilder.buttonDown(level, place);
        if (down && !buttonWasDown) {
            pressed(level);
        }
        buttonWasDown = down;

        int fightersIn = switch (match.phase()) {
            case COUNTDOWN -> playersInPlace().size();
            case WAVE, BREATHER -> fightersStillIn();
            default -> 0;
        };
        if (allowEmpty && match.fighting()) {
            fightersIn = Math.max(1, fightersIn);
        }
        int mobsLeft = match.phase() == ArenaMatch.Phase.WAVE ? mobsLeft(level) : 0;
        ArenaMatch.Event ev = match.tick(mobsLeft, fightersIn);
        if (ev != null) {
            handle(level, ev);
        }
        if (ev != null || ticks % 10 == 0) {
            updateBar();
        }
        if (match.phase() == ArenaMatch.Phase.IDLE && ticks % 100 == 0) {
            int swept = discard(level, placeBox(level), e -> e.getTags().contains(TAG));
            if (swept > 0) {
                LOG.info("Moddy Arena: removed {} wave mobs left over from a battle that is over", swept);
            }
        }
    }

    private void pressed(ServerLevel level) {
        int[] b = place.startButton();
        ServerPlayer presser = null;
        double best = 36;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
            if (d < best) {
                best = d;
                presser = p;
            }
        }
        String why = tryStart(presser == null ? "the start button" : presser.getScoreboardName());
        if (why != null) {
            if (presser != null) {
                presser.sendSystemMessage(Component.literal(why));
            } else {
                sayToPlace(why);
            }
        }
    }

    private void handle(ServerLevel level, ArenaMatch.Event ev) {
        int of = match.table() == null ? 0 : match.table().count();
        switch (ev.kind()) {
            case COUNTDOWN -> {
                for (ServerPlayer p : audience()) {
                    p.displayClientMessage(Component.literal(ArenaWords.comingIn(ev.wave(), ev.seconds())), true);
                }
            }
            case WAVE_START -> {
                if (ev.wave() == 1) {
                    fighters.clear();
                    for (ServerPlayer p : playersInPlace()) {
                        fighters.put(p.getUUID(), deaths(p));
                        told.add(p.getUUID());
                    }
                    LOG.info("Moddy Arena: battle: the fighters are {}", names(fighters.keySet()));
                }
                int n = spawnWave(level, ev.wave(), match.table().wave(ev.wave()));
                match.spawned(n);
                LOG.info("Moddy Arena: battle: WAVE {} of {} begins: {} mobs", ev.wave(), of, n);
                titleToPlace(ArenaWords.waveTitle(ev.wave(), of), ArenaWords.waveSubtitle());
            }
            case WAVE_CLEARED -> {
                LOG.info("Moddy Arena: battle: wave {} cleared; BREATHER of {} s", ev.wave(), ev.seconds());
                sayToPlace(ArenaWords.cleared(ev.wave(), ev.seconds()));
            }
            case WON -> {
                LOG.info("Moddy Arena: battle: WON, all {} waves cleared", of);
                titleToPlace(ArenaWords.wonTitle(), ArenaWords.wonSubtitle(of));
                sayToPlace(ArenaWords.wonChat(of));
            }
            case LOST -> {
                int removed = discardWave(level);
                LOG.info("Moddy Arena: battle: LOST on wave {} ({}); {} of its mobs removed", ev.wave(), ev.why(),
                        removed);
                titleToPlace(ArenaWords.lostTitle(), ArenaWords.lostSubtitle(ev.why()));
                sayToPlace(ArenaWords.lostChat(ev.why(), ev.wave()));
            }
            case RESET -> {
                long t0 = System.nanoTime();
                int blocks = ArenaBuilder.reset(level, place);
                int entities = clearArena(level);
                fighters.clear();
                waveMobs.clear();
                LOG.info("Moddy Arena: battle: RESETTING: the arena is put back as built ({} blocks set, {} things"
                        + " removed) in {} ms", blocks, entities, (System.nanoTime() - t0) / 1_000_000);
            }
            case READY -> {
                state.battleOn(false);
                LOG.info("Moddy Arena: battle: IDLE, the arena is ready for the next battle");
                sayToPlace(ArenaWords.ready());
                told.clear();
                onReady.run();
            }
        }
    }

    // ── the fighters ────────────────────────────────────────────────────────────────────────────────────────────────

    /** The players in the place now (the arena or the prep room), alive and not spectating. */
    private List<ServerPlayer> playersInPlace() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level().players()) {
            if (!p.isSpectator() && p.isAlive() && place.keeps(p.getX(), p.getY(), p.getZ())) {
                out.add(p);
            }
        }
        return out;
    }

    /** How many fighters are still in; those who are out (died, logged out, left the place) are dropped for good. */
    private int fightersStillIn() {
        var it = fighters.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            String out = null;
            if (p == null) {
                out = "logged out";
            } else if (p.isDeadOrDying() || deaths(p) > e.getValue()) {
                out = "was knocked out";
            } else if (p.level() != level() || p.isSpectator() || !place.keeps(p.getX(), p.getY(), p.getZ())) {
                out = "left the place";
            }
            if (p != null && "was knocked out".equals(out)) {
                keepDropsOf(p);
            }
            if (out != null) {
                LOG.info("Moddy Arena: battle: {} {}; out of the battle", p == null ? e.getKey() : p.getScoreboardName(),
                        out);
                it.remove();
            }
        }
        return fighters.size();
    }

    /** The things a knocked-out fighter just dropped where he fell: kept for him, not cleared with the arena. */
    private void keepDropsOf(ServerPlayer p) {
        p.getLastDeathLocation().ifPresent(at -> {
            if (!at.dimension().equals(level().dimension())) {
                return;
            }
            BlockPos b = at.pos();
            AABB near = new AABB(b).inflate(4);
            for (ItemEntity item : level().getEntities(EntityTypeTest.forClass(ItemEntity.class), near,
                    i -> i.tickCount < 200)) {
                fightersDrops.add(item.getUUID());
            }
        });
    }

    private static int deaths(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
    }

    private String names(Set<UUID> ids) {
        List<String> out = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            out.add(p == null ? id.toString() : p.getScoreboardName());
        }
        return out.isEmpty() ? "nobody" : String.join(", ", out);
    }

    // ── the mobs ────────────────────────────────────────────────────────────────────────────────────────────────────

    private int spawnWave(ServerLevel level, int waveNumber, ArenaWaves.Wave wave) {
        waveMobs.clear();
        List<double[]> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            players.add(new double[] {p.getX(), p.getZ()});
        }
        List<int[]> spots = ArenaSpawns.pick(place, players, wave.size(), ArenaSpawns.seedFor(waveNumber, level.random::nextLong));
        int i = 0;
        for (ArenaWaves.Group g : wave.groups()) {
            EntityType<?> type = EntityType.byString(g.mob()).orElse(null);
            for (int k = 0; k < g.count(); k++) {
                int[] s = spots.get(i++);
                if (type == null) {
                    continue;
                }
                Entity e = spawnOne(level, type, new BlockPos(s[0], place.floorY(), s[1]));
                if (e != null) {
                    waveMobs.add(e.getUUID());
                }
            }
            if (type == null) {
                LOG.warn("Moddy Arena: battle: no mob called {} in this game; its {} are left out", g.mob(), g.count());
            }
        }
        return waveMobs.size();
    }

    private static <T extends Entity> T spawnOne(ServerLevel level, EntityType<T> type, BlockPos at) {
        Consumer<T> mark = e -> {
            e.addTag(TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();   // never despawned while the fighters stand far off
            }
        };
        return type.spawn(level, mark, at, EntitySpawnReason.EVENT, false, false);
    }

    /** How many of the wave's mobs are left; one that has left the place is removed and counts as cleared. */
    private int mobsLeft(ServerLevel level) {
        var it = waveMobs.iterator();
        while (it.hasNext()) {
            Entity e = level.getEntity(it.next());
            if (e == null || !e.isAlive()) {
                it.remove();
            } else if (!place.keeps(e.getX(), e.getY(), e.getZ())) {
                LOG.info("Moddy Arena: battle: a {} left the arena at {} {} {}; removed, and counted as cleared",
                        EntityType.getKey(e.getType()), (int) e.getX(), (int) e.getY(), (int) e.getZ());
                e.discard();
                it.remove();
            }
        }
        return waveMobs.size();
    }

    private int discardWave(ServerLevel level) {
        int n = 0;
        for (UUID id : waveMobs) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                e.discard();
                n++;
            }
        }
        waveMobs.clear();
        return n;
    }

    /**
     * After a battle: every thing in the arena that is not a player (its mobs, drops, experience, arrows, and anything
     * wished into it), except a pet someone owns and anything carrying a player; and any wave mob that reached the
     * prep room.
     */
    private int clearArena(ServerLevel level) {
        ArenaPlace.Box r = place.resetBox(level.getMaxY());
        AABB arena = new AABB(r.x0(), r.y0() - 1, r.z0(), r.x1() + 1, r.y1() + 1, r.z1() + 1);
        // a player's own things (what a fighter dropped when knocked out, what a player threw) go to the prep room
        int[] home = place.arrival();
        int moved = 0;
        for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), arena, this::playersOwn)) {
            item.teleportTo(home[0] + 0.5, home[1] + 0.2, home[2] + 0.5);
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            moved++;
        }
        if (moved > 0) {
            LOG.info("Moddy Arena: battle: {} of the players' own dropped things moved to the prep room", moved);
        }
        fightersDrops.clear();
        int n = discard(level, arena, e -> !(e instanceof Player) && !owned(e) && !playersOwn(e)
                && !e.hasPassenger(p -> p instanceof Player));
        return n + discard(level, placeBox(level), e -> e.getTags().contains(TAG));
    }

    /** An item a player threw, or one a fighter dropped when he was knocked out. */
    private boolean playersOwn(Entity e) {
        return e instanceof ItemEntity item && (fightersDrops.contains(item.getUUID()) || item.getOwner() instanceof Player);
    }

    private static boolean owned(Entity e) {
        return e instanceof OwnableEntity o && o.getOwnerReference() != null;
    }

    private static int discard(ServerLevel level, AABB box, java.util.function.Predicate<Entity> which) {
        List<Entity> found = level.getEntities(EntityTypeTest.forClass(Entity.class), box, which);
        Set<Entity> gone = new HashSet<>(found);
        for (Entity e : gone) {
            if (!e.isRemoved()) {
                e.discard();
            }
        }
        return gone.size();
    }

    private AABB placeBox(ServerLevel level) {
        ArenaPlace.Box f = place.footprint();
        return new AABB(f.x0(), f.y0() - 2, f.z0(), f.x1() + 1, level.getMaxY() + 1, f.z1() + 1);
    }

    // ── telling the players ─────────────────────────────────────────────────────────────────────────────────────────

    /** Who is told about the battle: everyone in the place (alive or not), and every fighter of it still online. */
    private Set<ServerPlayer> audience() {
        Set<ServerPlayer> out = new java.util.LinkedHashSet<>();
        for (ServerPlayer p : level().players()) {
            if (place.keeps(p.getX(), p.getY(), p.getZ())) {
                out.add(p);
            }
        }
        for (UUID id : told) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private void updateBar() {
        ArenaMatch.Bar b = match.bar();
        if (b == null) {
            bar.removeAllPlayers();
            return;
        }
        bar.setName(Component.literal(b.name()));
        bar.setProgress(Math.max(0f, Math.min(1f, b.progress())));
        Set<ServerPlayer> want = audience();
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (!want.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (ServerPlayer p : want) {
            bar.addPlayer(p);
        }
    }

    private void sayToPlace(String words) {
        for (ServerPlayer p : audience()) {
            p.sendSystemMessage(Component.literal(words));
        }
    }

    private void titleToPlace(String title, String subtitle) {
        for (ServerPlayer p : audience()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        }
    }

    private ServerLevel level() {
        return server.overworld();
    }
}
