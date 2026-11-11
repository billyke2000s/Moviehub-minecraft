package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** Short-lived server results. Remotes select an issued ID, never supply a playback URL. */
public final class ResolvedMedia {
    private ResolvedMedia() {}
    private record Entry(long expires, String json) {}
    private static final Map<String, Entry> ITEMS = new LinkedHashMap<>();
    public static synchronized String add(JsonObject source) {
        URI url = URI.create(TmdbGateway.text(source, "url"));
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null)
            throw new IllegalArgumentException("Invalid media URL");
        if (source.toString().length() > 16000) throw new IllegalArgumentException("Source too large");
        ITEMS.entrySet().removeIf(e -> e.getValue().expires < System.currentTimeMillis());
        while (ITEMS.size() >= 512) ITEMS.remove(ITEMS.keySet().iterator().next());
        String id = UUID.randomUUID().toString();
        ITEMS.put(id, new Entry(System.currentTimeMillis() + 7200000, source.toString()));
        return id;
    }
    public static synchronized String find(String locator) {
        if (!locator.startsWith("vod:moviehub:")) return "";
        String id = locator.substring(13).split("\\|", 2)[0];
        Entry entry = ITEMS.get(id);
        return entry != null && entry.expires > System.currentTimeMillis() ? entry.json : "";
    }
    public static synchronized void clear() { ITEMS.clear(); }
}
