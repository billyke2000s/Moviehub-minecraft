package com.billyke2000s.moviehubtv.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/moviehubtv-client.toml */
public final class ClientConfig {
    private ClientConfig() {}

    static final String DEFAULT_PLAYLISTS =
            "https://raw.githubusercontent.com/billyke2000s/Moviehub-freelinks/main/output/theloop.m3u";
    /**
     * Free-TV's free channels provided by broadcasters themselves (UK first). A separate setting so that
     * existing config files, which already store "playlists", still pick it up.
     */
    static final String DEFAULT_EXTRA_PLAYLISTS = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8";
    static final String DEFAULT_GUIDE =
            "https://raw.githubusercontent.com/billyke2000s/Moviehub-epg/main/epg.xml.gz";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> PLAYLISTS;
    public static final ModConfigSpec.ConfigValue<String> EXTRA_PLAYLISTS;
    public static final ModConfigSpec.BooleanValue PLUTO_NATIVE;
    public static final ModConfigSpec.ConfigValue<String> GUIDE_URL;
    public static final ModConfigSpec.IntValue VOLUME;
    public static final ModConfigSpec.IntValue HEARING_RANGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PLAYLISTS = b.comment("M3U playlist URLs, separated by commas. Channels are numbered from 101 in this order.")
                .define("playlists", DEFAULT_PLAYLISTS);
        PLUTO_NATIVE = b.comment("Load Pluto TV straight from Pluto for your region (with a fresh session token).",
                        "Turn off to use only the Pluto entries in the playlists.")
                .define("plutoNative", true);
        EXTRA_PLAYLISTS = b.comment("More M3U playlists, added after the ones above. Clear it to hide Free-TV's channels.")
                .define("extraPlaylists", DEFAULT_EXTRA_PLAYLISTS);
        GUIDE_URL = b.comment("XMLTV guide (.xml or .xml.gz) used for now/next listings.")
                .define("guide", DEFAULT_GUIDE);
        VOLUME = b.comment("TV volume when standing next to it, 0-100.")
                .defineInRange("volume", 80, 0, 100);
        HEARING_RANGE = b.comment("Blocks away at which a TV becomes silent.")
                .defineInRange("hearingRange", 16, 4, 64);
        SPEC = b.build();
    }

    static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
