package com.moddingfromamod.levels.arena;

import com.google.gson.JsonObject;
import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>{@code arena_wishes}, the arena's third rule type</b>: how many Magician wishes a player may make in one prep, and
 * how long a kept thing rests after it is fired in a battle ({@link ArenaWishes} checks them). A file with a wrong value
 * is refused whole, in words, as the core refuses any bad rule. A world whose rules carry none gets
 * {@link ArenaWishes#DEFAULT}. Pure: no Minecraft.
 */
public final class ArenaWishesRule implements WorldRuleType {

    /** The file's spelling: {@code "type": "arena_wishes"}. */
    public static final String KEY = "arena_wishes";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Map<String, Object> params(JsonObject raw, String where, List<String> err) {
        ArenaWishes.Parsed p = ArenaWishes.parse(ArenaRule.values(raw));
        if (!p.ok()) {
            err.add(where + ": " + KEY + " " + p.problem());
            return new LinkedHashMap<>();
        }
        return p.wishes().toParams();
    }

    /** The tunables a world's rules name, or {@link ArenaWishes#DEFAULT} when they name none. */
    public static ArenaWishes wishesOf(WorldRules rules) {
        WorldRule r = rules == null ? null : rules.first(KEY);
        if (r == null) {
            return ArenaWishes.DEFAULT;
        }
        ArenaWishes.Parsed p = ArenaWishes.parse(r.params());
        return p.ok() ? p.wishes() : ArenaWishes.DEFAULT;
    }
}
