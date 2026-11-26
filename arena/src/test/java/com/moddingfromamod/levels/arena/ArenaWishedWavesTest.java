package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.moddingfromamod.wishbuilder.BuilderAllowlist;
import com.moddingfromamod.wishbuilder.BuilderSpec;
import org.junit.jupiter.api.Test;

class ArenaWishedWavesTest {

    /** The Builder's own multipliers ({@code BuilderCreatureStrength}), to check the ramp never weakens. */
    private static final java.util.Map<String, double[]> SIZE = java.util.Map.of("", new double[] {1, 1},
            "4", new double[] {1, 1}, "5", new double[] {1.5, 1.25}, "6", new double[] {2.5, 1.6},
            "7", new double[] {3.5, 2.0}, "8", new double[] {5.0, 2.5});
    private static final java.util.Map<String, double[]> STRENGTH = java.util.Map.of("", new double[] {1, 1},
            "1", new double[] {0.75, 0.5}, "2", new double[] {1, 1}, "3", new double[] {1.5, 1.5});

    private static ArenaWaves.Wave wave(int husks, int pillagers) {
        return new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", husks),
                new ArenaWaves.Group("minecraft:pillager", pillagers)));
    }

    @Test
    void rungZeroIsThePlainMobAsABuilderRealMob() {
        BuilderSpec s = ArenaWishedWaves.mob("minecraft:husk", 0);
        assertEquals(BuilderSpec.Kind.CREATURE, s.kind());
        assertEquals("mob:minecraft:husk", s.from());
        assertTrue(s.opts().isEmpty(), "rung 0 picks nothing: the plain mob, " + s.opts());
        assertTrue(s.powers().isEmpty() && s.scheme().isEmpty(), "a Builder real mob takes no powers or colours");
    }

    @Test
    void theRampNeverGetsWeaker() {
        double lastH = 0, lastD = 0;
        for (ArenaWishedWaves.Rung r : ArenaWishedWaves.RAMP) {
            double h = SIZE.get(r.mobsize())[0] * STRENGTH.get(r.strength())[0];
            double d = SIZE.get(r.mobsize())[1] * STRENGTH.get(r.strength())[1];
            assertTrue(h >= lastH && d >= lastD, "rung " + r.after() + " is weaker than the last");
            lastH = h;
            lastD = d;
        }
    }

    @Test
    void championsAreShadowBuilderDesignsTheWorldsListAllowsAndNoChildsDoes() {
        for (int r = 0; r < ArenaWishedWaves.RAMP.size(); r++) {
            BuilderSpec c = ArenaWishedWaves.champion(r);
            assertEquals("shadow", c.scheme());
            assertTrue(c.from().startsWith("preset:bodies/"), c.from());
            assertTrue(c.powers().contains("power:hostile") && c.powers().contains("power:glowing"));
            assertTrue(BuilderAllowlist.FOR_WORLDS.problems(c).isEmpty(),
                    "the world's own list refuses its champion: " + BuilderAllowlist.FOR_WORLDS.problems(c));
            assertFalse(BuilderAllowlist.REVIEWED.problems(c).isEmpty(), "a child's Builder may make a hostile monster");
        }
    }

    @Test
    void oneInFourIsAChampionDesignsAndFullPillarsAreCapped() {
        List<ArenaWishedWaves.Plan> plan = ArenaWishedWaves.plan(wave(20, 20), 5);
        assertEquals(40, plan.size());
        assertEquals(10, plan.stream().filter(ArenaWishedWaves.Plan::champion).count());
        assertEquals(ArenaWishedWaves.MAX_DESIGN_CHAMPIONS, plan.stream().filter(p -> p.design() != null).count());
        assertEquals(ArenaWishedWaves.MAX_FULL_PILLARS, plan.stream().filter(p -> !p.slim()).count());
        for (ArenaWishedWaves.Plan p : plan) {
            assertNotNull(p.mob(), "every monster has a real-mob spec to stand in");
            if (!p.champion()) {
                assertTrue(p.slim());
                assertNull(p.design());
            }
        }
        List<ArenaWishedWaves.Plan> small = ArenaWishedWaves.plan(wave(1, 0), 0);
        assertTrue(small.get(0).champion() && !small.get(0).slim() && small.get(0).design() != null,
                "a wave of one still has its champion");
    }

    @Test
    void theSwitchIsOnUnlessTurnedOff() {
        String was = System.getProperty("moddyarena.wishedWaves");
        try {
            System.clearProperty("moddyarena.wishedWaves");
            assertTrue(ArenaWishedWaves.on());
            System.setProperty("moddyarena.wishedWaves", "false");
            assertFalse(ArenaWishedWaves.on());
        } finally {
            if (was == null) {
                System.clearProperty("moddyarena.wishedWaves");
            } else {
                System.setProperty("moddyarena.wishedWaves", was);
            }
        }
    }
}
