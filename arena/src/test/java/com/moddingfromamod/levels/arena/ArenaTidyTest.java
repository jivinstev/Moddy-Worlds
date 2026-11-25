package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.moddingfromamod.levels.arena.ArenaTidy.Act;
import com.moddingfromamod.levels.arena.ArenaTidy.Thing;
import org.junit.jupiter.api.Test;

/** Nothing of a player's is ever taken away by the arena's tidying; the fight's litter is. */
class ArenaTidyTest {

    @Test
    void aPlayersOwnThingsAreNeverRemovedAtAnyTidy() {
        for (Thing t : new Thing[] {Thing.PLAYERS_ITEM, Thing.PLAYERS_PICKUP}) {
            assertEquals(Act.KEEP, ArenaTidy.betweenWaves(t), t + ": left for him to pick up in the break");
            assertEquals(Act.TO_PREP_ROOM, ArenaTidy.atBattleEnd(t), t + ": moved where he comes back, not removed");
        }
    }

    @Test
    void betweenWavesOnlyTheLooseThingsGo() {
        for (Thing t : Thing.values()) {
            assertEquals(t == Thing.LOOSE ? Act.REMOVE : Act.KEEP, ArenaTidy.betweenWaves(t), t.toString());
        }
    }

    @Test
    void atTheBattlesEndTheFightsThingsGoAndPetsStay() {
        assertEquals(Act.REMOVE, ArenaTidy.atBattleEnd(Thing.DROP), "a monster's drop, experience");
        assertEquals(Act.REMOVE, ArenaTidy.atBattleEnd(Thing.WAVE_MOB));
        assertEquals(Act.REMOVE, ArenaTidy.atBattleEnd(Thing.WISHED), "a wished creature, tamed or not");
        assertEquals(Act.REMOVE, ArenaTidy.atBattleEnd(Thing.LOOSE));
        assertEquals(Act.REMOVE, ArenaTidy.atBattleEnd(Thing.OTHER));
        assertEquals(Act.KEEP, ArenaTidy.atBattleEnd(Thing.PET));
        assertEquals(Act.KEEP, ArenaTidy.atBattleEnd(Thing.UNTOUCHED), "a player, his mount, a board's words");
    }

    @Test
    void noTidyEverRemovesWhatIsUntouched() {
        assertNotEquals(Act.REMOVE, ArenaTidy.betweenWaves(Thing.UNTOUCHED));
        assertNotEquals(Act.REMOVE, ArenaTidy.atBattleEnd(Thing.UNTOUCHED));
    }
}
