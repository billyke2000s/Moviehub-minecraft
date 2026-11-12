package com.billyke2000s.moviehubtv.catalogue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player catalogue request allowance: a short burst, refilled at one request per 250 ms (the old
 * sustained limit). Clients send one request every 300 ms, but packets sent that far apart can arrive
 * closer together over the internet; the old strict 250 ms gap then rejected menu clicks from players
 * who are not on the host's network. Server thread only.
 */
final class RequestAllowance {
    static final int BURST = 6;
    static final long REFILL_MS = 250;
    private record State(double tokens, long at) {}
    private final Map<UUID, State> players = new HashMap<>();

    boolean allow(UUID player, long now) {
        State s = players.get(player);
        double tokens = s == null ? BURST : Math.min(BURST, s.tokens + Math.max(0, now - s.at) / (double) REFILL_MS);
        boolean ok = tokens >= 1;
        players.put(player, new State(ok ? tokens - 1 : tokens, now));
        if (players.size() > 2048) players.entrySet().removeIf(e -> now - e.getValue().at > 60000);
        return ok;
    }

    void clear() { players.clear(); }
}
