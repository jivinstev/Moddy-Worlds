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
 *          [      Best for Steve: wave 7 in 3:12       ]
 *   y 66   [-] DIFFICULTY Normal [+]  |      START BATTLE
 *                                            Ready! Press START.
 *   y 65   [-]   WAVES      5    [+] gate   [ (o) ]       (o) the start button, on green blocks 3 by 1
 *   y 64     First wave: 3 monsters  gate   Boss wave: 5
 *                                           Hotbar wishes: 5 a wave
 *          x -4 ...........  -1   0   1 ............ 4
 * </pre>
 * The left half holds the two selectors (a down button, the value, an up button), DIFFICULTY and WAVES (how many
 * waves), at eye height, and under them how many monsters the first wave brings (the difficulty sets it: there is no
 * selector for it); the right half the start button on a green block 3 wide, with its label and the battle's state
 * above it (high on the board, where the hand a player holds never covers them) and what the settings mean under it
 * (the boss waves, the hotbar wishes a wave). The top row is the title and the line with the best run of whoever
 * pressed a button last, at these settings. A wall only 3 high (the smallest) has no top row: no title, no best, and
 * nothing under the start button. Every block is in the window, so nothing a player built is in the way of a new place;
 * the buttons stand in the prep room's first row, against the board.
 *
 * <p>Until 0.1.6 the panel had a third selector, MONSTERS, in the top selector row ({@link #oldButtons}): a world built
 * then loses its buttons ({@link ArenaBuilder#addControl}).
 */
public final class ArenaPanel {

    /** What a button does. */
    public enum Control { DIFFICULTY_DOWN, DIFFICULTY_UP, WAVES_DOWN, WAVES_UP, START }

    /** A button: what it does, and its block (in the prep room, against the board). */
    public record Button(Control control, int x, int y, int z) {
        public int[] pos() {
            return new int[] {x, y, z};
        }
    }

    /** What a floating line of words on the board says ({@link ArenaWords#panel}). */
    public enum Words {
        TITLE, BEST, DIFFICULTY_LABEL, DIFFICULTY_VALUE, DIFFICULTY_DOWN_SIGN, DIFFICULTY_UP_SIGN, WAVES_LABEL,
        WAVES_VALUE, WAVES_DOWN_SIGN, WAVES_UP_SIGN, FIRST_WAVE, MEANS, START_LABEL, STATE
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

    /** True when the board has a top row for the title (4 rows); a 3-row board starts with the selectors. */
    public boolean hasTitleRow() {
        return rows() >= 4;
    }

    /** The top selector row: the first under the title row. */
    private int difficultyRow() {
        return hasTitleRow() ? top() - 1 : top();
    }

    private int wavesRow() {
        return difficultyRow() - 1;
    }

    /** The bottom row: the first wave's monsters on the left, what the settings mean on the right. */
    private int meansRow() {
        return difficultyRow() - 2;
    }

    private int startRow() {
        return wavesRow();
    }

    /**
     * The selector buttons of the panel before 0.1.6, which had MONSTERS in the top selector row, DIFFICULTY under it
     * and WAVES at the bottom: where they stood (in the prep room's first row), for taking away what the new panel does
     * not use.
     */
    public List<int[]> oldButtons() {
        int z = place.sharedWallZ() + 1;
        int r = difficultyRow();
        List<int[]> out = new ArrayList<>();
        for (int row = r; row >= r - 2; row--) {
            out.add(new int[] {-h(), row, z});
            out.add(new int[] {-1, row, z});
        }
        return out;
    }

    /** Every button, in the prep room's first row (one block in front of the board). */
    public List<Button> buttons() {
        int z = place.sharedWallZ() + 1;
        return List.of(
                new Button(Control.DIFFICULTY_DOWN, -h(), difficultyRow(), z),
                new Button(Control.DIFFICULTY_UP, -1, difficultyRow(), z),
                new Button(Control.WAVES_DOWN, -h(), wavesRow(), z),
                new Button(Control.WAVES_UP, -1, wavesRow(), z),
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
     * the button's block and one each side, in the right half), then the buttons on them.
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
                out.add(new Fill(new Box(x0, b.y(), wz, x1, b.y(), wz), Kind.PANEL_START));
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
            case BEST -> {
                out.add("Best for MMMMMMMMMMMMMMMM: wave 995 in 99:59");
                out.add("MMMMMMMMMMMMMMMM: no best at these settings yet");
                out.add(ArenaWords.bestNotRecorded("MMMMMMMMMMMMMMMM", ArenaUnranked.Mode.SPECTATOR));
                out.add("Press a button to see your best");
            }
            case DIFFICULTY_LABEL -> out.add(ArenaWords.PANEL_DIFFICULTY);
            case WAVES_LABEL -> out.add(ArenaWords.PANEL_WAVES);
            case WAVES_VALUE -> {
                out.add(ArenaWords.ENDLESS);
                out.add(String.valueOf(ArenaSettings.MAX_WAVE_COUNT));
            }
            case DIFFICULTY_VALUE -> {
                for (ArenaDifficulty d : ArenaDifficulty.values()) {
                    out.add(d.word());
                }
            }
            case DIFFICULTY_DOWN_SIGN, DIFFICULTY_UP_SIGN, WAVES_DOWN_SIGN, WAVES_UP_SIGN -> out.add("+");
            case FIRST_WAVE -> {
                out.add(ArenaWords.firstWave(ArenaWaves.MAX_PER_WAVE + 1));
                out.add(ArenaWords.firstWave(-1));
            }
            case MEANS -> {
                out.add("Boss waves: 5, 10, 15, 20");
                out.add("A boss every 50 waves");
                out.add("No boss waves");
                out.add(ArenaWords.wishesMeans(ArenaWishes.MAX_PER_WAVE));
            }
            case START_LABEL -> {
                out.add(ArenaWords.PANEL_START);
                out.add(ArenaWords.PANEL_READY);
            }
            case STATE -> {
                int most = ArenaSettings.MAX_WAVE_COUNT;
                for (ArenaMatch.Phase p : ArenaMatch.Phase.values()) {
                    out.add(ArenaWords.panelState(p, most, most, false));
                    out.add(ArenaWords.panelState(p, most, most, true));
                    out.add(ArenaWords.panelState(p, most - 1, most, true, ArenaWaves.MAX_BOSS_BREATHER));
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
        double rightRoom = rightRoom(right);
        List<Slot> out = new ArrayList<>();
        if (hasTitleRow()) {
            float title = fit(2.1, 2 * h() + 1 - 0.6, longest(Words.TITLE));
            out.add(new Slot(Words.TITLE, 0.5, top() + 0.97 - LINE * title, z, title, "gold"));
            float best = fit(0.9, 2 * h() + 1 - 0.6, longest(Words.BEST));
            out.add(new Slot(Words.BEST, 0.5, top() + 0.08, z, best, "white"));
        }
        row(out, Words.DIFFICULTY_LABEL, Words.DIFFICULTY_VALUE, Words.DIFFICULTY_DOWN_SIGN, Words.DIFFICULTY_UP_SIGN,
                difficultyRow(), left, down, up, between, z, 2.2);
        row(out, Words.WAVES_LABEL, Words.WAVES_VALUE, Words.WAVES_DOWN_SIGN, Words.WAVES_UP_SIGN, wavesRow(), left,
                down, up, between, z, 2.2);
        // under the selectors, across the left half: how many monsters the first wave brings (the difficulty sets it)
        float first = fit(1.3, h() - 0.3, longest(Words.FIRST_WAVE));
        out.add(new Slot(Words.FIRST_WAVE, left, meansRow() + 0.5 - 0.5 * LINE * first, z, first, "aqua"));
        // the start label, and the battle's state under it, in the row above the start button
        int lr = difficultyRow();
        out.add(new Slot(Words.START_LABEL, right, lr + 0.46, z, fit(1.4, rightRoom, longest(Words.START_LABEL)),
                "green"));
        out.add(new Slot(Words.STATE, right, lr + 0.1, z, fit(1.0, rightRoom, longest(Words.STATE)), "aqua"));
        if (hasTitleRow()) {
            float means = fit(1.0, rightRoom, longest(Words.MEANS));
            // two lines (the boss waves, the hotbar wishes a wave), centred on the row
            out.add(new Slot(Words.MEANS, right, meansRow() + 0.5 - LINE * means, z, means, "aqua"));
        }
        return out;
    }

    /** One selector's words: its label high in its row, its value under it, and a sign on each button. */
    private static void row(List<Slot> out, Words label, Words value, Words downSign, Words upSign, int r, double left,
                            double down, double up, double between, double z, double valueMax) {
        float vs = fit(valueMax, between, longest(value));
        out.add(new Slot(label, left, r + 0.68, z, fit(1.2, between, longest(label)), "white"));
        out.add(new Slot(value, left, r + 0.08 + Math.max(0, (0.6 - LINE * vs) / 2), z, vs, "yellow"));
        out.add(new Slot(downSign, down, r + 0.64, z, 1.3f, "white"));
        out.add(new Slot(upSign, up, r + 0.64, z, 1.3f, "white"));
    }

    /**
     * One line of the wish ladder board's words ({@link ArenaLadderLayout}), on the prep room's west wall beside the
     * control panel, facing into the room: (x, y, z) is the bottom middle of the words; {@code yaw} which way they face
     * (-90: east, into the room); {@code scale} how big.
     */
    public record LadderSlot(ArenaLadderLayout.Role role, String text, String colour, boolean bold, double x, double y,
                             double z, float yaw, float scale) {
    }

    /**
     * The wish ladder board's blocks: the prep room's west wall, from the floor to the room's top, its first four
     * columns beside the shared wall (the panel's wall). Seen from the room, they are a black board on the left of the
     * panel.
     */
    public Box ladderBoard() {
        int z0 = place.sharedWallZ() + 1;
        return new Box(-h() - 1, place.floorY(), z0, -h() - 1, place.floorY() + ArenaPlace.PREP_HEIGHT - 1, z0 + 3);
    }

    /** Where each line of the wish ladder board's words floats, and how big ({@link ArenaLadderLayout#lay}). */
    public List<LadderSlot> ladderSlots(ArenaWords.LadderBoardWords words) {
        Box b = ladderBoard();
        double width = b.z1() - b.z0() + 1;
        double x = -h() + IN_FRONT;                                     // the west wall's inside face
        double z = b.z0() + width / 2;                                  // the board's middle
        List<LadderSlot> out = new ArrayList<>();
        for (ArenaLadderLayout.Line l : ArenaLadderLayout.lay(words, width, b.y1() - b.y0() + 1)) {
            out.add(new LadderSlot(l.role(), l.text(), l.colour(), l.bold(), x, b.y0() + l.bottom(), z, -90f,
                    l.scale()));
        }
        return out;
    }

    /** The box round the wish ladder board's words, to find them in the world. */
    public Box ladderWordsBox() {
        Box b = ladderBoard();
        return new Box(b.x0(), b.y0() - 1, b.z0() - 1, b.x0() + 2, b.y1() + 1, b.z1() + 1);
    }

    /** The box round every line of words (and its buttons), to find the board's words in the world. */
    public Box wordsBox() {
        int z = place.sharedWallZ() + 1;
        return new Box(-h() - 1, place.floorY() - 1, z - 1, h() + 1, top() + 2, z + 1);
    }
}
