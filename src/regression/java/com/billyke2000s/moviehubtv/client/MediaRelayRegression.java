package com.billyke2000s.moviehubtv.client;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class MediaRelayRegression {
    public static void main(String[] args) throws Exception {
        var source = JsonParser.parseString("{\"url\":\"https://media.example/master.m3u8\",\"headers\":{\"Referer\":\"https://site.example/\"}}").getAsJsonObject();
        try (LocalMediaRelay relay = new LocalMediaRelay(source)) {
            String hls = relay.rewrite("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\nvideo/part.ts\n", URI.create("https://media.example/master.m3u8"));
            if (hls.contains("\"key.bin\"") || hls.contains("video/part.ts")) throw new AssertionError("Rewrite keys and media segments through header transport");
            if (!hls.contains("http://127.0.0.1:")) throw new AssertionError("Relay stays client-local");
            String xml = "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"><Period><AdaptationSet><Representation><SegmentTemplate media=\"video-$Number%05d$.m4s\" initialization=\"init.mp4\"/></Representation></AdaptationSet></Period></MPD>";
            String dash = new String(relay.rewriteDash(xml.getBytes(StandardCharsets.UTF_8), URI.create("https://media.example/manifest.mpd")), StandardCharsets.UTF_8);
            if (!dash.contains("$Number%05d$")) throw new AssertionError("Preserve DASH sequence formatting");
            if (!dash.contains("media=\"http://127.0.0.1:")) throw new AssertionError("DASH media templates use the same scoped transport");
            boolean blocked = false;
            try { relay.rewriteDash("<!DOCTYPE MPD SYSTEM 'https://example.org/test'><MPD/>".getBytes(StandardCharsets.UTF_8), URI.create("https://media.example/manifest.mpd")); }
            catch (Exception expected) { blocked = true; }
            if (!blocked) throw new AssertionError("External XML entities are disabled");
        }
        System.out.println("PASS: header-aware HLS keys/segments and DASH templates, local binding and XML boundary");
    }
}
