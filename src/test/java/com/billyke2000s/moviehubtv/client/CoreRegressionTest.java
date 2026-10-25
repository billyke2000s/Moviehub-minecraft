package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.client.provider.ProviderRegressionTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/** Runs with Gradle coreRegressionTest and also with plain Java for offline parser/runtime checks. */
public final class CoreRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        List<ChannelRepository.Channel> channels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ChannelRepository.parseM3u("\uFEFF#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"BBCOne.uk\" group-title=\"News, UK\",BBC One, London\n"
                + "https://example.com/bbc.m3u8\n"
                + "#EXTINF:-1,Local file\nfile:///etc/passwd\n"
                + "#EXTINF:-1,Option injection\nhttps://example.com/a|--option\n"
                + "#EXTINF:-1,Duplicate\nhttps://example.com/bbc.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"pluto:1234567890abcdef12345678.us\",Pluto\n"
                + "https://example.com/channel/1234567890abcdef12345678/master.m3u8\n", channels, seen);
        check(channels.size() == 2, "reject unsafe media URLs; deduplicate URL");
        check(channels.get(0).name().equals("BBC One, London"), "commas in names and quoted attributes");
        check(channels.get(0).guideId().equals("BBCOne.uk"), "retain tvg-id");
        check(channels.get(1).id().equals("1234567890abcdef12345678"), "saved Pluto ID compatibility");
        check(channels.get(1).guideId().equals(channels.get(1).id()), "Pluto guide alias");
        String longUrl = "https://example.com/" + "a".repeat(1000);
        check(ChannelRepository.channelIdFor(longUrl, "x").length() < 256, "bounded packet ID");
        check(ChannelRepository.matchId("enginestirr:42").equals("enginestirr:42"), "provider guide IDs");
        check(ChannelRepository.matchId("xmltv:us:1234567890abcdef12345678").equals("1234567890abcdef12345678"), "legacy XMLTV aliases");
        check(!ChannelRepository.digest("Aa").equals(ChannelRepository.digest("BB")), "avoid Java hash collisions");
        Path file = Files.createTempFile("moviehubtv-guide-", ".xml.gz");
        try {
            DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z");
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String start = now.minusMinutes(10).format(time);
            String stop = now.plusMinutes(30).format(time);
            String xml = "<?xml version=\"1.0\"?><tv>"
                    + "<programme channel=\"BBCOne.uk\" start=\"" + start + "\" stop=\"" + stop + "\"><title>News &amp; weather</title></programme>"
                    + "<programme channel=\"enginestirr:42\" start=\"" + start + "\" stop=\"" + stop + "\"><title>Live</title></programme>"
                    + "<programme channel=\"not-wanted\" start=\"" + start + "\" stop=\"" + stop + "\"><title>Ignore</title></programme>"
                    + "<programme channel=\"BBCOne.uk\" start=\"bad\" stop=\"bad\"><title>Bad</title></programme></tv>";
            try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) { out.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            var guide = ChannelRepository.parseGuide(file, Set.of("BBCOne.uk", "enginestirr:42"));
            check(guide.size() == 2, "non-Pluto guide coverage");
            check(guide.get("BBCOne.uk").size() == 1, "ignore malformed programme");
            check(guide.get("BBCOne.uk").get(0).title().equals("News & weather"), "XML title decoding");
        } finally { Files.deleteIfExists(file); }
        ProviderRegressionTest.run();
        System.out.println("Core regression checks passed: " + checks + " parser/guide checks plus provider checks");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
