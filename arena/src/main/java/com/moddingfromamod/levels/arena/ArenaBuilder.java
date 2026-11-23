package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>Puts the arena's place in the world</b>: {@link ArenaPlace#fills()} in order, each {@link ArenaPlace.Kind} as one
 * block. Every decision about where is in {@link ArenaPlace}; this only picks the blocks and places them.
 */
final class ArenaBuilder {

    private ArenaBuilder() {
    }

    /** The block a kind is built of. */
    static BlockState blockOf(ArenaPlace.Kind kind) {
        return switch (kind) {
            case ARENA_FLOOR -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case ARENA_WALL -> Blocks.STONE_BRICKS.defaultBlockState();
            case ARENA_RIM -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case PREP_FLOOR -> Blocks.OAK_PLANKS.defaultBlockState();
            case PREP_LIGHT -> Blocks.SEA_LANTERN.defaultBlockState();
            case GLASS -> Blocks.GLASS.defaultBlockState();
            case PREP_ROOF -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
            // opens toward the arena; a player opens it, a mob cannot
            case GATE -> Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH);
        };
    }

    /** Build the whole place; returns how many blocks were set. Server thread. */
    static int build(ServerLevel level, ArenaPlace place) {
        int set = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Fill f : place.fills()) {
            BlockState state = blockOf(f.kind());
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        at.set(x, y, z);
                        if (!level.getBlockState(at).equals(state)) {
                            level.setBlock(at, state, Block.UPDATE_CLIENTS);
                            set++;
                        }
                    }
                }
            }
        }
        return set;
    }
}
