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
   - **WATERMeDIA 2.1.37 or newer** (the video player library):
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

- The Loop playlist is retained, with native regional Pluto and MovieHub's Free-TV/iptv-org lists.
- The same 15 bundled live-provider modules as the Android app are included: FreeLiveSports,
  STIRR, TCL, Xumo, LG, Whale, Vizio, Roku, Plex, DaddyLive, Tubi, Samsung, Local Now, ZLive and
  FreeShot. Channels are discovered in the background; a failed source does not block the others.
- **Click the source name in the guide header** with the remote to cycle through source filters.
- Guide: the single combined [Moviehub-epg](https://github.com/billyke2000s/Moviehub-epg) XMLTV file,
  cached locally. M3U tvg-id and engine guide IDs are matched as well as Pluto IDs.
- `config/moviehubtv-client.toml` controls custom comma-separated `playlists`, `publicPlaylists`,
  comma-separated `providers`, `guide`, volume and hearing range. Existing configs receive the new
  provider/public-list settings without deleting your old playlist value.
- Required stream headers, child playlists, keys, token renewal and supported media containers go
  through a local per-TV relay. Up to four TVs decode concurrently on each client.
- Availability depends on the service and region. Bundled modules have fixture coverage; live
  playback of every service is **not yet verified in Minecraft**. DRM is not supported.
- This remains a live-TV mod. Android movie/series browsing, Premiumize and account login screens
  are not part of it. A private provider's exported HTTP(S) M3U can be configured locally.

See [AUDIT.md](AUDIT.md) for the source comparison, findings and remaining limits, and
[TESTING.md](TESTING.md) for regression and in-game acceptance checks.

## Build from source

```sh
./gradlew build    # jar ends up in build/libs/
```

Every push to `main` is built by GitHub Actions and published as the `latest` pre-release.

## Status

See [STATUS.md](STATUS.md).
