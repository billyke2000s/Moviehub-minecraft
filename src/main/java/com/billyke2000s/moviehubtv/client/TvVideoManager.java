package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.billyke2000s.moviehubtv.client.provider.ProviderEngine;
import com.billyke2000s.moviehubtv.client.provider.PlaybackProxy;
import com.billyke2000s.moviehubtv.client.provider.SafeNetwork;
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
        String locator = "";
        String error = "";
        long retryAt;
        java.util.concurrent.Future<ProviderEngine.Stream> pending;
        PlaybackProxy proxy;

        Session(BlockPos pos) {
            this.pos = pos;
            this.texture = MovieHubTV.id("video/" + Long.toHexString(pos.asLong()));
        }

        boolean hasPicture() {
            return player != null && player.texture() > 0 && player.width() > 0 && player.height() > 0
                    && (player.isPlaying() || player.isPaused());
        }

        void release() {
            if (pending != null) { pending.cancel(true); pending = null; }
            if (proxy != null) { proxy.close(); proxy = null; }
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
    private static final java.util.concurrent.ThreadPoolExecutor RESOLVERS = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16), r -> {
                Thread thread = new Thread(r, "MovieHubTV-Resolve");
                thread.setDaemon(true);
                return thread;
            }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    static boolean videoAvailable() {
        try {
            return PlayerAPI.isReady();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called while rendering a TV that should be playing channelId. Returns null if video is unavailable. */
    static Session play(BlockPos pos, ChannelData.Channel channel) {
        Session s = SESSIONS.get(pos);
        if (s == null) {
            if (SESSIONS.size() >= 4) return null;
            s = new Session(pos.immutable());
            SESSIONS.put(pos.immutable(), s);
        }
        if (!channel.id().equals(s.channelId) || !channel.url().equals(s.locator)) {
            s.release();
            s.channelId = channel.id();
            s.locator = channel.url();
            s.error = "";
            s.retryAt = 0;
        }
        if (s.player == null && s.pending == null && System.currentTimeMillis() >= s.retryAt) {
            if (!videoAvailable()) {
                if (!warnedNoVlc) {
                    MovieHubTV.LOGGER.error("WATERMeDIA could not load VLC, so the TV cannot play video");
                    warnedNoVlc = true;
                }
                return s;
            }
            s.error = "";
            s.tunedAt = System.currentTimeMillis();
            try {
                s.pending = RESOLVERS.submit(() -> channel.url().startsWith("moviehub:")
                        ? ProviderEngine.resolveLocator(channel.url())
                        : new ProviderEngine.Stream(SafeNetwork.url(channel.url()), Map.of()));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                failed(s, "Player busy - retrying");
            }
        }
        if (s.pending != null && s.pending.isDone()) {
            try {
                ProviderEngine.Stream stream = s.pending.get();
                s.pending = null;
                s.proxy = new PlaybackProxy(stream);
                s.player = new VideoPlayer(Minecraft.getInstance());
                Session session = s;
                Minecraft.getInstance().getTextureManager().register(s.texture,
                        new VideoTexture(() -> session.player != null ? session.player.texture() : 0));
                s.registered = true;
                s.player.setVolume(0);
                s.tunedAt = System.currentTimeMillis();
                s.player.start(s.proxy.uri());
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
                String message = cause == null ? "" : cause.getMessage();
                failed(s, "DRM playback is not supported".equals(message)
                        ? "DRM is not supported" : "No signal - retrying");
                MovieHubTV.LOGGER.warn("TV playback failed ({})", e.getClass().getSimpleName());
            }
        }
        return s;
    }

    private static void failed(Session session, String message) {
        session.release();
        session.error = message;
        session.retryAt = System.currentTimeMillis() + 30000;
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
            if (s.player != null && (s.player.isBroken() || s.player.isEnded()
                    || !s.hasPicture() && System.currentTimeMillis() - s.tunedAt > 45000)) {
                failed(s, "No signal - retrying");
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
