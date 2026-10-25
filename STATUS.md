# Status

Version 0.2.0 — source parity and code audit branch.

## Verified locally

- Exact module comparison against the pinned Android experimental checkout: no missing, extra or
  changed bundled modules (15 modules).
- Deterministic core suite: 12 M3U/identity/guide checks, all 15 JS module startup steps, a complete
  STIRR resolver replay, sandbox/URL boundaries, HLS rewrite and header validation, token-origin
  scoping, ZLive advertised-source expansion, 17 PNG and 9 media-carrier fixtures pass.
- Core Java/parser/provider/relay code compiles using the available Java 17 compiler API and
  lightweight Minecraft logging/config/path stubs. This is not a full Minecraft compile.
- `git diff --check` passes.

## Full build

Pending GitHub Actions Java 21/NeoForge compilation and regression run. Local Gradle cannot
fetch its distribution because the build-download hosts are unreachable in this environment.
The final result will be recorded after CI completes.

## Not yet verified

- In-game rendering, pointing/cursor feel, audio, crafting, world transitions and native playback.
- Real playback and regional availability of every added provider, including long-running token
  renewal, plus WATERMeDIA picture orientation.
- Dedicated-server startup and hostile-client packet rejection in a running server.

## Known limits

- One-block TVs; no channel logos; no film/series browser or account login UI.
- Four active TV decoders per client. A fifth TV shows a limit message until another stops.
- Separate providers retain separate channel copies; there is no automatic cross-provider failover.
- Guide overlay hides picture while audio continues. Read the block's small guide close up.
- Public-IP validation is not DNS pinning. Provider JS is sandboxed in-process, not in a separate OS
  process. ZLive contact history is in-memory for the client run.
- Custom non-Pluto channels saved with old hash-based IDs need reselecting once. Pluto IDs persist.

See AUDIT.md for the complete review and source matrix, SECURITY.md for trust boundaries, and
TESTING.md for in-game acceptance checks.
