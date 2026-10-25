# Status

Version 0.1.0 (first build).

## Verified

- Channel and guide parsing, run outside Minecraft against the real files:
  427 channels load, guide listings match 425, and 424 have a "now" programme.
  Parsing the 52 MB guide takes about 7 seconds in the background.
- Screen placement maths: the drawing position and the cursor hit position agree for all four facings.

- It compiles: the GitHub Actions build passes and publishes `moviehubtv-0.1.0.jar`
  (the jar contains the classes, assets, recipes and mod metadata).

## Not yet verified

- Anything in-game: rendering, the cursor feel, video playback, sound, recipes.
- WATERMeDIA texture orientation (picture could be upside down) and Pluto streams playing
  from the UK through VLC.

## Known limits

- One-block TVs only; no multi-block screens.
- No channel logos in the guide.
- While the guide is open over a tuned channel, the sound keeps playing but the picture is hidden.
- The guide is drawn on the block itself, so it is small; read it close up.
