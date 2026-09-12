#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36 DarbakMaps/1.0'


def resolve(session: requests.Session, page_url: str) -> str:
    response = session.get(page_url, timeout=45, allow_redirects=True)
    response.raise_for_status()
    content_type = response.headers.get('content-type', '')
    if 'application/' in content_type and 'text/html' not in content_type:
        return response.url
    soup = BeautifulSoup(response.text, 'html.parser')
    button = soup.select_one('#downloadButton') or soup.select_one('a.input')
    if button and button.get('href'):
        return urljoin(response.url, button['href'])
    patterns = [
        r'https://download[^"\'<> ]+mediafire\.com/[^"\'<> ]+',
        r'https://[^"\'<> ]+\.mediafire\.com/[^"\'<> ]+',
    ]
    for pattern in patterns:
        match = re.search(pattern, response.text)
        if match:
            return match.group(0).replace('\\/', '/')
    raise RuntimeError('MediaFire direct download link was not found')


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: download_mediafire.py page_url output_file', file=sys.stderr)
        return 64
    page_url, output = sys.argv[1], Path(sys.argv[2])
    session = requests.Session()
    session.headers.update({'User-Agent': UA, 'Accept-Language': 'en-US,en;q=0.8'})
    direct = resolve(session, page_url)
    print(f'MediaFire direct URL resolved: {direct.split("?")[0]}')
    with session.get(direct, stream=True, timeout=90, allow_redirects=True) as response:
        response.raise_for_status()
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open('wb') as fh:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    fh.write(chunk)
    if output.stat().st_size < 1024 * 1024:
        raise RuntimeError(f'downloaded file is unexpectedly small: {output.stat().st_size} bytes')
    print(f'Downloaded {output} ({output.stat().st_size} bytes)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
