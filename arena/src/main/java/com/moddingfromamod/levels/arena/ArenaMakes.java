package com.moddingfromamod.levels.arena;

import com.moddingfromamod.wish.WishAsk;
import com.moddingfromamod.wishbuilder.MakeFacts;
import com.moddingfromamod.world.MakeView;
import com.moddingfromamod.world.WorldLimits;
import com.moddingfromamod.world.WorldMakes;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * <b>What a player may make in the arena, and how strong</b>: the arena's answer to the core's {@code WorldMakes} hook,
 * handed to the core by the arena's world module ({@link ArenaModule#makes}), so it is in force in arena worlds only.
 * It reads the world's unlock ladder ({@link ArenaLadder}) and how far the battle on now has got (none in prep: the
 * first step), the same for every player, and:
 * <ul>
 *   <li>states his limits up front ({@link #limits}), in the core's fact vocabulary, with a stamp that changes when his
 *       step does; the core then fits every make to them (Builder pages and tune dialogs, the Apprentice's tiles,
 *       replays and hotbar fires, a creature on the live entity) and says so once, in its own words;</li>
 *   <li>in a battle, locks a make whose kind or tag the ladder keeps for later ({@link #view}), with the arena's own
 *       words saying when it opens; everything else is allowed (and fitted). Nothing is hidden. In prep nothing is
 *       locked (a locked kind is made at the first step's size): prep is where a player makes what he puts on his
 *       hotbar, and a kind locked there too could never reach his hotbar at all, since a new make in a battle is refused
 *       and a battle's ladder starts again at every battle.</li>
 * </ul>
 * It decides from the make's facts alone (its kind, its tags, its numbers): never a Builder id or name, so a core that
 * adds Builder content needs no change here. A tag, kind or number the ladder does not name is never locked or limited.
 * Cheap and free of side effects, as the hook asks: a map read and a list walk.
 */
final class ArenaMakes implements WorldMakes {

    private final Supplier<ArenaLadder> ladder;
    private final IntSupplier reached;
    private final BooleanSupplier inBattle;

    /**
     * @param ladder  the ladder in force (the world's {@code arena_ladder} rule, read when asked)
     * @param reached  how far the battle on now has got, as that ladder counts it (0 outside a battle)
     * @param inBattle whether a battle is on (from its button to the arena being ready again)
     */
    ArenaMakes(Supplier<ArenaLadder> ladder, IntSupplier reached, BooleanSupplier inBattle) {
        this.ladder = ladder;
        this.reached = reached;
        this.inBattle = inBattle;
    }

    @Override
    public MakeView view(MakeFacts facts, WishAsk ask) {
        if (facts == null || ask == null || ask.player() == null) {
            return MakeView.ALLOWED;
        }
        ArenaLadder l = ladder.get();
        int until = l.locksUntil(facts.kind(), facts.tags(), facts.known());
        if (until > 0 && inBattle.getAsBoolean() && reached.getAsInt() < until) {
            return MakeView.locked(ArenaWords.ladderLocked(l.progress(), until));
        }
        return MakeView.ALLOWED;   // the limits below fit it
    }

    @Override
    public WorldLimits limits(UUID player) {
        if (player == null) {
            return WorldLimits.NONE;
        }
        ArenaLadder l = ladder.get();
        int b = reached.getAsInt();
        return new WorldLimits(l.limits(b), l.stamp(b));
    }
}
