package com.moddingfromamod.isle;

import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.WorldModule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * <b>Scarcity Isle's world module</b>: its bases are islands ({@link IslandService}), its
 * onboarding is the island copied on first join, the move once the pack has run. Named by
 * {@code worlds/isle.json}'s {@code "module": "isle"}.
 */
public final class IsleModule implements WorldModule {

    public static final String ID = "isle";

    private IslandService service;
    /** #2542: the next cells' terrain, generated while the server is quiet; null when off. */
    private IslandPregen pregen;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Bases start(MinecraftServer server) {
        if (IslandsConfig.spacing() <= 0) {
            // #2750: a world made as an Isle (its world type), with no launcher properties: the islands' spacing is
            // the manifest's, and its datapack defaults are written once, at the world's creation
            IslandsConfig.useWorld(IsleWorld.manifestSpacing());
            String manifest = com.moddingfromamod.world.WorldFacts.manifestText(ID);   // not yet "live": named directly
            if (manifest != null) {
                com.moddingfromamod.world.DatapackDefaults.apply(server,
                        com.moddingfromamod.world.DatapackDefaults.parse(manifest));
            }
        }
        service = IslandService.startLive(server);
        if (service != null) {
            // a service island (IslandService#ensureServiceIsland) is made by whatever needs one, once this has
            // started: the Isle names nothing outside itself and the core (#2750, a level jar on core alone)
            pregen = IslandPregen.startLive(server, service);
        }
        return service;
    }

    @Override
    public void stop() {
        IslandPregen p = pregen;
        pregen = null;
        if (p != null) {
            p.stop();
        }
        service = null;
        IslandService.setLive(null);
        IslandsConfig.useWorld(0);
    }

    @Override
    public void onLogin(ServerPlayer player) {
        IslandService s = service;
        if (s != null) {
            s.arrive(player);
        }
    }

    /** #1785 step 7: a leave counts as "seen" for the reclaim of an idle Crafter's island. */
    @Override
    public void onLogout(ServerPlayer player) {
        IslandService s = service;
        if (s != null) {
            s.left(player);
        }
    }

    /** #2179: a player with an island may start over ("Want to start over?"). */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> helpLines(ServerPlayer player) {
        IslandService s = IslandService.live();
        return s != null && s.islandOf(player.getUUID()) != null
                ? java.util.List.of(IslandFresh.linkLine()) : java.util.List.of();
    }

    /** #2542 follow-up: an empty Isle keeps ticking while the next cells are being generated. */
    @Override
    public String keepTicking() {
        IslandPregen p = pregen;
        return p == null ? null : p.keepTicking();
    }

    @Override
    public void onTick() {
        IslandService s = service;
        if (s != null) {
            s.tick();
        }
        IslandPregen p = pregen;
        if (p != null) {
            p.tick();
        }
    }

    /** #2750: this world's wishes are priced by the Isle's wish bank, and only this world's. */
    @Override
    public com.moddingfromamod.wish.WishCharge charge() {
        return com.moddingfromamod.bank.WishBankPlugin.charge();
    }

    /**
     * #2750: until (or unless) a rules file is read, the Isle's own rules from this jar (worlds/rules/isle.rules.json):
     * a world made as an Isle on a player's own game has no rules file, and its wish bank is one of those rules.
     * A server that names a rules file replaces this as soon as it is read. No bundled rules: the default.
     */
    @Override
    public com.moddingfromamod.world.WorldRules fallbackRules(com.moddingfromamod.world.Bases bases) {
        String text = IsleWorld.bundledRules();
        com.moddingfromamod.world.WorldRules.Parsed p = text == null ? null
                : com.moddingfromamod.world.WorldRules.parse(text, ID);
        return p != null && p.ok() ? p.rules() : com.moddingfromamod.world.WorldModule.super.fallbackRules(bases);
    }
}
