package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import java.util.*;
import java.util.concurrent.*;

/** Fetching never runs on Minecraft's render thread. Catalogues follow the player's real region. */
final class VodRepository {
    private VodRepository() {}
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "MovieHubTV-FreeVOD"); t.setDaemon(true); return t;
    });
    private static volatile List<VodCatalogue.Item> catalogue = List.of();
    private static volatile String status = "Loading free titles...";
    private static volatile boolean loading;
    private static volatile long loadedAt;
    private static volatile int generation;
    private static final Map<String, List<VodCatalogue.Item>> EPISODES = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> RETRY = new ConcurrentHashMap<>();
    static String status() { return status; }
    static boolean loading() { return loading; }
    static List<VodCatalogue.Item> all() { return catalogue; }
    static void ensureLoaded() {
        if (loading || System.currentTimeMillis() - loadedAt < 3600000) return;
        loading = true; int gen = generation;
        WORK.execute(() -> {
            try {
                List<VodCatalogue.Item> found = VodCatalogue.catalogue(PlutoClient.fetchVodCatalogue());
                if (gen != generation) return;
                catalogue = found; status = found.isEmpty() ? "No free titles returned for your region" : "Free on demand - Pluto TV";
                loadedAt = System.currentTimeMillis();
            } catch (Exception e) {
                if (gen != generation) return;
                status = "Free catalogue unavailable - retrying shortly";
                loadedAt = System.currentTimeMillis() - 3570000;
                MovieHubTV.LOGGER.warn("Could not load free on-demand catalogue ({})", e.getClass().getSimpleName());
            } finally { if (gen == generation) loading = false; }
        });
    }
    static List<VodCatalogue.Item> list(int tab, String seriesId, String query) {
        ensureLoaded();
        List<VodCatalogue.Item> source = seriesId.isEmpty() ? catalogue : EPISODES.getOrDefault(seriesId, List.of());
        String q = query.toLowerCase(Locale.ROOT).trim();
        return source.stream().filter(i -> !seriesId.isEmpty() || (tab == 1 ? !i.series() : tab == 2 ? i.series() : i.anime()))
                .filter(i -> q.isEmpty() || i.title().toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    static void loadEpisodes(VodCatalogue.Item item) {
        if (EPISODES.containsKey(item.id()) || System.currentTimeMillis() < RETRY.getOrDefault(item.id(), 0L) || !PENDING.add(item.id())) return;
        int gen = generation;
        WORK.execute(() -> {
            try {
                var found = VodCatalogue.episodes(PlutoClient.vodGet("/v3/vod/series/" + item.id() + "/seasons?includeItems=true&deviceType=web"), item);
                if (gen == generation) EPISODES.put(item.id(), found);
            } catch (Exception e) {
                if (gen == generation) RETRY.put(item.id(), System.currentTimeMillis() + 30000);
            } finally { if (gen == generation) PENDING.remove(item.id()); }
        });
    }
    static String episodeStatus(String id) {
        if (PENDING.contains(id)) return "Loading episodes...";
        if (RETRY.containsKey(id)) return "Episodes unavailable - click Back and retry shortly";
        return "No free episodes available for your region";
    }
    static void clear() { generation++; catalogue = List.of(); loadedAt = 0; loading = false; EPISODES.clear(); PENDING.clear(); RETRY.clear(); }
}
