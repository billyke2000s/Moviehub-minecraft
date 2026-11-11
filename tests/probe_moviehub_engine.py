"""Optional live smoke check of real MovieHub adapters using a public-domain film. No stream URLs logged."""
import contextlib
import importlib.util
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'upstream-moviehub/android-tv-launcher/app/src/main/python'))
with contextlib.redirect_stdout(sys.stderr):
    import moviehub_vod as engine
spec = importlib.util.spec_from_file_location('worker', root / 'src/engine/moviehub_worker.py')
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)
sites = [p for p in json.loads(engine.search_providers()) if p['id'] in ('native:rive', 'native:movy', 'free99:levidia_ch')]
engine.search_providers = lambda: json.dumps(sites)
found = []
def safe_log(event):
    if event['event'] == 'source':
        found.append(event['source'])
        print('Playable media returned by:', event['source'].get('providerName'), event['source'].get('quality'), flush=True)
    elif event['event'] == 'provider':
        print(event['provider'], event['status'], flush=True)
worker.emit = safe_log
worker.run(engine, dict(title='Night of the Living Dead', year='1968', imdb='tt0063350', tmdb=10331,
                        season=-1, episode=-1, http=False), budget=60)
print('Real MovieHub providers checked:', len(sites), 'validated sources returned:', len(found))
if not found:
    raise SystemExit(2)
