package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaMatch.Event;
import com.moddingfromamod.levels.arena.ArenaMatch.Kind;
import com.moddingfromamod.levels.arena.ArenaMatch.Phase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A battle as a state machine, tick by tick, with no Minecraft. */
class ArenaMatchTest {

    private static final int T = ArenaMatch.TICKS_PER_SECOND;

    /** 3 waves: countdown 5 s, breather 4 s, a wave may last 30 s. */
    private static final ArenaWaves TABLE = new ArenaWaves(5, 4, 30, List.of(
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 2))),
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 3))),
            new ArenaWaves.Wave(List.of(new ArenaWaves.Group("minecraft:husk", 1),
                    new ArenaWaves.Group("minecraft:pillager", 1)))));

    /** Ticks until an event comes, feeding the same facts; fails after max ticks. */
    private static Event until(ArenaMatch m, int mobs, int fighters, int max) {
        for (int i = 0; i < max; i++) {
            Event e = m.tick(mobs, fighters);
            if (e != null && e.kind() != Kind.COUNTDOWN) {
                return e;
            }
        }
        throw new AssertionError("no event in " + max + " ticks; phase " + m.phase());
    }

    @Test
    void aWholeBattleWonWaveByWaveThenResetThenIdle() {
        ArenaMatch m = new ArenaMatch();
        assertEquals(Phase.IDLE, m.phase());
        assertNull(m.tick(0, 1), "idle does nothing");
        assertTrue(m.start(TABLE));
        assertEquals(Phase.COUNTDOWN, m.phase());
        assertEquals("Wave 1 of 3 in 5 seconds", m.bar().name());

        List<Integer> countdown = new ArrayList<>();
        Event e;
        while (true) {
            e = m.tick(0, 1);
            if (e == null) {
                continue;
            }
            if (e.kind() != Kind.COUNTDOWN) {
                break;
            }
            countdown.add(e.seconds());
        }
        assertEquals(List.of(4, 3, 2, 1), countdown, "a line each whole second");
        assertEquals(new Event(Kind.WAVE_START, 1, 0, null), e);
        assertEquals(Phase.WAVE, m.phase());
        m.spawned(2);
        assertEquals("Wave 1 of 3: 2 monsters left", m.bar().name());
        assertNull(m.tick(2, 1));
        assertNull(m.tick(1, 1), "one down, one to go");
        assertEquals("Wave 1 of 3: 1 monster left", m.bar().name());
        assertEquals(0.5f, m.bar().progress(), 1e-6);
        assertEquals(new Event(Kind.WAVE_CLEARED, 1, 4, null), m.tick(0, 1));
        assertEquals(Phase.BREATHER, m.phase());
        assertEquals("Wave 2 of 3 in 4 seconds", m.bar().name());

        assertEquals(new Event(Kind.WAVE_START, 2, 0, null), until(m, 0, 1, 4 * T + 1));
        m.spawned(3);
        assertEquals(Kind.WAVE_CLEARED, m.tick(0, 1).kind());
        assertEquals(new Event(Kind.WAVE_START, 3, 0, null), until(m, 0, 1, 4 * T + 1));
        m.spawned(2);
        assertEquals(new Event(Kind.WON, 3, 0, null), m.tick(0, 1), "the last wave cleared is a win");
        assertEquals(Phase.WON, m.phase());
        assertEquals("You won!", m.bar().name());
        assertTrue(m.busy());

        assertEquals(Kind.RESET, until(m, 0, 1, ArenaMatch.END_HOLD_TICKS + 2).kind());
        assertEquals(Phase.RESETTING, m.phase());
        assertNull(m.bar());
        assertEquals(Kind.READY, m.tick(0, 0).kind());
        assertEquals(Phase.IDLE, m.phase());
        assertFalse(m.busy());
        assertTrue(m.start(TABLE), "a new battle can start");
    }

    @Test
    void onlyOneBattleAtATime() {
        ArenaMatch m = new ArenaMatch();
        assertTrue(m.start(TABLE));
        assertFalse(m.start(TABLE), "pressing during a battle starts nothing");
        assertFalse(new ArenaMatch().start((ArenaRun) null), "no table, no battle");
    }

    @Test
    void theLastFighterOutLosesTheBattle() {
        ArenaMatch m = new ArenaMatch();
        m.start(TABLE);
        until(m, 0, 1, 5 * T + 1);
        m.spawned(2);
        assertNull(m.tick(2, 2));
        assertNull(m.tick(2, 1), "one of two out: still on");
        Event lost = m.tick(2, 0);
        assertEquals(new Event(Kind.LOST, 1, 0, ArenaMatch.Why.NOBODY_LEFT), lost);
        assertEquals(Phase.LOST, m.phase());
        assertEquals("Battle lost", m.bar().name());
        assertEquals(Kind.RESET, until(m, 0, 0, ArenaMatch.END_HOLD_TICKS + 2).kind());
        assertEquals(Kind.READY, m.tick(0, 0).kind());
    }

    @Test
    void nobodyInThePlaceDuringTheCountdownOrABreatherLosesToo() {
        ArenaMatch m = new ArenaMatch();
        m.start(TABLE);
        assertEquals(ArenaMatch.Why.NOBODY_LEFT, m.tick(0, 0).why());
        ArenaMatch b = new ArenaMatch();
        b.start(TABLE);
        until(b, 0, 1, 5 * T + 1);
        b.spawned(2);
        b.tick(0, 1);
        assertEquals(Phase.BREATHER, b.phase());
        assertEquals(Kind.LOST, b.tick(0, 0).kind());
    }

    @Test
    void aWaveThatOutlastsItsTimeIsLost() {
        ArenaMatch m = new ArenaMatch();
        m.start(TABLE);
        until(m, 0, 1, 5 * T + 1);
        m.spawned(2);
        for (int i = 0; i < 30 * T - 1; i++) {
            assertNull(m.tick(1, 1), "tick " + i);
        }
        assertEquals(new Event(Kind.LOST, 1, 0, ArenaMatch.Why.TIME_UP), m.tick(1, 1));
    }

    @Test
    void aWaveThatSpawnedNothingIsClearedAtOnce() {
        ArenaMatch m = new ArenaMatch();
        m.start(TABLE);
        until(m, 0, 1, 5 * T + 1);
        m.spawned(0);
        assertEquals(Kind.WAVE_CLEARED, m.tick(0, 1).kind());
    }

    @Test
    void aRestartMidBattleEndsItAndResetsOnceThingsHaveLoaded() {
        ArenaMatch m = new ArenaMatch();
        m.endedByRestart();
        assertEquals(Phase.RESETTING, m.phase());
        assertTrue(m.busy());
        assertFalse(m.fighting());
        assertNull(m.bar(), "no battle to show");
        for (int i = 0; i < ArenaMatch.RESTART_WAIT_TICKS; i++) {
            assertNull(m.tick(0, 0));
        }
        assertEquals(Kind.RESET, m.tick(0, 0).kind());
        assertEquals(Kind.READY, m.tick(0, 0).kind());
        assertEquals(Phase.IDLE, m.phase());
        assertNotNull(m);
    }

    @Test
    void reachSaysHowFarTheBattleGotAndABossFillsTheBar() throws Exception {
        ArenaWaves base = ArenaSettingsTest.bundled();
        ArenaMatch m = new ArenaMatch();
        assertTrue(m.start(new ArenaSettings(5, ArenaDifficulty.NORMAL, 10).run(base)));
        until(m, 0, 1, 5 * T + 1);
        assertEquals(new ArenaChampions.Reach(1, false, false, 0), m.reach());
        m.spawned(3);
        for (int i = 0; i < 40; i++) {
            m.tick(3, 1);
        }
        assertEquals(Kind.WAVE_CLEARED, m.tick(0, 1).kind());
        assertEquals(new ArenaChampions.Reach(1, true, false, 41), m.reach(), "cleared wave 1 after 41 ticks");
        for (int w = 2; w <= 4; w++) {
            assertEquals(w, until(m, 0, 1, 10 * T + 1).wave());
            m.spawned(1);
            assertEquals(Kind.WAVE_CLEARED, m.tick(0, 1).kind());
        }
        assertEquals("BOSS wave 5 of 10 in 10 seconds", m.bar().name());
        assertEquals(5, until(m, 0, 1, 10 * T + 1).wave());
        m.spawned(5);
        m.boss("Rumble the Ravager", 0.5f);
        m.tick(5, 1);
        assertEquals(new ArenaMatch.Bar("BOSS: Rumble the Ravager (wave 5 of 10)", 0.5f, true), m.bar());
        m.boss(null, 0f);
        m.tick(2, 1);
        assertEquals("Wave 5 of 10: 2 monsters left", m.bar().name(), "the boss beaten, the wave's monsters");
        assertEquals(Kind.WAVE_CLEARED, m.tick(0, 1).kind(), "wave 5 of 10 is not the last");
    }

    @Test
    void anEndlessBattleIsNeverWon() throws Exception {
        ArenaMatch m = new ArenaMatch();
        m.start(new ArenaSettings(1, ArenaDifficulty.NORMAL, 0).run(ArenaSettingsTest.bundled()));
        for (int w = 1; w <= 30; w++) {
            assertEquals(w, until(m, 0, 1, 10 * T + 1).wave());
            m.spawned(1);
            assertEquals(Kind.WAVE_CLEARED, m.tick(0, 1).kind());
        }
        assertEquals("Wave 31 in 10 seconds", m.bar().name());
        assertEquals(0, m.of());
        assertEquals(new Event(Kind.LOST, 30, 0, ArenaMatch.Why.NOBODY_LEFT), m.tick(0, 0));
        assertEquals(new ArenaChampions.Reach(30, true, false, m.reach().ticks()), m.reach());
    }
}
