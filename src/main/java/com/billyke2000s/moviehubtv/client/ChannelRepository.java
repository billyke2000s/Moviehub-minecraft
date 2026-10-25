package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
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

    public record Channel(int number, String id, String name, String url) {}
    public record Programme(long start, long stop, String title) {}

    private static final Duration PLAYLIST_MAX_AGE = Duration.ofHours(6);
    private static final Duration GUIDE_MAX_AGE = Duration.ofHours(6);
    private static final Pattern PLUTO_ID = Pattern.compile("/channel/([0-9a-fA-F]{24})/");
    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z");

    private static volatile List<Channel> channels = List.of();
    private static volatile Map<String, Channel> byId = Map.of();
    private static volatile Map<String, List<Programme>> guide = Map.of();
    private static volatile String status = "Tuning in...";
    private static volatile long lastLoad;
    private static volatile boolean loading;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static List<Channel> channels() { return channels; }
    public static Channel channel(String id) { return byId.get(id); }
    public static String status() { return status; }

    /** Starts (or refreshes) loading in the background. Cheap to call every frame. */
    public static void ensureLoaded() {
        if (loading) return;
        if (lastLoad != 0 && System.currentTimeMillis() - lastLoad < GUIDE_MAX_AGE.toMillis()) return;
        loading = true;
        lastLoad = System.currentTimeMillis();
        Thread t = new Thread(ChannelRepository::loadAll, "MovieHubTV-Loader");
        t.setDaemon(true);
        t.start();
    }

    /** Returns [now, next] (either may be null). */
    public static Programme[] nowAndNext(String channelId, long nowMillis) {
        List<Programme> list = guide.get(channelId);
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
        try {
            Path cache = FMLPaths.GAMEDIR.get().resolve("moviehubtv-cache");
            Files.createDirectories(cache);

            String[] urls = ClientConfig.get(ClientConfig.PLAYLISTS, ClientConfig.DEFAULT_PLAYLISTS).split(",");
            List<Channel> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (url.isEmpty()) continue;
                try {
                    Path file = fetch(url, cache.resolve("playlist-" + i + ".m3u"), PLAYLIST_MAX_AGE);
                    parseM3u(Files.readString(file, StandardCharsets.UTF_8), list, seen);
                } catch (Exception e) {
                    MovieHubTV.LOGGER.warn("Could not load playlist {}", url, e);
                }
            }
            if (list.isEmpty()) {
                status = "No channels - check your internet";
                return;
            }
            Map<String, Channel> ids = new HashMap<>();
            for (Channel c : list) ids.put(c.id(), c);
            byId = ids;
            channels = List.copyOf(list);
            status = "";
            MovieHubTV.LOGGER.info("MovieHub TV loaded {} channels", list.size());

            String guideUrl = ClientConfig.get(ClientConfig.GUIDE_URL, ClientConfig.DEFAULT_GUIDE).trim();
            if (!guideUrl.isEmpty()) {
                try {
                    Path file = fetch(guideUrl, cache.resolve(guideUrl.endsWith(".gz") ? "guide.xml.gz" : "guide.xml"), GUIDE_MAX_AGE);
                    guide = parseGuide(file, ids.keySet());
                    MovieHubTV.LOGGER.info("MovieHub TV guide has listings for {} of {} channels", guide.size(), ids.size());
                } catch (Exception e) {
                    MovieHubTV.LOGGER.warn("Could not load TV guide {}", guideUrl, e);
                }
            }
        } catch (Exception e) {
            status = "Could not load channels";
            MovieHubTV.LOGGER.error("MovieHub TV failed to load", e);
        } finally {
            loading = false;
        }
    }

    /** Downloads url to target unless a fresh enough copy exists; falls back to an old copy when offline. */
    private static Path fetch(String url, Path target, Duration maxAge) throws IOException, InterruptedException {
        if (Files.exists(target)
                && System.currentTimeMillis() - Files.getLastModifiedTime(target).toMillis() < maxAge.toMillis()) {
            return target;
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "MovieHubTV-Minecraft")
                    .GET().build();
            HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException e) {
            Files.deleteIfExists(part);
            if (Files.exists(target)) {
                MovieHubTV.LOGGER.warn("Using cached copy of {} ({})", url, e.toString());
                return target;
            }
            throw e;
        }
    }

    static void parseM3u(String text, List<Channel> out, Set<String> seen) {
        String name = null;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Channel";
            } else if (!line.startsWith("#") && name != null) {
                String id = channelIdFor(line, name);
                if (seen.add(id)) out.add(new Channel(101 + out.size(), id, name, line));
                name = null;
            }
        }
    }

    /** Pluto channels use their Pluto ID so the guide can match them; anything else uses its URL. */
    static String channelIdFor(String url, String name) {
        Matcher m = PLUTO_ID.matcher(url);
        if (m.find()) return m.group(1).toLowerCase();
        return "url:" + Integer.toHexString(url.hashCode()) + ":" + name;
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
            XMLStreamReader xml = factory.createXMLStreamReader(in, "UTF-8");
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
                        String id = matchId(guideId);
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
                        found.computeIfAbsent(channel, k -> new HashMap<>())
                                .computeIfAbsent(rank, k -> new ArrayList<>())
                                .add(new Programme(start, stop, title));
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
            return null;
        }
        return id.length() == 24 ? id.toLowerCase() : null;
    }

    /** Lower is better: the playlist streams are the US Pluto feeds. */
    static int rank(String guideId) {
        if (guideId.startsWith("pluto:") && guideId.endsWith(".us")) return 0;
        if (guideId.startsWith("pluto:")) return 1;
        if (guideId.startsWith("xmltv:us:")) return 2;
        return 3;
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
