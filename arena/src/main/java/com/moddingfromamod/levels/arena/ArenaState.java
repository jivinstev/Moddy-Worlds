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
 * gains its start control once), and whether a battle was on (so a restart in the middle of one ends it and resets the
 * arena, rather than leaving a half battle).
 */
public final class ArenaState extends SavedData {

    /** The built sizes: arenaSize, prepSize, wallHeight, floorY. */
    private record Built(int arenaSize, int prepSize, int wallHeight, int floorY) {
        static final Codec<Built> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("arenaSize").forGetter(Built::arenaSize),
                Codec.INT.fieldOf("prepSize").forGetter(Built::prepSize),
                Codec.INT.fieldOf("wallHeight").forGetter(Built::wallHeight),
                Codec.INT.fieldOf("floorY").forGetter(Built::floorY)).apply(i, Built::new));
    }

    static final Codec<ArenaState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Built.CODEC.optionalFieldOf("built").forGetter(s -> Optional.ofNullable(s.built)),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("arrived", List.of())
                    .forGetter(s -> new ArrayList<>(s.arrived)),
            Codec.STRING.listOf().optionalFieldOf("upgrades", List.of())
                    .forGetter(s -> new ArrayList<>(s.upgrades)),
            Codec.BOOL.optionalFieldOf("battleOn", false).forGetter(s -> s.battleOn)).apply(i, ArenaState::new));

    static final SavedDataType<ArenaState> TYPE =
            new SavedDataType<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(ArenaMod.MODID, "place"), ArenaState::new, CODEC);

    private Built built;
    private final Set<UUID> arrived = new LinkedHashSet<>();
    private final Set<String> upgrades = new LinkedHashSet<>();
    private boolean battleOn;

    private ArenaState() {
    }

    private ArenaState(Optional<Built> built, List<UUID> arrived, List<String> upgrades, boolean battleOn) {
        this.built = built.orElse(null);
        this.arrived.addAll(arrived);
        this.upgrades.addAll(upgrades);
        this.battleOn = battleOn;
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
            return new ArenaPlace(built.arenaSize, built.prepSize, built.wallHeight, built.floorY);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    void markBuilt(ArenaPlace p) {
        built = new Built(p.arenaSize(), p.prepSize(), p.wallHeight(), p.floorY());
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
}
