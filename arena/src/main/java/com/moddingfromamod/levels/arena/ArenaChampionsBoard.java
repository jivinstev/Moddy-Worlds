package com.moddingfromamod.levels.arena;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The Champions board, in the world</b>: the top {@value ArenaChampions#SHOWN} runs at the settings the control
 * panel is set to now ({@link ArenaChampions}), on the prep room's back wall ({@link ArenaChampionsLayout}). Its words
 * are vanilla text displays and each champion's face a vanilla item display holding a player head with his profile,
 * so a stock client draws them (his skin from the profile; a profile with no skin, as in an offline game, draws a
 * default one). Every one is tagged {@value #TAG} and with its own role tag, and they are KEPT, not remade: whenever
 * the board's chunk has its entities loaded, a missing one is made, a second copy is removed, and changed words or a
 * changed face are rewritten. So an older world gets them on load, and a restart never doubles them. Server thread
 * only. Owned by {@link ArenaBattle}.
 */
final class ArenaChampionsBoard {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on every line of words and every face on the board. */
    static final String TAG = ArenaMod.MODID + "_champions";

    private static final int CHECK_TICKS = 40;

    private final MinecraftServer server;
    private final ArenaChampionsLayout layout;
    private final ArenaState state;
    private final Map<UUID, String> shown = new HashMap<>();
    /** A face whose profile is being looked up (a champion who is not online), by his id. */
    private final Map<String, ResolvableProfile> resolved = new HashMap<>();
    private final java.util.Set<String> resolving = new java.util.HashSet<>();
    private boolean dirty = true;
    private long ticks;

    ArenaChampionsBoard(MinecraftServer server, ArenaPlace place, ArenaState state) {
        this.server = server;
        this.layout = place.champions();
        this.state = state;
    }

    /** The words or the champions changed: look again at once. */
    void changed() {
        dirty = true;
    }

    static String roleTag(String role, int row) {
        return TAG + "_" + role + (row > 0 ? "_" + row : "");
    }

    /** Keeps the board up to date for {@code settings}: at once after a change, else every 2 seconds. */
    void refresh(ServerLevel level, ArenaSettings settings) {
        ticks++;
        if (!dirty && ticks % CHECK_TICKS != 0) {
            return;
        }
        if (!entitiesLoaded(level)) {
            return;
        }
        ArenaChampions.Key key = ArenaChampions.Key.of(settings);
        List<ArenaChampions.Entry> top = ArenaChampions.top(state.champions(), key);
        Map<String, String> want = new HashMap<>();
        Map<String, ArenaChampionsLayout.Slot> slots = new HashMap<>();
        for (ArenaChampionsLayout.Slot s : layout.slots()) {
            String tag = roleTag(s.role(), s.row());
            slots.put(tag, s);
            String words;
            if (s.row() == 0) {
                words = switch (s.role()) {
                    case "title" -> ArenaWords.CHAMPIONS_TITLE;
                    case "settings" -> ArenaWords.championsAt(key);
                    default -> top.isEmpty() ? ArenaWords.championsNone() : "";
                };
            } else if (s.row() > top.size()) {
                words = "";
            } else {
                ArenaChampions.Entry e = top.get(s.row() - 1);
                words = switch (s.role()) {
                    case "rank" -> s.row() + ".";
                    case "name" -> e.name();
                    case "result" -> ArenaChampions.result(e);
                    default -> ArenaChampions.time(e.ticks());
                };
            }
            want.put(tag, words);
        }
        keepWords(level, want, slots);
        keepFaces(level, top);
        dirty = false;
    }

    private boolean entitiesLoaded(ServerLevel level) {
        ArenaPlace.Box b = layout.wordsBox();
        for (int cx = b.x0() >> 4; cx <= b.x1() >> 4; cx++) {
            for (int cz = b.z0() >> 4; cz <= b.z1() >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private AABB box() {
        ArenaPlace.Box b = layout.wordsBox();
        return new AABB(b.x0(), b.y0(), b.z0(), b.x1() + 1, b.y1() + 1, b.z1() + 1);
    }

    private static String roleOf(Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith(TAG + "_")) {
                return t;
            }
        }
        return null;
    }

    private void keepWords(ServerLevel level, Map<String, String> want, Map<String, ArenaChampionsLayout.Slot> slots) {
        Map<String, Display.TextDisplay> have = new HashMap<>();
        int removed = 0;
        for (Display.TextDisplay e : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), box(),
                e -> e.getTags().contains(TAG))) {
            String role = roleOf(e);
            ArenaChampionsLayout.Slot s = role == null ? null : slots.get(role);
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
        for (Map.Entry<String, ArenaChampionsLayout.Slot> en : slots.entrySet()) {
            ArenaChampionsLayout.Slot s = en.getValue();
            String words = want.get(en.getKey());
            Display.TextDisplay e = have.get(en.getKey());
            if (e == null) {
                run(level, "summon minecraft:text_display " + num(s.x()) + " " + num(s.y()) + " " + num(s.z()) + " "
                        + textNbt(en.getKey(), s, words));
                made++;
                for (Display.TextDisplay n : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class),
                        box(), n -> n.getTags().contains(en.getKey()))) {
                    shown.put(n.getUUID(), words);
                }
            } else if (!words.equals(shown.get(e.getUUID()))) {
                run(level, "data merge entity " + e.getUUID() + " {text:" + ArenaBoard.component(words, s.colour())
                        + "}");
                shown.put(e.getUUID(), words);
            }
        }
        if (made > 0 || removed > 0) {
            LOG.info("Moddy Arena: Champions board: {} lines of words made, {} removed", made, removed);
        }
    }

    /** A text display's data: its tags, words, no background, a shadow, full light, fixed and facing into the room. */
    static String textNbt(String role, ArenaChampionsLayout.Slot s, String words) {
        String sc = num(s.scale()) + "f";
        return "{Tags:[\"" + TAG + "\",\"" + role + "\"],text:" + ArenaBoard.component(words, s.colour())
                + ",background:0,line_width:400,shadow:1b,alignment:\"center\",billboard:\"fixed\",view_range:2.0f,"
                + "brightness:{sky:15,block:15},Rotation:[" + num(ArenaChampionsLayout.YAW) + "f,0.0f],"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],"
                + "scale:[" + sc + "," + sc + "," + sc + "]}}";
    }

    /** Each row's face: a player head with the champion's profile, or none for an empty row. */
    private void keepFaces(ServerLevel level, List<ArenaChampions.Entry> top) {
        Map<String, ArenaChampionsLayout.Face> faces = new HashMap<>();
        for (ArenaChampionsLayout.Face f : layout.faces()) {
            faces.put(roleTag("face", f.row()), f);
        }
        Map<String, Display.ItemDisplay> have = new HashMap<>();
        Map<String, ResolvableProfile> known = new HashMap<>();
        for (Display.ItemDisplay e : level.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class), box(),
                e -> e.getTags().contains(TAG))) {
            ResolvableProfile p = e.getSlot(0).get().get(DataComponents.PROFILE);
            if (p != null && idOf(p).isPresent() && !bare(p)) {
                known.put(idOf(p).get().toString(), p);
            }
            String role = roleOf(e);
            ArenaChampionsLayout.Face f = role == null ? null : faces.get(role);
            boolean inPlace = f != null && e.distanceToSqr(f.x(), f.y(), f.z()) < 1.0e-4;
            if (f == null || !inPlace || have.containsKey(role)) {
                e.discard();
                continue;
            }
            have.put(role, e);
        }
        for (Map.Entry<String, ArenaChampionsLayout.Face> en : faces.entrySet()) {
            ArenaChampionsLayout.Face f = en.getValue();
            Display.ItemDisplay e = have.get(en.getKey());
            ArenaChampions.Entry champ = f.row() <= top.size() ? top.get(f.row() - 1) : null;
            if (champ == null) {
                if (e != null) {
                    e.discard();
                }
                continue;
            }
            if (e == null) {
                run(level, "summon minecraft:item_display " + num(f.x()) + " " + num(f.y()) + " " + num(f.z()) + " "
                        + faceNbt(en.getKey(), f));
                for (Display.ItemDisplay n : level.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class),
                        box(), n -> n.getTags().contains(en.getKey()))) {
                    e = n;
                }
                if (e == null) {
                    continue;
                }
            }
            ResolvableProfile now = e.getSlot(0).get().get(DataComponents.PROFILE);
            boolean same = now != null && idOf(now).map(u -> u.toString().equals(champ.id())).orElse(false)
                    && now.name().map(champ.name()::equals).orElse(false);
            ResolvableProfile best = profile(champ, known);
            boolean better = best != null && !bare(best) && now != null
                    && bare(now);
            if (!same || better) {
                ResolvableProfile p = best != null ? best
                        : ResolvableProfile.createResolved(new GameProfile(UUID.fromString(champ.id()), champ.name()));
                ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                head.set(DataComponents.PROFILE, p);
                e.getSlot(0).set(head);
            }
        }
    }

    /** A profile's player id, if it has one (since 1.21.9 a profile with no id carries the nil UUID). */
    private static Optional<UUID> idOf(ResolvableProfile p) {
        UUID u = p.partialProfile().id();
        return u == null || (u.getMostSignificantBits() == 0L && u.getLeastSignificantBits() == 0L)
                ? Optional.empty() : Optional.of(u);
    }

    /** True for a profile with no properties: no skin to show yet. */
    private static boolean bare(ResolvableProfile p) {
        return p.partialProfile().properties().isEmpty();
    }

    /** The best profile for a champion: his own while he is online (his skin), else one known or looked up. */
    private ResolvableProfile profile(ArenaChampions.Entry champ, Map<String, ResolvableProfile> known) {
        UUID id;
        try {
            id = UUID.fromString(champ.id());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            return ResolvableProfile.createResolved(p.getGameProfile());
        }
        ResolvableProfile k = known.get(champ.id());
        if (k != null) {
            return k;
        }
        ResolvableProfile r = resolved.get(champ.id());
        if (r != null) {
            return r;
        }
        if (resolving.add(champ.id())) {
            // an id alone is looked up by the game (the same lookup a placed player head makes)
            ResolvableProfile.createUnresolved(id).resolveProfile(server.services().profileResolver())
                    .thenAcceptAsync(done -> {
                        if (done != null && !done.properties().isEmpty()) {
                            resolved.put(champ.id(), ResolvableProfile.createResolved(done));
                            dirty = true;
                        }
                    }, server);
        }
        return null;
    }

    /** An item display's data: its tags, full light, fixed, facing into the room, the size of a face. */
    static String faceNbt(String role, ArenaChampionsLayout.Face f) {
        String sc = num(f.size()) + "f";
        return "{Tags:[\"" + TAG + "\",\"" + role + "\"],item:{id:\"minecraft:player_head\",count:1},"
                + "item_display:\"fixed\",billboard:\"fixed\",view_range:2.0f,brightness:{sky:15,block:15},"
                + "Rotation:[" + num(ArenaChampionsLayout.YAW) + "f,0.0f],transformation:{left_rotation:[0f,0f,0f,1f],"
                + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + sc + "," + sc + "," + sc + "]}}";
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
