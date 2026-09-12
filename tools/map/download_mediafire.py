#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36 DarbakMaps/1.0'
DIRECT_RE = re.compile(r'https://download\d+\.mediafire\.com/[^"\'<> )\]]+', re.I)


def direct_from_text(text: str, quick_key: str | None = None) -> str | None:
    normalized = text.replace('\\/', '/').replace('&amp;', '&')
    for match in DIRECT_RE.finditer(normalized):
        url = match.group(0).rstrip('.,')
        if quick_key is None or quick_key in url:
            return url
    return None


def quick_key_from_url(page_url: str) -> str | None:
    match = re.search(r'(?:\?|/file/)([A-Za-z0-9_-]{10,})', page_url)
    return match.group(1) if match else None


def pick_download_anchor(response: requests.Response, quick_key: str | None = None) -> str | None:
    soup = BeautifulSoup(response.text, 'html.parser')
    selectors = ('#downloadButton', 'a.input', 'a.popsok', 'a[aria-label*="Download"]')
    for selector in selectors:
        button = soup.select_one(selector)
        if button and button.get('href'):
            href = urljoin(response.url, button['href'])
            if re.match(r'https://download\d+\.mediafire\.com/', href, re.I):
                if quick_key is None or quick_key in href:
                    return href
    for anchor in soup.find_all('a', href=True):
        href = urljoin(response.url, anchor['href'])
        if re.match(r'https://download\d+\.mediafire\.com/', href, re.I):
            if quick_key is None or quick_key in href:
                return href
    return direct_from_text(response.text, quick_key)


def resolve_native(session: requests.Session, page_url: str) -> str:
    quick_key = quick_key_from_url(page_url)
    response = session.get(page_url, timeout=45, allow_redirects=True)
    response.raise_for_status()
    content_type = response.headers.get('content-type', '')
    if 'application/' in content_type and 'text/html' not in content_type:
        return response.url
    direct = pick_download_anchor(response, quick_key)
    if direct:
        return direct
    raise RuntimeError('MediaFire direct download link was not found')


def resolve_via_repair(session: requests.Session, page_url: str) -> str:
    quick_key = quick_key_from_url(page_url)
    if not quick_key:
        raise RuntimeError('MediaFire quick key could not be extracted')
    repair = (
        'https://www.mediafire.com/download_repair.php'
        f'?qkey={quick_key}&origin=server_error&template=unselected'
    )
    response = session.get(
        repair,
        timeout=60,
        allow_redirects=True,
        headers={'User-Agent': UA, 'Referer': page_url},
    )
    response.raise_for_status()
    direct = pick_download_anchor(response, quick_key)
    if direct:
        return direct
    candidates = []
    soup = BeautifulSoup(response.text, 'html.parser')
    for anchor in soup.find_all('a', href=True):
        href = urljoin(response.url, anchor['href'])
        if 'download' in href.lower() or quick_key in href:
            candidates.append(href)
    if candidates:
        print('Repair candidate anchors:', file=sys.stderr)
        for href in candidates[:20]:
            print(f'  {href}', file=sys.stderr)
    raise RuntimeError('MediaFire repair endpoint did not issue a runner-local CDN link')


def resolve_via_reader(session: requests.Session, page_url: str) -> str:
    quick_key = quick_key_from_url(page_url)
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
    direct = direct_from_text(response.text, quick_key)
    if direct:
        return direct
    canonical = re.search(r'https://www\.mediafire\.com/file/[^\s)\]]+', response.text)
    if canonical:
        return resolve_native(session, canonical.group(0).rstrip('.,'))
    raise RuntimeError('Reader fallback did not expose a MediaFire CDN URL')


def resolve(session: requests.Session, page_url: str) -> str:
    errors = []
    for label, resolver in (
        ('native', resolve_native),
        ('repair', resolve_via_repair),
        ('reader', resolve_via_reader),
    ):
        try:
            direct = resolver(session, page_url)
            print(f'MediaFire resolver succeeded: {label}', file=sys.stderr)
            return direct
        except (requests.RequestException, RuntimeError) as error:
            errors.append(f'{label}: {error}')
            print(f'MediaFire resolver failed ({label}): {error}', file=sys.stderr)
    raise RuntimeError('All MediaFire resolvers failed: ' + ' | '.join(errors))


def download(session: requests.Session, direct: str, output: Path, page_url: str) -> None:
    headers = {
        'User-Agent': UA,
        'Referer': page_url,
        'Accept': 'application/octet-stream,application/x-rar-compressed,application/zip;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.8',
    }
    with session.get(direct, stream=True, timeout=120, allow_redirects=True, headers=headers) as response:
        response.raise_for_status()
        content_type = response.headers.get('content-type', '')
        if 'text/html' in content_type.lower():
            body = response.text[:600].replace('\n', ' ')
            print(f'MediaFire CDN returned HTML at {response.url}: {body}', file=sys.stderr)
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
    download(session, direct, output, page_url)
    if output.stat().st_size < 1024 * 1024:
        raise RuntimeError(f'downloaded file is unexpectedly small: {output.stat().st_size} bytes')
    print(f'Downloaded {output} ({output.stat().st_size} bytes)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
