package com.billyke2000s.moviehubtv.block;

import com.billyke2000s.moviehubtv.ModRegistry;
import com.billyke2000s.moviehubtv.MovieHubTV;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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

    private static final Map<Pending, Long> QUEUE = new LinkedHashMap<>();
    /** TVs whose wall must be worked out again, once their block entities surely exist. */
    private static final Map<Pending, Long> REGROUP = new LinkedHashMap<>();

    /** True while parts are being swapped, so a big TV doesn't remove its other parts. */
    static boolean converting;

    static void queue(ServerLevel level, BlockPos controller) {
        QUEUE.putIfAbsent(new Pending(level, controller.immutable()), level.getGameTime());
    }

    /** Regroups the TV wall at pos at the end of this server tick. */
    static void queueRegroup(ServerLevel level, BlockPos pos) {
        REGROUP.putIfAbsent(new Pending(level, pos.immutable()), level.getGameTime());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        process(QUEUE, true);
        process(REGROUP, false);
    }

    private static void process(Map<Pending, Long> pending, boolean conversion) {
        for (Pending p : List.copyOf(pending.keySet())) {
            if (p.level().getGameTime() < pending.get(p)) continue;
            pending.remove(p);
            if (!p.level().isLoaded(p.pos())) continue;
            try {
                boolean complete = conversion ? convert(p.level(), p.pos()) : TvWall.recompute(p.level(), p.pos());
                if (!complete) pending.putIfAbsent(p, p.level().getGameTime() + 20);
            } catch (Exception e) {
                MovieHubTV.LOGGER.warn("Could not {} TV at {}", conversion ? "convert" : "regroup", p.pos(), e);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
        REGROUP.clear();
        converting = false;
    }

    static boolean convert(ServerLevel level, BlockPos controller) {
        if (!level.isLoaded(controller)) return false;
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof BigTvBlock big)) return true;
        if (state.getValue(BigTvBlock.PART_X) != 0 || state.getValue(BigTvBlock.PART_Y) != 0) return true;
        Direction facing = state.getValue(TvBlock.FACING);

        // Do not replace the controller until its complete legacy footprint is available.
        for (int px = 0; px < big.widthBlocks(); px++) {
            for (int py = 0; py < big.heightBlocks(); py++) {
                if (!level.isLoaded(BigTvBlock.partPos(controller, facing, px, py))) return false;
            }
        }
        if (!(level.getBlockEntity(controller) instanceof TvBlockEntity)) return false;

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
        return true;
    }
}
