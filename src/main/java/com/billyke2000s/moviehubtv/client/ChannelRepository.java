package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.billyke2000s.moviehubtv.client.provider.ProviderEngine;
import com.billyke2000s.moviehubtv.client.provider.SafeNetwork;
import net.neoforged.fml.loading.FMLPaths;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Downloads the MovieHub channel playlists and TV guide in the background, caches them in
 * .minecraft/moviehubtv-cache, and keeps only now-ish listings for the channels in the playlists.
 */
public final class ChannelRepository {
    private ChannelRepository() {}

    public record Channel(int number, String id, String name, String url, String guideId, String source) {}
    private record Snapshot(List<Channel> channels, Map<String, Channel> byId) {}
    private static volatile Snapshot snapshot = new Snapshot(List.of(), Map.of());
    private static volatile Map<String, String> sourceStatus = Map.of();
    public static Map<String, String> sourceStatus() { return sourceStatus; }
    public record Programme(long start, long stop, String title) {}

    private static final Duration PLAYLIST_MAX_AGE = Duration.ofHours(6);
    private static final Duration GUIDE_MAX_AGE = Duration.ofHours(6);
    private static final Pattern PLUTO_ID = Pattern.compile("/channel/([0-9a-fA-F]{24})/");
    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z");

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

    static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    private static final Pattern TVG_ID = Pattern.compile("(?i)\\btvg-id=\"([^\"]*)\"");

    static void parseM3u(String text, List<Channel> out, Set<String> seen) {
        String name = null;
        String guideId = "";
        for (String raw : text.replace("\uFEFF", "").split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                // The first comma outside quoted attributes starts the display name.
                boolean quoted = false;
                int comma = -1;
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == '"') quoted = !quoted;
                    if (line.charAt(i) == ',' && !quoted) { comma = i; break; }
                }
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Channel";
                Matcher attribute = TVG_ID.matcher(line);
                guideId = attribute.find() ? attribute.group(1).trim() : "";
            } else if (!line.startsWith("#") && name != null) {
                try {
                    String url = SafeNetwork.url(line);
                    String id = channelIdFor(url, name);
                    String epg = guideId.isEmpty() ? id : matchId(guideId);
                    if (seen.add(id) && out.size() < 50000) {
                        out.add(new Channel(101 + out.size(), id, name.substring(0, Math.min(512, name.length())),
                                url, epg, "playlist"));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip web pages, local files, native VLC options and unsupported protocols.
                }
                name = null;
                guideId = "";
            }
        }
    }

    /** Keep saved Pluto identities; other channels get collision-resistant URL identities. */
    static String channelIdFor(String url, String name) {
        Matcher m = PLUTO_ID.matcher(url);
        if (m.find()) return m.group(1).toLowerCase(java.util.Locale.ROOT);
        return "url:" + digest(url);
    }

    /**
     * Reads the XMLTV file as a stream and keeps listings for the wanted channels only, from one
     * hour ago to a day ahead. Guide IDs "pluto:ID.us", "pluto:ID.uk" and "xmltv:cc:ID" all match ID;
     * when several sources have the same channel, the earliest-ranked source wins.
     */
    static Map<String, List<Programme>> parseGuide(Path file, Set<String> wanted) throws Exception {
        long now = System.currentTimeMillis();
        long from = now - Duration.ofHours(1).toMillis();
        long to = now + Duration.ofHours(26).toMillis();

        Map<String, Map<Integer, List<Programme>>> found = new HashMap<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
             InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw) {
            XMLStreamReader xml = factory.createXMLStreamReader(new LimitedInputStream(in, 512L * 1024 * 1024), "UTF-8");
            String channel = null;
            int rank = 0;
            long start = 0;
            long stop = 0;
            String title = null;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tag = xml.getLocalName();
                    if ("programme".equals(tag)) {
                        String guideId = xml.getAttributeValue(null, "channel");
                        String id = wanted.contains(guideId) ? guideId : matchId(guideId);
                        if (id != null && wanted.contains(id)) {
                            channel = id;
                            rank = rank(guideId);
                            start = parseTime(xml.getAttributeValue(null, "start"));
                            stop = parseTime(xml.getAttributeValue(null, "stop"));
                            title = null;
                        } else {
                            channel = null;
                        }
                    } else if ("title".equals(tag) && channel != null && title == null) {
                        title = xml.getElementText().trim();
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "programme".equals(xml.getLocalName())) {
                    if (channel != null && title != null && stop > from && start < to && stop > start) {
                        List<Programme> programmes = found.computeIfAbsent(channel, k -> new HashMap<>())
                                .computeIfAbsent(rank, k -> new ArrayList<>());
                        if (programmes.size() < 1000) programmes.add(new Programme(start, stop,
                                title.substring(0, Math.min(1024, title.length()))));
                    }
                    channel = null;
                }
            }
            xml.close();
        }

        Map<String, List<Programme>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, List<Programme>>> e : found.entrySet()) {
            int best = Collections.min(e.getValue().keySet());
            List<Programme> list = new ArrayList<>(e.getValue().get(best));
            list.sort((a, b) -> Long.compare(a.start(), b.start()));
            result.put(e.getKey(), List.copyOf(list));
        }
        return result;
    }

    static String matchId(String guideId) {
        if (guideId == null) return null;
        String id;
        if (guideId.startsWith("pluto:")) {
            id = guideId.substring(6);
            int dot = id.indexOf('.');
            if (dot >= 0) id = id.substring(0, dot);
        } else if (guideId.startsWith("xmltv:")) {
            id = guideId.substring(guideId.lastIndexOf(':') + 1);
        } else {
            return guideId;
        }
        return id.matches("[0-9a-fA-F]{24}") ? id.toLowerCase(java.util.Locale.ROOT) : guideId;
    }

    /** Lower is better: the playlist streams are the US Pluto feeds. */
    static int rank(String guideId) {
        if (guideId.startsWith("pluto:") && guideId.endsWith(".us")) return 0;
        if (guideId.startsWith("pluto:")) return 1;
        if (guideId.startsWith("xmltv:us:")) return 2;
        return 3;
    }

    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long remaining;
        LimitedInputStream(InputStream in, long limit) { super(in); remaining = limit; }
        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0 && --remaining < 0) throw new IOException("Guide size limit");
            return value;
        }
        @Override public int read(byte[] bytes, int off, int length) throws IOException {
            int count = in.read(bytes, off, length);
            if (count > 0 && (remaining -= count) < 0) throw new IOException("Guide size limit");
            return count;
        }
    }

    static long parseTime(String value) {
        if (value == null) return 0;
        try {
            return OffsetDateTime.parse(value.trim(), XMLTV_TIME).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }
}
