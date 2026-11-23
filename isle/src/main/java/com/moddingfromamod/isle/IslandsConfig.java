package com.moddingfromamod.isle;

/**
 * <b>The switch: is this an islands world?</b> — read once, from one boot property.
 *
 * <p>The world manifest says it ({@code worlds/isle.json} → {@code islands.spacing}). A server's launcher
 * passes it to the JVM as {@code -Dmoddingfromamod.islands.spacing=N}; a world made with World Type
 * "Moddy Isle" reads it from the manifest in this jar ({@link #useWorld}).
 * Absent, empty or 0 means OFF, which is every world that is not an Isle: nothing in the
 * {@code isle} package does anything there.
 *
 * <p>A boot property rather than a live knob because the grid is fixed when the world's first island
 * is made ({@link IslandStore} keeps the recorded spacing against a later change), and switching
 * islands on under a world full of children would hand every one of them a new island.
 */
public final class IslandsConfig {

    /** The JVM property the render sets. */
    public static final String PROPERTY = "moddingfromamod.islands.spacing";

    private IslandsConfig() {
    }

    /** #2750: the manifest's spacing, set while the Isle module runs on a world it claimed (no property there). */
    private static volatile int fromWorld;

    /** The spacing this world's islands want, or 0 when islands are off: the property (a server's launcher), else the world's. */
    public static int spacing() {
        int p = parse(System.getProperty(PROPERTY));
        return p > 0 ? p : fromWorld;
    }

    /** Set by {@link IsleModule} when it starts with no property (a world made as an Isle); 0 again at its stop. */
    static void useWorld(int spacing) {
        fromWorld = Math.max(0, spacing);
    }

    public static boolean enabled() {
        return spacing() > 0;
    }

    /** "1024" → 1024; absent, blank, "0", "off" or garbage → 0 (off). Never throws. */
    static int parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
