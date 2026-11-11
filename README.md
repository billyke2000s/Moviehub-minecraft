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


**Bigger screens:** place normal TVs next to each other (all facing the same way). Any full
rectangle of them, from 2x1 up to 12 wide by 8 tall, becomes one screen with one joined frame (even when switched off): e.g. 3x1 ultra-wide,
4x3, 8x5. Shapes that aren't a full rectangle stay as separate TVs until you fill them in.
The bottom-left TV (as you look at it) runs the wall; the remote and right-clicking work
anywhere on it. Breaking a TV splits the wall; whatever rectangles are left rejoin.
(The crafted Wide/Large TVs from 0.2.0 are no longer craftable. Since 0.5.1 any that are already placed, or placed from an inventory, turn into the same number of normal TVs joined as one screen, keeping power and channel.)

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
```

Every push to `main` is built by GitHub Actions and published as the `latest` pre-release.

## Status

See [STATUS.md](STATUS.md).
