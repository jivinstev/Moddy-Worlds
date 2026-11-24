package com.moddingfromamod.levels.arena;

import com.moddingfromamod.levels.arena.ArenaPlace.Box;
import com.moddingfromamod.levels.arena.ArenaPlace.Fill;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
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
            case CONTROL -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            // on the wall to its north, facing into the prep room
            case START_BUTTON -> Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL)
                    .setValue(ButtonBlock.FACING, Direction.SOUTH);
            case START_SIGN -> Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH);
        };
    }

    /** Puts one block of a kind, writing the sign's words when it is the start sign; true when it changed. */
    private static boolean put(ServerLevel level, BlockPos at, ArenaPlace.Kind kind, int flags) {
        BlockState state = blockOf(kind);
        if (level.getBlockState(at).equals(state)) {
            return false;
        }
        level.setBlock(at, state, flags);
        if (kind == ArenaPlace.Kind.START_SIGN && level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            // 26.3: SignText is built through its Mutable, and a side is a SignTextSlot
            SignText.Mutable text = SignText.EMPTY.asMutable();
            List<String> lines = ArenaWords.SIGN;
            for (int i = 0; i < Math.min(lines.size(), SignText.LINES); i++) {
                text.setLine(i, Component.literal(lines.get(i)));
            }
            sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(at, state, state, Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /**
     * Gives a place built without one its start control ({@link ArenaPlace#controlFills()}): only where the block is
     * still what was built there (the window's glass, the prep room's air), so nothing a player put there is replaced.
     * Returns how many blocks were set, or -1 when something stood in the way (then nothing is placed).
     */
    static int addStartControl(ServerLevel level, ArenaPlace place) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Fill f : place.controlFills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        BlockState now = level.getBlockState(at.set(x, y, z));
                        boolean asBuilt = f.kind() == ArenaPlace.Kind.CONTROL ? now.is(Blocks.GLASS) : now.isAir();
                        if (!asBuilt && !now.equals(blockOf(f.kind()))) {
                            return -1;
                        }
                    }
                }
            }
        }
        int set = 0;
        for (Fill f : place.controlFills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int y = b.y0(); y <= b.y1(); y++) {
                    for (int z = b.z0(); z <= b.z1(); z++) {
                        set += put(level, new BlockPos(x, y, z), f.kind(), Block.UPDATE_ALL) ? 1 : 0;
                    }
                }
            }
        }
        return set;
    }

    /** True when the start button is pressed right now (its chunk loaded and the button down). Server thread. */
    static boolean buttonDown(ServerLevel level, ArenaPlace place) {
        int[] b = place.startButton();
        BlockPos at = new BlockPos(b[0], b[1], b[2]);
        if (!level.isLoaded(at)) {
            return false;
        }
        BlockState s = level.getBlockState(at);
        return s.getBlock() instanceof ButtonBlock && s.getValue(ButtonBlock.POWERED);
    }

    /**
     * Puts the arena back as built ({@link ArenaPlace#reset()}): its inside cleared to the sky, its floor, walls, rim,
     * window, gate and start control. The gate is left as it stands if it is still an oak fence gate (open or shut).
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
