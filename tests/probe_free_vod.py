"""Optional regional provider smoke check; no key, account or region override. Does not launch Minecraft."""
import json
import urllib.request
import urllib.parse
import uuid

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0'

def read(url, token=None, limit=20_000_000):
    headers = {'User-Agent': UA, 'Accept': 'application/json, */*'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=25) as response:
        data = response.read(limit + 1)
        if len(data) > limit:
            raise ValueError('Response too large')
        return data.decode('utf-8')

def main():
    boot = json.loads(read('https://boot.pluto.tv/v4/start?' + urllib.parse.urlencode({
        'appName': 'web', 'appVersion': '9.0.0', 'deviceVersion': '145.0', 'deviceModel': 'web',
        'deviceMake': 'firefox', 'deviceType': 'web', 'clientModelNumber': '1.0.0',
        'serverSideAds': 'true', 'clientID': str(uuid.uuid4()),
    })))
    token = boot['sessionToken']
    catalogue = json.loads(read('https://api.pluto.tv/v3/vod/categories?includeItems=true&deviceType=web', token))
    unique = {row.get('_id', row.get('id')): row for cat in catalogue.get('categories', [])
              if not cat.get('plutoOfficeOnly') for row in cat.get('items', []) if not row.get('plutoOfficeOnly')}
    movies = [r for r in unique.values() if r.get('type') == 'movie' and r.get('stitched', {}).get('urls')]
    shows = [r for r in unique.values() if r.get('type') == 'series']
    print('Probe region:', boot.get('session', {}).get('countryCode', 'unknown'))
    print('Free VOD categories:', len(catalogue.get('categories', [])), 'movies:', len(movies), 'shows:', len(shows))
    if not movies:
        raise ValueError('No regional playable movies returned')
    source = movies[0]['stitched']['urls'][0]['url']
    path = urllib.parse.urlsplit(source).path
    if path.startswith('/stitch/'):
        path = '/v2' + path
    stitcher = boot['servers']['stitcher'].rstrip('/')
    host = urllib.parse.urlsplit(stitcher)
    if host.scheme != 'https' or not host.hostname.endswith('.pluto.tv'):
        raise ValueError('Unexpected provider endpoint')
    manifest = read(stitcher + path + '?' + urllib.parse.urlencode({'jwt': token, 'masterJWTPassthrough': 'true'}), limit=1_000_000)
    if not manifest.lstrip().startswith('#EXTM3U'):
        raise ValueError('Provider did not return an HLS manifest')
    print('PASS: a provider-returned free movie yields an HLS manifest. This is not an in-game playback test.')

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Never print an exception URL: it may contain the anonymous session token.
        print('Free-provider smoke check unavailable:', type(error).__name__)
        raise SystemExit(1)
