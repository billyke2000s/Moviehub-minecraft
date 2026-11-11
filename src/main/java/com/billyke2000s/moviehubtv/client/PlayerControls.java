package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.block.TvBlockEntity;

/** The controls sit inside the existing blue video information popup. Shared draw/hit geometry. */
final class PlayerControls {
    private PlayerControls() {}
    static int hit(float w, float h, float u, float v) {
        if (v < h - 18 || v >= h - 5 || u < 5 || u >= w - 5) return TvBlockEntity.ACTION_OPEN_GUIDE;
        int i = Math.min(3, (int) ((u - 5) / ((w - 10) / 4)));
        return switch (i) {
            case 0 -> TvBlockEntity.ACTION_SEEK_BACK;
            case 1 -> TvBlockEntity.ACTION_PAUSE;
            case 2 -> TvBlockEntity.ACTION_SEEK_FORWARD;
            default -> TvBlockEntity.ACTION_OPEN_GUIDE;
        };
    }
    static void draw(Canvas c, ScreenGeometry.Screen screen, TvBlockEntity tv, ChannelRepository.Channel channel,
                     TvVideoManager.Session session, RemoteControl.Pointer pointer) {
        float w = screen.width(), h = screen.height(), y = h - 48;
        c.rect(4, y, w - 8, 44, 1, 0xD00A1630);
        c.text(c.fit(channel.name(), w - 16, 0.8f), 8, y + 4, 1.5f, 0.8f, 0xFFFFFFFF);
        long duration = session != null && session.player != null ? session.player.getDuration() : 0;
        long time = session != null && session.player != null ? session.player.getTime() : tv.mediaPosition();
        String progress = time(time) + (duration > 0 ? " / " + time(duration) : "") + (tv.mediaPaused() ? "  Paused" : "  Pluto TV");
        c.text(c.fit(progress, w - 16, 0.65f), 8, y + 16, 1.5f, 0.65f, 0xFFA9BCDD);
        String[] labels = {"-10s", tv.mediaPaused() ? "Play" : "Pause", "+10s", "Guide"};
        float bw = (w - 10) / 4;
        for (int i = 0; i < labels.length; i++) {
            float x = 5 + i * bw;
            boolean over = pointer != null && pointer.u() >= x && pointer.u() < x + bw
                    && pointer.v() >= h - 18 && pointer.v() < h - 5;
            c.panel(x, h - 18, bw - 1, 13, 1.5f, over ? 0xFF3366C2 : 0xFF1F4385, 0x40FFFFFF, 0x60000000);
            c.textCentered(labels[i], x + (bw - 1) / 2, h - 14, 1.7f, 0.65f, over ? 0xFFFFC83D : 0xFFFFFFFF);
        }
    }
    private static String time(long ms) { long sec = Math.max(0, ms / 1000); return String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60); }
}
