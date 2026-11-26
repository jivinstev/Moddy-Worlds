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
}
