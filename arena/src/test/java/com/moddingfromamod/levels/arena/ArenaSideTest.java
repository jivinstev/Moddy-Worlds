package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A wished creature is on the players' side in the arena: never hurts one there, never keeps one as its target. */
class ArenaSideTest {

    @Test
    void aWishedCreaturesBiteOnAPlayerInThePlaceIsStopped() {
        assertTrue(ArenaSide.spare(true, true, true, true), "a shark's own bite: cause and hit are the shark");
        assertTrue(ArenaSide.spare(true, true, true, false), "its arrow: the cause is the creature");
        assertTrue(ArenaSide.spare(true, true, false, true), "fangs or a cloud it owns, traced by the thing that hit");
    }

    @Test
    void outsideThePlaceNothingChanges() {
        assertFalse(ArenaSide.spare(true, false, true, true), "beyond the arena's walls a hunter still hunts");
        assertFalse(ArenaSide.dropTarget(true, false, true));
    }

    @Test
    void otherHurtIsNotTouched() {
        assertFalse(ArenaSide.spare(true, true, false, false), "a wave's monster, a fall, lava, a blast nobody owns");
        assertFalse(ArenaSide.spare(false, true, true, true), "a wished creature hitting a monster: that is its job");
    }

    @Test
    void aWishedCreatureLetsGoOfAPlayerTargetOnly() {
        assertTrue(ArenaSide.dropTarget(true, true, true));
        assertFalse(ArenaSide.dropTarget(true, true, false), "a monster as its target: it fights on");
        assertFalse(ArenaSide.dropTarget(false, true, true), "a wave's monster goes for the players, as it should");
    }
}
