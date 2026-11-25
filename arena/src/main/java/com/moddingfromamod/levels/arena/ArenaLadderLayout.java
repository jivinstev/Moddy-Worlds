package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The wish ladder board's layout, as numbers</b>: where each of its few lines of words goes on the black board and
 * how big, so it reads from where a player stands in the prep room, like the control panel beside it. Pure: no
 * Minecraft here; {@link ArenaLadderBoard} puts one text display per line.
 *
 * <p>Top down, centred on the board: the title, big and gold; where the battle is on the ladder; at most four big lines
 * of what wishes can have now; one line of what is locked; one of what the next step brings. Each line has its own
 * most size and is made smaller only as far as its words need to fit the board's width; the lines are stacked so none
 * overlaps another, and if they would not fit the board's height (a board smaller than the prep room's, say) every
 * size shrinks together. Short words for the board are {@link ArenaWords#ladderBoard}'s; the full list is
 * {@code /moddy wishes}'.
 */
final class ArenaLadderLayout {

    private ArenaLadderLayout() {
    }

    /** What a line of the board says. */
    enum Role { TITLE, WHERE, NOW_1, NOW_2, NOW_3, NOW_4, LOCKED, NEXT }

    /** The biggest each kind of line may be (1 is a sign's size). */
    static final float TITLE_MAX = 1.9f;
    static final float WHERE_MAX = 1.1f;
    static final float NOW_MAX = 1.5f;
    static final float SMALL_MAX = 1.0f;
    /** The space between two lines, and above the top one, in blocks. */
    static final double GAP = 0.08;
    static final double TOP_MARGIN = 0.2;
    /** The margin each side of the words, in blocks. */
    static final double SIDE_MARGIN = 0.2;

    /**
     * One line: what it says, its colour (as Minecraft names it), whether bold, how big, and where its bottom sits,
     * in blocks above the board's bottom edge.
     */
    record Line(Role role, String text, String colour, boolean bold, float scale, double bottom) {
        /** How tall the line is, in blocks. */
        double height() {
            return ArenaPanel.LINE * scale;
        }

        /** How wide the line is, in blocks (as {@link ArenaPanel#width} estimates it). */
        double width() {
            return ArenaPanel.width(text) * scale * (bold ? 1.17 : 1.0);
        }
    }

    /** The lines for {@code w} on a board {@code width} blocks wide and {@code height} high. Pure. */
    static List<Line> lay(ArenaWords.LadderBoardWords w, double width, double height) {
        record Want(Role role, String text, String colour, boolean bold, float max) {
        }
        List<Want> want = new ArrayList<>();
        want.add(new Want(Role.TITLE, w.title(), "gold", true, TITLE_MAX));
        want.add(new Want(Role.WHERE, w.where(), "aqua", false, WHERE_MAX));
        Role[] now = {Role.NOW_1, Role.NOW_2, Role.NOW_3, Role.NOW_4};
        for (int i = 0; i < Math.min(now.length, w.now().size()); i++) {
            want.add(new Want(now[i], w.now().get(i), "white", false, NOW_MAX));
        }
        if (w.locked() != null) {
            want.add(new Want(Role.LOCKED, w.locked(), "gray", false, SMALL_MAX));
        }
        if (w.next() != null) {
            want.add(new Want(Role.NEXT, w.next(), "green", false, SMALL_MAX));
        }
        double room = width - 2 * SIDE_MARGIN;
        float[] scale = new float[want.size()];
        double total = TOP_MARGIN;
        for (int i = 0; i < want.size(); i++) {
            Want x = want.get(i);
            double wpx = ArenaPanel.width(x.text()) * (x.bold() ? 1.17 : 1.0);
            scale[i] = (float) Math.min(x.max(), wpx <= 0 ? x.max() : 0.995 * room / wpx);
            total += ArenaPanel.LINE * scale[i] + GAP;
        }
        double shrink = total > height ? (height - TOP_MARGIN - GAP * want.size()) / (total - TOP_MARGIN
                - GAP * want.size()) : 1.0;
        List<Line> out = new ArrayList<>();
        double top = height - TOP_MARGIN;
        for (int i = 0; i < want.size(); i++) {
            Want x = want.get(i);
            float s = (float) (scale[i] * shrink);
            double bottom = top - ArenaPanel.LINE * s;
            out.add(new Line(x.role(), x.text(), x.colour(), x.bold(), s, bottom));
            top = bottom - GAP;
        }
        return out;
    }
}
