#!/usr/bin/env python3
"""Compare actual bundled live-provider modules; never treats a matching name as matching code."""
import hashlib
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
android = Path(sys.argv[1]) / 'android-tv-launcher/app/src/main/assets/providers/builtin.json'
shipped = root / 'src/main/resources/providers/builtin.json'
reference = json.loads(android.read_text())['modules']
actual = json.loads(shipped.read_text())['modules']
missing = sorted(reference.keys() - actual.keys())
extra = sorted(actual.keys() - reference.keys())
changed = sorted(key for key in reference.keys() & actual.keys() if reference[key] != actual[key])
print(json.dumps({'missing': missing, 'extra': extra, 'changed': changed,
                  'shipped_sha256': hashlib.sha256(shipped.read_bytes()).hexdigest()}, indent=2))
sys.exit(bool(missing or extra or changed))
