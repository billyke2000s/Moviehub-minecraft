package com.billyke2000s.moviehubtv.catalogue;

import com.billyke2000s.moviehubtv.ServerConfig;
import com.billyke2000s.moviehubtv.network.CataloguePayload;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** TMDB catalogue plus the same free-provider engine used by Android MovieHub. */
public final class CatalogueService {
    private CatalogueService() {}
    private static final ExecutorService WORK = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20), r -> { var t = new Thread(r, "MovieHubTV-Catalogue"); t.setDaemon(true); return t; });
    private static final Set<UUID> SEARCHING = ConcurrentHashMap.newKeySet();
    private static final Semaphore SEARCH_SLOTS = new Semaphore(2);
    private static final Map<UUID, Long> LAST = new HashMap<>();
    public static void clear() { LAST.clear(); SEARCHING.clear(); TmdbGateway.clear(); MovieHubEngine.stop(); ResolvedMedia.clear(); }
    public static void request(CataloguePayload.Request q, Player player) {
        if (!(player instanceof ServerPlayer p)) return;
        long now = System.currentTimeMillis();
        if (now - LAST.getOrDefault(p.getUUID(), 0L) < 250) { reply(p, q.serial(), error("Please wait briefly, then retry.")); return; }
        LAST.put(p.getUUID(), now);
        if (q.route().equals("cancel")) {
            MovieHubEngine.cancel(p.getUUID());
            JsonObject out = new JsonObject(); out.addProperty("status", "Search cancelled"); reply(p, q.serial(), out); return;
        }
        if (LAST.size() > 2048) LAST.entrySet().removeIf(e -> now - e.getValue() > 60000);
        if (q.route().equals("settings")) {
            JsonObject out = new JsonObject(); JsonArray urls = new JsonArray();
            ServerConfig.get(ServerConfig.PLAYLISTS, List.<String>of()).stream().limit(20).forEach(urls::add);
            out.add("playlists", urls); reply(p, q.serial(), out); return;
        }
        if (!Set.of("movies", "shows", "anime", "movie", "tv", "seasons", "episodes", "resolve",
                "browse-menu", "browse-list", "facet-genres", "facet-providers", "facet-years",
                "facet-countries", "facet-languages", "facet-regions", "facet-access").contains(q.route())) return;
        String key = ServerConfig.get(ServerConfig.TMDB_KEY, "").trim();
        if (key.isEmpty()) {
            reply(p, q.serial(), error("Set tmdbKey in the SERVER's config/moviehubtv-common.toml, then restart the server. No key is required on players' PCs.")); return;
        }
        var server = p.getServer();
        if (server == null) return;
        boolean resolving = q.route().equals("resolve");
        if (resolving && !SEARCHING.add(p.getUUID())) {
            reply(p, q.serial(), error("A free-source search is already running for you. Its links appear as they arrive.")); return;
        }
        try {
            WORK.execute(() -> {
                boolean slot = false;
                try {
                    if (!resolving) { send(p, q.serial(), TmdbGateway.catalogue(q, key)); return; }
                    slot = SEARCH_SLOTS.tryAcquire();
                    if (!slot) { send(p, q.serial(), error("Both source searches are busy. Please retry shortly.")); return; }
                    JsonObject info = TmdbGateway.identity(q.query(), q.id(), q.season(), q.page(), key);
                    String title = TmdbGateway.text(info, "title");
                    if (q.query().equals("tv")) title += " S" + q.season() + " E" + q.page();
                    String label = title.replaceAll("[\\p{Cntrl}|]", " ");
                    String displayTitle = label.substring(0, Math.min(100, label.length()));
                    MovieHubEngine.search(p.getUUID(), info, event -> {
                        String type = TmdbGateway.text(event, "event");
                        JsonObject out = new JsonObject(); out.addProperty("more", !type.equals("done") && !type.equals("error"));
                        if (type.equals("source")) {
                            try {
                                JsonObject source = event.getAsJsonObject("source");
                                String id = ResolvedMedia.add(source);
                                JsonObject item = new JsonObject();
                                item.addProperty("id", id); item.addProperty("kind", "source");
                                item.addProperty("title", TmdbGateway.text(source, "providerName"));
                                item.addProperty("subtitle", TmdbGateway.text(source, "quality") + " - " + TmdbGateway.text(source, "info"));
                                item.addProperty("poster", TmdbGateway.text(info, "poster"));
                                item.addProperty("play", "vod:moviehub:" + id + "|" + displayTitle);
                                out.add("source", item);
                            } catch (Exception ignored) { return; }
                        } else if (type.equals("inventory")) {
                            out.add("providers", event.getAsJsonArray("providers"));
                            out.addProperty("poster", TmdbGateway.text(info, "poster"));
                            out.addProperty("status", "Searching " + event.getAsJsonArray("providers").size() + " MovieHub free services...");
                        } else if (type.equals("provider")) {
                            out.addProperty("provider", TmdbGateway.text(event, "provider"));
                            out.addProperty("providerStatus", TmdbGateway.text(event, "status"));
                            out.addProperty("status", TmdbGateway.text(event, "provider") + ": " + TmdbGateway.text(event, "status"));
                        } else if (type.equals("error")) {
                            out.addProperty("error", "MovieHub resolver error: " + TmdbGateway.text(event, "error"));
                        } else out.addProperty("status", "Free-source search finished");
                        send(p, q.serial(), out);
                    });
                } catch (Exception e) {
                    String message = e instanceof IOException ? e.getMessage() : "TMDB request failed. Check the SERVER key, connection and title ID.";
                    if (message == null || message.contains("://")) message = "MovieHub service request failed";
                    send(p, q.serial(), error(message));
                } finally {
                    if (slot) SEARCH_SLOTS.release();
                    if (resolving) SEARCHING.remove(p.getUUID());
                }
            });
        } catch (RejectedExecutionException e) {
            if (resolving) SEARCHING.remove(p.getUUID());
            reply(p, q.serial(), error("Server catalogue is busy. Try again shortly."));
        }
    }
    private static JsonObject error(String message) { JsonObject o = new JsonObject(); o.addProperty("error", message); return o; }
    private static void send(ServerPlayer p, int serial, JsonObject json) {
        var server = p.getServer();
        if (server != null) server.execute(() -> { if (server.getPlayerList().getPlayer(p.getUUID()) == p) reply(p, serial, json); });
    }
    private static void reply(ServerPlayer p, int serial, JsonObject json) {
        String value = json.toString();
        if (value.length() > 59000) value = error("Catalogue response too large").toString();
        PacketDistributor.sendToPlayer(p, new CataloguePayload.Reply(serial, value));
    }
}
