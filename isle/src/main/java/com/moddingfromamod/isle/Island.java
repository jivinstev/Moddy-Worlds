package com.moddingfromamod.isle;

import java.time.Instant;
import java.util.UUID;

import com.google.gson.JsonObject;

/**
 * One child's island: whose it is, which grid cell, where its origin block is, and when it
 * was made. A PLACE, so it lives in the world's store ({@link IslandStore}), not on the child's
 * shelf — the shelf follows a child between worlds and an island does not.
 *
 * <p>The record's key is {@code ownerId}, so a store reader
 * reads it the way it reads every other world store: identity {@code islands.json} + owner, content
 * everything else.
 */
public record Island(UUID owner, int cellX, int cellZ, int x, int y, int z, long createdAtMs) {

    /** Where a child stands on a fresh island: on the grass, above the origin's dirt column. */
    public int standY() {
        return y + 3;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("ownerId", owner.toString());
        o.addProperty("cellX", cellX);
        o.addProperty("cellZ", cellZ);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("createdAt", Instant.ofEpochMilli(createdAtMs).toString());
        return o;
    }

    /** Null for a record missing its owner or its place — one bad record costs that record only. */
    public static Island fromJson(JsonObject o) {
        if (o == null || !o.has("ownerId") || !o.has("x") || !o.has("y") || !o.has("z")
                || !o.has("cellX") || !o.has("cellZ")) {
            return null;
        }
        long at = 0L;
        if (o.has("createdAt")) {
            try {
                at = Instant.parse(o.get("createdAt").getAsString()).toEpochMilli();
            } catch (RuntimeException e) {
                at = 0L;
            }
        }
        return new Island(UUID.fromString(o.get("ownerId").getAsString()),
                o.get("cellX").getAsInt(), o.get("cellZ").getAsInt(),
                o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(), at);
    }
}
