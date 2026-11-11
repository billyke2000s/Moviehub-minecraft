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
| Battery | Redstone, iron ingot, redstone in a vertical column |
| TV | Iron ingots down both sides, battery top and bottom, glass in the middle |
| Remote | Any button, battery, iron ingot in a vertical column |
| Wide TV (2x1) | 2 TVs side by side |
| Large TV (2x2) | 4 TVs in a square |
| Large Wide TV (3x2) | 6 TVs: 3 across, 2 down |

Big TVs grow to your right and upwards from where you place them (they won't place if something
is in the way). Breaking any part removes the whole TV and gives it back. The remote works on any
part of the screen; the guide is scaled up to fit.

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
  broadcasters themselves, e.g. BBC, STV, S4C), UK channels first. Free-TV's on-demand groups and
  streams served from bare IP addresses (third-party re-streams) are skipped.
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
