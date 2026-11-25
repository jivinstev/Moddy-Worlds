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
            // the gateway while a wave is on: nobody opens iron bars, and everyone sees through them
            case GATE_BARS -> Blocks.IRON_BARS.defaultBlockState();
            // the battle arena control panel: a black board, blue selector blocks, green blocks round the start button
            case PANEL_BOARD -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case PANEL_SELECT -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case PANEL_START -> Blocks.LIME_CONCRETE.defaultBlockState();
            // the Champions board on the prep room's back wall
            case CHAMPIONS_BOARD, LADDER_BOARD -> Blocks.BLACK_CONCRETE.defaultBlockState();
            // the base the place stands on: ground round it, a layer a blast can break, bedrock under that
            case GROUND -> Blocks.GRASS_BLOCK.defaultBlockState();
            case BASE_FILL -> Blocks.STONE.defaultBlockState();
            case BASE_BEDROCK -> Blocks.BEDROCK.defaultBlockState();
            // the hand-over spot in the prep room's floor, where a finished make shows before it goes to him
            case HAND_OVER_SPOT -> Blocks.GOLD_BLOCK.defaultBlockState();
            // on the board to its north, facing into the prep room
            case SELECT_BUTTON, START_BUTTON -> Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH);
        };
    }

    /** Puts one block of a kind; true when it changed. */
    private static boolean put(ServerLevel level, BlockPos at, ArenaPlace.Kind kind, int flags) {
        BlockState state = blockOf(kind);
        if (kind == ArenaPlace.Kind.GATE_BARS) {
            if (level.getBlockState(at).is(Blocks.IRON_BARS)) {
                return false;                   // bars, joined to their neighbours however they are
            }
            state = Block.updateFromNeighbourShapes(state, level, at);
        }
        if (level.getBlockState(at).equals(state)) {
            return false;
        }
        level.setBlock(at, state, flags);
        return true;
    }

    /** True when a block is one the panel's board may be built over: the window's glass, or the panel's own blocks. */
    private static boolean boardMayReplace(BlockState now) {
        return now.is(Blocks.GLASS) || now.is(Blocks.CHISELED_STONE_BRICKS) || now.is(Blocks.BLACK_CONCRETE)
                || now.is(Blocks.LIGHT_BLUE_CONCRETE) || now.is(Blocks.LIME_CONCRETE);
    }

    /**
     * Gives a place built without one its battle arena control panel ({@link ArenaPlace#controlFills()}), in place of
     * the first start control (a button and a sign beside the gate, on a chiseled stone brick block) or of the bare
     * window: only where the blocks are still as built (the window's glass or the first control's block, the prep
     * room's air), so nothing a player put there is replaced. The first control's button and sign go, if they are
     * still there, and so do the buttons of the panel before 0.1.6 that this one does not use. Returns how many blocks
     * were set, or -1 when something stood in the way (then nothing is placed).
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
        // the buttons of the panel before 0.1.6 (it had a MONSTERS selector) that this one does not use
        for (int[] old : place.panel().oldButtons()) {
            BlockPos at = new BlockPos(old[0], old[1], old[2]);
            boolean panelsOwn = blocks.stream().anyMatch(p -> p.x() == old[0] && p.y() == old[1] && p.z() == old[2]);
            if (!panelsOwn && level.getBlockState(at).is(Blocks.STONE_BUTTON)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                set++;
            }
        }
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

    /**
     * Gives a place built without one its Champions board ({@link ArenaPlace#championsFills()}): over the prep room's
     * back wall, only where it is still the glass it was built of (or the board), so nothing a player put there is
     * replaced. Returns how many blocks were set, or -1 when something stood in the way (then nothing is placed).
     * Running it again on a place that has its board sets nothing.
     */
    static int addChampions(ServerLevel level, ArenaPlace place) {
        Box b = place.champions().board();
        for (int x = b.x0(); x <= b.x1(); x++) {
            for (int y = b.y0(); y <= b.y1(); y++) {
                BlockState now = level.getBlockState(new BlockPos(x, y, b.z0()));
                if (!now.is(Blocks.GLASS) && !now.is(Blocks.BLACK_CONCRETE)) {
                    return -1;
                }
            }
        }
        int set = 0;
        for (int x = b.x0(); x <= b.x1(); x++) {
            for (int y = b.y0(); y <= b.y1(); y++) {
                set += put(level, new BlockPos(x, y, b.z0()), ArenaPlace.Kind.CHAMPIONS_BOARD, Block.UPDATE_ALL) ? 1 : 0;
            }
        }
        return set;
    }

    /**
     * Puts the base under a place built without one ({@link ArenaPlace#baseFills()}): the bedrock and the fill layer
     * under the whole place and round it, and the ground round the floors only where nothing stands (so a floor, a
     * wall, or anything a player put there stays). Running it again sets nothing. Returns how many blocks were set.
     */
    static int addBase(ServerLevel level, ArenaPlace place) {
        int set = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Fill f : place.baseFills()) {
            Box b = f.box();
            for (int x = b.x0(); x <= b.x1(); x++) {
                for (int z = b.z0(); z <= b.z1(); z++) {
                    for (int y = b.y0(); y <= b.y1(); y++) {
                        at.set(x, y, z);
                        if (f.kind() == ArenaPlace.Kind.GROUND && !level.getBlockState(at).canBeReplaced()) {
                            continue;
                        }
                        set += put(level, at.immutable(), f.kind(), Block.UPDATE_CLIENTS) ? 1 : 0;
                    }
                }
            }
        }
        return set;
    }

    /**
     * Mends the prep room's floor where a block of it is missing (air, or anything a player could fall through): the
     * oak planks and the lights, as built. Never the floor's other blocks: what a player put on it stays. Returns how
     * many blocks were set.
     */
    static int mendPrepFloor(ServerLevel level, ArenaPlace place) {
        int set = 0;
        Box b = place.prepFloor();
        for (int x = b.x0(); x <= b.x1(); x++) {
            for (int z = b.z0(); z <= b.z1(); z++) {
                set += mendFloorBlock(level, place, x, b.y0(), z) ? 1 : 0;
            }
        }
        return set;
    }

    /**
     * Adds the wish ladder board to a place built before it (its west wall's glass beside the control panel turned to
     * the board), only where that glass still stands as built; -1 (nothing set) when something else stands there.
     */
    static int addLadderBoard(ServerLevel level, ArenaPlace place) {
        Box b = place.panel().ladderBoard();
        for (int z = b.z0(); z <= b.z1(); z++) {
            for (int y = b.y0(); y <= b.y1(); y++) {
                BlockState now = level.getBlockState(new BlockPos(b.x0(), y, z));
                if (!now.is(Blocks.GLASS) && !now.is(Blocks.BLACK_CONCRETE)) {
                    return -1;
                }
            }
        }
        int set = 0;
        for (int z = b.z0(); z <= b.z1(); z++) {
            for (int y = b.y0(); y <= b.y1(); y++) {
                set += put(level, new BlockPos(b.x0(), y, z), ArenaPlace.Kind.LADDER_BOARD, Block.UPDATE_ALL) ? 1 : 0;
            }
        }
        return set;
    }

    /**
     * Sets the hand-over spot in the prep room's floor ({@link ArenaPlace#handOverSpot}), only where the floor is still
     * as built (oak planks) or is the spot already: the blocks set (0 or 1), or -1 when something else stands there.
     */
    static int addHandOverSpot(ServerLevel level, ArenaPlace place) {
        int[] h = place.handOverSpot();
        BlockPos at = new BlockPos(h[0], h[1], h[2]);
        BlockState now = level.getBlockState(at);
        if (now.is(Blocks.GOLD_BLOCK)) {
            return 0;
        }
        if (!now.is(Blocks.OAK_PLANKS)) {
            return -1;
        }
        return put(level, at, ArenaPlace.Kind.HAND_OVER_SPOT, Block.UPDATE_ALL) ? 1 : 0;
    }

    /** Puts back the floor under the world spawn (the arrival spot) if it is missing; true when it was. */
    static boolean keepSpawnFloor(ServerLevel level, ArenaPlace place) {
        int[] f = place.spawnFloor();
        return mendFloorBlock(level, place, f[0], f[1], f[2]);
    }

    private static boolean mendFloorBlock(ServerLevel level, ArenaPlace place, int x, int y, int z) {
        BlockPos at = new BlockPos(x, y, z);
        if (!level.isLoaded(at) || !level.getBlockState(at).canBeReplaced()) {
            return false;
        }
        return put(level, at, place.isPrepLight(x, z) ? ArenaPlace.Kind.PREP_LIGHT : ArenaPlace.Kind.PREP_FLOOR,
                Block.UPDATE_ALL);
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
     * window, gate and the control panel's board. The gateway is as {@code shut} says ({@link ArenaGate#kind}): iron
     * bars while a wave is on, else the fence gate (left as it stands if it is still an oak fence gate, open or shut)
     * and the opening above it. Nothing drops from what is cleared. Returns how many blocks were set. Server thread.
     */
    static int reset(ServerLevel level, ArenaPlace place, boolean shut) {
        ArenaPlace.Reset plan = place.reset();
        Box box = place.resetBox(level.getMaxY());
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
                | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        int set = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = box.x0(); x <= box.x1(); x++) {
            for (int z = box.z0(); z <= box.z1(); z++) {
                for (int y = box.y0(); y <= box.y1(); y++) {
                    ArenaPlace.Kind k = ArenaGate.isGateway(place, x, y, z) ? ArenaGate.kind(place, y, shut)
                            : plan.kindAt(x, y, z);
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

    /**
     * Shuts the gate ({@code shut}: iron bars in the gateway, whatever stands there) or opens it (the bars, if they are
     * there, become the fence gate and the air above it; anything else is left as it stands). Returns how many blocks
     * were set. Server thread.
     */
    static int gate(ServerLevel level, ArenaPlace place, boolean shut) {
        int set = 0;
        for (int[] g : ArenaGate.gateway(place)) {
            BlockPos at = new BlockPos(g[0], g[1], g[2]);
            if (!level.isLoaded(at)) {
                continue;
            }
            if (shut || level.getBlockState(at).is(Blocks.IRON_BARS)) {
                set += put(level, at, ArenaGate.kind(place, g[1], shut), Block.UPDATE_ALL) ? 1 : 0;
            }
        }
        return set;
    }

    /** True when the gateway is shut now (iron bars in the gate's block). Server thread. */
    static boolean gateShut(ServerLevel level, ArenaPlace place) {
        int[] g = place.gate();
        return level.getBlockState(new BlockPos(g[0], g[1], g[2])).is(Blocks.IRON_BARS);
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
