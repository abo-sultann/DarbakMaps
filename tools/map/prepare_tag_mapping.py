#!/usr/bin/env python3
from pathlib import Path
import sys

POIS = '''
    <!-- DARBAK PRIVATE DESERT POIS -->
    <pois>
        <osm-tag key="man_made" value="water_well" zoom-appear="11"/>
        <osm-tag key="natural" value="hill" zoom-appear="11"/>
    </pois>

'''

WAYS = '''
    <!-- DARBAK PRIVATE DESERT WAYS -->
    <ways>
        <osm-tag key="natural" value="sand" zoom-appear="7"/>
        <osm-tag key="natural" value="dune" zoom-appear="9"/>
        <osm-tag key="natural" value="bare_rock" zoom-appear="9"/>
        <osm-tag key="natural" value="rock" zoom-appear="10"/>
        <osm-tag key="natural" value="salt_pond" zoom-appear="9"/>
        <osm-tag key="natural" value="valley" zoom-appear="9"/>
        <osm-tag key="waterway" value="wadi" zoom-appear="9"/>
        <osm-tag key="intermittent" value="yes" renderable="false"/>
        <osm-tag key="surface" value="unpaved" renderable="false"/>
        <osm-tag key="tracktype" value="grade1" renderable="false"/>
        <osm-tag key="tracktype" value="grade2" renderable="false"/>
        <osm-tag key="tracktype" value="grade3" renderable="false"/>
        <osm-tag key="tracktype" value="grade4" renderable="false"/>
        <osm-tag key="tracktype" value="grade5" renderable="false"/>
    </ways>

'''


def main() -> int:
    if len(sys.argv) != 3:
        print('usage: prepare_tag_mapping.py default.xml output.xml', file=sys.stderr)
        return 64
    source = Path(sys.argv[1]).read_text(encoding='utf-8')
    marker = '    <!-- ************* WAYS *************** -->'
    if marker not in source or '</tag-mapping>' not in source:
        raise SystemExit('unexpected Mapsforge tag-mapping.xml structure')
    source = source.replace(marker, POIS + marker, 1)
    source = source.replace('</tag-mapping>', WAYS + '</tag-mapping>', 1)
    Path(sys.argv[2]).write_text(source, encoding='utf-8')
    print('Darbak desert tags injected')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
