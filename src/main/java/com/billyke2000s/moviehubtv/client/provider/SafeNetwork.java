package com.billyke2000s.moviehubtv.client.provider;

import java.net.URI;

/** Network-only media: never lets a playlist launch local files, native options or other protocols. */
public final class SafeNetwork {
    public static final String USER_AGENT = "Mozilla/5.0 MovieHubTV-Minecraft";

    private SafeNetwork() {}

    public static String url(String value) {
        if (value == null || value.length() > 16384 || value.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid network URL");
        }
        URI uri = URI.create(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || value.contains("|")) {
            throw new IllegalArgumentException("Invalid network URL");
        }
        return uri.toString();
    }

    public static void publicUrl(String value) throws Exception {
        ProviderBroker.publicHost(URI.create(url(value)).getHost());
    }
}
