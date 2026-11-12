package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;

/** Server-side source groups (fallbacks sent to every player) and the menu request allowance. */
public final class SourceGroupRegression {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static JsonObject source(String url) {
        return JsonParser.parseString("{\"url\":\"" + url + "\",\"headers\":{\"Referer\":\"https://site.example/\"}}").getAsJsonObject();
    }
    private static JsonArray find(String id) {
        String json = ResolvedMedia.find("vod:moviehub:" + id + "|Fixture");
        check(!json.isEmpty(), "Issued source must resolve");
        return JsonParser.parseString(json).getAsJsonArray();
    }

    public static void run() {
        ResolvedMedia.clear();
        String group = "search-1";
        String a = ResolvedMedia.add(source("https://one.example/a.m3u8"), group);
        String b = ResolvedMedia.add(source("https://two.example/b.m3u8"), group);
        String c = ResolvedMedia.add(source("https://three.example/c.m3u8"), group);
        ResolvedMedia.add(source("https://two.example/b.m3u8"), group); // same link from another site
        ResolvedMedia.add(source("https://other.example/x.m3u8"), "search-2");

        JsonArray chosenB = find(b);
        check(chosenB.size() == 3, "Send every distinct source of the same search, without duplicates");
        check(chosenB.get(0).getAsJsonObject().get("url").getAsString().contains("two."), "The chosen source plays first");
        check(chosenB.get(1).getAsJsonObject().get("url").getAsString().contains("one."), "Fallbacks keep arrival order");
        check(chosenB.get(2).getAsJsonObject().get("url").getAsString().contains("three."), "Fallbacks keep arrival order");
        check(find(a).toString().indexOf("other.example") < 0, "Never mix in another title's sources");

        boolean[] told = {false};
        ResolvedMedia.Watcher tv = () -> told[0] = true;
        ResolvedMedia.watch("vod:moviehub:" + c + "|Fixture", tv);
        ResolvedMedia.add(source("https://four.example/d.m3u8"), group);
        ResolvedMedia.sourcesAdded("search-2");
        check(!told[0], "Only the TV's own search notifies it");
        ResolvedMedia.sourcesAdded(group);
        check(told[0] && find(c).size() == 4, "A TV hears about sources found after it was tuned");

        String big = "x".repeat(15000);
        String first = null;
        for (int i = 0; i < 20; i++) {
            JsonObject s = source("https://big" + i + ".example/v.m3u8");
            s.addProperty("info", big);
            String id = ResolvedMedia.add(s, "search-big");
            if (first == null) first = id;
        }
        String json = ResolvedMedia.find("vod:moviehub:" + first + "|Fixture");
        check(json.length() <= ResolvedMedia.MAX_CHARS && JsonParser.parseString(json).getAsJsonArray().size() >= 1,
                "Source list stays within the block-update string limit");
        ResolvedMedia.clear();

        RequestAllowance allowance = new RequestAllowance();
        UUID player = UUID.randomUUID();
        long t = 1_000_000;
        // Requests arriving together (packets bunched by the internet) are all accepted.
        for (int i = 0; i < RequestAllowance.BURST; i++) check(allowance.allow(player, t), "Accept a bunched burst");
        check(!allowance.allow(player, t), "Still limit a flood");
        check(allowance.allow(player, t + RequestAllowance.REFILL_MS), "Refill at the old sustained rate");
        // The client's steady 300 ms pace never runs out, even with jitter.
        UUID steady = UUID.randomUUID();
        for (int i = 0; i < 200; i++) check(allowance.allow(steady, t + i * 300L + (i % 2 == 0 ? 120 : -120)), "Jittered client pace is never rejected");
        System.out.println("PASS: per-title source groups, late sources reaching tuned TVs, size cap and menu request allowance");
    }

    public static void main(String[] args) { run(); }
}
