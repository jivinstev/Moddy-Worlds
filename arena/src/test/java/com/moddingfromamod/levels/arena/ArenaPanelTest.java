package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaPanel.Button;
import com.moddingfromamod.levels.arena.ArenaPanel.Control;
import com.moddingfromamod.levels.arena.ArenaPanel.Placed;
import com.moddingfromamod.levels.arena.ArenaPanel.Slot;
import com.moddingfromamod.levels.arena.ArenaPanel.Words;
import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The battle arena control panel's layout: its blocks, its buttons, and where every line of words floats. */
class ArenaPanelTest {

    private static final ArenaPlace P = ArenaPlace.DEFAULT;

    /** Every place of every size the rules allow (the arena's size does not move the panel; its smallest is used). */
    private static List<ArenaPlace> everyPlace() {
        List<ArenaPlace> out = new ArrayList<>();
        for (int prep = ArenaPlace.MIN_PREP; prep <= ArenaPlace.MAX_PREP; prep += 2) {
            for (int wall = ArenaPlace.MIN_WALL; wall <= ArenaPlace.MAX_WALL; wall++) {
                out.add(new ArenaPlace(Math.max(ArenaPlace.MIN_ARENA, prep + 2), prep, wall, 64));
            }
        }
        return out;
    }

    private static Map<List<Integer>, Kind> blocks(ArenaPlace p) {
        Map<List<Integer>, Kind> m = new HashMap<>();
        for (Placed b : p.panel().blocks()) {
            assertTrue(m.put(List.of(b.x(), b.y(), b.z()), b.kind()) == null, "each block once: " + b);
        }
        return m;
    }

    @Test
    void theDefaultBoardCoversTheWindowButTheGateway() {
        ArenaPanel panel = P.panel();
        assertEquals(4, panel.rows());
        assertEquals(new Box(-4, 64, 16, 4, 67, 16), panel.board());
        Map<List<Integer>, Kind> m = blocks(P);
        for (int x = -4; x <= 4; x++) {
            for (int y = 64; y <= 67; y++) {
                Kind k = m.get(List.of(x, y, 16));
                if (x == 0 && y <= 65) {
                    assertEquals(null, k, "the gate and the opening above it are left as built");
                } else {
                    assertTrue(k == Kind.PANEL_BOARD || k == Kind.PANEL_SELECT || k == Kind.PANEL_START, x + "," + y);
                }
            }
        }
        assertEquals(Kind.PANEL_BOARD, m.get(List.of(0, 66, 16)), "the title runs over the gate");
        assertEquals(List.of(Kind.PANEL_START, Kind.PANEL_START, Kind.PANEL_START),
                List.of(m.get(List.of(2, 65, 16)), m.get(List.of(3, 65, 16)), m.get(List.of(4, 65, 16))));
        assertEquals(Kind.PANEL_BOARD, m.get(List.of(3, 64, 16)), "the start button's green is 3 wide and 1 high");
        assertEquals(Kind.PANEL_SELECT, m.get(List.of(-4, 64, 16)), "the WAVES selector on the lowest row");
        assertEquals(Kind.PANEL_BOARD, m.get(List.of(1, 65, 16)), "a black margin by the gate");
    }

    @Test
    void theDefaultButtons() {
        Map<Control, List<Integer>> at = new HashMap<>();
        for (Button b : P.panel().buttons()) {
            at.put(b.control(), List.of(b.x(), b.y(), b.z()));
        }
        assertEquals(List.of(-4, 66, 17), at.get(Control.SIZE_DOWN));
        assertEquals(List.of(-1, 66, 17), at.get(Control.SIZE_UP));
        assertEquals(List.of(-4, 65, 17), at.get(Control.DIFFICULTY_DOWN));
        assertEquals(List.of(-1, 65, 17), at.get(Control.DIFFICULTY_UP));
        assertEquals(List.of(-4, 64, 17), at.get(Control.WAVES_DOWN));
        assertEquals(List.of(-1, 64, 17), at.get(Control.WAVES_UP));
        assertEquals(List.of(3, 65, 17), at.get(Control.START));
        // the start button at a child's eye height (he stands at 64, his eyes at 65.6), the selectors beside it
        assertEquals(65, P.startButton()[1]);
    }

    @Test
    void everyButtonHangsOnItsOwnColourInThePrepRoomsFirstRow() {
        for (ArenaPlace p : everyPlace()) {
            Map<List<Integer>, Kind> m = blocks(p);
            Set<List<Integer>> seen = new HashSet<>();
            for (Button b : p.panel().buttons()) {
                assertTrue(seen.add(List.of(b.x(), b.y(), b.z())), "two buttons in one block: " + p.describe());
                assertTrue(p.prepInside().contains(b.x(), b.y(), b.z()), b + " in the prep room of " + p.describe());
                assertEquals(p.sharedWallZ() + 1, b.z());
                Kind on = m.get(List.of(b.x(), b.y(), b.z() - 1));
                assertEquals(b.control() == Control.START ? Kind.PANEL_START : Kind.PANEL_SELECT, on,
                        b + " hangs on its colour in " + p.describe());
                assertEquals(b.control() == Control.START ? Kind.START_BUTTON : Kind.SELECT_BUTTON,
                        m.get(List.of(b.x(), b.y(), b.z())));
                assertFalse(p.panel().isGateway(b.x(), b.y()));
            }
            for (Map.Entry<List<Integer>, Kind> e : m.entrySet()) {
                List<Integer> k = e.getKey();
                if (k.get(2) == p.sharedWallZ()) {
                    assertTrue(p.panel().board().contains(k.get(0), k.get(1), k.get(2)), "in the window: " + k);
                    assertFalse(p.panel().isGateway(k.get(0), k.get(1)), "never in the gateway: " + k);
                }
            }
            // the blocks are placed board first, so a button never goes up before what it hangs on
            List<Placed> order = p.panel().blocks();
            int lastBoard = -1;
            int firstButton = Integer.MAX_VALUE;
            for (int i = 0; i < order.size(); i++) {
                Kind k = order.get(i).kind();
                if (k == Kind.START_BUTTON || k == Kind.SELECT_BUTTON) {
                    firstButton = Math.min(firstButton, i);
                } else {
                    lastBoard = i;
                }
            }
            assertTrue(lastBoard < firstButton);
            assertTrue(p.fills().containsAll(p.controlFills()), "a new place is built with its panel");
        }
    }

    /** A line of words' rectangle on the board's face: x0, x1, y0, y1 (the longest words it can show). */
    private static double[] rect(Slot s) {
        double w = 0;
        int lines = 1;
        for (String l : ArenaPanel.longest(s.words())) {
            w = Math.max(w, ArenaPanel.width(l) * s.scale());
        }
        if (s.words() == Words.SIZE_MEANS) {
            lines = 2;
        }
        return new double[] {s.x() - w / 2, s.x() + w / 2, s.y(), s.y() + lines * ArenaPanel.LINE * s.scale()};
    }

    private static boolean overlap(double[] a, double[] b) {
        return a[0] < b[1] && b[0] < a[1] && a[2] < b[3] && b[2] < a[3];
    }

    @Test
    void everyLineOfWordsStaysOnTheBoardAndClearOfTheOthersAndTheButtons() {
        for (ArenaPlace p : everyPlace()) {
            ArenaPanel panel = p.panel();
            List<Slot> slots = panel.slots();
            Set<Words> roles = new HashSet<>();
            int h = p.prepSize() / 2;
            for (Slot s : slots) {
                assertTrue(roles.add(s.words()), "one line each");
                double[] r = rect(s);
                String at = s.words() + " in " + p.describe();
                assertTrue(r[0] >= -h - 1e-9 && r[1] <= h + 1 + 1e-9, at + " stays on the board: " + r[0] + ".." + r[1]);
                assertTrue(r[2] >= p.floorY() && r[3] <= p.windowTop() + 1, at + " stays on the board, up and down");
                assertTrue(s.z() > p.sharedWallZ() + 1 && s.z() < p.sharedWallZ() + 1.1, at + " just in front of it");
                assertTrue(s.scale() >= 0.3f);
                boolean inGateway = r[0] < 1 && r[1] > 0 && r[2] < p.floorY() + 2;
                assertFalse(inGateway, at + " is not in front of the gate");
            }
            if (p.prepSize() < 7) {
                continue;   // the smallest prep room (5 wide) has no room between a selector's buttons
            }
            for (int i = 0; i < slots.size(); i++) {
                for (int j = i + 1; j < slots.size(); j++) {
                    assertFalse(overlap(rect(slots.get(i)), rect(slots.get(j))),
                            slots.get(i).words() + " and " + slots.get(j).words() + " in " + p.describe());
                }
                for (Button b : panel.buttons()) {
                    double[] face = {b.x() + 0.5 - 0.1875, b.x() + 0.5 + 0.1875, b.y() + 0.375, b.y() + 0.625};
                    assertFalse(overlap(rect(slots.get(i)), face), slots.get(i).words() + " over " + b.control()
                            + " in " + p.describe());
                }
            }
            assertEquals(p.windowTop() - p.floorY() + 1 >= 4,
                    slots.stream().anyMatch(s -> s.words() == Words.SIZE_MEANS),
                    "what the settings mean is shown when the board has a fourth row");
            assertEquals(p.panel().hasTitleRow(), slots.stream().anyMatch(s -> s.words() == Words.TITLE));
            assertEquals(p.panel().hasTitleRow(), slots.stream().anyMatch(s -> s.words() == Words.BEST));
            for (Words w : List.of(Words.SIZE_VALUE, Words.DIFFICULTY_VALUE, Words.WAVES_VALUE, Words.START_LABEL,
                    Words.STATE)) {
                assertTrue(slots.stream().anyMatch(s -> s.words() == w), w + " on every board");
            }
        }
    }

    @Test
    void theDefaultWordsAreBig() {
        Map<Words, Slot> s = new HashMap<>();
        for (Slot slot : P.panel().slots()) {
            s.put(slot.words(), slot);
        }
        assertTrue(s.get(Words.TITLE).scale() >= 2.0f, "the title is the biggest");
        assertTrue(s.get(Words.SIZE_VALUE).scale() >= 2.0f, "the size is a big number");
        assertTrue(s.get(Words.DIFFICULTY_VALUE).scale() >= 1.5f, "the difficulty is a big word");
        assertTrue(s.get(Words.WAVES_VALUE).scale() >= 1.5f, "the number of waves is a big word");
        assertTrue(s.get(Words.BEST).scale() >= 0.8f, "the best line reads from the room");
        assertTrue(s.get(Words.SIZE_MEANS).scale() >= 0.8f, "what the settings mean reads from the room");
        assertTrue(s.get(Words.START_LABEL).scale() >= 1.3f);
        assertNotNull(s.get(Words.SIZE_MEANS));
        assertTrue(ArenaPanel.width(ArenaWords.PANEL_TITLE) * s.get(Words.TITLE).scale() <= 8.4);
        assertEquals(0.5, s.get(Words.TITLE).x(), "the title is over the middle of the window");
    }

    @Test
    void wordWidthsAreMinecraftsFont() {
        assertEquals(5 / 40.0, ArenaPanel.width("A"));
        assertEquals((6 + 6 - 1) / 40.0, ArenaPanel.width("10"));
        assertEquals((2 + 3 + 4 - 1) / 40.0, ArenaPanel.width("ilt"));
    }
}
