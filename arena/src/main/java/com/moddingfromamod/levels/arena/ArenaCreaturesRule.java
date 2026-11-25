package com.moddingfromamod.levels.arena;

import com.google.gson.JsonObject;
import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>{@code arena_creatures}, the arena's fourth rule type</b>: the numbers of the wished creatures' battle roles
 * ({@link ArenaCreatures} checks them). A file with a wrong value is refused whole, in words, as the core refuses any bad
 * rule. A world whose rules carry none gets {@link ArenaCreatures#DEFAULT}. Pure: no Minecraft.
 */
public final class ArenaCreaturesRule implements WorldRuleType {

    /** The file's spelling: {@code "type": "arena_creatures"}. */
    public static final String KEY = "arena_creatures";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Map<String, Object> params(JsonObject raw, String where, List<String> err) {
        ArenaCreatures.Parsed p = ArenaCreatures.parse(ArenaRule.values(raw));
        if (!p.ok()) {
            err.add(where + ": " + KEY + " " + p.problem());
            return new LinkedHashMap<>();
        }
        return p.creatures().toParams();
    }

    /** The numbers a world's rules name, or {@link ArenaCreatures#DEFAULT} when they name none. */
    public static ArenaCreatures creaturesOf(WorldRules rules) {
        WorldRule r = rules == null ? null : rules.first(KEY);
        if (r == null) {
            return ArenaCreatures.DEFAULT;
        }
        ArenaCreatures.Parsed p = ArenaCreatures.parse(r.params());
        return p.ok() ? p.creatures() : ArenaCreatures.DEFAULT;
    }
}
