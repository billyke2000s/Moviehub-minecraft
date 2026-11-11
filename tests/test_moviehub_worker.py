"""Bridge checks against the exact pinned Android MovieHub runtime; no live website requests."""
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'upstream-moviehub/android-tv-launcher/app/src/main/python'))
import moviehub_vod as engine

spec = importlib.util.spec_from_file_location('worker', ROOT / 'src/engine/moviehub_worker.py')
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)

class BridgeTests(unittest.TestCase):
    def test_inventory_is_real_moviehub_not_pluto(self):
        sites = json.loads(engine.search_providers())
        self.assertEqual(len(sites), 74)
        self.assertIn('native:rive', {p['id'] for p in sites})
        self.assertIn('free99:levidia_ch', {p['id'] for p in sites})
        self.assertTrue(any(p['site'] == 'rivestream.app' for p in sites))

    def test_incremental_real_adapter_contract_preserves_identity_and_headers(self):
        sites = json.loads(engine.search_providers())[:2]
        info = {'title': 'Night of the Living Dead', 'tmdb': 10331, 'imdb': 'tt0063350',
                'year': '1968', 'season': -1, 'episode': -1, 'http': False}
        events = []
        def query(provider, raw, cancelled, callback):
            self.assertEqual(json.loads(raw), info)
            self.assertFalse(cancelled())
            source = {'url': 'https://media.example/movie.m3u8', 'headers': {'Referer': 'https://example.org/'},
                      'providerName': provider, 'quality': '1080p', 'segmentContainers': True}
            callback.ready(json.dumps(source))
            return json.dumps({'status': 'complete', 'sources': [source]})
        with patch.object(engine, 'search_providers', return_value=json.dumps(sites)), \
             patch.object(engine, 'query_site', side_effect=query) as calls, patch.object(worker, 'emit', side_effect=events.append):
            worker.run(engine, info, budget=10)
        self.assertEqual(calls.call_count, 2)
        self.assertEqual(events[0]['event'], 'inventory')
        # Same URL/headers from two sites appears once, preserving the media transport recipe.
        sources = [e['source'] for e in events if e['event'] == 'source']
        self.assertEqual(len(sources), 1)
        self.assertTrue(sources[0]['segmentContainers'])
        self.assertEqual(sources[0]['headers']['Referer'], 'https://example.org/')
        self.assertEqual(events[-1], {'event': 'done', 'count': 1})

if __name__ == '__main__':
    unittest.main()
