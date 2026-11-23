package com.moddingfromamod.isle;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * <b>Put a copy of the starter island at an origin</b>.
 *
 * <p><b>Where the island comes from, and why</b> — three candidates were weighed:
 * <ol>
 *   <li><b>(chosen) the pack's own template, copied into our resources</b>
 *       ({@code data/moddingfromamod/structure/isle/starter_island.nbt}, byte for byte, MIT notice
 *       beside it). Deterministic: one file, one rotation, one placement, no randomness. Reproducible:
 *       it is in the jar, so a GameTest and a server place the same blocks. Survives a pack update: a
 *       new Standard SkyBlock changes the island at SPAWN, never the children's, and changing theirs
 *       is a deliberate commit that swaps this file.</li>
 *   <li><b>the pack's own placement</b> ({@code place jigsaw skyvoid_standard_skyblock:starter_island
 *       … ~ 64 ~} run at another position). Rejected: {@code place jigsaw} picks a RANDOM rotation for
 *       its start piece, and the chest is a loot table the game rolls into random slots, so two
 *       children would not start in the same state; it also exists only in a world made with the pack,
 *       so no GameTest could run it, and its name and pool are the pack's to change in an update.</li>
 *   <li><b>our own equivalent island</b>. Rejected: it would no longer be the island the pack's
 *       advancements and guide describe, for no gain over (1).</li>
 * </ol>
 *
 * <p>Two things differ from what the pack does at spawn, both to make every copy identical: the
 * template's jigsaw block becomes {@code minecraft:bedrock} (its own {@code final_state}), and its
 * chest gets the pack's starter loot — one ice, one lava bucket ({@code starter_chest.json}, which has
 * no randomness in WHAT, only in which slot) — in fixed slots instead of the loot table. A sign beside the chest says what to do with them ({@link StarterSign}).
 */
public final class IslandPlacer {

    /** Our copy of Standard SkyBlock's starter island. */
    public static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath("moddingfromamod", "isle/starter_island");

    /** Where the template's jigsaw (the island's origin) sits inside it. */
    static final int JIGSAW_X = 3;
    static final int JIGSAW_Y = 0;
    static final int JIGSAW_Z = 1;

    private IslandPlacer() {
    }

    /**
     * Place the island so its origin (the bedrock) is at {@code origin}. Returns false when the template
     * is missing from the jar — a build fault, said loudly by the caller.
     */
    public static boolean place(ServerLevel level, BlockPos origin) {
        return place(level, origin, false);
    }

    /**
     * The same, {@code bare} for the canary's island (#1637): its chest left EMPTY and no sign, so a
     * player who reached it anyway would find nothing worth the trip.
     */
    public static boolean place(ServerLevel level, BlockPos origin, boolean bare) {
        StructureTemplate template = level.getStructureManager().get(TEMPLATE).orElse(null);
        if (template == null) {
            return false;
        }
        BlockPos corner = origin.offset(-JIGSAW_X, -JIGSAW_Y, -JIGSAW_Z);
        // Seeded by nothing that varies: the settings have no processors and no rotation, so the
        // random source is never consulted for anything a child could see.
        template.placeInWorld(level, corner, corner, new StructurePlaceSettings(),
                RandomSource.create(0L), 2);
        var size = template.getSize();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos p = corner.offset(x, y, z);
                    if (level.getBlockState(p).is(Blocks.JIGSAW)) {
                        level.setBlock(p, Blocks.BEDROCK.defaultBlockState(), 2);
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof RandomizableContainer chest) {
                        chest.setLootTable(null);
                        for (int i = 0; i < chest.getContainerSize(); i++) {
                            chest.setItem(i, ItemStack.EMPTY);
                        }
                        if (!bare) {
                            chest.setItem(0, new ItemStack(Items.ICE));
                            chest.setItem(1, new ItemStack(Items.LAVA_BUCKET));
                        }
                        be.setChanged();
                    }
                }
            }
        }
        if (!bare) {
            sign(level, corner);
        }
        return true;
    }

    /**
     * the sign beside the chest ({@link StarterSign}) — what to do with the ice and the lava,
     * and where the goals are. Only onto air standing on something solid, so a template change that
     * moved the chest can never put a sign in mid-air or inside a block.
     */
    static void sign(ServerLevel level, BlockPos corner) {
        BlockPos at = corner.offset(StarterSign.X, StarterSign.Y, StarterSign.Z);
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isSolid()) {
            return;
        }
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, StarterSign.ROTATION), 2);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            net.minecraft.world.level.block.entity.SignText text = new net.minecraft.world.level.block.entity.SignText();
            for (int i = 0; i < StarterSign.LINES.size(); i++) {
                text = text.setMessage(i, net.minecraft.network.chat.Component.literal(StarterSign.LINES.get(i)));
            }
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }

    /** The template's size, for a test that compares two islands block for block. */
    public static net.minecraft.core.Vec3i size(ServerLevel level) {
        return level.getStructureManager().get(TEMPLATE).map(StructureTemplate::getSize).orElse(null);
    }

    /** The template's corner for an origin. */
    public static BlockPos corner(BlockPos origin) {
        return origin.offset(-JIGSAW_X, -JIGSAW_Y, -JIGSAW_Z);
    }
}
