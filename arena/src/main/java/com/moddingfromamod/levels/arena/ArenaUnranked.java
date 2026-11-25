package com.moddingfromamod.levels.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Who is not on the Champions board this battle</b>: a fighter who is in Creative or Spectator at ANY point of a
 * battle has no run recorded for it, since in Creative no monster attacks him and nothing can knock him out, and in
 * Spectator he is not fighting at all. Each such player is told once per battle, the first time he is seen in one of
 * them (at the start, or when he switches); switching back changes nothing for that battle. Pure: no Minecraft;
 * {@link ArenaBattle} looks at the players' game modes, says the line and skips the run.
 */
final class ArenaUnranked {

    /** A player's game mode, as far as the board cares. */
    enum Mode { PLAY, CREATIVE, SPECTATOR }

    /** The players seen in Creative or Spectator this battle, and the mode they were first seen in. */
    private final Map<UUID, Mode> seen = new HashMap<>();

    /**
     * A player was seen in a game mode during the battle. The mode to tell him about (the first time he is seen in
     * Creative or Spectator this battle), else null: in Survival or Adventure, or already told.
     */
    synchronized Mode saw(UUID player, Mode mode) {
        if (player == null || mode == null || mode == Mode.PLAY || seen.containsKey(player)) {
            return null;
        }
        seen.put(player, mode);
        return mode;
    }

    /** Whether this player's run goes on the board: never once he was seen in Creative or Spectator this battle. */
    synchronized boolean recorded(UUID player) {
        return player != null && !seen.containsKey(player);
    }

    /** Of the battle's runs, by player, those that go on the board (in the same order). */
    synchronized <T> Map<UUID, T> onlyRecorded(Map<UUID, T> runs) {
        Map<UUID, T> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, T> e : runs.entrySet()) {
            if (recorded(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** The players with no run this battle. */
    synchronized Set<UUID> unranked() {
        return Set.copyOf(seen.keySet());
    }

    /** A new battle: everyone may be recorded again (and is told again if he is in Creative). */
    synchronized void clear() {
        seen.clear();
    }
}
