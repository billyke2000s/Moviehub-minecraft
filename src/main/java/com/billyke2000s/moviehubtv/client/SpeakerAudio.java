package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.block.SpeakerBlockEntity;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.block.TvScreenLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Loaded speaker registry: sound can start without the TV ever entering the renderer's view. */
final class SpeakerAudio {
    private SpeakerAudio() {}
    private static final Map<BlockPos, SpeakerBlockEntity> LOADED = new HashMap<>();

    static void loaded(SpeakerBlockEntity speaker) {
        LOADED.put(speaker.getBlockPos().immutable(), speaker);
    }

    static void tick(Minecraft mc, int range) {
        if (mc.level == null || mc.player == null) { clear(); return; }
        Vec3 ear = mc.player.getEyePosition();
        Set<BlockPos> started = new HashSet<>();
        Iterator<Map.Entry<BlockPos, SpeakerBlockEntity>> it = LOADED.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            BlockPos pos = entry.getKey();
            SpeakerBlockEntity speaker = entry.getValue();
            if (speaker.getLevel() != mc.level || !mc.level.isLoaded(pos) || mc.level.getBlockEntity(pos) != speaker) {
                it.remove(); continue;
            }
            if (ear.distanceToSqr(Vec3.atCenterOf(pos)) >= (double) range * range) continue;
            BlockPos target = speaker.tv();
            if (target == null || !mc.level.isLoaded(target)) continue;
            TvScreenLayout.Layout layout = TvScreenLayout.resolve(mc.level, target);
            if (layout == null || started.contains(layout.controller())) continue;
            if (!(mc.level.getBlockEntity(layout.controller()) instanceof TvBlockEntity tv) || !tv.isOn()) continue;
            // A stale or replaced speaker must not start a TV it is no longer registered with.
            if (!(mc.level.getBlockEntity(target) instanceof TvBlockEntity linked) || !linked.speakers().contains(pos)) continue;
            ChannelRepository.ensureLoaded();
            ChannelRepository.Channel channel = ChannelRepository.channel(tv.channelId());
            if (channel != null) {
                started.add(layout.controller());
                TvVideoManager.play(layout.controller(), channel);
            }
        }
    }

    static void clear() { LOADED.clear(); }
}
