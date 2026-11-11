package com.billyke2000s.moviehubtv.client;

/**
 * How loud a TV is for the listener. Pure maths with no Minecraft types, so it can be tested on its own.
 * The TV's own volume (set with the remote) is scaled by the player's master volume and fades to silence
 * at the TV's hearing range. Speakers have a separate room-sized range and full-volume zone.
 */
final class SpeakerMix {
    private SpeakerMix() {}

    static int volume(int tvVolume, int masterPercent, int tvRange, int speakerRange, int fullVolumeRange,
                      double distToTv, double[] distToSpeakers) {
        double gain = fade(distToTv, tvRange, 0);
        for (double distance : distToSpeakers) gain = Math.max(gain, fade(distance, speakerRange, fullVolumeRange));
        double level = clamp(tvVolume) * (clamp(masterPercent) / 100.0) * gain;
        return (int) Math.round(level);
    }

    /** Keep an existing player alive near a remote speaker, including beyond the TV's old 64-block cutoff. */
    static boolean keepPlaying(double distToTv, int speakerRange, double[] distToSpeakers) {
        if (distToTv < 64) return true;
        for (double distance : distToSpeakers) if (distance < Math.max(1, speakerRange)) return true;
        return false;
    }

    private static double fade(double distance, int range, int fullVolumeRange) {
        if (!Double.isFinite(distance)) return 0;
        double end = Math.max(1, range);
        double full = Math.max(0, Math.min(end - 1, fullVolumeRange));
        return Math.max(0, Math.min(1, (end - Math.max(0, distance)) / (end - full)));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}

