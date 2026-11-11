package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.MovieHubTV;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pluto TV, done the way Pluto's own web player does it (no account needed).
 *
 * Pluto retired its old token-less stream links: every stream now needs a session token from
 * {@code boot.pluto.tv/v4/start}. This asks for one from the player's own PC, so the session, the
 * channel list and the streams are all for the player's real region (e.g. the UK lineup in the UK),
 * and builds stream links as {@code <stitcher>/v2/stitch/hls/channel/<id>/master.m3u8?jwt=<token>...}.
 * Tokens last about a day; a fresh one is fetched in the background before the old one runs out.
 */
final class PlutoClient {
    private PlutoClient() {}

    static final String BOOT_URL = "https://boot.pluto.tv/v4/start";
    static final String CHANNELS_URL = "https://api.pluto.tv/v2/channels.json";
    private static final String DEFAULT_STITCHER = "https://cfd-v4-service-channel-stitcher-use1-1.prd.pluto.tv";
    private static final String APP_VERSION = "8.0.0-111b2b9dc00bd0bea9030b30662159ed9e7c8bc6";
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final Pattern PLUTO_ID = Pattern.compile("[0-9a-f]{24}");
    /** Renew this long before the token expires, so a channel change never gets a dead token. */
    private static final long RENEW_MARGIN_MS = Duration.ofMinutes(30).toMillis();
    private static final long RETRY_AFTER_FAILURE_MS = Duration.ofSeconds(30).toMillis();

    /** One random device id per game run, like a browser tab. */
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    record Session(String token, String stitcher, String stitcherParams, long expiresAt) {}
    record PlutoChannel(String id, String name, int number) {}

    private static volatile Session session;
    private static volatile boolean booting;
    private static volatile long lastFailure;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    static boolean isPlutoId(String channelId) {
        return channelId != null && PLUTO_ID.matcher(channelId).matches();
    }

    /**
     * The current stream link for a Pluto channel, or null while a session token is still being
     * fetched (call again next frame). Never blocks.
     */
    static String streamUrl(String channelId) {
        long now = System.currentTimeMillis();
        Session s = session;
        if (s == null || now > s.expiresAt() - RENEW_MARGIN_MS) bootInBackground();
        if (s == null || now > s.expiresAt()) return null;
        StringBuilder url = new StringBuilder(s.stitcher())
                .append("/v2/stitch/hls/channel/").append(channelId).append("/master.m3u8")
                .append("?jwt=").append(s.token())
                .append("&masterJWTPassthrough=true");
        if (!s.stitcherParams().isEmpty()) url.append('&').append(s.stitcherParams());
        return url.toString();
    }

    /** True once a token request has failed recently (shown on screen instead of "Tuning"). */
    static boolean recentlyFailed() {
        return session == null && System.currentTimeMillis() - lastFailure < RETRY_AFTER_FAILURE_MS;
    }

    private static void bootInBackground() {
        if (booting || System.currentTimeMillis() - lastFailure < RETRY_AFTER_FAILURE_MS) return;
        booting = true;
        Thread t = new Thread(() -> {
            try {
                session = boot();
                MovieHubTV.LOGGER.info("Pluto TV session ready (valid {} min)",
                        (session.expiresAt() - System.currentTimeMillis()) / 60000);
            } catch (Exception e) {
                lastFailure = System.currentTimeMillis();
                MovieHubTV.LOGGER.warn("Could not start a Pluto TV session", e);
            } finally {
                booting = false;
            }
        }, "MovieHubTV-Pluto");
        t.setDaemon(true);
        t.start();
    }

    static Session boot() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appName", "web");
        params.put("appVersion", APP_VERSION);
        params.put("deviceVersion", "122.0.0");
        params.put("deviceModel", "web");
        params.put("deviceMake", "chrome");
        params.put("deviceType", "web");
        params.put("clientID", CLIENT_ID);
        params.put("clientModelNumber", "1.0.0");
        params.put("serverSideAds", "false");
        params.put("drmCapabilities", "widevine:L3"); // same as the web player; live channels are plain HLS
        params.put("blockingMode", "");
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!query.isEmpty()) query.append('&');
            query.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(BOOT_URL + "?" + query))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "*/*")
                .header("Origin", "https://pluto.tv")
                .header("Referer", "https://pluto.tv/")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode() + " from Pluto start");
        return parseBoot(response.body(), System.currentTimeMillis());
    }

    static Session parseBoot(String json, long nowMillis) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String token = root.has("sessionToken") ? root.get("sessionToken").getAsString() : "";
        if (token.isEmpty()) throw new IllegalStateException("Pluto start returned no session token");
        String stitcher = DEFAULT_STITCHER;
        if (root.has("servers") && root.get("servers").isJsonObject()) {
            JsonObject servers = root.getAsJsonObject("servers");
            if (servers.has("stitcher")) stitcher = servers.get("stitcher").getAsString();
        }
        while (stitcher.endsWith("/")) stitcher = stitcher.substring(0, stitcher.length() - 1);
        String stitcherParams = root.has("stitcherParams") && !root.get("stitcherParams").isJsonNull()
                ? root.get("stitcherParams").getAsString() : "";
        long expires = tokenExpiry(token);
        if (expires <= nowMillis) expires = nowMillis + Duration.ofHours(12).toMillis();
        return new Session(token, stitcher, stitcherParams, expires);
    }

    /** The JWT's "exp" claim in milliseconds, or 0 if it can't be read. */
    static long tokenExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return 0;
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonObject claims = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            return claims.has("exp") ? claims.get("exp").getAsLong() * 1000L : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Parses api.pluto.tv/v2/channels.json (the requester's region): live channels, in Pluto's order. */
    static List<PlutoChannel> parseChannels(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        List<PlutoChannel> out = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject c = el.getAsJsonObject();
            if (c.has("isStitched") && !c.get("isStitched").getAsBoolean()) continue;
            String id = c.has("_id") ? c.get("_id").getAsString().toLowerCase() : "";
            if (!isPlutoId(id)) continue;
            String name = c.has("name") ? c.get("name").getAsString() : "Pluto TV";
            int number = c.has("number") && c.get("number").isJsonPrimitive() ? c.get("number").getAsInt() : Integer.MAX_VALUE;
            out.add(new PlutoChannel(id, name, number));
        }
        out.sort(Comparator.comparingInt(PlutoChannel::number));
        return out;
    }

    static HttpRequest channelsRequest() {
        return HttpRequest.newBuilder(URI.create(CHANNELS_URL))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://pluto.tv")
                .header("Referer", "https://pluto.tv/")
                .GET().build();
    }

    static HttpClient http() { return HTTP; }
}
