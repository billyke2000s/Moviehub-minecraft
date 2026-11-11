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
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z-]+)=\"([^\"]*)\"");
    /** Hosts that are bare IP addresses are third-party re-streams, not broadcasters; they are skipped. */
    private static final Pattern IP_HOST = Pattern.compile("^https?://\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?/");
    /** Country groups listed first, so local channels get the lowest numbers. */
    private static final String PREFERRED_GROUP = "UK";
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

            String[] urls = (ClientConfig.get(ClientConfig.PLAYLISTS, ClientConfig.DEFAULT_PLAYLISTS) + ","
                    + ClientConfig.get(ClientConfig.EXTRA_PLAYLISTS, ClientConfig.DEFAULT_EXTRA_PLAYLISTS)).split(",");
            List<Channel> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            // Pluto first, straight from Pluto for this player's region. When that works, Pluto entries
            // in the playlists (other regions' line-ups, old-style links) are skipped.
            boolean plutoNative = false;
            if (ClientConfig.get(ClientConfig.PLUTO_NATIVE, true)) {
                try {
                    Path file = fetchPluto(cache.resolve("pluto-channels.json"));
                    plutoNative = addPluto(Files.readString(file, StandardCharsets.UTF_8), list, seen) > 0;
                } catch (Exception e) {
                    MovieHubTV.LOGGER.warn("Could not load Pluto TV channels; using the playlists instead", e);
                }
            }
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (url.isEmpty()) continue;
                try {
                    Path file = fetch(url, cache.resolve("playlist-" + i + ".m3u"), PLAYLIST_MAX_AGE);
                    parseM3u(Files.readString(file, StandardCharsets.UTF_8), list, seen, plutoNative);
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
    /** Adds Pluto's live channels; their url is "pluto:<id>" and is turned into a real link at play time. */
    static int addPluto(String json, List<Channel> out, Set<String> seen) {
        int added = 0;
        for (PlutoClient.PlutoChannel c : PlutoClient.parseChannels(json)) {
            if (seen.add(c.id())) {
                out.add(new Channel(101 + out.size(), c.id(), c.name(), "pluto:" + c.id()));
                added++;
            }
        }
        return added;
    }

    /** Pluto's channel list for this player's region; cached so a Pluto outage still shows channels. */
    private static Path fetchPluto(Path target) throws IOException {
        try {
            Files.writeString(target, PlutoClient.fetchCatalogue(), StandardCharsets.UTF_8);
            return target;
        } catch (Exception e) {
            if (Files.exists(target)) {
                MovieHubTV.LOGGER.warn("Using cached Pluto channel list ({})", e.toString());
                return target;
            }
            throw new IOException("Could not load Pluto channels", e);
        }
    }

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

    /** One playlist entry before numbering. */
    private record Entry(String id, String name, String url, boolean preferred) {}

    /**
     * Adds a playlist's channels in order, but with entries in the {@link #PREFERRED_GROUP} group first.
     * Skips on-demand ("VOD ...") groups and streams served from bare IP addresses.
     */
    static void parseM3u(String text, List<Channel> out, Set<String> seen) {
        parseM3u(text, out, seen, false);
    }

    /** As above; with skipPluto, Pluto entries are left out (Pluto was already loaded natively). */
    static void parseM3u(String text, List<Channel> out, Set<String> seen, boolean skipPluto) {
        List<Entry> entries = new ArrayList<>();
        String name = null;
        Map<String, String> attrs = Map.of();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = cleanName(comma >= 0 ? line.substring(comma + 1).trim() : "Channel");
                attrs = attributes(comma >= 0 ? line.substring(0, comma) : line);
            } else if (!line.startsWith("#") && name != null) {
                String group = attrs.getOrDefault("group-title", "");
                String id = channelIdFor(line, name, attrs.get("tvg-id"));
                boolean skip = group.startsWith("VOD") || IP_HOST.matcher(line).find()
                        || (skipPluto && PlutoClient.isPlutoId(id));
                if (!skip) entries.add(new Entry(id, name, line, PREFERRED_GROUP.equalsIgnoreCase(group)));
                name = null;
                attrs = Map.of();
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (Entry e : entries) {
                if (e.preferred() != (pass == 0)) continue;
                if (seen.add(e.id())) out.add(new Channel(101 + out.size(), e.id(), e.name(), e.url()));
            }
        }
    }

    static Map<String, String> attributes(String extinf) {
        Map<String, String> map = new HashMap<>();
        Matcher m = ATTR.matcher(extinf);
        while (m.find()) map.put(m.group(1).toLowerCase(), m.group(2));
        return map;
    }

    /** Drops playlist markers such as the geo-block (Ⓖ) and YouTube (Ⓨ) symbols, which Minecraft's font lacks. */
    static String cleanName(String name) {
        String cleaned = name.replaceAll("[\\u24B6-\\u24E9]", "").trim();
        return cleaned.isEmpty() ? name : cleaned;
    }

    /**
     * Pluto channels use their Pluto ID; channels with a tvg-id (e.g. "BBCOne.uk") use "tvg:" plus it in
     * lower case; anything else uses its URL. Both forms are matched against the guide.
     */
    static String channelIdFor(String url, String name, String tvgId) {
        Matcher m = PLUTO_ID.matcher(url);
        if (m.find()) return m.group(1).toLowerCase();
        if (tvgId != null && !tvgId.isBlank()) return "tvg:" + tvgId.trim().toLowerCase();
        return "url:" + Integer.toHexString(url.hashCode()) + ":" + name;
    }

    /**
     * Guide IDs a channel can be listed under, best first. Pluto: "pluto:ID.us", "pluto:ID.*", "xmltv:*:ID".
     * tvg-id "Name.cc": "xmltv:cc:name", then the UK/GB twin.
     */
    static List<String> guideKeys(String channelId) {
        if (!channelId.startsWith("tvg:")) return List.of();
        String tvg = channelId.substring(4);
        int dot = tvg.lastIndexOf('.');
        String base = (dot > 0 ? tvg.substring(0, dot) : tvg).replaceAll("[^a-z0-9]", "");
        String cc = dot > 0 ? tvg.substring(dot + 1) : "";
        if (base.isEmpty()) return List.of();
        List<String> keys = new ArrayList<>();
        if (!cc.isEmpty()) keys.add("xmltv:" + cc + ":" + base);
        if (cc.equals("uk")) keys.add("xmltv:gb:" + base);
        if (cc.equals("gb")) keys.add("xmltv:uk:" + base);
        return keys;
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

        // tvg channels: guide id -> (channel id, rank by position in guideKeys)
        Map<String, String> tvgOwner = new HashMap<>();
        Map<String, Integer> tvgRank = new HashMap<>();
        for (String id : wanted) {
            List<String> keys = guideKeys(id);
            for (int k = 0; k < keys.size(); k++) {
                tvgOwner.putIfAbsent(keys.get(k), id);
                tvgRank.putIfAbsent(keys.get(k), 10 + k);
            }
        }

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
                        String tvg = guideId == null ? null : tvgOwner.get(guideId.toLowerCase());
                        if ((id != null && wanted.contains(id)) || tvg != null) {
                            channel = tvg != null ? tvg : id;
                            rank = tvg != null ? tvgRank.get(guideId.toLowerCase()) : rank(guideId);
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
