"""Verify the built jar contains the actual upstream engine and its corresponding source notices."""
from pathlib import Path
from zipfile import ZipFile
import io
import hashlib
import json
import subprocess
import sys
import tempfile

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
        assert 'packages/requests/__init__.py' in engine.namelist()
        assert 'packages/six.py' in engine.namelist()
        assert any(name.startswith('packages/requests-') and name.endswith('METADATA') for name in engine.namelist())
        with tempfile.TemporaryDirectory() as directory:
            engine.extractall(directory)
            # -S prevents accidentally passing via the CI machine's installed requests/six.
            result = subprocess.run([sys.executable, '-S', str(Path(directory) / 'moviehub_worker.py'), '--inventory'],
                                    text=True, capture_output=True, check=True, timeout=30)
            inventory = json.loads(result.stdout)['providers']
            assert len(inventory) == 74
            assert any(p['id'] == 'native:rive' for p in inventory)
print('PASS: jar bundles the exact MovieHub engine source, real providers and licences')
print('PASS: extracted worker starts with only the bundled portable dependencies and lists 74 real services')
