package com.billyke2000s.moviehubtv.client.provider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProviderRegressionTest {
    public static void run() throws Exception {
        JSONObject modules;
        try (InputStream in = ProviderRegressionTest.class.getResourceAsStream("/providers/builtin.json")) {
            modules = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getJSONObject("modules");
        }
        for (String id : ProviderEngine.IDS) {
            if (id.equals("nativepluto")) continue;
            String script = modules.getString(id);
            String operation = id.equals("freelivesports") ? "metadata" : "catalogue";
            String payload = id.equals("freelivesports") ? "" : new JSONObject().put("now", System.currentTimeMillis())
                    .put("seed", java.util.Base64.getEncoder().encodeToString(new byte[32]))
                    .put("row", new JSONObject()).put("responses", new JSONArray()).toString();
            JSONObject result = new JSONObject(ProviderScript.evaluate(script, operation, payload));
            if (!id.equals("freelivesports") && !result.has("requests") && !result.optBoolean("done")) {
                throw new AssertionError("Provider failed first catalogue step: " + id);
            }
        }
        AtomicInteger requests = new AtomicInteger();
        JSONObject result = (JSONObject) ProviderBroker.run(modules.getString("stirr"), "resolve",
                new JSONObject().put("channelId", "42"), ProviderScript::evaluate, request -> {
                    requests.incrementAndGet();
                    if (!request.getString("method").equals("POST") || !ProviderBroker.allowed("stirr", request.getString("url"))) {
                        throw new AssertionError("STIRR capability mismatch");
                    }
                    return new JSONObject().put("status", 200).put("url", request.getString("url"))
                            .put("headers", new JSONObject()).put("text",
                                    "{\"data\":[{\"media\":[\"https://example.com/live.m3u8?nonce=[vx_nonce]\"]}]}");
                }, new NativeHttp());
        if (requests.get() != 1 || result.getString("url").contains("[vx_nonce]")) throw new AssertionError("Resolver replay failed");
        String sandbox = ProviderScript.evaluate("function invoke(){return {java:typeof java, packages:typeof Packages};}", "test", "");
        if (!new JSONObject(sandbox).getString("packages").equals("undefined")) throw new AssertionError("Java access exposed");
        for (String url : new String[] {"file:///tmp/x", "https://a:b@example.com/x", "javascript:alert(1)", "https://example.com/x\nheader"}) {
            try { SafeNetwork.url(url); throw new AssertionError("Unsafe URL accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { ProviderBroker.publicHost("127.0.0.1"); throw new AssertionError("Loopback accepted"); }
        catch (java.io.IOException expected) { }
        if (ProviderBroker.allowed("stirr", "https://example.com/api/x")) throw new AssertionError("Provider capability widened");
        ProviderPngSegmentsTest.main(new String[0]);
        ProviderContainerSegmentsTest.main(new String[0]);
        java.net.URI media = java.net.URI.create("https://8.8.8.8/channel/master.m3u8");
        try (PlaybackProxy proxy = new PlaybackProxy(new ProviderEngine.Stream(media.toString(), java.util.Map.of("Referer", "https://example.com/")))) {
            String changed = proxy.rewrite("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=10\nchild\n"
                    + "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:4,\npart.ts\n", media);
            if (changed.contains("URI=\"key.bin\"") || changed.contains("\nchild\n") || !changed.contains("127.0.0.1")) {
                throw new AssertionError("Child playlists, keys and segments must pass through header relay");
            }
        }
        try {
            new PlaybackProxy(new ProviderEngine.Stream(media.toString(), java.util.Map.of("Referer", "x\r\nInjected: true")));
            throw new AssertionError("Header injection accepted");
        } catch (java.io.IOException expected) { }
        JSONObject session = new JSONObject().put("endpoint", "https://8.8.8.8/api/playlist-token?slug=channel")
                .put("slug", "channel").put("token", "test-token").put("expiresAtMs", System.currentTimeMillis() + 60000);
        PlaylistSession tokens = new PlaylistSession(media.toString(), session);
        java.net.URI authorized = tokens.authorize(media, true, false, (endpoint, body) -> { throw new AssertionError("Premature renewal"); });
        if (!authorized.toString().endsWith("t=test-token")) throw new AssertionError("Missing playlist authorization");
        java.net.URI foreign = java.net.URI.create("https://1.1.1.1/channel/master.m3u8");
        if (!tokens.authorize(foreign, true, false, (endpoint, body) -> null).equals(foreign)) throw new AssertionError("Token escaped source origin");
        JSONObject source = new JSONObject().put("resolverSlug", "feed-a").put("label", "Server A");
        JSONObject channel = new JSONObject().put("channelId", "main").put("name", "Test").put("region", "uk")
                .put("sources", new JSONArray().put(source).put(new JSONObject().put("resolverSlug", "feed-b").put("label", "Server B")));
        JSONArray expanded = ZliveSources.expand(new JSONArray().put(channel));
        if (expanded.length() != 2 || expanded.getJSONObject(0).getString("channelId").equals(expanded.getJSONObject(1).getString("channelId"))) {
            throw new AssertionError("ZLive sources collapsed");
        }
        System.out.println("Provider checks passed: 15 bundled modules, STIRR replay, sandbox, URL and capability boundaries");
    }
}
