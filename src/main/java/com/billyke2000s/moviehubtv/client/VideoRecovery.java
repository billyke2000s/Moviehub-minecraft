package com.billyke2000s.moviehubtv.client;

/** Bounded per-client recovery. A stream is not a picture merely because VLC says it is playing. */
final class VideoRecovery {
    private VideoRecovery() {}
    static final int MAX_STARTS = 3;
    static boolean failed(long ageMillis, boolean broken, boolean ended, boolean uploadedFrame) {
        return broken || (!ended && !uploadedFrame && ageMillis >= 20000);
    }
    static boolean mayStart(int starts, long now, long retryAt) {
        return starts < MAX_STARTS && now >= retryAt;
    }
}
