package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.*;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.*;

/**
 * Short-lived server results. Remotes select an issued ID, never supply a playback URL.
 * Sources found by the same search form a group. Tuning a source sends the whole group, chosen
 * source first, so each player's game can fall back to another source when a link will not play
 * on that PC (for example a link a website only serves to the connection that found it).
 */
public final class ResolvedMedia {
    private ResolvedMedia() {}
    /** Limits for the list sent to players: NBT strings are capped at 65535 bytes. */
    static final int MAX_SOURCES = 12, MAX_CHARS = 40000;
    private static final long LIFETIME = 7200000;

    /** A TV that wants to hear about more sources for the title it is showing. Server side only. */
    public interface Watcher { void mediaSourcesAdded(); }

    private record Entry(long expires, String group, String url, String json) {}
    private static final Map<String, Entry> ITEMS = new LinkedHashMap<>();
    private static final Map<String, List<WeakReference<Watcher>>> WATCHERS = new HashMap<>();

    /** Issues an ID for a source that is not part of a search group. */
    public static synchronized String add(JsonObject source) {
        return add(source, UUID.randomUUID().toString());
    }

    /** Issues an ID for a source found by the search identified by group. */
    public static synchronized String add(JsonObject source, String group) {
        String raw = TmdbGateway.text(source, "url");
        URI url = URI.create(raw);
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null)
            throw new IllegalArgumentException("Invalid media URL");
        if (source.toString().length() > 16000) throw new IllegalArgumentException("Source too large");
        prune();
        while (ITEMS.size() >= 512) ITEMS.remove(ITEMS.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        ITEMS.put(id, new Entry(System.currentTimeMillis() + LIFETIME, group, raw, source.toString()));
        return id;
    }

    /**
     * A JSON array of playable sources for this locator: the chosen one first, then the others its
     * search found, in the order they arrived. Empty when the ID was never issued or has expired.
     */
    public static synchronized String find(String locator) {
        if (!locator.startsWith("vod:moviehub:")) return "";
        String id = locator.substring(13).split("\\|", 2)[0];
        Entry chosen = ITEMS.get(id);
        long now = System.currentTimeMillis();
        if (chosen == null || chosen.expires <= now) return "";
        JsonArray out = new JsonArray();
        out.add(JsonParser.parseString(chosen.json));
        int chars = chosen.json.length();
        Set<String> urls = new HashSet<>(List.of(chosen.url));
        for (Entry e : ITEMS.values()) {
            if (out.size() >= MAX_SOURCES) break;
            if (e == chosen || !e.group.equals(chosen.group) || e.expires <= now || !urls.add(e.url)) continue;
            if (chars + e.json.length() + 1 > MAX_CHARS) continue;
            out.add(JsonParser.parseString(e.json));
            chars += e.json.length() + 1;
        }
        return out.toString();
    }

    /** The TV is told when its title's search finds more sources. Held weakly. */
    public static synchronized void watch(String locator, Watcher watcher) {
        if (!locator.startsWith("vod:moviehub:")) return;
        Entry chosen = ITEMS.get(locator.substring(13).split("\\|", 2)[0]);
        if (chosen == null) return;
        List<WeakReference<Watcher>> list = WATCHERS.computeIfAbsent(chosen.group, g -> new ArrayList<>());
        list.removeIf(r -> r.get() == null || r.get() == watcher);
        list.add(new WeakReference<>(watcher));
    }

    /** Call on the server thread after a search added a source to group. */
    public static void sourcesAdded(String group) {
        List<Watcher> live = new ArrayList<>();
        synchronized (ResolvedMedia.class) {
            List<WeakReference<Watcher>> list = WATCHERS.get(group);
            if (list == null) return;
            list.removeIf(r -> r.get() == null);
            for (WeakReference<Watcher> r : list) { Watcher w = r.get(); if (w != null) live.add(w); }
        }
        // Outside the lock: a watcher calls find().
        for (Watcher w : live) w.mediaSourcesAdded();
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        ITEMS.entrySet().removeIf(e -> e.getValue().expires < now);
        Set<String> groups = new HashSet<>();
        for (Entry e : ITEMS.values()) groups.add(e.group);
        WATCHERS.keySet().retainAll(groups);
    }

    public static synchronized void clear() { ITEMS.clear(); WATCHERS.clear(); }
}
