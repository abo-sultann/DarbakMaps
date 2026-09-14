from pathlib import Path

path = Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java')
s = path.read_text(encoding='utf-8')


def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit('missing pattern: ' + label)
    s = s.replace(old, new, 1)

rep('''    private static final int MAX_ACTIVE_DRAW_POINTS = 4000;
    private static final int MAX_STORED_DRAW_POINTS = 10000;
''', '''    private static final int MAX_ACTIVE_DRAW_POINTS = 4000;
    private static final int MAX_STORED_DRAW_POINTS = 10000;
    private static final int MAX_ACTIVE_DRAW_SEGMENTS = 160;
    private static final int MAX_STORED_DRAW_SEGMENTS = 320;
''', 'layer constants')

rep('''        drawSegmentedTrack(points, storedTrackSegments, MAX_STORED_DRAW_POINTS, 230, 8, 62, 45, 8f);''',
    '''        drawSegmentedTrack(points, storedTrackSegments, MAX_STORED_DRAW_POINTS, MAX_STORED_DRAW_SEGMENTS, 230, 8, 62, 45, 8f);''', 'stored draw call')
rep('''        drawSegmentedTrack(points, activeTrackSegments, MAX_ACTIVE_DRAW_POINTS, 255, 236, 122, 37, 7f);''',
    '''        drawSegmentedTrack(points, activeTrackSegments, MAX_ACTIVE_DRAW_POINTS, MAX_ACTIVE_DRAW_SEGMENTS, 255, 236, 122, 37, 7f);''', 'active draw call')

rep('''    public void startTrackSegment() {
        activeTrack = newTrackPolyline(255, 236, 122, 37, 7f);
        activeTrackSegments.add(activeTrack);
        mapView.getLayerManager().getLayers().add(activeTrack);
    }
''', '''    public void startTrackSegment() {
        while (activeTrackSegments.size() >= MAX_ACTIVE_DRAW_SEGMENTS) {
            Polyline oldest = activeTrackSegments.remove(0);
            mapView.getLayerManager().getLayers().remove(oldest);
        }
        activeTrack = newTrackPolyline(255, 236, 122, 37, 7f);
        activeTrackSegments.add(activeTrack);
        mapView.getLayerManager().getLayers().add(activeTrack);
    }
''', 'start segment bound')

old = '''    private void drawSegmentedTrack(List<GeoPoint> points, List<Polyline> targetLayers, int maxPoints,
                                    int alpha, int red, int green, int blue, float width) {
        if (points == null || points.isEmpty()) return;
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) maxPoints));
        Polyline segment = null;
        for (int index = 0; index < points.size(); index++) {
            GeoPoint point = points.get(index);
            boolean boundary = point.segmentStart || index == 0;
            boolean keep = boundary || index == points.size() - 1 || index % step == 0
                    || (index + 1 < points.size() && points.get(index + 1).segmentStart);
            if (!keep) continue;
            if (segment == null || boundary) {
                segment = newTrackPolyline(alpha, red, green, blue, width);
                targetLayers.add(segment);
                mapView.getLayerManager().getLayers().add(segment);
            }
            segment.addPoint(new LatLong(point.latitude, point.longitude));
        }
    }
'''
new = '''    private void drawSegmentedTrack(List<GeoPoint> points, List<Polyline> targetLayers, int maxPoints,
                                    int maxSegments, int alpha, int red, int green, int blue, float width) {
        if (points == null || points.isEmpty()) return;
        int totalSegments = 0;
        for (int i = 0; i < points.size(); i++) {
            if (i == 0 || points.get(i).segmentStart) totalSegments++;
        }
        int segmentsToSkip = Math.max(0, totalSegments - maxSegments);
        int currentSegment = -1;
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) maxPoints));
        Polyline segment = null;
        for (int index = 0; index < points.size(); index++) {
            GeoPoint point = points.get(index);
            boolean boundary = point.segmentStart || index == 0;
            if (boundary) currentSegment++;
            if (currentSegment < segmentsToSkip) continue;
            boolean keep = boundary || index == points.size() - 1 || index % step == 0
                    || (index + 1 < points.size() && points.get(index + 1).segmentStart);
            if (!keep) continue;
            if (segment == null || boundary) {
                segment = newTrackPolyline(alpha, red, green, blue, width);
                targetLayers.add(segment);
                mapView.getLayerManager().getLayers().add(segment);
            }
            segment.addPoint(new LatLong(point.latitude, point.longitude));
        }
    }
'''
rep(old, new, 'segmented draw')

old = '''        Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        icon.setTextSize(30f);
        icon.setColor(Color.rgb(215, 173, 85));
        icon.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(glyph, 34f, 42f, icon);
'''
new = '''        drawSavedIcon(canvas, place.iconKey, 34f, 31f);
'''
rep(old, new, 'saved icon text')

anchor = '''    private org.mapsforge.core.graphics.Bitmap createNavigationTarget() {'''
methods = r'''    private void drawSavedIcon(Canvas canvas, String key, float cx, float cy) {
        Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
        gold.setColor(Color.rgb(215, 173, 85));
        gold.setStyle(Paint.Style.FILL);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.rgb(215, 173, 85));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3f);
        line.setStrokeCap(Paint.Cap.ROUND);

        if (PlaceRepository.ICON_QUAIL.equals(key)) {
            canvas.drawOval(new RectF(cx - 12f, cy - 5f, cx + 8f, cy + 8f), gold);
            canvas.drawCircle(cx + 9f, cy - 8f, 6f, gold);
            Path beak = new Path();
            beak.moveTo(cx + 14f, cy - 9f); beak.lineTo(cx + 21f, cy - 6f); beak.lineTo(cx + 14f, cy - 4f); beak.close();
            canvas.drawPath(beak, gold);
            Path wing = new Path();
            wing.moveTo(cx - 7f, cy - 3f); wing.quadTo(cx, cy + 2f, cx + 4f, cy + 5f);
            canvas.drawPath(wing, line);
            canvas.drawLine(cx - 5f, cy + 7f, cx - 7f, cy + 14f, line);
            canvas.drawLine(cx + 2f, cy + 7f, cx + 1f, cy + 14f, line);
        } else if (PlaceRepository.ICON_CAMP.equals(key)) {
            Path tent = new Path();
            tent.moveTo(cx, cy - 15f); tent.lineTo(cx - 16f, cy + 14f); tent.lineTo(cx + 16f, cy + 14f); tent.close();
            canvas.drawPath(tent, line);
            canvas.drawLine(cx, cy - 15f, cx, cy + 14f, line);
        } else if (PlaceRepository.ICON_WATER.equals(key)) {
            Path drop = new Path();
            drop.moveTo(cx, cy - 17f); drop.cubicTo(cx - 18f, cy + 2f, cx - 10f, cy + 16f, cx, cy + 17f);
            drop.cubicTo(cx + 10f, cy + 16f, cx + 18f, cy + 2f, cx, cy - 17f); drop.close();
            canvas.drawPath(drop, line);
        } else if (PlaceRepository.ICON_TREE.equals(key)) {
            canvas.drawRect(cx - 3f, cy + 2f, cx + 3f, cy + 16f, gold);
            canvas.drawCircle(cx, cy - 5f, 11f, gold);
            canvas.drawCircle(cx - 8f, cy + 1f, 8f, gold);
            canvas.drawCircle(cx + 8f, cy + 1f, 8f, gold);
        } else if (PlaceRepository.ICON_HUNTING.equals(key)) {
            canvas.drawCircle(cx, cy, 13f, line);
            canvas.drawCircle(cx, cy, 4f, line);
            canvas.drawLine(cx - 18f, cy, cx + 18f, cy, line);
            canvas.drawLine(cx, cy - 18f, cx, cy + 18f, line);
        } else {
            Path star = new Path();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2d + i * Math.PI / 5d;
                float radius = i % 2 == 0 ? 16f : 7f;
                float x = cx + (float) Math.cos(angle) * radius;
                float y = cy + (float) Math.sin(angle) * radius;
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            star.close();
            canvas.drawPath(star, gold);
        }
    }

'''
if anchor not in s:
    raise SystemExit('missing icon method anchor')
s = s.replace(anchor, methods + anchor, 1)

# remove now-unused glyph local variable
s = s.replace('''        String glyph = PlaceRepository.iconGlyph(place.iconKey);\n''', '')
path.write_text(s, encoding='utf-8')
