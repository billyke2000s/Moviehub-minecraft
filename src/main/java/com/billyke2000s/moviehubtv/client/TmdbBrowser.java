package com.billyke2000s.moviehubtv.client;

import com.google.gson.*;
import java.util.*;

/** Per-TV navigation. All metadata and free-provider discovery requests go to the host. */
final class TmdbBrowser {
    static final int ROWS = 4, STEP = 31, HEIGHT = 29;
    List<JsonObject> items = new ArrayList<>();
    int scroll, page = 1, pages = 1, id, season, generation;
    String route = "", query = "", status = "";
    boolean loading;
    boolean sourcesFound;
    String browseState = "";
    private final Deque<Frame> history = new ArrayDeque<>();
    private record Frame(List<JsonObject> items, int scroll, int page, int pages, int id, int season,
                         String route, String query, String status) {}
    void root(int tab) {
        cancelSearch();
        history.clear();
        browseState = new com.billyke2000s.moviehubtv.catalogue.BrowseCatalogue.Filters(
                tab == 1 ? "movies" : tab == 2 ? "shows" : "anime", "popular", 0, 0, 0, "", "", "GB", "all").json().toString();
        request("browse-menu", 0, 0, 1, browseState, false);
    }
    void categories() { cancelSearch(); request("browse-menu", 0, 0, 1, browseState, true); }
    private void request(String next, int selected, int seasonNumber, int pageNumber, String search, boolean remember) {
        if (remember) history.push(new Frame(List.copyOf(items), scroll, page, pages, id, season, route, query, status));
        route = next; id = selected; season = seasonNumber; page = pageNumber; query = search;
        if (next.startsWith("browse-") || next.startsWith("facet-")) browseState = search;
        items = new ArrayList<>(); scroll = 0; pages = 1; loading = true; sourcesFound = false;
        status = next.equals("resolve") ? "Starting MovieHub's free-source search..." : "Loading TMDB...";
        int version = ++generation;
        CatalogueClient.request(next, selected, seasonNumber, pageNumber, search, data -> {
            if (version != generation) return;
            loading = data.has("more") && data.get("more").getAsBoolean();
            if (data.has("error")) { status = text(data, "error"); return; }
            if (data.has("status")) status = text(data, "status");
            if (data.has("providers")) {
                for (JsonElement e : data.getAsJsonArray("providers")) {
                    JsonObject service = new JsonObject();
                    service.addProperty("kind", "provider"); service.addProperty("title", e.getAsString());
                    service.addProperty("subtitle", "Searching..."); service.addProperty("poster", text(data, "poster"));
                    items.add(service);
                }
            }
            if (data.has("provider") && !sourcesFound) {
                for (JsonObject service : items) if (text(service, "title").equals(text(data, "provider")))
                    service.addProperty("subtitle", text(data, "providerStatus"));
            }
            if (data.has("source")) {
                if (!sourcesFound) { items.clear(); scroll = 0; sourcesFound = true; }
                items.add(data.getAsJsonObject("source"));
            }
            if (data.has("items")) {
                items = new ArrayList<>();
                for (JsonElement e : data.getAsJsonArray("items")) if (e.isJsonObject()) items.add(e.getAsJsonObject());
                pages = integer(data, "pages", 1);
                page = integer(data, "page", page);
                status = items.isEmpty() ? "No TMDB results - change filters or region" : "TMDB - page " + page + " / " + pages;
            }
            if (!loading && route.equals("resolve") && !sourcesFound && !data.has("error"))
                status = "No playable sources returned by MovieHub's free services";
        });
    }
    String activate(int index) {
        if (index < 0 || index >= items.size()) return null;
        JsonObject item = items.get(index);
        String kind = text(item, "kind");
        if (kind.equals("search")) return "moviehub-search";
        if (kind.equals("browse")) { request(text(item, "next"), 0, 0, 1, text(item, "query"), true); return null; }
        if (kind.equals("provider")) { status = text(item, "title") + ": " + text(item, "subtitle"); return null; }
        int selected = integer(item, "id", 0);
        if (kind.equals("source")) return text(item, "play");
        if (kind.equals("tv")) request("seasons", selected, 0, 1, "", true);
        else if (kind.equals("season")) request("episodes", selected, integer(item, "season", 0), 1, "", true);
        else if (kind.equals("episode")) request("resolve", selected, integer(item, "season", 0), integer(item, "episode", 1), "tv", true);
        else request("resolve", selected, -1, -1, "movie", true);
        return null;
    }
    boolean back() {
        if (history.isEmpty()) return false;
        cancelSearch();
        Frame f = history.pop(); generation++;
        items = new ArrayList<>(f.items); scroll = f.scroll; page = f.page; pages = f.pages;
        id = f.id; season = f.season; route = f.route; query = f.query; status = f.status; loading = false;
        if (route.startsWith("browse-") || route.startsWith("facet-")) browseState = query;
        return true;
    }
    boolean canBack() { return !history.isEmpty(); }
    void scrollBy(int amount) {
        int max = Math.max(0, items.size() - ROWS);
        if (scroll + amount > max && page < pages && !loading) request(route, id, season, page + 1, query, false);
        else if (scroll + amount < 0 && page > 1 && !loading) request(route, id, season, page - 1, query, false);
        else scroll = Math.max(0, Math.min(max, scroll + amount));
    }
    void search(int tab, String value) {
        cancelSearch();
        if (value.matches("tmdb:(movie|tv):[1-9][0-9]*")) {
            String[] p = value.split(":");
            try { request(p[1], Integer.parseInt(p[2]), 0, 1, "", true); }
            catch (NumberFormatException e) { status = "TMDB ID is too large"; }
        } else request(tab == 1 ? "movies" : tab == 2 ? "shows" : "anime", 0, 0, 1, value, false);
    }
    void cancelSearch() {
        if (route.equals("resolve") && loading) CatalogueClient.request("cancel", 0, ignored -> {});
    }
    static String text(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }
    static int integer(JsonObject o, String k, int d) { try { return o.has(k) ? o.get(k).getAsInt() : d; } catch (Exception e) { return d; } }
}
