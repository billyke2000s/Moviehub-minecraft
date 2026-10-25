# Testing

Use Java 21 and run `./gradlew build`. `check` includes `coreRegressionTest`, a deterministic
Java main suite without provider accounts or live network requests. It checks:

- M3U names/attributes containing commas, BOMs, deduplication and unsafe protocols.
- Stable/bounded identities and non-Pluto XMLTV guide matching.
- Startup of all 15 bundled JavaScript modules, plus a complete STIRR resolver replay fixture.
- No Java globals in the JS sandbox and provider HTTP capability restrictions.
- HLS child playlists, segments and key URI rewriting, and header-injection rejection.
- Playlist-token origin scoping and independently advertised ZLive source variants.
- PNG/raw/gzip/WebP carrier decoding, malformed inputs and decoded-size limits.

The tests do not prove real services are reachable or playable. For acceptance, install the built
jar with NeoForge 21.1.x and WATERMeDIA 2.1.37 on Minecraft 1.21.1. Check all four TV facings;
craft/place/break each item; use the remote at range and through a wall; scroll and cycle the
source header; tune one channel from every available source; keep token-based streams running
past renewal; switch channels rapidly; create five TVs; move away; disconnect and change world.
Repeat on a dedicated server to verify client-only classes never load there.

Compare the shipped provider package with a local Android checkout:

```sh
python3 tools/check_provider_parity.py /path/to/moviehub
```

Changes to bundled Android modules must preserve the upstream MIT notice and record the source
commit and package SHA-256 in `src/main/resources/providers/source.json`.
