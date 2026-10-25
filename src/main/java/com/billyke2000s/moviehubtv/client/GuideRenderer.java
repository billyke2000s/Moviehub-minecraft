package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.block.TvBlockEntity;
import net.minecraft.core.BlockPos;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Sky-style TV guide, drawn in a blocky Minecraft look on the TV canvas. */
final class GuideRenderer {
    private GuideRenderer() {}

    // Layout (canvas is 192 x 160)
    static final int HEADER_H = 16;
    static final int ROW_Y0 = 18;
    static final int ROW_H = 17;
    static final int ROW_STEP = 18;
    static final int ROWS = 7;
    static final int FOOTER_Y = 145;
    static final int CH_X = 2;
    static final int CH_W = 60;
    static final int PROG_X = 64;
    static final int PROG_W = 126;

    // Colours (ARGB)
    private static final int BG = 0xFF0A1630;
    private static final int BAR = 0xFF173A75;
    private static final int BAR_LIGHT = 0xFF2B5BA8;
    private static final int BAR_DARK = 0xFF0C2148;
    private static final int CH = 0xFF1F4385;
    private static final int CH_HOVER = 0xFF3366C2;
    private static final int PROG = 0xFF132B57;
    private static final int PROG_HOVER = 0xFF234A8F;
    private static final int EDGE_LIGHT = 0x40FFFFFF;
    private static final int EDGE_DARK = 0x60000000;
    private static final int GOLD = 0xFFFFC83D;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SUB = 0xFFA9BCDD;
    private static final int GREEN = 0xFF4CD964;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    enum Target { NONE, ROW, UP, DOWN, BACK, SOURCE }

    record Hit(Target target, int channelIndex, String channelId) {
        Hit(Target target, int channelIndex) { this(target, channelIndex, ""); }
        static final Hit NONE = new Hit(Target.NONE, -1);
    }

    private static final Map<BlockPos, Integer> SCROLL = new HashMap<>();

    private static String source = "";
    private static List<ChannelRepository.Channel> cachedAll = List.of();
    private static List<ChannelRepository.Channel> visible = List.of();
    private static String cachedSource = "";

    static void clear() { SCROLL.clear(); source = ""; cachedAll = List.of(); visible = List.of(); }

    static List<ChannelRepository.Channel> channels() {
        List<ChannelRepository.Channel> all = ChannelRepository.channels();
        if (all != cachedAll || !source.equals(cachedSource)) {
            cachedAll = all;
            cachedSource = source;
            visible = source.isEmpty() ? all : all.stream().filter(channel -> source.equals(channel.source())).toList();
        }
        return visible;
    }

    static void nextSource() {
        List<String> sources = new java.util.ArrayList<>();
        sources.add("");
        for (ChannelRepository.Channel channel : ChannelRepository.channels()) {
            if (!sources.contains(channel.source())) sources.add(channel.source());
        }
        source = sources.get((sources.indexOf(source) + 1) % sources.size());
        SCROLL.clear();
    }

    static int scroll(BlockPos pos, TvBlockEntity tv) {
        List<ChannelRepository.Channel> channels = channels();
        int max = Math.max(0, channels.size() - ROWS);
        Integer stored = SCROLL.get(pos);
        if (stored == null) {
            // First time the guide is seen: start on the page with the current channel.
            int start = 0;
            ChannelRepository.Channel current = ChannelRepository.channel(tv.channelId());
            if (current != null) start = channels.indexOf(current);
            stored = start;
            if (!channels.isEmpty()) SCROLL.put(pos.immutable(), stored);
        }
        return Math.max(0, Math.min(max, stored));
    }

    static void scrollBy(BlockPos pos, TvBlockEntity tv, int rows) {
        int max = Math.max(0, channels().size() - ROWS);
        SCROLL.put(pos.immutable(), Math.max(0, Math.min(max, scroll(pos, tv) + rows)));
    }

    static Hit hit(BlockPos pos, TvBlockEntity tv, float u, float v) {
        if (v >= 0 && v < HEADER_H && u >= 0 && u < 140) return new Hit(Target.SOURCE, -1);
        if (v >= FOOTER_Y) {
            if (u >= 3 && u < 21) return new Hit(Target.UP, -1);
            if (u >= 23 && u < 41) return new Hit(Target.DOWN, -1);
            if (u >= 146 && u < 190 && !tv.channelId().isEmpty()) return new Hit(Target.BACK, -1);
            return Hit.NONE;
        }
        if (v < ROW_Y0 || u < CH_X || u >= PROG_X + PROG_W) return Hit.NONE;
        int row = (int) ((v - ROW_Y0) / ROW_STEP);
        if (row >= ROWS || (v - ROW_Y0) - row * ROW_STEP >= ROW_H) return Hit.NONE;
        List<ChannelRepository.Channel> channels = channels();
        int index = scroll(pos, tv) + row;
        if (index >= channels.size()) return Hit.NONE;
        return new Hit(Target.ROW, index, channels.get(index).id());
    }

    static void draw(Canvas c, BlockPos pos, TvBlockEntity tv, RemoteControl.Pointer pointer) {
        c.rect(0, 0, ScreenGeometry.WIDTH, ScreenGeometry.HEIGHT, 0, BG);

        // Header
        c.panel(0, 0, ScreenGeometry.WIDTH, HEADER_H, 0.5f, BAR, BAR_LIGHT, BAR_DARK);
        c.text(c.fit(source.isEmpty() ? "All sources >" : source + " >", 132, 0.7f), 5, 5, 1, 0.7f, WHITE);
        c.textRight(LocalTime.now().format(CLOCK), ScreenGeometry.WIDTH - 5, 5, 1, 0.8f, GOLD);

        List<ChannelRepository.Channel> channels = channels();
        if (channels.isEmpty()) {
            String status = ChannelRepository.status();
            c.textCentered(status.isEmpty() ? "Tuning in..." : status, ScreenGeometry.WIDTH / 2f, 72, 1, 0.8f, SUB);
            drawFooter(c, tv, pointer, 0, 0, Hit.NONE);
            return;
        }

        Hit hover = pointer != null ? hit(pos, tv, pointer.u(), pointer.v()) : Hit.NONE;
        int first = scroll(pos, tv);
        long now = System.currentTimeMillis();
        for (int row = 0; row < ROWS; row++) {
            int index = first + row;
            if (index >= channels.size()) break;
            ChannelRepository.Channel ch = channels.get(index);
            float y = ROW_Y0 + row * ROW_STEP;
            boolean hovered = hover.target() == Target.ROW && hover.channelIndex() == index;
            boolean tuned = ch.id().equals(tv.channelId());

            // Channel cell: number and name
            c.panel(CH_X, y, CH_W, ROW_H, 0.5f, hovered ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
            c.text(String.valueOf(ch.number()), CH_X + 3, y + 2, 1, 0.75f, GOLD);
            if (tuned) c.rect(CH_X + CH_W - 6, y + 3, 3, 3, 1, GREEN);
            c.text(c.fit(ch.name(), CH_W - 5, 0.75f), CH_X + 3, y + 9.5f, 1, 0.75f, WHITE);

            // Programme cell: now and next
            c.panel(PROG_X, y, PROG_W, ROW_H, 0.5f, hovered ? PROG_HOVER : PROG, EDGE_LIGHT, EDGE_DARK);
            ChannelRepository.Programme[] nn = ChannelRepository.nowAndNext(ch.id(), now);
            String nowTitle = nn[0] != null ? nn[0].title() : "No listings";
            c.text(c.fit(nowTitle, PROG_W - 6, 0.75f), PROG_X + 3, y + 2, 1, 0.75f, nn[0] != null ? WHITE : SUB);
            if (nn[1] != null) {
                String next = time(nn[1].start()) + "  " + nn[1].title();
                c.text(c.fit(next, PROG_W - 6, 0.7f), PROG_X + 3, y + 9.5f, 1, 0.7f, SUB);
            }
            if (nn[0] != null) {
                float progress = (float) (now - nn[0].start()) / (float) (nn[0].stop() - nn[0].start());
                progress = Math.max(0, Math.min(1, progress));
                c.rect(PROG_X + 1, y + ROW_H - 2, (PROG_W - 2) * progress, 1, 0.8f, GOLD);
            }
            if (hovered) c.outline(CH_X - 1, y - 1, PROG_X + PROG_W - CH_X + 2, ROW_H + 2, 1.2f, GOLD);
        }
        drawFooter(c, tv, pointer, first, channels.size(), hover);
    }

    private static void drawFooter(Canvas c, TvBlockEntity tv, RemoteControl.Pointer pointer, int first, int total, Hit hover) {
        c.panel(0, FOOTER_Y, ScreenGeometry.WIDTH, ScreenGeometry.HEIGHT - FOOTER_Y, 0.5f, BAR, BAR_LIGHT, BAR_DARK);
        button(c, 3, "▲", hover.target() == Target.UP);
        button(c, 23, "▼", hover.target() == Target.DOWN);
        if (total > 0) {
            int last = Math.min(total, first + ROWS);
            String page = (first + 1) + "-" + last + " of " + total;
            c.text(page, 46, FOOTER_Y + 5, 1, 0.7f, SUB);
        }
        if (!tv.channelId().isEmpty()) {
            boolean hovered = hover.target() == Target.BACK;
            c.panel(146, FOOTER_Y + 2, 44, 11, 1, hovered ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
            c.textCentered("Back to TV", 168, FOOTER_Y + 4.5f, 1.5f, 0.6f, WHITE);
            if (hovered) c.outline(145, FOOTER_Y + 1, 46, 13, 1.6f, GOLD);
        }
    }

    private static void button(Canvas c, float x, String label, boolean hovered) {
        c.panel(x, FOOTER_Y + 2, 18, 11, 1, hovered ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
        c.textCentered(label, x + 9, FOOTER_Y + 3.5f, 1.5f, 0.8f, hovered ? GOLD : WHITE);
        if (hovered) c.outline(x - 1, FOOTER_Y + 1, 20, 13, 1.6f, GOLD);
    }

    static String time(long millis) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(CLOCK);
    }
}
