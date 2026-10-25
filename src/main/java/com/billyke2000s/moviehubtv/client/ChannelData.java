package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.client.provider.SafeNetwork;
import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

/** Pure channel identities and M3U/XMLTV parsing, independent of the game and native player. */
public final class ChannelData {
    private ChannelData() {}
    public record Channel(int number, String id, String name, String url, String guideId, String source) {}
    public record Programme(long start, long stop, String title) {}
    private static final Pattern PLUTO_ID = Pattern.compile("/channel/([0-9a-fA-F]{24})/");
    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z");

    static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
