package com.moddingfromamod.levels.arena;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The wish ladder board, in the world</b>: one vanilla text display on a black board (blocks, {@link
 * ArenaPanel#ladderBoard}), on the prep room's west wall beside the control panel ({@link ArenaPanel#ladderSign}), saying what the wishes of the player the panel shows may have
 * now, in real numbers (hearts, damage), what is still locked and what the next boss opens ({@link ArenaWords#ladderBoard}).
 * Tagged {@value #TAG}, kept like the panel's words: made when missing, a second copy removed, one that moved remade,
 * changed words rewritten, so a restart never doubles it and an older world gets it on load. Server thread only. Owned
 * by {@link ArenaBattle}.
 */
final class ArenaLadderBoard {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on the board's words. */
    static final String TAG = ArenaMod.MODID + "_ladder";

    /** How often the words are checked when nothing changed: every 2 seconds. */
    private static final int CHECK_TICKS = 40;

    private final MinecraftServer server;
    private final ArenaPanel.LadderSign sign;
    private String shown;
    private UUID shownEntity;
    private boolean dirty = true;
    private long ticks;

    ArenaLadderBoard(MinecraftServer server, ArenaPlace place) {
        this.server = server;
        this.sign = place.panel().ladderSign();
    }

    /** Something the board shows changed (a boss beaten, a different player at the panel). */
    void changed() {
        dirty = true;
    }

    /** Keeps the words: at once after a change, else every {@value #CHECK_TICKS} ticks, once its chunk has its entities. */
    void refresh(ServerLevel level, List<String> lines) {
        ticks++;
        String words = String.join("\n", lines);
        if (!dirty && words.equals(shown) && ticks % CHECK_TICKS != 0) {
            return;
        }
        if (!level.areEntitiesLoaded(ChunkPos.asLong(((int) Math.floor(sign.x())) >> 4,
                ((int) Math.floor(sign.z())) >> 4))) {
            return;
        }
        AABB box = new AABB(sign.x() - 1, sign.y() - 1, sign.z() - 3, sign.x() + 1, sign.y() + 3, sign.z() + 3);
        Display.TextDisplay keep = null;
        int removed = 0;
        for (Display.TextDisplay e : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box,
                e -> e.getTags().contains(TAG))) {
            boolean inPlace = e.distanceToSqr(sign.x(), sign.y(), sign.z()) < 1.0e-4;
            if (!inPlace || keep != null) {
                e.discard();
                removed++;
                continue;
            }
            keep = e;
        }
        if (keep == null) {
            run(level, "summon minecraft:text_display " + num(sign.x()) + " " + num(sign.y()) + " " + num(sign.z())
                    + " " + nbt(words));
            for (Display.TextDisplay n : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box,
                    n -> n.getTags().contains(TAG))) {
                shownEntity = n.getUUID();
            }
            LOG.info("Moddy Arena: the wish ladder board was made beside the control panel{}",
                    removed > 0 ? " (" + removed + " stray copies removed)" : "");
        } else if (!keep.getUUID().equals(shownEntity) || !words.equals(shown)) {
            run(level, "data merge entity " + keep.getUUID() + " {text:" + component(lines) + "}");
            shownEntity = keep.getUUID();
        }
        shown = words;
        dirty = false;
    }

    /**
     * The display's data: its tag, the words with a shadow and no backing (the board behind is black blocks: a backing
     * of the display's own flickered through its words from a few blocks away), full light, fixed, facing into the room.
     */
    String nbt(String words) {
        String sc = num(sign.scale()) + "f";
        return "{Tags:[\"" + TAG + "\"],text:" + component(List.of(words.split("\n", -1)))
                + ",background:0,line_width:" + sign.lineWidth() + ",shadow:1b,alignment:\"left\","
                + "billboard:\"fixed\",view_range:2.0f,brightness:{sky:15,block:15},Rotation:[" + num(sign.yaw())
                + "f,0.0f],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                + "translation:[0f,0f,0f],scale:[" + sc + "," + sc + "," + sc + "]}}";
    }

    /**
     * The words as a text component in SNBT: the title in gold, a locked line in grey, the "After wave" line and the
     * line after it in green, everything else white.
     */
    static String component(List<String> lines) {
        StringBuilder out = new StringBuilder("{text:\"\",extra:[");
        boolean nextGreen = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            boolean next = l.startsWith("After ");
            String colour = i == 0 ? "gold" : l.contains("ocked: ") ? "gray" : nextGreen || next ? "green" : "white";
            nextGreen = next;
            out.append(i == 0 ? "" : ",").append("{text:\"").append(quote(l + (i + 1 < lines.size() ? "\n" : "")))
                    .append("\",color:\"").append(colour).append("\"").append(i == 0 ? ",bold:1b" : "").append("}");
        }
        return out.append("]}").toString();
    }

    private static String quote(String s) {
        StringBuilder q = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\' -> q.append("\\\\");
                case '"' -> q.append("\\\"");
                case '\n' -> q.append("\\n");
                default -> q.append(ch);
            }
        }
        return q.toString();
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
