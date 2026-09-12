#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36 DarbakMaps/1.0'

DIRECT_PATTERNS = [
    r'https://download[^"\'<> )\]]*mediafire\.com/[^"\'<> )\]]+',
    r'https://[^"\'<> )\]]*\.mediafire\.com/[^"\'<> )\]]+',
]


def direct_from_text(text: str) -> str | None:
    normalized = text.replace('\\/', '/').replace('&amp;', '&')
    for pattern in DIRECT_PATTERNS:
        match = re.search(pattern, normalized)
        if match:
            return match.group(0).rstrip('.,')
    return None


def resolve_native(session: requests.Session, page_url: str) -> str:
    response = session.get(page_url, timeout=45, allow_redirects=True)
    response.raise_for_status()
    content_type = response.headers.get('content-type', '')
    if 'application/' in content_type and 'text/html' not in content_type:
        return response.url
    soup = BeautifulSoup(response.text, 'html.parser')
    button = soup.select_one('#downloadButton') or soup.select_one('a.input')
    if button and button.get('href'):
        return urljoin(response.url, button['href'])
    direct = direct_from_text(response.text)
    if direct:
        return direct
    raise RuntimeError('MediaFire direct download link was not found')


def resolve_via_reader(session: requests.Session, page_url: str) -> str:
    reader_url = 'https://r.jina.ai/' + page_url
    response = session.get(
        reader_url,
        timeout=90,
        headers={
            'User-Agent': UA,
            'Accept': 'text/plain, text/markdown;q=0.9, */*;q=0.1',
            'X-Engine': 'browser',
        },
    )
    response.raise_for_status()
    direct = direct_from_text(response.text)
    if direct:
        return direct

    # Jina can expose a canonical /file/... MediaFire URL instead of the CDN URL.
    canonical = re.search(r'https://www\.mediafire\.com/file/[^\s)\]]+', response.text)
    if canonical:
        return resolve_native(session, canonical.group(0).rstrip('.,'))
    raise RuntimeError('Reader fallback did not expose a MediaFire download URL')


def resolve(session: requests.Session, page_url: str) -> str:
    try:
        return resolve_native(session, page_url)
    except (requests.RequestException, RuntimeError) as first_error:
        print(f'Native MediaFire resolve failed: {first_error}; using reader fallback', file=sys.stderr)
        return resolve_via_reader(session, page_url)


def download(session: requests.Session, direct: str, output: Path) -> None:
    with session.get(direct, stream=True, timeout=120, allow_redirects=True) as response:
        response.raise_for_status()
        content_type = response.headers.get('content-type', '')
        if 'text/html' in content_type.lower():
            raise RuntimeError('MediaFire returned HTML instead of archive bytes')
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open('wb') as fh:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    fh.write(chunk)


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: download_mediafire.py page_url output_file', file=sys.stderr)
        return 64
    page_url, output = sys.argv[1], Path(sys.argv[2])
    session = requests.Session()
    session.headers.update({'User-Agent': UA, 'Accept-Language': 'en-US,en;q=0.8'})
    direct = resolve(session, page_url)
    print(f'MediaFire direct URL resolved: {direct.split("?")[0]}')
    download(session, direct, output)
    if output.stat().st_size < 1024 * 1024:
        raise RuntimeError(f'downloaded file is unexpectedly small: {output.stat().st_size} bytes')
    print(f'Downloaded {output} ({output.stat().st_size} bytes)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
