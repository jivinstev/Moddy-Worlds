package com.moddingfromamod.isle;

import java.util.List;

import com.moddingfromamod.compat.ChatEvents;
import com.moddingfromamod.ui.MenuKit;
import com.moddingfromamod.ui.TuneDialog;
import com.moddingfromamod.wish.ChildCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Start a fresh island</b> (#2179, owner): {@code /moddy island new} asks first (one TUNE
 * dialog, so Bedrock gets its form by the measured mapping), and its button runs {@code /moddy island new
 * confirm}, which:
 * <ul>
 *   <li>rests his island ({@link IslandService#startFresh}: never reused, still holds what he built) and
 *       gives him a fresh one in a new cell, where the tick moves him and sets his respawn point;</li>
 *   <li>empties his bag and his ender chest — or a fresh start would carry the old starter chest's ice and
 *       lava across;</li>
 *   <li>clears his goals (every advancement) — a fresh island with a finished goal list says the wrong
 *       thing, and the Crafter title, which is read off the goals, follows them.</li>
 * </ul>
 * Kept: his wish bank, everything on his shelf (his made things follow him), and
 * whether he is a Magician. Once a day ({@link IslandService#FRESH_GAP_MS}).
 */
public final class IslandFresh {

    private static final Logger LOGGER = LoggerFactory.getLogger("mfam-island");

    public static final String NEW = "new";
    public static final String CONFIRM = "confirm";

    public static final String TITLE = "Start a fresh island?";
    public static final String LINE_1 = "Your island and everything on it will rest.";
    public static final String LINE_2 = "Your bag and goals start over. Wishes stay.";
    public static final String BUTTON = "Start fresh";
    public static final String STARTED = "A brand-new island, all yours! Your old one is resting. It's being made now: hang on a moment.";
    public static final String NO_ISLAND = "This world has no islands to start over.";

    private IslandFresh() {
    }

    /** {@code /moddy island new}: the question, never the deed. */
    public static int ask(ServerPlayer p) {
        IslandService isle = IslandService.live();
        if (isle == null || isle.islandOf(p.getUUID()) == null) {
            p.sendSystemMessage(Component.literal(NO_ISLAND).withStyle(ChatFormatting.AQUA));
            return 0;
        }
        int wait = isle.freshInHours(p.getUUID(), System.currentTimeMillis());
        if (wait > 0) {
            p.sendSystemMessage(Component.literal(tooSoon(wait)).withStyle(ChatFormatting.AQUA));
            return 0;
        }
        MenuKit.tune(p, dialog());
        return 1;
    }

    /** The question as a dialog. Public for a test. */
    public static TuneDialog dialog() {
        return new TuneDialog(TITLE, List.of(new TuneDialog.Line(LINE_1), new TuneDialog.Line(LINE_2)), List.of(),
                BUTTON, confirmCommand().substring(1));
    }

    public static String askCommand() {
        return "/" + ChildCommands.MODDY + " " + ChildCommands.ISLAND + " " + NEW;
    }

    public static String confirmCommand() {
        return askCommand() + " " + CONFIRM;
    }

    public static String tooSoon(int hours) {
        return "You started a fresh island not long ago. You can start another in about " + hours
                + (hours == 1 ? " hour." : " hours.");
    }

    /** {@code /moddy island new confirm}: the deed. 1 when he has a fresh island. */
    public static int confirm(ServerPlayer p) {
        IslandService isle = IslandService.live();
        if (isle == null) {
            p.sendSystemMessage(Component.literal(NO_ISLAND).withStyle(ChatFormatting.AQUA));
            return 0;
        }
        return confirm(p, isle, System.currentTimeMillis());
    }

    /** {@link #confirm} against a given island service and clock (a GameTest's). */
    public static int confirm(ServerPlayer p, IslandService isle, long nowMs) {
        IslandService.Fresh f = isle.startFresh(p, nowMs);
        switch (f) {
            case NONE -> {
                p.sendSystemMessage(Component.literal(NO_ISLAND).withStyle(ChatFormatting.AQUA));
                return 0;
            }
            case TOO_SOON -> {
                p.sendSystemMessage(Component.literal(tooSoon(isle.freshInHours(p.getUUID(), nowMs)))
                        .withStyle(ChatFormatting.AQUA));
                return 0;
            }
            default -> {
            }
        }
        p.getInventory().clearContent();
        p.getEnderChestInventory().clearContent();
        int revoked = clearGoals(p);
        p.sendSystemMessage(Component.literal(STARTED).withStyle(ChatFormatting.GOLD));
        LOGGER.info("{} started a fresh island ({} goals cleared)", p.getScoreboardName(), revoked);
        return 1;
    }

    /** Every advancement he has made progress on, taken back. Returns how many. */
    static int clearGoals(ServerPlayer p) {
        var server = p.getServer();
        if (server == null) {
            return 0;
        }
        int n = 0;
        for (AdvancementHolder a : server.getAdvancements().getAllAdvancements()) {
            AdvancementProgress prog = p.getAdvancements().getOrStartProgress(a);
            if (!prog.hasProgress()) {
                continue;
            }
            for (String c : prog.getCompletedCriteria()) {
                p.getAdvancements().revoke(a, c);
            }
            n++;
        }
        return n;
    }

    /** The "Help & links" line that offers it, on a world with islands. */
    public static MutableComponent linkLine() {
        MutableComponent link = Component.literal("[" + BUTTON + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(ChatEvents.click(ClickEvent.Action.RUN_COMMAND, askCommand())));
        return Component.literal("   Want to start over? ").withStyle(ChatFormatting.AQUA).append(link)
                .append(Component.literal(" (asks first)").withStyle(ChatFormatting.GRAY));
    }
}
