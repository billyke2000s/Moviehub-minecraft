package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import com.billyke2000s.moviehubtv.MovieHubTV;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Turns the old crafted big TVs (wide, large, large wide) into normal TVs joined as a TV wall.
 *
 * A big TV's controller queues itself here whenever it is loaded or placed; at the end of the server
 * tick every part is swapped for a normal TV facing the same way, so the group becomes a TV wall of
 * the same size. Power, channel and guide carry over. Nothing drops and nothing is lost: a 2x1 wide TV
 * becomes two TVs, a 2x2 large TV four, a 3x2 large wide TV six.
 */
public final class BigTvConverter {
    private BigTvConverter() {}

    private record Pending(ServerLevel level, BlockPos pos) {}

    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();
    /** TVs whose wall must be worked out again, once their block entities surely exist. */
    private static final ConcurrentLinkedQueue<Pending> REGROUP = new ConcurrentLinkedQueue<>();

    /** True while parts are being swapped, so a big TV doesn't remove its other parts. */
    static boolean converting;

    static void queue(ServerLevel level, BlockPos controller) {
        QUEUE.add(new Pending(level, controller.immutable()));
    }

    /** Regroups the TV wall at pos at the end of this server tick. */
    static void queueRegroup(ServerLevel level, BlockPos pos) {
        REGROUP.add(new Pending(level, pos.immutable()));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Pending p;
        while ((p = QUEUE.poll()) != null) {
            try {
                convert(p.level(), p.pos());
            } catch (Exception e) {
                MovieHubTV.LOGGER.warn("Could not convert big TV at {}", p.pos(), e);
            }
        }
        java.util.Set<Pending> done = new java.util.HashSet<>();
        while ((p = REGROUP.poll()) != null) {
            if (!done.add(p) || !p.level().isLoaded(p.pos())) continue;
            try {
                TvWall.recompute(p.level(), p.pos());
            } catch (Exception e) {
                MovieHubTV.LOGGER.warn("Could not regroup TVs at {}", p.pos(), e);
            }
        }
    }

    static void convert(ServerLevel level, BlockPos controller) {
        if (!level.isLoaded(controller)) return;
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof BigTvBlock big)) return;
        if (state.getValue(BigTvBlock.PART_X) != 0 || state.getValue(BigTvBlock.PART_Y) != 0) return;
        Direction facing = state.getValue(TvBlock.FACING);

        boolean on = false, guide = true;
        String channel = "";
        if (level.getBlockEntity(controller) instanceof TvBlockEntity old) {
            on = old.isOn();
            channel = old.channelId();
            guide = old.guideOpenFlag();
        }

        BlockState tv = ModRegistry.TV.get().defaultBlockState()
                .setValue(TvBlock.FACING, facing).setValue(TvBlock.POWERED, on);
        converting = true;
        try {
            for (int px = 0; px < big.widthBlocks(); px++) {
                for (int py = 0; py < big.heightBlocks(); py++) {
                    BlockPos p = BigTvBlock.partPos(controller, facing, px, py);
                    if (!level.isLoaded(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.is(big) && s.getValue(TvBlock.FACING) == facing
                            && s.getValue(BigTvBlock.PART_X) == px && s.getValue(BigTvBlock.PART_Y) == py) {
                        level.setBlock(p, tv, Block.UPDATE_ALL);
                    }
                }
            }
        } finally {
            converting = false;
        }

        if (level.getBlockEntity(controller) instanceof TvBlockEntity fresh) {
            fresh.restore(on, channel, guide);
        }
        queueRegroup(level, controller);
        MovieHubTV.LOGGER.info("Turned a {}x{} big TV at {} into normal TVs", big.widthBlocks(), big.heightBlocks(), controller);
    }
}
