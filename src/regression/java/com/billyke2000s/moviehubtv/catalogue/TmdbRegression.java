package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.JsonParser;

public final class TmdbRegression {
    public static void main(String[] args) throws Exception {
        BrowseRegression.run();
        var raw = JsonParser.parseString("""
                {"id":10331,"title":"Night of the Living Dead","release_date":"1968-10-04",
                 "poster_path":"/tmdbPoster.jpg","overview":"Fixture synopsis"}
                """).getAsJsonObject();
        var movie = TmdbGateway.item(raw, "movie");
        if (!movie.get("poster").getAsString().equals("/tmdbPoster.jpg")) throw new AssertionError("TMDB poster must reach the client");
        if (movie.get("id").getAsInt() != 10331) throw new AssertionError("Preserve TMDB identity for real source lookup");
        if (!movie.get("subtitle").getAsString().startsWith("1968")) throw new AssertionError("Preserve release year");
        var source = JsonParser.parseString("{\"url\":\"https://media.example/movie.m3u8\",\"headers\":{\"Referer\":\"https://site.example/\"}}").getAsJsonObject();
        String issued = ResolvedMedia.add(source);
        if (ResolvedMedia.find("vod:moviehub:" + issued + "|Fixture").isEmpty()) throw new AssertionError("Issued sources tune successfully");
        if (!ResolvedMedia.find("vod:moviehub:unissued|Fixture").isEmpty()) throw new AssertionError("Reject client-invented source IDs");
        ResolvedMedia.clear();
        System.out.println("PASS: TMDB posters, title identity and server-issued free-source selection");
        SourceGroupRegression.run();
    }
}
