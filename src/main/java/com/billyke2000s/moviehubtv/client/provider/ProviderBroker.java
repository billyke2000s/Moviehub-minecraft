package com.billyke2000s.moviehubtv.client.provider;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Replays pure JS until completion, fulfilling only APK-audited provider HTTP capabilities. */
final class ProviderBroker {

    static final String[] IDS = {
        "stirr",
        "tcl",
        "xumo",
        "lg",
        "whale",
        "vizio",
        "roku",
        "plex",
        "dlhd",
        "tubi",
        "samsung",
        "localnow",
        "zlive",
        "freeshot",
    };

    private ProviderBroker() {}

    static boolean supported(String id) {
        return Arrays.asList(IDS).contains(id);
    }

    static boolean allowed(String provider, String url) {
        try {
            URI u = URI.create(url);
            if (
                !"https".equals(u.getScheme()) ||
                u.getUserInfo() != null ||
                (u.getPort() != -1 && u.getPort() != 443)
            ) return false;
            String host = u.getHost();
            if (host == null) return false;
            switch (provider) {
                case "localnow":
                    return (
                        host.equals("localnow.com") ||
                        host.endsWith(".localnow.com") ||
                        host.endsWith(".localnowapi.com") ||
                        host.endsWith(".amdvids.com")
                    );
                case "samsung":
                    return (
                        host.equals("i.mjh.nz") ||
                        // i.mjh.nz now redirects its Samsung list through GitHub.
                        (host.equals("github.com") && u.getPath().startsWith("/matthuisman/i.mjh.nz/")) ||
                        (host.equals("raw.githubusercontent.com") &&
                            u.getPath().startsWith("/matthuisman/i.mjh.nz/") &&
                            u.getPath().endsWith("/SamsungTVPlus/.channels.json.gz")) ||
                        host.equals("jmp2.uk") ||
                        host.endsWith(".samsungtvplus.com") ||
                        host.endsWith(".cloudfront.net") ||
                        // jmp2.uk also redirects some channels to Amagi's CDN.
                        host.endsWith(".amagi.tv") ||
                        host.endsWith(".samsung.com")
                    );
                case "tubi":
                    return (
                        host.equals("tubi.io") ||
                        host.endsWith(".tubi.io") ||
                        host.endsWith(".adrise.io") ||
                        host.endsWith(".tubi.video") ||
                        host.equals("tubitv.com")
                    );
                case "freeshot":
                    return (
                        ((host.equals("www.freeshot.live") || host.equals("freeshot.live")) &&
                            (u.getPath().startsWith("/live-tv/") ||
                                u.getPath().matches("/embed/[A-Za-z0-9_-]+\\.php"))) ||
                        (host.equals("wideiptv.top") && u.getPath().matches("/player/[A-Za-z0-9_-]+"))
                    );
                case "zlive":
                case "dlhd":
                    return true; // Public HTTPS only; the transport separately bounds ZLive session POST.
                case "stirr":
                    return host.equals("stirr.com") && u.getPath().startsWith("/api/");
                case "tcl":
                    return host.equals("gateway-prod.ideonow.com") && u.getPath().startsWith("/api/");
                case "xumo":
                    return host.equals("valencia-app-mds.xumo.com") && u.getPath().startsWith("/v2/");
                case "lg":
                    return host.equals("api.lgchannels.com") && u.getPath().startsWith("/api/v1.0/");
                case "whale":
                    return host.equals("rlaxx.zeasn.tv") && u.getPath().startsWith("/livetv/api/");
                case "vizio":
                    return (
                        host.equals("watchfreeplus-epg-prod.smartcasttv.com") &&
                        u.getPath().startsWith("/api/")
                    );
                case "roku":
                    return host.equals("therokuchannel.roku.com") || host.equals("content.sr.roku.com");
                case "plex":
                    return (
                        host.equals("watch.plex.tv") ||
                        host.equals("plex.tv") ||
                        host.equals("epg.provider.plex.tv")
                    );
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    static void publicHost(String host) throws Exception {
        if (
            host == null || host.equalsIgnoreCase("localhost") || host.endsWith(".local")
        ) throw new IOException("Local target rejected");
        for (InetAddress ip : InetAddress.getAllByName(host)) {
            if (
                ip.isAnyLocalAddress() ||
                ip.isLoopbackAddress() ||
                ip.isLinkLocalAddress() ||
                ip.isSiteLocalAddress() ||
                ip.isMulticastAddress() ||
                (ip.getAddress().length == 16 && (ip.getAddress()[0] & 0xfe) == 0xfc)
            ) throw new IOException("Private target rejected");
        }
    }

    private static String seed() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    interface Executor {
        String call(String module, String operation, String payload) throws Exception;
    }

    interface Transport {
        JSONObject request(JSONObject request) throws Exception;
    }

    static Object run(
        String module,
        String operation,
        JSONObject row,
        Executor engine,
        Transport transport,
        NativeHttp http
    ) throws Exception {
        return run(module, operation, row, engine, transport, http, null);
    }

    static Object run(
        String module,
        String operation,
        JSONObject row,
        Executor engine,
        Transport transport,
        NativeHttp http,
        JSONObject journal
    ) throws Exception {
        return run(module, operation, row, engine, transport, http, journal, false);
    }

    static Object run(
        String module,
        String operation,
        JSONObject row,
        Executor engine,
        Transport transport,
        NativeHttp http,
        JSONObject journal,
        boolean fast
    ) throws Exception {
        JSONArray responses = new JSONArray();
        JSONObject input = new JSONObject()
            .put("now", System.currentTimeMillis())
            .put("seed", seed())
            .put("row", row == null ? new JSONObject() : row)
            .put("responses", responses);
        if (fast) input.put("fastResolve", true);
        if (journal != null) {
            input.put("history", journal.getJSONArray("history"));
            journal.put("responses", responses).put("at", input.getLong("now"));
        }
        long deadline =
            System.nanoTime() + (operation.equals("catalogue") ? 240_000_000_000L : 45_000_000_000L);
        int requests = 0,
            bytes = 0;
        for (int round = 0; round < 64; round++) {
            http.check();
            if (System.nanoTime() > deadline) throw new IOException("Provider operation timeout");
            JSONObject step;
            try {
                step = new JSONObject(engine.call(module, operation, input.toString()));
            } catch (InterruptedIOException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw failure(e, responses);
            }
            if (step.optBoolean("done")) {
                if (journal != null) journal.put("complete", true);
                return step.get("value");
            }
            JSONArray pending = step.getJSONArray("requests");
            if (pending.length() == 0) throw new IOException("Provider stalled");
            for (int i = 0; i < pending.length(); i++) {
                if (++requests > 64) throw new IOException("Provider request limit");
                http.check();
                JSONObject request = pending.getJSONObject(i),
                    record = new JSONObject().put("request", request);
                try {
                    JSONObject response = transport.request(request);
                    record.put("response", response);
                } catch (InterruptedIOException e) {
                    // A per-hop socket timeout is not cancellation. Fast DaddyLive can try its
                    // next player while the whole lookup still has time left.
                    http.check();
                    if (!fast || !(e instanceof SocketTimeoutException)) throw e;
                    record.put("error", true).put("failure", ProviderFailure.describe(e));
                } catch (Exception e) {
                    http.check();
                    record.put("error", true).put("failure", ProviderFailure.describe(e));
                }
                if (record.has("response")) bytes += record.getJSONObject("response").toString().length();
                // Below ProviderScript.MAX_BYTES (16 MB), which bounds the script's whole input.
                if (bytes > 15 * 1024 * 1024) throw new IOException("Provider aggregate limit");
                record.put("completedAt", System.currentTimeMillis());
                responses.put(record);
            }
        }
        throw new IOException("Provider continuation limit");
    }

    private static ProviderFailure failure(Exception error, JSONArray responses) {
        if (!ProviderFailure.describe(error).startsWith("Provider error (")) return ProviderFailure.from(
            error
        );
        for (int i = responses.length() - 1; i >= 0; i--) {
            JSONObject record = responses.optJSONObject(i);
            if (record == null) continue;
            JSONObject response = record.optJSONObject("response");
            if (response != null && response.optInt("status") >= 400) return ProviderFailure.http(
                response.optInt("status"),
                error
            );
            if (record.optBoolean("error")) return ProviderFailure.transport(
                record.optString("failure"),
                error
            );
        }
        return ProviderFailure.from(error);
    }

}
