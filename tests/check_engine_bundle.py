"""Verify the built jar contains the actual upstream engine and its corresponding source notices."""
from pathlib import Path
from zipfile import ZipFile
import io
import hashlib

root = Path(__file__).resolve().parents[1]
version = next(line.split('=', 1)[1] for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
with ZipFile(root / f'build/libs/moviehubtv-{version}.jar') as jar:
    with ZipFile(io.BytesIO(jar.read('moviehub-engine.zip'))) as engine:
        for name in ('moviehub_vod.py', 'moviehub_providers.json', 'moviehub_native/rive.py',
                     'moviehub_scrubs/providers/levidia_ch.py', 'resolveurl/resolver.py'):
            original = root / 'upstream-moviehub/android-tv-launcher/app/src/main/python' / name
            assert hashlib.sha256(engine.read(name)).digest() == hashlib.sha256(original.read_bytes()).digest(), name
        assert 'moviehub_worker.py' in engine.namelist()
        assert 'licenses/NOTICE.md' in engine.namelist()
        assert 'licenses/Scrubs-GPL-3.0.txt' in engine.namelist()
print('PASS: jar bundles the exact MovieHub engine source, real providers and licences')
