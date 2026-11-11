package com.billyke2000s.moviehubtv.client;

/**
 * How speakers change a TV's sound. Pure maths with no Minecraft types, so it can be tested on its own.
 * Up to MAX_SPEAKERS speakers within RADIUS blocks of a TV count. Each one adds VOLUME_PER_SPEAKER to the
 * loudest volume (never above 100) and RANGE_PER_SPEAKER of the hearing range, and the TV can be heard
 * from each speaker as if standing at the TV.
 */
final class SpeakerMix {
    private SpeakerMix() {}

    static final int RADIUS = 8;
    static final int MAX_SPEAKERS = 4;
    static final int VOLUME_PER_SPEAKER = 5;
    static final double RANGE_PER_SPEAKER = 0.5;

    static int maxVolume(int baseVolume, int speakers) {
        return Math.min(100, baseVolume + VOLUME_PER_SPEAKER * Math.min(speakers, MAX_SPEAKERS));
    }

    static double range(int baseRange, int speakers) {
        return baseRange * (1 + RANGE_PER_SPEAKER * Math.min(speakers, MAX_SPEAKERS));
    }

    /** distToSpeakers holds the listener's distance to each counted speaker (extra entries are ignored). */
    static int volume(int baseVolume, int baseRange, double distToTv, double[] distToSpeakers) {
        int n = Math.min(distToSpeakers.length, MAX_SPEAKERS);
        double nearest = distToTv;
        for (int i = 0; i < n; i++) nearest = Math.min(nearest, distToSpeakers[i]);
        double range = range(baseRange, n);
        return (int) Math.round(maxVolume(baseVolume, n) * Math.max(0, 1 - nearest / range));
    }
}
