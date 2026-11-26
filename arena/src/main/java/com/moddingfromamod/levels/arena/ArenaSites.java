package com.moddingfromamod.levels.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Arenas on demand</b> (Moddy-Worlds#58): battle arenas built with {@code /moddy arena create <name>} in ANY world,
 * as many as {@link ArenaSiteRules#MAX_SITES}, each a whole place (prep room, arena, panel, boards) with its own
 * battle and its own record ({@code moddyarena_place_<name>}).
 *
 * <p>A site is a FEATURE of its world, not the world: building one moves no spawn, changes no game rule and teleports
 * nobody on join. In the arena world type, the place its module built on first start is the site {@code main} and
 * keeps everything it always did; extra sites work beside it. Sites are ticked and sent their game events here, from
 * the arena mod's own listeners, routed by where the entity stands.
 *
 * <p>Each site is also a REGION of its world ({@link #REGIONS}, the core's {@code WorldRegions},
 * jivinstev/ModdyTheWishWatch#182): inside it, wishes follow the arena's rules (the battle's hotbar rule, the wish
 * ladder, the fence that keeps a wish inside), whatever world it stands in; outside it, the world's own.
 *
 * <p>This class is also the registry ({@code data/moddyarena_sites.dat}): the names of the sites built with the
 * command, in order. {@code main} is never in it; the module owns that one. Overworld only.
 */
public final class ArenaSites extends SavedData {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    static final Codec<ArenaSites> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("v", 1).forGetter(s -> 1),
            Codec.STRING.listOf().optionalFieldOf("sites", List.of()).forGetter(s -> new ArrayList<>(s.names)))
            .apply(i, (v, names) -> {
                ArenaSites s = new ArenaSites();
                s.names.addAll(names);
                return s;
            }));

    static final SavedDataType<ArenaSites> TYPE =
            new SavedDataType<>(ArenaMod.MODID + "_sites", ArenaSites::new, CODEC);

    private final List<String> names = new ArrayList<>();

    private ArenaSites() {
    }

    static ArenaSites of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ── the running sites ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A site that is running: its name, its place and its battle, and (for a site this class opened) its own charge,
     * make limits, bases and hand-over: what it answers as a region of its world (#182). The module's {@code main} has
     * none here: the module answers for its whole world itself.
     */
    record Live(String name, ArenaPlace place, ArenaBattle battle, boolean module, ArenaCharge charge,
                ArenaMakes makes, ArenaBases bases, ArenaHandOverShow handOver) {
        /** The ground it takes, prep room and base included ({@link ArenaSiteRules#claim}). */
        ArenaSiteRules.Site claim() {
            return ArenaSiteRules.claim(name, place);
        }
    }

    /** Every running site by name, the module's {@code main} included. Server thread. */
    private static final Map<String, Live> LIVE = new LinkedHashMap<>();
    private static MinecraftServer server;

    /** The server started: every site the registry names is opened, its battle ready. */
    static void started(MinecraftServer s) {
        server = s;
        ArenaSites reg = of(s);
        for (String name : List.copyOf(reg.names)) {
            ArenaState state = ArenaState.of(s, name);
            ArenaPlace place = state.built();
            if (place == null) {
                LOG.warn("Moddy Arena: the arena {} has no built place on record; it is left out", name);
                continue;
            }
            open(s, name, place, state);
        }
        if (!LIVE.isEmpty()) {
            LOG.info("Moddy Arena: sites open: {}", LIVE.keySet());
        }
    }

    private static Live open(MinecraftServer s, String name, ArenaPlace place, ArenaState state) {
        ArenaCharge charge = ArenaCharge.of(s);
        ArenaBattle battle = new ArenaBattle(s, place, state, charge, name);
        charge.battle(battle);
        charge.phase(ArenaWishPolicy.phaseOf(battle.match().phase()));
        ArenaHandOverShow handOver = new ArenaHandOverShow(s, place, state);
        ArenaMakes makes = new ArenaMakes(ArenaModule::ladder, battle::reached, battle::ladderOn, handOver::where,
                handOver::handedOver, handOver::notKept);
        ArenaBases bases = new ArenaBases(place, id -> {
            ServerPlayer p = s.getPlayerList().getPlayer(id);
            return p == null ? null : new com.moddingfromamod.world.Bases.Pos(p.level().dimension().location()
                    .toString(), p.getX(), p.getY(), p.getZ());
        });
        Live live = new Live(name, place, battle, false, charge, makes, bases, handOver);
        LIVE.put(name, live);
        snapshot();
        return live;
    }

    // ── each site is a region of its world (jivinstev/ModdyTheWishWatch#182) ──────────────────────────────────────

    /** The sites a region lookup sees: read from any thread (an agent's), replaced whole on the server thread. */
    private static volatile List<Live> regions = List.of();

    private static void snapshot() {
        List<Live> out = new ArrayList<>();
        for (Live l : LIVE.values()) {
            if (!l.module()) {
                out.add(l);
            }
        }
        regions = List.copyOf(out);
    }

    /**
     * The arena's {@code WorldRegions}: inside a site built with {@code /moddy arena create}, wishes follow that
     * arena's rules, the same as in an arena world. In a battle only hotbar wishes, each resting, so many a wave (its
     * charge); every make held to the arena's wish ladder (its makes); and a wish cast inside lands inside (its bases,
     * under the arena's own rules, which carry base_reach). Outside every site the world's own module answers, as
     * before. The arena world's own place, {@code main}, needs none: its module answers for the whole world.
     */
    static final com.moddingfromamod.world.WorldRegions REGIONS = new com.moddingfromamod.world.WorldRegions() {
        @Override
        public com.moddingfromamod.world.WorldRegion at(String dimension, double x, double y, double z) {
            if (!"minecraft:overworld".equals(dimension)) {
                return null;
            }
            for (Live l : regions) {
                // the whole place, the prep room included: the reset box stops at the shared wall, and a region that
                // stopped there left the prep room outside the arena, where nothing was held to the ladder and no
                // make was kept as a hotbar wish (a Super Wolf at attack 100 was wished there, 2026-11-26)
                ArenaPlace.Box b = l.place().footprint();
                if (x >= b.x0() && x < b.x1() + 1 && z >= b.z0() && z < b.z1() + 1) {
                    return region(l);
                }
            }
            return null;
        }

        @Override
        public boolean any() {
            return !regions.isEmpty();
        }

        @Override
        public List<com.moddingfromamod.world.WorldRegion> all() {
            List<com.moddingfromamod.world.WorldRegion> out = new ArrayList<>();
            for (Live l : regions) {
                out.add(region(l));
            }
            return out;
        }
    };

    private static com.moddingfromamod.world.WorldRegion region(Live l) {
        return new com.moddingfromamod.world.WorldRegion() {
            @Override public String id() { return ArenaMod.MODID + "/" + l.name(); }
            @Override public com.moddingfromamod.wish.WishCharge charge() { return l.charge(); }
            @Override public com.moddingfromamod.world.WorldMakes makes() { return l.makes(); }
            @Override public com.moddingfromamod.world.Bases bases() { return l.bases(); }
            @Override public com.moddingfromamod.world.WorldRules rules() { return ArenaModule.bundledWorldRules(); }
            @Override public com.moddingfromamod.world.Region area() {
                ArenaPlace.Box b = l.place().footprint();   // the prep room included, as at()
                return new com.moddingfromamod.world.Region(b.x0(), b.x1(), b.z0(), b.z1());
            }
        };
    }

    /** The arena world's module opened its place: it is the site {@code main} (ticked by the module, not here). */
    static void module(ArenaPlace place, ArenaBattle battle) {
        LIVE.put(ArenaSiteRules.MAIN, new Live(ArenaSiteRules.MAIN, place, battle, true, null, null, null, null));
    }

    /**
     * The game's respawn-position event (Moddy-Worlds#60), asked before a player is respawned: a fighter knocked out in
     * any arena's battle that is on, whose respawn is outside that place (his bed, the world spawn of a world the arena
     * is not the whole of), comes back in its prep room instead. Only this one respawn is moved: his own respawn point
     * is kept (the event copies it over as for any respawn), the world spawn never changes, and the first respawn after
     * the battle is the normal one ({@link ArenaRespawn}). Moving the respawn itself, not teleporting after it, leaves
     * no moment outside the place for the battle to count as having left it. Server thread.
     */
    static void onRespawnPosition(net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        var to = event.getTeleportTransition();
        for (Live l : List.copyOf(LIVE.values())) {
            int[] a = l.battle().respawnAt(p, event.isFromEndFight(), to.newLevel(), to.position().x,
                    to.position().y, to.position().z);
            if (a == null) {
                continue;
            }
            event.setTeleportTransition(new net.minecraft.world.level.portal.TeleportTransition(l.battle().placeLevel(),
                    new net.minecraft.world.phys.Vec3(a[0] + 0.5, a[1], a[2] + 0.5), net.minecraft.world.phys.Vec3.ZERO,
                    ArenaPlace.ARRIVAL_YAW, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            LOG.info("Moddy Arena: {} comes back in the prep room of {} (knocked out in its battle; his respawn point"
                    + " is kept)", p.getScoreboardName(), l.name());
            return;
        }
    }

    /** The module stopped. */
    static void moduleStopped() {
        LIVE.remove(ArenaSiteRules.MAIN);
    }

    static void tick() {
        for (Live l : List.copyOf(LIVE.values())) {
            if (!l.module()) {
                l.battle().tick();
                l.charge().phase(ArenaWishPolicy.phaseOf(l.battle().match().phase()));
                l.handOver().tick();
            }
        }
    }

    static void stopped() {
        for (Live l : List.copyOf(LIVE.values())) {
            if (!l.module()) {
                l.battle().stop();
                l.handOver().stop();
            }
        }
        LIVE.keySet().removeIf(n -> !LIVE.get(n).module());
        snapshot();
        server = null;
    }

    /** The battle of the site whose place holds {@code e}, or null. */
    static ArenaBattle battleAt(Entity e) {
        if (e == null || !(e.level() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return null;
        }
        for (Live l : LIVE.values()) {
            if (l.battle().inPlace(e)) {
                return l.battle();
            }
        }
        return null;
    }

    /** The names of the sites with a battle on: what the config's guard asks. */
    static Set<String> battlesOn() {
        Set<String> out = new TreeSet<>();
        for (Live l : LIVE.values()) {
            if (l.battle().match().busy()) {
                out.add(l.name());
            }
        }
        return out;
    }

    static Map<String, Live> live() {
        return LIVE;
    }

    // ── the commands ──────────────────────────────────────────────────────────────────────────────────────────────

    /** May this source build, take or move arenas: an operator (a singleplayer host with cheats on is one). */
    static boolean operator(CommandSourceStack src) {
        return src.hasPermission(2);
    }

    /** {@code /moddy arena create <name> [confirm]}: build an arena with its prep room where he stands. */
    static int create(CommandSourceStack src, String rawName, boolean confirm) {
        if (!operator(src)) {
            return fail(src, "Only an operator may build an arena (in singleplayer: a world with cheats on).");
        }
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            return fail(src, "Stand where the prep room should be and run this as a player.");
        }
        if (p.level() != src.getServer().overworld()) {
            return fail(src, "Arenas are built in the Overworld.");
        }
        String name = ArenaSiteRules.name(rawName);
        ArenaPlace sized = ArenaRule.placeOf(ArenaModule.rules());
        // he stands where a player arrives: in the prep room, facing the arena
        int[] a0 = sized.arrival();
        ArenaPlace place = sized.at(p.getBlockX() - (a0[0] - sized.centreX()), p.getBlockZ() - (a0[2] - sized.centreZ()))
                .withFloorY(p.getBlockY());
        // the whole place, prep room and base included (Moddy-Worlds#62): never only the arena's reset box
        ArenaSiteRules.Site wanted = ArenaSiteRules.claim(name, place);
        List<ArenaSiteRules.Site> existing = new ArrayList<>();
        for (Live l : LIVE.values()) {
            existing.add(l.claim());
        }
        String why = ArenaSiteRules.problem(wanted, existing);
        if (why != null) {
            return fail(src, why);
        }
        // never over someone's base (an Isle world's island, any module's bases), nor another world feature's region
        why = clearOfBases(src.getServer(), wanted);
        if (why != null) {
            return fail(src, why);
        }
        // never over a player's things, another player, out of the world, or on ground the game has not loaded
        why = groundProblem((ServerLevel) p.level(), place, wanted, p);
        if (why != null) {
            return fail(src, why);
        }
        int wide = wanted.x1() - wanted.x0() + 1, deep = wanted.z1() - wanted.z0() + 1;
        if (!confirm) {
            src.sendSuccess(() -> Component.literal("This builds the arena " + name + " here: " + wide + " by " + deep
                    + " blocks, with its prep room where you stand and the arena to the north. Everything inside it is"
                    + " replaced. To build it, run /moddy arena create " + name + " confirm"), false);
            return 1;
        }
        long t0 = System.nanoTime();
        ServerLevel level = src.getServer().overworld();
        ArenaState state = ArenaState.of(src.getServer(), name);
        int set = ArenaBuilder.build(level, place);
        state.markBuilt(place);
        for (String u : List.of(ArenaPlace.UPGRADE_START_CONTROL, ArenaPlace.UPGRADE_CONTROL_PANEL,
                ArenaPlace.UPGRADE_CHAMPIONS, ArenaPlace.UPGRADE_BASE, ArenaPlace.UPGRADE_LADDER_BOARD,
                ArenaPlace.UPGRADE_HAND_OVER_SPOT, ArenaPlace.UPGRADE_NO_NATURAL_SPAWNS)) {
            state.markUpgraded(u);   // built whole; and a site never changes the world's game rules
        }
        ArenaSites reg = of(src.getServer());
        reg.names.add(name);
        reg.setDirty();
        open(src.getServer(), name, place, state);
        int[] a = place.arrival();
        p.teleportTo(level, a[0] + 0.5, a[1], a[2] + 0.5, Set.of(), ArenaPlace.ARRIVAL_YAW, 0f, true);
        LOG.info("Moddy Arena: {} built the arena {} ({}): {} blocks in {} ms", p.getScoreboardName(), name,
                place.describe(), set, (System.nanoTime() - t0) / 1_000_000);
        src.sendSuccess(() -> Component.literal("Built the arena " + name + ". You are in its prep room: set the"
                + " difficulty on the panel and press START BATTLE, or run /moddy arena start " + name + "."), true);
        return 1;
    }

    /**
     * Why the ground {@code site} takes may not be built on, or null: the place must fit between the world's bottom and
     * top and inside its border, every chunk under it must be loaded (nothing is built on ground the game has not
     * loaded), no block entity may stand anywhere in it from the base's bedrock to the sky (a chest, a bed, a sign, a
     * furnace, a spawner: a player's things, or a structure's), and no other player may be in it. Server thread.
     */
    static String groundProblem(ServerLevel level, ArenaPlace place, ArenaSiteRules.Site site, ServerPlayer builder) {
        String why = ArenaSiteRules.heightProblem(place.bedrockY(), place.footprint().y1(), level.getMinY(),
                level.getMaxY());
        if (why != null) {
            return why;
        }
        var border = level.getWorldBorder();
        if (!border.isWithinBounds(site.x0(), site.z0()) || !border.isWithinBounds(site.x1() + 1, site.z1() + 1)) {
            return "That would go past the world border. Move further in and try again.";
        }
        for (int cx = site.x0() >> 4; cx <= site.x1() >> 4; cx++) {
            for (int cz = site.z0() >> 4; cz <= site.z1() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return "Part of that ground is not loaded (round " + (cx * 16 + 8) + " " + (cz * 16 + 8)
                            + "). Go closer to the middle of where the arena goes, or let it load, and try again.";
                }
            }
        }
        for (int cx = site.x0() >> 4; cx <= site.x1() >> 4; cx++) {
            for (int cz = site.z0() >> 4; cz <= site.z1() >> 4; cz++) {
                for (var e : level.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    net.minecraft.core.BlockPos at = e.getKey();
                    if (at.getX() >= site.x0() && at.getX() <= site.x1() && at.getZ() >= site.z0()
                            && at.getZ() <= site.z1() && at.getY() >= place.bedrockY()) {
                        String what = level.getBlockState(at).getBlock().getName().getString();
                        return ArenaSiteRules.inTheWay("a " + what.toLowerCase(java.util.Locale.ROOT), at.getX(),
                                at.getY(), at.getZ());
                    }
                }
            }
        }
        for (ServerPlayer other : level.players()) {
            if (other != builder && other.getX() >= site.x0() && other.getX() < site.x1() + 1
                    && other.getZ() >= site.z0() && other.getZ() < site.z1() + 1) {
                return ArenaSiteRules.inTheWay(other.getScoreboardName(), other.getBlockX(), other.getBlockY(),
                        other.getBlockZ());
            }
        }
        return null;
    }

    /**
     * Why the footprint may not go here because of the world's own bases (its module's: an Isle world's islands) or
     * another plug-in's region, or null. Every block of it is asked.
     */
    static String clearOfBases(MinecraftServer s, ArenaSiteRules.Site site) {
        com.moddingfromamod.world.Bases bases = com.moddingfromamod.world.WorldModuleHost.liveBases();
        if (bases != null) {
            for (int cx = site.x0(); cx <= site.x1(); cx++) {
                for (int cz = site.z0(); cz <= site.z1(); cz++) {
                    java.util.UUID owner = bases.ownerAt("minecraft:overworld", cx + 0.5, cz + 0.5);
                    if (owner != null) {
                        ServerPlayer who = s.getPlayerList().getPlayer(owner);
                        String name = who != null ? who.getScoreboardName() + "'s" : "someone's";
                        return "That would cover " + name + " " + bases.noun() + " (at " + cx + " " + cz
                                + "). Build the arena somewhere clear.";
                    }
                }
            }
        }
        com.moddingfromamod.world.Region area = new com.moddingfromamod.world.Region(site.x0(), site.x1(), site.z0(),
                site.z1());
        for (com.moddingfromamod.world.WorldRegion r : otherRegions()) {
            com.moddingfromamod.world.Region a = r.area();
            if (a != null && a.minX() <= area.maxX() && area.minX() <= a.maxX() && a.minZ() <= area.maxZ()
                    && area.minZ() <= a.maxZ()) {
                return "That would cover " + r.id() + ". Build the arena somewhere clear.";
            }
        }
        return null;
    }

    /** Every other plug-in's region that says where it is (not the arena's own sites, which the site rules check). */
    private static List<com.moddingfromamod.world.WorldRegion> otherRegions() {
        List<com.moddingfromamod.world.WorldRegion> out = new ArrayList<>();
        for (com.moddingfromamod.world.WorldRegions w : com.moddingfromamod.plugin.Plugins.shared()
                .all(com.moddingfromamod.world.WorldRegions.class)) {
            if (w != REGIONS) {
                out.addAll(w.all());
            }
        }
        return out;
    }

    /** {@code /moddy arena list}. */
    static int list(CommandSourceStack src) {
        if (LIVE.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No arenas in this world yet. An operator can build one with"
                    + " /moddy arena create <name>."), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("Arenas in this world:");
        for (Live l : LIVE.values()) {
            int[] a = l.place().arrival();
            sb.append("\n  ").append(l.name()).append(": prep room at ").append(a[0]).append(' ').append(a[1])
                    .append(' ').append(a[2]).append(l.battle().match().busy() ? "  (a battle is on)" : "");
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return LIVE.size();
    }

    /** {@code /moddy arena go <name>}: to its prep room. */
    static int go(CommandSourceStack src, String rawName) {
        if (!operator(src)) {
            return fail(src, "Only an operator may move between arenas with this.");
        }
        Live l = LIVE.get(ArenaSiteRules.name(rawName));
        ServerPlayer p = src.getPlayer();
        if (l == null || p == null) {
            return fail(src, l == null ? "There is no arena called " + rawName + ". /moddy arena list shows them."
                    : "Run this as a player.");
        }
        int[] a = l.place().arrival();
        p.teleportTo(src.getServer().overworld(), a[0] + 0.5, a[1], a[2] + 0.5, Set.of(), ArenaPlace.ARRIVAL_YAW, 0f,
                true);
        return 1;
    }

    /** {@code /moddy arena remove <name> confirm}: its battle ends and it is forgotten; its blocks stay standing. */
    static int remove(CommandSourceStack src, String rawName, boolean confirm) {
        if (!operator(src)) {
            return fail(src, "Only an operator may remove an arena.");
        }
        String name = ArenaSiteRules.name(rawName);
        Live l = name == null ? null : LIVE.get(name);
        if (l == null) {
            return fail(src, "There is no arena called " + rawName + ". /moddy arena list shows them.");
        }
        if (l.module()) {
            return fail(src, "The arena " + ArenaSiteRules.MAIN + " is this arena world's own; it cannot be removed.");
        }
        if (!confirm) {
            src.sendSuccess(() -> Component.literal("This forgets the arena " + name + " and ends any battle in it. Its"
                    + " blocks stay standing. To do it, run /moddy arena remove " + name + " confirm"), false);
            return 1;
        }
        l.battle().stop();
        l.handOver().stop();
        LIVE.remove(name);
        snapshot();
        ArenaSites reg = of(src.getServer());
        reg.names.remove(name);
        reg.setDirty();
        ArenaConfigStore cfg = ArenaConfigStore.of(src.getServer());
        cfg.set(cfg.config().forget(name));
        LOG.info("Moddy Arena: the arena {} was removed (its blocks left standing)", name);
        src.sendSuccess(() -> Component.literal("The arena " + name + " is gone from the list. Its blocks are still"
                + " there."), true);
        return 1;
    }

    /**
     * {@code /moddy arena start [name]}: the named arena, else the one he stands in, else the only one. A player who is
     * not an operator starts only the arena he stands in: a battle takes whoever is in the place, so nobody may start
     * one on others from afar.
     */
    static int start(CommandSourceStack src, String rawName) {
        Live l;
        if (rawName != null) {
            l = LIVE.get(ArenaSiteRules.name(rawName));
            if (l == null) {
                return fail(src, "There is no arena called " + rawName + ". /moddy arena list shows them.");
            }
        } else {
            ArenaBattle here = battleAt(src.getEntity());
            l = null;
            for (Live x : LIVE.values()) {
                if (x.battle() == here) {
                    l = x;
                }
            }
            if (l == null && LIVE.size() == 1) {
                l = LIVE.values().iterator().next();
            }
            if (l == null) {
                return fail(src, LIVE.isEmpty() ? ArenaWords.notAnArena()
                        : "Stand in an arena's prep room, or name one: /moddy arena start <name>.");
            }
        }
        // a battle takes whoever stands in the place: only someone there (or an operator) may start one
        if (!operator(src) && battleAt(src.getEntity()) != l.battle()) {
            return fail(src, "Stand in the arena " + l.name() + " (its prep room or the arena) to start a battle there.");
        }
        return l.battle().startFromCommand(src);
    }

    /** {@code /moddy arena config [scope [key [value|reset]]]} (Moddy-Worlds#59). */
    static int config(CommandSourceStack src, String scope, List<String> args) {
        String sc = scope == null ? ArenaConfig.GLOBAL : scope.strip().toLowerCase(java.util.Locale.ROOT);
        if (!ArenaConfig.GLOBAL.equals(sc) && !LIVE.containsKey(sc)) {
            return fail(src, "There is no arena called " + scope + ". Use 'global' for all arenas, or one of: "
                    + String.join(", ", LIVE.keySet()) + ".");
        }
        ArenaConfigStore store = ArenaConfigStore.of(src.getServer());
        ArenaConfig.Outcome o = store.config().run(sc, args, baseWaves(), battlesOn(), operator(src));
        if (o.refused()) {
            return fail(src, o.words());
        }
        if (o.changed()) {
            store.set(o.config());
            LOG.info("Moddy Arena: config: {} set {} ({})", src.getTextName(), args, sc);
        }
        src.sendSuccess(() -> Component.literal(o.words()), o.changed());
        return 1;
    }

    private static ArenaWaves baseWaves() {
        return ArenaWavesRule.wavesOf(ArenaModule.rules());
    }

    private static int fail(CommandSourceStack src, String why) {
        src.sendFailure(Component.literal(why));
        return 0;
    }
}
