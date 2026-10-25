package com.billyke2000s.moviehubtv.client.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-player loopback HLS relay. Keeps required headers on master, child playlists, keys and segments. */
public final class PlaybackProxy implements AutoCloseable {
    private static final Pattern HLS_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final List<String> PUBLIC_HEADERS = List.of("user-agent", "accept", "accept-language", "referer", "origin");
    private static final List<String> FORBIDDEN_HEADERS = List.of("host", "connection", "content-length", "transfer-encoding");
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final Map<String, String> headers;
    private final URI origin;
    private final PlaylistSession playlistSession;
    private final boolean segmentContainers;
    private final java.util.Set<String> playlists = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final String token = UUID.randomUUID().toString();
    private final java.util.Set<HttpURLConnection> connections = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PlaybackProxy(ProviderEngine.Stream stream) throws IOException {
        origin = URI.create(SafeNetwork.url(stream.url()));
        headers = Map.copyOf(stream.headers());
        try {
            playlistSession = stream.playlistSession() == null ? null : new PlaylistSession(stream.url(), stream.playlistSession());
        } catch (Exception e) {
            throw new IOException("Invalid playlist session", e);
        }
        segmentContainers = stream.segmentContainers() || playlistSession != null;
        playlists.add(origin.toString());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!header.getKey().matches("[A-Za-z0-9-]{1,80}") || header.getValue().length() > 8192
                    || header.getValue().contains("\r") || header.getValue().contains("\n")
                    || FORBIDDEN_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                throw new IOException("Invalid playback header");
            }
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        executor = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
            Thread t = new Thread(r, "MovieHubTV-Media");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/" + token + "/", this::serve);
        server.start();
    }

    public URI uri() { return URI.create(local(origin.toString())); }

    private String local(String target) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(target.getBytes(StandardCharsets.UTF_8));
    }

    private void serve(HttpExchange exchange) throws IOException {
        HttpURLConnection connection = null;
        boolean replied = false;
        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String prefix = "/" + token + "/";
            String path = exchange.getRequestURI().getRawPath();
            if (!path.startsWith(prefix) || path.length() > 24000) throw new IOException("Invalid media path");
            URI current = URI.create(SafeNetwork.url(new String(Base64.getUrlDecoder().decode(path.substring(prefix.length())),
                    StandardCharsets.UTF_8)));
            boolean playlistRequest = playlists.contains(current.toString());
            boolean renewed = false;
            for (int hop = 0; hop < 5; hop++) {
                URI requestUri = playlistSession == null ? current
                        : playlistSession.authorize(current, playlistRequest, false, this::playlistToken);
                SafeNetwork.publicUrl(requestUri.toString());
                connection = (HttpURLConnection) requestUri.toURL().openConnection();
                connections.add(connection);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", SafeNetwork.USER_AGENT);
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    if (origin.getAuthority().equalsIgnoreCase(current.getAuthority())
                            || PUBLIC_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
                String range = exchange.getRequestHeaders().getFirst("Range");
                boolean wholeEntity = segmentContainers && "bytes=0-".equals(range);
                if (!wholeEntity && range != null && range.matches("bytes=[0-9]+-[0-9]*")) connection.setRequestProperty("Range", range);
                int status = connection.getResponseCode();
                if (status == 403 && !renewed && playlistSession != null && playlistSession.applies(current, playlistRequest)) {
                    connections.remove(connection);
                    connection.disconnect();
                    connection = null;
                    playlistSession.authorize(current, playlistRequest, true, this::playlistToken);
                    renewed = true;
                    continue;
                }
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing media redirect");
                    current = current.resolve(location);
                    connections.remove(connection);
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (status != 200 && status != 206) {
                    exchange.sendResponseHeaders(status >= 400 && status <= 599 ? status : 502, -1);
                    return;
                }
                String type = connection.getContentType();
                try (InputStream in = new java.io.BufferedInputStream(connection.getInputStream())) {
                    in.mark(ProviderSegments.HEAD + 1);
                    byte[] head = in.readNBytes(16);
                    in.reset();
                    boolean playlist = new String(head, StandardCharsets.UTF_8).startsWith("#EXTM3U")
                            || type != null && type.toLowerCase(Locale.ROOT).contains("mpegurl");
                    if (playlist) {
                        playlists.add(current.toString());
                        byte[] body = in.readNBytes(2 * 1024 * 1024 + 1);
                        if (body.length > 2 * 1024 * 1024) throw new IOException("Playlist size limit");
                        byte[] rewritten = rewrite(new String(body, StandardCharsets.UTF_8), current).getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/vnd.apple.mpegurl");
                        exchange.sendResponseHeaders(200, rewritten.length);
                        replied = true;
                        exchange.getResponseBody().write(rewritten);
                    } else {
                        boolean wrapped = segmentContainers && ProviderContainerSegments.matches(head, head.length);
                        if (wrapped) {
                            if (range != null && !wholeEntity) throw new IOException("Wrapped segment ranges unsupported");
                            exchange.getResponseHeaders().set("Content-Type", "video/mp2t");
                            exchange.sendResponseHeaders(200, 0);
                            replied = true;
                            ProviderContainerSegments.write(in, exchange.getResponseBody());
                        } else {
                            byte[] mediaPrefix = segmentContainers && (range == null || wholeEntity)
                                    ? in.readNBytes(ProviderSegments.HEAD) : new byte[0];
                            int skip = ProviderSegments.prefix(mediaPrefix, mediaPrefix.length);
                            if (type != null) exchange.getResponseHeaders().set("Content-Type", skip > 0 ? "video/mp2t" : type);
                            String contentRange = connection.getHeaderField("Content-Range");
                            if (contentRange != null && skip == 0) exchange.getResponseHeaders().set("Content-Range", contentRange);
                            long length = connection.getContentLengthLong();
                            exchange.sendResponseHeaders(skip > 0 ? 200 : status, length > skip ? length - skip : 0);
                            replied = true;
                            exchange.getResponseBody().write(mediaPrefix, skip, mediaPrefix.length - skip);
                            in.transferTo(exchange.getResponseBody());
                        }
                    }
                }
                return;
            }
            throw new IOException("Too many media redirects");
        } catch (Exception e) {
            if (!replied) exchange.sendResponseHeaders(502, -1);
        } finally {
            if (connection != null) {
                connections.remove(connection);
                connection.disconnect();
            }
            exchange.close();
        }
    }

    private org.json.JSONObject playlistToken(URI endpoint, org.json.JSONObject body) throws Exception {
        SafeNetwork.publicUrl(endpoint.toString());
        NativeHttp http = new NativeHttp();
        Map<String, String> tokenHeaders = new java.util.LinkedHashMap<>(headers);
        tokenHeaders.put("Content-Type", "application/json");
        org.json.JSONObject response = http.exchange(endpoint.toString(), body == null ? "GET" : "POST",
                tokenHeaders, body == null ? "" : body.toString(), url -> endpoint.toString().equals(url), true);
        if (response.getInt("status") != 200 || response.getString("text").length() > 16384) {
            throw new IOException("Playlist session unavailable");
        }
        return new org.json.JSONObject(response.getString("text"));
    }

    String rewrite(String text, URI base) {
        StringBuilder out = new StringBuilder();
        boolean nextPlaylist = false;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                String target = SafeNetwork.url(base.resolve(trimmed).toString());
                if (nextPlaylist || URI.create(target).getPath().contains(".m3u")) playlists.add(target);
                out.append(local(target));
                nextPlaylist = false;
            } else {
                if (trimmed.startsWith("#EXT-X-STREAM-INF:")) nextPlaylist = true;
                Matcher matcher = HLS_URI.matcher(line);
                StringBuffer changed = new StringBuffer();
                while (matcher.find()) {
                    String target = SafeNetwork.url(base.resolve(matcher.group(1)).toString());
                    if (trimmed.startsWith("#EXT-X-MEDIA:") || trimmed.startsWith("#EXT-X-I-FRAME-STREAM-INF:")
                            || trimmed.startsWith("#EXT-X-RENDITION-REPORT:")) playlists.add(target);
                    matcher.appendReplacement(changed, Matcher.quoteReplacement("URI=\"" + local(target) + "\""));
                }
                matcher.appendTail(changed);
                out.append(changed);
            }
            out.append('\n');
        }
        return out.toString();
    }

    @Override
    public void close() {
        server.stop(0);
        for (HttpURLConnection connection : connections) connection.disconnect();
        connections.clear();
        executor.shutdownNow();
    }
}
