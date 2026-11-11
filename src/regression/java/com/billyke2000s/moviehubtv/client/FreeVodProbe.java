package com.billyke2000s.moviehubtv.client;

/** Optional live check of the actual production provider code, using the runner's own region. */
public final class FreeVodProbe {
    public static void main(String[] args) throws Exception {
        String raw = PlutoClient.vodGet("/v3/vod/categories?includeItems=true&deviceType=web");
        var data = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
        System.out.println("VOD pagination: page=" + data.get("page") + " pages=" + data.get("totalPages")
                + " categories=" + data.get("totalCategories"));
        var items = VodCatalogue.catalogue(raw);
        long movies = items.stream().filter(i -> !i.series()).count();
        long shows = items.stream().filter(VodCatalogue.Item::series).count();
        long anime = items.stream().filter(VodCatalogue.Item::anime).count();
        System.out.println("Production free catalogue parser: movies=" + movies + " shows=" + shows + " anime=" + anime);
        var movie = items.stream().filter(i -> !i.series()).findFirst().orElseThrow();
        String url = PlutoClient.streamUrl(movie.streamId(), true);
        if (url == null || !PlutoClient.get(url, null).stripLeading().startsWith("#EXTM3U"))
            throw new IllegalStateException("No HLS manifest for production movie locator");
        System.out.println("PASS: production free movie locator yields an HLS manifest (runner region; not in-game)");
        var series = items.stream().filter(VodCatalogue.Item::series).findFirst().orElseThrow();
        var episodes = VodCatalogue.episodes(PlutoClient.vodGet("/v3/vod/series/" + series.id()
                + "/seasons?includeItems=true&deviceType=web"), series);
        if (episodes.isEmpty()) throw new IllegalStateException("No playable episodes parsed for sample show");
        System.out.println("PASS: production show parser returned " + episodes.size() + " playable episodes");
    }
}
