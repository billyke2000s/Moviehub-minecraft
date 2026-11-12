package com.billyke2000s.moviehubtv.client;

/** Bounded per-client recovery. A stream is not a picture merely because VLC says it is playing. */
final class VideoRecovery {
    private VideoRecovery() {}
    static final int MAX_STARTS = 3;

    enum Next { RETRY_SAME, NEXT_SOURCE, GIVE_UP }

    /**
     * hadPicture: this player has shown a frame at some point. A player that ends or breaks without
     * ever showing one has failed (a website refusing the link usually looks like that); a film that
     * played and then finished has not.
     */
    static boolean failed(long ageMillis, boolean broken, boolean ended, boolean hadPicture) {
        return broken || (!hadPicture && (ended || ageMillis >= 20000));
    }

    static boolean mayStart(int starts, long now, long retryAt) {
        return starts < MAX_STARTS && now >= retryAt;
    }

    /**
     * What this client does after a failure. sourceCount is the number of fallback sources the
     * server sent for an on-demand title (0 for live TV). A source that never showed a picture on
     * this PC is skipped straight away; one that played before is retried, as is the last source.
     */
    static Next afterFailure(int sourceCount, boolean hadPicture, int starts, int sourceIndex) {
        boolean another = sourceIndex + 1 < sourceCount;
        if (another && !hadPicture) return Next.NEXT_SOURCE;
        if (starts < MAX_STARTS) return Next.RETRY_SAME;
        return another ? Next.NEXT_SOURCE : Next.GIVE_UP;
    }
}
