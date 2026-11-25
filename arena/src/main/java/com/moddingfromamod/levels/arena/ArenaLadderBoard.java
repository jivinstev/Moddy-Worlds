package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPanel.LadderSlot;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The wish ladder board, in the world</b>: a few big lines of words on a black board (blocks, {@link
 * ArenaPanel#ladderBoard}) on the prep room's west wall beside the control panel, one vanilla text display per line
 * ({@link ArenaPanel#ladderSlots}, laid out by {@link ArenaLadderLayout}): the title, where the battle is on the ladder,
 * what wishes can have now, what is locked and what the next step brings ({@link ArenaWords#ladderBoard}). Every line
 * is tagged {@value #TAG} and with its own role tag, kept like the panel's words: made when missing, a second copy
 * removed, one that moved or changed size remade, changed words rewritten, a line no longer wanted removed; so a
 * restart never doubles it and an older world gets it on load. Server thread only. Owned by {@link ArenaBattle}.
 */
final class ArenaLadderBoard {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on every line of the board's words. */
    static final String TAG = ArenaMod.MODID + "_ladder";

    /** How often the words are checked when nothing changed: every 2 seconds. */
    private static final int CHECK_TICKS = 40;

    private final MinecraftServer server;
    private final ArenaPanel panel;
    /** The words and size each line was last given (by its entity), so an unchanged line is never rewritten. */
    private final Map<UUID, String> shown = new HashMap<>();
    private ArenaWords.LadderBoardWords last;
    private boolean dirty = true;
    private long ticks;

    ArenaLadderBoard(MinecraftServer server, ArenaPlace place) {
        this.server = server;
        this.panel = place.panel();
    }

    /** Something the board shows changed (a step reached, a battle begun or ended). */
    void changed() {
        dirty = true;
    }

    /** The role tag of one line. */
    static String roleTag(ArenaLadderLayout.Role r) {
        return TAG + "_" + r.name().toLowerCase(Locale.ROOT);
    }

    /** Keeps the words: at once after a change, else every {@value #CHECK_TICKS} ticks, once its chunk has its entities. */
    void refresh(ServerLevel level, ArenaWords.LadderBoardWords words) {
        ticks++;
        if (!dirty && words.equals(last) && ticks % CHECK_TICKS != 0) {
            return;
        }
        ArenaPlace.Box b = panel.ladderWordsBox();
        for (int cx = b.x0() >> 4; cx <= b.x1() >> 4; cx++) {
            for (int cz = b.z0() >> 4; cz <= b.z1() >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return;
                }
            }
        }
        AABB box = new AABB(b.x0(), b.y0(), b.z0(), b.x1() + 1, b.y1() + 1, b.z1() + 1);
        Map<ArenaLadderLayout.Role, LadderSlot> slots = new EnumMap<>(ArenaLadderLayout.Role.class);
        List<LadderSlot> want = panel.ladderSlots(words);
        for (LadderSlot s : want) {
            slots.put(s.role(), s);
        }
        Map<ArenaLadderLayout.Role, Display.TextDisplay> have = new EnumMap<>(ArenaLadderLayout.Role.class);
        int removed = 0;
        for (Display.TextDisplay e : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box,
                e -> e.getTags().contains(TAG))) {
            ArenaLadderLayout.Role role = roleOf(e);
            LadderSlot s = role == null ? null : slots.get(role);
            boolean inPlace = s != null && e.distanceToSqr(s.x(), s.y(), s.z()) < 1.0e-4
                    && e.getTags().contains(sizeTag(s.scale()));
            if (!inPlace || have.containsKey(role)) {
                shown.remove(e.getUUID());
                e.discard();
                removed++;
                continue;
            }
            have.put(role, e);
        }
        int made = 0;
        for (LadderSlot s : want) {
            Display.TextDisplay e = have.get(s.role());
            if (e == null) {
                run(level, "summon minecraft:text_display " + num(s.x()) + " " + num(s.y()) + " " + num(s.z()) + " "
                        + nbt(s));
                made++;
                for (Display.TextDisplay n : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box,
                        n -> n.getTags().contains(roleTag(s.role())))) {
                    shown.put(n.getUUID(), s.text());
                }
            } else if (!s.text().equals(shown.get(e.getUUID()))) {
                run(level, "data merge entity " + e.getUUID() + " {text:" + component(s) + "}");
                shown.put(e.getUUID(), s.text());
            }
        }
        if (made > 0 || removed > 0) {
            LOG.info("Moddy Arena: the wish ladder board: {} lines of words made, {} removed", made, removed);
        }
        last = words;
        dirty = false;
    }

    /** A tag that says how big a line was made (a display does not say), so one whose size changed is remade. */
    static String sizeTag(float scale) {
        return TAG + "_size_" + Math.round(scale * 1000);
    }

    private static ArenaLadderLayout.Role roleOf(Entity e) {
        for (ArenaLadderLayout.Role r : ArenaLadderLayout.Role.values()) {
            if (e.getTags().contains(roleTag(r))) {
                return r;
            }
        }
        return null;
    }

    /**
     * A line's data: its tags, the words with a shadow and no backing (the board behind is black blocks: a backing of
     * the display's own flickered through its words from a few blocks away), full light, fixed, facing into the room.
     */
    static String nbt(LadderSlot s) {
        String sc = num(s.scale()) + "f";
        return "{Tags:[\"" + TAG + "\",\"" + roleTag(s.role()) + "\",\"" + sizeTag(s.scale()) + "\"],text:" + component(s)
                + ",background:0,line_width:1000,shadow:1b,alignment:\"center\",billboard:\"fixed\",view_range:2.0f,"
                + "brightness:{sky:15,block:15},Rotation:[" + num(s.yaw()) + "f,0.0f],transformation:{left_rotation:"
                + "[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + sc + "," + sc + ","
                + sc + "]}}";
    }

    /** A line as a text component in SNBT: its words (plain characters), its colour, bold when it is the title. */
    static String component(LadderSlot s) {
        StringBuilder q = new StringBuilder();
        for (char ch : s.text().toCharArray()) {
            switch (ch) {
                case '\\' -> q.append("\\\\");
                case '"' -> q.append("\\\"");
                case '\n' -> q.append("\\n");
                default -> q.append(ch);
            }
        }
        return "{text:\"" + q + "\",color:\"" + s.colour() + "\"" + (s.bold() ? ",bold:1b" : "") + "}";
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private void run(ServerLevel level, String command) {
        CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput()
                .withPermission(4);
        server.getCommands().performPrefixedCommand(src, command);
    }
}
