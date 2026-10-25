# Notes for AI assistants

- Read README.md and STATUS.md first. Keep STATUS.md honest about what was actually tested.
- Target: Minecraft Java 1.21.1, NeoForge 21.1.x, ModDevGradle, Java 21. Video via WATERMeDIA 2.1.x
  (compile-only dependency; players install it themselves; it may not be redistributed).
- Common code (`block`, `item`, `network`, `ModRegistry`) must not reference anything in `client`.
  All WATERMeDIA use stays in `client`.
- Server-side TV state is in `TvBlockEntity` and only changes through `TvBlockEntity.apply`.
  The guide layout and channel list exist only on the client.
- Screen maths lives in `ScreenGeometry`; rendering and the remote's hit test must both use it.
- Channel sources are the owner's own repos (Moviehub-freelinks, Moviehub-epg). Do not add
  unofficial or pirated stream sources.
- If the Minecraft build servers are unreachable, push and use the GitHub Actions build to compile.
