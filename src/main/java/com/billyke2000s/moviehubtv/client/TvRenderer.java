package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import com.billyke2000s.moviehubtv.block.TvScreenLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

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
        TvScreenLayout.Layout layout = TvScreenLayout.resolve(tv.getLevel(), pos);
        if (layout == null) {
            // Never project an unverified saved rectangle into empty space. Ask the server to regroup.
            RemoteControl.requestLayoutRepair(pos);
            return;
        }
        if (!layout.controller().equals(pos)) return;
        RemoteControl.Pointer pointer = RemoteControl.pointer();
        boolean pointed = pointer != null && pointer.pos().equals(pos);
        if (!tv.isOn() && !pointed) return;

        Direction facing = layout.facing();
        ScreenGeometry.Screen screen = ScreenGeometry.screen(layout.width(), layout.height());
        pose.pushPose();
        ScreenGeometry.applyScreenTransform(pose, facing, screen);
        Canvas c = new Canvas(pose, buffers, font);

        if (tv.isOn()) {
            ChannelRepository.ensureLoaded();
            ChannelRepository.Channel channel = tv.channelId().isEmpty() ? null : ChannelRepository.channel(tv.channelId());
            TvVideoManager.Session session = channel != null ? TvVideoManager.play(pos, channel) : null;
            if (tv.isGuideShowing()) {
                drawGuide(c, pos, tv, screen, pointed ? pointer : null);
            } else {
                drawPicture(c, screen, tv, channel, session, pointed ? pointer : null);
            }
        }

        if (pointed) drawCursor(c, pointer);
        pose.popPose();
    }

    /** The guide has a fixed layout; on bigger screens it is scaled up and centred. */
    private static void drawGuide(Canvas c, BlockPos pos, TvBlockEntity tv, ScreenGeometry.Screen screen,
                                  RemoteControl.Pointer pointer) {
        if (screen.guideX() > 0.01f || screen.guideY() > 0.01f) {
            c.rect(0, 0, screen.width(), screen.height(), 0, BLACK);
        }
        RemoteControl.Pointer guidePointer = pointer == null ? null
                : new RemoteControl.Pointer(pointer.pos(), screen.toGuideU(pointer.u()), screen.toGuideV(pointer.v()));
        c.pose.pushPose();
        c.pose.translate(screen.guideX(), screen.guideY(), 0);
        c.pose.scale(screen.guideScale(), screen.guideScale(), 1f);
        GuideRenderer.draw(c, pos, tv, new GuideLayout(screen.guideWidth()), guidePointer);
        c.pose.popPose();
    }

    private void drawPicture(Canvas c, ScreenGeometry.Screen screen, TvBlockEntity tv, ChannelRepository.Channel channel,
                             TvVideoManager.Session session, RemoteControl.Pointer pointer) {
        boolean pointed = pointer != null;
        float w = screen.width();
        float h = screen.height();
        c.rect(0, 0, w, h, 0, BLACK);

        if (session != null && session.hasPicture()) {
            float vw = session.player.width();
            float vh = session.player.height();
            float scale = Math.min(w / vw, h / vh);
            float pw = vw * scale;
            float ph = vh * scale;
            c.image(session.texture, (w - pw) / 2f, (h - ph) / 2f, pw, ph, 0.5f, 0, 0, 1, 1, WHITE);
        } else {
            String status = channel != null && channel.url().startsWith("moviehub-vod:") && tv.mediaSource().isEmpty()
                    ? "Source expired - choose the title again" : statusText(channel, session);
            c.textCentered(c.fit(status, w - 10, 0.8f), w / 2f, h / 2f - 4, 1, 0.8f, SUB);
        }

        boolean recentlyTuned = session != null && System.currentTimeMillis() - session.tunedAt < BANNER_MS;
        if (channel != null && (recentlyTuned || pointed)) {
            if (tv.isOnDemand()) PlayerControls.draw(c, screen, tv, channel, session, pointer);
            else drawBanner(c, screen, channel, pointed);
        }
    }

    private static String statusText(ChannelRepository.Channel channel, TvVideoManager.Session session) {
        if (session != null && !session.failure.isEmpty()) return session.failure;
        if (channel == null) {
            return ChannelRepository.channels().isEmpty() ? "Tuning in..." : "Channel not available";
        }
        if (!TvVideoManager.videoAvailable()) return "Video needs VLC - see README";
        if (PlutoClient.isPlutoId(channel.id()) && PlutoClient.recentlyFailed()) return "Pluto TV unavailable - retrying";
        if (session != null && session.player != null && (session.player.isBroken() || session.player.isEnded())) {
            return "No signal";
        }
        if (session != null && session.sourceCount > 1 && session.sourceIndex < session.sourceCount) {
            return "Tuning in - source " + (session.sourceIndex + 1) + " of " + session.sourceCount + "...";
        }
        return "Tuning in...";
    }

    private void drawBanner(Canvas c, ScreenGeometry.Screen screen, ChannelRepository.Channel channel, boolean pointed) {
        float w = screen.width();
        float y = screen.height() - 30;
        c.rect(4, y, w - 8, 26, 1, PANEL);
        c.text(String.valueOf(channel.number()), 8, y + 4, 1.5f, 0.8f, GOLD);
        c.text(c.fit(channel.name(), 140, 0.8f), 30, y + 4, 1.5f, 0.8f, WHITE);
        ChannelRepository.Programme[] nn = ChannelRepository.nowAndNext(channel.id(), System.currentTimeMillis());
        String now = nn[0] != null ? "Now: " + nn[0].title() : "Live";
        c.text(c.fit(now, w - 20, 0.7f), 8, y + 15, 1.5f, 0.7f, SUB);
        if (pointed) c.textRight("Click: guide", w - 8, y + 4.5f, 1.5f, 0.6f, SUB);
    }

    private static void drawCursor(Canvas c, RemoteControl.Pointer pointer) {
        float size = RemoteControl.recentlyPressed() ? CURSOR_SIZE * 0.85f : CURSOR_SIZE;
        float x = pointer.u() - HOTSPOT_X * size;
        float y = pointer.v() - HOTSPOT_Y * size;
        c.image(Canvas.CURSOR, x, y, size, size, 3f, 0, 0, 1, 1, WHITE);
    }

    /** Big TVs draw well beyond the controller block, so widen the culling box to cover them. */
    @Override
    public AABB getRenderBoundingBox(TvBlockEntity blockEntity) {
        int size = Math.max(3, Math.max(blockEntity.wallWidth(), blockEntity.wallHeight()));
        return new AABB(blockEntity.getBlockPos()).inflate(size + 0.5);
    }

    @Override
    public boolean shouldRenderOffScreen(TvBlockEntity blockEntity) {
        return true; // the cursor can poke slightly past the screen edge
    }
}

