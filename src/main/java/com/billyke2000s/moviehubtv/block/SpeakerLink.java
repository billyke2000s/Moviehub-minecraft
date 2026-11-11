package com.billyke2000s.moviehubtv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Links a speaker to the TV it was tuned to. Untuned speakers stay unlinked and silent. Server side only. */
public final class SpeakerLink {
    private SpeakerLink() {}

    /** A speaker tuned to a TV (right-click the TV with it) can be placed this far from it. */
    public static final int MAX_TUNED_DISTANCE = 48;

    /**
     * Links the speaker to the TV block at {@code tv} if it is still a TV within MAX_TUNED_DISTANCE;
     * otherwise returns null and leaves the speaker unlinked.
     */
    public static @Nullable BlockPos linkTuned(ServerLevel level, BlockPos speaker, BlockPos tv) {
        boolean ok = tv.distSqr(speaker) <= (double) MAX_TUNED_DISTANCE * MAX_TUNED_DISTANCE
                && level.isLoaded(tv) && level.getBlockEntity(tv) instanceof TvBlockEntity;
        return linkTo(level, speaker, ok ? tv : null);
    }

    private static @Nullable BlockPos linkTo(ServerLevel level, BlockPos speaker, @Nullable BlockPos target) {
        if (!(level.getBlockEntity(speaker) instanceof SpeakerBlockEntity self)) return null;
        BlockPos old = self.tv();
        if (old != null && !old.equals(target) && level.isLoaded(old)
                && level.getBlockEntity(old) instanceof TvBlockEntity oldTv) {
            oldTv.removeSpeaker(speaker);
        }
        self.setTv(target);
        if (target != null && level.getBlockEntity(target) instanceof TvBlockEntity tv) tv.addSpeaker(speaker);
        return target;
    }

    public static void unlink(ServerLevel level, BlockPos speaker) {
        if (!(level.getBlockEntity(speaker) instanceof SpeakerBlockEntity self)) return;
        BlockPos tvPos = self.tv();
        if (tvPos != null && level.isLoaded(tvPos) && level.getBlockEntity(tvPos) instanceof TvBlockEntity tv) {
            tv.removeSpeaker(speaker);
        }
    }
}

