package com.billyke2000s.moviehubtv.client;

import com.billyke2000s.moviehubtv.network.CataloguePayload;
import com.google.gson.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Consumer;

/** Main-thread reply routing. No TMDB credentials exist in this client class. */
final class CatalogueClient {
    private CatalogueClient() {}
    private record Pending(long deadline, Consumer<JsonObject> callback) {}
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static final ArrayDeque<CataloguePayload.Request> QUEUE = new ArrayDeque<>();
    private static long nextSend;
    private static int serial;
    private static boolean settingsRequested;
    private static long retrySettingsAt;
    static void tick() {
        if (!settingsRequested && System.currentTimeMillis() >= retrySettingsAt && net.minecraft.client.Minecraft.getInstance().player != null) {
            settingsRequested = true;
            request("settings", 0, data -> {
                if (data.has("playlists")) ChannelRepository.serverPlaylists(VodCatalogue.array(data, "playlists").asList()
                        .stream().filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString).toList());
                else { settingsRequested = false; retrySettingsAt = System.currentTimeMillis() + 5000; }
            });
        }
        long now = System.currentTimeMillis();
        if (now >= nextSend && !QUEUE.isEmpty()) {
            PacketDistributor.sendToServer(QUEUE.removeFirst());
            nextSend = now + 300;
        }
        List<Pending> expired = new ArrayList<>();
        PENDING.values().removeIf(p -> { if (now < p.deadline) return false; expired.add(p); return true; });
        for (Pending p : expired) { JsonObject error = new JsonObject(); error.addProperty("error", "Request timed out. Please retry."); p.callback.accept(error); }
    }
    static void request(String type, int id, Consumer<JsonObject> callback) {
        request(type, id, 0, 1, "", callback);
    }
    static void request(String type, int id, int season, int page, String query, Consumer<JsonObject> callback) {
        if (PENDING.size() >= 8) { JsonObject error = new JsonObject(); error.addProperty("error", "Please wait for the current request."); callback.accept(error); return; }
        int n = ++serial;
        PENDING.put(n, new Pending(System.currentTimeMillis() + (type.equals("resolve") ? 540000 : 45000), callback));
        QUEUE.addLast(new CataloguePayload.Request(n, type, id, season, page, query));
    }
    static void receive(CataloguePayload.Reply reply) {
        Pending p = PENDING.get(reply.serial());
        if (p == null) return;
        try {
            JsonObject data = JsonParser.parseString(reply.json()).getAsJsonObject();
            if (!data.has("more") || !data.get("more").getAsBoolean()) PENDING.remove(reply.serial());
            p.callback.accept(data);
        }
        catch (Exception e) { JsonObject error = new JsonObject(); error.addProperty("error", "Invalid catalogue response."); p.callback.accept(error); }
    }
    static void clear() { PENDING.clear(); QUEUE.clear(); nextSend = 0; settingsRequested = false; retrySettingsAt = 0; ChannelRepository.serverPlaylists(List.of()); }
}
