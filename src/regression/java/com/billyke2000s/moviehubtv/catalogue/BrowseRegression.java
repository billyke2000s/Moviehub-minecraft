package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.*;
import java.util.*;

/** Exercises production navigation, pagination, filter validation and TMDB request construction. */
final class BrowseRegression {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static void run() throws Exception {
        var f = new BrowseCatalogue.Filters("movies", "top", 28, 8, 2024, "US", "en", "GB", "flatrate");
        String path = BrowseCatalogue.discoverPath(BrowseCatalogue.Filters.parse(f.json().toString()), "movie", 2);
        for (String part : List.of("discover/movie?", "page=2", "with_genres=28", "with_watch_providers=8", "watch_region=GB",
                "primary_release_year=2024", "with_origin_country=US", "with_original_language=en", "vote_count.gte=200", "with_watch_monetization_types=flatrate"))
            check(path.contains(part), "Combined filter missing: " + part);
        var anime = new BrowseCatalogue.Filters("anime", "popular", 35, 8, 0, "", "", "GB", "all");
        String animePath = BrowseCatalogue.discoverPath(anime, "tv", 1);
        check(animePath.contains("with_genres=16,35") && animePath.contains("with_original_language=ja"), "Keep anime constraints with provider and genre");
        check(!BrowseCatalogue.discoverPath(new BrowseCatalogue.Filters("shows", "popular", 0, 0, 2020, "", "", "GB", "all"), "tv", 1)
                .contains("primary_release_year"), "TV filters use TV API field names");
        boolean rejected = false;
        try { BrowseCatalogue.Filters.parse(f.json().toString().replace("\"GB\"", "\"GB&api_key=bad\"")); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Reject arbitrary query/path injection");
        List<String> calls = new ArrayList<>();
        BrowseCatalogue.Source source = request -> {
            calls.add(request);
            if (request.startsWith("watch/providers/movie")) {
                JsonArray providers = new JsonArray();
                for (int i=1; i<=48; i++) { JsonObject p = new JsonObject(); p.addProperty("provider_id", i); p.addProperty("provider_name", i == 8 ? "Netflix" : "Provider " + i); p.addProperty("logo_path", "/MixedCASE.jpg"); providers.add(p); }
                JsonObject out = new JsonObject(); out.add("results", providers); return out;
            }
            if (request.startsWith("genre/")) return JsonParser.parseString("{\"genres\":[{\"id\":28,\"name\":\"Action\"},{\"id\":35,\"name\":\"Comedy\"}]}");
            if (request.equals("configuration/countries")) return JsonParser.parseString("[{\"iso_3166_1\":\"GB\",\"english_name\":\"United Kingdom\"}]");
            if (request.equals("configuration/languages")) return JsonParser.parseString("[{\"iso_639_1\":\"en\",\"english_name\":\"English\"},{\"iso_639_1\":\"ja\",\"english_name\":\"Japanese\"}]");
            if (request.equals("watch/providers/regions")) return JsonParser.parseString("{\"results\":[{\"iso_3166_1\":\"GB\",\"english_name\":\"United Kingdom\"},{\"iso_3166_1\":\"US\",\"english_name\":\"United States\"}]}");
            return JsonParser.parseString("{\"total_pages\":3,\"results\":[{\"id\":42,\"title\":\"Fixture film\",\"poster_path\":\"/MixedCASE.jpg\",\"genre_ids\":[16],\"original_language\":\"ja\"},{\"id\":43,\"title\":\"Other animation\",\"genre_ids\":[16],\"original_language\":\"en\"}]}");
        };
        String state = f.json().toString();
        var menu = BrowseCatalogue.catalogue("browse-menu", state, 1, source);
        check(TmdbGateway.array(menu, "items").size() == 15, "All top-level categories and search are reachable");
        var providerPage = BrowseCatalogue.catalogue("facet-providers", state, 1, source);
        check(providerPage.get("pages").getAsInt() == 3 && TmdbGateway.array(providerPage, "items").size() == 20, "All providers use bounded menu pages");
        JsonObject netflix = TmdbGateway.array(providerPage, "items").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(o -> TmdbGateway.text(o,"title").equals("Netflix")).findFirst().orElseThrow();
        check(BrowseCatalogue.Filters.parse(TmdbGateway.text(netflix,"query")).provider() == 8, "Netflix row selects actual TMDB provider ID");
        check(calls.contains("watch/providers/movie?watch_region=GB"), "Provider inventory respects selected region");
        for (String facet : List.of("genres", "years", "countries", "languages", "regions", "access"))
            check(!TmdbGateway.array(BrowseCatalogue.catalogue("facet-" + facet, state, 1, source), "items").isEmpty(), "Facet is navigable: " + facet);
        var titles = BrowseCatalogue.catalogue("browse-list", anime.json().toString(), 1, source);
        check(TmdbGateway.array(titles,"items").size() == 2, "Anime merges TV and films while excluding non-Japanese animation");
        check(TmdbGateway.text(TmdbGateway.array(titles,"items").get(0).getAsJsonObject(), "poster").equals("/MixedCASE.jpg"), "Posters survive filtered browsing");
        System.out.println("PASS: nested browse menus, all provider pages, Netflix IDs, region, combined filters, anime and validation");

        // Audit fixes: every mode row and every filter option opens a list of titles, not the same menu again.
        for (JsonElement e : TmdbGateway.array(menu, "items")) {
            JsonObject row = e.getAsJsonObject();
            String title = TmdbGateway.text(row, "title");
            if (List.of("Popular", "Top rated", "Upcoming", "New releases / airing", "Trending this week", "Browse titles").contains(title))
                check(TmdbGateway.text(row, "next").equals("browse-list"), "Mode row opens titles: " + title);
        }
        for (String facet : List.of("genres", "providers", "years", "countries", "languages", "regions", "access"))
            for (JsonElement e : TmdbGateway.array(BrowseCatalogue.catalogue("facet-" + facet, state, 1, source), "items"))
                check(TmdbGateway.text(e.getAsJsonObject(), "next").equals("browse-list"), "Filter option opens titles: " + facet);
        // Anime trending uses anime discovery, not the general weekly chart filtered down to nothing.
        var animeTrending = new BrowseCatalogue.Filters("anime", "trending", 0, 0, 0, "", "", "GB", "all");
        String trendTv = BrowseCatalogue.discoverPath(animeTrending, "tv", 1);
        check(trendTv.startsWith("discover/tv?") && trendTv.contains("with_genres=16") && trendTv.contains("with_original_language=ja")
                && trendTv.contains("air_date.gte="), "Anime trending discovers popular airing anime");
        check(BrowseCatalogue.discoverPath(new BrowseCatalogue.Filters("movies", "trending", 0, 0, 0, "", "", "GB", "all"), "movie", 1)
                .startsWith("trending/movie/week"), "Other tabs keep TMDB's weekly chart");
        // Anime mixes TV and film genre lists: map to the equivalent, or skip a type with no equivalent.
        var animeAction = new BrowseCatalogue.Filters("anime", "popular", 10759, 0, 0, "", "", "GB", "all");
        check(BrowseCatalogue.discoverPath(animeAction, "tv", 1).contains("with_genres=16,10759"), "TV genre kept for anime TV");
        check(BrowseCatalogue.discoverPath(animeAction, "movie", 1).contains("with_genres=16,28"), "TV Action & Adventure maps to film Action");
        var animeHorror = new BrowseCatalogue.Filters("anime", "popular", 27, 0, 0, "", "", "GB", "all");
        check(BrowseCatalogue.discoverPath(animeHorror, "tv", 1) == null, "Film-only genre skips TV instead of an empty query");
        calls.clear();
        BrowseCatalogue.catalogue("browse-list", animeHorror.json().toString(), 1, source);
        check(calls.size() == 1 && calls.get(0).startsWith("discover/movie?"), "Only the type with that genre is queried");
        check(BrowseCatalogue.discoverPath(new BrowseCatalogue.Filters("shows", "popular", 10765, 0, 0, "", "", "GB", "all"), "tv", 1)
                .contains("with_genres=10765"), "Shows keep their own genre IDs");
        System.out.println("PASS: menu audit - options open titles, anime trending, anime genre equivalents");
    }
}
