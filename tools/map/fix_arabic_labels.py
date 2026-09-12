#!/usr/bin/env python3
import html
import re
import sys
from pathlib import Path

NAME_TAG = re.compile(r'^(\s*<tag\s+k="name"\s+v=")([^"]*)("\s*/>\s*)$')
ARABIC = re.compile(r'[\u0600-\u06ff]')
MOJIBAKE = re.compile(r'[ÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ]')


def repair(value: str) -> str:
    decoded = html.unescape(value)
    # Garmin Arabic maps commonly expose cp1256 bytes through a cp1252 decode.
    # Reverse that only when it makes the result more Arabic and less mojibake.
    try:
        candidate = decoded.encode('cp1252').decode('cp1256')
    except (UnicodeEncodeError, UnicodeDecodeError):
        return decoded
    before_ar = len(ARABIC.findall(decoded))
    after_ar = len(ARABIC.findall(candidate))
    before_bad = len(MOJIBAKE.findall(decoded))
    after_bad = len(MOJIBAKE.findall(candidate))
    if after_ar > before_ar and (before_bad > 0 or after_bad < before_bad):
        return candidate
    return decoded


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: fix_arabic_labels.py input.osm output.osm', file=sys.stderr)
        return 64
    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    changed = 0
    total = 0
    with source.open('r', encoding='utf-8', errors='strict') as src, target.open('w', encoding='utf-8', newline='\n') as dst:
        for line in src:
            match = NAME_TAG.match(line.rstrip('\n'))
            if not match:
                dst.write(line)
                continue
            total += 1
            original = html.unescape(match.group(2))
            fixed = repair(match.group(2))
            if fixed != original:
                changed += 1
            escaped = html.escape(fixed, quote=True)
            dst.write(f'{match.group(1)}{escaped}{match.group(3)}\n')
    print(f'Arabic label repair: changed {changed}/{total} name tags')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
