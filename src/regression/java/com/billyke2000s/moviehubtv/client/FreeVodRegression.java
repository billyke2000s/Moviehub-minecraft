package com.billyke2000s.moviehubtv.client;

/** Offline provider fixtures. Exercises the production parser without launching Minecraft or VLC. */
public final class FreeVodRegression {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        String movie = "111111111111111111111111", series = "222222222222222222222222";
        var items = VodCatalogue.catalogue("""
                {"categories":[
                  {"name":"Films","items":[
                    {"_id":"111111111111111111111111","name":"A Film","type":"movie","summary":"A story",
                     "stitched":{"urls":[{"url":"https://service-stitcher.clusters.pluto.tv/stitch/hls/episode/aaaaaaaaaaaaaaaaaaaaaaaa/master.m3u8?oldToken=1"}]}},
                    {"_id":"bad","name":"Invalid","type":"movie"},
                    {"_id":"333333333333333333333333","name":"Office","type":"movie","plutoOfficeOnly":true},
                    {"_id":"444444444444444444444444","name":"Unplayable trailer","type":"clip"}]},
                  {"name":"Shows","items":[{"_id":"222222222222222222222222","name":"A Show","type":"series"}]},
                  {"name":"Anime","items":[{"_id":"222222222222222222222222","name":"A Show","type":"series"}]},
                  {"name":"Kids animation","items":[{"_id":"555555555555555555555555","name":"Cartoon","type":"movie",
                    "stitched":{"urls":[{"url":"https://service-stitcher.clusters.pluto.tv/stitch/hls/episode/555555555555555555555555/master.m3u8"}]}}]},
                  {"name":"Unavailable","items":[{"_id":"999999999999999999999999","name":"No video","type":"movie"}]}
                ]}
                """);
        check(items.size() == 3, "Deduplicate carousel titles; omit invalid IDs, office-only entries and clips");
        var show = items.stream().filter(i -> i.id().equals(series)).findFirst().orElseThrow();
        check(show.series() && show.anime(), "Anime category survives duplicate appearances");
        check(!items.get(2).anime(), "Generic children's animation is not automatically anime");
        check(items.get(0).summary().equals("A story"), "Retain provider synopsis");
        var episodes = VodCatalogue.episodes("""
                [{"number":2,"episodes":[{"_id":"666666666666666666666666","name":"Later","number":3,
                   "stitched":{"urls":[{"url":"https://service-stitcher.clusters.pluto.tv/stitch/hls/episode/666666666666666666666666/master.m3u8"}]}}]},
                 {"number":1,"episodes":[{"_id":"777777777777777777777777","name":"First","number":1,
                   "stitched":{"urls":[{"url":"https://service-stitcher.clusters.pluto.tv/stitch/hls/episode/777777777777777777777777/master.m3u8"}]}},
                  {"_id":"bad","name":"Broken","number":2}]}]
                """, show);
        check(episodes.size() == 2 && episodes.get(0).season() == 1 && episodes.get(1).episode() == 3,
                "Episodes are browsable in season/episode order");
        check(episodes.get(0).playbackId().startsWith("vod:pluto:777777777777777777777777|"), "Play an episode, not its series ID");
        check(VodCatalogue.episodes("{\"seasons\":[]}", show).isEmpty(), "Empty regional seasons are supported");
        check(VodCatalogue.normal("Pokémon!").equals(VodCatalogue.normal("Pokemon")), "TMDB title matching handles accents");
        check(items.get(0).id().equals(movie), "Preserve provider identity across tabs");
        check(items.get(0).playbackId().startsWith("vod:pluto:aaaaaaaaaaaaaaaaaaaaaaaa|"),
                "Movie uses the actual provider episode ID, not its metadata ID or an expired token");
        for (float width : new float[]{192, 271, 448}) {
            float h = 160, button = (width - 10) / 4;
            int[] expected = {8, 7, 9, 1};
            for (int i = 0; i < 4; i++) check(PlayerControls.hit(width, h, 5 + button * (i + 0.5f), h - 12) == expected[i],
                    "Existing popup control hit targets match on every supported screen width");
            check(PlayerControls.hit(width, h, width / 2, h / 2) == 1, "Clicking the picture keeps the existing Guide action");
        }
        System.out.println("PASS: free movies, shows, anime classification, episodes, locators and title matching");
    }
}
