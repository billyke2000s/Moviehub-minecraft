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

/**
 * The Sky-style TV guide, drawn in a blocky Minecraft look on the TV canvas.
 *
 * The canvas is {@link ScreenGeometry#GUIDE_HEIGHT} tall and as wide as the screen allows (at least 192),
 * see {@link GuideLayout}. Wide screens show Now / Next / Later in separate columns. The guide first opens
 * on the category list ({@link ChannelCategories}, "All channels" first); picking one shows its channels,
 * and the Categories button goes back to the list. After that it reopens wherever you left it.
 * Category, page and picker state are per TV and per player (client side only).
 */
final class GuideRenderer {
    private GuideRenderer() {}

    static final int HEADER_H = 16;
    static final int ROW_Y0 = 18;
    static final int ROW_H = 17;
    static final int ROW_STEP = 18;
    static final int ROWS = 7;
    static final int FOOTER_Y = 145;

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
    private static final String[] COLUMN_NAMES = {"Now", "Next", "Later"};

    enum Target { NONE, ROW, CATEGORY, UP, DOWN, BACK, CATEGORIES, TAB, VOD }

    /** index: a channel index (ROW) or a category index (CATEGORY). */
    record Hit(Target target, int channelIndex) {
        static final Hit NONE = new Hit(Target.NONE, -1);
    }

    /** Per-TV guide state on this client. */
    private static final class State {
        int tab; // Live TV, Movies, Shows, Anime. The existing guide stays intact below the tabs.
        final TmdbBrowser browser = new TmdbBrowser();
        // Remembered by name: the category list is rebuilt (and can reorder) whenever channels reload.
        String category = ChannelCategories.ALL;
        boolean picking = true; // showing the category list; the guide opens on it the first time
        Integer channelScroll; // null until first shown, then the first visible row
        int categoryScroll;
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    static void clear() { STATES.clear(); }
    static boolean isVod(BlockPos pos) { return state(pos).tab != 0; }
    static void vodCategories(BlockPos pos) { state(pos).browser.categories(); }
    static void chooseTab(BlockPos pos, int tab) {
        State s = state(pos);
        if (tab < 0 || tab > 3) return;
        if (tab == 0) s.browser.cancelSearch();
        s.tab = tab;
        if (tab != 0) s.browser.root(tab);
    }
    static void search(BlockPos pos, String query) {
        State s = state(pos); s.browser.search(s.tab, query);
    }
    static boolean backVod(BlockPos pos) {
        State s = state(pos);
        return s.tab != 0 && s.browser.back();
    }
    private static List<com.google.gson.JsonObject> vod(State s) {
        return s.browser.items;
    }
    /** Returns a playable free locator, or opens the selected show's episodes. */
    static String chooseVod(BlockPos pos, int index) {
        String result = state(pos).browser.activate(index);
        if ("moviehub-search".equals(result)) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new CatalogueSearchScreen(pos)); return null;
        }
        return result;
    }

    private static State state(BlockPos pos) {
        return STATES.computeIfAbsent(pos.immutable(), p -> new State());
    }

    /** Index of the TV's chosen category in ChannelRepository.categories(); 0 (All channels) if it has gone. */
    private static int categoryIndex(State s) {
        List<ChannelCategories.Category> cats = ChannelRepository.categories();
        for (int i = 1; i < cats.size(); i++) if (cats.get(i).name().equals(s.category)) return i;
        return 0;
    }

    /** Channel indices (into ChannelRepository.channels()) in the TV's current category. */
    private static int[] visible(State s) {
        List<ChannelCategories.Category> cats = ChannelRepository.categories();
        int category = categoryIndex(s);
        if (category > 0) return cats.get(category).indices();
        int n = ChannelRepository.channels().size();
        int[] all = new int[n];
        for (int i = 0; i < n; i++) all[i] = i;
        return all;
    }

    private static String categoryName(State s) {
        List<ChannelCategories.Category> cats = ChannelRepository.categories();
        int category = categoryIndex(s);
        return category > 0 ? cats.get(category).name() : ChannelCategories.ALL;
    }

    private static int count(State s) {
        if (s.tab != 0) return vod(s).size();
        return s.picking ? ChannelRepository.categories().size() : visible(s).length;
    }

    static int scroll(BlockPos pos, TvBlockEntity tv) {
        State s = state(pos);
        int max = Math.max(0, count(s) - ROWS);
        if (s.tab != 0) return s.browser.scroll;
        if (s.picking) return Math.max(0, Math.min(max, s.categoryScroll));
        if (s.channelScroll == null) {
            // First time the guide is seen: start on the page with the current channel.
            int start = 0;
            ChannelRepository.Channel current = ChannelRepository.channel(tv.channelId());
            int[] list = visible(s);
            if (current != null) {
                int index = ChannelRepository.channels().indexOf(current);
                for (int i = 0; i < list.length; i++) if (list[i] == index) { start = i; break; }
            }
            if (list.length > 0) s.channelScroll = start;
            return Math.max(0, Math.min(max, start));
        }
        return Math.max(0, Math.min(max, s.channelScroll));
    }

    static void scrollBy(BlockPos pos, TvBlockEntity tv, int rows) {
        State s = state(pos);
        if (s.tab != 0) {
            s.browser.scrollBy(Math.abs(rows) == ROWS ? Integer.signum(rows) * TmdbBrowser.ROWS : rows);
            return;
        }
        int max = Math.max(0, count(s) - ROWS);
        int next = Math.max(0, Math.min(max, scroll(pos, tv) + rows));
        if (s.picking) s.categoryScroll = next;
        else s.channelScroll = next;
    }

    /** Opens or closes the category list (the Categories / Channels button). */
    static void toggleCategories(BlockPos pos) {
        State s = state(pos);
        s.picking = !s.picking;
        if (s.picking) s.categoryScroll = Math.max(0, categoryIndex(s) - ROWS / 2);
    }

    static void chooseCategory(BlockPos pos, int category) {
        State s = state(pos);
        List<ChannelCategories.Category> cats = ChannelRepository.categories();
        s.category = category > 0 && category < cats.size() ? cats.get(category).name() : ChannelCategories.ALL;
        s.picking = false;
        s.channelScroll = 0;
    }

    static Hit hit(BlockPos pos, TvBlockEntity tv, GuideLayout l, float u, float v) {
        State s = state(pos);
        if (v >= 1 && v < HEADER_H - 1 && u >= 2 && u < 2 + tabWidth(l) * 4)
            return new Hit(Target.TAB, (int) ((u - 2) / tabWidth(l)));
        if (v >= FOOTER_Y) {
            if (u >= 3 && u < 3 + GuideLayout.BUTTON_W) return new Hit(Target.UP, -1);
            if (u >= 23 && u < 23 + GuideLayout.BUTTON_W) return new Hit(Target.DOWN, -1);
            if (u >= l.categoriesX() && u < l.categoriesX() + GuideLayout.CATEGORIES_W) return new Hit(Target.CATEGORIES, -1);
            if (u >= l.backX() && u < l.backX() + GuideLayout.BACK_W && (!tv.channelId().isEmpty() || (s.tab != 0 && s.browser.canBack()))) return new Hit(Target.BACK, -1);
            return Hit.NONE;
        }
        if (v < ROW_Y0 || u < GuideLayout.CH_X || u >= l.rowRight()) return Hit.NONE;
        if (s.tab != 0) {
            int row = (int) ((v - ROW_Y0) / TmdbBrowser.STEP);
            int index = s.browser.scroll + row;
            return row < TmdbBrowser.ROWS && (v - ROW_Y0) - row * TmdbBrowser.STEP < TmdbBrowser.HEIGHT
                    && index < vod(s).size() ? new Hit(Target.VOD, index) : Hit.NONE;
        }
        int row = (int) ((v - ROW_Y0) / ROW_STEP);
        if (row >= ROWS || (v - ROW_Y0) - row * ROW_STEP >= ROW_H) return Hit.NONE;
        int index = scroll(pos, tv) + row;
        if (s.picking) {
            return index < ChannelRepository.categories().size() ? new Hit(Target.CATEGORY, index) : Hit.NONE;
        }
        int[] list = visible(s);
        return index < list.length ? new Hit(Target.ROW, list[index]) : Hit.NONE;
    }

    static void draw(Canvas c, BlockPos pos, TvBlockEntity tv, GuideLayout l, RemoteControl.Pointer pointer) {
        State s = state(pos);
        c.rect(0, 0, l.width, ScreenGeometry.GUIDE_HEIGHT, 0, BG);

        // Header: title and category, column names on wide screens, clock
        c.panel(0, 0, l.width, HEADER_H, 0.5f, BAR, BAR_LIGHT, BAR_DARK);
        String[] labels = {"Live TV", "Movies", "Shows", "Anime"};
        Hit hover = pointer != null ? hit(pos, tv, l, pointer.u(), pointer.v()) : Hit.NONE;
        for (int i = 0; i < labels.length; i++) {
            float x = 2 + i * tabWidth(l), w = tabWidth(l) - 1;
            boolean over = hover.target() == Target.TAB && hover.channelIndex() == i;
            c.panel(x, 1, w, HEADER_H - 2, 0.7f, over ? CH_HOVER : s.tab == i ? CH : BAR_DARK, EDGE_LIGHT, EDGE_DARK);
            c.textCentered(labels[i], x + w / 2, 5, 1, 0.7f, s.tab == i || over ? GOLD : WHITE);
        }
        c.textRight(LocalTime.now().format(CLOCK), l.width - 5, 5, 1, 0.8f, GOLD);

        if (s.tab != 0) {
            int first = scroll(pos, tv);
            drawVod(c, s, l, first, hover);
            drawFooter(c, s, tv, l, first, count(s), hover);
            return;
        }

        List<ChannelRepository.Channel> channels = ChannelRepository.channels();
        if (channels.isEmpty()) {
            String status = ChannelRepository.status();
            c.textCentered(status.isEmpty() ? "Tuning in..." : status, l.width / 2f, 72, 1, 0.8f, SUB);
            drawFooter(c, s, tv, l, 0, 0, Hit.NONE);
            return;
        }

        int first = scroll(pos, tv);
        if (s.picking) drawCategories(c, s, l, first, hover);
        else drawChannels(c, s, tv, l, first, hover, channels);
        drawFooter(c, s, tv, l, first, count(s), hover);
    }

    private static float tabWidth(GuideLayout l) { return Math.min(58, (l.width - 42) / 4); }

    private static void drawVod(Canvas c, State s, GuideLayout l, int first, Hit hover) {
        var items = vod(s);
        if (items.isEmpty()) {
            String message = s.browser.status;
            for (int line = 0; line < 9 && !message.isEmpty(); line++) {
                String part = c.font.plainSubstrByWidth(message, (int) ((l.width - 12) / 0.65f));
                c.text(part, 6, 35 + line * 10, 1, 0.65f, SUB);
                message = message.substring(part.length()).stripLeading();
            }
        }
        for (int row = 0; row < TmdbBrowser.ROWS && first + row < items.size(); row++) {
            int index = first + row; var item = items.get(index);
            float y = ROW_Y0 + row * TmdbBrowser.STEP, w = l.rowRight() - GuideLayout.CH_X;
            boolean over = hover.target() == Target.VOD && hover.channelIndex() == index;
            c.panel(GuideLayout.CH_X, y, w, TmdbBrowser.HEIGHT, 0.5f, over ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
            var poster = PosterCache.get(TmdbBrowser.text(item, "poster"));
            if (poster != null) {
                boolean logo = TmdbBrowser.text(item, "kind").equals("browse");
                c.image(poster, 4, y + (logo ? 5 : 1), 18, logo ? 18 : 27, 1, 0, 0, 1, 1, WHITE);
            }
            else c.rect(4, y + 1, 18, 27, 1, BAR_DARK);
            c.text(c.fit(TmdbBrowser.text(item, "title"), w - 52, 0.75f), 27, y + 3, 1, 0.75f, WHITE);
            c.text(c.fit(TmdbBrowser.text(item, "subtitle"), w - 34, 0.6f), 27, y + 13, 1, 0.6f, GOLD);
            c.text(c.fit(TmdbBrowser.text(item, "overview"), w - 34, 0.55f), 27, y + 21, 1, 0.55f, SUB);
            String kind = TmdbBrowser.text(item, "kind");
            c.textRight(kind.equals("source") ? "Play" : kind.equals("provider") ? "..." : ">", l.rowRight() - 4, y + 3, 1, 0.65f, GOLD);
            if (over) c.outline(GuideLayout.CH_X - 1, y - 1, w + 2, TmdbBrowser.HEIGHT + 2, 1.2f, GOLD);
        }
    }

    private static void drawCategories(Canvas c, State s, GuideLayout l, int first, Hit hover) {
        List<ChannelCategories.Category> cats = ChannelRepository.categories();
        int chosen = categoryIndex(s);
        for (int row = 0; row < ROWS; row++) {
            int index = first + row;
            if (index >= cats.size()) break;
            ChannelCategories.Category cat = cats.get(index);
            float y = ROW_Y0 + row * ROW_STEP;
            boolean hovered = hover.target() == Target.CATEGORY && hover.channelIndex() == index;
            float w = l.rowRight() - GuideLayout.CH_X;
            c.panel(GuideLayout.CH_X, y, w, ROW_H, 0.5f, hovered ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
            if (index == chosen) c.rect(GuideLayout.CH_X + 3, y + 7, 3, 3, 1, GREEN);
            c.text(c.fit(cat.name(), w - 60, 0.8f), GuideLayout.CH_X + 9, y + 5, 1, 0.8f, WHITE);
            c.textRight(cat.indices().length + " channels", l.rowRight() - 4, y + 5.5f, 1, 0.7f, SUB);
            if (hovered) c.outline(GuideLayout.CH_X - 1, y - 1, w + 2, ROW_H + 2, 1.2f, GOLD);
        }
    }

    private static void drawChannels(Canvas c, State s, TvBlockEntity tv, GuideLayout l, int first, Hit hover,
                                     List<ChannelRepository.Channel> channels) {
        int[] list = visible(s);
        long now = System.currentTimeMillis();
        for (int row = 0; row < ROWS; row++) {
            int pos = first + row;
            if (pos >= list.length) break;
            int index = list[pos];
            ChannelRepository.Channel ch = channels.get(index);
            float y = ROW_Y0 + row * ROW_STEP;
            boolean hovered = hover.target() == Target.ROW && hover.channelIndex() == index;
            boolean tuned = ch.id().equals(tv.channelId());

            // Channel cell: number and name
            c.panel(GuideLayout.CH_X, y, GuideLayout.CH_W, ROW_H, 0.5f, hovered ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
            c.text(String.valueOf(ch.number()), GuideLayout.CH_X + 3, y + 2, 1, 0.75f, GOLD);
            if (tuned) c.rect(GuideLayout.CH_X + GuideLayout.CH_W - 6, y + 3, 3, 3, 1, GREEN);
            c.text(c.fit(ch.name(), GuideLayout.CH_W - 5, 0.75f), GuideLayout.CH_X + 3, y + 9.5f, 1, 0.75f, WHITE);

            List<ChannelRepository.Programme> shows = ChannelRepository.upcoming(ch.id(), now, Math.max(2, l.columns));
            if (l.columns == 1) drawNowNextCell(c, l, y, hovered, shows, now);
            else drawColumns(c, l, y, hovered, shows, now);
            if (hovered) c.outline(GuideLayout.CH_X - 1, y - 1, l.rowRight() - GuideLayout.CH_X + 2, ROW_H + 2, 1.2f, GOLD);
        }
    }

    /** Narrow screens: one programme cell with now on top and next underneath. */
    private static void drawNowNextCell(Canvas c, GuideLayout l, float y, boolean hovered,
                                        List<ChannelRepository.Programme> shows, long now) {
        float x = GuideLayout.PROG_X, w = l.progW;
        c.panel(x, y, w, ROW_H, 0.5f, hovered ? PROG_HOVER : PROG, EDGE_LIGHT, EDGE_DARK);
        ChannelRepository.Programme on = shows.isEmpty() || shows.get(0).start() > now ? null : shows.get(0);
        ChannelRepository.Programme next = on == null ? (shows.isEmpty() ? null : shows.get(0)) : (shows.size() > 1 ? shows.get(1) : null);
        c.text(c.fit(on != null ? on.title() : "No listings", w - 6, 0.75f), x + 3, y + 2, 1, 0.75f, on != null ? WHITE : SUB);
        if (next != null) c.text(c.fit(time(next.start()) + "  " + next.title(), w - 6, 0.7f), x + 3, y + 9.5f, 1, 0.7f, SUB);
        if (on != null) progress(c, x, y, w, on, now);
    }

    /** Wide screens: Now / Next / Later each in its own cell, with times. */
    private static void drawColumns(Canvas c, GuideLayout l, float y, boolean hovered,
                                    List<ChannelRepository.Programme> shows, long now) {
        boolean somethingOn = !shows.isEmpty() && shows.get(0).start() <= now;
        for (int col = 0; col < l.columns; col++) {
            float x = l.columnX(col), w = l.columnW();
            c.panel(x, y, w, ROW_H, 0.5f, hovered ? PROG_HOVER : PROG, EDGE_LIGHT, EDGE_DARK);
            // Column 0 is what is on now; if nothing is on, it stays empty and the rest move along.
            int index = somethingOn ? col : col - 1;
            ChannelRepository.Programme p = index >= 0 && index < shows.size() ? shows.get(index) : null;
            if (p == null) {
                if (col == 0) c.text(shows.isEmpty() ? "No listings" : "Off air", x + 3, y + 5.5f, 1, 0.75f, SUB);
                continue;
            }
            c.text(c.fit(p.title(), w - 6, 0.75f), x + 3, y + 2, 1, 0.75f, col == 0 ? WHITE : 0xFFDCE6F5);
            c.text(c.fit(time(p.start()) + " - " + time(p.stop()), w - 6, 0.65f), x + 3, y + 9.5f, 1, 0.65f, SUB);
            if (col == 0) progress(c, x, y, w, p, now);
        }
    }

    private static void progress(Canvas c, float x, float y, float w, ChannelRepository.Programme on, long now) {
        float done = (float) (now - on.start()) / (float) Math.max(1, on.stop() - on.start());
        c.rect(x + 1, y + ROW_H - 2, (w - 2) * Math.max(0, Math.min(1, done)), 1, 0.8f, GOLD);
    }

    private static void drawFooter(Canvas c, State s, TvBlockEntity tv, GuideLayout l, int first, int total, Hit hover) {
        c.panel(0, FOOTER_Y, l.width, ScreenGeometry.GUIDE_HEIGHT - FOOTER_Y, 0.5f, BAR, BAR_LIGHT, BAR_DARK);
        button(c, 3, GuideLayout.BUTTON_W, "▲", hover.target() == Target.UP, 0.8f);
        button(c, 23, GuideLayout.BUTTON_W, "▼", hover.target() == Target.DOWN, 0.8f);
        if (total > 0) {
            int last = Math.min(total, first + (s.tab != 0 ? TmdbBrowser.ROWS : ROWS));
            String page = (first + 1) + "-" + last + " of " + total;
            c.text(c.fit(page, l.pageW(), 0.7f), l.pageX(), FOOTER_Y + 5, 1, 0.7f, SUB);
        }
        button(c, l.categoriesX(), GuideLayout.CATEGORIES_W, s.tab != 0 ? "Categories" : s.picking ? "Channels" : "Categories",
                hover.target() == Target.CATEGORIES, 0.6f);
        if (!tv.channelId().isEmpty() || (s.tab != 0 && s.browser.canBack())) {
            button(c, l.backX(), GuideLayout.BACK_W, s.tab != 0 && s.browser.canBack() ? "Back" : "Back to TV", hover.target() == Target.BACK, 0.6f);
        }
    }

    private static void button(Canvas c, float x, float w, String label, boolean hovered, float scale) {
        c.panel(x, FOOTER_Y + 2, w, 11, 1, hovered ? CH_HOVER : CH, EDGE_LIGHT, EDGE_DARK);
        float y = scale >= 0.8f ? FOOTER_Y + 3.5f : FOOTER_Y + 4.5f;
        c.textCentered(c.fit(label, w - 2, scale), x + w / 2, y, 1.5f, scale, hovered ? GOLD : WHITE);
        if (hovered) c.outline(x - 1, FOOTER_Y + 1, w + 2, 13, 1.6f, GOLD);
    }

    static String time(long millis) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(CLOCK);
    }
}

