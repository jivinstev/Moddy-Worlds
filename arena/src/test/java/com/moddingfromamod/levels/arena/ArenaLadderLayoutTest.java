package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The wish ladder board's layout ({@link ArenaLadderLayout}): a few big lines that never run off the board, never
 * overlap, and stay big enough to read from across the prep room, whatever the ladder says.
 */
class ArenaLadderLayoutTest {

    private static final double WIDTH = 4;
    private static final double HEIGHT = ArenaPlace.PREP_HEIGHT;

    /** Every board the default ladder and its boss-counted twin can show, in prep and in a battle. */
    private static List<ArenaWords.LadderBoardWords> everyBoard() {
        List<ArenaWords.LadderBoardWords> out = new ArrayList<>();
        for (ArenaLadder.Count pr : ArenaLadder.Count.values()) {
            ArenaLadder l = new ArenaLadder(pr, ArenaLadder.DEFAULT.steps(), ArenaLadder.DEFAULT.locked());
            for (int b = 0; b <= 1000; b += b < 25 ? 1 : 245) {
                out.add(ArenaWords.ladderBoard(l, true, b));
                out.add(ArenaWords.ladderBoard(l, false, b));
            }
        }
        // the longest words a board can carry: every lock at once, the widest numbers
        out.add(new ArenaWords.LadderBoardWords(ArenaWords.LADDER_TITLE, "This battle: 1000 bosses beaten",
                List.of("Health: 1000.5 hearts", "Hits: 1000.5 hearts", "Builder size: 4 of 4", "Knockback: 100%"),
                "Locked in a battle: Creatures, Rides, Items, Machines, Magic blocks, Buildings, Pictures",
                "After boss 1000: wishes that change blocks open"));
        return out;
    }

    @Test
    void wordsNeverRunOffTheBoardNorOverlap() {
        for (ArenaWords.LadderBoardWords w : everyBoard()) {
            List<ArenaLadderLayout.Line> lines = ArenaLadderLayout.lay(w, WIDTH, HEIGHT);
            double above = HEIGHT;
            for (ArenaLadderLayout.Line l : lines) {
                assertTrue(l.width() <= WIDTH - 2 * ArenaLadderLayout.SIDE_MARGIN + 1e-9, "too wide: " + l);
                assertTrue(l.bottom() + l.height() <= above + 1e-9, "overlaps the line above: " + l);
                assertTrue(l.bottom() >= 0, "below the board: " + l);
                above = l.bottom();
            }
        }
    }

    @Test
    void itIsFewBigLinesInOrder() {
        ArenaWords.LadderBoardWords w = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, true, 2);
        List<ArenaLadderLayout.Line> lines = ArenaLadderLayout.lay(w, WIDTH, HEIGHT);
        assertEquals(List.of(ArenaLadderLayout.Role.TITLE, ArenaLadderLayout.Role.WHERE, ArenaLadderLayout.Role.NOW_1,
                ArenaLadderLayout.Role.NOW_2, ArenaLadderLayout.Role.NOW_3, ArenaLadderLayout.Role.NOW_4,
                ArenaLadderLayout.Role.LOCKED, ArenaLadderLayout.Role.NEXT), lines.stream().map(
                ArenaLadderLayout.Line::role).toList());
        assertTrue(lines.size() <= 8, "at most eight lines");
        assertEquals("This battle: 2 waves cleared", lines.get(1).text());
        for (ArenaLadderLayout.Line l : lines) {
            switch (l.role()) {
                case TITLE -> assertTrue(l.scale() >= 1.5f, "the title is big: " + l);
                case NOW_1, NOW_2, NOW_3, NOW_4 -> assertTrue(l.scale() >= 1.2f, "what matters now is big: " + l);
                default -> assertTrue(l.scale() >= 0.8f, "every line readable: " + l);
            }
        }
    }

    @Test
    void aSmallerBoardShrinksEveryLineTogether() {
        ArenaWords.LadderBoardWords w = ArenaWords.ladderBoard(ArenaLadder.DEFAULT, true, 2);
        List<ArenaLadderLayout.Line> lines = ArenaLadderLayout.lay(w, WIDTH, 2.0);
        double above = 2.0;
        for (ArenaLadderLayout.Line l : lines) {
            assertTrue(l.bottom() + l.height() <= above + 1e-9 && l.bottom() >= 0, l.toString());
            above = l.bottom();
        }
    }
}
