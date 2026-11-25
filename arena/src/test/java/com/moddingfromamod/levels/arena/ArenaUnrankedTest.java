package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaUnranked.Mode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A battle fought in Creative or Spectator is not on the Champions board, and he is told once. */
class ArenaUnrankedTest {

    private static final UUID HIM = UUID.randomUUID();
    private static final UUID HER = UUID.randomUUID();

    /** One battle as the arena sees it: each tick, the game modes it looked at; the lines it said, in order. */
    private static List<Mode> battle(ArenaUnranked u, UUID who, Mode... ticks) {
        List<Mode> told = new java.util.ArrayList<>();
        for (Mode m : ticks) {
            Mode t = u.saw(who, m);
            if (t != null) {
                told.add(t);
            }
        }
        return told;
    }

    @Test
    void startingInCreativeIsToldOnceAndNotRecorded() {
        ArenaUnranked u = new ArenaUnranked();
        assertEquals(List.of(Mode.CREATIVE), battle(u, HIM, Mode.CREATIVE, Mode.CREATIVE, Mode.CREATIVE),
                "told at the start, once, not every tick");
        assertFalse(u.recorded(HIM));
    }

    @Test
    void switchingToCreativeMidBattleAndBackStillCostsTheRun() {
        ArenaUnranked u = new ArenaUnranked();
        assertEquals(List.of(Mode.CREATIVE), battle(u, HIM, Mode.PLAY, Mode.PLAY, Mode.CREATIVE, Mode.CREATIVE,
                Mode.PLAY, Mode.PLAY, Mode.CREATIVE));
        assertFalse(u.recorded(HIM), "back in Survival for the rest of it: still not recorded");
    }

    @Test
    void spectatorIsNotRecordedEither() {
        ArenaUnranked u = new ArenaUnranked();
        assertEquals(List.of(Mode.SPECTATOR), battle(u, HIM, Mode.PLAY, Mode.SPECTATOR, Mode.SPECTATOR));
        assertFalse(u.recorded(HIM));
        assertEquals(List.of(), battle(u, HIM, Mode.CREATIVE), "Creative after Spectator: told once a battle");
    }

    @Test
    void survivalAllTheWayIsRecordedAndNeverTold() {
        ArenaUnranked u = new ArenaUnranked();
        assertEquals(List.of(), battle(u, HIM, Mode.PLAY, Mode.PLAY));
        assertTrue(u.recorded(HIM));
        assertNull(u.saw(null, Mode.CREATIVE), "nobody: nothing");
        assertFalse(u.recorded(null));
    }

    @Test
    void twoPlayersOneInCreativeTheOthersRunIsStillRecorded() {
        ArenaUnranked u = new ArenaUnranked();
        battle(u, HIM, Mode.PLAY, Mode.CREATIVE);
        battle(u, HER, Mode.PLAY, Mode.PLAY);
        Map<UUID, String> runs = new LinkedHashMap<>();
        runs.put(HER, "wave 5");
        runs.put(HIM, "won");
        assertEquals(Map.of(HER, "wave 5"), u.onlyRecorded(runs));
        assertEquals(java.util.Set.of(HIM), u.unranked());
    }

    @Test
    void theNextBattleStartsClean() {
        ArenaUnranked u = new ArenaUnranked();
        battle(u, HIM, Mode.CREATIVE);
        u.clear();
        assertTrue(u.recorded(HIM), "a new battle in Survival is recorded");
        assertEquals(List.of(Mode.CREATIVE), battle(u, HIM, Mode.CREATIVE), "and in Creative he is told again");
    }

    @Test
    void theWordsSayItPlainly() {
        assertEquals("You are in Creative: this battle will not go on the Champions board.",
                ArenaWords.notRecorded(Mode.CREATIVE));
        assertEquals("You are in Spectator: this battle will not go on the Champions board.",
                ArenaWords.notRecorded(Mode.SPECTATOR));
        assertEquals("Steve: in Creative, runs not recorded", ArenaWords.bestNotRecorded("Steve", Mode.CREATIVE));
    }
}
