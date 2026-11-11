package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A screen may only cover actual, loaded TV blocks with matching layout data. */
public final class TvScreenLayout {
    private TvScreenLayout() {}

    public record Layout(BlockPos controller, Direction facing, int width, int height) {}

    public static @Nullable Layout resolve(@Nullable Level level, BlockPos member) {
        if (level == null || !level.isLoaded(member)) return null;
        // Use the world's state, which is also what the casing model renders, not a cached BE state.
        BlockState state = level.getBlockState(member);
        if (!(state.getBlock() instanceof TvBlock)) return null;
        Direction facing = state.getValue(TvBlock.FACING);
        if (state.getBlock() instanceof BigTvBlock big) {
            int x = state.getValue(BigTvBlock.PART_X), y = state.getValue(BigTvBlock.PART_Y);
            int width = big.widthBlocks(), height = big.heightBlocks();
            if (x < 0 || x >= width || y < 0 || y >= height) return null;
            BlockPos controller = big.controllerPos(state, member);
            for (int cx = 0; cx < width; cx++) {
                for (int cy = 0; cy < height; cy++) {
                    BlockPos pos = BigTvBlock.partPos(controller, facing, cx, cy);
                    if (!level.isLoaded(pos)) return null;
                    BlockState part = level.getBlockState(pos);
                    if (!part.is(big) || part.getValue(TvBlock.FACING) != facing
                            || part.getValue(BigTvBlock.PART_X) != cx || part.getValue(BigTvBlock.PART_Y) != cy) return null;
                }
            }
            if (!(level.getBlockEntity(controller) instanceof TvBlockEntity)) return null;
            return new Layout(controller, facing, width, height);
        }
        if (!state.is(ModRegistry.TV.get()) || !(level.getBlockEntity(member) instanceof TvBlockEntity tv)) return null;
        int width = tv.wallWidth(), height = tv.wallHeight();
        int x = tv.wallX(), y = tv.wallY();
        if (!TvWall.supportedSize(width, height) || x < 0 || x >= width || y < 0 || y >= height) return null;
        BlockPos controller = TvWall.memberPos(member, facing, -x, -y);
        for (int cx = 0; cx < width; cx++) {
            for (int cy = 0; cy < height; cy++) {
                BlockPos pos = TvWall.memberPos(controller, facing, cx, cy);
                if (!level.isLoaded(pos)) return null;
                BlockState part = level.getBlockState(pos);
                if (!part.is(ModRegistry.TV.get()) || part.getValue(TvBlock.FACING) != facing
                        || part.getValue(TvBlock.WALL_COLUMN) != TvBlock.WallPart.column(cx, width)
                        || part.getValue(TvBlock.WALL_ROW) != TvBlock.WallPart.row(cy, height)) return null;
                if (!(level.getBlockEntity(pos) instanceof TvBlockEntity other)
                        || other.wallX() != cx || other.wallY() != cy
                        || other.wallWidth() != width || other.wallHeight() != height) return null;
            }
        }
        return new Layout(controller, facing, width, height);
    }
}
