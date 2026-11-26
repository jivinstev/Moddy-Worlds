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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
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
    /** What may be made here, and how strong: the unlock ladder ({@link ArenaMakes}). */
    private volatile ArenaMakes makes;
    /** The prep room's hand-over: where a make is finished, and showing what was handed over ({@link ArenaHandOver}). */
    private volatile ArenaHandOverShow handOver;
    /** Players who arrived for the first time, and the tick until which the core's guide book is looked for. */
    private final Map<UUID, Long> guideDue = new HashMap<>();
    /** The wished creatures whose hurt to a player was stopped, so the log says it once for each. */
    private final Set<UUID> spared = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return ID;
    }

    /**
     * The game's events (registered by {@link ArenaMod}, on {@code NeoForge.EVENT_BUS}): the items a player drops when
     * he dies (or is knocked out) are his own, so the arena keeps them between waves and moves them to the prep room
     * at a battle's end, never removes them. Only in an arena world, while its module runs. Server thread.
     */
    static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        ArenaBattle b = battleFor(event.getEntity());
        if (b == null || !(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        for (net.minecraft.world.entity.item.ItemEntity item : event.getDrops()) {
            b.playersItem(item.getUUID());
        }
    }

    /**
     * The game's damage event: a creature a wish made never hurts a player in the place ({@link ArenaSide}). What is
     * traced: the creature itself, and anything the game records it as the owner of (an arrow, a fireball, a trident,
     * evoker fangs, a potion cloud, lit TNT it set off: the damage's cause, or the thing that hit). Not traced: damage
     * the game gives no source for (a fall, fire or lava it caused, a blast nobody owns, a block it changed, a command a
     * datapack power runs), which a player takes as from anything else.
     */
    static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        ArenaBattle b = battleFor(event.getEntity());
        if (b == null || !(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        var source = event.getSource();
        if (ArenaSide.spare(true, b.inPlace(p), wishMade(source.getEntity()), wishMade(source.getDirectEntity()))) {
            event.setCanceled(true);
            net.minecraft.world.entity.Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            if (by != null && SPARED.add(by.getUUID())) {
                LOG.info("Moddy Arena: a wished {} would have hurt {}; stopped (a wished creature is on the players' side"
                        + " here)", net.minecraft.world.entity.EntityType.getKey(by.getType()), p.getScoreboardName());
            }
        }
    }

    /**
     * The game's event for a creature choosing a target: a creature a wish made, in the place, never picks a player
     * ({@link ArenaSide}); the role keeper's pass lets go of one it still holds.
     */
    static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        ArenaBattle b = battleFor(event.getEntity());
        if (b == null || !(event.getNewAboutToBeSetTarget() instanceof net.minecraft.world.entity.player.Player)) {
            return;
        }
        if (ArenaSide.dropTarget(com.moddingfromamod.world.Wished.isWished(event.getEntity()),
                b.inPlace(event.getEntity()), true)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** The battle of the arena {@code e} stands in (any site), else the arena world's own (its old behaviour), or null. */
    private static ArenaBattle battleFor(net.minecraft.world.entity.Entity e) {
        ArenaBattle here = ArenaSites.battleAt(e);
        if (here != null) {
            return here;
        }
        ArenaModule m = live;
        return m == null ? null : m.battle;
    }

    /** The wished creatures whose hurt to a player was stopped, across every arena, so the log says it once for each. */
    private static final Set<UUID> SPARED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** True for an entity a wish made, or one whose owner a wish made (what such a creature shot or threw). */
    private static boolean wishMade(net.minecraft.world.entity.Entity e) {
        if (e == null) {
            return false;
        }
        if (com.moddingfromamod.world.Wished.isWished(e)) {
            return true;
        }
        return e instanceof net.minecraft.world.entity.TraceableEntity t && t.getOwner() != null
                && com.moddingfromamod.world.Wished.isWished(t.getOwner());
    }

    /** The game's event for an item a player throws (Q, or out of his inventory): his own, as {@link #onDrops}. */
    static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        ArenaBattle b = battleFor(event.getPlayer());
        if (b != null && event.getPlayer() instanceof ServerPlayer) {
            b.playersItem(event.getEntity().getUUID());
        }
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
            state.markUpgraded(ArenaPlace.UPGRADE_CHAMPIONS);
            state.markUpgraded(ArenaPlace.UPGRADE_BASE);
            state.markUpgraded(ArenaPlace.UPGRADE_LADDER_BOARD);
            state.markUpgraded(ArenaPlace.UPGRADE_HAND_OVER_SPOT);
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
        LOG.info("Moddy Arena: control panel: difficulty {}, waves {}{}; {} champion entries",
                state.settings().difficulty().word(),
                ArenaWords.wavesValue(state.settings().waves()), state.readVersion() < ArenaState.VERSION
                        ? " (this world was saved by an older arena: what it lacks is the default)" : "",
                state.champions().size());
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
        battle = new ArenaBattle(server, place, state, c);
        c.phase(ArenaWishPolicy.phaseOf(battle.match().phase()));
        charge = c;
        c.battle(battle);
        ArenaBattle b0 = battle;
        ArenaHandOverShow h = new ArenaHandOverShow(server, place, state);
        handOver = h;
        makes = new ArenaMakes(ArenaModule::ladder, b0::reached, b0::ladderOn, h::where, h::handedOver, h::notKept);
        ArenaLadder ladder = ArenaLadderRule.ladderOf(rulesAtStart());
        LOG.info("Moddy Arena: wish ladder: {} steps, counted in {}, locked {} {}; every battle starts at the first",
                ladder.steps().size(), ladder.progress().id(), ladder.locked().kinds(), ladder.locked().tags());
        ArenaWishes w = ArenaWishesRule.wishesOf(rulesAtStart());
        LOG.info("Moddy Arena: wishes: the arena's wish charge is in force (in prep, {} Magician wishes a player;"
                + " in a battle, hotbar wishes only, each resting {} s)", w.magicianWishes(), w.cooldownSeconds());
        live = this;
        ArenaSites.module(place, battle);   // this world's own arena is the site main (Moddy-Worlds#58)
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
                case ArenaPlace.UPGRADE_CHAMPIONS -> {
                    int set = ArenaBuilder.addChampions(level, place);
                    if (set < 0) {
                        LOG.warn("Moddy Arena: the place has no Champions board, and something stands where it goes"
                                + " (the prep room's back wall); it is not added.");
                        continue;
                    }
                    LOG.info("Moddy Arena: the place was missing its Champions board; added it on the prep room's"
                            + " back wall ({} blocks)", set);
                }
                case ArenaPlace.UPGRADE_BASE -> {
                    int set = ArenaBuilder.addBase(level, place);
                    LOG.info("Moddy Arena: the place stood in the sky; put a base under it ({} blocks: bedrock, a layer"
                            + " of stone, ground round the floors), so nobody falls out of it", set);
                }
                case ArenaPlace.UPGRADE_LADDER_BOARD -> {
                    int set = ArenaBuilder.addLadderBoard(level, place);
                    if (set < 0) {
                        LOG.warn("Moddy Arena: the place has no wish ladder board, and something stands where it goes"
                                + " (the prep room's west wall beside the panel); its words float there on their own.");
                        continue;
                    }
                    LOG.info("Moddy Arena: the place was missing its wish ladder board; added it on the prep room's"
                            + " west wall beside the panel ({} blocks)", set);
                }
                case ArenaPlace.UPGRADE_HAND_OVER_SPOT -> {
                    int set = ArenaBuilder.addHandOverSpot(level, place);
                    if (set < 0) {
                        LOG.warn("Moddy Arena: the place has no hand-over spot, and something stands where it goes"
                                + " (the prep room's floor by its east wall); a finished make shows there all the same.");
                        continue;
                    }
                    LOG.info("Moddy Arena: the place was missing its hand-over spot; set it in the prep room's floor by"
                            + " its east wall ({} block)", set);
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
        if (ArenaBuilder.keepSpawnFloor(level, place)) {
            LOG.info("Moddy Arena: the floor under the world spawn was missing; put back");
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
            ArenaSites.moduleStopped();
        }
        ArenaHandOverShow h = handOver;
        if (h != null) {
            h.stop();
        }
        handOver = null;
        battle = null;
        charge = null;
        makes = null;
        bases = null;
        server = null;
    }

    @Override
    public void onTick() {
        if (!guideDue.isEmpty()) {
            shelveGuides();
        }
        ArenaBattle b = battle;
        if (b != null) {
            b.tick();
            ArenaCharge c = charge;
            if (c != null) {
                c.phase(ArenaWishPolicy.phaseOf(b.match().phase()));
            }
        }
        ArenaHandOverShow h = handOver;
        if (h != null) {
            h.tick();
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

    /**
     * What may be made in an arena world, and how strong ({@link ArenaMakes}): the limits that rise as bosses fall, and
     * the kinds the ladder keeps for later. Null before the module starts and after it stops (nothing is limited then).
     */
    @Override
    public com.moddingfromamod.world.WorldMakes makes() {
        return makes;
    }

    /** The unlock ladder in force: the world's {@code arena_ladder} rule, or the default. */
    static ArenaLadder ladder() {
        return ArenaLadderRule.ladderOf(WorldRulesService.current());
    }

    @Override
    public String keepTicking() {
        ArenaBattle b = battle;
        return b == null ? null : b.keepTicking();
    }

    /** {@code /moddy arena start}: starts a battle in an arena world; says why not anywhere else. */
    static int startCommand(net.minecraft.commands.CommandSourceStack source) {
        if (!ArenaSites.live().isEmpty()) {
            return ArenaSites.start(source, null);
        }
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
        guideDue.put(player.getUUID(), (long) s.getTickCount() + ArenaGuide.SHELVE_TICKS);
        LOG.info("Moddy Arena: {} arrived in the prep room", player.getScoreboardName());
    }

    /**
     * The core gives the Arena Guide on a first arrival, into the first free slot, which is on the hotbar. Once, for the
     * copy given then, the arena moves it into his bag ({@link ArenaGuide#bagSlot}): the hotbar is for the wishes he
     * fights with. A copy he asks for later, or moves back himself, is left where it is.
     */
    private void shelveGuides() {
        MinecraftServer s = server;
        if (s == null) {
            guideDue.clear();
            return;
        }
        long now = s.getTickCount();
        for (Iterator<Map.Entry<UUID, Long>> it = guideDue.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            if (p == null || now > e.getValue()) {
                it.remove();
                continue;
            }
            var inv = p.getInventory();
            int book = -1;
            for (int i = 0; i < ArenaGuide.HOTBAR; i++) {
                if (isGuide(inv.getItem(i))) {
                    book = i;
                    break;
                }
            }
            if (book < 0) {
                continue;
            }
            boolean[] empty = new boolean[ArenaGuide.BAG_END];
            for (int i = 0; i < empty.length; i++) {
                empty[i] = inv.getItem(i).isEmpty();
            }
            int to = ArenaGuide.bagSlot(book, empty);
            if (to >= 0) {
                inv.setItem(to, inv.getItem(book));
                inv.setItem(book, ItemStack.EMPTY);
                p.containerMenu.broadcastChanges();
                LOG.info("Moddy Arena: {}'s Arena Guide went from hotbar slot {} into his bag", p.getScoreboardName(),
                        book + 1);
            }
            it.remove();
        }
    }

    private static boolean isGuide(ItemStack st) {
        if (!st.is(Items.WRITTEN_BOOK)) {
            return false;
        }
        WrittenBookContent c = st.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return c != null && ArenaGuide.TITLE.equals(c.title().raw()) && ArenaGuide.AUTHOR.equals(c.author());
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

    private static volatile WorldRules bundledParsed;

    /** The arena's own rules (this jar's), parsed once: what a site judges reach by as a region (#182). */
    static WorldRules bundledWorldRules() {
        WorldRules r = bundledParsed;
        if (r == null) {
            r = parse(bundledRules());
            bundledParsed = r;
        }
        return r;
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
