package com.billyke2000s.moviehubtv.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.billyke2000s.moviehubtv.MovieHubTV;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded TMDB poster textures. Downloads run off-thread; GPU operations stay on Minecraft's thread. */
final class PosterCache {
    private PosterCache() {}
    private record Entry(ResourceLocation texture, long retry) {}
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static final ExecutorService WORK = Executors.newFixedThreadPool(2, r -> {
        var t = new Thread(r, "MovieHubTV-Posters"); t.setDaemon(true); return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static int generation;
    static ResourceLocation get(String path) {
        if (!path.matches("/[A-Za-z0-9]+\\.(jpg|png)")) return null;
        Entry entry = CACHE.get(path);
        if (entry != null && (entry.texture != null || System.currentTimeMillis() < entry.retry)) return entry.texture;
        int version = generation;
        CACHE.put(path, new Entry(null, Long.MAX_VALUE));
        while (CACHE.size() > 128) {
            String oldest = CACHE.keySet().iterator().next();
            Entry removed = CACHE.remove(oldest);
            if (removed.texture != null) Minecraft.getInstance().getTextureManager().release(removed.texture);
        }
        WORK.execute(() -> {
            NativeImage image = null;
            try {
                var response = HTTP.send(HttpRequest.newBuilder(URI.create("https://image.tmdb.org/t/p/w92" + path))
                        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                try (var in = response.body()) {
                    if (response.statusCode() != 200) throw new IllegalStateException();
                    byte[] bytes = in.readNBytes(1048577);
                    if (bytes.length > 1048576) throw new IllegalStateException();
                    image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                    if (image.getWidth() > 512 || image.getHeight() > 512) throw new IllegalStateException();
                }
                NativeImage result = image;
                Minecraft.getInstance().execute(() -> {
                    if (version != generation || !CACHE.containsKey(path)) { result.close(); return; }
                    ResourceLocation texture = MovieHubTV.id("poster/" + path.substring(1).replace('.', '_'));
                    Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(result));
                    CACHE.put(path, new Entry(texture, 0));
                });
            } catch (Exception e) {
                if (image != null) image.close();
                Minecraft.getInstance().execute(() -> {
                    if (version == generation && CACHE.containsKey(path)) CACHE.put(path, new Entry(null, System.currentTimeMillis() + 30000));
                });
            }
        });
        return null;
    }
    static void clear() {
        generation++;
        CACHE.values().forEach(e -> { if (e.texture != null) Minecraft.getInstance().getTextureManager().release(e.texture); });
        CACHE.clear();
    }
}
