# Security

TV channel locators, not account credentials or raw playback URLs, are sent to the Minecraft
server. Native HTTP resolution, EPG downloads and decoding take place on each client. The server
validates remote control reach, line of sight, held item, player state and interaction permission.

Provider scripts run with Rhino safe standard objects, no Java class access and instruction
deadlines. Only bundled modules are loaded. No remote code update or arbitrary script setting
exists. Network requests have provider host/method capabilities and response limits. Native media
is reached through a per-session loopback relay with an unpredictable path, which keeps required
headers on HLS requests. Token renewal is scoped to the discovered origin/path. DTDs and XML
external entities are disabled. Logs omit source URLs and response bodies.

Use trusted playlists. Public-IP validation is not DNS pinning, and the JS runtime is in-process,
so this is not a strong isolation boundary against a malicious provider package or hostile DNS.
HTTP streams remain unencrypted. Native VLC/WATERMeDIA must be kept updated separately. No DRM or
regional restrictions are bypassed. See AUDIT.md for detailed limits and unverified acceptance tests.

Credentials placed in custom playlist query strings remain in the local TOML config. Never commit
that config, caches or world saves containing private data to GitHub.
