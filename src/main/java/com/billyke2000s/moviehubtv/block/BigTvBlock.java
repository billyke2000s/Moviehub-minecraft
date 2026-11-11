package com.billyke2000s.moviehubtv.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A TV made of several blocks (2x1 wide, 2x2 large, 3x2 large wide).
 *
 * PART_X counts columns from the viewer's left (0) to right; PART_Y counts rows from the bottom (0).
 * Part (0, 0), the bottom-left as the viewer sees it, is the controller: it holds the
 * {@link TvBlockEntity} and the whole screen is drawn from it. When placed, the TV grows to the
 * placer's right and upwards from the clicked spot. Breaking any part removes the whole TV
 * (only the broken part drops the item, so you always get exactly one back).
 */
public class BigTvBlock extends TvBlock {
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 1);

    public static final MapCodec<BigTvBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 3).fieldOf("width").forGetter(BigTvBlock::widthBlocks),
            Codec.intRange(1, 2).fieldOf("height").forGetter(BigTvBlock::heightBlocks),
            propertiesCodec()
    ).apply(i, BigTvBlock::new));

    private final int width;
    private final int height;

    public BigTvBlock(int width, int height, Properties properties) {
        super(properties);
        this.width = width;
        this.height = height;
        registerDefaultState(defaultBlockState().setValue(PART_X, 0).setValue(PART_Y, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART_X, PART_Y);
    }

    @Override
    public int widthBlocks() { return width; }

    @Override
    public int heightBlocks() { return height; }

    /** Position of part (px, py) for a TV whose controller is at controller. */
    public static BlockPos partPos(BlockPos controller, Direction facing, int px, int py) {
        return controller.relative(rightOf(facing), px).above(py);
    }

    @Override
    public BlockPos controllerPos(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return pos.relative(rightOf(facing), -state.getValue(PART_X)).below(state.getValue(PART_Y));
    }

    private boolean isController(BlockState state) {
        return state.getValue(PART_X) == 0 && state.getValue(PART_Y) == 0;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                BlockPos p = partPos(origin, facing, px, py);
                if (p.getY() >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(p)) return null;
                if (!level.getBlockState(p).canBeReplaced(context)) return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART_X, 0).setValue(PART_Y, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                if (px == 0 && py == 0) continue;
                level.setBlock(partPos(pos, facing, px, py),
                        state.setValue(PART_X, px).setValue(PART_Y, py), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level.isClientSide || state.is(newState.getBlock()) || BigTvConverter.converting) return;
        // One part went: take the rest of the TV with it (no extra drops).
        Direction facing = state.getValue(FACING);
        BlockPos controller = controllerPos(state, pos);
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                BlockPos p = partPos(controller, facing, px, py);
                if (p.equals(pos)) continue;
                BlockState other = level.getBlockState(p);
                if (other.is(this) && other.getValue(FACING) == facing
                        && other.getValue(PART_X) == px && other.getValue(PART_Y) == py) {
                    level.removeBlock(p, false);
                }
            }
        }
    }

    /** Sets POWERED on every part so the whole TV glows together. */
    public void setPoweredAll(Level level, BlockPos controller, BlockState controllerState, boolean powered) {
        Direction facing = controllerState.getValue(FACING);
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                BlockPos p = partPos(controller, facing, px, py);
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(POWERED) != powered) {
                    level.setBlock(p, s.setValue(POWERED, powered), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new TvBlockEntity(pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(controllerPos(state, pos)) instanceof TvBlockEntity tv) {
            tv.apply(TvBlockEntity.ACTION_POWER, "");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
