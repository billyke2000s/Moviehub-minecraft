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
    }

    public static void main(String[] args) { run(); }
}
