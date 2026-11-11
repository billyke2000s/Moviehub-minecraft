package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.*;
import java.time.LocalDate;
import java.util.*;

/** Validated browse state: clients send filter values, never TMDB paths or credentials. */
public final class BrowseCatalogue {
    private BrowseCatalogue() {}
    interface Source { JsonElement get(String path) throws Exception; }
    private static final Map<String, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();
    public record Filters(String section, String mode, int genre, int provider, int year,
                          String country, String language, String region, String access) {
        public static Filters parse(String json) {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String section = value(o, "section", "movies");
            String mode = value(o, "mode", "popular");
            String access = value(o, "access", "all");
            if (!Set.of("movies", "shows", "anime").contains(section)
                    || !Set.of("popular", "trending", "top", "upcoming", "recent").contains(mode)
                    || !Set.of("all", "flatrate", "free", "ads", "rent", "buy").contains(access)) throw new IllegalArgumentException();
            int genre = TmdbGateway.integer(o, "genre", 0), provider = TmdbGateway.integer(o, "provider", 0);
            int year = TmdbGateway.integer(o, "year", 0);
            String country = value(o, "country", ""), language = value(o, "language", ""), region = value(o, "region", "GB");
            if (genre < 0 || genre > 100000 || provider < 0 || provider > 100000
                    || (year != 0 && (year < 1870 || year > LocalDate.now().getYear() + 5))
                    || !country.matches("([A-Z]{2})?") || !region.matches("[A-Z]{2}") || !language.matches("([a-z]{2})?")) throw new IllegalArgumentException();
            return new Filters(section, mode, genre, provider, year, country, language, region, access);
        }
        public JsonObject json() { return new Gson().toJsonTree(this).getAsJsonObject(); }
        public List<String> types() { return section.equals("anime") ? List.of("tv", "movie") : List.of(section.equals("movies") ? "movie" : "tv"); }
    }
    private static String value(JsonObject o, String key, String fallback) {
        String v = TmdbGateway.text(o, key); return v.isEmpty() ? fallback : v;
    }
    private static JsonObject row(String title, String subtitle, String route, JsonObject state) {
        JsonObject o = new JsonObject(); o.addProperty("kind", "browse"); o.addProperty("title", title);
        o.addProperty("subtitle", subtitle); o.addProperty("next", route); o.addProperty("query", state.toString()); return o;
    }
    private static JsonObject option(Filters f, String field, Object value, String title) {
        JsonObject state = f.json();
        boolean selected = state.has(field) && state.get(field).getAsString().equals(value.toString());
        if (value instanceof Number n) state.addProperty(field, n); else state.addProperty(field, value.toString());
        return row(title, selected ? "Selected - choose Browse titles to view results" : "Select, then Browse titles; filters can be combined", "browse-menu", state);
    }
    public static JsonObject catalogue(String route, String query, int page, String key) throws Exception {
        return catalogue(route, query, page, path -> TmdbGateway.getJson(path, key));
    }
    static JsonObject catalogue(String route, String query, int page, Source source) throws Exception {
        Filters f = Filters.parse(query); JsonArray rows = new JsonArray();
        if (route.equals("browse-list")) return titles(f, page, source);
        if (route.equals("browse-menu")) {
            rows.add(row("Browse titles", summary(f), "browse-list", f.json()));
            rows.add(row("Popular", "Browse by popularity", "browse-menu", changed(f, "mode", "popular")));
            JsonObject trending = new Filters(f.section, "trending", 0, 0, 0, "", "", f.region, "all").json();
            rows.add(row("Trending this week", "Weekly trending; resets other filters", "browse-list", trending));
            rows.add(row("Top rated", "Highest rated; at least 200 votes", "browse-menu", changed(f, "mode", "top")));
            rows.add(row("Upcoming", "Future releases / new series", "browse-menu", changed(f, "mode", "upcoming")));
            rows.add(row("New releases / airing", "Recent movies and currently airing series", "browse-menu", changed(f, "mode", "recent")));
            for (String facet : List.of("genres", "providers", "years", "countries", "languages", "regions", "access")) {
                JsonObject item = row(switch (facet) { case "regions" -> "Provider region"; case "access" -> "Availability type"; default -> Character.toUpperCase(facet.charAt(0)) + facet.substring(1); }, summary(f), "facet-" + facet, f.json());
                if (facet.equals("providers")) item.addProperty("overview", "Availability by JustWatch via TMDB");
                rows.add(item);
            }
            rows.add(row("Clear filters", "Reset this tab; keep provider region", "browse-menu", new Filters(f.section, "popular", 0, 0, 0, "", "", f.region, "all").json()));
            JsonObject search = new JsonObject(); search.addProperty("kind", "search"); search.addProperty("title", "Search titles / TMDB ID"); search.addProperty("subtitle", "Search all titles in this tab"); rows.add(search);
        } else if (route.equals("facet-years")) {
            rows.add(option(f, "year", 0, "All years"));
            for (int y = LocalDate.now().getYear() + 2; y >= 1870; y--) rows.add(option(f, "year", y, String.valueOf(y)));
        } else if (route.equals("facet-access")) {
            String[][] options = {{"all", "All availability"}, {"flatrate", "Subscription"}, {"free", "Free"}, {"ads", "Free with adverts"}, {"rent", "Rent"}, {"buy", "Buy"}};
            for (String[] o : options) rows.add(option(f, "access", o[0], o[1]));
        } else if (route.equals("facet-genres") || route.equals("facet-providers")) {
            boolean providers = route.equals("facet-providers");
            String field = providers ? "provider" : "genre";
            rows.add(option(f, field, 0, providers ? "All providers" : "All genres"));
            Map<Integer, JsonObject> all = new LinkedHashMap<>();
            for (String type : f.types()) {
                JsonObject data = source.get(providers ? "watch/providers/" + type + "?watch_region=" + f.region : "genre/" + type + "/list").getAsJsonObject();
                for (JsonElement e : TmdbGateway.array(data, providers ? "results" : "genres")) {
                    JsonObject o = e.getAsJsonObject(); int id = TmdbGateway.integer(o, providers ? "provider_id" : "id", 0);
                    if (NAMES.size() < 10000) NAMES.put(field + id, TmdbGateway.text(o, providers ? "provider_name" : "name"));
                    JsonObject item = option(f, field, id, TmdbGateway.text(o, providers ? "provider_name" : "name"));
                    if (providers) { item.addProperty("poster", TmdbGateway.text(o, "logo_path")); item.addProperty("overview", "Availability by JustWatch via TMDB"); }
                    all.putIfAbsent(id, item);
                }
            }
            all.values().stream().sorted(Comparator.comparing(o -> TmdbGateway.text(o, "title"), String.CASE_INSENSITIVE_ORDER)).forEach(rows::add);
        } else if (Set.of("facet-countries", "facet-languages", "facet-regions").contains(route)) {
            boolean languages = route.equals("facet-languages"), regions = route.equals("facet-regions");
            String field = languages ? "language" : regions ? "region" : "country";
            if (!regions) rows.add(option(f, field, "", "All " + (languages ? "languages" : "countries")));
            JsonElement data = regions ? source.get("watch/providers/regions").getAsJsonObject().get("results")
                    : source.get("configuration/" + (languages ? "languages" : "countries"));
            List<JsonObject> options = new ArrayList<>();
            for (JsonElement e : data.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject(); String code = TmdbGateway.text(o, languages ? "iso_639_1" : "iso_3166_1");
                if (!code.matches(languages ? "[a-z]{2}" : "[A-Z]{2}")) continue;
                if (languages && f.section.equals("anime") && !code.equals("ja")) continue;
                options.add(option(f, field, code, TmdbGateway.text(o, "english_name") + " (" + code + ")"));
            }
            options.sort(Comparator.comparing(o -> TmdbGateway.text(o, "title"))); options.forEach(rows::add);
        } else throw new IllegalArgumentException();
        // Paginate menu options too: the bounded packet must fit even with hundreds of providers.
        int pages = Math.max(1, (rows.size() + 19) / 20), current = Math.max(1, Math.min(pages, page));
        JsonArray slice = new JsonArray(); for (int i = (current - 1) * 20; i < Math.min(current * 20, rows.size()); i++) slice.add(rows.get(i));
        return response(slice, current, pages, summary(f));
    }
    static JsonObject changed(Filters f, String field, String value) { JsonObject o = f.json(); o.addProperty(field, value); return o; }
    static String summary(Filters f) {
        return f.mode + " | " + f.region + (f.provider > 0 ? " | " + NAMES.getOrDefault("provider" + f.provider, "provider selected") : "")
                + (f.genre > 0 ? " | " + NAMES.getOrDefault("genre" + f.genre, "genre selected") : "") + (f.year > 0 ? " | " + f.year : "")
                + (f.country.isEmpty() ? "" : " | " + f.country) + (f.language.isEmpty() ? "" : " | " + f.language)
                + (f.access.equals("all") ? "" : " | " + f.access);
    }
    public static String discoverPath(Filters f, String type, int page) {
        if (!f.types().contains(type)) throw new IllegalArgumentException();
        if (f.mode.equals("trending")) return "trending/" + type + "/week?page=" + Math.max(1, Math.min(500, page));
        StringBuilder p = new StringBuilder("discover/" + type + "?include_adult=false&page=" + Math.max(1, Math.min(500, page)));
        p.append("&sort_by=").append(f.mode.equals("top") ? "vote_average.desc&vote_count.gte=200" : "popularity.desc");
        if (f.section.equals("anime")) p.append("&with_genres=16").append(f.genre > 0 && f.genre != 16 ? "," + f.genre : "").append("&with_original_language=ja");
        else { if (f.genre > 0) p.append("&with_genres=").append(f.genre); if (!f.language.isEmpty()) p.append("&with_original_language=").append(f.language); }
        if (f.provider > 0) p.append("&with_watch_providers=").append(f.provider);
        p.append("&watch_region=").append(f.region);
        if (!f.access.equals("all")) p.append("&with_watch_monetization_types=").append(f.access);
        if (f.year > 0) p.append(type.equals("movie") ? "&primary_release_year=" : "&first_air_date_year=").append(f.year);
        if (!f.country.isEmpty()) p.append("&with_origin_country=").append(f.country);
        String date = type.equals("movie") ? "primary_release_date" : "first_air_date";
        if (f.mode.equals("upcoming")) p.append("&").append(date).append(".gte=").append(LocalDate.now().plusDays(1));
        if (f.mode.equals("recent")) {
            date = type.equals("movie") ? "primary_release_date" : "air_date";
            p.append("&").append(date).append(".gte=").append(LocalDate.now().minusDays(type.equals("movie") ? 90 : 7));
            p.append("&").append(date).append(".lte=").append(LocalDate.now());
        }
        return p.toString();
    }
    private static JsonObject titles(Filters f, int page, Source source) throws Exception {
        JsonArray items = new JsonArray(); int pages = 1;
        for (String type : f.types()) {
            JsonObject data = source.get(discoverPath(f, type, page)).getAsJsonObject();
            pages = Math.max(pages, TmdbGateway.integer(data, "total_pages", 1));
            for (JsonElement e : TmdbGateway.array(data, "results")) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("adult") && o.get("adult").getAsBoolean()) continue;
                if (f.section.equals("anime") && (!TmdbGateway.text(o, "original_language").equals("ja")
                        || !TmdbGateway.array(o, "genre_ids").contains(new JsonPrimitive(16)))) continue;
                items.add(TmdbGateway.item(o, type));
            }
        }
        return response(items, page, Math.min(500, pages), summary(f));
    }
    private static JsonObject response(JsonArray items, int page, int pages, String label) {
        JsonObject out = new JsonObject(); out.add("items", items); out.addProperty("page", page);
        out.addProperty("pages", pages); out.addProperty("label", label);
        out.addProperty("attribution", "Provider availability: JustWatch via TMDB"); return out;
    }
}
