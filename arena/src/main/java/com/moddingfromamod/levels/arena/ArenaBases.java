package com.moddingfromamod.levels.arena;

import com.moddingfromamod.world.Base;
import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.Region;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

/**
 * <b>The arena is every player's base</b>, so the core's {@code base_reach} rule keeps a wish inside it: a wish is
 * aimed inside the arena ({@link ArenaPlace#reach}), and nothing it does reaches past the place, prep room included
 * ({@link ArenaPlace#area}). Home (a respawn, a "send him home") is the arrival spot in the prep room.
 *
 * <p>One place, shared: every player gets the same base, and the place itself answers "whose is this spot" with one
 * id of its own ({@link #OWNER}), which the core only uses to find a base for a rule that fires with nobody near.
 * Minecraft-free: where a player is comes in as a function.
 */
final class ArenaBases implements Bases {

    /** The id the place answers to when asked whose a spot is. */
    static final UUID OWNER = UUID.nameUUIDFromBytes((ArenaMod.MODID + ":arena").getBytes(StandardCharsets.UTF_8));

    private final ArenaPlace place;
    private final Function<UUID, Pos> where;

    ArenaBases(ArenaPlace place, Function<UUID, Pos> where) {
        this.place = place;
        this.where = where;
    }

    ArenaPlace place() {
        return place;
    }

    @Override
    public String noun() {
        return "arena";
    }

    @Override
    public Base baseOf(UUID player) {
        if (player == null) {
            return null;
        }
        int[] home = place.arrival();
        return new Base(player, OVERWORLD, home[0], home[1], home[2], home[1], region(place.area()),
                region(place.reach()));
    }

    @Override
    public UUID ownerAt(String dimension, double x, double z) {
        if (dimension != null && !dimension.isEmpty() && !OVERWORLD.equals(dimension)) {
            return null;
        }
        return region(place.area()).contains(x, z) ? OWNER : null;
    }

    @Override
    public Pos positionOf(UUID player) {
        return player == null ? null : where.apply(player);
    }

    static Region region(ArenaPlace.Columns c) {
        return new Region(c.minX(), c.maxX(), c.minZ(), c.maxZ());
    }
}
