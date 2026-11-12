package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.ModRegistry;
import com.billyke2000s.moviehubtv.MovieHubTV;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.block.TvScreenLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
        int mediaRevision = -1;
        LocalMediaRelay relay;
        int starts;
        long retryAt, frameProbeAt;
        boolean uploadedFrame;
        /** The current source has shown a picture on this PC. */
        boolean sawPicture;
        /** On-demand fallback sources sent by the server (0 for live TV), and the one being tried. */
        int sourceCount, sourceIndex;
        String failure = "";

        Session(BlockPos pos) {
            this.pos = pos;
            this.texture = MovieHubTV.id("video/" + Long.toHexString(pos.asLong()));
        }

        boolean hasPicture() {
            if (player == null || player.width() <= 1 || player.height() <= 1) return false;
            long now = System.currentTimeMillis();
            if (now >= frameProbeAt) {
                uploadedFrame = VideoRenderState.hasFrame(player.texture());
                frameProbeAt = now + 250;
            }
            // Keep the last decoded image during buffering instead of turning the TV black.
            return uploadedFrame && !player.isBroken();
        }

        void releasePlayback() {
            if (relay != null) { relay.close(); relay = null; }
            if (player != null) {
                try {
                    player.release();
                } catch (Exception e) {
                    MovieHubTV.LOGGER.warn("Error releasing TV player", e);
                }
                player = null;
            }
            uploadedFrame = false;
            frameProbeAt = 0;
        }

        /** Decides what to try next after this PC could not play the current source. */
        void failedHere() {
            long now = System.currentTimeMillis();
            switch (VideoRecovery.afterFailure(sourceCount, sawPicture, starts, sourceIndex)) {
                case NEXT_SOURCE -> {
                    MovieHubTV.LOGGER.warn("TV source {} of {} did not play on this client; trying the next one", sourceIndex + 1, sourceCount);
                    sourceIndex++;
                    starts = 0;
                    sawPicture = false;
                    retryAt = now + 1000;
                    failure = "Source " + sourceIndex + " won't play on this PC - trying " + (sourceIndex + 1) + " of " + sourceCount + "...";
                }
                case RETRY_SAME -> {
                    retryAt = now + 3000;
                    failure = "Retrying video on this PC...";
                }
                case GIVE_UP -> {
                    retryAt = now + 3000;
                    if (sourceCount > 0) {
                        // Parked past the end; resumes by itself if the search sends more sources.
                        failure = "None of the " + sourceCount + " sources play on this PC - choose the title again";
                        sourceIndex = sourceCount;
                        starts = 0;
                    } else failure = "Video failed on this PC - choose another source";
                }
            }
        }

        void release() {
            releasePlayback();
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
        if (!channel.id().equals(s.channelId)) {
            s.releasePlayback();
            s.channelId = channel.id();
            s.starts = 0; s.retryAt = 0; s.failure = "";
            s.sourceCount = 0; s.sourceIndex = 0; s.sawPicture = false;
        }
        if (s.player == null) {
            if (!VideoRecovery.mayStart(s.starts, System.currentTimeMillis(), s.retryAt)) return s;
            if (!videoAvailable()) {
                if (!warnedNoVlc) {
                    MovieHubTV.LOGGER.error("WATERMeDIA could not load VLC, so the TV cannot play video");
                    warnedNoVlc = true;
                }
                s.channelId = channel.id();
                return s;
            }
            // Pluto links need a current session token; while it is being fetched, try again next frame.
            String url = channel.url();
            if (url.startsWith("moviehub-vod:")) {
                var level = Minecraft.getInstance().level;
                if (level == null || !(level.getBlockEntity(pos) instanceof TvBlockEntity tv) || tv.mediaSource().isEmpty()) return s;
                // The server may add sources while its search runs; this PC works down the list itself.
                java.util.List<com.google.gson.JsonObject> sources = MediaSources.parse(tv.mediaSource());
                s.sourceCount = sources.size();
                if (sources.isEmpty()) {
                    s.failure = "Unreadable source list - choose the title again";
                    return s;
                }
                if (s.sourceIndex >= sources.size()) return s; // every source failed here; wait for more
                try {
                    if (s.relay != null) s.relay.close();
                    s.relay = new LocalMediaRelay(sources.get(s.sourceIndex));
                    url = s.relay.url();
                } catch (Exception e) {
                    s.starts++;
                    MovieHubTV.LOGGER.warn("Could not start local MovieHub media transport ({})", e.getClass().getSimpleName());
                    s.failedHere();
                    return s;
                }
            } else if (s.relay != null) { s.relay.close(); s.relay = null; }
            if (url.startsWith("pluto-vod:")) {
                url = PlutoClient.streamUrl(url.substring("pluto-vod:".length()), true);
                if (url == null) return s;
            }
            if (PlutoClient.isPlutoId(channel.id())) {
                url = PlutoClient.streamUrl(channel.id());
                if (url == null) return s;
            }
            try {
            s.starts++;
            s.player = VideoRenderState.call(() -> new VideoPlayer(task ->
                    Minecraft.getInstance().execute(() -> VideoRenderState.run(task))));
            s.channelId = channel.id();
            s.tunedAt = System.currentTimeMillis();
            s.mediaRevision = -1;
            s.failure = "";
            final VideoPlayer player = s.player;
            if (!s.registered) {
                Minecraft.getInstance().getTextureManager().register(s.texture,
                        new VideoTexture(() -> s.player != null ? s.player.texture() : 0));
                s.registered = true;
            }
            player.setVolume(0);
            player.start(URI.create(url));
            } catch (Exception e) {
                s.releasePlayback();
                s.retryAt = System.currentTimeMillis() + 3000;
                s.failure = "Video initialisation failed on this PC - choose another source";
                MovieHubTV.LOGGER.warn("TV video initialisation failed on this client ({})", e.getClass().getSimpleName());
            }
        }
        return s;
    }

    static Session get(BlockPos pos) {
        return SESSIONS.get(pos);
    }

    /** Each client tick: stop players whose TV is gone, off, retuned or far away; set volume by distance and speakers. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            releaseAll();
            return;
        }
        int master = ClientConfig.get(ClientConfig.MASTER_VOLUME, 100);
        int range = ClientConfig.get(ClientConfig.HEARING_RANGE, 16);
        int speakerRange = ClientConfig.get(ClientConfig.SPEAKER_HEARING_RANGE, 32);
        int fullVolumeRange = ClientConfig.get(ClientConfig.SPEAKER_FULL_VOLUME_RANGE, 4);
        SpeakerAudio.tick(mc, speakerRange);
        Vec3 ear = mc.player.getEyePosition();
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            double dist = Math.sqrt(ear.distanceToSqr(Vec3.atCenterOf(s.pos)));
            TvBlockEntity tv = mc.level.isLoaded(s.pos) && mc.level.getBlockEntity(s.pos) instanceof TvBlockEntity t ? t : null;
            TvScreenLayout.Layout layout = tv == null ? null : TvScreenLayout.resolve(mc.level, s.pos);
            List<BlockPos> speakers = layout == null ? List.of() : speakers(mc, layout);
            double[] toSpeakers = new double[speakers.size()];
            for (int i = 0; i < toSpeakers.length; i++) {
                toSpeakers[i] = Math.sqrt(ear.distanceToSqr(Vec3.atCenterOf(speakers.get(i))));
            }
            boolean keep = SpeakerMix.keepPlaying(dist, speakerRange, toSpeakers) && tv != null && tv.isOn() && tv.channelId().equals(s.channelId)
                    && tv.isWallController() && layout != null;
            if (!keep) {
                s.release();
                it.remove();
                continue;
            }
            if (s.player != null) {
                boolean picture = s.hasPicture();
                if (picture) s.sawPicture = true;
                if (VideoRecovery.failed(System.currentTimeMillis() - s.tunedAt, s.player.isBroken(), s.player.isEnded(), s.sawPicture)) {
                    MovieHubTV.LOGGER.warn("TV video has no usable frame on this client; attempt {} of {} (state={}, size={}x{})",
                            s.starts, VideoRecovery.MAX_STARTS, s.player.getStateName(), s.player.width(), s.player.height());
                    s.releasePlayback();
                    s.failedHere();
                    continue;
                }
                // Apply controls once after VLC is ready; do not repeatedly seek through ad breaks.
                if (tv.isOnDemand() && s.mediaRevision != tv.mediaRevision()
                        && picture) {
                    long duration = s.player.getDuration();
                    long target = tv.mediaPosition();
                    if (duration > 0) target = Math.min(target, Math.max(0, duration - 1000));
                    if (s.player.isSeekAble()) s.player.seekTo(target);
                    s.player.setPauseMode(tv.mediaPaused());
                    s.mediaRevision = tv.mediaRevision();
                }
                int volume = SpeakerMix.volume(tv.volume(), master, range, speakerRange, fullVolumeRange, dist, toSpeakers);
                if (s.player.getVolume() != volume) s.player.setVolume(volume);
            }
        }
    }

    /** Loaded speakers linked to any TV block of this screen. */
    private static List<BlockPos> speakers(Minecraft mc, TvScreenLayout.Layout layout) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = 0; x < layout.width(); x++) {
            for (int y = 0; y < layout.height(); y++) {
                BlockPos member = com.billyke2000s.moviehubtv.block.TvWall.memberPos(layout.controller(), layout.facing(), x, y);
                if (!(mc.level.getBlockEntity(member) instanceof TvBlockEntity tv)) continue;
                for (BlockPos p : tv.speakers()) {
                    if (mc.level.isLoaded(p) && mc.level.getBlockState(p).is(ModRegistry.SPEAKER.get())
                            && mc.level.getBlockEntity(p) instanceof com.billyke2000s.moviehubtv.block.SpeakerBlockEntity speaker
                            && member.equals(speaker.tv())) found.add(p);
                }
            }
        }
        return found;
    }

    static void releaseAll() {
        for (Session s : SESSIONS.values()) s.release();
        SESSIONS.clear();
    }
}

