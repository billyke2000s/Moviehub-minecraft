package com.billyke2000s.moviehubtv.catalogue;

import com.billyke2000s.moviehubtv.ServerConfig;
import com.billyke2000s.moviehubtv.network.CataloguePayload;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Server-only TMDB metadata proxy. The secret is never included in a packet or an error. */
public final class CatalogueService {
    private CatalogueService() {}
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService WORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { var t = new Thread(r, "MovieHubTV-TMDB"); t.setDaemon(true); return t; });
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<String, Cached> CACHE = Collections.synchronizedMap(new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, Cached> e) { return size() > 128; }
    });
    private record Cached(long at, String value) {}
    public static void clear() { LAST.clear(); CACHE.clear(); }

    public static void request(CataloguePayload.Request q, Player player) {
        if (!(player instanceof ServerPlayer p)) return;
        long now = System.currentTimeMillis();
        if (now - LAST.getOrDefault(p.getUUID(), 0L) < 400) { reply(p, q.serial(), error("Please wait a moment, then retry.")); return; }
        LAST.put(p.getUUID(), now);
        // Limit state even on servers with many unique visitors.
        if (LAST.size() > 2048) LAST.entrySet().removeIf(e -> now - e.getValue() > 60000);
        if (q.route().equals("settings")) {
            JsonObject out = new JsonObject();
            JsonArray urls = new JsonArray();
            ServerConfig.get(ServerConfig.PLAYLISTS, List.<String>of()).stream().limit(20).forEach(urls::add);
            out.add("playlists", urls);
            reply(p, q.serial(), out.toString());
            return;
        }
        if ((!q.route().equals("movie") && !q.route().equals("tv")) || q.id() < 1) {
            reply(p, q.serial(), error("Choose Movie or Show and a positive TMDB ID.")); return;
        }
        String key = ServerConfig.get(ServerConfig.TMDB_KEY, "").trim();
        if (key.isEmpty()) { reply(p, q.serial(), error("TMDB is optional. Set tmdbKey in the server's config/moviehubtv-common.toml to look up IDs. Free browsing works without it.")); return; }
        var server = p.getServer();
        if (server == null) return;
        try {
            WORK.execute(() -> {
                String result;
                try { result = metadata(q.route(), q.id(), key); }
                catch (Exception e) { result = error("TMDB is unavailable or the ID/key was not accepted. Free browsing still works."); }
                String json = result;
                server.execute(() -> { if (server.getPlayerList().getPlayer(p.getUUID()) == p) reply(p, q.serial(), json); });
            });
        } catch (RejectedExecutionException e) { reply(p, q.serial(), error("TMDB is busy. Try again shortly.")); }
    }
    private static String metadata(String type, int id, String key) throws Exception {
        String cacheKey = type + ":" + id;
        Cached cached = CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.at < 3600000) return cached.value;
        String url = "https://api.themoviedb.org/3/" + type + "/" + id + "?language=en-GB";
        var request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(20));
        if (key.matches("[a-fA-F0-9]{32}")) url += "&api_key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        else request.header("Authorization", "Bearer " + key);
        var response = HTTP.send(request.uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var in = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException();
            byte[] bytes = in.readNBytes(262145);
            if (bytes.length > 262144) throw new IllegalStateException();
            JsonObject data = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject out = new JsonObject();
            out.addProperty("title", text(data, type.equals("movie") ? "title" : "name", 160));
            out.addProperty("original", text(data, type.equals("movie") ? "original_title" : "original_name", 160));
            out.addProperty("overview", text(data, "overview", 1500));
            out.addProperty("id", id); out.addProperty("kind", type);
            String json = out.toString(); CACHE.put(cacheKey, new Cached(System.currentTimeMillis(), json)); return json;
        }
    }
    private static String text(JsonObject o, String key, int max) {
        String s = o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
        return s.substring(0, Math.min(max, s.length()));
    }
    private static String error(String message) { JsonObject o = new JsonObject(); o.addProperty("error", message); return o.toString(); }
    private static void reply(ServerPlayer p, int serial, String json) { PacketDistributor.sendToPlayer(p, new CataloguePayload.Reply(serial, json)); }
}
