package com.billyke2000s.moviehubtv.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The TV. FACING is the direction the screen faces (towards the player who placed it).
 * Right-clicking without the remote turns it on or off; the remote is handled client-side.
 */
public class TvBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TvBlock> CODEC = simpleCodec(TvBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Where a normal TV sits in a TV wall, so the frames join into one big frame. */
    public static final EnumProperty<WallPart> WALL_COLUMN = EnumProperty.create("wall_column", WallPart.class,
            WallPart.SINGLE, WallPart.LEFT, WallPart.MIDDLE, WallPart.RIGHT);
    public static final EnumProperty<WallPart> WALL_ROW = EnumProperty.create("wall_row", WallPart.class,
            WallPart.SINGLE, WallPart.BOTTOM, WallPart.MIDDLE, WallPart.TOP);

    public enum WallPart implements StringRepresentable {
        SINGLE, LEFT, MIDDLE, RIGHT, BOTTOM, TOP;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }

        static WallPart column(int x, int width) {
            return width <= 1 ? SINGLE : x == 0 ? LEFT : x == width - 1 ? RIGHT : MIDDLE;
        }
        static WallPart row(int y, int height) {
            return height <= 1 ? SINGLE : y == 0 ? BOTTOM : y == height - 1 ? TOP : MIDDLE;
        }
    }

    public TvBlock(Properties properties) {
        super(properties);
        BlockState d = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false);
        if (d.hasProperty(WALL_COLUMN)) d = d.setValue(WALL_COLUMN, WallPart.SINGLE).setValue(WALL_ROW, WallPart.SINGLE);
        registerDefaultState(d);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
        // Only the normal TV joins walls; the old crafted big TVs keep their own parts.
        if (getClass() == TvBlock.class) builder.add(WALL_COLUMN, WALL_ROW);
    }

    /** Screen size in blocks. The normal TV is one block. */
    public int widthBlocks() { return 1; }

    public int heightBlocks() { return 1; }

    /** The block holding the TV's state (itself for a one-block TV). */
    public BlockPos controllerPos(BlockState state, BlockPos pos) { return pos; }

    /** As above, but also follows a TV wall made of separate TVs to its controller. */
    public BlockPos controllerPos(BlockGetter level, BlockState state, BlockPos pos) {
        if (level instanceof Level world) {
            TvScreenLayout.Layout layout = TvScreenLayout.resolve(world, pos);
            // A click on stale metadata repairs this block instead of acting on an unrelated position.
            if (layout != null) return layout.controller();
            if (state.is(com.billyke2000s.moviehubtv.ModRegistry.TV.get())) return pos;
        }
        return controllerPos(state, pos);
    }

    /** The viewer's right-hand direction when looking at a screen that faces {@code facing}. */
    public static Direction rightOf(Direction facing) { return facing.getCounterClockWise(); }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, TvPlacement.facing(context));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TvBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(controllerPos(level, state, pos)) instanceof TvBlockEntity tv) {
            tv.apply(TvBlockEntity.ACTION_POWER, "");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A TV placed next to others may complete a rectangle: regroup at the end of the tick, when the new
     * TV's block entity exists (it is created after onPlace). Big crafted TVs never join walls.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof net.minecraft.server.level.ServerLevel server && !oldState.is(this) && getClass() == TvBlock.class) {
            BigTvConverter.queueRegroup(server, pos);
        }
    }

    /** Removing a TV splits its wall; regroup what is left. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !state.is(newState.getBlock()) && getClass() == TvBlock.class) {
            TvWall.recomputeAround(level, pos, state.getValue(FACING));
        }
    }
}
