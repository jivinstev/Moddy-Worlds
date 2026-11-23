package com.moddingfromamod.isle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * <b>Which islands rest</b> (#1785, the owner's decision): on a world
 * open to Wish Crafters most strangers come once, and every one of them was given an island. A
 * CRAFTER's island whose owner has not been seen for {@code days} is retired — his claim is let go, and
 * the cell is NEVER handed to anyone else (it still holds what he built, and nobody may be put on top
 * of that). If he comes back he gets a fresh island and is told why.
 *
 * <p>Never retired: a Wish Magician's or an operator's ({@code keep}), the canary's service island, and
 * anyone online. "Seen" is the last join or leave the island service recorded, or the island's own
 * creation when it has none (an island made before #1785 step 7 counts from then). Pure.
 */
public final class IslandReclaim {

    private IslandReclaim() {
    }

    public static final int DEFAULT_DAYS = 14;

    /** The owners whose islands rest now. */
    public static List<UUID> due(List<Island> islands, Map<UUID, Long> seen, Predicate<UUID> keep, long nowMs,
                                 int days) {
        List<UUID> out = new ArrayList<>();
        if (days <= 0) {
            return out;
        }
        long after = days * 24L * 3600_000L;
        for (Island i : islands) {
            if (i == null || IslandRegistry.isService(i) || keep.test(i.owner())) {
                continue;
            }
            long last = Math.max(i.createdAtMs(), seen.getOrDefault(i.owner(), 0L));
            if (last > 0 && nowMs - last >= after) {
                out.add(i.owner());
            }
        }
        return out;
    }

    /** What a Crafter whose island rested is told when he comes back. */
    public static String welcomeBack(int days) {
        return "✨ Welcome back! Your old island rested after " + days + " days away — here's a fresh one to grow.";
    }
}
