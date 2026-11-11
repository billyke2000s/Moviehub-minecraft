# MovieHub TV for Minecraft

Live TV from [MovieHub](https://github.com/billyke2000s/moviehub) inside Minecraft.
A TV block shows a Sky-style TV guide the moment you turn it on, and a Wii-style
remote lets you point at the screen with a hand cursor and pick a channel.

**Minecraft Java 1.21.1 · NeoForge · client and singleplayer.** Bedrock is not supported.

## Install

1. Install NeoForge for Minecraft 1.21.1.
2. Put both jars in your `mods` folder:
   - **MovieHub TV**: download `moviehubtv-*.jar` from the
     [latest build](https://github.com/billyke2000s/moviehub-minecraft/releases/tag/latest).
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

Use the same 0.5.8 jar on the server and all clients, replacing older MovieHub jars and fully
restarting both. Older builds are rejected by the compatibility handshake. The guide and remote use the actual casing’s facing and a validated footprint; inconsistent layouts
request automatic server repair instead of projecting into empty space. Existing normal
TV walls regroup when their chunks load; there is no new rotation tool or wall-face placement mode.

## Using it

- **Right-click the TV** (without the remote) to turn it on or off.
- Turning it on goes straight to the **TV guide**: channel number and name, what's on now,
  what's next, and a progress bar.
- **Hold the remote and look at the screen**: a hand cursor appears where you aim.
  - **Right-click**: press what's under the cursor (pick a channel, ▲ ▼ page buttons, "Back to TV").
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
  (comma-separated; clear `extraPlaylists` to hide Free-TV), `guide`, volume and hearing range.

## Build from source

```sh
./gradlew build    # jar ends up in build/libs/
python3 tests/run_tv_regressions.py  # JDK 21; isolated logic and model checks
```

Every push to `main` is built by GitHub Actions and published as the `latest` pre-release.

## Status

See [STATUS.md](STATUS.md).
