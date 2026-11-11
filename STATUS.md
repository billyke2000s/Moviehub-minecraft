# Status

## 0.11.0 - nested browsing, posters and client video recovery

- Movies, Shows and Anime open nested menus in the existing blue guide: popular, weekly trending,
  top rated, upcoming, recent/airing, genres, providers, years, countries, languages, provider
  regions and availability type. Search/TMDB-ID lookup remains available as a menu row.
- Genres and providers come from TMDB rather than a short hardcoded provider list. The selected
  provider region defaults to GB. Provider/genre/year/origin/language/availability filters combine;
  Browse titles applies them. Weekly trending opens the actual weekly list and resets other filters.
  Anime retains Japanese animation constraints and combines TV/film inventories. Lists paginate,
  including provider/region menus. Availability is JustWatch data via TMDB, not subscription access.
- Confirmed poster failure in the owner's client log: mixed-case filenames produced invalid
  Minecraft resource IDs. Poster IDs now hash the original path to lowercase; URLs retain case.
  JPEG downloads are converted to bounded PNG images for Minecraft's PNG-only decoder. Texture
  registration exceptions are handled on the render thread, with retry instead of a stuck request.
- WATERMeDIA raw GL uploads now preserve Minecraft texture bindings and unpack state. Picture
  readiness requires allocated video texture storage, not just the player's initial texture name.
  Keep the last frame while buffering; delay initial VOD control sync until a frame exists; recover
  failed/frameless players with at most three starts and a three-second backoff. Resource reloads
  release stale poster/video textures. These address observed local texture errors; the friend's
  exact black-picture cause and in-game success remain unverified.
- Update server and every client together: protocol tv-0.11.0. TMDB credentials remain host-only.
- Verified: full CI build; production browse-menu pagination and Netflix-ID selection fixtures;
  combined provider/region/genre/year/country/language filters, anime constraints and validation;
  actual Minecraft JPEG-to-PNG decoding and mixed-case resource IDs; hidden OpenGL allocated-frame
  checks and restoration of real/cached texture bindings and unpack state. Existing TV/wall/speaker,
  resolver and media transport suites also pass. No in-game multiplayer playback or visual test
  is claimed.

## 0.10.1 — speaker distance and audio startup

- Speakers now have their own 32-block falloff and four-block full-volume zone; the TV retains
  its existing falloff. This makes room speakers louder at normal listening distances without
  overriding TV/master volume or stacking several speakers into clipping.
- Speaker links are now sent to clients. Loaded linked speakers start the TV's shared player when
  the listener approaches, even if that TV never entered the renderer. Playback is retained near
  speakers past the TV's old 64-block cutoff. Stale, broken, untuned and unloaded links are excluded.
- The linked TV and the members of its wall must still be loaded; this does not force-load chunks.
- New client defaults: speakerHearingRange=32, speakerFullVolumeRange=4. Existing hearingRange
  controls the TV itself. Update server and clients together: protocol tv-0.10.1.
- Verified: production attenuation/mute/master-volume/no-clipping/retention tests; the actual
  SpeakerAudio class starts a shared TV player without a renderer and rejects off, distant,
  stale, untuned, unloaded and cross-dimension speakers. Existing regressions and the full
  GitHub Actions build pass. No in-game listening test is claimed.

## 0.10.0 — real MovieHub free services and TMDB posters

- Corrects 0.9.0's scope error: Movies / Shows / Anime now browse TMDB through the host's key,
  with poster paths and a client texture loader. No silent substitution with Pluto's catalogue.
- Selecting a film searches Android MovieHub's actual free-provider runtime. Shows open TMDB
  seasons and episodes before searching. Results appear incrementally with website/quality names
  and tune via server-issued IDs. The same blue menu and existing video popup remain.
- The jar bundles the unmodified Python provider and host-resolver sources from MovieHub commit
  08343545afd7b535f8c9112f818ad60ee4432bd4, including the same 74 active website groups and their
  disabled/retired-site policy. This is actual provider-code reuse, not a new substitute source list.
- Host needs Python 3.10+. Portable requests/six dependencies are bundled in a private
  engine directory, not system Python. Players need neither Python nor a TMDB key. Missing key,
  Python, no-match and provider failures are reported in the menu.
- A client-local relay carries source headers, rewrites HLS links and uses MovieHub's PNG-segment
  decoder. Videos download on players' connections. TMDB and source discovery run on the server.
- Source URLs/headers are ephemeral live TV updates, not saved in world NBT. After a host restart,
  reselect the film/episode; expiring links may also need a new source search. Some remote services
  can fail or return no links. No claim that every website/title works, or of in-game verification.
- Verified: full build; all 144 upstream resolver tests and two worker bridge tests; bundled-source
  hashes/licences; clean extracted-worker startup using only bundled dependencies; production Java
  host extraction/Python startup and all 74 service groups; TMDB poster DTO and server-issued source
  selection; 17 PNG checks; HLS key/segment and DASH-template transport fixtures; existing TV tests.
- A live GitHub-runner search for Night of the Living Dead (1968), through all 69 movie-capable
  service groups, returned 15 media-validated links from nine websites. Some
  services returned HTTP failures, timeouts or no match. This is resolver/media probing, not
  in-game rendering, VLC playback, audio verification or a live test using the owner's TMDB key.
- Protocol tv-0.10.0 on server and clients.

## 0.9.0 — free Movies / Shows / Anime tabs

- Existing on-TV blue guide, channel categories, remote cursor and player information banner retained.
  The top bar now has Live TV, Movies, Shows and Anime. Movies and Shows browse Pluto's regional
  free on-demand catalogue; a show opens its episodes. Anime uses Pluto's Anime category/genre,
  not all children's animation. Availability depends on the player's real region.
- Title search is in the footer. Optional `tmdb:movie:ID` / `tmdb:tv:ID` lookup uses a key/read token
  stored only in the host's `config/moviehubtv-common.toml`. It matches the result against the free
  Pluto catalogue; TMDB does not provide videos. Free browsing requires no TMDB key or account.
- Free on-demand playback uses the existing WATERMeDIA player, with -10s / Play-Pause / +10s / Guide
  controls inside the existing blue banner. Pause and seek state is server-owned. No paid-provider
  setup, debrid account or subscription has been added.
- Live TV additionally imports the public iptv-org list used by Android MovieHub (enabled by default
  through a separate setting, including for existing installations). Public additional M3U URLs can
  be set once on the host via `channelPlaylists`; clients receive those URLs, never the TMDB key.
- Full Android source parity is NOT established: Android's provider-engine adapters, authenticated
  accounts and site-specific free-VOD resolver runtime are not ported by this change. Native Samsung,
  Plex, Roku, etc. still do not have dedicated adapters in the mod; some channels can appear in the
  public lists. Do not claim every Android channel or movie is present or playable.
- Verified: offline production-parser fixtures and the existing TV regression suite pass; full
  GitHub Actions compilation passes. The production Java provider code fetched all 373 categories
  across four pages in the US runner and parsed 8,135 movies, 3,826 shows and 312 anime entries
  (anime overlaps movies/shows). A selected movie returned an HLS manifest, and a sample show
  returned 62 playable episodes. Popup hit targets are checked at every supported screen width.
  These counts are regional and can change. No in-game or UK playback is claimed.
- Protocol `tv-0.9.0`: update the server and every client together.

## 0.8.1 — guide opens on categories

- The guide first opens on the category list (All channels first) for each TV on each client; after
  that it reopens wherever the player left it (category list, or a category's channels and page).
  State is in memory only, so it starts on the category list again after restarting the game.
- Verified: regression script and GitHub Actions build. Not tested in-game.

## 0.8.0 — guide categories and full-width guide

- The guide fills the screen width (canvas 160 tall, at least 192 wide): 1x1 = 192, 2x2 = 192 (shrunk),
  3x2 ≈ 271 (Now + Next columns), 2x1 = 448 (Now, Next, Later). Narrow screens keep the now/next cell.
  `GuideLayout` holds the positions; rendering and the remote's hit test both use it.
- Categories button: All channels, keyword genres, Pluto TV, UK, then playlist groups A-Z, built by
  `ChannelCategories` when channels load; empty ones hidden. Channel records now keep their playlist
  group and Pluto's `category` field when the catalogue has one (not verified that Pluto sends it).
  Category, page and picker state are per TV on each client, not shared or saved.
- Client-only change: network version stays `tv-0.7.0`, so a 0.7.x server works; players should update.
- Verified: regression script tests `GuideLayout` for every TV size and `ChannelCategories`; on the real
  playlists (11 Nov 2026, approximating the mod's filters) 2,098 channels gave 104 categories (e.g.
  News 172, Sport 44, Movies 32, Kids 28, UK 48). Full build: GitHub Actions. Not tested in-game: how
  the wider guide and columns look and read, category picking with the remote.

## 0.7.2 — only tuned speakers work

- Removed nearest-TV linking: a speaker plays a TV only if it was tuned by right-clicking that TV with
  the speaker item before placing. Untuned speakers stay silent and say so; empty-hand right-click only
  reports the linked TV. Breaking a speaker drops an untuned speaker (tune it again before re-placing).
- Verified: regression script and GitHub Actions build. Not tested in-game.

## 0.7.1 — tune speakers to a chosen TV

- Right-clicking a TV with a speaker stack stores that TV (position + dimension) on the stack; placing a
  tuned speaker links it to that TV if it still exists in the same dimension within 48 blocks, otherwise it
  stays unlinked and says so. Sneak + right-click places a speaker against a TV as normal. Untuned
  speakers keep the 0.7.0 nearest-TV rule. Same network version as 0.7.0 (`tv-0.7.0`).
- Verified: regression script and GitHub Actions build. Not tested in-game.

## 0.7.0 — TV volume and linked speakers (replaces 0.6.0's speaker behaviour)

- Each TV has its own saved volume (0-100, default 80, steps of 5), changed with the remote's scroll
  wheel while watching, or sneak + scroll while the guide is open. It is server state, so everyone hears
  the same TV volume. The client setting `volume` is replaced by `masterVolume` (percent, default 100).
- Speakers (same recipe as 0.6.0) link on the server to the nearest TV block within 32 blocks of loaded
  chunks when placed; empty-hand right-click relinks; breaking unlinks. A TV block holds up to 8 speakers
  (oldest dropped). The client hears a screen from the TV or any loaded speaker linked to any of its wall
  members, at the TV's volume, fading over `hearingRange`. 0.6.0's radius/boost rules are gone.
- Network version `tv-0.7.0`; server and every player must run 0.7.0.
- Verified: regression script runs the real `SpeakerMix` maths and checks resources and wiring. Full
  build: GitHub Actions. Not tested in-game: linking messages, volume control feel, how it sounds through
  WATERMeDIA/VLC, speaker links after a TV wall regroups or a linked TV is broken (the speaker then needs
  an empty-hand right-click to relink).

## 0.6.0 — first speakers (superseded)

- Released 11 Nov 2026 but never installed on the server: speakers within 8 blocks boosted volume
  (+5 each) and range (+50% each, max 4). Replaced by 0.7.0.

## 0.5.8 — casing-aligned screen validation and automatic repair

- Rendering and remote hit testing now resolve the same checked physical TV layout. Both take facing
  from the actual world block state used by the casing model, rather than the block entity's cached
  state. The renderer and cursor reject missing, unloaded, differently facing or inconsistent parts,
  wrong frame pieces and unsupported dimensions. No oversized guide is projected into empty space
  from unchecked saved width/height metadata.
- Invalid visible layouts request a server-side regroup at most once per second within remote range.
  Repair uses the existing deferred queue and preserves a complete valid controller's saved channel
  and power. It does not guess a new facing or rotate existing blocks. Playback stops for invalid
  layouts until their state is consistent. Empty-hand interaction uses the same validated controller.
- Retains the 0.5.7 player-facing placement and sizes 1x1 / 2x1 / 2x2 / 3x2, along with the 0.5.6 load
  safeguards. Both sides must use 0.5.8 because the repair action requires the new network version.
- Verified locally: production placement, grouping, conversion and layout-validator classes compile
  with JDK 21 against minimal Minecraft API stubs. Regression cases pass for all directions/sizes,
  legacy layouts, missing chunks/entities, inconsistent dimensions/frame states and a saved 3x2
  rectangle pointing perpendicular to its real blocks. Valid frame regrouping repairs the layout;
  invalid geometry is rejected instead of drawing a floating screen. These are not in-game tests.
- The previous full local build was blocked by policy on the NeoForge dependency host. The user has
  explicitly authorized pushing the fixes to main for the GitHub Actions full build; result pending.
- The live modpack's original cause (version mismatch, cached state or a rendering interaction) remains
  unconfirmed. The screenshot has not been reproduced in-game; do not claim in-game verification.

## 0.5.7 — requested placement and screen sizes

- Normal TVs and legacy large TVs share player-facing placement using the actual player’s horizontal
  direction rather than an overridden placement context. Non-player contexts fall back to their heading.
- Supported joined sizes are 1×1, 2×1, 2×2 and 3×2 (1, 2, 4 and 6 TVs). Other rectangles and incomplete
  arrangements stay separate. Existing joined-frame models are shared between powered and unpowered
  states, and regrouping updates both frame pieces and the shared screen metadata.
- Retains the 0.5.6 delayed-chunk conversion/regroup safeguards, per-member on-load repair, saved-offset
  validation, unloaded-chunk power guards and server-stop queue cleanup. Network version requires 0.5.7
  on both client and server, rejecting older builds that may interpret state differently.
- Verified: `python3 tests/run_tv_regressions.py` with JDK 21 passes the actual production placement,
  grouping and conversion classes against small Minecraft API stubs. Tests cover all four headings,
  modified-context fallback, all requested sizes, joined frame metadata, unsupported shapes, delayed
  loads, split/rejoin and saved migration state. All 128 model variants have expected compass rotation
  and use identical assets when powered/unpowered. These are isolated tests, not in-game rendering tests.
- Full build blocked: access to `https://maven.neoforged.net:443` was rejected by environment policy.
  No installable 0.5.7 jar has been built. GitHub Actions requires publishing the source changes; the user subsequently authorized the push
  to main for 0.5.8.
- The exact cause of the reported one-direction behaviour in the live modpack is not reproduced.
  Do not describe these changes as an in-game-verified fix or as proof that existing saved facings
  are automatically corrected. No previously damaged legacy fragments are reconstructed.

Version 0.5.5: TV walls regroup when loaded, so walls built before 0.5.2 get the joined frame. 0.5.4: battery recipe is copper/iron/redstone (plus shape); guide matches channels by name when IDs differ (149 -> 182 channels from the MovieHub guide, measured offline on the player's cached playlists and guide), and loads Free-TV's per-country guides (epgshare01, from the playlist header) on demand; their ID match is unverified. 0.5.3: Pluto guide comes straight from Pluto (/v2/guide/timelines, 20 channels per request, 24 h, like MovieHub) because the Moviehub-epg file's Pluto listings had stopped at 10 Nov 03:37. 0.5.2: TVs in a wall swap to frame pieces (wall_column/wall_row block states) so the wall looks like one TV even when off. 0.5.1: old crafted big TVs turn into normal TVs joined as a wall; wall regrouping now runs at the end of the tick (after the new TV's block entity exists). 0.5.0: TVs placed side by side join into one screen (up to 12x8); big-TV recipes removed. 0.4.2: hides Free-TV channels locked to other countries. 0.4.1: Pluto TV with session tokens, the same way as MovieHub's PlutoSource,, for the player's own region (0.3.0 added Free-TV, 0.2.0 big TVs).

## Verified

- Channel and guide parsing, run outside Minecraft against the real files:
  427 channels load, guide listings match 425, and 424 have a "now" programme.
  Parsing the 52 MB guide takes about 7 seconds in the background.
- Screen placement maths: the drawing position and the cursor hit position agree for all four facings.

- It compiles: the GitHub Actions build passes and publishes `moviehubtv-0.1.0.jar`
  (the jar contains the classes, assets, recipes and mod metadata).

- 0.1.0 in-game (NeoForge 21.1.265 with Sinytra Connector and other mods): loads, 456 channels and
  the guide (452 with listings) load. With WATERMeDIA 3.x installed no video plays, because WATERMeDIA 3
  removed `org.watermedia.api.player` (VLC). 0.2.0 therefore requires WATERMeDIA [2.1.37, 3).

- 0.3.0 channel loading, run outside Minecraft with the real `parseM3u`/`parseGuide` against the live
  files (9 November 2026): The Loop 456 + Free-TV 1,771 = 2,227 channels; UK Free-TV channels number
  from 557; guide listings for 673 channels (was 452), including 44 of 52 UK Free-TV channels
  (BBC One, BBC Two, S4C checked). Parsing the 58 MB guide took about 3 seconds.

- 0.3.0 in-game (11 Nov 2026, WATERMeDIA 2.1.37): 2,229 channels and the guide load and players start,
  but Pluto channels show no picture. Cause: Pluto retired its token-less stream links; every stream now
  needs a session token from `boot.pluto.tv/v4/start`. 0.4.0 fetches one (parsing unit-tested with sample
  data) and loads Pluto's channel list for the player's region.

## Not yet verified

- Pluto playback with 0.4.0's session tokens (Pluto's start service could not be reached from the
  build environment), and video playback in general with WATERMeDIA 2.1.37 in-game, including whether Free-TV streams play
  (some are geo-blocked to their country).
- TV walls (0.5.0) and big-TV conversion (0.5.1): joining/splitting, conversion, wall rendering and remote aiming on walls are untested in-game.
- Big TVs (0.2.0): placing, breaking, rendering across blocks and remote aiming are compile-checked only.
  The screen maths for the one-block TV is unchanged (same numbers as 0.1.0).
- Anything in-game: rendering, the cursor feel, video playback, sound, recipes.
- WATERMeDIA texture orientation (picture could be upside down) and Pluto streams playing
  from the UK through VLC.

## Known limits

- Big TVs only come in 2x1, 2x2 and 3x2; they show the guide centred at its normal shape.
- No channel logos in the guide.
- With over 2,000 channels the guide is long; ▲/▼ move a page of 7 at a time.
- Samsung TV Plus, Plex, Roku and the other services MovieHub's Android app adapts are not included.
- While the guide is open over a tuned channel, the sound keeps playing but the picture is hidden.
- The guide is drawn on the block itself, so it is small; read it close up.

