package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPanel.Button;
import com.moddingfromamod.levels.arena.ArenaPanel.Control;
import com.moddingfromamod.levels.arena.ArenaPanel.Slot;
import com.moddingfromamod.levels.arena.ArenaPanel.Words;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The battle arena control panel, in the world</b>: reads its buttons, carries out a selector press (the pure
 * {@link ArenaSettings} decides, {@link ArenaWords} says it) and keeps its words up to date. The words are vanilla text
 * displays, one per line of the board ({@link ArenaPanel#slots}), each tagged {@value #TAG} and with its own role tag,
 * so a stock client draws them and the panel finds its own. They are kept, not remade: whenever the board's chunks
 * have their entities loaded, a missing line is made, a second copy of one is removed, one that moved is remade, and a
 * line whose words changed is rewritten. So an older world gets them on load, and a restart never doubles them.
 * Server thread only. Owned by {@link ArenaBattle}.
 */
final class ArenaBoard {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on every line of words on the panel. */
    static final String TAG = ArenaMod.MODID + "_panel";

    /** How often the words are checked when nothing changed: every 2 seconds. */
    private static final int CHECK_TICKS = 40;

    private final MinecraftServer server;
    private final ArenaPlace place;
    private final ArenaPanel panel;
    private final ArenaState state;
    private final Map<Control, Boolean> wasDown = new EnumMap<>(Control.class);
    /** The words each line was last given (by its entity), so an unchanged line is never rewritten. */
    private final Map<UUID, String> shown = new HashMap<>();
    private boolean dirty = true;
    private String lastState = "";
    /** Who pressed a button on the panel last (his best is on the board), or null. */
    private UUID lastPresser;
    private String lastPresserName = "";
    private long ticks;

    ArenaBoard(MinecraftServer server, ArenaPlace place, ArenaState state) {
        this.server = server;
        this.place = place;
        this.panel = place.panel();
        this.state = state;
    }

    /** The role tag of one line of words. */
    static String roleTag(Words w) {
        return TAG + "_" + w.name().toLowerCase(Locale.ROOT);
    }

    // ── the buttons ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The buttons that went down since the last tick, in the panel's order. The first look only notes how they stand: a
     * button saved down when the server stopped is not a press when it starts again.
     */
    List<Control> pressed(ServerLevel level) {
        List<Control> out = new ArrayList<>();
        boolean first = wasDown.isEmpty();
        for (Button b : panel.buttons()) {
            boolean down = ArenaBuilder.buttonDown(level, b.pos());
            if (down && !first && !wasDown.getOrDefault(b.control(), false)) {
                out.add(b.control());
            }
            wasDown.put(b.control(), down);
        }
        return out;
    }

    /** The player nearest a button (within 6 blocks): who pressed it, or null (a button pressed by nobody near). */
    ServerPlayer presser(ServerLevel level, Control c) {
        int[] b = panel.button(c).pos();
        ServerPlayer best = null;
        double bestD = 36;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * A selector was pressed: one step of the difficulty or the number of waves (as the world's rules allow), said on the
     * presser's action bar with a click; during a battle ({@code busy}) nothing moves, and that is said.
     */
    void select(ServerLevel level, Control c, boolean busy, ArenaWaves base) {
        ServerPlayer presser = presser(level, c);
        noteBy(presser);
        int[] at = panel.button(c).pos();
        String who = presser == null ? "a button nobody stood near" : presser.getScoreboardName();
        if (busy) {
            tell(level, presser, at, ArenaWords.panelBusy(), false, 0);
            return;
        }
        ArenaSettings.Limits limits = base == null ? ArenaSettings.Limits.NONE : base.limits();
        ArenaSettings now = state.settings();
        boolean up = c == Control.DIFFICULTY_UP || c == Control.WAVES_UP;
        boolean count = c == Control.WAVES_DOWN || c == Control.WAVES_UP;
        ArenaSettings.Step step = count ? now.stepWaves(up, limits) : now.stepDifficulty(up, limits);
        ArenaSettings next = step.settings();
        java.util.List<Integer> options = limits.waveOptions();
        String words = switch (step.result()) {
            case CHANGED -> count ? ArenaWords.wavesChanged(next.waves())
                    : ArenaWords.difficultyChanged(next.difficulty(), next.run(base));
            case AT_END -> count ? ArenaWords.wavesAtEnd(up, options.get(0), options.get(options.size() - 1),
                            limits.maxCount() != ArenaSettings.ENDLESS)
                    : ArenaWords.difficultyAtEnd(up, limits.maxDifficulty());
            case FIXED -> count ? ArenaWords.wavesFixed(next.waves()) : ArenaWords.difficultyFixed(next.difficulty());
        };
        if (step.result() == ArenaSettings.Result.CHANGED) {
            state.settings(next);
            dirty = true;
            LOG.info("Moddy Arena: control panel: {} set the {} to {} (difficulty {}, so wave size {}; waves {})", who,
                    count ? "waves" : "difficulty", count ? ArenaWords.wavesValue(next.waves())
                            : next.difficulty().word(), next.difficulty().word(), next.size(limits),
                    ArenaWords.wavesValue(next.waves()));
        }
        int idx = Math.max(0, options.indexOf(next.waves()));
        float pitch = count ? 0.7f + 0.25f * idx : 0.7f + 0.3f * next.difficulty().ordinal();
        tell(level, presser, at, words, step.result() == ArenaSettings.Result.CHANGED, pitch);
    }

    /** One short line on the presser's action bar, and a click: a rising note for a change, a low one for no change. */
    private static void tell(ServerLevel level, ServerPlayer presser, int[] at, String words, boolean changed,
                             float pitch) {
        if (presser != null) {
            presser.displayClientMessage(Component.literal(words), true);
        }
        level.playSound((Entity) null, at[0] + 0.5, at[1] + 0.5, at[2] + 0.5,
                changed ? SoundEvents.NOTE_BLOCK_PLING : SoundEvents.NOTE_BLOCK_BASS, SoundSource.BLOCKS, 0.8f,
                changed ? pitch : 0.6f);
    }

    // ── the words ───────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Keeps the words on the board: at once after a change ({@code changed}, or a new state of the battle), else every
     * {@value #CHECK_TICKS} ticks. Nothing happens until the board's chunks have their entities loaded.
     */
    /** Notes who pressed a button, so his best shows (a press nobody stood near changes nothing). */
    void noteBy(ServerPlayer presser) {
        if (presser != null && (!presser.getUUID().equals(lastPresser)
                || !presser.getScoreboardName().equals(lastPresserName))) {
            lastPresser = presser.getUUID();
            lastPresserName = presser.getScoreboardName();
            dirty = true;
        }
    }

    /** The words have to be looked at again (a run was recorded, say). */
    void changed() {
        dirty = true;
    }

    /** The player whose best the board shows: who pressed last, else the only player in the world, else nobody. */
    private String[] bestFor(ServerLevel level) {
        if (lastPresser != null) {
            return new String[] {lastPresser.toString(), lastPresserName};
        }
        if (level.players().size() == 1) {
            ServerPlayer p = level.players().get(0);
            return new String[] {p.getUUID().toString(), p.getScoreboardName()};
        }
        return null;
    }

    void refresh(ServerLevel level, ArenaMatch match, java.util.function.Supplier<ArenaWaves> rules, boolean changed) {
        ticks++;
        int of = match.of();
        boolean boss = boss(match);
        int breakLeft = match.inBreak() ? match.secondsLeft() : -1;   // a break's timer, on the board
        String stateWords = ArenaWords.panelState(match.phase(), match.wave(), of, boss, breakLeft);
        if (!stateWords.equals(lastState)) {
            changed = true;
        }
        if (!changed && !dirty && ticks % CHECK_TICKS != 0) {
            return;
        }
        if (!entitiesLoaded(level)) {
            return;
        }
        ArenaWaves base = rules.get();
        ArenaSettings settings = state.settings().within(base == null ? null : base.limits());
        ArenaRun run = settings.run(base);
        String[] who = bestFor(level);
        String best = who == null ? null : ArenaChampions.bestLine(who[1], ArenaChampions.bestOf(state.champions(),
                who[0], ArenaChampions.Key.of(settings)));
        int perWave = ArenaWishesRule.wishesOf(com.moddingfromamod.world.WorldRulesService.current()).wishesPerWave();
        Map<Words, String> want = new EnumMap<>(Words.class);
        for (Slot s : panel.slots()) {
            want.put(s.words(), String.join("\n",
                    ArenaWords.panel(s.words(), settings, run, match.phase(), match.wave(), of, boss, best, perWave,
                            breakLeft)));
        }
        keep(level, want);
        lastState = stateWords;
        dirty = false;
    }

    /** True when the wave fought now (or next, in a breather) is a boss wave. */
    private static boolean boss(ArenaMatch match) {
        ArenaRun t = match.table();
        if (t == null) {
            return false;
        }
        return match.phase() == ArenaMatch.Phase.WAVE ? t.isBossWave(match.wave())
                : match.phase() == ArenaMatch.Phase.BREATHER && t.isBossWave(match.wave() + 1);
    }

    private boolean entitiesLoaded(ServerLevel level) {
        ArenaPlace.Box b = panel.wordsBox();
        for (int cx = b.x0() >> 4; cx <= b.x1() >> 4; cx++) {
            for (int cz = b.z0() >> 4; cz <= b.z1() >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Makes the board's lines match {@code want}: one entity per line, where its slot is, with its words. */
    private void keep(ServerLevel level, Map<Words, String> want) {
        ArenaPlace.Box b = panel.wordsBox();
        AABB box = new AABB(b.x0(), b.y0(), b.z0(), b.x1() + 1, b.y1() + 1, b.z1() + 1);
        Map<Words, Slot> slots = new EnumMap<>(Words.class);
        for (Slot s : panel.slots()) {
            slots.put(s.words(), s);
        }
        Map<Words, Display.TextDisplay> have = new EnumMap<>(Words.class);
        int removed = 0;
        for (Display.TextDisplay e : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box,
                e -> e.getTags().contains(TAG))) {
            Words role = roleOf(e);
            Slot s = role == null ? null : slots.get(role);
            boolean inPlace = s != null && e.distanceToSqr(s.x(), s.y(), s.z()) < 1.0e-4;
            if (s == null || !inPlace || have.containsKey(role)) {
                shown.remove(e.getUUID());
                e.discard();
                removed++;
                continue;
            }
            have.put(role, e);
        }
        int made = 0;
        int rewritten = 0;
        for (Slot s : panel.slots()) {
            String words = want.get(s.words());
            Display.TextDisplay e = have.get(s.words());
            if (e == null) {
                run(level, "summon minecraft:text_display " + num(s.x()) + " " + num(s.y()) + " " + num(s.z()) + " "
                        + nbt(s, words));
                made++;
                for (Display.TextDisplay n : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box,
                        n -> n.getTags().contains(roleTag(s.words())))) {
                    shown.put(n.getUUID(), words);
                }
            } else if (!words.equals(shown.get(e.getUUID()))) {
                run(level, "data merge entity " + e.getUUID() + " {text:" + component(words, s.colour()) + "}");
                shown.put(e.getUUID(), words);
                rewritten++;
            }
        }
        if (made > 0 || removed > 0) {
            LOG.info("Moddy Arena: control panel: {} lines of words made, {} removed", made, removed);
        } else if (rewritten > 0) {
            LOG.debug("Moddy Arena: control panel: {} lines rewritten", rewritten);
        }
    }

    private static Words roleOf(Entity e) {
        for (Words w : Words.values()) {
            if (e.getTags().contains(roleTag(w))) {
                return w;
            }
        }
        return null;
    }

    /** A text display's data: its tags, words, no background, a shadow, full light, fixed and facing the prep room. */
    static String nbt(Slot s, String words) {
        String sc = num(s.scale()) + "f";
        return "{Tags:[\"" + TAG + "\",\"" + roleTag(s.words()) + "\"],text:" + component(words, s.colour())
                + ",background:0,line_width:400,shadow:1b,alignment:\"center\",billboard:\"fixed\",view_range:2.0f,"
                + "brightness:{sky:15,block:15},Rotation:[0.0f,0.0f],transformation:{left_rotation:[0f,0f,0f,1f],"
                + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + sc + "," + sc + "," + sc + "]}}";
    }

    /** A text component in SNBT: the words (plain characters and line breaks) in a colour. */
    static String component(String words, String colour) {
        StringBuilder q = new StringBuilder();
        for (char ch : words.toCharArray()) {
            switch (ch) {
                case '\\' -> q.append("\\\\");
                case '"' -> q.append("\\\"");
                case '\n' -> q.append("\\n");
                default -> q.append(ch);
            }
        }
        return "{text:\"" + q + "\",color:\"" + colour + "\"}";
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
