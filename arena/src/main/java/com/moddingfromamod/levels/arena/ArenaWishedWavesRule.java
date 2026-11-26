package com.moddingfromamod.levels.arena;

import com.google.gson.JsonObject;
import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>{@code arena_wished_waves}, the arena's rule type for wished waves</b> (Moddy-Worlds#55): the ramp, the champions
 * and the boss ({@link ArenaWishedWaves} checks them). Every key is optional. A file with a wrong value is refused
 * whole, in words, as the core refuses any bad rule. A world whose rules carry none gets
 * {@link ArenaWishedWaves#DEFAULT}. A change applies to the next battle.
 */
public final class ArenaWishedWavesRule implements WorldRuleType {

    /** The file's spelling: {@code "type": "arena_wished_waves"}. */
    public static final String KEY = "arena_wished_waves";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Map<String, Object> params(JsonObject raw, String where, List<String> err) {
        ArenaWishedWaves.Parsed p = ArenaWishedWaves.parse(ArenaWavesRule.plainMap(raw), ArenaWishedWaves::builderBody);
        if (!p.ok()) {
            err.add(where + ": " + KEY + " " + p.problem());
            return new LinkedHashMap<>();
        }
        return p.waves().toParams();
    }

    /** The wished waves a world's rules name, or {@link ArenaWishedWaves#DEFAULT} when they name none. */
    public static ArenaWishedWaves of(WorldRules rules) {
        WorldRule r = rules == null ? null : rules.first(KEY);
        if (r == null) {
            return ArenaWishedWaves.DEFAULT;
        }
        ArenaWishedWaves.Parsed p = ArenaWishedWaves.parse(r.params(), ArenaWishedWaves::builderBody);
        return p.ok() ? p.waves() : ArenaWishedWaves.DEFAULT;
    }
}
