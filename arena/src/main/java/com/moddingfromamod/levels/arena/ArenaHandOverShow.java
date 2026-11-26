package com.moddingfromamod.levels.arena;

import com.moddingfromamod.world.HandedOver;
import com.moddingfromamod.world.NotKept;
import com.moddingfromamod.world.WishedItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The hand-over, shown</b> ({@link ArenaHandOver}): where a player's make is finished (where he stands), and, when the core has handed a kept make over, the moment he sees it: the make shows on the prep
 * room's hand-over spot ({@link ArenaPlace#handOverSpot}) for {@link ArenaHandOver#SHOW_TICKS} ticks, then flies to him
 * with the game's own pick-up motion and sound, and one action-bar line names it and where it went (the first time, a
 * chat line says the difference between a wish and an item). Everything a stock client draws: an item entity and the
 * game's pick-up packet.
 *
 * <p>What shows is a COPY no player can pick up (the core has already put the real thing where it went: a hotbar wish
 * in its slot, or the real item in his inventory), removed when it flies; so nothing is ever given twice. A copy a stop
 * or a crash left behind is swept away. Server thread, but for {@link #handedOver} and {@link #notKept}, which may come
 * from any thread and are queued.
 */
final class ArenaHandOverShow {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on a shown copy, so one left behind is known and swept. */
    static final String TAG = "moddyarena_handover";

    /** How often a copy left behind is looked for: every 5 seconds. */
    private static final int SWEEP_TICKS = 100;

    private final MinecraftServer server;
    private final ArenaPlace place;
    private final ArenaState state;
    private final Queue<HandedOver> handed = new ConcurrentLinkedQueue<>();
    private final Queue<NotKept> notKept = new ConcurrentLinkedQueue<>();
    private final List<Showing> showing = new ArrayList<>();

    /** One make on the spot: its copy, whose it is, when it flies, and its line. */
    private record Showing(UUID entity, UUID player, long flyAt, String line, boolean first) {
    }

    ArenaHandOverShow(MinecraftServer server, ArenaPlace place, ArenaState state) {
        this.server = server;
        this.place = place;
        this.state = state;
    }

    /** Where this player's make is finished now ({@link ArenaHandOver#where}): where he stands. Not in the arena's world: outside. */
    ArenaHandOver.Where where(UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null || p.level() != server.overworld()) {
            return ArenaHandOver.Where.OUTSIDE;
        }
        return ArenaHandOver.where(place, p.getX(), p.getY(), p.getZ());
    }

    /** The core handed a kept make over: shown on the next tick. Any thread. */
    void handedOver(HandedOver what) {
        handed.add(what);
    }

    /** The core could not keep a make the arena asked it to: said on the next tick. Any thread. */
    void notKept(NotKept what) {
        notKept.add(what);
    }

    /** Every tick: start what was handed over, fly what has shown long enough, and now and then sweep. */
    void tick() {
        long now = server.getTickCount();
        for (HandedOver h; (h = handed.poll()) != null; ) {
            start(h, now);
        }
        for (NotKept n; (n = notKept.poll()) != null; ) {
            ServerPlayer p = server.getPlayerList().getPlayer(n.player());
            String line = ArenaWords.handOverNotKept(n.kind(), ArenaHandOver.notKeptWhy(n.why().name()));
            LOG.info("Moddy Arena: hand-over: a {} the arena asked to keep ({}) was not kept: {}", n.kind(), n.asked(),
                    n.why());
            if (p != null) {
                p.sendSystemMessage(Component.literal(line));
            }
        }
        ServerLevel level = server.overworld();
        for (Iterator<Showing> it = showing.iterator(); it.hasNext(); ) {
            Showing s = it.next();
            if (now < s.flyAt()) {
                continue;
            }
            it.remove();
            fly(level, s);
        }
        if (now % SWEEP_TICKS == 0) {
            sweep(level);
        }
    }

    private void start(HandedOver h, long now) {
        ServerPlayer p = server.getPlayerList().getPlayer(h.player());
        if (p == null) {
            return;
        }
        ArenaHandOver.Went went = ArenaHandOver.went(h.slot(), h.realItem());
        ItemStack shown = switch (went) {
            case HOTBAR -> p.getInventory().getItem(h.slot());
            case REAL_ITEM -> realItem(p);
            case WISH_BAG -> ItemStack.EMPTY;
        };
        String name = shown.isEmpty() ? "" : ArenaPinNotice.nameOf(shown.getHoverName().getString());
        String line = ArenaWords.handOverReady(name, went, h.slot());
        boolean first = state.tellHandOver(p.getUUID());
        LOG.info("Moddy Arena: hand-over: {}'s {} ({}) went {}{}", p.getScoreboardName(), h.kind(), h.recipe(), went,
                went == ArenaHandOver.Went.HOTBAR ? " to slot " + (h.slot() + 1) : "");
        if (shown.isEmpty()) {
            tell(p, line, first);   // nothing to show (it went into his Wish Bag): the line alone
            return;
        }
        int[] spot = place.handOverSpot();
        ServerLevel level = server.overworld();
        ItemStack copy = shown.copyWithCount(1);
        ItemEntity e = new ItemEntity(level, spot[0] + 0.5, spot[1] + 1.25, spot[2] + 0.5, copy, 0, 0, 0);
        e.setNoGravity(true);
        e.setNeverPickUp();
        e.addTag(TAG);
        if (!level.addFreshEntity(e)) {
            tell(p, line, first);
            return;
        }
        showing.add(new Showing(e.getUUID(), p.getUUID(), now + ArenaHandOver.SHOW_TICKS, line, first));
    }

    /**
     * The real item the core just gave him: what he holds, if a wish made it, else the last thing a wish made in his
     * inventory; empty when there is none to show.
     */
    private static ItemStack realItem(ServerPlayer p) {
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty() && WishedItem.madeBy(held).isPresent()) {
            return held;
        }
        var inv = p.getInventory();
        for (int i = inv.getContainerSize() - 1; i >= 0; i--) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && WishedItem.madeBy(s).isPresent()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The copy flies to him (the game's pick-up packet: its motion and its sound), is removed, and the line is said. */
    private void fly(ServerLevel level, Showing s) {
        Entity e = level.getEntity(s.entity());
        ServerPlayer p = server.getPlayerList().getPlayer(s.player());
        if (e instanceof ItemEntity item) {
            if (p != null && p.level() == level) {
                p.take(item, item.getItem().getCount());
            }
            item.discard();
        }
        if (p != null) {
            tell(p, s.line(), s.first());
        }
    }

    private static void tell(ServerPlayer p, String line, boolean first) {
        p.displayClientMessage(Component.literal(line), true);
        if (first) {
            p.sendSystemMessage(Component.literal(ArenaWords.handOverFirst()));
        }
    }

    /** A shown copy nobody is waiting for (a stop or a crash left it): removed. Only where the prep room is loaded. */
    private void sweep(ServerLevel level) {
        ArenaPlace.Box b = place.prepInside();
        AABB box = new AABB(b.x0() - 1, b.y0() - 1, b.z0() - 1, b.x1() + 2, b.y1() + 2, b.z1() + 2);
        if (!level.hasChunksAt((int) box.minX, (int) box.minZ, (int) box.maxX, (int) box.maxZ)) {
            return;
        }
        Set<UUID> waiting = new HashSet<>();
        for (Showing s : showing) {
            waiting.add(s.entity());
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.getTags().contains(TAG) && !waiting.contains(e.getUUID()))) {
            e.discard();
        }
    }

    /** The module stops: what is still showing is removed (it was already given). */
    void stop() {
        ServerLevel level = server.overworld();
        for (Showing s : showing) {
            Entity e = level.getEntity(s.entity());
            if (e != null) {
                e.discard();
            }
        }
        showing.clear();
    }
}
