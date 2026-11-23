package com.moddingfromamod.isle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** #1785 step 4: the private-island guard reads the Nether as the overworld ×8. */
class IslandGridColumnTest {

    @Test
    void theOverworldIsItselfTheNetherIsTimesEightTheEndIsNothing() {
        assertArrayEquals(new double[]{1024.5, -2048.0},
                IslandGrid.overworldColumn("minecraft:overworld", 1024.5, -2048.0));
        assertArrayEquals(new double[]{1024.0, -1024.0},
                IslandGrid.overworldColumn("minecraft:the_nether", 128.0, -128.0),
                "a neighbour's island is only 128 Nether blocks away");
        assertNull(IslandGrid.overworldColumn("minecraft:the_end", 0, 0));
    }
}
