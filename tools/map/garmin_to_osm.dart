import 'dart:convert';
import 'dart:io';

import 'package:garmin_img/garmin_img.dart';

class Classification {
  final Map<String, String> tags;
  const Classification(this.tags);
}

typedef GeoPoint = ({double lat, double lng});

const double minLat = 14.0;
const double maxLat = 33.5;
const double minLon = 33.0;
const double maxLon = 58.0;
const double quantizeScale = 1000000.0; // ~0.11 m latitude resolution.
const double largeGeometryTolerance = 0.00002; // roughly 2 m.
const int largeGeometryThreshold = 5000;

bool validXml10Rune(int rune) =>
    rune == 0x09 ||
    rune == 0x0A ||
    rune == 0x0D ||
    (rune >= 0x20 && rune <= 0xD7FF) ||
    (rune >= 0xE000 && rune <= 0xFFFD) ||
    (rune >= 0x10000 && rune <= 0x10FFFF);

bool validCoordinate(LatLng point) =>
    point.lat.isFinite &&
    point.lng.isFinite &&
    point.lat >= minLat &&
    point.lat <= maxLat &&
    point.lng >= minLon &&
    point.lng <= maxLon;

String xmlSafe(String value) {
  final clean = String.fromCharCodes(value.runes.where(validXml10Rune));
  return clean
      .replaceAll('&', '&amp;')
      .replaceAll('"', '&quot;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;');
}

String cleanLabel(String? raw) {
  if (raw == null) return '';
  var value = String.fromCharCodes(raw.runes.where(validXml10Rune)).trim();
  value = value.replaceAll(RegExp(r'\s+'), ' ');
  return value;
}

double _q(double value) => (value * quantizeScale).round() / quantizeScale;

bool _same(GeoPoint a, GeoPoint b) => a.lat == b.lat && a.lng == b.lng;

double _pointSegmentDistanceSquared(GeoPoint p, GeoPoint a, GeoPoint b) {
  final dx = b.lng - a.lng;
  final dy = b.lat - a.lat;
  if (dx == 0 && dy == 0) {
    final px = p.lng - a.lng;
    final py = p.lat - a.lat;
    return px * px + py * py;
  }
  var t = ((p.lng - a.lng) * dx + (p.lat - a.lat) * dy) / (dx * dx + dy * dy);
  if (t < 0) t = 0;
  if (t > 1) t = 1;
  final projLng = a.lng + t * dx;
  final projLat = a.lat + t * dy;
  final ex = p.lng - projLng;
  final ey = p.lat - projLat;
  return ex * ex + ey * ey;
}

List<GeoPoint> _rdp(List<GeoPoint> points, double tolerance) {
  if (points.length <= 2) return List<GeoPoint>.from(points);
  final keep = List<bool>.filled(points.length, false);
  keep[0] = true;
  keep[points.length - 1] = true;
  final toleranceSquared = tolerance * tolerance;
  final stack = <(int, int)>[(0, points.length - 1)];
  while (stack.isNotEmpty) {
    final (start, end) = stack.removeLast();
    var maxDistance = 0.0;
    var maxIndex = -1;
    for (var i = start + 1; i < end; i++) {
      final distance = _pointSegmentDistanceSquared(points[i], points[start], points[end]);
      if (distance > maxDistance) {
        maxDistance = distance;
        maxIndex = i;
      }
    }
    if (maxIndex >= 0 && maxDistance > toleranceSquared) {
      keep[maxIndex] = true;
      stack.add((start, maxIndex));
      stack.add((maxIndex, end));
    }
  }
  final result = <GeoPoint>[];
  for (var i = 0; i < points.length; i++) {
    if (keep[i]) result.add(points[i]);
  }
  return result;
}

List<GeoPoint> normalizeGeometry(List<LatLng> raw, {required bool polygon}) {
  final points = <GeoPoint>[];
  for (final p in raw) {
    final point = (lat: _q(p.lat), lng: _q(p.lng));
    if (points.isEmpty || !_same(points.last, point)) {
      points.add(point);
    }
  }

  // Remove immediate A-B-A spikes that are common in legacy Garmin geometry.
  if (points.length >= 3) {
    var i = 1;
    while (i < points.length - 1) {
      if (_same(points[i - 1], points[i + 1])) {
        points.removeAt(i);
        if (i > 1) i--;
      } else {
        i++;
      }
    }
  }

  if (points.length > largeGeometryThreshold) {
    if (polygon && points.length >= 4) {
      final ring = List<GeoPoint>.from(points);
      if (_same(ring.first, ring.last)) ring.removeLast();
      if (ring.length >= 3) {
        final simplified = _rdp([...ring, ring.first], largeGeometryTolerance);
        if (simplified.length >= 4) {
          simplified.removeLast();
          return simplified;
        }
      }
    } else {
      return _rdp(points, largeGeometryTolerance);
    }
  }
  return points;
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
    return const Classification({'place': 'locality'});
  }
  return null;
}

Classification? classifyLine(int type, String label) {
  switch (type) {
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
  if ((type >= 0x01 && type <= 0x09) ||
      type == 0x0b || type == 0x0c || type == 0x14 || type == 0x15 ||
      (type >= 0x1c && type <= 0x1e) ||
      (type >= 0x20 && type <= 0x25) ||
      type == 0x27 || type == 0x28 || type == 0x29) {
    return null;
  }
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
  final polygonTypeNames = img.polygonNames;
  final sink = File(output).openWrite();
  var nextNodeId = 4000000000000;
  var nextWayId = 4500000000000;
  final seen = <String>{};
  final keptByKind = <String, int>{};
  final skippedByType = <String, int>{};
  final keptByType = <String, int>{};
  final invalidByType = <String, int>{};
  var decoded = 0;
  var duplicates = 0;
  var invalidFeatures = 0;
  var simplifiedFeatures = 0;
  var removedGeometryPoints = 0;

  sink.writeln('<?xml version="1.0" encoding="UTF-8"?>');
  sink.writeln('<osm version="0.6" generator="DarbakMaps-Mishari">');

  try {
    for (final map in img.maps) {
      for (final f in map.features()) {
        decoded++;
        final typeKey = '0x${f.type.toRadixString(16)}';

        if (f.points.isEmpty || f.points.any((p) => !validCoordinate(p))) {
          invalidFeatures++;
          invalidByType[typeKey] = (invalidByType[typeKey] ?? 0) + 1;
          continue;
        }

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
            c = classifyPolygon(f.type, polygonTypeNames[f.type]);
        }
        if (c == null) {
          skippedByType[typeKey] = (skippedByType[typeKey] ?? 0) + 1;
          continue;
        }

        final points = normalizeGeometry(f.points, polygon: f.kind == FeatureKind.polygon);
        removedGeometryPoints += f.points.length - points.length;
        if (points.length < f.points.length) simplifiedFeatures++;

        if (f.kind == FeatureKind.polyline && points.length < 2) {
          skippedByType[typeKey] = (skippedByType[typeKey] ?? 0) + 1;
          continue;
        }
        if (f.kind == FeatureKind.polygon && points.length < 3) {
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
          final id = nextNodeId++;
          final p = points.first;
          sink.writeln('  <node id="$id" lat="${p.lat}" lon="${p.lng}">');
          for (final e in tags.entries) {
            sink.writeln('    <tag k="${xmlSafe(e.key)}" v="${xmlSafe(e.value)}"/>');
          }
          sink.writeln('  </node>');
          continue;
        }

        final nodeIds = <int>[];
        for (final p in points) {
          final nodeId = nextNodeId++;
          nodeIds.add(nodeId);
          sink.writeln('  <node id="$nodeId" lat="${p.lat}" lon="${p.lng}"/>');
        }
        if (f.kind == FeatureKind.polygon) {
          nodeIds.add(nodeIds.first);
        }
        final wayId = nextWayId++;
        sink.writeln('  <way id="$wayId">');
        for (final nodeId in nodeIds) {
          sink.writeln('    <nd ref="$nodeId"/>');
        }
        for (final e in tags.entries) {
          sink.writeln('    <tag k="${xmlSafe(e.key)}" v="${xmlSafe(e.value)}"/>');
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
    'coordinateFilter': {
      'minLat': minLat,
      'maxLat': maxLat,
      'minLon': minLon,
      'maxLon': maxLon,
    },
    'geometryNormalization': {
      'coordinateDecimals': 6,
      'largeGeometryThreshold': largeGeometryThreshold,
      'rdpToleranceDegrees': largeGeometryTolerance,
      'simplifiedFeatures': simplifiedFeatures,
      'removedGeometryPoints': removedGeometryPoints,
    },
    'idPolicy': {
      'nodeStart': 4000000000000,
      'wayStart': 4500000000000,
    },
    'decodedFeatures': decoded,
    'duplicateFeaturesSkipped': duplicates,
    'invalidFeaturesSkipped': invalidFeatures,
    'invalidByGarminType': invalidByType,
    'keptByKind': keptByKind,
    'keptByGarminType': keptByType,
    'skippedByGarminType': skippedByType,
    'polygonTypeNames': polygonTypeNames.map((k, v) => MapEntry('0x${k.toRadixString(16)}', v)),
  };
  await File(reportPath).writeAsString(const JsonEncoder.withIndent('  ').convert(report));
  stderr.writeln(
      'Darbak Mishari: decoded=$decoded kept=${keptByKind.values.fold<int>(0, (a, b) => a + b)} duplicates=$duplicates invalid=$invalidFeatures simplified=$simplifiedFeatures removedPoints=$removedGeometryPoints');
}
