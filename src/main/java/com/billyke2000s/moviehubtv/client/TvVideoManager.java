package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** One WATERMeDIA player per TV that is on and tuned. Players are created on the render thread. */
final class TvVideoManager {
    private TvVideoManager() {}

    static final class Session {
        final BlockPos pos;
        final ResourceLocation texture;
        VideoPlayer player;
        String channelId = "";
        long tunedAt;
        boolean registered;

        Session(BlockPos pos) {
            this.pos = pos;
            this.texture = MovieHubTV.id("video/" + Long.toHexString(pos.asLong()));
        }

        boolean hasPicture() {
            return player != null && player.texture() > 0 && player.width() > 0 && player.height() > 0
                    && (player.isPlaying() || player.isPaused());
        }

        void release() {
            if (player != null) {
                try {
                    player.release();
                } catch (Exception e) {
                    MovieHubTV.LOGGER.warn("Error releasing TV player", e);
                }
                player = null;
            }
            if (registered) {
                Minecraft.getInstance().getTextureManager().release(texture);
                registered = false;
            }
        }
    }

    private static final Map<BlockPos, Session> SESSIONS = new HashMap<>();
    private static boolean warnedNoVlc;

    static boolean videoAvailable() {
        try {
            return PlayerAPI.isReady();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called while rendering a TV that should be playing channelId. Returns null if video is unavailable. */
    static Session play(BlockPos pos, ChannelRepository.Channel channel) {
        Session s = SESSIONS.computeIfAbsent(pos.immutable(), Session::new);
        if (!channel.id().equals(s.channelId) || s.player == null) {
            if (!videoAvailable()) {
                if (!warnedNoVlc) {
                    MovieHubTV.LOGGER.error("WATERMeDIA could not load VLC, so the TV cannot play video");
                    warnedNoVlc = true;
                }
                s.channelId = channel.id();
                return s;
            }
            if (s.player != null) s.player.release();
            s.player = new VideoPlayer(Minecraft.getInstance());
            s.channelId = channel.id();
            s.tunedAt = System.currentTimeMillis();
            final VideoPlayer player = s.player;
            if (!s.registered) {
                Minecraft.getInstance().getTextureManager().register(s.texture,
                        new VideoTexture(() -> s.player != null ? s.player.texture() : 0));
                s.registered = true;
            }
            player.setVolume(0);
            player.start(URI.create(channel.url()));
        }
        return s;
    }

    static Session get(BlockPos pos) {
        return SESSIONS.get(pos);
    }

    /** Each client tick: stop players whose TV is gone, off, retuned or far away; set volume by distance. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            releaseAll();
            return;
        }
        int maxVolume = ClientConfig.get(ClientConfig.VOLUME, 80);
        int range = ClientConfig.get(ClientConfig.HEARING_RANGE, 16);
        Vec3 ear = mc.player.getEyePosition();
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            double dist = Math.sqrt(ear.distanceToSqr(Vec3.atCenterOf(s.pos)));
            boolean keep = dist < 64
                    && mc.level.isLoaded(s.pos)
                    && mc.level.getBlockEntity(s.pos) instanceof TvBlockEntity tv
                    && tv.isOn() && tv.channelId().equals(s.channelId);
            if (!keep) {
                s.release();
                it.remove();
                continue;
            }
            if (s.player != null) {
                int volume = (int) Math.round(maxVolume * Math.max(0, 1 - dist / range));
                if (s.player.getVolume() != volume) s.player.setVolume(volume);
            }
        }
    }

    static void releaseAll() {
        for (Session s : SESSIONS.values()) s.release();
        SESSIONS.clear();
    }
}
