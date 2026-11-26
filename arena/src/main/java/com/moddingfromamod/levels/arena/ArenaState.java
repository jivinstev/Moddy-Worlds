package com.moddingfromamod.levels.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * <b>What this world remembers about its arena</b> ({@code data/moddyarena_place.dat}): the sizes the place was built
 * with (none until it is built, so a restart builds nothing), who has already arrived (so only a first join is
 * put in the prep room; a returning player stays where he left), which upgrades the place has had (a step-1 place
 * gains its start control once), whether a battle was on (so a restart in the middle of one ends it and resets the
 * arena, rather than leaving a half battle), what the control panel is set to (the difficulty and the number of waves),
 * and the champions ({@link ArenaChampions}: each player's id, name and best run at each
 * settings, nothing else).
 *
 * <p>Versioned: {@code v} is {@value #VERSION}. Version 2 added the panel's size and difficulty, version 3 the number
 * of waves ({@code waveCount}, 0 for Endless) and the champions. Version 4 (0.1.6) dropped the panel's monsters size (the
 * difficulty brings its own): an older record's {@code waveSize} is not read, and its champions, which were kept by
 * difficulty, waves AND size, are kept by difficulty and waves, each player's best at each ({@link
 * ArenaChampions#migrate}). A record without them reads them as the defaults (5 waves, no champions), and everything
 * else in it is kept. A saved difficulty this jar does not know reads as the default too, and the log says so.
 * Version 5 added who has been told the prep room's hand-over ({@code handOverTold}, {@link ArenaHandOver}): a record
 * without it reads as nobody told yet.
 */
public final class ArenaState extends SavedData {

    /**
     * The built sizes: arenaSize, prepSize, wallHeight, floorY, and the arena's centre (ox, oz). A record saved before
     * a place could stand anywhere has no ox/oz and reads as (0, 0); a place at (0, 0) saves none, as before.
     */
    private record Built(int arenaSize, int prepSize, int wallHeight, int floorY, int ox, int oz) {
        static final Codec<Built> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("arenaSize").forGetter(Built::arenaSize),
                Codec.INT.fieldOf("prepSize").forGetter(Built::prepSize),
                Codec.INT.fieldOf("wallHeight").forGetter(Built::wallHeight),
                Codec.INT.fieldOf("floorY").forGetter(Built::floorY),
                Codec.INT.optionalFieldOf("ox", 0).forGetter(Built::ox),
                Codec.INT.optionalFieldOf("oz", 0).forGetter(Built::oz)).apply(i, Built::new));
    }

    /** One champion entry, as saved. */
    private static final Codec<ArenaChampions.Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ArenaChampions.Entry::id),
            Codec.STRING.fieldOf("name").forGetter(ArenaChampions.Entry::name),
            Codec.STRING.fieldOf("difficulty").forGetter(e -> e.key().difficulty()),
            Codec.INT.fieldOf("waves").forGetter(e -> e.key().waves()),
            Codec.INT.fieldOf("wave").forGetter(ArenaChampions.Entry::wave),
            Codec.BOOL.optionalFieldOf("cleared", false).forGetter(ArenaChampions.Entry::cleared),
            Codec.BOOL.optionalFieldOf("won", false).forGetter(ArenaChampions.Entry::won),
            Codec.LONG.fieldOf("ticks").forGetter(ArenaChampions.Entry::ticks),
            Codec.LONG.optionalFieldOf("seq", 0L).forGetter(ArenaChampions.Entry::seq))
            .apply(i, (id, name, d, w, wave, cleared, won, ticks, seq) -> new ArenaChampions.Entry(id, name,
                    new ArenaChampions.Key(d, w), wave, cleared, won, ticks, seq)));

    /**
     * This record's version: 2 added the panel's settings, 3 the number of waves and the champions, 4 dropped the
     * monsters size (from the settings and the champions' keys), 5 added who has been told the hand-over.
     */
    static final int VERSION = 5;

    static final Codec<ArenaState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("v", 1).forGetter(s -> VERSION),
            Built.CODEC.optionalFieldOf("built").forGetter(s -> Optional.ofNullable(s.built)),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("arrived", List.of())
                    .forGetter(s -> new ArrayList<>(s.arrived)),
            Codec.STRING.listOf().optionalFieldOf("upgrades", List.of())
                    .forGetter(s -> new ArrayList<>(s.upgrades)),
            Codec.BOOL.optionalFieldOf("battleOn", false).forGetter(s -> s.battleOn),
            Codec.STRING.optionalFieldOf("difficulty").forGetter(s -> Optional.of(s.settings.difficulty().id())),
            Codec.INT.optionalFieldOf("waveCount").forGetter(s -> Optional.of(s.settings.waves())),
            ENTRY.listOf().optionalFieldOf("champions", List.of()).forGetter(s -> s.champions),
            Codec.LONG.optionalFieldOf("runs", 0L).forGetter(s -> s.runs),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("handOverTold", List.of())
                    .forGetter(s -> new ArrayList<>(s.handOverTold)))
            .apply(i, ArenaState::new));

    static final SavedDataType<ArenaState> TYPE =
            new SavedDataType<>(ArenaMod.MODID + "_place", ArenaState::new, CODEC);

    private Built built;
    private final Set<UUID> arrived = new LinkedHashSet<>();
    private final Set<String> upgrades = new LinkedHashSet<>();
    /** Who has read the hand-over's longer line (the difference between a wish and an item). */
    private final Set<UUID> handOverTold = new LinkedHashSet<>();
    private boolean battleOn;
    private ArenaSettings settings = ArenaSettings.DEFAULT;
    /** The version this record was read as (1 for one saved before the panel), for the log. */
    private int readVersion = VERSION;
    /** A saved difficulty this jar does not know, or null. */
    private String unknownDifficulty;
    private List<ArenaChampions.Entry> champions = List.of();
    /** How many runs have been recorded (each run's sequence number, for ties). */
    private long runs;

    private ArenaState() {
    }

    private ArenaState(int v, Optional<Built> built, List<UUID> arrived, List<String> upgrades, boolean battleOn,
                       Optional<String> difficulty, Optional<Integer> waveCount,
                       List<ArenaChampions.Entry> champions, long runs, List<UUID> handOverTold) {
        this.readVersion = v;
        this.built = built.orElse(null);
        this.arrived.addAll(arrived);
        this.upgrades.addAll(upgrades);
        this.battleOn = battleOn;
        this.settings = ArenaSettings.of(difficulty.orElse(null), waveCount.orElse(null));
        this.champions = List.copyOf(v < 4 ? ArenaChampions.migrate(champions) : champions);
        this.runs = runs;
        this.handOverTold.addAll(handOverTold);
        if (difficulty.isPresent() && ArenaDifficulty.byId(difficulty.get()) == null) {
            unknownDifficulty = difficulty.get();
        }
    }

    /** The version this record was saved as (1: before the control panel). */
    int readVersion() {
        return readVersion;
    }

    /** A saved difficulty this jar does not know (then the default is used), or null. */
    String unknownDifficulty() {
        return unknownDifficulty;
    }

    /** What the control panel is set to (the defaults in a world saved before it). */
    ArenaSettings settings() {
        return settings;
    }

    void settings(ArenaSettings s) {
        if (s != null && !s.equals(settings)) {
            settings = s;
            setDirty();
        }
    }

    /** Every champion entry (each player's best at each settings). */
    List<ArenaChampions.Entry> champions() {
        return champions;
    }

    /**
     * Records one player's run at these settings ({@link ArenaChampions#merge}); returns his rank at them afterwards
     * (1 is the top), or 0 when his run reached no wave.
     */
    int record(String id, String name, ArenaChampions.Key key, ArenaChampions.Reach r) {
        if (r == null || r.wave() <= 0) {
            return 0;
        }
        runs++;
        champions = List.copyOf(ArenaChampions.merge(champions, new ArenaChampions.Entry(id, name, key, r.wave(),
                r.cleared(), r.won(), r.ticks(), runs)));
        setDirty();
        List<ArenaChampions.Entry> at = ArenaChampions.at(champions, key);
        for (int i = 0; i < at.size(); i++) {
            if (at.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The upgrades this world's place has had ({@link ArenaPlace#UPGRADES}). */
    Set<String> upgrades() {
        return Set.copyOf(upgrades);
    }

    void markUpgraded(String upgrade) {
        if (upgrades.add(upgrade)) {
            setDirty();
        }
    }

    /** True when a battle was on when this record was last saved. */
    boolean battleOn() {
        return battleOn;
    }

    void battleOn(boolean on) {
        if (battleOn != on) {
            battleOn = on;
            setDirty();
        }
    }

    /** This world's record (made empty on first use). Server thread. */
    static ArenaState of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The place as it was built here, or null when nothing has been built yet (or the record is unreadable). */
    ArenaPlace built() {
        if (built == null) {
            return null;
        }
        try {
            return new ArenaPlace(built.arenaSize, built.prepSize, built.wallHeight, built.floorY, built.ox, built.oz);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    void markBuilt(ArenaPlace p) {
        built = new Built(p.arenaSize(), p.prepSize(), p.wallHeight(), p.floorY(), p.ox(), p.oz());
        setDirty();
    }

    /** Records his first arrival; true when it was his first. */
    boolean arrive(UUID player) {
        boolean first = arrived.add(player);
        if (first) {
            setDirty();
        }
        return first;
    }

    /** Records that he was told the hand-over's longer line; true the first time (say it then, and only then). */
    boolean tellHandOver(UUID player) {
        boolean first = handOverTold.add(player);
        if (first) {
            setDirty();
        }
        return first;
    }
}
