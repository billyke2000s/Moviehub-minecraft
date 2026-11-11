package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Joins normal TVs placed next to each other into one big screen ("TV wall").
 *
 * TVs that face the same way and touch side by side or above/below form a group. If the group fills
 * a supported rectangle (2x1, 2x2 or 3x2), every TV in it becomes part of
 * one screen: the bottom-left TV (as the viewer sees it) is the controller, whose power and channel
 * the whole wall uses. Any other shape stays as separate TVs. Worked out on the server whenever a TV
 * is placed or removed, saved in each TV's block entity and synced to clients.
 */
public final class TvWall {
    private TvWall() {}

    public static final int MAX_WIDTH = 12; // Retained for reading existing wall metadata.
    public static final int MAX_HEIGHT = 8;
    private static final int MAX_MEMBERS = MAX_WIDTH * MAX_HEIGHT;

    private static boolean isWallTv(BlockState state, Direction facing) {
        return state.is(ModRegistry.TV.get()) && state.getValue(TvBlock.FACING) == facing;
    }

    /** Recomputes the group containing start (if start is a normal TV). Server side only. */
    public static boolean recompute(Level level, BlockPos start) {
        if (level.isClientSide) return true;
        if (!level.isLoaded(start)) return false;
        BlockState first = level.getBlockState(start);
        if (!first.is(ModRegistry.TV.get())) return true;
        Direction facing = first.getValue(TvBlock.FACING);
        Direction right = TvBlock.rightOf(facing);

        // Flood fill across the screen plane: coordinates are (columns to the right, rows up) from start.
        Map<BlockPos, int[]> members = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        members.put(start.immutable(), new int[]{0, 0});
        queue.add(start.immutable());
        boolean tooBig = false;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int[] c = members.get(p);
            for (int[] step : steps) {
                BlockPos n = p.relative(right, step[0]).above(step[1]);
                if (members.containsKey(n)) continue;
                if (!level.isLoaded(n)) return false;
                if (!isWallTv(level.getBlockState(n), facing)) continue;
                if (members.size() >= MAX_MEMBERS) {
                    tooBig = true;
                    continue;
                }
                members.put(n.immutable(), new int[]{c[0] + step[0], c[1] + step[1]});
                queue.add(n.immutable());
            }
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] c : members.values()) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        boolean wall = !tooBig && members.size() > 1 && members.size() == width * height
                && supportedSize(width, height);

        for (BlockPos p : members.keySet()) {
            if (!(level.getBlockEntity(p) instanceof TvBlockEntity)) return false;
        }

        // Join (or split) the frames first; same block, so the block entities stay.
        for (Map.Entry<BlockPos, int[]> e : members.entrySet()) {
            BlockState s = level.getBlockState(e.getKey());
            TvBlock.WallPart col = wall ? TvBlock.WallPart.column(e.getValue()[0] - minX, width) : TvBlock.WallPart.SINGLE;
            TvBlock.WallPart row = wall ? TvBlock.WallPart.row(e.getValue()[1] - minY, height) : TvBlock.WallPart.SINGLE;
            if (s.getValue(TvBlock.WALL_COLUMN) != col || s.getValue(TvBlock.WALL_ROW) != row) {
                level.setBlock(e.getKey(), s.setValue(TvBlock.WALL_COLUMN, col).setValue(TvBlock.WALL_ROW, row), Block.UPDATE_ALL);
            }
        }

        TvBlockEntity controller = null;
        for (Map.Entry<BlockPos, int[]> e : members.entrySet()) {
            if (!(level.getBlockEntity(e.getKey()) instanceof TvBlockEntity tv)) continue;
            if (wall) {
                int x = e.getValue()[0] - minX;
                int y = e.getValue()[1] - minY;
                tv.setWall(x, y, width, height);
                if (x == 0 && y == 0) controller = tv;
            } else {
                tv.setWall(0, 0, 1, 1);
                tv.refreshPower();
            }
        }
        if (controller != null) controller.refreshPower();
        return true;
    }

    /** After a TV is removed, regroup what is left on each side of it. */
    public static void recomputeAround(Level level, BlockPos removed, Direction facing) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        Direction right = TvBlock.rightOf(facing);
        for (BlockPos n : new BlockPos[]{removed.relative(right), removed.relative(right.getOpposite()), removed.above(), removed.below()}) {
            if (level.isLoaded(n) && isWallTv(level.getBlockState(n), facing)) BigTvConverter.queueRegroup(server, n);
        }
    }

    /** Player-facing TV sizes: single, wide, large and large-wide. */
    public static boolean supportedSize(int width, int height) {
        return (width == 1 && height == 1) || (width == 2 && height == 1)
                || (width == 2 && height == 2) || (width == 3 && height == 2);
    }

    /** Position of wall member (x, y) for a wall whose controller is at controller. */
    public static BlockPos memberPos(BlockPos controller, Direction facing, int x, int y) {
        return controller.relative(TvBlock.rightOf(facing), x).above(y);
    }
}
