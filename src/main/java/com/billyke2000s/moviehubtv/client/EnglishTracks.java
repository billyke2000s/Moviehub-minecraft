package com.billyke2000s.moviehubtv.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HLS master playlists list alternative audio and subtitle tracks (#EXT-X-MEDIA). Players start the
 * one marked DEFAULT, which for non-English titles is usually the original language. This marks an
 * English audio track as the default in each audio group; when a playlist has no English audio, it
 * turns on its English subtitles instead. Pure text, so it can be tested on its own.
 */
final class EnglishTracks {
    private EnglishTracks() {}
    private static final Pattern ATTR = Pattern.compile("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)");

    /**
     * VLC per-media options: when a file or stream has several audio or subtitle tracks, pick English.
     * English subtitles also show over English audio when a source carries both.
     */
    static String[] vlcOptions() { return new String[]{":audio-language=en,eng,english", ":sub-language=en,eng,english"}; }

    static String prefer(String playlist) {
        if (!playlist.contains("#EXT-X-MEDIA")) return playlist;
        String[] lines = playlist.split("\\R", -1);
        // Per media type and group: line indices of every track, and of the first English one.
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Map<String, Integer> english = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].startsWith("#EXT-X-MEDIA:")) continue;
            Map<String, String> a = attributes(lines[i]);
            String type = a.getOrDefault("TYPE", "");
            if (!type.equals("AUDIO") && !type.equals("SUBTITLES")) continue;
            String key = type + "|" + a.getOrDefault("GROUP-ID", "");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            if (!english.containsKey(key) && isEnglish(a)) english.put(key, i);
        }
        boolean englishAudio = english.keySet().stream().anyMatch(k -> k.startsWith("AUDIO|"));
        for (Map.Entry<String, List<Integer>> g : groups.entrySet()) {
            Integer chosen = english.get(g.getKey());
            if (chosen == null) continue;
            // English subtitles are only switched on when there is no English audio to listen to.
            if (g.getKey().startsWith("SUBTITLES|") && englishAudio) continue;
            for (int i : g.getValue()) {
                boolean on = i == chosen;
                lines[i] = set(set(lines[i], "DEFAULT", on ? "YES" : "NO"), "AUTOSELECT", on ? "YES" : "NO");
                if (g.getKey().startsWith("SUBTITLES|") && on) lines[i] = set(lines[i], "FORCED", "NO");
            }
        }
        return String.join("\n", lines);
    }

    private static boolean isEnglish(Map<String, String> a) {
        String lang = a.getOrDefault("LANGUAGE", "").toLowerCase(Locale.ROOT);
        String name = a.getOrDefault("NAME", "").toLowerCase(Locale.ROOT);
        return lang.equals("en") || lang.startsWith("en-") || lang.equals("eng") || name.matches(".*\\benglish\\b.*");
    }

    private static Map<String, String> attributes(String line) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(line.substring(line.indexOf(':') + 1));
        while (m.find()) out.put(m.group(1), m.group(2).replace("\"", ""));
        return out;
    }

    /** Sets an unquoted attribute, replacing it when present and appending it otherwise. */
    private static String set(String line, String name, String value) {
        Pattern p = Pattern.compile("([:,])" + name + "=[^,]*");
        Matcher m = p.matcher(line);
        if (m.find()) return line.substring(0, m.start()) + m.group(1) + name + "=" + value + line.substring(m.end());
        return line + "," + name + "=" + value;
    }
}
