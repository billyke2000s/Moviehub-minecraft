package com.billyke2000s.moviehubtv.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Matches playlist channels to guide channels by name when their IDs don't line up.
 *
 * Names are compared after dropping accents, playlist markers, bracketed notes, quality tags (HD, 4K...)
 * and the word "TV", then everything but letters and digits: "BBC One HD" and "BBC ONE" both become
 * "bbcone". A guide channel tied to a country (xmltv:it:..., Name.it) only matches channels from that
 * country; otherwise a name must point to exactly one channel, so "News 24" in two countries never
 * gets the wrong listings.
 */
final class GuideMatch {
    private GuideMatch() {}

    private static final Pattern MARKERS = Pattern.compile("[\\u24B6-\\u24E9]");
    private static final Pattern BRACKETS = Pattern.compile("\\([^)]*\\)|\\[[^]]*]");
    private static final Pattern TAGS = Pattern.compile("\\b(hd|fhd|uhd|sd|4k|tv)\\b");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

    static String norm(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD);
        s = ACCENTS.matcher(s).replaceAll("");
        s = MARKERS.matcher(s).replaceAll("");
        s = BRACKETS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT).replace('&', ' ');
        s = TAGS.matcher(s).replaceAll("");
        return NON_ALNUM.matcher(s).replaceAll("");
    }

    /** Country of a playlist channel ("tvg:kanali7.al" -> "al"), "uk" for gb; "" when unknown. */
    static String countryOf(String channelId) {
        if (channelId == null || !channelId.startsWith("tvg:")) return "";
        int dot = channelId.lastIndexOf('.');
        if (dot < 0 || dot == channelId.length() - 1) return "";
        String cc = channelId.substring(dot + 1);
        if (cc.equals("gb")) return "uk";
        return cc.matches("[a-z]{2}") ? cc : "";
    }

    /** The name part of a tvg channel id ("tvg:kanali7.al" -> "kanali7"), compacted. */
    static String tvgBase(String channelId) {
        if (channelId == null || !channelId.startsWith("tvg:")) return "";
        String t = channelId.substring(4);
        int dot = t.lastIndexOf('.');
        return norm(dot > 0 ? t.substring(0, dot) : t);
    }

    /** Index of playlist channels by normalised name (and tvg base name). */
    static final class Index {
        private final Map<String, List<ChannelRepository.Channel>> byName = new HashMap<>();

        Index(Iterable<ChannelRepository.Channel> channels) {
            for (ChannelRepository.Channel c : channels) {
                if (c.url().startsWith("pluto:")) continue; // Pluto has its own guide
                add(norm(c.name()), c);
                add(tvgBase(c.id()), c);
            }
        }

        private void add(String key, ChannelRepository.Channel c) {
            if (key.length() < 2) return;
            List<ChannelRepository.Channel> list = byName.computeIfAbsent(key, k -> new ArrayList<>(1));
            if (!list.contains(c)) list.add(c);
        }

        boolean isEmpty() { return byName.isEmpty(); }

        /**
         * The playlist channel a guide channel belongs to, or null.
         * country is the guide channel's country ("" when it has none).
         */
        String find(String guideName, String country) {
            List<ChannelRepository.Channel> list = byName.get(norm(guideName));
            if (list == null) return null;
            String cc = country.equals("gb") ? "uk" : country;
            if (!cc.isEmpty()) {
                String found = null;
                for (ChannelRepository.Channel c : list) {
                    String own = countryOf(c.id());
                    if (own.equals(cc)) {
                        if (found != null && !found.equals(c.id())) return null; // ambiguous
                        found = c.id();
                    }
                }
                return found;
            }
            return list.size() == 1 ? list.get(0).id() : null;
        }
    }
}
