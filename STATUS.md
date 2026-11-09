# Status

Version 0.2.0: adds Wide (2x1), Large (2x2) and Large Wide (3x2) TVs.

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

## Not yet verified

- Video playback with WATERMeDIA 2.1.37 in-game.
- Big TVs (0.2.0): placing, breaking, rendering across blocks and remote aiming are compile-checked only.
  The screen maths for the one-block TV is unchanged (same numbers as 0.1.0).
- Anything in-game: rendering, the cursor feel, video playback, sound, recipes.
- WATERMeDIA texture orientation (picture could be upside down) and Pluto streams playing
  from the UK through VLC.

## Known limits

- Big TVs only come in 2x1, 2x2 and 3x2; they show the guide centred at its normal shape.
- No channel logos in the guide.
- While the guide is open over a tuned channel, the sound keeps playing but the picture is hidden.
- The guide is drawn on the block itself, so it is small; read it close up.
