package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.billyke2000s.moviehubtv.client.ChannelData.Channel;
import com.billyke2000s.moviehubtv.client.ChannelData.Programme;
import static com.billyke2000s.moviehubtv.client.ChannelData.*;
import com.billyke2000s.moviehubtv.client.provider.ProviderEngine;
import com.billyke2000s.moviehubtv.client.provider.SafeNetwork;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Downloads the MovieHub channel playlists and TV guide in the background, caches them in
 * .minecraft/moviehubtv-cache, and keeps only now-ish listings for the channels in the playlists.
 */
public final class ChannelRepository {
    private ChannelRepository() {}

    private record Snapshot(List<Channel> channels, Map<String, Channel> byId) {}
    private static volatile Snapshot snapshot = new Snapshot(List.of(), Map.of());
    private static volatile Map<String, String> sourceStatus = Map.of();
    public static Map<String, String> sourceStatus() { return sourceStatus; }

    private static final Duration PLAYLIST_MAX_AGE = Duration.ofHours(6);
    private static final Duration GUIDE_MAX_AGE = Duration.ofHours(6);

    private static volatile Map<String, List<Programme>> guide = Map.of();
    private static volatile String status = "Tuning in...";
    private static volatile long nextLoad;
    private static volatile String loadedConfig = "";
    private static volatile boolean loading;

    public static List<Channel> channels() { return snapshot.channels(); }
    public static Channel channel(String id) { return snapshot.byId().get(id); }
    public static String status() { return status; }

    /** Configuration changes refresh immediately; failures retry after one minute. */
    public static synchronized void ensureLoaded() {
        String config = ClientConfig.get(ClientConfig.PLAYLISTS, ClientConfig.DEFAULT_PLAYLISTS) + "\n"
                + ClientConfig.get(ClientConfig.PROVIDERS, ClientConfig.DEFAULT_PROVIDERS) + "\n"
                + ClientConfig.get(ClientConfig.GUIDE_URL, ClientConfig.DEFAULT_GUIDE) + "\n"
                + ClientConfig.get(ClientConfig.PUBLIC_PLAYLISTS, true);
        if (loading || (config.equals(loadedConfig) && System.currentTimeMillis() < nextLoad)) return;
        loading = true;
        loadedConfig = config;
        Thread t = new Thread(ChannelRepository::loadAll, "MovieHubTV-Loader");
        t.setDaemon(true);
        t.start();
    }

    /** Returns [now, next] (either may be null). */
    public static Programme[] nowAndNext(String channelId, long nowMillis) {
        Channel selected = channel(channelId);
        String guideId = selected == null ? channelId : selected.guideId();
        List<Programme> list = guide.get(guideId);
        Programme[] out = new Programme[2];
        if (list == null) return out;
        for (int i = 0; i < list.size(); i++) {
            Programme p = list.get(i);
            if (p.start() <= nowMillis && nowMillis < p.stop()) {
                out[0] = p;
                if (i + 1 < list.size()) out[1] = list.get(i + 1);
                return out;
            }
            if (p.start() > nowMillis) {
                out[1] = p;
                return out;
            }
        }
        return out;
    }

    private static void loadAll() {
        boolean complete = true;
        Map<String, String> outcomes = new LinkedHashMap<>();
        try {
            Path cache = FMLPaths.GAMEDIR.get().resolve("moviehubtv-cache");
            Files.createDirectories(cache);

            String configured = ClientConfig.get(ClientConfig.PLAYLISTS, ClientConfig.DEFAULT_PLAYLISTS);
            if (ClientConfig.get(ClientConfig.PUBLIC_PLAYLISTS, true)) {
                configured += ",https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8,"
                        + "https://iptv-org.github.io/iptv/index.m3u";
            }
            String[] urls = configured.split(",");
            List<Channel> previous = channels();
            List<Channel> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (url.isEmpty()) continue;
                try {
                    Path file = fetch(url, cache.resolve("playlist-" + digest(url) + ".m3u"), PLAYLIST_MAX_AGE);
                    int before = list.size();
                    parseM3u(Files.readString(file, StandardCharsets.UTF_8), list, seen);
                    outcomes.put("playlist-" + (i + 1), (list.size() - before) + " channels");
                } catch (Exception e) {
                    complete = false;
                    outcomes.put("playlist-" + (i + 1), "Unavailable");
                    MovieHubTV.LOGGER.warn("Could not load playlist {} ({})", i + 1, e.getClass().getSimpleName());
                }
            }
            // Each provider fails independently. Keep existing rows on transient failures.
            publish(list);
            // A small worker pool prevents a slow regional source from holding up every catalogue.
            List<Channel> playlistChannels = List.copyOf(list);
            if (!list.isEmpty()) updateGuide(cache, list);
            List<String> providers = java.util.Arrays.stream(
                    ClientConfig.get(ClientConfig.PROVIDERS, ClientConfig.DEFAULT_PROVIDERS).split(","))
                    .map(String::trim).filter(id -> !id.isEmpty()).distinct().limit(16).toList();
            java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
                Thread thread = new Thread(r, "MovieHubTV-Catalogue");
                thread.setDaemon(true);
                return thread;
            });
            java.util.concurrent.CompletionService<SourceResult> completions =
                    new java.util.concurrent.ExecutorCompletionService<>(workers);
            Map<String, List<ProviderEngine.Entry>> loaded = new HashMap<>();
            Set<String> failed = new HashSet<>();
            try {
                for (String provider : providers) completions.submit(() -> {
                    try { return new SourceResult(provider, ProviderEngine.catalogue(provider), null); }
                    catch (Exception e) { return new SourceResult(provider, List.of(), e); }
                });
                for (int finished = 0; finished < providers.size(); finished++) {
                    SourceResult result = completions.take().get();
                    if (result.error() == null) {
                        loaded.put(result.provider(), result.entries());
                        outcomes.put(result.provider(), result.entries().size() + " channels");
                    } else {
                        complete = false;
                        failed.add(result.provider());
                        outcomes.put(result.provider(), "Unavailable");
                        MovieHubTV.LOGGER.warn("Provider {} unavailable ({})", result.provider(), result.error().getClass().getSimpleName());
                    }
                    list = new ArrayList<>(playlistChannels);
                    seen = new HashSet<>();
                    for (Channel channel : list) seen.add(channel.id());
                    for (String provider : providers) {
                        for (ProviderEngine.Entry entry : loaded.getOrDefault(provider, List.of())) {
                            String id = "engine:" + provider + ":" + digest(entry.key());
                            if (seen.add(id)) list.add(new Channel(101 + list.size(), id, entry.name(),
                                    ProviderEngine.locator(entry), entry.guideId(), provider));
                        }
                        if (failed.contains(provider)) for (Channel old : previous) {
                            if (old.source().equals(provider) && seen.add(old.id())) {
                                list.add(new Channel(101 + list.size(), old.id(), old.name(), old.url(), old.guideId(), old.source()));
                            }
                        }
                    }
                    sourceStatus = Map.copyOf(outcomes);
                    publish(list);
                }
            } finally {
                workers.shutdownNow();
            }
            if (list.isEmpty()) {
                status = "No channels - check your internet";
                return;
            }
            publish(list);
            status = "";
            MovieHubTV.LOGGER.info("MovieHub TV loaded {} channels", list.size());
            if (!updateGuide(cache, list)) complete = false;
        } catch (Exception e) {
            complete = false;
            status = "Could not load channels";
            MovieHubTV.LOGGER.error("MovieHub TV failed to load ({})", e.getClass().getSimpleName());
        } finally {
            sourceStatus = Map.copyOf(outcomes);
            nextLoad = System.currentTimeMillis() + (complete && !channels().isEmpty()
                    ? GUIDE_MAX_AGE.toMillis() : Duration.ofMinutes(1).toMillis());
            loading = false;
        }
    }

    private record SourceResult(String provider, List<ProviderEngine.Entry> entries, Exception error) {}

    private static boolean updateGuide(Path cache, List<Channel> list) {
        String guideUrl = ClientConfig.get(ClientConfig.GUIDE_URL, ClientConfig.DEFAULT_GUIDE).trim();
        if (guideUrl.isEmpty()) { guide = Map.of(); return true; }
        try {
            Set<String> wanted = new HashSet<>();
            for (Channel channel : list) wanted.add(channel.guideId());
            String suffix = URI.create(guideUrl).getPath().endsWith(".gz") ? ".xml.gz" : ".xml";
            Path file = fetch(guideUrl, cache.resolve("guide-" + digest(guideUrl) + suffix), GUIDE_MAX_AGE);
            guide = parseGuide(file, wanted);
            return true;
        } catch (Exception e) {
            MovieHubTV.LOGGER.warn("Could not load TV guide ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private static void publish(List<Channel> list) {
        if (list.isEmpty()) return;
        Map<String, Channel> ids = new HashMap<>();
        for (Channel channel : list) ids.put(channel.id(), channel);
        snapshot = new Snapshot(List.copyOf(list), Map.copyOf(ids));
    }

    /** URL-keyed cache prevents changed/reordered configuration from loading a different source. */
    private static Path fetch(String url, Path target, Duration maxAge) throws Exception {
        SafeNetwork.url(url);
        if (Files.exists(target)
                && System.currentTimeMillis() - Files.getLastModifiedTime(target).toMillis() < maxAge.toMillis()) {
            return target;
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        java.net.HttpURLConnection connection = null;
        try {
            URI current = URI.create(url);
            for (int hop = 0; hop < 5; hop++) {
                SafeNetwork.publicUrl(current.toString());
                connection = (java.net.HttpURLConnection) current.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "MovieHubTV-Minecraft");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing redirect");
                    current = URI.create(SafeNetwork.url(current.resolve(location).toString()));
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (code != 200) throw new IOException("Source HTTP failure");
                long limit = target.getFileName().toString().startsWith("guide-") ? 128L * 1024 * 1024 : 16L * 1024 * 1024;
                long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
                try (InputStream in = connection.getInputStream(); java.io.OutputStream out = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[8192];
                    long size = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        size += n;
                        if (size > limit || System.nanoTime() > deadline) throw new IOException("Source limit exceeded");
                        out.write(buffer, 0, n);
                    }
                }
                try {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return target;
            }
            throw new IOException("Too many redirects");
        } catch (IOException e) {
            if (Files.exists(target)) return target;
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
            Files.deleteIfExists(part);
        }
    }

}
