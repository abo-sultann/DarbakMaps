from pathlib import Path

p=Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java')
s=p.read_text(encoding='utf-8')
s=s.replace('''import com.abosultan.darbakmaps.data.GeoPoint;\nimport com.abosultan.darbakmaps.data.PlaceRepository;\n''','''import com.abosultan.darbakmaps.data.GeoPoint;\nimport com.abosultan.darbakmaps.data.PlaceRepository;\nimport com.abosultan.darbakmaps.data.SavedPlaceSpatialSelector;\nimport com.abosultan.darbakmaps.data.TrackDisplayPolicy;\n''',1)
old='''        List<PlaceRepository.Place> candidates = new ArrayList<>();\n        for (PlaceRepository.Place place : allSavedPlaces) {\n            if (distanceMeters(center.latitude, center.longitude, place.latitude, place.longitude) <= radiusMeters) {\n                candidates.add(place);\n            }\n        }\n        Collections.sort(candidates, Comparator.comparingDouble(\n                place -> distanceMeters(center.latitude, center.longitude, place.latitude, place.longitude)));\n        if (candidates.size() > MAX_VISIBLE_SAVED_MARKERS) {\n            candidates = new ArrayList<>(candidates.subList(0, MAX_VISIBLE_SAVED_MARKERS));\n        }\n'''
new='''        List<PlaceRepository.Place> candidates = SavedPlaceSpatialSelector.select(\n                allSavedPlaces, center.latitude, center.longitude, radiusMeters, MAX_VISIBLE_SAVED_MARKERS);\n'''
if old not in s: raise SystemExit('saved viewport block missing')
s=s.replace(old,new,1)
old='''        int totalSegments = 0;\n        for (int i = 0; i < points.size(); i++) {\n            if (i == 0 || points.get(i).segmentStart) totalSegments++;\n        }\n        int segmentsToSkip = Math.max(0, totalSegments - maxSegments);\n        int currentSegment = -1;\n        int step = Math.max(1, (int) Math.ceil(points.size() / (double) maxPoints));\n        Polyline segment = null;\n        for (int index = 0; index < points.size(); index++) {\n            GeoPoint point = points.get(index);\n            boolean boundary = point.segmentStart || index == 0;\n            if (boundary) currentSegment++;\n            if (currentSegment < segmentsToSkip) continue;\n'''
new='''        int totalSegments = 0;\n        for (int i = 0; i < points.size(); i++) {\n            if (i == 0 || points.get(i).segmentStart) totalSegments++;\n        }\n        java.util.Set<Integer> selectedSegments = TrackDisplayPolicy.selectSegments(totalSegments, maxSegments);\n        int currentSegment = -1;\n        int step = Math.max(1, (int) Math.ceil(points.size() / (double) maxPoints));\n        Polyline segment = null;\n        for (int index = 0; index < points.size(); index++) {\n            GeoPoint point = points.get(index);\n            boolean boundary = point.segmentStart || index == 0;\n            if (boundary) { currentSegment++; segment = null; }\n            if (!selectedSegments.contains(currentSegment)) continue;\n'''
if old not in s: raise SystemExit('track segment selection block missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

Path('app/src/test/java/com/abosultan/darbakmaps/data/TrackDisplayPolicyTest.java').write_text(r'''package com.abosultan.darbakmaps.data;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class TrackDisplayPolicyTest {
    @Test public void layerCapKeepsWholeHistoryAndAllRecentSegments() {
        Set<Integer> selected = TrackDisplayPolicy.selectSegments(1000, 160);
        assertTrue(selected.size() <= 160);
        assertTrue(selected.contains(0));
        assertTrue(selected.contains(999));
        for (int i = 920; i < 1000; i++) assertTrue("recent segment " + i, selected.contains(i));
        boolean middleHistory = false;
        for (Integer i : selected) if (i > 200 && i < 700) middleHistory = true;
        assertTrue(middleHistory);
    }
}
''',encoding='utf-8')

Path('app/src/test/java/com/abosultan/darbakmaps/data/SavedPlaceSpatialSelectorTest.java').write_text(r'''package com.abosultan.darbakmaps.data;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SavedPlaceSpatialSelectorTest {
    @Test public void placeAfterFirst250StillAppearsWhenViewportMovesToIt() {
        List<PlaceRepository.Place> places = new ArrayList<>();
        for (int i = 0; i < 299; i++) {
            places.add(new PlaceRepository.Place("far-" + i, "بعيد " + i,
                    21.0 + i * 0.00001, 39.0, 1L,
                    PlaceRepository.ICON_STAR, "عام", ""));
        }
        PlaceRepository.Place late = new PlaceRepository.Place("late-299", "سمان متأخر",
                26.35, 43.97, 1L, PlaceRepository.ICON_QUAIL, "سمان", "");
        places.add(late);

        List<PlaceRepository.Place> visible = SavedPlaceSpatialSelector.select(
                places, 26.35, 43.97, 20_000d, 300);
        boolean found = false;
        for (PlaceRepository.Place p : visible) if (late.id.equals(p.id)) found = true;
        assertTrue("storage position must not hide a visible saved place", found);
    }
}
''',encoding='utf-8')
