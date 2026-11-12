# MovieHub TV for Minecraft

Live TV from [MovieHub](https://github.com/billyke2000s/moviehub) inside Minecraft.
A TV block shows a Sky-style TV guide the moment you turn it on, and a Wii-style
remote lets you point at the screen with a hand cursor and pick a channel.

**Minecraft Java 1.21.1 · NeoForge · client and server.** Bedrock is not supported.

## Install

Movies, Shows and Anime each have nested **Categories** inside the existing TV guide. Choose
Popular, Trending, Top Rated, Upcoming, New Releases/Airing, Genres, Providers, Years, Countries
or Languages. Provider region defaults to **GB** and can be changed in the menu. Provider lists
(Netflix, Disney+, Prime Video and every other provider returned for that region/type) and genres
load from TMDB. Combine filters, then select **Browse titles**. **Clear filters** resets the tab;
**Search titles / TMDB ID** opens search. Back and the existing arrows navigate menus and pages.
Weekly trending uses TMDB's weekly list and resets other filters. Anime stays Japanese animation.

Provider categories describe availability, supplied by JustWatch via TMDB; choosing Netflix does
not supply a Netflix subscription or video. Playback continues through the existing source picker.
The server's TMDB key is used for all browsing; players never receive it. Version 0.11.1 requires
the updated jar on the server and every client.

1. Install NeoForge for Minecraft 1.21.1.
2. Put both jars in your `mods` folder:
   - **MovieHub TV**: download `moviehubtv-<version>.jar` from the
     [latest release](https://github.com/billyke2000s/moviehub-minecraft/releases/latest).
   - **WATERMeDIA 2.1.x** (2.1.37 or newer, **not 3.x**: WATERMeDIA 3 removed the VLC player this mod uses):
     [CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia) or
     [Modrinth](https://modrinth.com/mod/watermedia).
3. Windows works out of the box. macOS and Linux need [VLC](https://www.videolan.org/vlc/) installed
   (WATERMeDIA's requirement).

## Crafting

| Item | Recipe |
| --- | --- |
| Battery | Copper ingot top and bottom, iron ingots left and right, redstone in the middle |
| TV | Iron ingots down both sides, battery top and bottom, glass in the middle |
| Remote | Any button, battery, iron ingot in a vertical column |
| Speaker | Planks down both sides, any wool top middle, note block in the centre, redstone bottom middle |

**Volume:** aim the remote at a TV and scroll the mouse wheel while watching a channel (or hold
sneak and scroll while the guide is open). Each step is 5, from 0 to 100; everyone hears that TV
at its volume. Your own `masterVolume` setting scales all TVs for you only.

**Speakers:** right-click a TV while holding speakers to tune them to that TV, then place them
anywhere within 48 blocks of it (sneak + right-click places a speaker against the TV instead).
Untuned speakers play nothing. A message says which TV a speaker plays. You hear the TV from each linked speaker as if standing at the TV, so
speakers carry the sound around a room or building. Next to a speaker the sound is 1.5x the TV's volume (it follows
the remote's volume and your `masterVolume`), stays at full volume within 12 blocks, then fades to silence at 48 blocks;
the TV itself keeps its 16-block fade. Several speakers do not add up: the nearest one sets the level. Speakers change
loudness only; the sound has no direction. A nearby
speaker starts audio even when its TV is out of view, provided the TV's chunk is loaded.
`speakerBoost`, `speakerFullVolume` and `speakerRange` in the client config adjust this.
Right-click a placed speaker with an empty
hand to see which TV it plays; to change it, break it, tune it on another TV and place it again.
Up to 8 speakers per TV block.


**TV sizes:** place normal TVs with the screen facing you. Matching TVs that touch in the
same plane join into one TV, including one outer frame when switched off:

| TVs | Layout |
| --- | --- |
| 1 | 1 wide × 1 high |
| 2 | 2 wide × 1 high |
| 4 | 2 wide × 2 high |
| 6 | 3 wide × 2 high |

Place all parts while facing the same direction. The screen faces opposite your horizontal
heading in all four compass directions, regardless of which block face you click.
Incomplete shapes and other rectangles stay separate until a supported rectangle is complete.
The bottom-left TV controls the joined screen. Old crafted big TVs convert to the equivalent
normal-TV group when loaded, preserving facing, channel and power.

Use the same jar version on the server and all clients, replacing older MovieHub jars and fully
restarting both. Older builds are rejected by the compatibility handshake. The guide and remote use the actual casing’s facing and a validated footprint; inconsistent layouts
request automatic server repair instead of projecting into empty space. Existing normal
TV walls regroup when their chunks load; there is no new rotation tool or wall-face placement mode.

## Using it

- **Live TV | Movies | Shows | Anime** retain the existing blue on-TV menu and player popup.
  The three catalogue tabs browse TMDB with posters. Shows open seasons and episodes; picking a
  film or episode searches the same free website services as Android MovieHub and lists sources
  as they arrive. Pick a source to play it. Search accepts a title or `tmdb:movie:550` / `tmdb:tv:1399`.
- Set `tmdbKey` in the host's `config/moviehubtv-common.toml` to your TMDB API key or Read Access
  Token, then restart the host. The key and TMDB requests stay server-side. Players fetch poster
  images from TMDB's image CDN and video streams on their own connection.
- The host needs **Python 3.10+**. The jar contains the actual MovieHub provider runtime and its portable dependencies;
  it detects python3 or python, or uses pythonCommand from the common config. On first source
  search, its bundled requests/six packages extract into config/moviehubtv-engine-0.10.0/packages.
  This runs off the game thread and opens no public server port. Players need no Python install.
- Source searching can continue for several minutes; choose a returned source immediately without
  waiting for every website. Every source the search finds is sent to all players; if the chosen
  source will not play on someone's PC, their game tries the next one by itself ("source 2 of 5"). Provider names, quality and failures come from the real resolver.
  Websites can be offline or have no matching video. TMDB supplies metadata, not the movie itself.
- Aim the remote to use the existing player's **-10s | Play/Pause | +10s | Guide** popup. After a
  server restart or an expired link, select the title again to obtain fresh source URLs.
- Live TV keeps its existing channels, guide and categories. Its public extra playlists remain
  available through channelPlaylists on the host and movieHubFreeChannels on the client.
- This product uses the TMDB API but is not endorsed or certified by TMDB. See NOTICE.md for
  the bundled runtime licences and corresponding source.

- **Right-click the TV** (without the remote) to turn it on or off.
- Turning it on goes straight to the **TV guide**: channel number and name, what's on now,
  what's next, and a progress bar. The guide fills the whole screen; wide TVs show **Now**,
  **Next** and (on the 2-wide TV) **Later** in their own columns with start and end times.
- **Categories**: the guide opens on the category list the first time (after that it reopens where
  you left it). The list is All channels, genres (News, Movies, Kids, Sport, Music, Comedy,
  Crime & Drama, Reality & Lifestyle, Documentaries), Pluto TV, UK, then each Free-TV country.
  Pick one to show only those channels; the **Categories** button goes back to the list. Channel
  numbers stay the same. Genres are matched from
  channel names (and Pluto's category when Pluto provides one), so a few channels may land in the
  wrong genre or none.
- **Hold the remote and look at the screen**: a hand cursor appears where you aim.
  - **Right-click**: press what's under the cursor (pick a channel or category, ▲ ▼ page buttons,
    "Categories"/"Channels", "Back to TV").
  - While watching, right-click opens the guide again.
  - **Left-click**: power.
  - **Scroll wheel**: move through the guide one channel at a time.
- The remote works up to about 20 blocks away. Sound fades with distance.

## Channels and guide

- Pluto TV comes straight from Pluto for your region (UK line-up in the UK): the mod gets a free
  session token from Pluto's start service the way Pluto's website does (no account) and builds the
  current `/v2/stitch/...?jwt=` stream links. Turn off with `plutoNative = false`.
- Other channels: every channel in Moviehub-freelinks
  [`output/theloop.m3u`](https://github.com/billyke2000s/Moviehub-freelinks/blob/main/output/theloop.m3u),
  numbered from 101, then [Free-TV](https://github.com/Free-TV/IPTV) (free channels streamed by the
  broadcasters themselves, e.g. BBC, STV, S4C), UK channels first. Free-TV's on-demand groups,
  streams served from bare IP addresses (third-party re-streams) and channels Free-TV marks as
  locked to another country (Ⓖ, except UK ones) are skipped.
- Guide: [Moviehub-epg `epg.xml.gz`](https://github.com/billyke2000s/Moviehub-epg).
  It is about 50 MB, so it downloads in the background (at most every 6 hours) and is
  cached in `.minecraft/moviehubtv-cache`. Only listings for the playlist's channels are kept.
- The URLs can be changed in `config/moviehubtv-client.toml`: `playlists` and `extraPlaylists`
  (comma-separated; clear `extraPlaylists` to hide Free-TV), `guide`, `masterVolume` and `hearingRange`.

## Build from source

```sh
./gradlew build    # jar ends up in build/libs/
python3 tests/run_tv_regressions.py  # JDK 21; isolated logic and model checks
```

Every push to `main` is built by GitHub Actions and published as the `latest` pre-release.
The first successful `main` build of each new `mod_version` (gradle.properties) is also published as a
stable release tagged `v<mod_version>`; bump `mod_version` to release a new version.

## Status

See [STATUS.md](STATUS.md).


For a local source build, check out billyke2000s/moviehub at commit 08343545afd7b535f8c9112f818ad60ee4432bd4
in `upstream-moviehub`. Collect its portable packages before running Gradle:

```sh
python3 -m pip install --only-binary=:all: --platform any --no-compile --target build/engine-packages requests==2.32.5 six==1.17.0
./gradlew build
```

CI performs these steps automatically. The server needs Python 3.10+, but no pip setup or additional
provider download: the published jar already includes the runtime, portable packages and licences.
