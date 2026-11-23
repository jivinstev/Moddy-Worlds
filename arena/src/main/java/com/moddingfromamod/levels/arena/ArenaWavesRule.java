package com.moddingfromamod.levels.arena;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.moddingfromamod.world.WorldRule;
import com.moddingfromamod.world.WorldRuleType;
import com.moddingfromamod.world.WorldRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * <b>{@code arena_waves}, the arena's second rule type</b>: the battle's wave table and timings ({@link ArenaWaves}
 * checks them), so a rules file with a wrong table is refused whole, in words, as the core refuses any bad rule.
 * Registered by {@link ArenaPlugin} with the game's own "is this a living entity" question; a test passes its own.
 */
public final class ArenaWavesRule implements WorldRuleType {

    /** The file's spelling: {@code "type": "arena_waves"}. */
    public static final String KEY = "arena_waves";

    private final Predicate<String> living;

    /** @param living whether an id names a living entity the game knows (null: any well-formed id) */
    public ArenaWavesRule(Predicate<String> living) {
        this.living = living;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Map<String, Object> params(JsonObject raw, String where, List<String> err) {
        ArenaWaves.Parsed p = ArenaWaves.parse(plainMap(raw), living);
        if (!p.ok()) {
            err.add(where + ": " + KEY + " " + p.problem());
            return Map.of();
        }
        return p.waves().toParams();
    }

    /** The wave table a world's rules name, or null when they name none (then no battle can start). */
    public static ArenaWaves wavesOf(WorldRules rules) {
        WorldRule r = rules == null ? null : rules.first(KEY);
        if (r == null) {
            return null;
        }
        ArenaWaves.Parsed p = ArenaWaves.parse(r.params(), null);
        return p.ok() ? p.waves() : null;
    }

    /** A JSON object as plain values (maps, lists, numbers, strings, booleans); notes ({@code _…}) left out. */
    static Map<String, Object> plainMap(JsonObject o) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (o == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (!e.getKey().startsWith("_")) {
                out.put(e.getKey(), plain(e.getValue()));
            }
        }
        return out;
    }

    static Object plain(JsonElement v) {
        if (v == null || v.isJsonNull()) {
            return null;
        }
        if (v instanceof JsonObject o) {
            return plainMap(o);
        }
        if (v instanceof JsonArray a) {
            List<Object> out = new ArrayList<>();
            for (JsonElement e : a) {
                out.add(plain(e));
            }
            return out;
        }
        JsonPrimitive p = v.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsNumber();
        }
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        return p.getAsString();
    }
}
