package com.moddingfromamod.isle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moddingfromamod.levels.isle.shared.Level;
import com.moddingfromamod.levels.isle.shared.LevelClaim;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import net.minecraft.server.MinecraftServer;

/**
 * <b>Is THIS world a Scarcity Isle?</b> (#2750, the owner's world-type decision). A level jar's world is
 * switched on per world, never per instance, so one game can hold an Isle and a plain world side by side.
 *
 * <p>An Isle world is one created with the Isle's built-in packs on, its marker pack among them: picking World Type
 * "Moddy Isle" ticks them (the shared Create World listener), and a dedicated server names them in
 * {@code initial-enabled-packs} beside {@code level-type=moddyisle:isle}. Either way they are in the world's own
 * {@code level.dat} data configuration. The
 * first start records it in the world's saved data ({@link LevelClaim}, {@code data/moddyisle_level.dat}), so the world
 * stays an Isle whatever happens to its pack list later; a world without either is not one, and the Isle and its
 * wish bank stay inert there.
 *
 * <p>A server's launcher can still switch the Isle on by JVM properties ({@code moddingfromamod.world.module} / islands
 * spacing), which win over this.
 */
public final class IsleWorld {

    /** The Isle as the shared level code sees it: its mod id, level id, World Type name and built-in packs. */
    public static final Level LEVEL = new Level("moddyisle", IsleModule.ID, "Moddy Isle",
            java.util.List.of("standard_skyblock", "sky_void_additions", "skyblock_advancements"));
    /** The id prefix NeoForge gives a mod's built-in pack: {@code mod/<modid>:<path>}. */
    public static final String PACK_PREFIX = LEVEL.packPrefix();
    /** The built-in packs, by folder (the build unpacks the pinned SkyBlock packs here). */
    public static final java.util.List<String> PACKS = LEVEL.packs();
    /** The pack whose presence at creation marks an Isle world: the level's marker pack (shared {@code Level}). */
    public static final String MARK_PACK = LEVEL.markPackId();

    private IsleWorld() {
    }

    /**
     * True when this world is an Isle: its saved data says so, or its data configuration enables the Isle's sky pack
     * (then the saved data is written, once). Server thread, at server start.
     */
    public static boolean claims(MinecraftServer server) {
        return LevelClaim.claims(server, LEVEL);
    }

    /** True when {@code enabled} (a world's enabled pack ids) holds the Isle's marker pack. Pure. */
    static boolean isIslePackSet(Collection<String> enabled) {
        return LEVEL.marks(enabled);
    }

    /** The islands' spacing the Isle's own manifest names (worlds/isle.json's islands.spacing), or 0. */
    static int manifestSpacing() {
        return spacingOf(com.moddingfromamod.world.WorldFacts.manifestText(IsleModule.ID));
    }

    /** {@code islands.spacing} of a manifest's text, or 0 when it has none. Pure. */
    static int spacingOf(String manifest) {
        try {
            JsonElement el = JsonParser.parseString(manifest == null ? "" : manifest);
            if (el.isJsonObject() && el.getAsJsonObject().get("islands") instanceof JsonObject i
                    && i.has("spacing")) {
                return Math.max(0, i.get("spacing").getAsInt());
            }
        } catch (RuntimeException e) {
            // no usable spacing
        }
        return 0;
    }

    /** The Isle's own rules file text (worlds/rules/isle.rules.json, in this jar), or null. */
    static String bundledRules() {
        try (InputStream in = IsleWorld.class.getResourceAsStream("/worlds/rules/" + IsleModule.ID + ".rules.json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
