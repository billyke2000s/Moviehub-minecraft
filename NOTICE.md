# MovieHub TV runtime notices

Original Minecraft mod code retains its MIT notice. Version 0.10 bundles the same free VOD
runtime as Android MovieHub at commit 08343545afd7b535f8c9112f818ad60ee4432bd4:
https://github.com/billyke2000s/moviehub/tree/08343545afd7b535f8c9112f818ad60ee4432bd4/android-tv-launcher/app/src/main/python

The combined distribution includes GPLv3 components. Their corresponding source, original
copyright notices and licence texts are included inside `moviehub-engine.zip` in the mod jar,
under `licenses/` and in the individual Python files. See that archive's NOTICE.md for the
Scrubs, Free99, ResolveURL and dependency provenance. The bridge source is in `src/engine`.
The mod's original MIT licence does not describe every bundled component.

Python itself is provided by the server host. Requests 2.32.5, six 1.17.0 and dependencies
are installed into the engine's private packages directory if unavailable; their dist-info
licences accompany those installed packages. WATERMeDIA is not included and remains a
separate client installation.
