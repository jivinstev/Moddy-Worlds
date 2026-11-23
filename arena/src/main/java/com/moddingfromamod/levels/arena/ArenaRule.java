package com.moddingfromamod.levels.arena;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>{@code arena_place}, the arena's own rule type</b> in a world's rules file: the arena's size, the prep room's and
 * the wall's height ({@link ArenaPlace#parse} checks them). Registered by {@link ArenaPlugin}, so the core reads a
 * rules file that carries it, refuses one whose sizes are wrong, and hands the rule back through
 * {@link WorldRules#first(String)}. It denies nothing itself. Pure: no Minecraft.
 */
public final class ArenaRule implements WorldRuleType {

    /** The file's spelling: {@code "type": "arena_place"}. */
    public static final String KEY = "arena_place";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Map<String, Object> params(JsonObject raw, String where, List<String> err) {
        ArenaPlace.Parsed p = ArenaPlace.parse(values(raw));
        if (!p.ok()) {
            err.add(where + ": " + KEY + " " + p.problem());
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("arenaSize", p.place().arenaSize());
        out.put("prepSize", p.place().prepSize());
        out.put("wallHeight", p.place().wallHeight());
        return out;
    }

    /** The place a world's rules name ({@code arena_place}), or the default when they name none. */
    public static ArenaPlace placeOf(WorldRules rules) {
        WorldRule r = rules == null ? null : rules.first(KEY);
        if (r == null) {
            return ArenaPlace.DEFAULT;
        }
        ArenaPlace.Parsed p = ArenaPlace.parse(r.params());
        return p.ok() ? p.place() : ArenaPlace.DEFAULT;
    }

    /** A params object as plain values: numbers as numbers, anything else as its text. Notes ({@code _…}) left out. */
    static Map<String, Object> values(JsonObject raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : raw.entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            JsonElement v = e.getValue();
            if (v instanceof JsonPrimitive prim && prim.isNumber()) {
                out.put(e.getKey(), prim.getAsNumber());
            } else if (v instanceof JsonPrimitive prim && prim.isString()) {
                out.put(e.getKey(), prim.getAsString());
            } else {
                out.put(e.getKey(), String.valueOf(v));
            }
        }
        return out;
    }
}
