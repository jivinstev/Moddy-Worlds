package com.moddingfromamod.levels.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The level's World Type ticks its packs.</b> CLIENT ONLY.
 *
 * <p>On the Create World screen, picking the level's world type ticks its built-in packs, and picking any other type
 * unticks them, so a player chooses ONE thing. Packs that change worldgen must be on when the world is CREATED; that
 * needs the screen to reload its worldgen for the new pack set, which is exactly what its own Data Packs screen does on
 * "Done" ({@code tryApplyNewDataPacks}). That method is private: the shared access transformer
 * ({@code shared/resources/META-INF/accesstransformer.cfg}) opens it and {@code getDataPackSelectionSettings}, and
 * nothing else is touched. The reload re-opens the screen and sets the settings again, so the listener acts only on a
 * CHANGE of world type and attaches once per screen state.
 *
 * <p>SHARED SOURCE (see {@link Level}).
 */
public final class LevelWorldType {

    /** The screen states already listened to, each with whether it last showed this level's type. */
    private final Map<WorldCreationUiState, Boolean> seen = Collections.synchronizedMap(new WeakHashMap<>());
    private final Level level;
    private final Identifier preset;
    private final Logger log;

    private LevelWorldType(Level level) {
        this.level = level;
        this.preset = Identifier.fromNamespaceAndPath(level.modid(), level.id());
        this.log = LoggerFactory.getLogger(level.loggerName());
    }

    /** Call from the level's {@code @Mod} constructor, on the client only. */
    public static void register(Level level) {
        LevelWorldType t = new LevelWorldType(level);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> t.onInit(event));
    }

    private void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) {
            return;
        }
        WorldCreationUiState state = screen.getUiState();
        if (seen.putIfAbsent(state, isOurs(state)) == null) {
            state.addListener(s -> onChange(screen, s));
        }
    }

    private boolean isOurs(WorldCreationUiState state) {
        var type = state.getWorldType();
        return type != null && type.preset() != null && type.preset().is(preset);
    }

    private void onChange(CreateWorldScreen screen, WorldCreationUiState state) {
        boolean ours = isOurs(state);
        Boolean was = seen.put(state, ours);
        WorldDataConfiguration cur = state.getSettings().dataConfiguration();
        List<String> enabled = new ArrayList<>(cur.dataPacks().getEnabled());
        boolean on = enabled.containsAll(level.packIds());
        if ((was != null && was == ours) || on == ours) {
            return;                       // not a change of type, or the packs already match it (the reload's own call)
        }
        enabled.removeAll(level.packIds());
        if (ours) {
            enabled.addAll(level.packIds());
        }
        PackRepository repo = screen.getDataPackSelectionSettings(cur).getSecond();
        if (repo == null) {
            log.warn("the Create World screen gave no pack list: {}'s packs were not {}", level.typeName(),
                    ours ? "ticked" : "unticked");
            return;
        }
        repo.setSelected(enabled);
        log.info("World Type {}: its packs {}", ours ? level.typeName() : "changed", ours ? "ticked" : "unticked");
        screen.tryApplyNewDataPacks(repo, false, c -> {
        });
    }
}
