package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    /**
     * The battle's fighters: each one's death count at his last knock-out (or when the battle began) and his lives
     * left. A fighter who is out (no lives left, logged out, gone from the place) is removed for good.
     */
    private final Map<UUID, int[]> fighters = new LinkedHashMap<>();
    /** How far each fighter got, when his battle ended (recorded on the Champions board when the battle ends). */
    private final Map<UUID, ArenaChampions.Reach> reached = new LinkedHashMap<>();
    private final Map<UUID, String> fighterNames = new HashMap<>();
    /** Fighters knocked out with lives left who have not been told their lives on their feet yet. */
    private final Set<UUID> tellLives = new HashSet<>();
    /** The boss of the wave being fought, while it stands, and its name. */
    private UUID boss;
    private String bossName;
    /** Everyone who fought in this battle, out or not: they are told how it ends, wherever they respawned. */
    private final Set<UUID> told = new HashSet<>();
    private final List<UUID> waveMobs = new ArrayList<>();
    /** What fighters dropped when they were knocked out: moved to the prep room at the reset, never cleared. */
    private final Set<UUID> fightersDrops = new HashSet<>();
    /**
     * The arena's wish charge: told when a wave is cleared (the hotbar wishes start again) and when a battle is over
     * (every count and rest starts again), and asked how many hotbar wishes each fighter has left; null in a test.
     */
    private final ArenaCharge wishes;
    /** Each player's own bar in a battle: his hotbar wishes left this wave. */
    private final Map<UUID, ServerBossEvent> wishBars = new HashMap<>();
    /** The battle arena control panel: its buttons and its words. */
    private final ArenaBoard board;
    /** The Champions board on the prep room's back wall. */
    private final ArenaChampionsBoard champions;
    /** The settings the Champions board shows (those on the panel), to see when they change. */
    private ArenaChampions.Key shownKey;
    /** The battle's settings, as its runs are recorded. */
    private ArenaChampions.Key battleKey;
    /** The difficulty of the battle that is on (its mobs' health), fixed when it starts. */
    private ArenaDifficulty battleDifficulty = ArenaDifficulty.DEFAULT;
    private long ticks;

    ArenaBattle(MinecraftServer server, ArenaPlace place, ArenaState state, ArenaCharge wishes) {
        this.server = server;
        this.place = place;
        this.state = state;
        this.wishes = wishes;
        this.board = new ArenaBoard(server, place, state);
        this.champions = new ArenaChampionsBoard(server, place, state);
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
        for (ServerBossEvent b : wishBars.values()) {
            b.removeAllPlayers();
        }
        wishBars.clear();
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
        ArenaWaves base = baseWaves();
        if (base == null) {
            return ArenaWords.noWaves();
        }
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            return ArenaWords.peaceful();
        }
        if (playersInPlace().isEmpty() && !allowEmpty) {
            return ArenaWords.nobody();
        }
        ArenaSettings settings = state.settings().within(base.limits());
        ArenaRun waves = settings.run(base);
        battleDifficulty = settings.difficulty();
        battleKey = ArenaChampions.Key.of(settings);
        match.start(waves);
        state.battleOn(true);
        fighters.clear();
        reached.clear();
        fighterNames.clear();
        tellLives.clear();
        boss = null;
        told.clear();
        waveMobs.clear();
        LOG.info("Moddy Arena: battle: COUNTDOWN, started by {}: {} waves, the first in {} s (monsters {}, difficulty"
                + " {}; the first wave {} mobs; boss waves {}; {} lives)", by, ArenaWords.wavesValue(waves.count()),
                waves.countdownSeconds(), settings.size(), settings.difficulty().word(), waves.size(1),
                waves.bossWaves(20), waves.lives());
        sayToPlace(ArenaWords.started(waves.count(), waves.countdownSeconds(), waves.lives()));
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
        for (ArenaPanel.Control c : board.pressed(level)) {
            if (c == ArenaPanel.Control.START) {
                pressed(level);
            } else {
                board.select(level, c, match.busy(), baseWaves());
            }
        }

        int fightersIn = switch (match.phase()) {
            case COUNTDOWN -> playersInPlace().size();
            case WAVE, BREATHER -> fightersStillIn();
            default -> 0;
        };
        if (allowEmpty && match.fighting()) {
            fightersIn = Math.max(1, fightersIn);
        }
        int mobsLeft = match.phase() == ArenaMatch.Phase.WAVE ? mobsLeft(level) : 0;
        watchBoss(level);
        ArenaMatch.Event ev = match.tick(mobsLeft, fightersIn);
        if (ev != null) {
            handle(level, ev);
        }
        if (ev != null || ticks % 10 == 0) {
            updateBar();
        }
        if (ev != null || ticks % 5 == 0) {
            updateWishBars();
        }
        board.refresh(level, match, ArenaBattle::baseWaves, ev != null);
        ArenaWaves rules = baseWaves();
        ArenaChampions.Key key = ArenaChampions.Key.of(state.settings().within(rules == null ? null : rules.limits()));
        if (!key.equals(shownKey)) {
            shownKey = key;
            champions.changed();
        }
        champions.refresh(level, state.settings().within(rules == null ? null : rules.limits()));
        livesOnTheirFeet();
        if (match.phase() == ArenaMatch.Phase.IDLE && ticks % 100 == 0) {
            int swept = discard(level, placeBox(level), e -> e.getTags().contains(TAG));
            if (swept > 0) {
                LOG.info("Moddy Arena: removed {} wave mobs left over from a battle that is over", swept);
            }
        }
    }

    private void pressed(ServerLevel level) {
        board.noteBy(board.presser(level, ArenaPanel.Control.START));
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
        int of = match.of();
        switch (ev.kind()) {
            case COUNTDOWN -> {
                for (ServerPlayer p : audience()) {
                    p.displayClientMessage(Component.literal(ArenaWords.comingIn(ev.wave(), ev.seconds())), true);
                }
            }
            case WAVE_START -> {
                if (ev.wave() == 1) {
                    fighters.clear();
                    int lives = match.table().lives();
                    for (ServerPlayer p : playersInPlace()) {
                        fighters.put(p.getUUID(), new int[] {deaths(p), lives});
                        fighterNames.put(p.getUUID(), p.getScoreboardName());
                        told.add(p.getUUID());
                    }
                    LOG.info("Moddy Arena: battle: the fighters are {}, {} lives each", names(fighters.keySet()),
                            lives);
                }
                ArenaRun run = match.table();
                ArenaBosses.Boss b = run.boss(ev.wave());
                int n = spawnWave(level, run.wave(ev.wave()), run.health(ev.wave()));
                if (b != null && spawnBoss(level, b, run.bossHealth())) {
                    n++;
                }
                match.spawned(n);
                LOG.info("Moddy Arena: battle: WAVE {} of {} begins: {} mobs{}", ev.wave(),
                        ArenaWords.wavesValue(of), n, b == null ? "" : " (a boss wave: " + b.name() + ", a "
                                + b.mob() + ")");
                if (b != null) {
                    titleToPlace(ArenaWords.bossTitle(), ArenaWords.bossSubtitle(b.name()));
                    sayToPlace(ArenaWords.bossChat(b.name(), ev.wave()));
                    for (ServerPlayer p : audience()) {
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(),
                                SoundSource.HOSTILE, 1.5f, 1.0f);
                    }
                } else {
                    titleToPlace(ArenaWords.waveTitle(ev.wave(), of), ArenaWords.waveSubtitle());
                }
            }
            case WAVE_CLEARED -> {
                LOG.info("Moddy Arena: battle: wave {} cleared; BREATHER of {} s; the hotbar wishes start again",
                        ev.wave(), ev.seconds());
                sayToPlace(ArenaWords.cleared(ev.wave(), ev.seconds()));
                if (wishes != null) {
                    wishes.newWave();
                    sayToPlace(ArenaWords.wishesBack(wishes.wishes().wishesPerWave()));
                }
            }
            case WON -> {
                LOG.info("Moddy Arena: battle: WON, all {} waves cleared", of);
                titleToPlace(ArenaWords.wonTitle(), ArenaWords.wonSubtitle(of));
                sayToPlace(ArenaWords.wonChat(of));
                recordRuns();
            }
            case LOST -> {
                recordRuns();
                int removed = discardWave(level);
                LOG.info("Moddy Arena: battle: LOST on wave {} ({}); {} of its mobs removed", ev.wave(), ev.why(),
                        removed);
                titleToPlace(ArenaWords.lostTitle(), ArenaWords.lostSubtitle(ev.why()));
                sayToPlace(ArenaWords.lostChat(ev.why(), ev.wave()));
            }
            case RESET -> {
                long t0 = System.nanoTime();
                int blocks = ArenaBuilder.reset(level, place);
                int panelBlocks = ArenaBuilder.addControl(level, place);   // a button knocked off its board, put back
                if (panelBlocks > 0) {
                    blocks += panelBlocks;
                }
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
                if (wishes != null) {
                    wishes.battleEnded();
                }
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

    /**
     * How many fighters are still in. A fighter knocked out with lives left loses one and stays in (he comes back in
     * the prep room and may walk back in); one knocked out of his last life, logged out or gone from the place is out
     * for good, and how far he got is kept for the Champions board.
     */
    private int fightersStillIn() {
        var it = fighters.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            int[] f = e.getValue();
            String out = null;
            if (p == null) {
                out = "logged out";
            } else if (deaths(p) > f[0]) {
                f[0] = deaths(p);
                f[1]--;
                keepDropsOf(p);
                if (f[1] > 0) {
                    LOG.info("Moddy Arena: battle: {} was knocked out; {} lives left", p.getScoreboardName(), f[1]);
                    p.sendSystemMessage(Component.literal(ArenaWords.knockedOut(f[1])));
                    tellLives.add(p.getUUID());
                } else {
                    out = "was knocked out of his last life";
                    p.sendSystemMessage(Component.literal(ArenaWords.outOfLives()));
                }
            } else if (p.isDeadOrDying()) {
                continue;   // on the way to his next life
            } else if (p.level() != level() || p.isSpectator() || !place.keeps(p.getX(), p.getY(), p.getZ())) {
                out = "left the place";
            }
            if (out != null) {
                LOG.info("Moddy Arena: battle: {} {}; out of the battle", p == null ? e.getKey() : p.getScoreboardName(),
                        out);
                reached.put(e.getKey(), match.reach());
                tellLives.remove(e.getKey());
                it.remove();
            }
        }
        return fighters.size();
    }

    /** A fighter back on his feet after a knock-out is told his lives on the action bar, once. */
    private void livesOnTheirFeet() {
        var it = tellLives.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            int[] f = fighters.get(id);
            if (p == null || f == null) {
                it.remove();
            } else if (p.isAlive() && !p.isDeadOrDying()) {
                p.displayClientMessage(Component.literal(ArenaWords.livesLeft(f[1])), true);
                it.remove();
            }
        }
    }

    /**
     * The battle is over (won or lost): every fighter's run goes on the Champions board, at the battle's settings:
     * those still in get how far the battle got, those out before it how far they got then.
     */
    private void recordRuns() {
        for (UUID id : fighters.keySet()) {
            reached.put(id, match.reach());
        }
        if (battleKey == null) {
            return;
        }
        for (Map.Entry<UUID, ArenaChampions.Reach> e : reached.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            String name = p != null ? p.getScoreboardName() : fighterNames.getOrDefault(e.getKey(), "?");
            ArenaChampions.Reach r = e.getValue();
            int rank = state.record(e.getKey().toString(), name, battleKey, r);
            if (rank == 0) {
                continue;
            }
            LOG.info("Moddy Arena: Champions: {} reached wave {} ({}) in {} at {}: number {}", name, r.wave(),
                    r.won() ? "won" : r.cleared() ? "cleared" : "not cleared", ArenaChampions.time(r.ticks()),
                    ArenaChampions.settingsWords(battleKey), rank);
            if (p != null) {
                ArenaChampions.Entry run = new ArenaChampions.Entry("", name, battleKey, r.wave(), r.cleared(),
                        r.won(), r.ticks(), 0);
                p.sendSystemMessage(Component.literal(ArenaWords.recorded(ArenaChampions.result(run),
                        ArenaChampions.time(r.ticks()), rank <= ArenaChampions.SHOWN ? rank : 0)));
            }
        }
        reached.clear();
        champions.changed();
        board.changed();
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

    private int spawnWave(ServerLevel level, ArenaWaves.Wave wave, double health) {
        waveMobs.clear();
        List<double[]> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            players.add(new double[] {p.getX(), p.getZ()});
        }
        List<int[]> spots = ArenaSpawns.pick(place, players, wave.size(), level.random.nextLong());
        int i = 0;
        for (ArenaWaves.Group g : wave.groups()) {
            EntityType<?> type = EntityType.byString(g.mob()).orElse(null);
            for (int k = 0; k < g.count(); k++) {
                int[] s = spots.get(i++);
                if (type == null) {
                    continue;
                }
                Entity e = spawnOne(level, type, new BlockPos(s[0], place.floorY(), s[1]), health);
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

    /** The ids of a boss's changes to its attributes. */
    private static final net.minecraft.resources.ResourceLocation BOSS_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArenaMod.MODID, "boss");

    /**
     * Spawns a boss wave's boss: its mob, named, with its health, attack, size and knockback resistance
     * ({@link ArenaBosses.Boss}) and the difficulty's health on top. Where: the arena's middle when no player is near
     * it (the most room for a big mob), else the spot farthest from the players. True when it stands.
     */
    private boolean spawnBoss(ServerLevel level, ArenaBosses.Boss b, double difficultyHealth) {
        EntityType<?> type = EntityType.byString(b.mob()).orElse(null);
        if (type == null) {
            LOG.warn("Moddy Arena: battle: no mob called {} in this game; the boss {} is left out", b.mob(), b.name());
            return false;
        }
        List<double[]> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            players.add(new double[] {p.getX(), p.getZ()});
        }
        int[] spot = ArenaSpawns.nearestPlayer(new int[] {0, 0}, players) >= ArenaSpawns.MIN_FROM_PLAYER
                ? new int[] {0, 0} : ArenaSpawns.pick(place, players, 1, level.random.nextLong()).get(0);
        Entity e = spawnOne(level, type, new BlockPos(spot[0], place.floorY(), spot[1]), 1.0);
        if (!(e instanceof LivingEntity living)) {
            return false;
        }
        setBase(living, Attributes.MAX_HEALTH, b.health());
        if (difficultyHealth != 1.0) {
            living.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID,
                    difficultyHealth - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance dmg = living.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && b.damage() != 1.0) {
            dmg.addOrReplacePermanentModifier(new AttributeModifier(BOSS_ID, b.damage() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        setBase(living, Attributes.SCALE, b.scale());
        setBase(living, Attributes.KNOCKBACK_RESISTANCE, b.knockback());
        living.setHealth(living.getMaxHealth());
        living.setCustomName(Component.literal(b.name()));
        living.setCustomNameVisible(true);
        waveMobs.add(e.getUUID());
        boss = e.getUUID();
        bossName = b.name();
        return true;
    }

    private static void setBase(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a,
                                double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    /** Tells the match how the boss stands (the boss bar shows its health while it does), and when it is beaten. */
    private void watchBoss(ServerLevel level) {
        if (boss == null) {
            match.boss(null, 0f);
            return;
        }
        Entity e = match.phase() == ArenaMatch.Phase.WAVE ? level.getEntity(boss) : null;
        if (e instanceof LivingEntity l && l.isAlive()) {
            match.boss(bossName, l.getHealth() / Math.max(1f, l.getMaxHealth()));
            return;
        }
        if (match.phase() == ArenaMatch.Phase.WAVE) {
            LOG.info("Moddy Arena: battle: the boss {} is beaten", bossName);
            sayToPlace(ArenaWords.bossBeaten(bossName));
        }
        boss = null;
        match.boss(null, 0f);
    }

    /** The id of the difficulty's change to a wave mob's most health. */
    private static final net.minecraft.resources.ResourceLocation HEALTH_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArenaMod.MODID, "difficulty_health");

    private static <T extends Entity> T spawnOne(ServerLevel level, EntityType<T> type, BlockPos at, double health) {
        Consumer<T> mark = e -> {
            e.addTag(TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();   // never despawned while the fighters stand far off
            }
            // a piglin or a hoglin turns into a zombie in the Overworld; an arena's never does
            if (e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
                piglin.setImmuneToZombification(true);
            }
            if (e instanceof net.minecraft.world.entity.monster.hoglin.Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
        };
        T e = type.spawn(level, mark, at, EntitySpawnReason.EVENT, false, false);
        if (e instanceof LivingEntity living && health != 1.0) {
            AttributeInstance max = living.getAttribute(Attributes.MAX_HEALTH);
            if (max != null) {
                max.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, health - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                living.setHealth(living.getMaxHealth());
            }
        }
        return e;
    }

    /** The wave table the world's rules name now (the panel's settings are applied on top), or null. */
    private static ArenaWaves baseWaves() {
        return ArenaWavesRule.wavesOf(com.moddingfromamod.world.WorldRulesService.current());
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
                && !e.hasPassenger(p -> p instanceof Player) && !e.getTags().contains(ArenaBoard.TAG));
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
        bar.setColor(b.boss() ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
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

    /**
     * Each fighter's own bar while the fighting is on (the countdown, the waves and the breathers): his hotbar wishes
     * left this wave, emptying as he fires them. Gone once the fighting is over. A boss bar is what a stock client
     * draws at the top of the screen, always in sight, under the wave's own bar.
     */
    private void updateWishBars() {
        Set<UUID> want = new HashSet<>();
        if (wishes != null && match.fighting()) {
            int perWave = wishes.wishes().wishesPerWave();
            for (ServerPlayer p : audience()) {
                boolean fighter = match.phase() == ArenaMatch.Phase.COUNTDOWN ? place.keeps(p.getX(), p.getY(), p.getZ())
                        : fighters.containsKey(p.getUUID());
                if (!fighter) {
                    continue;
                }
                want.add(p.getUUID());
                int left = wishes.firesLeft(p.getUUID());
                ServerBossEvent b = wishBars.computeIfAbsent(p.getUUID(), id -> new ServerBossEvent(
                        Component.literal(""), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS));
                String name = ArenaWords.barWishes(left, perWave);
                if (!name.equals(b.getName().getString())) {
                    b.setName(Component.literal(name));
                }
                b.setColor(left > 0 ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.WHITE);
                b.setProgress(Math.max(0f, Math.min(1f, (float) left / perWave)));
                if (!b.getPlayers().contains(p)) {
                    b.removeAllPlayers();   // his body before a knock-out, if any: one bar, on the body he has now
                    b.addPlayer(p);
                }
            }
        }
        var it = wishBars.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!want.contains(e.getKey())) {
                e.getValue().removeAllPlayers();
                it.remove();
            }
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
