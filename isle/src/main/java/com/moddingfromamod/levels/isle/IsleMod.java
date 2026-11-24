package com.moddingfromamod.levels.isle;

import com.moddingfromamod.ModdingFromAModMod;
import com.moddingfromamod.isle.IsleWorld;
import com.moddingfromamod.levels.isle.shared.LevelPacks;
import com.moddingfromamod.levels.isle.shared.LevelWorldType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Scarcity Isle as a level jar</b> (#2750): {@code isle/} and {@code bank/}, loaded beside the core mod.
 *
 * <p>Its hooks reach the core through the core's own plug-in list: {@code META-INF/services/…ModdyPlugin} names
 * {@code IslePlugin} and {@code WishBankPlugin}, and the core's {@code Plugins.load} finds them with {@code ServiceLoader}.
 * The mod ordering (a REQUIRED dependency on the core, {@code ordering="AFTER"}) makes the core construct first.
 *
 * <p>What only this jar does (the owner's world-type decision), through the shared level code: it offers the Isle's
 * three built-in packs, OFF by default ({@link LevelPacks}), and on the client the Create World screen's World Type
 * "Moddy Isle" ticks them ({@link LevelWorldType}).
 */
@Mod(IsleMod.MODID)
public final class IsleMod {

    public static final String MODID = "moddyisle";

    private static final Logger LOG = LoggerFactory.getLogger(IsleMod.class);

    public IsleMod(IEventBus modBus) {
        LOG.info("{}: loaded beside {} {}", MODID, ModdingFromAModMod.MODID,
                ModList.get().getModContainerById(ModdingFromAModMod.MODID)
                        .map(c -> c.getModInfo().getVersion().toString()).orElse("MISSING"));
        LevelPacks.register(modBus, IsleWorld.LEVEL);
        if (FMLEnvironment.getDist().isClient()) {
            LevelWorldType.register(IsleWorld.LEVEL);
        }
    }
}
