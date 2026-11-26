package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ArenaSiteRulesTest {

    private static ArenaSiteRules.Site site(String name, int x, int z) {
        return new ArenaSiteRules.Site(name, "minecraft:overworld", x - 20, z - 30, x + 20, z + 20);
    }

    @Test
    void namesAreShortWordsAndNeverGlobal() {
        assertEquals("north", ArenaSiteRules.name(" North "));
        assertEquals("pit_2", ArenaSiteRules.name("pit_2"));
        assertNull(ArenaSiteRules.name("global"), "global is the config's scope for every arena");
        assertNull(ArenaSiteRules.name("2pit"));
        assertNull(ArenaSiteRules.name("has space"));
        assertNull(ArenaSiteRules.name(""));
    }

    @Test
    void arenasMayNotOverlapOrCrowdOrShareAName() {
        List<ArenaSiteRules.Site> sites = List.of(site("main", 0, 0));
        assertNull(ArenaSiteRules.problem(site("far", 500, 0), sites));
        assertNotNull(ArenaSiteRules.problem(site("over", 10, 0), sites));
        assertNotNull(ArenaSiteRules.problem(site("close", 41 + ArenaSiteRules.GAP - 1, 0), sites), "inside the gap");
        assertNull(ArenaSiteRules.problem(site("clear", 41 + ArenaSiteRules.GAP + 1, 0), sites));
        assertNotNull(ArenaSiteRules.problem(site("main", 900, 900), sites), "a name in use");
        assertNull(ArenaSiteRules.problem(new ArenaSiteRules.Site("nether", "minecraft:the_nether", -20, -30, 20, 20),
                sites), "another dimension never overlaps");
    }

    @Test
    void aWorldHoldsAtMostSoMany() {
        List<ArenaSiteRules.Site> sites = new ArrayList<>();
        for (int i = 0; i < ArenaSiteRules.MAX_SITES; i++) {
            sites.add(site("a" + i, i * 200, 0));
        }
        assertNotNull(ArenaSiteRules.problem(site("one_more", 99_000, 0), sites));
    }

    @Test
    void theSiteAtASpotIsTheOneWhoseFootprintHoldsIt() {
        List<ArenaSiteRules.Site> sites = List.of(site("main", 0, 0), site("east", 300, 0));
        assertEquals("east", ArenaSiteRules.at(sites, "minecraft:overworld", 301, 5).name());
        assertNull(ArenaSiteRules.at(sites, "minecraft:overworld", 150, 0));
        assertNull(ArenaSiteRules.at(sites, "minecraft:the_end", 0, 0));
    }

    /** The ground an arena takes is its whole place, prep room and base included, not only the arena's reset box. */
    @Test
    void theClaimIsTheWholePlaceWithItsBase() {
        ArenaPlace p = ArenaPlace.DEFAULT.at(500, -300);
        ArenaSiteRules.Site c = ArenaSiteRules.claim("north", p);
        ArenaPlace.Box f = p.footprint();
        ArenaPlace.Box b = p.baseBox();
        org.junit.jupiter.api.Assertions.assertTrue(c.x0() <= f.x0() && c.x1() >= f.x1() && c.z0() <= f.z0()
                && c.z1() >= f.z1(), "the footprint, prep room included, is inside the claim");
        org.junit.jupiter.api.Assertions.assertEquals(b.x0(), c.x0());
        org.junit.jupiter.api.Assertions.assertEquals(b.z1(), c.z1());
        int[] a = p.arrival();
        org.junit.jupiter.api.Assertions.assertTrue(a[0] >= c.x0() && a[0] <= c.x1() && a[2] >= c.z0() && a[2] <= c.z1(),
                "the prep room's arrival spot is claimed");
        // a second place whose prep room would reach into the first one's is refused
        ArenaPlace q = ArenaPlace.DEFAULT.at(500, -300 + (f.z1() - f.z0()));
        org.junit.jupiter.api.Assertions.assertNotNull(ArenaSiteRules.problem(ArenaSiteRules.claim("south", q),
                java.util.List.of(c)));
    }

    @Test
    void aPlaceMustFitBetweenTheWorldsBottomAndTop() {
        org.junit.jupiter.api.Assertions.assertNull(ArenaSiteRules.heightProblem(61, 68, -64, 319));
        org.junit.jupiter.api.Assertions.assertTrue(ArenaSiteRules.heightProblem(-66, -59, -64, 319).contains("too low"));
        org.junit.jupiter.api.Assertions.assertTrue(ArenaSiteRules.heightProblem(313, 320, -64, 319).contains("too high"));
        org.junit.jupiter.api.Assertions.assertTrue(ArenaSiteRules.inTheWay("a chest", 1, 64, -3)
                .contains("a chest at 1 64 -3"));
    }
}
