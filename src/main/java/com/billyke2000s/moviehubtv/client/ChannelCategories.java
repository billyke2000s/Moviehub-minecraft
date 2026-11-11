package com.billyke2000s.moviehubtv.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Splits the channel list into guide categories. Pure Java, so it can be tested on its own.
 *
 * Order: All channels, genres (matched from the channel name, the playlist group or Pluto's own category),
 * Pluto TV, the preferred country (UK), then the other playlist groups (Free-TV's countries) A-Z.
 * Empty categories are left out; a channel can be in several.
 */
final class ChannelCategories {
    private ChannelCategories() {}

    record Category(String name, int[] indices) {}

    static final String ALL = "All channels";
    static final String PLUTO = "Pluto TV";

    /** Genre name and the whole words (lower case) that put a channel in it. */
    private static final Map<String, Pattern> GENRES = new LinkedHashMap<>();
    static {
        genre("News", "news", "cnn", "bloomberg", "euronews", "al jazeera", "aljazeera", "france 24", "cbsn",
                "newsmax", "newsroom", "weather", "headlines", "politics", "political", "opinion");
        genre("Movies", "movie", "movies", "film", "films", "cinema", "flix", "western", "westerns", "action", "thriller",
                "horror", "sci-fi", "scifi");
        genre("Kids", "kid", "kids", "junior", "cartoon", "toon", "toons", "nick", "baby", "little", "children",
                "children's", "family", "anime", "teen");
        genre("Sport", "sport", "sports", "espn", "nfl", "nba", "nhl", "mlb", "golf", "racing", "fight", "fighting",
                "wrestling", "football", "soccer", "tennis", "ufc", "boxing", "outdoor", "fishing", "poker");
        genre("Music", "music", "mtv", "vevo", "hits", "concert", "concerts", "radio", "karaoke", "country music");
        genre("Comedy", "comedy", "laugh", "funny", "sitcom", "sitcoms", "stand-up", "standup");
        genre("Crime & Drama", "crime", "mystery", "mysteries", "detective", "cops", "csi", "drama", "dramas",
                "court", "investigation", "forensic");
        genre("Reality & Lifestyle", "reality", "home", "food", "cooking", "kitchen", "travel", "lifestyle", "garden",
                "auction", "dating", "game show", "gameshow", "game shows");
        genre("Documentaries", "documentary", "documentaries", "docs", "history", "nature", "science",
                "animal", "animals", "wildlife", "space", "discovery", "explore", "planet");
    }

    private static void genre(String name, String... words) {
        StringBuilder re = new StringBuilder("(?<![a-z0-9])(");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) re.append('|');
            re.append(Pattern.quote(words[i]));
        }
        re.append(")(?![a-z0-9])");
        GENRES.put(name, Pattern.compile(re.toString()));
    }

    /**
     * names, groups and plutoCategories are parallel lists (one entry per channel; groups/categories may be
     * empty strings). isPluto marks Pluto channels. preferredGroup (e.g. "UK") is listed before the other groups.
     */
    static List<Category> build(List<String> names, List<String> groups, List<String> plutoCategories,
                                List<Boolean> isPluto, String preferredGroup, List<String> ignoredGroups) {
        int n = names.size();
        Map<String, List<Integer>> genres = new LinkedHashMap<>();
        for (String g : GENRES.keySet()) genres.put(g, new ArrayList<>());
        List<Integer> pluto = new ArrayList<>();
        List<Integer> preferred = new ArrayList<>();
        Map<String, List<Integer>> others = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < n; i++) {
            String group = groups.get(i) == null ? "" : groups.get(i).trim();
            String text = (names.get(i) + " " + group + " " + plutoCategories.get(i)).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Pattern> g : GENRES.entrySet()) {
                if (g.getValue().matcher(text).find()) genres.get(g.getKey()).add(i);
            }
            if (Boolean.TRUE.equals(isPluto.get(i))) {
                pluto.add(i);
            } else if (group.equalsIgnoreCase(preferredGroup)) {
                preferred.add(i);
            } else if (!group.isEmpty() && !ignored(group, ignoredGroups) && !GENRES.containsKey(titleCase(group))) {
                others.computeIfAbsent(group, k -> new ArrayList<>()).add(i);
            }
        }
        List<Category> out = new ArrayList<>();
        int[] all = new int[n];
        for (int i = 0; i < n; i++) all[i] = i;
        if (n > 0) out.add(new Category(ALL, all));
        for (Map.Entry<String, List<Integer>> g : genres.entrySet()) add(out, g.getKey(), g.getValue());
        add(out, PLUTO, pluto);
        add(out, preferredGroup, preferred);
        for (Map.Entry<String, List<Integer>> g : others.entrySet()) add(out, g.getKey(), g.getValue());
        return out;
    }

    private static boolean ignored(String group, List<String> ignoredGroups) {
        for (String g : ignoredGroups) if (g.equalsIgnoreCase(group)) return true;
        return false;
    }

    private static String titleCase(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static void add(List<Category> out, String name, List<Integer> indices) {
        if (indices.isEmpty()) return;
        int[] a = new int[indices.size()];
        for (int i = 0; i < a.length; i++) a[i] = indices.get(i);
        out.add(new Category(name, a));
    }
}
