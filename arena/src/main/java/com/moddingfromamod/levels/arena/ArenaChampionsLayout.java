package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import com.moddingfromamod.levels.arena.ArenaPlace.Kind;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>The Champions board, as numbers</b>: a board over the inside of the prep room's back wall (the glass wall behind a
 * player who arrives, opposite the control panel), and where each of its words and faces floats. Pure: no Minecraft;
 * {@link ArenaBuilder} places its blocks and {@link ArenaChampionsBoard} its words and faces.
 *
 * <p>Seen from inside the prep room, facing it (the default place: 9 wide and 4 high):
 * <pre>
 *   y 67   [              ARENA CHAMPIONS              ]
 *          [        Normal, 5 waves, 5 monsters        ]
 *   y 66   [ 1. (face) Steve          Won all 5   3:12 ]
 *          [ 2. (face) Alex           Wave 4      2:40 ]
 *   y 65   [ 3. ...                                    ]
 *   y 64   [ 4. ...                                    ]
 *          [ 5. ...                                    ]
 * </pre>
 * The top row is the title and the settings these champions played at (those on the control panel now); under it five
 * rows, each: the place, the player's face (a player head carrying his profile, so a stock client draws his skin), his
 * name, how far he got, and his time. The words face into the room ({@link #YAW}).
 */
public final class ArenaChampionsLayout {

    /** What a line of words on the board says. */
    public enum Cell { RANK, NAME, RESULT, TIME }

    /**
     * Where one line of words floats: (x, y, z) is the bottom middle of the words, just in front of the board, and
     * {@code scale} how big. {@code row} is the champion's place (0: the title, the settings or the empty line).
     */
    public record Slot(String role, int row, double x, double y, double z, float scale, String colour) {
    }

    /** Where a face floats: its middle, and how big (blocks). */
    public record Face(int row, double x, double y, double z, float size) {
    }

    /** The way the board's words and faces turn: toward the prep room's arena side (Minecraft's yaw 180, north). */
    public static final float YAW = 180f;

    /** How high one champion's row is, in blocks. */
    static final double ROW = 0.6;

    private final ArenaPlace place;

    ArenaChampionsLayout(ArenaPlace place) {
        this.place = place;
    }

    private int h() {
        return place.prepSize() / 2;
    }

    /** How wide the board is: the prep room's inside. */
    private int width() {
        return place.prepSize();
    }

    /** The z of the prep room's back wall. */
    public int wallZ() {
        return place.prepInside().z1() + 1;
    }

    private int top() {
        return place.floorY() + ArenaPlace.PREP_HEIGHT - 1;
    }

    /** The board: the back wall's inside width, floor to roof. */
    public Box board() {
        return new Box(-h(), place.floorY(), wallZ(), h(), top(), wallZ());
    }

    /** The board's blocks. */
    public List<Fill> fills() {
        return List.of(new Fill(board(), Kind.CHAMPIONS_BOARD));
    }

    /** The z its words float at: just in front of the board's face, on the room's side. */
    double wordsZ() {
        return wallZ() - ArenaPanel.IN_FRONT;
    }

    /**
     * A point across the board as the viewer sees it, from his left edge ({@code u} 0) to his right ({@code u} the
     * width), as a world x: he faces south (+z), so his left is east (+x).
     */
    double x(double u) {
        return h() + 1 - u;
    }

    /** The bottom of champion row {@code r} (1 to {@link ArenaChampions#SHOWN}). */
    double rowBottom(int r) {
        return top() - ROW * r;
    }

    /** The middle of each column and the room it has, as parts of the width: rank, face, name, result, time. */
    private static final double[][] COLUMNS = {{0.06, 0.09}, {0.13, 0.06}, {0.35, 0.34}, {0.66, 0.24}, {0.88, 0.17}};

    private static final String[] LONGEST = {"5.", "", "MMMMMMMMMMMMMMMM", "Won all 995", "99:59"};

    /** Every line of words: the title, the settings, the empty line, and each row's four cells. */
    public List<Slot> slots() {
        double z = wordsZ();
        int w = width();
        double mid = x(w / 2.0);
        List<Slot> out = new ArrayList<>();
        float title = ArenaPanel.fit(1.9, w - 0.6, List.of(ArenaWords.CHAMPIONS_TITLE));
        out.add(new Slot("title", 0, mid, top() + 0.97 - ArenaPanel.LINE * title, z, title, "gold"));
        float sub = ArenaPanel.fit(0.9, w - 0.6, List.of("Extreme, Endless, 10 monsters"));
        out.add(new Slot("settings", 0, mid, top() + 0.06, z, sub, "yellow"));
        float none = ArenaPanel.fit(1.0, w - 0.6, List.of(ArenaWords.championsNone()));
        out.add(new Slot("none", 0, mid, rowBottom(2) + 0.1, z, none, "gray"));
        float cell = 1.2f;
        for (int c = 0; c < COLUMNS.length; c++) {
            if (!LONGEST[c].isEmpty()) {
                cell = Math.min(cell, ArenaPanel.fit(1.2, COLUMNS[c][1] * w - 0.1, List.of(LONGEST[c])));
            }
        }
        String[] roles = {"rank", null, "name", "result", "time"};
        String[] colours = {"gold", null, "white", "aqua", "green"};
        for (int r = 1; r <= ArenaChampions.SHOWN; r++) {
            double y = rowBottom(r) + (ROW - ArenaPanel.LINE * cell) / 2;
            for (int c = 0; c < COLUMNS.length; c++) {
                if (roles[c] != null) {
                    out.add(new Slot(roles[c], r, x(COLUMNS[c][0] * w), y, z, cell, colours[c]));
                }
            }
        }
        return out;
    }

    /** Where each row's face floats. */
    public List<Face> faces() {
        List<Face> out = new ArrayList<>();
        float size = (float) Math.min(ROW * 0.8, COLUMNS[1][1] * width() * 0.95);
        for (int r = 1; r <= ArenaChampions.SHOWN; r++) {
            out.add(new Face(r, x(COLUMNS[1][0] * width()), rowBottom(r) + ROW / 2, wordsZ() - size / 2, size));
        }
        return out;
    }

    /** How wide the longest words a slot may show are, in blocks (for the layout's test). */
    static double widest(Slot s) {
        String words = switch (s.role()) {
            case "title" -> ArenaWords.CHAMPIONS_TITLE;
            case "settings" -> "Extreme, Endless, 10 monsters";
            case "none" -> ArenaWords.championsNone();
            case "rank" -> LONGEST[0];
            case "name" -> LONGEST[2];
            case "result" -> LONGEST[3];
            default -> LONGEST[4];
        };
        return ArenaPanel.width(words) * s.scale();
    }

    /** The box round every line of words and face on the board, to find them in the world. */
    public Box wordsBox() {
        return new Box(-h() - 1, place.floorY() - 1, wallZ() - 2, h() + 1, top() + 2, wallZ());
    }
}
