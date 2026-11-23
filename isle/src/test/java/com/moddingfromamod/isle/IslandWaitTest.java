package com.moddingfromamod.isle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** the rules of an island's wait, Minecraft-free. Where they RUN is {@code IslandWaitGameTest}. */
class IslandWaitTest {

    @Test
    void theSpanCoversTheTemplateAndTheRingItsShapeUpdatesRead() {
        // the shipped grid: origin on a chunk corner, the 8 x 8 template's corner 3 west / 1 north
        assertArrayEquals(new int[]{-1, -1, 0, 0}, IslandWait.chunkSpan(-3, -1, 8, 8));
        // a box flush with a chunk's east edge still asks for the chunk east of it: the edge ring
        assertArrayEquals(new int[]{0, 0, 1, 0}, IslandWait.chunkSpan(8, 4, 8, 8));
        // ... and flush with the west edge, the chunk west of it
        assertArrayEquals(new int[]{-1, 0, 0, 0}, IslandWait.chunkSpan(0, 4, 8, 8));
        // an origin off the chunk grid (the GameTest grids' bases are 1000 apart, not a multiple of 16)
        assertArrayEquals(new int[]{1312, 1312, 1312, 1312}, IslandWait.chunkSpan(20997, 20999, 8, 8));
        assertThrows(IllegalArgumentException.class, () -> IslandWait.chunkSpan(0, 0, 0, 8));
    }

    @Test
    void aWaitingChildIsToldAfterTenSecondsAndThenEveryThirtyNeverEveryTick() {
        int told = 0;
        int first = -1;
        for (int t = 0; t <= 20 * 70; t++) {
            if (IslandWait.nudge(t)) {
                told++;
                if (first < 0) {
                    first = t;
                }
            }
        }
        assertEquals(IslandWait.NUDGE_FIRST_TICKS, first, "first told at 10 s");
        assertEquals(3, told, "70 s of waiting is told three times (10, 40, 70 s), not every tick");
        assertFalse(IslandWait.nudge(0));
    }

    @Test
    void theTicketIsRenewedBeforeItLapses() {
        assertTrue(IslandWait.TICKET_REFRESH_TICKS < IslandWait.TICKET_LIFESPAN_TICKS,
                "a ticket that lapses between renewals drops his chunks mid-generation");
        assertFalse(IslandWait.refreshTicket(0), "arrive() has just asked");
        assertTrue(IslandWait.refreshTicket(IslandWait.TICKET_REFRESH_TICKS));
        assertFalse(IslandWait.refreshTicket(IslandWait.TICKET_REFRESH_TICKS + 1));
    }

    @Test
    void onlyTheHeadOfTheQueueBuysPriorityWithChunks() {
        // measured: given to every island in a burst, a radius is the backlog --
        // 40 in a burst, island p95 40 s at 0, 64 s at 1, nothing placed in 60 s at 3
        assertEquals(0, IslandWait.TICKET_DISTANCE,
                "a wider ticket makes ~(2d+2)^2 chunks FULL per island instead of four");
        // ... and with none, an island waits behind every child already home (level 33 vs 31)
        assertTrue(33 - IslandWait.HEAD_TICKET_DISTANCE < 31, "the head must rank ahead of view chunks");
        assertTrue(IslandWait.escalate(0, IslandWait.ESCALATE_AFTER_TICKS));
        assertTrue(IslandWait.escalate(IslandWait.HEAD - 1, 10_000));
        assertFalse(IslandWait.escalate(IslandWait.HEAD, 10_000), "only the head of the queue");
        assertFalse(IslandWait.escalate(0, IslandWait.ESCALATE_AFTER_TICKS - 1),
                "a quiet server, whose island comes in a second or two, never pays for it");
        assertTrue(IslandWait.PLACEMENTS_PER_TICK >= 1, "a budget of nothing places nothing");
    }
}
