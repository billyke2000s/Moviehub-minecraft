"""Minecraft stdin/stdout bridge to MovieHub's unmodified, pinned free VOD engine."""
import concurrent.futures
import contextlib
import json
import sys
import threading
import time

OUTPUT = sys.stdout
LOCK = threading.Lock()

def emit(value):
    with LOCK:
        OUTPUT.write(json.dumps(value, ensure_ascii=True) + '\n')
        OUTPUT.flush()

def run(engine, info, budget=400):
    started = time.monotonic()
    stop = threading.Event()
    sites = [p for p in json.loads(engine.search_providers()) if p['series' if info['season'] >= 0 else 'movies']]
    emit({'event': 'inventory', 'providers': [p['site'] for p in sites]})
    count = 0
    seen = set()
    class Callback:
        def ready(self, raw):
            nonlocal count
            source = json.loads(raw)
            signature = (source['url'], json.dumps(source.get('headers', {}), sort_keys=True))
            with LOCK:
                if signature in seen or count >= 40:
                    return
                seen.add(signature)
                count += 1
            emit({'event': 'source', 'source': source})
    def cancelled():
        return stop.is_set() or time.monotonic() - started >= budget
    def query(row):
        try:
            result = json.loads(engine.query_site(row['id'], json.dumps(info), cancelled, Callback()))
            emit({'event': 'provider', 'provider': row['site'], 'status': result.get('status', 'error')})
        except Exception as error:
            emit({'event': 'provider', 'provider': row['site'], 'status': type(error).__name__})
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = [pool.submit(query, site) for site in sites]
        for future in concurrent.futures.as_completed(futures):
            future.result()
            if count >= 40 or cancelled():
                stop.set()
    emit({'event': 'done', 'count': count})

def main():
    import os
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'packages'))
    with contextlib.redirect_stdout(sys.stderr):
        import moviehub_vod
    if '--inventory' in sys.argv:
        emit({'providers': json.loads(moviehub_vod.search_providers())})
        return
    info = json.loads(sys.stdin.readline())
    with contextlib.redirect_stdout(sys.stderr):
        run(moviehub_vod, info)

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        emit({'event': 'error', 'error': type(error).__name__})
        raise SystemExit(1)
