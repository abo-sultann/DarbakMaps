import 'dart:convert';
import 'dart:io';

import 'package:garmin_img/garmin_img.dart';

class Classification {
  final Map<String, String> tags;
  const Classification(this.tags);
}

String xml(String value) => const HtmlEscape(HtmlEscapeMode.element).convert(value);

String cleanLabel(String? raw) {
  if (raw == null) return '';
  var value = raw.replaceAll(RegExp(r'[\x00-\x1E]'), ' ').trim();
  value = value.replaceAll(RegExp(r'\s+'), ' ');
  return value;
}

Classification? classifyPoint(int type, String label) {
  switch (type) {
    case 0x2f01:
      return const Classification({'amenity': 'fuel'});
    case 0x4800:
    case 0x2b03:
      return const Classification({'tourism': 'camp_site'});
    case 0x5000:
      return const Classification({'amenity': 'drinking_water'});
    case 0x5200:
      return const Classification({'tourism': 'viewpoint'});
    case 0x6300:
      return const Classification({'natural': 'peak'});
    case 0x6412:
      return const Classification({'tourism': 'information', 'information': 'guidepost'});
    case 0x6414:
      return const Classification({'man_made': 'water_well'});
    case 0x6616:
      return const Classification({'natural': 'peak'});
    case 0x660a:
      return const Classification({'natural': 'wood'});
    case 0x6604:
      return const Classification({'natural': 'beach'});
  }
  if (label.isNotEmpty) {
    // Named desert POIs are valuable even when a custom Garmin type is used.
    return const Classification({'place': 'locality'});
  }
  return null;
}

Classification? classifyLine(int type, String label) {
  switch (type) {
    // OSM is authoritative for paved roads. Keep only Mishari desert additions.
    case 0x0a:
      return const Classification({'highway': 'track', 'surface': 'unpaved'});
    case 0x0f:
      return const Classification({'highway': 'track', 'tracktype': 'grade3'});
    case 0x13:
      return const Classification({'highway': 'track', 'tracktype': 'grade4'});
    case 0x16:
      return const Classification({'highway': 'path'});
    case 0x18:
      return const Classification({'waterway': 'stream'});
    case 0x1f:
      return const Classification({'waterway': 'river'});
    case 0x26:
      return const Classification({'waterway': 'stream', 'intermittent': 'yes'});
  }
  // Explicitly discard paved roads, ramps, railways, boundaries, contours and
  // infrastructure that OSM already maintains more accurately.
  if ((type >= 0x01 && type <= 0x09) ||
      type == 0x0b || type == 0x0c || type == 0x14 || type == 0x15 ||
      (type >= 0x1c && type <= 0x1e) ||
      (type >= 0x20 && type <= 0x25) ||
      type == 0x27 || type == 0x28 || type == 0x29) {
    return null;
  }
  // Unknown named lines from custom Mishari types are kept as off-road tracks.
  if (label.isNotEmpty) {
    return const Classification({'highway': 'track', 'surface': 'unpaved'});
  }
  return null;
}

Classification? classifyPolygon(int type, String? typName) {
  final name = (typName ?? '').toLowerCase();
  if (name.contains('sand') || name.contains('dune')) {
    return const Classification({'natural': 'sand'});
  }
  if (name.contains('rock')) {
    return const Classification({'natural': 'bare_rock'});
  }
  if (name.contains('scrub') || name.contains('bush')) {
    return const Classification({'natural': 'scrub'});
  }
  if (name.contains('wetland') || name.contains('marsh')) {
    return const Classification({'natural': 'wetland'});
  }
  if (name.contains('wadi') || name.contains('valley')) {
    return const Classification({'natural': 'valley'});
  }
  switch (type) {
    case 0x3c:
    case 0x41:
      return const Classification({'natural': 'water'});
    case 0x46:
      return const Classification({'natural': 'water', 'water': 'river'});
    case 0x4c:
      return const Classification({'natural': 'water', 'intermittent': 'yes'});
    case 0x4e:
      return const Classification({'landuse': 'orchard'});
    case 0x4f:
      return const Classification({'natural': 'scrub'});
    case 0x50:
      return const Classification({'natural': 'wood'});
    case 0x51:
      return const Classification({'natural': 'wetland'});
  }
  return null;
}

String signature(ImgFeature f, String label) {
  final first = f.points.isEmpty ? null : f.points.first;
  final last = f.points.isEmpty ? null : f.points.last;
  String coord(LatLng? p) => p == null
      ? '-'
      : '${p.lat.toStringAsFixed(5)},${p.lng.toStringAsFixed(5)}';
  return '${f.kind.name}|${f.type}|$label|${f.points.length}|${coord(first)}|${coord(last)}';
}

Future<void> main(List<String> args) async {
  if (args.length < 2) {
    stderr.writeln('usage: dart run garmin_to_osm.dart <input.img> <output.osm> [report.json]');
    exit(64);
  }
  final input = args[0];
  final output = args[1];
  final reportPath = args.length >= 3 ? args[2] : '$output.report.json';

  final img = await GarminImg.open(input);
  final sink = File(output).openWrite();
  var nextId = -1;
  final seen = <String>{};
  final keptByKind = <String, int>{};
  final skippedByType = <String, int>{};
  final keptByType = <String, int>{};
  var decoded = 0;
  var duplicates = 0;

  sink.writeln('<?xml version="1.0" encoding="UTF-8"?>');
  sink.writeln('<osm version="0.6" generator="DarbakMaps-Mishari">');

  try {
    for (final map in img.maps) {
      for (final f in map.features()) {
        decoded++;
        final label = cleanLabel(f.label);
        final sig = signature(f, label);
        if (!seen.add(sig)) {
          duplicates++;
          continue;
        }

        Classification? c;
        switch (f.kind) {
          case FeatureKind.point:
            c = classifyPoint(f.type, label);
          case FeatureKind.polyline:
            c = classifyLine(f.type, label);
          case FeatureKind.polygon:
            c = classifyPolygon(f.type, img.polygonNames[f.type]);
        }
        final typeKey = '0x${f.type.toRadixString(16)}';
        if (c == null || f.points.isEmpty) {
          skippedByType[typeKey] = (skippedByType[typeKey] ?? 0) + 1;
          continue;
        }

        final tags = <String, String>{...c.tags};
        if (label.isNotEmpty) tags['name'] = label;
        tags['source'] = 'Al Mishari';
        tags['darbak_source'] = 'mishari';
        tags['garmin_type'] = typeKey;

        keptByKind[f.kind.name] = (keptByKind[f.kind.name] ?? 0) + 1;
        keptByType[typeKey] = (keptByType[typeKey] ?? 0) + 1;

        if (f.kind == FeatureKind.point) {
          final id = nextId--;
          final p = f.points.first;
          sink.writeln('  <node id="$id" lat="${p.lat}" lon="${p.lng}">');
          for (final e in tags.entries) {
            sink.writeln('    <tag k="${xml(e.key)}" v="${xml(e.value)}"/>');
          }
          sink.writeln('  </node>');
          continue;
        }

        if (f.kind == FeatureKind.polyline && f.points.length < 2) continue;
        if (f.kind == FeatureKind.polygon && f.points.length < 3) continue;

        final nodeIds = <int>[];
        for (final p in f.points) {
          final nodeId = nextId--;
          nodeIds.add(nodeId);
          sink.writeln('  <node id="$nodeId" lat="${p.lat}" lon="${p.lng}"/>');
        }
        if (f.kind == FeatureKind.polygon && nodeIds.first != nodeIds.last) {
          nodeIds.add(nodeIds.first);
        }
        final wayId = nextId--;
        sink.writeln('  <way id="$wayId">');
        for (final nodeId in nodeIds) {
          sink.writeln('    <nd ref="$nodeId"/>');
        }
        for (final e in tags.entries) {
          sink.writeln('    <tag k="${xml(e.key)}" v="${xml(e.value)}"/>');
        }
        sink.writeln('  </way>');
      }
    }
  } finally {
    sink.writeln('</osm>');
    await sink.flush();
    await sink.close();
    img.close();
  }

  final report = {
    'input': input,
    'decodedFeatures': decoded,
    'duplicateFeaturesSkipped': duplicates,
    'keptByKind': keptByKind,
    'keptByGarminType': keptByType,
    'skippedByGarminType': skippedByType,
    'polygonTypeNames': img.polygonNames.map((k, v) => MapEntry('0x${k.toRadixString(16)}', v)),
  };
  await File(reportPath).writeAsString(const JsonEncoder.withIndent('  ').convert(report));
  stderr.writeln('Darbak Mishari: decoded=$decoded kept=${keptByKind.values.fold<int>(0, (a, b) => a + b)} duplicates=$duplicates');
}
