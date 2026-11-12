package com.billyke2000s.moviehubtv.client;

import static com.billyke2000s.moviehubtv.client.VideoRecovery.Next.*;

/** Per-PC source fallback decisions and the server's source-list format. No Minecraft or VLC needed. */
public final class MediaFallbackRegression {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void run() {
        // A source that never showed a picture on this PC is skipped at once when another exists.
        check(VideoRecovery.afterFailure(5, false, 1, 0) == NEXT_SOURCE, "Skip a source this PC cannot play");
        check(VideoRecovery.afterFailure(5, false, 1, 3) == NEXT_SOURCE, "Keep working down the list");
        // A source that was playing gets its retries before moving on.
        check(VideoRecovery.afterFailure(5, true, 1, 0) == RETRY_SAME, "Retry a source that was playing");
        check(VideoRecovery.afterFailure(5, true, VideoRecovery.MAX_STARTS, 0) == NEXT_SOURCE, "Move on once its retries are used");
        // The last source is retried, then the client gives up (and resumes if more sources arrive).
        check(VideoRecovery.afterFailure(5, false, 1, 4) == RETRY_SAME, "Retry the last source");
        check(VideoRecovery.afterFailure(5, false, VideoRecovery.MAX_STARTS, 4) == GIVE_UP, "Give up after the last source");
        // Live TV (no source list) keeps its old bounded retry.
        check(VideoRecovery.afterFailure(0, false, 1, 0) == RETRY_SAME, "Live TV retries");
        check(VideoRecovery.afterFailure(0, false, VideoRecovery.MAX_STARTS, 0) == GIVE_UP, "Live TV retry is bounded");

        var list = MediaSources.parse("[{\"url\":\"https://a.example/1.m3u8\"},{\"url\":\"https://b.example/2.m3u8\"}]");
        check(list.size() == 2 && list.get(1).get("url").getAsString().startsWith("https://b."), "Read the server's ordered source list");
        check(MediaSources.parse("{\"url\":\"https://a.example/1.m3u8\"}").size() == 1, "Accept a single source object");
        check(MediaSources.parse("").isEmpty() && MediaSources.parse("not json").isEmpty()
                && MediaSources.parse("[1,\"x\"]").isEmpty(), "Unreadable lists are empty, not a crash");
        System.out.println("PASS: per-PC source fallback order, retry bounds and source-list parsing");

        String master = String.join("\n",
                "#EXTM3U",
                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Japanese\",LANGUAGE=\"ja\",DEFAULT=YES,AUTOSELECT=YES,URI=\"ja.m3u8\"",
                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"English\",LANGUAGE=\"en\",DEFAULT=NO,URI=\"en.m3u8\"",
                "#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"English\",LANGUAGE=\"en\",DEFAULT=NO,URI=\"en.vtt.m3u8\"",
                "#EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO=\"aud\",SUBTITLES=\"subs\"",
                "video.m3u8");
        String out = EnglishTracks.prefer(master);
        String[] l = out.split("\n");
        check(l[1].contains("DEFAULT=NO") && l[1].contains("AUTOSELECT=NO"), "Original-language audio is no longer the default");
        check(l[2].contains("DEFAULT=YES") && l[2].contains("AUTOSELECT=YES") && l[2].contains("URI=\"en.m3u8\""), "English audio becomes the default");
        check(l[3].contains("DEFAULT=NO"), "No forced subtitles over English audio");
        check(l[5].equals("video.m3u8") && l.length == 6, "Rest of the playlist is unchanged");
        String noEnglishAudio = master.replace("NAME=\"English\",LANGUAGE=\"en\",DEFAULT=NO,URI=\"en.m3u8\"",
                "NAME=\"Korean\",LANGUAGE=\"ko\",DEFAULT=NO,URI=\"ko.m3u8\"");
        String subs = EnglishTracks.prefer(noEnglishAudio).split("\n")[3];
        check(subs.contains("DEFAULT=YES") && subs.contains("AUTOSELECT=YES"), "English subtitles switch on when there is no English audio");
        check(EnglishTracks.prefer("#EXTM3U\n#EXTINF:4,\nseg1.ts").equals("#EXTM3U\n#EXTINF:4,\nseg1.ts"), "Media playlists untouched");
        check(String.join(" ", EnglishTracks.vlcOptions()).contains(":audio-language=en"), "VLC prefers English audio tracks");
        System.out.println("PASS: English audio/subtitle preference in HLS playlists and VLC options");
    }

    public static void main(String[] args) { run(); }
}
