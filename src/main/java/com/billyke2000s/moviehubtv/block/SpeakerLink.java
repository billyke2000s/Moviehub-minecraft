package com.billyke2000s.moviehubtv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** Links a speaker to the nearest TV block in loaded chunks. Server side only. */
public final class SpeakerLink {
    private SpeakerLink() {}

    /** How far a speaker looks for a TV when placed or right-clicked. */
    public static final int RADIUS = 32;

    /** Links the speaker to the nearest TV and returns that TV's position, or null if there is none in range. */
    public static @Nullable BlockPos link(ServerLevel level, BlockPos speaker) {
        if (!(level.getBlockEntity(speaker) instanceof SpeakerBlockEntity self)) return null;
        BlockPos nearest = nearestTv(level, speaker);
        BlockPos old = self.tv();
        if (old != null && !old.equals(nearest) && level.isLoaded(old)
                && level.getBlockEntity(old) instanceof TvBlockEntity oldTv) {
            oldTv.removeSpeaker(speaker);
        }
        self.setTv(nearest);
        if (nearest != null && level.getBlockEntity(nearest) instanceof TvBlockEntity tv) tv.addSpeaker(speaker);
        return nearest;
    }

    public static void unlink(ServerLevel level, BlockPos speaker) {
        if (!(level.getBlockEntity(speaker) instanceof SpeakerBlockEntity self)) return;
        BlockPos tvPos = self.tv();
        if (tvPos != null && level.isLoaded(tvPos) && level.getBlockEntity(tvPos) instanceof TvBlockEntity tv) {
            tv.removeSpeaker(speaker);
        }
    }

    private static @Nullable BlockPos nearestTv(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bestDist = (double) RADIUS * RADIUS;
        int minX = SectionPos.blockToSectionCoord(from.getX() - RADIUS), maxX = SectionPos.blockToSectionCoord(from.getX() + RADIUS);
        int minZ = SectionPos.blockToSectionCoord(from.getZ() - RADIUS), maxZ = SectionPos.blockToSectionCoord(from.getZ() + RADIUS);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof TvBlockEntity)) continue;
                    double d = be.getBlockPos().distSqr(from);
                    if (d <= bestDist) {
                        bestDist = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        return best == null ? null : best.immutable();
    }
}
