package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaChampionsLayout.Face;
import com.moddingfromamod.levels.arena.ArenaChampionsLayout.Slot;
import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Champions board's layout: on the prep room's back wall, every word and face on it and clear of the others. */
class ArenaChampionsLayoutTest {

    @Test
    void theDefaultBoardIsTheBackWall() {
        ArenaPlace p = ArenaPlace.DEFAULT;
        ArenaChampionsLayout c = p.champions();
        assertEquals(26, c.wallZ());
        assertEquals(new Box(-4, 64, 26, 4, 67, 26), c.board());
        assertTrue(p.fills().containsAll(p.championsFills()), "a new place is built with it");
        assertFalse(p.panel().board().contains(0, 66, 26), "a different wall from the control panel");
    }

    private static double[] rect(Slot s) {
        double w = ArenaChampionsLayout.widest(s);
        return new double[] {s.x() - w / 2, s.x() + w / 2, s.y(), s.y() + ArenaPanel.LINE * s.scale()};
    }

    private static boolean overlap(double[] a, double[] b) {
        return a[0] < b[1] && b[0] < a[1] && a[2] < b[3] && b[2] < a[3];
    }

    @Test
    void everyWordAndFaceStaysOnTheBoardAndClearOfTheOthers() {
        for (int prep = 7; prep <= ArenaPlace.MAX_PREP; prep += 2) {
            ArenaPlace p = new ArenaPlace(Math.max(ArenaPlace.MIN_ARENA, prep + 2), prep, 4, 64);
            ArenaChampionsLayout c = p.champions();
            Box b = c.board();
            List<Slot> slots = c.slots();
            Set<String> roles = new HashSet<>();
            for (Slot s : slots) {
                assertTrue(roles.add(s.role() + s.row()), "one each");
                double[] r = rect(s);
                String at = s.role() + s.row() + " in " + p.describe();
                assertTrue(r[0] >= b.x0() - 1e-9 && r[1] <= b.x1() + 1 + 1e-9, at + " across");
                assertTrue(r[2] >= b.y0() && r[3] <= b.y1() + 1, at + " up and down");
                assertTrue(s.z() < c.wallZ() && s.z() > c.wallZ() - 0.1, at + " just in front");
                assertTrue(s.scale() >= (s.row() == 0 ? 0.8f : 0.7f), at + " reads from the room: " + s.scale());
            }
            for (int i = 0; i < slots.size(); i++) {
                for (int j = i + 1; j < slots.size(); j++) {
                    Slot a = slots.get(i);
                    Slot o = slots.get(j);
                    if (a.role().equals("none") && o.row() > 0 || o.role().equals("none") && a.row() > 0) {
                        continue;   // the empty line shows only when no row does
                    }
                    assertFalse(overlap(rect(a), rect(o)), a.role() + a.row() + " and " + o.role() + o.row());
                }
                for (Face f : c.faces()) {
                    double[] face = {f.x() - f.size() / 2, f.x() + f.size() / 2, f.y() - f.size() / 2,
                            f.y() + f.size() / 2};
                    if (!slots.get(i).role().equals("none")) {
                        assertFalse(overlap(rect(slots.get(i)), face), slots.get(i).role() + " over a face");
                    }
                }
            }
            assertEquals(ArenaChampions.SHOWN, c.faces().size());
        }
    }
}
