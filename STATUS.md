# Status

Version 0.4.2: hides Free-TV channels locked to other countries. 0.4.1: Pluto TV with session tokens, the same way as MovieHub's PlutoSource,, for the player's own region (0.3.0 added Free-TV, 0.2.0 big TVs).

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
