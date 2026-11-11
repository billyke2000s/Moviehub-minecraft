package com.billyke2000s.moviehubtv;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/** Unsynchronised COMMON config: credentials remain on the machine hosting the world. */
public final class ServerConfig {
    private ServerConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> TMDB_KEY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PLAYLISTS;
    public static final ModConfigSpec.ConfigValue<String> PYTHON;
    static {
        var b = new ModConfigSpec.Builder();
        TMDB_KEY = b.comment("TMDB v3 API key OR API Read Access Token. Set only on the server; never sent to players.")
                .define("tmdbKey", "");
        PYTHON = b.comment("Python 3.10+ executable on this server. Blank auto-detects python3 / python.",
                        "The bundled MovieHub resolver and portable dependencies extract into a private config directory.")
                .define("pythonCommand", "");
        PLAYLISTS = b.comment("Additional public M3U URLs sent to players. Paste your MovieHub channel export URL here to mirror it.",
                        "Do not put private account/password URLs here: players must receive the URL to download the playlist.")
                .defineListAllowEmpty("channelPlaylists", List.<String>of(), o -> o instanceof String s && s.startsWith("https://") && s.length() <= 2048);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try { return value.get(); } catch (IllegalStateException e) { return fallback; }
    }
}
