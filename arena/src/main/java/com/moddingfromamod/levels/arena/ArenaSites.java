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
        ArenaSiteRules.Site footprint(int maxY) {
            ArenaPlace.Box b = place.resetBox(maxY);
            return new ArenaSiteRules.Site(name, "minecraft:overworld", b.x0(), b.z0(), b.x1(), b.z1());
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
                ArenaPlace.Box b = l.place().resetBox(Integer.MAX_VALUE / 2);
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
                ArenaPlace.Box b = l.place().resetBox(Integer.MAX_VALUE / 2);
                return new com.moddingfromamod.world.Region(b.x0(), b.x1(), b.z0(), b.z1());
            }
        };
    }

    /** The arena world's module opened its place: it is the site {@code main} (ticked by the module, not here). */
    static void module(ArenaPlace place, ArenaBattle battle) {
        LIVE.put(ArenaSiteRules.MAIN, new Live(ArenaSiteRules.MAIN, place, battle, true, null, null, null, null));
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
        ArenaPlace sized = ArenaRule.placeOf(com.moddingfromamod.world.WorldRulesService.current());
        // he stands where a player arrives: in the prep room, facing the arena
        int[] a0 = sized.arrival();
        ArenaPlace place = sized.at(p.getBlockX() - (a0[0] - sized.centreX()), p.getBlockZ() - (a0[2] - sized.centreZ()))
                .withFloorY(p.getBlockY());
        int maxY = p.level().getMaxY();
        ArenaSiteRules.Site wanted = new ArenaSiteRules.Site(name, "minecraft:overworld",
                place.resetBox(maxY).x0(), place.resetBox(maxY).z0(), place.resetBox(maxY).x1(), place.resetBox(maxY).z1());
        List<ArenaSiteRules.Site> existing = new ArrayList<>();
        for (Live l : LIVE.values()) {
            existing.add(l.footprint(maxY));
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
     * Why the footprint may not go here because of the world's own bases (its module's: an Isle world's islands) or
     * another plug-in's region, or null. Sampled every 4 blocks across it, edges included.
     */
    static String clearOfBases(MinecraftServer s, ArenaSiteRules.Site site) {
        com.moddingfromamod.world.Bases bases = com.moddingfromamod.world.WorldModuleHost.liveBases();
        if (bases != null) {
            for (int x = site.x0(); x <= site.x1() + 3; x += 4) {
                for (int z = site.z0(); z <= site.z1() + 3; z += 4) {
                    int cx = Math.min(x, site.x1()), cz = Math.min(z, site.z1());
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

    /** {@code /moddy arena start [name]}: the named arena, else the one he stands in, else the only one. */
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
        ArenaWaves rules = baseWaves();
        ArenaConfig.Outcome o = store.config().run(sc, args,
                k -> rules == null ? 0 : ArenaConfig.rulesValue(rules, k), battlesOn(), operator(src));
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

    /** The battle timings a battle in {@code site} starts with: the rules' table with the config's layers on top. */
    static ArenaWaves configured(MinecraftServer s, ArenaWaves base, String site) {
        return s == null || base == null ? base : ArenaConfigStore.of(s).config().apply(base, site);
    }

    private static ArenaWaves baseWaves() {
        return ArenaWavesRule.wavesOf(com.moddingfromamod.world.WorldRulesService.current());
    }

    private static int fail(CommandSourceStack src, String why) {
        src.sendFailure(Component.literal(why));
        return 0;
    }
}
