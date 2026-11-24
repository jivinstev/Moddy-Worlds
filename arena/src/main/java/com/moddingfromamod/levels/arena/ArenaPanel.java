package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import com.moddingfromamod.levels.arena.ArenaPlace.Kind;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>The battle arena control panel, as numbers</b>: a board over the whole window of the wall the prep room shares
 * with the arena (beside and above the gate), its buttons, and where each of its words floats. Pure: no Minecraft
 * here; {@link ArenaBuilder} places its blocks and {@link ArenaBoard} its words.
 *
 * <p>Seen from the prep room (the default place: the window is 9 wide and 4 high, the gate in its middle):
 * <pre>
 *   y 67   [        BATTLE ARENA CONTROL PANEL         ]
 *   y 66   [-]  WAVE SIZE  5  [+]   |      START BATTLE
 *                                          Ready! Press START.
 *   y 65   [-] DIFFICULTY Normal [+] gate   [ (o) ]       (o) the start button, on green blocks
 *   y 64   First wave: 3 monsters  gate   [     ]       (green: the start button's blocks, 3 by 2)
 *          Last wave: 10 monsters
 *          x -4 ...........  -1   0   1 ............ 4
 * </pre>
 * The left half holds the two selectors (a down button, the value, an up button) and what the wave size means; the
 * right half the start button on a block of green 3 wide and 2 high, with its label and the battle's state above it
 * (high on the board, where the hand a player holds never covers them). A wall only 3 high (the smallest) drops the
 * line that says what the size means and has a green block 1 high. Every block is in the window, so nothing a
 * player built is in the way of a new place; the buttons stand in the prep room's first row, against the board.
 */
public final class ArenaPanel {

    /** What a button does. */
    public enum Control { SIZE_DOWN, SIZE_UP, DIFFICULTY_DOWN, DIFFICULTY_UP, START }

    /** A button: what it does, and its block (in the prep room, against the board). */
    public record Button(Control control, int x, int y, int z) {
        public int[] pos() {
            return new int[] {x, y, z};
        }
    }

    /** What a floating line of words on the board says ({@link ArenaWords#panel}). */
    public enum Words {
        TITLE, SIZE_LABEL, SIZE_VALUE, SIZE_DOWN_SIGN, SIZE_UP_SIGN, DIFFICULTY_LABEL, DIFFICULTY_VALUE,
        DIFFICULTY_DOWN_SIGN, DIFFICULTY_UP_SIGN, SIZE_MEANS, START_LABEL, STATE
    }

    /**
     * Where one line of words floats: (x, y, z) is the bottom middle of the words, just in front of the board, and
     * {@code scale} how big they are (1 is a sign's size, a quarter of a block for a line). {@code colour} is the
     * words' colour, as Minecraft names it.
     */
    public record Slot(Words words, double x, double y, double z, float scale, String colour) {
    }

    /** How far in front of the board's face the words float (so they never flicker into it). */
    static final double IN_FRONT = 0.03;
    /** One line of words' height at scale 1, in blocks (a 10 pixel line, 40 pixels a block). */
    static final double LINE = 0.25;

    private final ArenaPlace place;

    ArenaPanel(ArenaPlace place) {
        this.place = place;
    }

    /** Half the prep room's width: how many columns each side of the gate has. */
    private int h() {
        return place.prepSize() / 2;
    }

    /** How many rows the board has: the window's height, 3 or 4. */
    public int rows() {
        return place.windowTop() - place.floorY() + 1;
    }

    private int top() {
        return place.windowTop();
    }

    /** The board: the whole window of the shared wall, but the gate and the opening above it. */
    public Box board() {
        int wz = place.sharedWallZ();
        return new Box(-h(), place.floorY(), wz, h(), top(), wz);
    }

    /** True when the board's block (x, y) is the gate's or the opening above it (left as built). */
    public boolean isGateway(int x, int y) {
        return x == 0 && (y == place.floorY() || y == place.floorY() + 1);
    }

    /** The column of the start button: the middle of the right half (round up). */
    private int startX() {
        return h() / 2 + 1;
    }

    private int sizeRow() {
        return top() - 1;
    }

    private int difficultyRow() {
        return top() - 2;
    }

    private int startRow() {
        return top() - 2;
    }

    /** Every button, in the prep room's first row (one block in front of the board). */
    public List<Button> buttons() {
        int z = place.sharedWallZ() + 1;
        return List.of(
                new Button(Control.SIZE_DOWN, -h(), sizeRow(), z),
                new Button(Control.SIZE_UP, -1, sizeRow(), z),
                new Button(Control.DIFFICULTY_DOWN, -h(), difficultyRow(), z),
                new Button(Control.DIFFICULTY_UP, -1, difficultyRow(), z),
                new Button(Control.START, startX(), startRow(), z));
    }

    public Button button(Control c) {
        for (Button b : buttons()) {
            if (b.control() == c) {
                return b;
            }
        }
        throw new IllegalArgumentException(String.valueOf(c));
    }

    /**
     * The panel's blocks, in order: the board (black), the selectors' blocks (blue), the start button's blocks (green,
     * the button's column and one each side, in the right half), then the buttons on them.
     */
    public List<Fill> fills() {
        List<Fill> out = new ArrayList<>();
        int wz = place.sharedWallZ();
        int fy = place.floorY();
        // the board, in three boxes round the gateway (the gate and the opening above it are left as built)
        out.add(new Fill(new Box(-h(), fy, wz, -1, top(), wz), Kind.PANEL_BOARD));
        out.add(new Fill(new Box(1, fy, wz, h(), top(), wz), Kind.PANEL_BOARD));
        out.add(new Fill(new Box(0, fy + 2, wz, 0, top(), wz), Kind.PANEL_BOARD));
        for (Button b : buttons()) {
            if (b.control() == Control.START) {
                int x0 = Math.max(1, b.x() - 1);
                int x1 = Math.min(h(), b.x() + 1);
                out.add(new Fill(new Box(x0, Math.max(fy, b.y() - 1), wz, x1, b.y(), wz), Kind.PANEL_START));
            } else {
                out.add(new Fill(new Box(b.x(), b.y(), wz, b.x(), b.y(), wz), Kind.PANEL_SELECT));
            }
        }
        for (Button b : buttons()) {
            Kind k = b.control() == Control.START ? Kind.START_BUTTON : Kind.SELECT_BUTTON;
            out.add(new Fill(new Box(b.x(), b.y(), b.z(), b.x(), b.y(), b.z()), k));
        }
        return out;
    }

    /** One block of the panel: where, and what it is. */
    public record Placed(int x, int y, int z, Kind kind) {
    }

    /**
     * The panel's blocks, each once, as {@link #fills()} leave them (a later fill wins), in an order that puts every
     * button after the block it hangs on.
     */
    public List<Placed> blocks() {
        java.util.Map<List<Integer>, Kind> m = new java.util.LinkedHashMap<>();
        for (Fill f : fills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        m.put(List.of(x, y, z), f.kind());
                    }
                }
            }
        }
        List<Placed> out = new ArrayList<>();
        m.forEach((k, kind) -> out.add(new Placed(k.get(0), k.get(1), k.get(2), kind)));
        return out;
    }

    /** The z the words float at: just in front of the board's face, on the prep room's side. */
    double wordsZ() {
        return place.sharedWallZ() + 1 + IN_FRONT;
    }

    /**
     * How long a line of words is at scale 1, in blocks: Minecraft's default font, 40 pixels a block, each letter its
     * width and a pixel's gap (most letters 6, a space 4, {@code i ! : . ,} 2, {@code l} 3, {@code t I} 4, {@code f k}
     * 5). An estimate good to a few pixels, which is what keeps every line on the board. Pure.
     */
    public static double width(String line) {
        int px = 0;
        for (char c : line.toCharArray()) {
            px += switch (c) {
                case 'i', '!', ':', '.', ',', '\'', '|' -> 2;
                case 'l' -> 3;
                case 't', 'I', ' ', '(', ')' -> 4;
                case 'f', 'k', '<', '>' -> 5;
                default -> 6;
            };
        }
        return Math.max(0, px - 1) / 40.0;
    }

    /** The scale that fits the longest of {@code samples} in {@code room} blocks, at most {@code max}, at least 0.3. */
    static float fit(double max, double room, List<String> samples) {
        double w = 0;
        for (String s : samples) {
            w = Math.max(w, width(s));
        }
        return (float) Math.max(0.3, Math.min(max, w <= 0 ? max : room / w));
    }

    /** The longest words each place on the board can show (the most waves, the most monsters a wave may bring). */
    static List<String> longest(Words w) {
        List<String> out = new ArrayList<>();
        switch (w) {
            case TITLE -> out.add(ArenaWords.PANEL_TITLE);
            case SIZE_LABEL -> out.add(ArenaWords.PANEL_SIZE);
            case DIFFICULTY_LABEL -> out.add(ArenaWords.PANEL_DIFFICULTY);
            case SIZE_VALUE -> out.add(String.valueOf(ArenaSettings.MAX_SIZE));
            case DIFFICULTY_VALUE -> {
                for (ArenaDifficulty d : ArenaDifficulty.values()) {
                    out.add(d.word());
                }
            }
            case SIZE_DOWN_SIGN, SIZE_UP_SIGN, DIFFICULTY_DOWN_SIGN, DIFFICULTY_UP_SIGN -> out.add("+");
            case SIZE_MEANS -> {
                int most = ArenaWaves.MAX_PER_WAVE;
                out.add("First wave: " + most + " monsters");
                out.add("Last wave: " + most + " monsters");
                out.add("1 wave: " + most + " monsters");
                out.add("No waves in the rules");
            }
            case START_LABEL -> out.add(ArenaWords.PANEL_START);
            case STATE -> {
                for (ArenaMatch.Phase p : ArenaMatch.Phase.values()) {
                    out.add(ArenaWords.panelState(p, ArenaWaves.MAX_WAVES, ArenaWaves.MAX_WAVES));
                }
            }
        }
        return out;
    }

    /** The middle of the right half's words: over the start button, unless that leaves them too little room. */
    double rightMiddle() {
        double c = startX() + 0.5;
        return rightRoom(c) >= 2.0 ? c : (h() + 2) / 2.0;
    }

    /** How wide words centred at {@code c} may be in the right half (x 1 to h + 1), with a margin. */
    private double rightRoom(double c) {
        return 2 * Math.min(c - 1, h() + 1 - c) - 0.2;
    }

    /** Every line of words on the board, with where it floats and how big. */
    public List<Slot> slots() {
        double z = wordsZ();
        double left = -h() / 2.0;                   // the middle of the left half (x -h to 0)
        double right = rightMiddle();
        double down = -h() + 0.5;
        double up = -0.5;
        double between = h() >= 3 ? h() - 2 - 0.15 : h() - 0.2;    // between a selector's two buttons
        double half = h() - 0.2;
        double rightRoom = rightRoom(right);
        float title = fit(2.1, 2 * h() + 1 - 0.6, longest(Words.TITLE));
        List<Slot> out = new ArrayList<>();
        out.add(new Slot(Words.TITLE, 0.5, top() + 0.5 - LINE * title / 2, z, title, "gold"));
        int sr = sizeRow();
        out.add(new Slot(Words.SIZE_LABEL, left, sr + 0.72, z, fit(1.0, between, longest(Words.SIZE_LABEL)), "white"));
        out.add(new Slot(Words.SIZE_VALUE, left, sr + 0.08, z, fit(2.4, between, longest(Words.SIZE_VALUE)),
                "yellow"));
        out.add(new Slot(Words.SIZE_DOWN_SIGN, down, sr + 0.64, z, 1.3f, "white"));
        out.add(new Slot(Words.SIZE_UP_SIGN, up, sr + 0.64, z, 1.3f, "white"));
        int dr = difficultyRow();
        out.add(new Slot(Words.DIFFICULTY_LABEL, left, dr + 0.72, z,
                fit(1.0, between, longest(Words.DIFFICULTY_LABEL)), "white"));
        out.add(new Slot(Words.DIFFICULTY_VALUE, left, dr + 0.14, z,
                fit(1.8, between, longest(Words.DIFFICULTY_VALUE)), "yellow"));
        out.add(new Slot(Words.DIFFICULTY_DOWN_SIGN, down, dr + 0.64, z, 1.3f, "white"));
        out.add(new Slot(Words.DIFFICULTY_UP_SIGN, up, dr + 0.64, z, 1.3f, "white"));
        if (rows() >= 4) {
            out.add(new Slot(Words.SIZE_MEANS, left, top() - 3 + 0.2, z, fit(1.0, half, longest(Words.SIZE_MEANS)),
                    "aqua"));
        }
        // the start label, and the battle's state under it, in the row above the start button
        out.add(new Slot(Words.START_LABEL, right, top() - 1 + 0.46, z,
                fit(1.4, rightRoom, longest(Words.START_LABEL)), "green"));
        out.add(new Slot(Words.STATE, right, top() - 1 + 0.1, z, fit(1.0, rightRoom, longest(Words.STATE)), "aqua"));
        return out;
    }

    /** The box round every line of words (and its buttons), to find the board's words in the world. */
    public Box wordsBox() {
        int z = place.sharedWallZ() + 1;
        return new Box(-h() - 1, place.floorY() - 1, z - 1, h() + 1, top() + 2, z + 1);
    }
}
