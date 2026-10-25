# Code audit and source comparison — 25 October 2026

## Scope and reference

Reviewed all original Java classes, resources, build files, mod metadata and CI workflow at
Minecraft mod commit `83146f18b9162aa28f0370ffe4794102e3858134`.
Compared live-source architecture with `billyke2000s/moviehub` main
`4dd38ba` and the experimental repair checkout
`b4a4b3c2ecde9383dd8094a9ed324dade1b2a39c`.
The bundled provider package and Java compatibility helpers come from the latter checkout.
Provider code provenance is recorded in `src/main/resources/providers/NOTICE.txt` and
`source.json`. No Android repository files were changed.

This is a source-level audit and deterministic regression suite, not a penetration test or a
claim that every live website works. Minecraft, WATERMeDIA and real provider playback must still
be exercised together in-game.

## Source comparison

| Android live source | Previous Minecraft build | This change |
| --- | --- | --- |
| The Loop / Pluto playlist | Sole default source | Retained; saved Pluto IDs preserved |
| Native regional Pluto | Missing | Same anonymous regional catalogue/session API |
| Free-TV and iptv-org | Missing by default | Included; configurable independently of old playlist setting |
| FreeLiveSports | Missing | Shared bundled catalogue and resolver module |
| STIRR, TCL, Xumo, LG, Whale, Vizio | Missing | Shared bundled catalogue and resolver modules |
| Roku, Plex, Tubi, Samsung, Local Now | Missing | Shared bundled catalogue and resolver modules |
| DaddyLive | Missing | Shared resolver, scoped playlist-token renewal and media relay |
| ZLive | Missing | Shared resolver, advertised source variants, contact history, media containers |
| FreeShot | Missing | Shared resolver and scoped playlist-token renewal |
| User M3U | Manual configuration only | Retained, with HTTP(S) media validation and tvg-id support |
| Xtream account | Missing login UI | An account's exported M3U can be configured locally; no account UI added |
| External Masqueradarr server | No server client | Not required for bundled modules; exported M3U still works |
| Android film/series providers and Premiumize | No film/series feature | Outside this live-TV mod's existing feature scope; not implemented |

A module being present does not establish channel availability, regional access, account access,
codec support or successful live playback. Region restrictions and DRM are not bypassed.
The owner's `output/sporthd.m3u` was inspected: it contains only an M3U header and contributes
no channels. It is not advertised as a working additional channel source.

## Findings addressed

| Severity | Finding | Change |
| --- | --- | --- |
| High | Playlist entries could give native VLC local-file or non-network protocols | Accept HTTP(S) media only; relay validates public targets and redirects |
| High | Remote packets did not require a remote item or line of sight | Server checks held item, living/non-spectator player, interaction permission, reach, line of sight and rate |
| Medium | Changed/reordered URLs could reuse another source's cache file | Cache keys derive from the complete URL using SHA-256 |
| Medium | Failed first load suppressed retries for six hours | Retry failed refreshes after one minute; provider-specific cooldown prevents repeated contact |
| Medium | Guide only matched Pluto's 24-character IDs | Preserve M3U tvg-id and exact engine guide IDs; retain old Pluto aliases |
| Medium | Native playback start failures could escape the render callback | Resolve in bounded background workers, catch start errors and show retry status |
| Medium | One native decoder per visible TV had no cap | Four active TV sessions; bounded resolver and relay queues; cleanup on disconnect |
| Medium | Downloads and inflated guides lacked limits | Bounded download size/time, inflated XML limit and per-channel programme cap |
| Medium | Java URL hash collisions and long names could break channel identities/packets | Fixed-length SHA-256 identities; saved Pluto identities unchanged |
| Medium | Desktop JSON serialization changed provider replay request order | Canonical request-object comparison in desktop runtime; real bundled STIRR resolver fixture |
| Medium | Browser-context headers/token renewal were absent in Minecraft | Loopback relay rewrites child playlists, segments and key URIs; scoped token renewal |
| Low | Scroll/pointer state survived world disconnect | Clear local UI state on logout; selection carries channel ID rather than a mutable index |
| Low | No automated regression checks | Build runs parser, guide, all-module startup, resolver replay, sandbox and media-container fixtures |

The guide disables DTDs and external entities. Common server code remains independent of client
and WATERMeDIA classes. The native video library remains an external install, never redistributed.

## Validation

Local parser/provider tests pass using the available Java 17 compiler API and cached Rhino/JSON
jars, without game stubs. Pure parsing is separated into ChannelData so the regression suite
can run without starting Minecraft. These checks verify actual parsing/runtime/relay classes,
not the renderer, event wiring or native decoder.
The full Java 21/NeoForge build and regression suite passed in
[GitHub Actions run 40225431725](https://github.com/billyke2000s/Moviehub-minecraft/actions/runs/40225431725)
for code commit `78d42e233e10e4c42be621a1e4280461b3177611`, producing the version 0.2.0 mod jar.
The initial CI run compiled the mod but exposed a standalone harness dependency on game config;
separating the pure parser fixed that dependency. See STATUS.md for the complete validation limits.

## Remaining limitations and risks

- Live network/playback acceptance is unverified for every added service. US-only services can
  fail from the UK; an empty or refused provider does not block other catalogues.
- ZLive contact history and anonymous tokens stay in memory. Reuse/cooldown survives within one
  client run, not a complete Minecraft restart. No tokens are put in shared world data.
- Public-IP checks precede URLConnection DNS resolution. This blocks ordinary LAN/localhost
  targets but does not constitute a DNS-pinned transport resistant to DNS rebinding. Use trusted
  playlist sources; do not treat arbitrary remote playlist ingestion as a security boundary.
- Sandboxed JS has instruction deadlines and data limits, but is in-process and does not have
  the Android isolated-process memory boundary. Only shipped modules are executed; remote JS
  updates and arbitrary user scripts are not accepted.
- HTTP is accepted for public playlists that still use it; those streams are not TLS protected.
- M3U entries with `#EXTVLCOPT`, DRM, non-HTTP protocols or `url|headers` need an explicit adapter;
  raw native VLC options are intentionally not accepted.
- Duplicate channels from distinct providers remain individually selectable, since each has
  different stream context. There is no automatic cross-provider channel-name matching/failover.
- No in-game tests were available for rendering, audio, recipes, remote feel or dedicated-server
  startup. These remain acceptance checks, not assertions of success.
- Existing custom non-Pluto selections using the old Java-hash ID must be reselected once.
- Existing release workflow still automatically publishes pushes on main. This branch/PR does
  not publish a new release. The mutable `latest` release can contain several historical jars.
