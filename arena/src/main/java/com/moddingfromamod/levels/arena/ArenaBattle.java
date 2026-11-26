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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
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
    /**
     * The dropped items that are a player's own: what any player dropped when he died (or was knocked out), and what he
     * threw (both told by the game's events, {@link #playersItem}). Kept between waves, moved to the prep room at the
     * battle's end ({@link ArenaTidy}), never removed. Forgotten at the reset, once they are moved.
     */
    private final Set<UUID> playersDrops = new HashSet<>();
    /** The players seen in Creative or Spectator this battle: their runs are not recorded ({@link ArenaUnranked}). */
    private final ArenaUnranked unranked = new ArenaUnranked();
    /**
     * The hotbar wishes (their recipes) each player in the place had when last looked at, so a new one is noticed when he
     * puts it on his hotbar and told once ({@link #watchPins}); a player not looked at yet is absent.
     */
    private final Map<UUID, Set<String>> pins = new HashMap<>();
    /**
     * The arena's wish charge: told when a wave is cleared (the hotbar wishes start again) and when a battle is over
     * (every count and rest starts again), and asked how many hotbar wishes each fighter has left; null in a test.
     */
    private final ArenaCharge wishes;
    /** The fighters who pressed READY in this break. */
    private final Set<UUID> ready = new HashSet<>();
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
    /** The wished creatures' battle roles, during a wave ({@link ArenaRoles}). */
    private final ArenaRoleKeeper roles;
    /** Fighters knocked out with lives left in this wave, to be moved back in when they are on their feet. */
    private final Set<UUID> knockedOut = new HashSet<>();
    /** Of those, the ones back on their feet outside the arena: the ticks until each is moved in. */
    private final Map<UUID, Integer> backIn = new HashMap<>();
    /** Whether the gateway was last set shut (null: not set yet in this run of the server). */
    private Boolean gateSetShut;
    /** The wish ladder board beside the control panel. */
    private final ArenaLadderBoard ladderBoard;
    /**
     * The battle's own place on the wish ladder: the waves it has cleared and the bosses it has beaten, the same for
     * every fighter; none outside a battle, so prep is the first step. Read by the core's {@code WorldMakes} questions
     * (any thread), written on the server thread.
     */
    private volatile int wavesCleared;
    private volatile int bossesBeaten;
    /** True from the button to the arena being ready again: the ladder is the battle's. */
    private volatile boolean ladderOn;
    /** This battle's {@link ArenaRespawn#KEY} setting, read when it began. */
    private boolean respawnInPrep = true;
    /** Where the ladder was when its step was last told (at the start of a break). */
    private int ladderTold;
    private long ticks;

    /** Which arena this is ({@code main} for an arena world's own), and the id its world wishes go under. */
    private final String site;
    private final String wishModule;

    ArenaBattle(MinecraftServer server, ArenaPlace place, ArenaState state, ArenaCharge wishes) {
        this(server, place, state, wishes, ArenaSiteRules.MAIN);
    }

    /**
     * A battle for the arena {@code site} (Moddy-Worlds#58). Its world wishes (pillars, champion designs) go under its
     * own module id, so one arena's {@code cancelAll} never touches another's: {@code moddyarena} for {@code main}, as it
     * always was, {@code moddyarena/<site>} for the rest.
     */
    ArenaBattle(MinecraftServer server, ArenaPlace place, ArenaState state, ArenaCharge wishes, String site) {
        this.site = site == null ? ArenaSiteRules.MAIN : site;
        this.wishModule = ArenaSiteRules.MAIN.equals(this.site) ? ArenaMod.MODID : ArenaMod.MODID + "/" + this.site;
        this.server = server;
        this.place = place;
        this.state = state;
        this.wishes = wishes;
        this.board = new ArenaBoard(server, place, state);
        this.champions = new ArenaChampionsBoard(server, place, state);
        this.ladderBoard = new ArenaLadderBoard(server, place);
        this.roles = new ArenaRoleKeeper(server, place, List.of(TAG, ArenaBoard.TAG, ArenaChampionsBoard.TAG,
                ArenaRoleKeeper.SIGN_TAG, ArenaLadderBoard.TAG));
        if (state.battleOn()) {
            match.endedByRestart();
            LOG.info("Moddy Arena: battle: a battle was on when the server stopped; it is over, and the arena will be"
                    + " reset in {} seconds", ArenaMatch.RESTART_WAIT_TICKS / ArenaMatch.TICKS_PER_SECOND);
        }
    }

    ArenaMatch match() {
        return match;
    }

    /** Whether an entity is in the place (the arena or its prep room) in the arena's world. */
    /**
     * Where a player the game is about to respawn at {@code to} in {@code toLevel} should come back instead
     * (Moddy-Worlds#60): this place's prep room when he fought in the battle that is on and {@code to} is outside the
     * place, else null (where the game would put him). Server thread.
     */
    int[] respawnAt(ServerPlayer p, boolean fromTheEnd, ServerLevel toLevel, double x, double y, double z) {
        UUID id = p.getUUID();
        boolean fought = fighters.containsKey(id) || told.contains(id);
        boolean inPlace = toLevel == level() && place.keeps(x, y, z);
        return ArenaRespawn.toPrepRoom(fromTheEnd, p.isSpectator(), match.phase() != ArenaMatch.Phase.IDLE, fought,
                respawnInPrep, inPlace) ? place.arrival() : null;
    }

    /** The world the place stands in. */
    ServerLevel placeLevel() {
        return level();
    }

    boolean inPlace(Entity e) {
        return e != null && e.level() == level() && place.keeps(e.getX(), e.getY(), e.getZ());
    }

    void stop() {
        ServerLevel level = level();
        if (level != null) {
            roles.releaseAll(level);
        }
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
        // the settings command's layers on the rules' timings (Moddy-Worlds#59), read now, once, for this battle
        ArenaConfig config = server == null ? ArenaConfig.EMPTY : ArenaConfigStore.of(server).config();
        ArenaRun waves = settings.run(config.apply(base, site));
        respawnInPrep = config.value(ArenaConfig.key(ArenaRespawn.KEY), site, base) != 0;
        battleDifficulty = settings.difficulty();
        battleKey = ArenaChampions.Key.of(settings);
        match.start(waves);
        state.battleOn(true);
        fighters.clear();
        reached.clear();
        fighterNames.clear();
        tellLives.clear();
        knockedOut.clear();
        backIn.clear();
        boss = null;
        ladderReset(true);   // every battle starts at the ladder's first step
        // wished waves (Moddy-Worlds#55): this battle's rule and its champions, drawn now that its settings are locked
        // in, and the first designs made by the Builder during the countdown
        var usable = ArenaWishedWavesRule.of(com.moddingfromamod.world.WorldRulesService.current())
                .usable(ArenaWishedWaves::builderBody, ArenaWishedWaves::builderScheme);
        wished = usable.waves();
        for (String w : usable.skipped()) {
            LOG.warn("Moddy Arena: wished waves: {}", w);
        }
        picks = wished.pick(new java.util.Random(level().random.nextLong()));
        designs.clear();
        held.clear();
        designQueue.clear();
        designing.clear();
        if (makingKey != null) {
            designing.add(makingKey);   // still being made: it lands in this battle's designs if this battle wants it
        }
        if (wished.active()) {
            LOG.info("Moddy Arena: champions: this battle's bodies are {} ({})", picks.perTier(), wished.scheme());
            wantDesigns(0);
        }
        unranked.clear();
        told.clear();
        waveMobs.clear();
        com.moddingfromamod.world.WorldWishes.cancelAll(wishModule);   // pillars still rising: called off
        held.clear();
        ready.clear();
        roles.battleStarted(ArenaCreaturesRule.creaturesOf(com.moddingfromamod.world.WorldRulesService.current()));
        LOG.info("Moddy Arena: battle: COUNTDOWN, started by {}: {} waves, the first in {} s (wave size {}, difficulty"
                + " {}; the first wave {} mobs; boss waves {}; {} lives)", by, ArenaWords.wavesValue(waves.count()),
                waves.countdownSeconds(), waves.waveSize(), settings.difficulty().word(), waves.size(1),
                waves.bossWaves(20), waves.lives());
        sayToPlace(ArenaWords.started(waves.count(), waves.countdownSeconds(), waves.lives()));
        sayToPlace(ArenaWords.gateRule());
        sayToPlace(ArenaWords.creaturesHelp());
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
        safetyNet(level);
        releaseChampions(level);
        if (match.fighting()) {
            watchModes(level);   // before the fighters are counted: one who turned Spectator is out, and unrecorded
        }
        if (ticks % 10 == 0) {
            watchPins(level);
        }
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
        roles.tick(level, match.phase() == ArenaMatch.Phase.WAVE && mobsLeft > 0, fighters.keySet(), waveMobs);
        watchBoss(level);
        if (match.inBreak() && !ready.isEmpty() && ArenaMatch.allReady(fighters.keySet(), ready) && match.ready()) {
            LOG.info("Moddy Arena: battle: every fighter still in is ready; the break ends in {} s",
                    ArenaMatch.READY_TICKS / ArenaMatch.TICKS_PER_SECOND);
            sayToPlace(ArenaWords.allReady(ArenaMatch.READY_TICKS / ArenaMatch.TICKS_PER_SECOND));
        }
        ArenaMatch.Event ev = match.tick(mobsLeft, fightersIn);
        if (ev != null) {
            handle(level, ev);
        }
        keepGate(level, ev != null);
        backInTheArena(level);
        if (ev != null || ticks % 10 == 0) {
            updateBar();
        }
        if (ev != null || ticks % 5 == 0) {
            updateWishBars();
        }
        board.refresh(level, match, ArenaBattle::baseWaves, ev != null);
        if (ev != null || ticks % 10 == 0) {
            ArenaLadder ladder = ArenaLadderRule.ladderOf(com.moddingfromamod.world.WorldRulesService.current());
            ladderBoard.refresh(level, ArenaWords.ladderBoard(ladder, ladderOn, reached(ladder)));
        }
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
        if (match.inBreak()) {
            readyPressed(level, presser, b);
            return;
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

    /**
     * In a break the start button reads READY: a fighter who presses it is ready, and once every fighter still in is,
     * the break ends in 3 seconds (the tick sees it, so a fighter who leaves does not hold the others up).
     */
    private void readyPressed(ServerLevel level, ServerPlayer presser, int[] at) {
        if (presser == null) {
            return;
        }
        if (!fighters.containsKey(presser.getUUID())) {
            presser.displayClientMessage(Component.literal(ArenaWords.readyNotFighting()), true);
            return;
        }
        boolean first = ready.add(presser.getUUID());
        level.playSound(null, at[0] + 0.5, at[1] + 0.5, at[2] + 0.5, SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.BLOCKS, 0.8f, 1.2f);
        int notYet = 0;
        for (UUID id : fighters.keySet()) {
            notYet += ready.contains(id) ? 0 : 1;
        }
        if (first) {
            LOG.info("Moddy Arena: battle: {} is ready ({} fighters not yet)", presser.getScoreboardName(), notYet);
        }
        if (notYet > 0) {
            presser.displayClientMessage(Component.literal(ArenaWords.readyWaiting(notYet)), true);
        }
    }

    /**
     * Between two waves the arena mends itself: every block of it is put back as built (a wish that tore it up, or
     * built in it, does not spoil the next wave), a panel button knocked off goes back, and the loose things a fight
     * leaves flying are tidied ({@link #tidyBetweenWaves}). How many blocks were set.
     */
    private int mend(ServerLevel level) {
        long t0 = System.nanoTime();
        int blocks = ArenaBuilder.reset(level, place, ArenaGate.shut(match.phase()))
                + Math.max(0, ArenaBuilder.addControl(level, place)) + ArenaBuilder.mendPrepFloor(level, place);
        int loose = tidyBetweenWaves(level);
        LOG.info("Moddy Arena: battle: the arena is mended for the next wave ({} blocks set, {} loose things removed)"
                + " in {} ms", blocks, loose, (System.nanoTime() - t0) / 1_000_000);
        return blocks;
    }

    /**
     * What a break tidies in the arena: the end-of-battle rule ({@link #clearArena}) for the things a fight leaves
     * flying or falling (arrows and other thrown things, lit TNT, falling blocks, lingering clouds, evoker fangs), but
     * not what the end of the battle waits for: living creatures (the players' own wished creatures and pets fight on
     * with them), the displays a wished creature is drawn with, and dropped things and experience (a fighter may pick
     * them up in the break; the battle's end tidies them).
     */
    private int tidyBetweenWaves(ServerLevel level) {
        ArenaPlace.Box r = place.resetBox(level.getMaxY());
        AABB arena = new AABB(r.x0(), r.y0() - 1, r.z0(), r.x1() + 1, r.y1() + 1, r.z1() + 1);
        return discard(level, arena, e -> ArenaTidy.betweenWaves(thing(e)) == ArenaTidy.Act.REMOVE);
    }

    /** A thing a fight leaves flying or falling, tidied between waves. */
    static boolean looseThing(Entity e) {
        return e instanceof net.minecraft.world.entity.projectile.Projectile
                || e instanceof net.minecraft.world.entity.item.PrimedTnt
                || e instanceof net.minecraft.world.entity.item.FallingBlockEntity
                || e instanceof net.minecraft.world.entity.AreaEffectCloud
                || e instanceof net.minecraft.world.entity.projectile.EvokerFangs;
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
                ready.clear();
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
                moveFightersIn(level);
                keepGate(level, true);
                ArenaRun run = match.table();
                ArenaBosses.Boss b = run.boss(ev.wave());
                int n;
                if (wishedOn()) {
                    // wished waves (Moddy-Worlds#55): the arena wishes them, under its red pillars, and they arrive
                    // in about two seconds; the wave is not over while any is still on its way (mobsLeft)
                    n = wishWave(level, run.wave(ev.wave()), run.health(ev.wave()));
                    if (b != null && wishBoss(level, b, run.bossHealth())) {
                        n++;
                    }
                } else {
                    n = spawnWave(level, run.wave(ev.wave()), run.health(ev.wave()));
                    if (b != null && spawnBoss(level, b, run.bossHealth())) {
                        n++;
                    }
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
                if (wishedOn()) {
                    wantDesigns(reached());   // the next rung's champions, made during the break
                }
                LOG.info("Moddy Arena: battle: wave {} cleared; BREATHER of {} s; the hotbar wishes start again",
                        ev.wave(), ev.seconds());
                sayToPlace(ArenaWords.cleared(ev.wave(), ev.seconds()));
                wavesCleared = Math.max(wavesCleared, ev.wave());
                ladderUp();
                ready.clear();
                if (mend(level) > 0) {
                    sayToPlace(ArenaWords.mended());
                }
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
                int blocks = ArenaBuilder.reset(level, place, ArenaGate.shut(match.phase()));
                gateSetShut = false;
                blocks += ArenaBuilder.mendPrepFloor(level, place);
                int panelBlocks = ArenaBuilder.addControl(level, place);   // a button knocked off its board, put back
                if (panelBlocks > 0) {
                    blocks += panelBlocks;
                }
                int entities = clearArena(level);
                fighters.clear();
                waveMobs.clear();
                com.moddingfromamod.world.WorldWishes.cancelAll(wishModule);   // pillars still rising: called off
                held.clear();
                LOG.info("Moddy Arena: battle: RESETTING: the arena is put back as built ({} blocks set, {} things"
                        + " removed) in {} ms", blocks, entities, (System.nanoTime() - t0) / 1_000_000);
            }
            case READY -> {
                state.battleOn(false);
                LOG.info("Moddy Arena: battle: IDLE, the arena is ready for the next battle");
                sayToPlace(ArenaWords.ready());
                // the battle's ladder is gone with it: prep is the first step again, and the creatures are held back
                // to it (the core's line about them after the arena's)
                ladderReset(false);
                told.clear();
                pins.clear();   // a new prep: his hotbar wishes are looked at afresh, silently; a new one is told
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
                if (f[1] > 0) {
                    LOG.info("Moddy Arena: battle: {} was knocked out; {} lives left", p.getScoreboardName(), f[1]);
                    p.sendSystemMessage(Component.literal(ArenaWords.knockedOut(f[1])));
                    tellLives.add(p.getUUID());
                    if (match.phase() == ArenaMatch.Phase.WAVE) {
                        knockedOut.add(p.getUUID());
                    }
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

    // ── the ground ──────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Nobody is lost under the place: the floor under the world spawn is put back when it is missing (every second), and
     * a player who fell out under the place (past the base, {@link ArenaPlace#fellOut}) is put back in the prep room,
     * unharmed, with one line. It costs no life: a life is lost only by being knocked out.
     */
    private void safetyNet(ServerLevel level) {
        if (ticks % ArenaMatch.TICKS_PER_SECOND == 0 && ArenaBuilder.keepSpawnFloor(level, place)) {
            LOG.info("Moddy Arena: the floor under the world spawn was missing; put back");
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || !place.fellOut(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            ArenaBuilder.keepSpawnFloor(level, place);
            int[] a = place.arrival();
            p.stopRiding();
            p.teleportTo(level, a[0] + 0.5, a[1], a[2] + 0.5, Set.of(), ArenaPlace.ARRIVAL_YAW, 0f, true);
            p.fallDistance = 0;
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.clearFire();
            p.displayClientMessage(Component.literal(ArenaWords.fellOut()), true);
            LOG.info("Moddy Arena: {} fell out under the place; put back in the prep room", p.getScoreboardName());
        }
    }

    // ── Creative and Spectator ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * While the fighting is on, every player in the place and every fighter is looked at: one in Creative or Spectator
     * (at the start, or switched to it) has no run recorded for this battle, and is told so once, in chat
     * ({@link ArenaUnranked}). Chat is the one channel that is his alone: the boards are text displays every player sees
     * alike.
     */
    private void watchModes(ServerLevel level) {
        Set<ServerPlayer> look = new java.util.LinkedHashSet<>();
        for (ServerPlayer p : level.players()) {
            if (place.keeps(p.getX(), p.getY(), p.getZ())) {
                look.add(p);
            }
        }
        for (UUID id : fighters.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                look.add(p);
            }
        }
        for (ServerPlayer p : look) {
            ArenaUnranked.Mode mode = unranked.saw(p.getUUID(), modeOf(p));
            if (mode != null) {
                LOG.info("Moddy Arena: battle: {} is in {} mode; this battle's run of his is not recorded",
                        p.getScoreboardName(), mode);
                p.sendSystemMessage(Component.literal(ArenaWords.notRecorded(mode)));
            }
        }
    }

    /** A player's game mode, as the Champions board cares (a player not online plays). */
    static ArenaUnranked.Mode modeOf(ServerPlayer p) {
        if (p == null) {
            return ArenaUnranked.Mode.PLAY;
        }
        return p.isSpectator() ? ArenaUnranked.Mode.SPECTATOR
                : p.isCreative() ? ArenaUnranked.Mode.CREATIVE : ArenaUnranked.Mode.PLAY;
    }

    // ── a wish put on the hotbar ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Twice a second: the hotbar wishes (the core's proxies, read by their recipe, {@code WishHotbar.recipeOf}) of each
     * player in the place. A recipe he did not have before in this prep is one he just put on his hotbar: when, at the
     * step the battle is on (prep and a battle's start are the first), it would be locked or made smaller or weaker, he
     * is told once, with when that changes ({@link ArenaPinNotice}). The first look at a player, and the first after a
     * battle, only notes what he has. A wish taken off and put back in the same prep is not told again.
     */
    private void watchPins(ServerLevel level) {
        pins.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        ArenaLadder ladder = null;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || !place.keeps(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            Map<String, ItemStack> now = hotbarWishes(p);
            Set<String> before = pins.get(p.getUUID());
            Set<String> after = new HashSet<>(now.keySet());
            if (before != null) {
                after.addAll(before);
            }
            pins.put(p.getUUID(), after);
            if (before == null) {
                continue;
            }
            for (Map.Entry<String, ItemStack> e : now.entrySet()) {
                if (before.contains(e.getKey())) {
                    continue;
                }
                if (ladder == null) {
                    ladder = ArenaLadderRule.ladderOf(com.moddingfromamod.world.WorldRulesService.current());
                }
                tellPin(p, e.getKey(), e.getValue(), ladder);
            }
        }
    }

    /** His hotbar wishes, by recipe: the proxies in his inventory (hotbar, bag and off hand). */
    private static Map<String, ItemStack> hotbarWishes(ServerPlayer p) {
        Map<String, ItemStack> out = new LinkedHashMap<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            String recipe = s.isEmpty() ? null : com.moddingfromamod.wish.WishHotbar.recipeOf(s);
            if (recipe != null) {
                out.putIfAbsent(recipe, s);
            }
        }
        return out;
    }

    private void tellPin(ServerPlayer p, String recipe, ItemStack proxy, ArenaLadder ladder) {
        UUID owner = p.getUUID();
        String o = com.moddingfromamod.wish.WishHotbar.ownerOf(proxy);
        try {
            if (o != null && !o.isEmpty()) {
                owner = UUID.fromString(o);
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID: his own
        }
        com.moddingfromamod.wishbuilder.MakeFacts facts;
        try {
            facts = com.moddingfromamod.wish.MakeFactsLookup.forRecipe(server, owner, recipe);
        } catch (RuntimeException ex) {
            LOG.warn("Moddy Arena: could not read what the hotbar wish {} of {} makes: {}", recipe,
                    p.getScoreboardName(), ex.toString());
            return;
        }
        ArenaPinNotice.Notice n = ArenaPinNotice.of(ladder, reached(ladder), facts);
        String line = ArenaWords.pinNotice(ArenaPinNotice.nameOf(proxy.getHoverName().getString()), n,
                ladder.progress());
        if (line == null) {
            return;
        }
        LOG.info("Moddy Arena: hotbar: {} put {} on his hotbar: {} (step {})", p.getScoreboardName(), recipe, n,
                ladder.stepIndex(reached(ladder)) + 1);
        p.sendSystemMessage(Component.literal(line));
    }

    // ── the gate ────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Keeps the gateway as the battle wants it ({@link ArenaGate#shut}): iron bars while a wave is on, the fence gate
     * otherwise. Set at once when that changes ({@code now}), and looked at again every second (so bars broken in a
     * wave come back, and bars left by a stop in the middle of one go).
     */
    private void keepGate(ServerLevel level, boolean now) {
        boolean shut = ArenaGate.shut(match.phase());
        if (!now && gateSetShut != null && gateSetShut == shut && ticks % ArenaMatch.TICKS_PER_SECOND != 0) {
            return;
        }
        if (shut && !Boolean.TRUE.equals(gateSetShut)) {
            standOutOfTheGateway(level);
        }
        int set = ArenaBuilder.gate(level, place, shut);
        if (set > 0) {
            LOG.info("Moddy Arena: battle: the gate is {} ({} blocks set)", shut ? "shut: iron bars for the wave"
                    : "open: the fence gate again", set);
        }
        gateSetShut = shut;
    }

    /** Before the bars go in: a player standing in the gateway who is not fighting steps to the prep room's side. */
    private void standOutOfTheGateway(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && !fighters.containsKey(p.getUUID())
                    && ArenaGate.inGateway(place, p.getX(), p.getZ(), p.getBbWidth() / 2)
                    && p.getY() < place.floorY() + 2 && p.getY() > place.floorY() - 1) {
                double[] o = ArenaGate.outside(place);
                p.teleportTo(level, o[0], o[1], o[2], Set.of(), p.getYRot(), p.getXRot(), true);
            }
        }
    }

    /** When a wave starts: every fighter on his feet who is not inside the arena is moved in, and told. */
    private void moveFightersIn(ServerLevel level) {
        backIn.clear();
        knockedOut.clear();
        for (UUID id : fighters.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && onFeet(p) && !ArenaGate.inArena(place, p.getX(), p.getY(), p.getZ())) {
                moveIn(level, p, ArenaWords.gateShut());
            }
        }
    }

    /**
     * During a wave, a fighter knocked out with lives left who is back on his feet outside the arena (in the prep room,
     * where he comes back) counts {@value ArenaGate#BACK_IN_SECONDS} seconds on his action bar, with his lives, and is
     * then moved in. Once the wave is over (or he went in himself) nothing more happens: in a break he walks in, and the
     * next wave takes him in.
     */
    private void backInTheArena(ServerLevel level) {
        if (knockedOut.isEmpty()) {
            return;
        }
        boolean waveOn = match.phase() == ArenaMatch.Phase.WAVE;
        var it = knockedOut.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            int[] f = fighters.get(id);
            if (p == null || f == null || !waveOn) {
                backIn.remove(id);
                it.remove();
                continue;
            }
            boolean inArena = ArenaGate.inArena(place, p.getX(), p.getY(), p.getZ());
            Integer left = backIn.get(id);
            switch (ArenaGate.act(true, false, onFeet(p), inArena, left == null ? -1 : left)) {
                case START_COUNT -> {
                    backIn.put(id, ArenaGate.BACK_IN_TICKS);
                    tellLives.remove(id);
                    p.displayClientMessage(Component.literal(ArenaWords.backInCount(f[1],
                            ArenaGate.BACK_IN_SECONDS)), true);
                }
                case COUNT -> {
                    int now = left - 1;
                    backIn.put(id, now);
                    if (now > 0 && now % ArenaMatch.TICKS_PER_SECOND == 0) {
                        p.displayClientMessage(Component.literal(ArenaWords.backInCount(f[1],
                                ArenaGate.secondsLeft(now))), true);
                    }
                }
                case MOVE -> {
                    backIn.remove(id);
                    it.remove();
                    moveIn(level, p, ArenaWords.backIn());
                }
                case STOP_COUNT -> {
                    backIn.remove(id);
                    if (inArena && onFeet(p)) {
                        it.remove();            // he went in by himself
                    }
                }
                case NONE -> {
                    if (inArena && onFeet(p)) {
                        it.remove();            // on his feet in the arena: nothing to do (a knock-out is a body there)
                    }
                }
            }
        }
    }

    /** Moves a fighter just inside the gate, facing into the arena, with one short line on his action bar. */
    private void moveIn(ServerLevel level, ServerPlayer p, String words) {
        double[] in = ArenaGate.inside(place);
        p.stopRiding();
        p.teleportTo(level, in[0], in[1], in[2], Set.of(), ArenaGate.INSIDE_YAW, 0f, true);
        p.fallDistance = 0;
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.displayClientMessage(Component.literal(words), true);
        LOG.info("Moddy Arena: battle: {} moved into the arena, just inside the gate", p.getScoreboardName());
    }

    private static boolean onFeet(ServerPlayer p) {
        return p.isAlive() && !p.isDeadOrDying() && !p.isSpectator();
    }

    private List<double[]> playerSpots(ServerLevel level) {
        List<double[]> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            players.add(new double[] {p.getX(), p.getZ()});
        }
        return players;
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
        for (UUID id : unranked.unranked()) {
            if (reached.containsKey(id)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                LOG.info("Moddy Arena: Champions: {} was in Creative or Spectator in this battle; the run is not recorded",
                        p != null ? p.getScoreboardName() : fighterNames.getOrDefault(id, id.toString()));
            }
        }
        for (Map.Entry<UUID, ArenaChampions.Reach> e : unranked.onlyRecorded(reached).entrySet()) {
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
        int[] centre = {place.centreX(), place.centreZ()};      // the arena's middle, wherever the place stands
        int[] spot = ArenaSpawns.nearestPlayer(centre, players) >= ArenaSpawns.MIN_FROM_PLAYER
                ? centre : ArenaSpawns.pick(place, players, 1, level.random.nextLong()).get(0);
        Entity e = spawnOne(level, type, new BlockPos(spot[0], place.floorY(), spot[1]), 1.0);
        if (!(e instanceof LivingEntity living)) {
            return false;
        }
        bossStats(living, b, difficultyHealth);
        waveMobs.add(e.getUUID());
        boss = e.getUUID();
        bossName = b.name();
        return true;
    }

    /** A boss's own numbers (its health, attack, size, knockback resistance, name), and the difficulty's health. */
    private static void bossStats(LivingEntity living, ArenaBosses.Boss b, double difficultyHealth) {
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
    }

    // ── wished waves (Moddy-Worlds#55) ──────────────────────────────────────────────────────────────────────────────

    /** This battle's wished waves (its rule, read at its start) and its champions' bodies, drawn then. */
    private ArenaWishedWaves wished = ArenaWishedWaves.DEFAULT;
    private ArenaWishedWaves.Picks picks;

    private boolean wishedOn() {
        return wished != null && wished.active();
    }

    /** A champion waiting for its design: it arrives last, once the design is made, or as its stand-in at the deadline. */
    private record Held(ArenaWishedWaves.Plan plan, int[] spot, com.moddingfromamod.world.WorldWishes.Limits limits,
                        Consumer<LivingEntity> arrived, long deadline) {
    }

    private final List<Held> held = new ArrayList<>();

    /**
     * Each champion still waiting is wished the moment its design is made, or, past its deadline
     * ({@code championWaitSeconds}), as the wave's mob one rung up (a design that failed or is slow). Server thread.
     */
    private void releaseChampions(ServerLevel level) {
        if (held.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (var it = held.iterator(); it.hasNext(); ) {
            Held h = it.next();
            String key = h.plan().design().toJson().toString();
            String species = designs.get(key);
            if (species == null && now < h.deadline() && !unmade.contains(key)) {
                continue;
            }
            it.remove();
            try {
                if (species != null) {
                    com.moddingfromamod.world.WorldWishes.species(level, wishModule, species, h.spot()[0] + 0.5,
                            place.floorY(), h.spot()[1] + 0.5, h.limits(), h.plan().slim(), h.arrived());
                } else {
                    LOG.info("Moddy Arena: champions: {} was not made in time; its stand-in comes instead",
                            h.plan().design().name());
                    com.moddingfromamod.world.WorldWishes.creature(level, wishModule, h.plan().mob(),
                            h.spot()[0] + 0.5, place.floorY(), h.spot()[1] + 0.5, h.limits(), h.plan().slim(),
                            h.arrived());
                }
            } catch (IllegalArgumentException notAMob) {
                LOG.warn("Moddy Arena: battle: a champion is left out: {}", notAMob.getMessage());
            }
        }
    }

    /** The world's own champion designs: a design spec (its JSON) → the species the Builder made for it. */
    private final Map<String, String> designs = new HashMap<>();
    /** Designs asked for and not made yet: the queue, and the one being made (keys are design spec JSON). */
    private final java.util.Set<String> designing = new java.util.HashSet<>();
    private final java.util.ArrayDeque<com.moddingfromamod.wishbuilder.BuilderSpec> designQueue = new java.util.ArrayDeque<>();
    private String makingKey;

    /**
     * Have the Builder make the champion designs this battle will want next (this rung's and the next one's) that are
     * not made or asked for yet. Each is a real Builder make, owned by the arena ({@code BuilderMake.forWorld}), made
     * once a battle and then wished as copies. They are made ONE AT A TIME, in order: the core runs one Builder make
     * per owner at once and answers a second with BUSY, so asking for two together lost the second. Measured
     * (WorldWishesGameTest#everyChampionBodyIsMadeWellInsideTheCountdown): 77–510 ms a design, the slowest two in a
     * row 760 ms, against the 5 s countdown. Server thread.
     */
    private void wantDesigns(int cleared) {
        for (com.moddingfromamod.wishbuilder.BuilderSpec spec : wished.designsAhead(cleared, picks)) {
            String key = spec.toJson().toString();
            if (designs.containsKey(key) || unmade.contains(key) || !designing.add(key)) {
                continue;
            }
            designQueue.add(spec);
        }
        makeNextDesign();
    }

    /** Start the next queued design if none is being made. Server thread. */
    /**
     * The designs the Builder would not make this run of the game (refused, failed): not asked again, and their
     * champions come as their stand-ins at once rather than after a wait.
     */
    private final java.util.Set<String> unmade = new java.util.HashSet<>();

    /** The name a champion takes when the Builder's name sweep refuses the one the rules gave it. */
    static final String SAFE_CHAMPION_NAME = "Champion";

    private void makeNextDesign() {
        if (makingKey != null || designQueue.isEmpty()) {
            return;
        }
        com.moddingfromamod.wishbuilder.BuilderSpec spec = designQueue.poll();
        makeDesign(spec, spec.toJson().toString(), false);
    }

    /**
     * One design made, kept under {@code key} (the spec the battle asked for). A make the core refuses (its name fails
     * the child-name safety sweep) is asked once more under {@link #SAFE_CHAMPION_NAME}; one that still does not come
     * is {@link #unmade}: its champions are their stand-ins, and no wave waits for it or breaks on it.
     */
    private void makeDesign(com.moddingfromamod.wishbuilder.BuilderSpec spec, String key, boolean renamed) {
        makingKey = key;
        long t0 = System.nanoTime();
        LOG.info("Moddy Arena: champions: the Builder makes {} ({}, {}); {} more queued", spec.name(), spec.from(),
                spec.scheme(), designQueue.size());
        com.moddingfromamod.wishbuilder.BuilderMake.forWorld(server, wishModule, "The Arena", spec)
                .whenComplete((r, err) -> server.execute(() -> {
                    makingKey = null;
                    designing.remove(key);
                    boolean ok = err == null && r != null
                            && (r.outcome() == com.moddingfromamod.wishbuilder.BuilderMake.Outcome.MADE
                            || r.outcome() == com.moddingfromamod.wishbuilder.BuilderMake.Outcome.PARTIAL)
                            && r.madeId() != null && !r.madeId().isEmpty();
                    if (ok) {
                        designs.put(key, r.madeId());
                    } else if (!renamed && r != null
                            && r.outcome() == com.moddingfromamod.wishbuilder.BuilderMake.Outcome.REFUSED) {
                        LOG.warn("Moddy Arena: champions: the Builder refused {} ({}); asked again as {}", spec.name(),
                                r.detail(), SAFE_CHAMPION_NAME);
                        makingKey = key;
                        designing.add(key);
                        makeDesign(new com.moddingfromamod.wishbuilder.BuilderSpec(spec.version(), spec.kind(),
                                spec.from(), spec.scheme(), spec.size(), spec.powers(), spec.temper(), spec.count(),
                                spec.at(), SAFE_CHAMPION_NAME, spec.opts()), key, true);
                        return;
                    } else if (r == null || r.outcome() != com.moddingfromamod.wishbuilder.BuilderMake.Outcome.BUSY) {
                        unmade.add(key);
                    }
                    LOG.info("Moddy Arena: champions: {} {} in {} ms", spec.name(), ok ? "is ready (" + r.madeId() + ")"
                            : "could not be made: " + (err != null ? err.toString() : r == null ? "no result"
                            : r.outcome() + " " + r.detail()), (System.nanoTime() - t0) / 1_000_000);
                    makeNextDesign();
                }));
    }

    /**
     * The wave, wished through the Builder: each monster is a Builder real mob ({@link ArenaWishedWaves#mob}), or a
     * champion's Builder design once made, held at the ladder's limits for a PLAYER at the battle's place now, at the
     * spot {@link ArenaSpawns} picks, and marked as a wave mob when it arrives (as {@link #spawnOne} marks one).
     * @return how many were wished.
     */
    private int wishWave(ServerLevel level, ArenaWaves.Wave wave, double health) {
        waveMobs.clear();
        List<double[]> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            players.add(new double[] {p.getX(), p.getZ()});
        }
        List<int[]> spots = ArenaSpawns.pick(place, players, wave.size(), level.random.nextLong());
        ArenaLadder ladder = ArenaLadderRule.ladderOf(com.moddingfromamod.world.WorldRulesService.current());
        int cleared = reached(ladder);
        wantDesigns(cleared);
        var limits = com.moddingfromamod.world.WorldWishes.Limits.of(ladder.limits(cleared));
        int i = 0;
        int count = 0;
        int asDesigns = 0;
        long now = server.getTickCount();
        int waiting = 0;
        for (ArenaWishedWaves.Plan plan : wished.plan(wave, cleared, picks)) {
            int[] s = spots.get(i++);
            Consumer<LivingEntity> arrived = m -> {
                markWave(m);
                difficultyHealth(m, health);
                waveMobs.add(m.getUUID());
            };
            String species = plan.design() == null ? null : designs.get(plan.design().toJson().toString());
            boolean neverComes = plan.design() != null && unmade.contains(plan.design().toJson().toString());
            if (plan.design() != null && species == null && !neverComes && wished.championWaitSeconds() > 0) {
                // its design is still being made: it arrives last, as soon as it is (releaseChampions)
                held.add(new Held(plan, s, limits, arrived, now + wished.championWaitSeconds() * 20L));
                waiting++;
                count++;
                continue;
            }
            try {
                if (species != null) {
                    com.moddingfromamod.world.WorldWishes.species(level, wishModule, species, s[0] + 0.5,
                            place.floorY(), s[1] + 0.5, limits, plan.slim(), arrived);
                    asDesigns++;
                } else {
                    com.moddingfromamod.world.WorldWishes.creature(level, wishModule, plan.mob(), s[0] + 0.5,
                            place.floorY(), s[1] + 0.5, limits, plan.slim(), arrived);
                }
                count++;
            } catch (IllegalArgumentException notAMob) {
                LOG.warn("Moddy Arena: battle: {} is left out: {}", plan.mob().from(), notAMob.getMessage());
            }
        }
        LOG.info("Moddy Arena: battle: {} wished through the Builder ({} as champion designs, {} champion(s) arriving"
                + " last) at {} waves cleared, held to {}", count, asDesigns, waiting, cleared, limits.ceiling());
        return count;
    }

    /**
     * The boss, wished under a full pillar as a Builder real mob, UNCAPPED (a boss fight is meant to be hard): its own
     * numbers as {@link #spawnBoss} gives them, when it arrives.
     */
    private boolean wishBoss(ServerLevel level, ArenaBosses.Boss b, double difficultyHealth) {
        List<double[]> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            players.add(new double[] {p.getX(), p.getZ()});
        }
        int[] centre = {place.centreX(), place.centreZ()};      // the arena's middle, wherever the place stands
        int[] spot = ArenaSpawns.nearestPlayer(centre, players) >= ArenaSpawns.MIN_FROM_PLAYER
                ? centre : ArenaSpawns.pick(place, players, 1, level.random.nextLong()).get(0);
        try {
            com.moddingfromamod.world.WorldWishes.creature(level, wishModule, wished.mob(b.mob(), 0),
                    spot[0] + 0.5, place.floorY(), spot[1] + 0.5, wished.bossUncapped()
                            ? com.moddingfromamod.world.WorldWishes.Limits.UNCAPPED
                            : com.moddingfromamod.world.WorldWishes.Limits.of(ArenaLadderRule.ladderOf(
                                    com.moddingfromamod.world.WorldRulesService.current()).limits(reached())),
                    false, m -> {
                        markWave(m);
                        bossStats(m, b, difficultyHealth);
                        waveMobs.add(m.getUUID());
                        boss = m.getUUID();
                        bossName = b.name();
                    });
            return true;
        } catch (IllegalArgumentException notAMob) {
            LOG.warn("Moddy Arena: battle: the boss {} ({}) is left out: {}", b.name(), b.mob(), notAMob.getMessage());
            return false;
        }
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
            bossesBeaten++;
            LOG.info("Moddy Arena: ladder: boss {} of this battle beaten", bossesBeaten);
        }
        boss = null;
        match.boss(null, 0f);
    }

    /** Where the battle is on this ladder (0 outside a battle: the first step). */
    int reached(ArenaLadder ladder) {
        return ladderOn ? ladder.reached(wavesCleared, bossesBeaten) : 0;
    }

    /** True from the button to the arena being ready again: the ladder is the battle's. */
    boolean ladderOn() {
        return ladderOn;
    }

    /** Where the battle is on the ladder in force now (0 outside a battle). Any thread. */
    int reached() {
        return reached(ArenaLadderRule.ladderOf(com.moddingfromamod.world.WorldRulesService.current()));
    }

    /** The ladder back at its first step: a battle starting ({@code on}), or the arena ready again after one. */
    private void ladderReset(boolean on) {
        boolean wasUp = ladderTold > 0 || wavesCleared > 0 || bossesBeaten > 0;
        wavesCleared = 0;
        bossesBeaten = 0;
        ladderTold = 0;
        ladderOn = on;
        ladderBoard.changed();
        if (wasUp) {
            refitWished("the ladder is back at its first step");
        }
    }

    /**
     * The ladder's step changed, so the limits the arena answers the core's {@code WorldMakes.limits} with changed too,
     * for everyone (the ladder is the battle's, the same for every fighter): the core is told for every player on the
     * server, and it fits each one's living wished creatures again from their own numbers (stronger as a step opens,
     * held down again at the battle's end), keeping how hurt each was, and tells him once if anything moved. The arena
     * never writes the core's fit itself. Server thread.
     */
    private void refitWished(String why) {
        List<ServerPlayer> everyone = List.copyOf(server.getPlayerList().getPlayers());
        LOG.info("Moddy Arena: ladder: {}; the core re-fits the living wished creatures of {} player(s)", why,
                everyone.size());
        for (ServerPlayer p : everyone) {
            com.moddingfromamod.world.WorldMakes.limitsChanged(p.getUUID());
        }
    }

    /**
     * A wave was cleared and a break begins: the battle's place on the ladder may have risen (a wave cleared, or a boss
     * beaten during it). If its step did, everyone in the battle is told at once, as a moment, so the break is his to use
     * the stronger wishes: a title, one or two lines saying what rose and what opened ({@link ArenaWords#ladderRose}),
     * and a fanfare. The core reads the new limits at the next make or hotbar fire, and fits the living wished creatures
     * again at once ({@link #refitWished}).
     */
    private void ladderUp() {
        ArenaLadder ladder = ArenaLadderRule.ladderOf(com.moddingfromamod.world.WorldRulesService.current());
        int now = reached(ladder);
        ArenaLadder.Rise rise = ladder.rise(ladderTold, now);
        int was = ladderTold;
        ladderTold = now;
        ladderBoard.changed();
        if (rise.isEmpty()) {
            return;
        }
        LOG.info("Moddy Arena: ladder: {} -> {} ({}): step {} -> {}; rose {}, opened {}", was, now,
                ladder.progress().id(), ladder.stepIndex(was) + 1, ladder.stepIndex(now) + 1, rise.rose().keySet(),
                rise.opened());
        for (ServerPlayer p : audience()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                    ArenaWords.ladderSubtitle(ladder.progress(), now))));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(ArenaWords.ladderTitle())));
            for (String line : ArenaWords.ladderRose(rise)) {
                p.sendSystemMessage(Component.literal(line));
            }
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        // after the arena's own lines, so the core's one line about his creatures ("stronger now") follows them
        refitWished("step " + (ladder.stepIndex(now) + 1) + " opened");
    }

    /** The id of the difficulty's change to a wave mob's most health. */
    private static final net.minecraft.resources.ResourceLocation HEALTH_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArenaMod.MODID, "difficulty_health");

    /** What makes a mob the arena's wave mob: its tag, and the rules a wave mob keeps ({@link #spawnOne}). */
    private static void markWave(Entity e) {
        e.addTag(TAG);
        if (e instanceof Mob m) {
            m.setPersistenceRequired();
            m.setCanPickUpLoot(false);
        }
        if (e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (e instanceof net.minecraft.world.entity.monster.hoglin.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
    }

    /** The difficulty's health on a wave mob (none at 1.0). */
    private static void difficultyHealth(Entity e, double health) {
        if (e instanceof LivingEntity living && health != 1.0) {
            AttributeInstance max = living.getAttribute(Attributes.MAX_HEALTH);
            if (max != null) {
                max.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, health - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                living.setHealth(living.getMaxHealth());
            }
        }
    }

    private static <T extends Entity> T spawnOne(ServerLevel level, EntityType<T> type, BlockPos at, double health) {
        Consumer<T> mark = e -> {
            e.addTag(TAG);
            if (e instanceof Mob m) {
                m.setPersistenceRequired();   // never despawned while the fighters stand far off
                // never carries off a player's dropped armour or sword: a wave mob removed (a lost battle, one that
                // left the arena) would take it with it
                m.setCanPickUpLoot(false);
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
                continue;
            }
            switch (ArenaGate.mob(place, e.getX(), e.getY(), e.getZ())) {
                case REMOVE -> {
                    LOG.info("Moddy Arena: battle: a {} left the arena at {} {} {}; removed, and counted as cleared",
                            EntityType.getKey(e.getType()), (int) e.getX(), (int) e.getY(), (int) e.getZ());
                    e.discard();
                    it.remove();
                }
                case PUT_BACK -> {
                    if (match.phase() == ArenaMatch.Phase.WAVE) {
                        int[] spot = ArenaSpawns.pick(place, playerSpots(level), 1, level.random.nextLong()).get(0);
                        LOG.info("Moddy Arena: battle: a {} was outside the arena at {} {} {}; put back inside at {} {}",
                                EntityType.getKey(e.getType()), (int) e.getX(), (int) e.getY(), (int) e.getZ(),
                                spot[0], spot[1]);
                        e.teleportTo(spot[0] + 0.5, place.floorY(), spot[1] + 0.5);
                        e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    }
                }
                case KEEP -> {
                }
            }
        }
        // a wished monster still under its pillar is a mob left: the wave is not cleared before it arrives
        return waveMobs.size() + held.size() + com.moddingfromamod.world.WorldWishes.pending(wishModule);
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
        com.moddingfromamod.world.WorldWishes.cancelAll(wishModule);   // pillars still rising: called off
        held.clear();
        return n;
    }

    /**
     * After a battle ({@link ArenaTidy#atBattleEnd}): a player's own things in the arena (what he dropped when knocked
     * out, what he threw, an arrow or trident he may pick up) are moved to the prep room, never removed; a pet someone
     * owns, a player and what carries one stay; everything else in the arena goes (its mobs, their drops, experience,
     * arrows, anything wished into it, and every creature a wish made, tamed or not); and any wave mob that reached
     * the prep room.
     */
    private int clearArena(ServerLevel level) {
        ArenaPlace.Box r = place.resetBox(level.getMaxY());
        AABB arena = new AABB(r.x0(), r.y0() - 1, r.z0(), r.x1() + 1, r.y1() + 1, r.z1() + 1);
        int[] home = place.arrival();
        int moved = 0;
        int removed = 0;
        for (Entity e : List.copyOf(level.getEntities(EntityTypeTest.forClass(Entity.class), arena, x -> true))) {
            if (e.isRemoved()) {
                continue;
            }
            switch (ArenaTidy.atBattleEnd(thing(e))) {
                case TO_PREP_ROOM -> {
                    if (toPrepRoom(level, e, home)) {
                        moved++;
                    }
                }
                case REMOVE -> {
                    e.discard();
                    removed++;
                }
                case KEEP -> {
                }
            }
        }
        if (moved > 0) {
            LOG.info("Moddy Arena: battle: {} of the players' own things moved to the prep room", moved);
        }
        playersDrops.clear();
        return removed + discard(level, placeBox(level), e -> e.getTags().contains(TAG));
    }

    /**
     * Moves one of a player's things to the prep room's arrival spot: a dropped item as it is; an arrow or trident he
     * may pick up as the item it is picked up as (it would not lie still in the air). True when it went.
     */
    private static boolean toPrepRoom(ServerLevel level, Entity e, int[] home) {
        if (e instanceof ItemEntity item) {
            item.teleportTo(home[0] + 0.5, home[1] + 0.2, home[2] + 0.5);
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            return true;
        }
        if (e instanceof AbstractArrow arrow) {
            ItemStack stack = arrow.getPickupItemStackOrigin().copy();
            arrow.discard();
            if (stack.isEmpty()) {
                return false;
            }
            ItemEntity item = new ItemEntity(level, home[0] + 0.5, home[1] + 0.2, home[2] + 0.5, stack);
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
            return true;
        }
        return false;
    }

    /** What a thing in the arena is, for {@link ArenaTidy}. */
    private ArenaTidy.Thing thing(Entity e) {
        if (e instanceof Player || e.hasPassenger(p -> p instanceof Player) || e.getTags().contains(ArenaBoard.TAG)) {
            return ArenaTidy.Thing.UNTOUCHED;
        }
        if (e instanceof ItemEntity item) {
            return playersOwn(item) ? ArenaTidy.Thing.PLAYERS_ITEM : ArenaTidy.Thing.DROP;
        }
        if (e instanceof net.minecraft.world.entity.ExperienceOrb) {
            return ArenaTidy.Thing.DROP;
        }
        if (e instanceof AbstractArrow a && a.pickup == AbstractArrow.Pickup.ALLOWED && a.getOwner() instanceof Player) {
            return ArenaTidy.Thing.PLAYERS_PICKUP;
        }
        if (looseThing(e)) {
            return ArenaTidy.Thing.LOOSE;
        }
        if (e.getTags().contains(TAG)) {
            return ArenaTidy.Thing.WAVE_MOB;
        }
        if (wished(e)) {
            return ArenaTidy.Thing.WISHED;
        }
        return owned(e) ? ArenaTidy.Thing.PET : ArenaTidy.Thing.OTHER;
    }

    /** An item a player threw, or one a player dropped when he died (or was knocked out). */
    private boolean playersOwn(ItemEntity item) {
        return playersDrops.contains(item.getUUID()) || item.getOwner() instanceof Player;
    }

    /**
     * A dropped item is a player's own (the game's events: what a player dropped when he died, or threw): kept between
     * waves and moved to the prep room when the battle is over. Server thread.
     */
    void playersItem(UUID item) {
        if (item != null) {
            playersDrops.add(item);
        }
    }

    /** A creature a player's wish made ({@link ArenaRoles#wished}). */
    private static boolean wished(Entity e) {
        return ArenaRoles.wished(com.moddingfromamod.world.Wished.isWished(e), e.getTags(), List.of(TAG, ArenaBoard.TAG, ArenaChampionsBoard.TAG,
                ArenaRoleKeeper.SIGN_TAG, ArenaLadderBoard.TAG));
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

    /** Which arena this battle is. */
    String site() {
        return site;
    }
}
