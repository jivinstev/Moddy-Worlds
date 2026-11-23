package com.moddingfromamod.isle;

import java.util.List;

/**
 * <b>The sign beside the starter chest</b> — a new player dropped on a small island with a chest of lava
 * and ice has no idea what to do with them, so the first thing a child reads on his island is what to do with what is in that chest. Pure, so its words and place are a
 * Gate A test ({@code StarterSignTest}); {@link IslandPlacer} puts it on every newly copied island.
 *
 * <p>Where: the template's chest is at (7, 3, 1) facing west, on grass (read out of
 * {@code starter_island.nbt}); the sign stands at (7, 3, 2) — beside the chest on the same grass,
 * never in front of it, so it blocks neither the chest's lid nor the walk to it — turned to face WEST,
 * toward where he arrives (the bedrock column at (3, 0, 1)). Waxed, so nobody can rewrite it.
 *
 * <p>Islands placed before have no sign; nothing goes back and adds one (a finished island is
 * his, and the intro and the guide book say the same thing).
 */
public final class StarterSign {

    /** The sign's place inside the template. */
    public static final int X = 7;
    public static final int Y = 3;
    public static final int Z = 2;

    /** {@code StandingSignBlock.ROTATION}: 4 faces west, toward his arrival. */
    public static final int ROTATION = 4;

    /** A sign shows four lines of about 15 characters (90 pixels). */
    public static final int MAX_CHARS = 15;

    /** The four lines. */
    public static final List<String> LINES = List.of(
            "Open the chest!",
            "Lava + water",
            "= cobblestone",
            "Watch has goals");

    private StarterSign() {
    }
}
