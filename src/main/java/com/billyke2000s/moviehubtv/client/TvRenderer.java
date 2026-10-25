package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.block.TvBlock;
import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Draws the TV picture, the guide and the remote's cursor onto the TV's screen. */
public class TvRenderer implements BlockEntityRenderer<TvBlockEntity> {
    private static final int BLACK = 0xFF000000;
    private static final int PANEL = 0xD00A1630;
    private static final int GOLD = 0xFFFFC83D;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SUB = 0xFFA9BCDD;
    private static final long BANNER_MS = 5000;
    private static final float CURSOR_SIZE = 22f;
    /** Hotspot of the obskyra.stream hand cursor: fingertip at (14, 4) of 32. */
    private static final float HOTSPOT_X = 14f / 32f;
    private static final float HOTSPOT_Y = 4f / 32f;

    private final Font font;

    public TvRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(TvBlockEntity tv, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos pos = tv.getBlockPos();
        RemoteControl.Pointer pointer = RemoteControl.pointer();
        boolean pointed = pointer != null && pointer.pos().equals(pos);
        if (!tv.isOn() && !pointed) return;

        Direction facing = tv.getBlockState().getValue(TvBlock.FACING);
        pose.pushPose();
        ScreenGeometry.applyScreenTransform(pose, facing);
        Canvas c = new Canvas(pose, buffers, font);

        if (tv.isOn()) {
            ChannelRepository.ensureLoaded();
            ChannelRepository.Channel channel = tv.channelId().isEmpty() ? null : ChannelRepository.channel(tv.channelId());
            TvVideoManager.Session session = channel != null ? TvVideoManager.play(pos, channel) : null;
            if (tv.isGuideShowing()) {
                GuideRenderer.draw(c, pos, tv, pointed ? pointer : null);
            } else {
                drawPicture(c, tv, channel, session, pointed);
            }
        }

        if (pointed) drawCursor(c, pointer);
        pose.popPose();
    }

    private void drawPicture(Canvas c, TvBlockEntity tv, ChannelRepository.Channel channel,
                             TvVideoManager.Session session, boolean pointed) {
        float w = ScreenGeometry.WIDTH;
        float h = ScreenGeometry.HEIGHT;
        c.rect(0, 0, w, h, 0, BLACK);

        if (session != null && session.hasPicture()) {
            float vw = session.player.width();
            float vh = session.player.height();
            float scale = Math.min(w / vw, h / vh);
            float pw = vw * scale;
            float ph = vh * scale;
            c.image(session.texture, (w - pw) / 2f, (h - ph) / 2f, pw, ph, 0.5f, 0, 0, 1, 1, WHITE);
        } else {
            c.textCentered(statusText(channel, session), w / 2f, h / 2f - 4, 1, 0.8f, SUB);
        }

        boolean recentlyTuned = session != null && System.currentTimeMillis() - session.tunedAt < BANNER_MS;
        if (channel != null && (recentlyTuned || pointed)) drawBanner(c, channel, pointed);
    }

    private static String statusText(ChannelRepository.Channel channel, TvVideoManager.Session session) {
        if (channel == null) {
            return ChannelRepository.channels().isEmpty() ? "Tuning in..." : "Channel not available";
        }
        if (!TvVideoManager.videoAvailable()) return "Video needs VLC - see README";
        if (session != null && session.player != null && (session.player.isBroken() || session.player.isEnded())) {
            return "No signal";
        }
        return "Tuning in...";
    }

    private void drawBanner(Canvas c, ChannelRepository.Channel channel, boolean pointed) {
        float y = ScreenGeometry.HEIGHT - 30;
        c.rect(4, y, ScreenGeometry.WIDTH - 8, 26, 1, PANEL);
        c.text(String.valueOf(channel.number()), 8, y + 4, 1.5f, 0.8f, GOLD);
        c.text(c.fit(channel.name(), 140, 0.8f), 30, y + 4, 1.5f, 0.8f, WHITE);
        ChannelRepository.Programme[] nn = ChannelRepository.nowAndNext(channel.id(), System.currentTimeMillis());
        String now = nn[0] != null ? "Now: " + nn[0].title() : "Live";
        c.text(c.fit(now, ScreenGeometry.WIDTH - 20, 0.7f), 8, y + 15, 1.5f, 0.7f, SUB);
        if (pointed) c.textRight("Click: guide", ScreenGeometry.WIDTH - 8, y + 4.5f, 1.5f, 0.6f, SUB);
    }

    private static void drawCursor(Canvas c, RemoteControl.Pointer pointer) {
        float size = RemoteControl.recentlyPressed() ? CURSOR_SIZE * 0.85f : CURSOR_SIZE;
        float x = pointer.u() - HOTSPOT_X * size;
        float y = pointer.v() - HOTSPOT_Y * size;
        c.image(Canvas.CURSOR, x, y, size, size, 3f, 0, 0, 1, 1, WHITE);
    }

    @Override
    public boolean shouldRenderOffScreen(TvBlockEntity blockEntity) {
        return true; // the cursor can poke slightly past the screen edge
    }
}
