package com.billyke2000s.moviehubtv.client;

/**
 * How loud a TV is for the listener. Pure maths with no Minecraft types, so it can be tested on its own.
 * The TV's own volume (set with the remote) is scaled by the player's master volume and fades to silence
 * at the hearing range, measured from the TV or from whichever of its speakers is nearest.
 */
final class SpeakerMix {
    private SpeakerMix() {}

    static int volume(int tvVolume, int masterPercent, int range, double distToTv, double[] distToSpeakers) {
        double nearest = distToTv;
        for (double d : distToSpeakers) nearest = Math.min(nearest, d);
        double level = clamp(tvVolume) * (clamp(masterPercent) / 100.0) * Math.max(0, 1 - nearest / Math.max(1, range));
        return (int) Math.round(level);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
