package com.billyke2000s.moviehubtv.client;

/** Offline provider fixtures. Exercises the production parser without launching Minecraft or VLC. */
public final class FreeVodRegression {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        String movie = "111111111111111111111111", series = "222222222222222222222222";
        var items = VodCatalogue.catalogue("""
                {"categories":[
                  {"name":"Films","items":[
                    {"_id":"111111111111111111111111","name":"A Film","type":"movie","summary":"A story"},
                    {"_id":"bad","name":"Invalid","type":"movie"},
                    {"_id":"333333333333333333333333","name":"Office","type":"movie","plutoOfficeOnly":true},
                    {"_id":"444444444444444444444444","name":"Unplayable trailer","type":"clip"}]},
                  {"name":"Shows","items":[{"_id":"222222222222222222222222","name":"A Show","type":"series"}]},
                  {"name":"Anime","items":[{"_id":"222222222222222222222222","name":"A Show","type":"series"}]},
                  {"name":"Kids animation","items":[{"_id":"555555555555555555555555","name":"Cartoon","type":"movie"}]}
                ]}
                """);
        check(items.size() == 3, "Deduplicate carousel titles; omit invalid IDs, office-only entries and clips");
        var show = items.stream().filter(i -> i.id().equals(series)).findFirst().orElseThrow();
        check(show.series() && show.anime(), "Anime category survives duplicate appearances");
        check(!items.get(2).anime(), "Generic children's animation is not automatically anime");
        check(items.get(0).summary().equals("A story"), "Retain provider synopsis");
        var episodes = VodCatalogue.episodes("""
                [{"number":2,"episodes":[{"_id":"666666666666666666666666","name":"Later","number":3}]},
                 {"number":1,"episodes":[{"_id":"777777777777777777777777","name":"First","number":1},
                  {"_id":"bad","name":"Broken","number":2}]}]
                """, show);
        check(episodes.size() == 2 && episodes.get(0).season() == 1 && episodes.get(1).episode() == 3,
                "Episodes are browsable in season/episode order");
        check(episodes.get(0).playbackId().startsWith("vod:pluto:777777777777777777777777|"), "Play an episode, not its series ID");
        check(VodCatalogue.episodes("{\"seasons\":[]}", show).isEmpty(), "Empty regional seasons are supported");
        check(VodCatalogue.normal("Pokémon!").equals(VodCatalogue.normal("Pokemon")), "TMDB title matching handles accents");
        check(items.get(0).id().equals(movie), "Preserve provider identity across tabs");
        System.out.println("PASS: free movies, shows, anime classification, episodes, locators and title matching");
    }
}
