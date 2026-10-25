package com.billyke2000s.moviehubtv.client.provider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Desktop host for the exact bundled MovieHub JS modules. No Java or filesystem access in JS. */
public final class ProviderEngine {
    public record Entry(String provider, String key, String name, String guideId, String sourceLabel) {}
    public record Stream(String url, Map<String, String> headers, JSONObject playlistSession, boolean segmentContainers) {
        public Stream(String url, Map<String, String> headers) { this(url, headers, null, false); }
    }
    private record Catalogue(long at, List<Entry> entries, Map<String, JSONObject> rows) {}

    public static final List<String> IDS = List.of(
            "nativepluto", "freelivesports", "stirr", "tcl", "xumo", "lg", "whale", "vizio", "roku", "plex",
            "dlhd", "tubi", "samsung", "localnow", "zlive", "freeshot");
    private static final Map<String, Catalogue> CATALOGUES = new ConcurrentHashMap<>();
    private static final JSONObject MODULES = modules();
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();

    private ProviderEngine() {}

    private static JSONObject modules() {
        try (InputStream in = ProviderEngine.class.getResourceAsStream("/providers/builtin.json")) {
            if (in == null) throw new IOException("Missing provider package");
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getJSONObject("modules");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read bundled providers", e);
        }
    }

    private static String module(String id) throws IOException {
        if (!IDS.contains(id) || !MODULES.has(id)) throw new IOException("Unknown provider");
        return MODULES.getString(id);
    }

    public static List<Entry> catalogue(String id) throws Exception {
        if (!IDS.contains(id)) throw new IOException("Unknown provider");
        synchronized (LOCKS.computeIfAbsent(id, ignored -> new Object())) {
            if (System.currentTimeMillis() < RETRY_AFTER.getOrDefault(id, 0L)) {
                throw new IOException("Provider cooldown");
            }
            try {
                return loadCatalogue(id);
            } catch (Exception e) {
                long delay = e instanceof ProviderFailure && (((ProviderFailure) e).summary.contains("403")
                        || ((ProviderFailure) e).summary.contains("429")) ? 3600000 : 300000;
                RETRY_AFTER.put(id, System.currentTimeMillis() + delay);
                throw e;
            }
        }
    }

    private static List<Entry> loadCatalogue(String id) throws Exception {
        Catalogue existing = CATALOGUES.get(id);
        if (existing != null && System.currentTimeMillis() - existing.at() < Duration.ofMinutes(10).toMillis()) {
            return existing.entries();
        }
        NativeHttp http = new NativeHttp();
        http.deadlineNanos = System.nanoTime() + Duration.ofMinutes(4).toNanos();
        if (id.equals("nativepluto")) {
            List<Entry> entries = new ArrayList<>();
            for (NativePluto.Channel channel : NativePluto.catalogue(http::get)) {
                entries.add(new Entry(id, NativePluto.channelId(channel.url()), channel.name(), channel.guideId(), ""));
            }
            return entries;
        }
        String script = module(id);
        http.deadlineNanos = System.nanoTime() + Duration.ofMinutes(4).toNanos();
        JSONArray rows;
        if (id.equals("freelivesports")) {
            JSONObject metadata = new JSONObject(ProviderScript.evaluate(script, "metadata", ""));
            String url = metadata.getString("url");
            if (!url.startsWith("https://epg.unreel.me/v2/sites/freelivesports/live-channels/public/")) {
                throw new IOException("Catalogue capability rejected");
            }
            Map<String, String> headers = headers(metadata.optJSONObject("headers"));
            rows = new JSONArray(ProviderScript.evaluate(script, "catalogue", http.getHeaders(url, headers)));
        } else {
            rows = (JSONArray) call(id, script, "catalogue", null, http);
        }
        if (id.equals("zlive")) rows = ZliveSources.expand(rows);
        if (rows.length() > 5000 || rows.isEmpty()) throw new IOException("Invalid provider catalogue");
        List<Entry> entries = new ArrayList<>();
        Map<String, JSONObject> byKey = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String key = row.getString("channelId");
            String name = row.getString("name").trim();
            if (key.isEmpty() || key.length() > 400 || name.isEmpty() || name.length() > 512) continue;
            if (byKey.putIfAbsent(key, row) != null) continue;
            String prefix = id.equals("freelivesports") ? "enginefls:" : "engine" + id + ":";
            entries.add(new Entry(id, key, name, prefix + row.optString("guideChannelId", key),
                    row.optString("sourceLabel", "")));

        }
        Catalogue loaded = new Catalogue(System.currentTimeMillis(), List.copyOf(entries), Map.copyOf(byKey));
        CATALOGUES.put(id, loaded);
        return loaded.entries();
    }

    public static Stream resolve(String id, String key) throws Exception {
        if (id.equals("nativepluto")) {
            NativeHttp http = new NativeHttp();
            return new Stream(NativePluto.resolve(NativePluto.ROOT + key, http::get), Map.of());
        }
        catalogue(id); // Refresh short-lived Plex catalogue identities and session metadata before tuning.
        JSONObject row = CATALOGUES.get(id).rows().get(key);
        if (row == null) throw new IOException("Channel no longer advertised");
        String script = module(id);
        JSONObject result;
        if (id.equals("freelivesports")) {
            result = new JSONObject(ProviderScript.evaluate(script, "resolve", row.getString("streamUrl")));
        } else {
            NativeHttp http = new NativeHttp();
            http.deadlineNanos = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            result = (JSONObject) call(id, script, "resolve", row, http);
        }
        if (result.has("drm")) throw new IOException("DRM playback is not supported");
        String url = SafeNetwork.url(result.getString("url"));
        SafeNetwork.publicUrl(url);
        Map<String, String> headers = headers(result.optJSONObject("headers"));
        for (String[] field : new String[][] {{"userAgent", "User-Agent"}, {"referer", "Referer"}}) {
            if (!result.optString(field[0]).isEmpty()) headers.put(field[1], result.getString(field[0]));
        }
        if (id.equals("zlive")) headers.putAll(headers(row.optJSONObject("sourceHeaders")));
        return new Stream(url, Map.copyOf(headers), result.optJSONObject("playlistSession"),
                id.equals("zlive") || result.optBoolean("segmentContainers"));
    }

    private static Map<String, String> headers(JSONObject json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json != null) for (String key : json.keySet()) result.put(key, json.getString(key));
        return result;
    }

    private static Object call(String id, String script, String operation, JSONObject row, NativeHttp http)
            throws Exception {
        ProviderBroker.Executor engine = (source, op, payload) -> ProviderScript.evaluate(source, op, payload, true);
        ProviderBroker.Transport transport = request -> {
                    String target = request.getString("url");
                    if (target.startsWith("https://provider-timer.invalid/")) {
                        int ms = Integer.parseInt(target.substring("https://provider-timer.invalid/".length()));
                        if (ms < 0 || ms > 3000) throw new IOException("Timer capability rejected");
                        Thread.sleep(ms);
                        http.check();
                        return response(target, "");
                    }
                    if (id.equals("zlive") && target.startsWith("https://provider-dns.invalid/")) {
                        String host = URLDecoder.decode(target.substring("https://provider-dns.invalid/".length()),
                                StandardCharsets.UTF_8);
                        ProviderBroker.publicHost(host);
                        JSONArray addresses = new JSONArray();
                        for (InetAddress address : InetAddress.getAllByName(host)) {
                            addresses.put(new JSONObject().put("address", address.getHostAddress())
                                    .put("family", address.getAddress().length == 4 ? 4 : 6));
                        }
                        return response(target, addresses.toString());
                    }
                    String method = request.getString("method");
                    boolean sessionPost = id.equals("zlive") && method.equals("POST")
                            && target.equals("https://iptv.zlive.st/resolve")
                            && request.getString("body").length() <= 16384;
                    if ((id.equals("dlhd") || id.equals("zlive")) && !method.equals("GET") && !sessionPost) {
                        throw new IOException("Provider method capability rejected");
                    }
                    return http.exchange(target, method, headers(request.optJSONObject("headers")),
                            request.getString("body"),
                            url -> ProviderBroker.allowed(id, url) && (!sessionPost || url.equals(target)),
                            request.optString("redirect").equals("manual"));
                };
        if (id.equals("zlive") && operation.equals("resolve")) {
            return ProviderSessions.resolve(script, row, engine, transport, http);
        }
        return ProviderBroker.run(script, operation, row, engine, transport, http);
    }

    private static JSONObject response(String target, String text) {
        return new JSONObject().put("status", 200).put("url", target)
                .put("headers", new JSONObject()).put("text", text);
    }

    public static String locator(Entry entry) {
        return "moviehub:" + entry.provider() + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(entry.key().getBytes(StandardCharsets.UTF_8));
    }

    public static Stream resolveLocator(String locator) throws Exception {
        String[] parts = locator.split(":", 3);
        if (parts.length != 3 || !parts[0].equals("moviehub")) throw new IOException("Invalid locator");
        return resolve(parts[1], new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8));
    }
}
