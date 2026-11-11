package com.billyke2000s.moviehubtv.client;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Client-local media transport. Preserves MovieHub headers and HLS segment containers for VLC. */
final class LocalMediaRelay implements AutoCloseable {
    private static final Set<String> HEADERS = Set.of("user-agent", "referer", "origin", "cookie", "accept", "authorization", "x-csrf-token", "x-requested-with");
    private static final Pattern URI_ATTRIBUTE = Pattern.compile("URI=\"([^\"]+)\"");
    private final HttpServer server;
    private final ExecutorService workers;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String token = UUID.randomUUID().toString();
    private final URI original;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, URI> assets = new ConcurrentHashMap<>();
    private final Map<String, URI> bases = new ConcurrentHashMap<>();
    private final boolean containers;
    private final String root;

    LocalMediaRelay(JsonObject source) throws IOException {
        original = URI.create(TmdbBrowser.text(source, "url"));
        if (!"https".equalsIgnoreCase(original.getScheme()) || original.getHost() == null) throw new IOException("Invalid media URL");
        if (source.has("headers") && source.get("headers").isJsonObject()) {
            source.getAsJsonObject("headers").entrySet().forEach(e -> {
                String value = e.getValue().getAsString();
                if (HEADERS.contains(e.getKey().toLowerCase(Locale.ROOT)) && value.length() < 8192 && !value.contains("\r") && !value.contains("\n"))
                    headers.put(e.getKey(), value);
            });
        }
        containers = source.has("segmentContainers") && source.get("segmentContainers").getAsBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        workers = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "MovieHubTV-MediaRelay"); t.setDaemon(true); return t; });
        server.setExecutor(workers);
        server.createContext("/" + token + "/", this::serve);
        server.start();
        root = asset(original);
    }
    String url() { return root; }
    private String asset(URI uri) {
        if (assets.size() > 30000) throw new IllegalStateException("Media playlist limit");
        String id = UUID.randomUUID().toString();
        assets.put(id, uri);
        // Keep an extension so VLC recognises HLS/DASH even before inspecting content type.
        String path = uri.getPath();
        String extension = path != null && path.contains(".") ? path.substring(path.lastIndexOf('.')) : ".bin";
        if (!extension.matches("\\.[a-zA-Z0-9]{1,8}")) extension = ".bin";
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/" + id + extension;
    }
    private String assetBase(URI uri) {
        if (bases.size() > 2000) throw new IllegalStateException("DASH base limit");
        String id = UUID.randomUUID().toString();
        bases.put(id, uri);
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/" + id + "/";
    }
    private void serve(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(405, -1); return;
            }
            String key = exchange.getRequestURI().getPath().substring(token.length() + 2);
            URI uri;
            int slash = key.indexOf('/');
            if (slash >= 0) {
                URI base = bases.get(key.substring(0, slash));
                String tail = exchange.getRequestURI().getRawPath().substring(token.length() + 2 + slash + 1);
                if (exchange.getRequestURI().getRawQuery() != null) tail += "?" + exchange.getRequestURI().getRawQuery();
                uri = base == null || tail.startsWith("/") || tail.contains("://") ? null : base.resolve(tail);
            } else {
                int dot = key.indexOf('.'); if (dot >= 0) key = key.substring(0, dot);
                uri = assets.get(key);
            }
            if (uri == null) { exchange.sendResponseHeaders(404, -1); return; }
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null && !range.matches("bytes=[0-9]*-[0-9]*")) { exchange.sendResponseHeaders(416, -1); return; }
            HttpResponse<InputStream> response = fetch(uri, range, exchange.getRequestMethod().equals("HEAD"));
            try (InputStream raw = response.body(); BufferedInputStream in = new BufferedInputStream(raw)) {
                if (response.statusCode() != 200 && response.statusCode() != 206) {
                    exchange.sendResponseHeaders(response.statusCode(), -1); return;
                }
                String type = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
                if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Type", type);
                    response.headers().firstValue("Content-Length").ifPresent(v -> exchange.getResponseHeaders().set("Content-Length", v));
                    exchange.sendResponseHeaders(response.statusCode(), -1);
                    return;
                }
                in.mark(16); byte[] prefix = in.readNBytes(8); in.reset();
                boolean hls = new String(prefix, StandardCharsets.US_ASCII).startsWith("#EXTM3U")
                        || type.toLowerCase(Locale.ROOT).contains("mpegurl");
                if (hls) {
                    byte[] bytes = in.readNBytes(2000001);
                    if (bytes.length > 2000000) throw new IOException("Playlist limit");
                    byte[] body = rewrite(new String(bytes, StandardCharsets.UTF_8), response.uri()).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/vnd.apple.mpegurl");
                    exchange.sendResponseHeaders(200, exchange.getRequestMethod().equals("HEAD") ? -1 : body.length);
                    if (!exchange.getRequestMethod().equals("HEAD")) exchange.getResponseBody().write(body);
                } else if (type.toLowerCase(Locale.ROOT).contains("dash+xml") || response.uri().getPath().endsWith(".mpd")) {
                    byte[] bytes = in.readNBytes(2000001);
                    if (bytes.length > 2000000) throw new IOException("DASH manifest limit");
                    byte[] body = rewriteDash(bytes, response.uri());
                    exchange.getResponseHeaders().set("Content-Type", "application/dash+xml");
                    exchange.sendResponseHeaders(200, exchange.getRequestMethod().equals("HEAD") ? -1 : body.length);
                    if (!exchange.getRequestMethod().equals("HEAD")) exchange.getResponseBody().write(body);
                } else if (containers && ProviderPngSegments.matches(prefix, prefix.length)) {
                    ByteArrayOutputStream decoded = new ByteArrayOutputStream();
                    ProviderPngSegments.write(in, decoded);
                    exchange.getResponseHeaders().set("Content-Type", "video/mp2t");
                    exchange.sendResponseHeaders(200, decoded.size());
                    decoded.writeTo(exchange.getResponseBody());
                } else {
                    exchange.getResponseHeaders().set("Content-Type", type);
                    for (String name : List.of("Content-Range", "Accept-Ranges")) response.headers().firstValue(name)
                            .ifPresent(v -> exchange.getResponseHeaders().set(name, v));
                    long length = response.headers().firstValueAsLong("Content-Length").orElse(0);
                    exchange.sendResponseHeaders(response.statusCode(), exchange.getRequestMethod().equals("HEAD") ? -1 : length);
                    if (!exchange.getRequestMethod().equals("HEAD")) in.transferTo(exchange.getResponseBody());
                }
            }
        } catch (Exception e) {
            try { exchange.sendResponseHeaders(502, -1); } catch (IOException ignored) { }
        } finally { exchange.close(); }
    }
    private HttpResponse<InputStream> fetch(URI uri, String range, boolean head) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IOException("Invalid source address");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) throw new IOException("Nonpublic source address");
            }
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45));
            for (var e : headers.entrySet()) {
                String name = e.getKey().toLowerCase(Locale.ROOT);
                boolean secret = name.equals("cookie") || name.equals("authorization") || name.equals("x-csrf-token");
                if (!secret || original.getAuthority().equalsIgnoreCase(uri.getAuthority())) request.header(e.getKey(), e.getValue());
            }
            if (range != null) request.header("Range", range);
            var response = http.send(head ? request.method("HEAD", HttpRequest.BodyPublishers.noBody()).build() : request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 3) return response;
            response.body().close();
            uri = uri.resolve(response.headers().firstValue("Location").orElseThrow());
        }
        throw new IOException("Source redirect limit");
    }
    String rewrite(String text, URI base) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (!line.isBlank() && !line.startsWith("#")) line = asset(base.resolve(line.trim()));
            else {
                Matcher m = URI_ATTRIBUTE.matcher(line); StringBuffer changed = new StringBuffer();
                while (m.find()) m.appendReplacement(changed, Matcher.quoteReplacement("URI=\"" + asset(base.resolve(m.group(1))) + "\""));
                m.appendTail(changed); line = changed.toString();
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
    byte[] rewriteDash(byte[] bytes, URI base) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        rewriteDashElement(doc.getDocumentElement(), base);
        var tf = javax.xml.transform.TransformerFactory.newInstance();
        tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tf.newTransformer().transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(out));
        return out.toByteArray();
    }
    private void rewriteDashElement(org.w3c.dom.Element element, URI inherited) {
        URI base = inherited;
        var nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof org.w3c.dom.Element child && name(child).equals("BaseURL")) {
                URI resolved = inherited.resolve(child.getTextContent().trim());
                base = resolved;
                child.setTextContent(resolved.getPath().endsWith("/") ? assetBase(resolved) : asset(resolved));
            }
        }
        String tag = name(element);
        for (String attribute : List.of("media", "initialization", "sourceURL")) {
            if (!element.hasAttribute(attribute)) continue;
            String value = element.getAttribute(attribute);
            URI resolved = base.resolve(value.replace("%", "%25"));
            if (value.contains("$")) {
                String path = resolved.getRawPath();
                int last = path.lastIndexOf('/');
                URI directory = resolved.resolve(".");
                String suffix = path.substring(last + 1).replace("%25", "%");
                if (resolved.getRawQuery() != null) suffix += "?" + resolved.getRawQuery().replace("%25", "%");
                element.setAttribute(attribute, assetBase(directory) + suffix);
            } else element.setAttribute(attribute, asset(resolved));
        }
        if (tag.equals("MPD") && element.getElementsByTagNameNS("*", "BaseURL").getLength() == 0) {
            var child = element.getOwnerDocument().createElementNS(element.getNamespaceURI(), "BaseURL");
            child.setTextContent(assetBase(inherited.resolve(".")));
            element.insertBefore(child, element.getFirstChild());
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof org.w3c.dom.Element child && !name(child).equals("BaseURL")) rewriteDashElement(child, base);
        }
    }
    private static String name(org.w3c.dom.Element e) { return e.getLocalName() == null ? e.getTagName() : e.getLocalName(); }
    public void close() { server.stop(0); workers.shutdownNow(); assets.clear(); bases.clear(); }
}
