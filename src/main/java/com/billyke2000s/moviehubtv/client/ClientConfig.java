package com.billyke2000s.moviehubtv.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/moviehubtv-client.toml */
public final class ClientConfig {
    private ClientConfig() {}

    static final String DEFAULT_PLAYLISTS =
            "https://raw.githubusercontent.com/billyke2000s/Moviehub-freelinks/main/output/theloop.m3u";
    static final String DEFAULT_PROVIDERS = "nativepluto,freelivesports,stirr,tcl,xumo,lg,whale,vizio,roku,plex,tubi,samsung,localnow,dlhd,zlive,freeshot";
    static final String DEFAULT_GUIDE =
            "https://raw.githubusercontent.com/billyke2000s/Moviehub-epg/main/epg.xml.gz";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> PLAYLISTS;
    public static final ModConfigSpec.ConfigValue<String> GUIDE_URL;
    public static final ModConfigSpec.ConfigValue<String> PROVIDERS;
    public static final ModConfigSpec.BooleanValue PUBLIC_PLAYLISTS;
    public static final ModConfigSpec.IntValue VOLUME;
    public static final ModConfigSpec.IntValue HEARING_RANGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PLAYLISTS = b.comment("M3U playlist URLs, separated by commas. Channels are numbered from 101 in this order.")
                .define("playlists", DEFAULT_PLAYLISTS);
        PUBLIC_PLAYLISTS = b.comment("Include MovieHub's Free-TV and iptv-org public playlists alongside your configured playlists.")
                .define("publicPlaylists", true);
        PROVIDERS = b.comment("MovieHub provider IDs, comma separated. Available: nativepluto,freelivesports,stirr,tcl,xumo,lg,whale,vizio,roku,plex,dlhd,tubi,samsung,localnow,zlive,freeshot.",
                        "Some sources are regional; unavailable providers do not block the others.")
                .define("providers", DEFAULT_PROVIDERS);
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
