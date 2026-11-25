package com.moddingfromamod.levels.arena;

import com.google.gson.JsonObject;
import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>{@code arena_ladder}, the arena's unlock ladder as a rule type</b>: the limits each player's makes are fitted to,
 * rising as he beats bosses, and what stays locked until then ({@link ArenaLadder} checks them). A file with a wrong
 * ladder (an unknown fact, kind or tag, a limit that falls, a lock no step opens) is refused whole, in words, as the
 * core refuses any bad rule. A world whose rules carry none gets {@link ArenaLadder#DEFAULT}. Pure: no Minecraft.
 */
public final class ArenaLadderRule implements WorldRuleType {

    /** The file's spelling: {@code "type": "arena_ladder"}. */
    public static final String KEY = "arena_ladder";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Map<String, Object> params(JsonObject raw, String where, List<String> err) {
        ArenaLadder.Parsed p = ArenaLadder.parse(ArenaWavesRule.plainMap(raw));
        if (!p.ok()) {
            err.add(where + ": " + KEY + " " + p.problem());
            return new LinkedHashMap<>();
        }
        return p.ladder().toParams();
    }

    /** The last rules read and their ladder: a menu asks per tile per draw, and the rules change only on a reload. */
    private record Read(WorldRules rules, ArenaLadder ladder) {
    }

    private static volatile Read last;

    /** The ladder a world's rules name, or {@link ArenaLadder#DEFAULT} when they name none. */
    public static ArenaLadder ladderOf(WorldRules rules) {
        Read l = last;
        if (l != null && l.rules() == rules) {
            return l.ladder();
        }
        ArenaLadder ladder = read(rules);
        last = new Read(rules, ladder);
        return ladder;
    }

    private static ArenaLadder read(WorldRules rules) {
        WorldRule r = rules == null ? null : rules.first(KEY);
        if (r == null) {
            return ArenaLadder.DEFAULT;
        }
        ArenaLadder.Parsed p = ArenaLadder.parse(r.params());
        return p.ok() ? p.ladder() : ArenaLadder.DEFAULT;
    }
}
