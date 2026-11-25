package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

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
            // the battle arena control panel: a black board, blue selector blocks, green blocks round the start button
            case PANEL_BOARD -> Blocks.CONCRETE.black().defaultBlockState();
            case PANEL_SELECT -> Blocks.CONCRETE.lightBlue().defaultBlockState();
            case PANEL_START -> Blocks.CONCRETE.lime().defaultBlockState();
            // on the board to its north, facing into the prep room
            case SELECT_BUTTON, START_BUTTON -> Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH);
        };
    }

    /** Puts one block of a kind; true when it changed. */
    private static boolean put(ServerLevel level, BlockPos at, ArenaPlace.Kind kind, int flags) {
        BlockState state = blockOf(kind);
        if (level.getBlockState(at).equals(state)) {
            return false;
        }
        level.setBlock(at, state, flags);
        return true;
    }

    /** True when a block is one the panel's board may be built over: the window's glass, or the panel's own blocks. */
    private static boolean boardMayReplace(BlockState now) {
        return now.is(Blocks.GLASS) || now.is(Blocks.CHISELED_STONE_BRICKS) || now.is(Blocks.CONCRETE.black())
                || now.is(Blocks.CONCRETE.lightBlue()) || now.is(Blocks.CONCRETE.lime());
    }

    /**
     * Gives a place built without one its battle arena control panel ({@link ArenaPlace#controlFills()}), in place of
     * the first start control (a button and a sign beside the gate, on a chiseled stone brick block) or of the bare
     * window: only where the blocks are still as built (the window's glass or the first control's block, the prep
     * room's air), so nothing a player put there is replaced. The first control's button and sign go, if they are
     * still there. Returns how many blocks were set, or -1 when something stood in the way (then nothing is placed).
     * Running it again on a place that has its panel sets nothing.
     */
    static int addControl(ServerLevel level, ArenaPlace place) {
        List<ArenaPanel.Placed> blocks = place.panel().blocks();
        int wz = place.sharedWallZ();
        for (ArenaPanel.Placed p : blocks) {
            BlockState now = level.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
            boolean asBuilt = p.z() == wz ? boardMayReplace(now) : now.isAir() || now.is(Blocks.STONE_BUTTON);
            if (!asBuilt) {
                return -1;
            }
        }
        int set = 0;
        // the first start control's button and sign, before the block they hang on is changed
        for (int[] old : place.firstStartControl()) {
            BlockPos at = new BlockPos(old[0], old[1], old[2]);
            BlockState now = level.getBlockState(at);
            boolean panelsOwn = blocks.stream().anyMatch(p -> p.x() == old[0] && p.y() == old[1] && p.z() == old[2]);
            if (!panelsOwn && (now.is(Blocks.STONE_BUTTON) || now.is(Blocks.OAK_WALL_SIGN))) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                set++;
            }
        }
        for (ArenaPanel.Placed p : blocks) {
            BlockPos at = new BlockPos(p.x(), p.y(), p.z());
            BlockState now = level.getBlockState(at);
            BlockState want = blockOf(p.kind());
            boolean same = now.getBlock() instanceof ButtonBlock && want.getBlock() == now.getBlock()
                    ? now.getValue(ButtonBlock.FACING) == want.getValue(ButtonBlock.FACING)
                            && now.getValue(ButtonBlock.FACE) == want.getValue(ButtonBlock.FACE)
                    : now.equals(want);
            if (!same) {
                level.setBlock(at, want, Block.UPDATE_ALL);
                set++;
            }
        }
        return set;
    }

    /** True when the button at {@code b} is pressed right now (its chunk loaded and the button down). Server thread. */
    static boolean buttonDown(ServerLevel level, int[] b) {
        BlockPos at = new BlockPos(b[0], b[1], b[2]);
        if (!level.isLoaded(at)) {
            return false;
        }
        BlockState s = level.getBlockState(at);
        return s.getBlock() instanceof ButtonBlock && s.getValue(ButtonBlock.POWERED);
    }

    /**
     * Puts the arena back as built ({@link ArenaPlace#reset()}): its inside cleared to the sky, its floor, walls, rim,
     * window, gate and the control panel's board. The gate is left as it stands if it is still an oak fence gate (open or shut).
     * Nothing drops from what is cleared. Returns how many blocks were set. Server thread.
     */
    static int reset(ServerLevel level, ArenaPlace place) {
        ArenaPlace.Reset plan = place.reset();
        Box box = place.resetBox(level.getMaxY());
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
                | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        int set = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = box.x0(); x <= box.x1(); x++) {
            for (int z = box.z0(); z <= box.z1(); z++) {
                for (int y = box.y0(); y <= box.y1(); y++) {
                    ArenaPlace.Kind k = plan.kindAt(x, y, z);
                    if (k == null) {
                        continue;
                    }
                    at.set(x, y, z);
                    if (k == ArenaPlace.Kind.GATE && level.getBlockState(at).is(Blocks.OAK_FENCE_GATE)) {
                        continue;
                    }
                    if (k == ArenaPlace.Kind.AIR && level.getBlockState(at).isAir()) {
                        continue;
                    }
                    set += put(level, at.immutable(), k, flags) ? 1 : 0;
                }
            }
        }
        return set;
    }

    /** Build the whole place; returns how many blocks were set. Server thread. */
    static int build(ServerLevel level, ArenaPlace place) {
        int set = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Fill f : place.fills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        at.set(x, y, z);
                        set += put(level, at.immutable(), f.kind(), Block.UPDATE_CLIENTS) ? 1 : 0;
                    }
                }
            }
        }
        return set;
    }
}
