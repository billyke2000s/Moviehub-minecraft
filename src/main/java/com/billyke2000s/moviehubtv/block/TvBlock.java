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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The TV. FACING is the direction the screen faces (towards the player who placed it).
 * Right-clicking without the remote turns it on or off; the remote is handled client-side.
 */
public class TvBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TvBlock> CODEC = simpleCodec(TvBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TvBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    /** Screen size in blocks. The normal TV is one block. */
    public int widthBlocks() { return 1; }

    public int heightBlocks() { return 1; }

    /** The block holding the TV's state (itself for a one-block TV). */
    public BlockPos controllerPos(BlockState state, BlockPos pos) { return pos; }

    /** As above, but also follows a TV wall made of separate TVs to its controller. */
    public BlockPos controllerPos(BlockGetter level, BlockState state, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TvBlockEntity tv && tv.inWall()) return tv.controllerPos();
        return controllerPos(state, pos);
    }

    /** The viewer's right-hand direction when looking at a screen that faces {@code facing}. */
    public static Direction rightOf(Direction facing) { return facing.getCounterClockWise(); }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
