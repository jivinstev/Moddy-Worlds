package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where a fighter knocked out in a battle comes back (Moddy-Worlds#60). */
class ArenaRespawnTest {

    @Test
    void aFighterWhoWokeOutsideThePlaceInABattleGoesToThePrepRoom() {
        assertTrue(ArenaRespawn.toPrepRoom(false, false, true, true, true, false));
    }

    @Test
    void everythingElseIsTheGamesOwnRespawn() {
        assertFalse(ArenaRespawn.toPrepRoom(false, false, false, true, true, false), "after the battle: his own spawn");
        assertFalse(ArenaRespawn.toPrepRoom(false, false, true, false, true, false), "not a fighter: his own spawn");
        assertFalse(ArenaRespawn.toPrepRoom(true, false, true, true, true, false), "back from the End: not a knock-out");
        assertFalse(ArenaRespawn.toPrepRoom(false, true, true, true, true, false), "a spectator (Hardcore) stays put");
        assertFalse(ArenaRespawn.toPrepRoom(false, false, true, true, false, false), "the setting is off");
        assertFalse(ArenaRespawn.toPrepRoom(false, false, true, true, true, true), "already in the place: left there");
    }
}
