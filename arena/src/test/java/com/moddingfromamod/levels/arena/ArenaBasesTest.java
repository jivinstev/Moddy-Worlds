package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.world.Base;
import com.moddingfromamod.world.Bases;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The arena as the core's bases see it: every player's base is the place, home is the arrival spot. */
class ArenaBasesTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;
    private static final UUID HIM = UUID.randomUUID();

    @Test
    void hisBaseIsTheArenaAndHomeIsThePrepRoom() {
        ArenaBases b = new ArenaBases(P, id -> new Bases.Pos(Bases.OVERWORLD, 0.5, 64, 21.5));
        Base base = b.baseOf(HIM);
        assertEquals(HIM, base.owner());
        assertEquals(Bases.OVERWORLD, base.dimension());
        int[] a = P.arrival();
        assertEquals(a[0], base.x());
        assertEquals(a[2], base.z());
        assertEquals(P.floorY(), base.homeY());
        // he stands in the prep room: his wishes land in the prep room
        assertTrue(base.reach().contains(0.5, 21.5), "aimed at the prep room, from it: it lands");
        assertFalse(base.reach().contains(0.5, 0.5), "aimed at the arena from the prep room: refused");
        assertFalse(base.reach().contains(40.5, 0.5), "aimed past the wall: refused");
        assertTrue(base.holds(Bases.OVERWORLD, 0.5, 21.5), "he stands on his base in the prep room");
        assertFalse(base.holds("minecraft:the_nether", 0.5, 21.5));
        assertEquals("arena", b.noun());
        assertEquals(21.5, b.positionOf(HIM).z());
    }

    @Test
    void inTheArenaHisWishesLandInTheArena() {
        double[] at = {0.5, 0.5};
        ArenaBases b = new ArenaBases(P, id -> new Bases.Pos(Bases.OVERWORLD, at[0], 64, at[1]));
        assertTrue(b.baseOf(HIM).reach().contains(0.5, 0.5));
        assertFalse(b.baseOf(HIM).reach().contains(0.5, 21.5));
        // he walks to the prep room: the next wish asks again
        at[1] = 21.5;
        assertTrue(b.baseOf(HIM).reach().contains(0.5, 21.5));
        // away from the place, offline, or in another dimension: the arena's inside, as before
        assertEquals(P.arenaReach().minX(), new ArenaBases(P, id -> null).baseOf(HIM).reach().minX());
        ArenaBases nether = new ArenaBases(P, id -> new Bases.Pos("minecraft:the_nether", 0.5, 64, 21.5));
        assertFalse(nether.baseOf(HIM).reach().contains(0.5, 21.5));
    }

    @Test
    void thePlaceAnswersForItsOwnSpotsOnly() {
        ArenaBases b = new ArenaBases(P, id -> null);
        assertEquals(ArenaBases.OWNER, b.ownerAt(Bases.OVERWORLD, 0, 0));
        assertEquals(ArenaBases.OWNER, b.ownerAt("", 0, 21));
        assertNull(b.ownerAt(Bases.OVERWORLD, 100, 0));
        assertNull(b.ownerAt("minecraft:the_end", 0, 0));
        assertNull(b.baseOf(null));
        assertNull(b.positionOf(HIM));
    }
}
