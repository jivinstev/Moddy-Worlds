package com.moddingfromamod.levels.arena;

/**
 * <b>Where a fighter knocked out in a battle comes back</b> (Moddy-Worlds#60): in that arena's prep room, at the spot
 * every arrival uses, while its battle is on; his own way (his bed, an anchor, the world spawn) once it is over. Pure:
 * the caller teleports once after the respawn and saves nothing, so his bed and the world spawn never change.
 *
 * <p>Without this a fighter whose respawn is outside the place (a bed elsewhere, or the world spawn of a world the arena
 * is not the whole of) wakes up outside it, and the battle counts him as having left: out for good, with lives left.
 */
final class ArenaRespawn {

    /** The arena setting ({@link ArenaConfig}) that turns this off for an arena, or for all of them. */
    static final String KEY = "respawnInPrep";

    private ArenaRespawn() {
    }

    /**
     * Is this respawn to be moved to the prep room?
     *
     * @param fromTheEnd  the respawn of a player coming back from the End (never moved: that is not a knock-out)
     * @param spectator   he is a spectator now (a Hardcore death, or a spectator): left where he is
     * @param battleOn    the arena's battle is on (anything but ready for the next)
     * @param fought      he fought in this battle (still in it, or out of lives and watching)
     * @param switchedOn  the arena's {@value #KEY} setting, read when the battle began
     * @param inPlace     where the game respawned him is already inside the arena's place (its prep room, say)
     */
    static boolean toPrepRoom(boolean fromTheEnd, boolean spectator, boolean battleOn, boolean fought,
                              boolean switchedOn, boolean inPlace) {
        return !fromTheEnd && !spectator && battleOn && fought && switchedOn && !inPlace;
    }
}
