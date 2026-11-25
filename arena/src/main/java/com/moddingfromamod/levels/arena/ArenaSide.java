package com.moddingfromamod.levels.arena;

/**
 * <b>A wished creature is always on the players' side in the arena</b> (the owner: "something the player wishes is the
 * player's side"). In the place (the arena and its prep room), in a battle or not, in a wave or a break, a creature a
 * wish made never hurts a player and never keeps a player as its target, whatever it was wished as (a shark that hunts,
 * say). In a wave it fights the monsters like any other wished creature ({@link ArenaRoles}). Outside the place nothing
 * changes. Pure: no Minecraft; {@link ArenaModule} (the game's damage event) and {@link ArenaRoleKeeper} (its pass over
 * the creatures) ask these and act.
 */
final class ArenaSide {

    private ArenaSide() {
    }

    /**
     * Is this hurt to a player stopped? Yes when the player is in the place and what hurts him was made by a wish: the
     * one that caused it ({@code causeWished}: the creature, or the owner of the arrow, fireball or fangs it sent) or
     * the thing that hit him ({@code directWished}). Damage with no creature behind it (a fall, lava, a blast nobody
     * owns, a command) is not traced, and is not stopped.
     */
    static boolean spare(boolean victimIsPlayer, boolean victimInPlace, boolean causeWished, boolean directWished) {
        return victimIsPlayer && victimInPlace && (causeWished || directWished);
    }

    /** Does a wished creature in the place let go of its target? Yes when the target is a player. */
    static boolean dropTarget(boolean creatureWished, boolean creatureInPlace, boolean targetIsPlayer) {
        return creatureWished && creatureInPlace && targetIsPlayer;
    }
}
