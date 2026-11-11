package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Joins normal TVs placed next to each other into one big screen ("TV wall").
 *
 * TVs that face the same way and touch side by side or above/below form a group. If the group fills
 * a whole rectangle (at most {@link #MAX_WIDTH} x {@link #MAX_HEIGHT}), every TV in it becomes part of
 * one screen: the bottom-left TV (as the viewer sees it) is the controller, whose power and channel
 * the whole wall uses. Any other shape stays as separate TVs. Worked out on the server whenever a TV
 * is placed or removed, saved in each TV's block entity and synced to clients.
 */
public final class TvWall {
    private TvWall() {}

    public static final int MAX_WIDTH = 12;
    public static final int MAX_HEIGHT = 8;
    private static final int MAX_MEMBERS = MAX_WIDTH * MAX_HEIGHT;

    private static boolean isWallTv(BlockState state, Direction facing) {
        return state.is(ModRegistry.TV.get()) && state.getValue(TvBlock.FACING) == facing;
    }

    /** Recomputes the group containing start (if start is a normal TV). Server side only. */
    public static void recompute(Level level, BlockPos start) {
        if (level.isClientSide) return;
        BlockState first = level.getBlockState(start);
        if (!first.is(ModRegistry.TV.get())) return;
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
                if (members.containsKey(n) || !level.isLoaded(n) || !isWallTv(level.getBlockState(n), facing)) continue;
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
                && width <= MAX_WIDTH && height <= MAX_HEIGHT;

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
    }

    /** After a TV is removed, regroup what is left on each side of it. */
    public static void recomputeAround(Level level, BlockPos removed, Direction facing) {
        Direction right = TvBlock.rightOf(facing);
        for (BlockPos n : new BlockPos[]{removed.relative(right), removed.relative(right.getOpposite()), removed.above(), removed.below()}) {
            if (isWallTv(level.getBlockState(n), facing)) recompute(level, n);
        }
    }

    /** Position of wall member (x, y) for a wall whose controller is at controller. */
    public static BlockPos memberPos(BlockPos controller, Direction facing, int x, int y) {
        return controller.relative(TvBlock.rightOf(facing), x).above(y);
    }
}
