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
import java.util.List;
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
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0";
    private static final Pattern PLUTO_ID = Pattern.compile("[0-9a-f]{24}");
    /** Pluto says how often to start a new session (refreshInSec); like MovieHub, at most every 10 minutes. */
    private static final int MAX_REFRESH_SEC = 600;
    /** Start renewing this long before the session is due, so a channel change never waits. */
    private static final long RENEW_MARGIN_MS = Duration.ofMinutes(2).toMillis();
    private static final long RETRY_AFTER_FAILURE_MS = Duration.ofSeconds(30).toMillis();

    /** One random device id per game run, like a browser tab. */
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    record Session(String token, String stitcher, String channels, String stitcherParams, String country, long expiresAt) {}
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
        if (!isPlutoId(channelId)) return null;
        StringBuilder url = new StringBuilder(s.stitcher())
                .append("/v2/stitch/hls/channel/").append(channelId).append("/master.m3u8?");
        if (!s.stitcherParams().isEmpty()) url.append(s.stitcherParams()).append('&');
        url.append("jwt=").append(URLEncoder.encode(s.token(), StandardCharsets.UTF_8))
                .append("&masterJWTPassthrough=true");
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

    /** Same request as MovieHub's PlutoSource: anonymous, for the requester's own region. */
    static Session boot() throws Exception {
        String url = BOOT_URL + "?appName=web&appVersion=9.0.0"
                + "&deviceVersion=145.0&deviceModel=web&deviceMake=firefox&deviceType=web"
                + "&clientModelNumber=1.0.0&serverSideAds=true&clientID=" + CLIENT_ID;
        return parseBoot(get(url, null), System.currentTimeMillis());
    }

    static String get(String url, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, */*")
                .header("User-Agent", USER_AGENT);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> response = HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
        return response.body();
    }

    static Session parseBoot(String json, long nowMillis) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String token = root.has("sessionToken") ? root.get("sessionToken").getAsString() : "";
        if (token.isEmpty()) throw new IllegalStateException("Pluto start returned no session token");
        JsonObject servers = root.getAsJsonObject("servers");
        String stitcher = endpoint(servers.get("stitcher").getAsString());
        String channels = endpoint(servers.get("channels").getAsString());
        String stitcherParams = root.has("stitcherParams") && !root.get("stitcherParams").isJsonNull()
                ? root.get("stitcherParams").getAsString() : "";
        String country = "us";
        if (root.has("session") && root.get("session").isJsonObject()
                && root.getAsJsonObject("session").has("countryCode")) {
            country = root.getAsJsonObject("session").get("countryCode").getAsString().toLowerCase();
        }
        if (country.equals("gb")) country = "uk";
        int refresh = root.has("refreshInSec") ? root.get("refreshInSec").getAsInt() : MAX_REFRESH_SEC;
        long expires = nowMillis + Math.min(MAX_REFRESH_SEC, Math.max(60, refresh)) * 1000L;
        long tokenExp = tokenExpiry(token);
        if (tokenExp > nowMillis) expires = Math.min(expires, tokenExp);
        return new Session(token, stitcher, channels, stitcherParams, country, expires);
    }

    /** Only Pluto's own *.pluto.tv servers, as returned by Pluto, ever receive the token. */
    static String endpoint(String url) {
        URI uri = URI.create(url.trim());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().endsWith(".pluto.tv")
                || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("Unexpected Pluto server address: " + url);
        }
        return url.trim().replaceAll("/+$", "");
    }

    /**
     * Starts a session (on the calling thread) and returns this region's live channel list, as
     * MovieHub does: {@code <channels server>/v2/guide/channels} with the session token.
     */
    static String fetchCatalogue() throws Exception {
        Session s = boot();
        session = s;
        return get(s.channels() + "/v2/guide/channels", s.token());
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

    /** Parses {@code /v2/guide/channels}: live channels for this region, in Pluto's channel-number order. */
    static List<PlutoChannel> parseChannels(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray rows = root.isJsonObject() && root.getAsJsonObject().has("data")
                ? root.getAsJsonObject().getAsJsonArray("data")
                : root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        List<PlutoChannel> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonElement el : rows) {
            if (!el.isJsonObject()) continue;
            JsonObject c = el.getAsJsonObject();
            if (bool(c, "plutoOfficeOnly", false) || !bool(c, "isStitched", true)) continue;
            String id = c.has("id") ? c.get("id").getAsString() : c.has("_id") ? c.get("_id").getAsString() : "";
            id = id.toLowerCase();
            String name = c.has("name") ? c.get("name").getAsString().trim() : "";
            if (!isPlutoId(id) || name.isEmpty() || !seen.add(id)) continue;
            int number = c.has("number") && c.get("number").isJsonPrimitive() ? c.get("number").getAsInt() : Integer.MAX_VALUE;
            out.add(new PlutoChannel(id, name, number));
        }
        out.sort(Comparator.comparingInt(PlutoChannel::number));
        return out;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
    }

    static HttpClient http() { return HTTP; }
}
