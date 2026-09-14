from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing pattern {label} in {path}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

# 1) TrackJournal.preview: bounded streaming turn-aware sampling instead of every-Nth point.
p = Path('app/src/main/java/com/abosultan/darbakmaps/data/TrackJournal.java')
s = p.read_text(encoding='utf-8')
start = s.index('    public static List<GeoPoint> preview(File file, int limit) throws IOException {')
end = s.index('\n    /** Returns connected-track distance only;', start)
new_preview = r'''    public static List<GeoPoint> preview(File file, int limit) throws IOException {
        recoverInterruptedTrim(file);
        List<GeoPoint> points = new ArrayList<>();
        if (!file.isFile()) return points;
        final int boundedLimit = Math.max(16, limit);

        long count = 0L;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) if (parse(line, false) != null) count++;
        }
        if (count <= boundedLimit) {
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                String line;
                boolean segmentStart = true;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("#segment")) { segmentStart = true; continue; }
                    GeoPoint pnt = parse(line, segmentStart);
                    if (pnt == null) continue;
                    points.add(pnt);
                    segmentStart = false;
                }
            }
            return points;
        }

        // Streaming largest-turn-per-bucket sampler. Memory remains O(limit), while every segment
        // boundary and each bucket's strongest bend are preferred over blind every-Nth sampling.
        long bucketSize = Math.max(2L, (count + boundedLimit - 1L) / boundedLimit);
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            long validIndex = 0L;
            boolean segmentStart = true;
            GeoPoint previousKept = null;
            GeoPoint candidate = null;
            GeoPoint candidatePrev = null;
            double candidateTurn = -1d;
            long bucket = -1L;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#segment")) {
                    if (candidate != null) addPreviewPoint(points, candidate, candidate.segmentStart);
                    candidate = null; candidatePrev = null; candidateTurn = -1d;
                    previousKept = points.isEmpty() ? null : points.get(points.size() - 1);
                    segmentStart = true;
                    bucket = -1L;
                    continue;
                }
                GeoPoint current = parse(line, segmentStart);
                if (current == null) continue;
                long currentBucket = validIndex / bucketSize;
                if (segmentStart) {
                    addPreviewPoint(points, current, true);
                    previousKept = current;
                    candidate = null; candidatePrev = null; candidateTurn = -1d;
                    bucket = currentBucket;
                    segmentStart = false;
                    validIndex++;
                    continue;
                }
                if (bucket != currentBucket) {
                    if (candidate != null) {
                        addPreviewPoint(points, candidate, false);
                        previousKept = candidate;
                    }
                    candidate = current;
                    candidatePrev = previousKept;
                    candidateTurn = -1d;
                    bucket = currentBucket;
                } else {
                    double turn = triangleArea(previousKept, candidate, current);
                    if (candidate == null || turn >= candidateTurn) {
                        candidatePrev = previousKept;
                        candidate = current;
                        candidateTurn = turn;
                    }
                }
                validIndex++;
            }
            if (candidate != null) addPreviewPoint(points, candidate, candidate.segmentStart);
        }
        return points;
    }

    private static void addPreviewPoint(List<GeoPoint> points, GeoPoint point, boolean segmentStart) {
        if (point == null) return;
        if (!points.isEmpty()) {
            GeoPoint last = points.get(points.size() - 1);
            if (last.latitude == point.latitude && last.longitude == point.longitude && last.timeMillis == point.timeMillis) return;
        }
        points.add(new GeoPoint(point.latitude, point.longitude, point.timeMillis, segmentStart));
    }

    private static double triangleArea(GeoPoint a, GeoPoint b, GeoPoint c) {
        if (a == null || b == null || c == null) return 0d;
        double scale = Math.cos(Math.toRadians(b.latitude));
        double ax = a.longitude * scale, ay = a.latitude;
        double bx = b.longitude * scale, by = b.latitude;
        double cx = c.longitude * scale, cy = c.latitude;
        return Math.abs((ax - cx) * (by - ay) - (ax - bx) * (cy - ay));
    }
'''
s = s[:start] + new_preview + s[end:]
p.write_text(s, encoding='utf-8')

# 2) BackgroundTrackStore: committed generation + versioned preview snapshot.
Path('app/src/main/java/com/abosultan/darbakmaps/data/BackgroundTrackStore.java').write_text(r'''package com.abosultan.darbakmaps.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Persistent automatic-track store. The journal is the single source of truth for rendering. */
public final class BackgroundTrackStore {
    public static final double MAX_RETAINED_METERS = 1_000_000d;
    private static final double TRIM_TRIGGER_METERS = 1_001_000d;
    private static final String PREFS = "darbak_rolling_track";
    private static final String KEY_DISTANCE = "distance_m";
    private static final String KEY_LENGTH = "journal_length";
    private static final String KEY_GENERATION = "journal_generation";

    private BackgroundTrackStore() {}

    public static synchronized AppendResult append(Context context, Location location) throws IOException {
        return append(context, location, false, 0d);
    }

    public static synchronized AppendResult append(Context context, Location location, boolean newSegment) throws IOException {
        return append(context, location, newSegment, 0d);
    }

    public static synchronized AppendResult append(Context context, Location location, boolean newSegment,
                                                    double connectedMeters) throws IOException {
        if (location == null) return new AppendResult(generation(context), false, newSegment);
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            State reconciled = reconcileState(source, state);
            double total = reconciled.distance;

            TrackJournal.append(source, location.getLatitude(), location.getLongitude(), location.getTime(), newSegment);
            if (!newSegment && Double.isFinite(connectedMeters) && connectedMeters > 0d && connectedMeters < 2000d) {
                total += connectedMeters;
            }
            boolean trimmed = false;
            if (total > TRIM_TRIGGER_METERS) {
                total = TrackJournal.trimToDistance(source, MAX_RETAINED_METERS);
                trimmed = true;
            }
            long nextGeneration = Math.max(reconciled.generation + 1L, 1L);
            persistState(state, source, total, nextGeneration);
            return new AppendResult(nextGeneration, trimmed, newSegment);
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized Snapshot loadActiveSnapshot(Context context) {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            try {
                TrackJournal.recoverInterruptedTrim(source);
                SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                State reconciled = reconcileState(source, state);
                List<GeoPoint> points = TrackJournal.preview(source, 5000);
                return new Snapshot(reconciled.generation, points);
            } catch (IOException error) {
                return new Snapshot(generation(context), Collections.emptyList());
            }
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized List<GeoPoint> loadActive(Context context) {
        return loadActiveSnapshot(context).points;
    }

    public static synchronized long generation(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_GENERATION, 0L);
    }

    public static synchronized double retainedDistanceMeters(Context context) throws IOException {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            return reconcileState(source, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).distance;
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized File snapshotActive(Context context) throws IOException {
        DataStoreLock.lock();
        try {
            File source = activeFile(context);
            TrackJournal.recoverInterruptedTrim(source);
            if (!source.isFile() || source.length() == 0L) throw new IOException("لا يوجد مسار تلقائي محفوظ حتى الآن");
            File saved = new File(source.getParentFile(),
                    "مسار-محفوظ-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".gpx");
            TrackJournal.export(source, saved, "نسخة من مسار دربك التلقائي");
            return saved;
        } finally {
            DataStoreLock.unlock();
        }
    }

    public static synchronized File finalizeActive(Context context) throws IOException { return snapshotActive(context); }

    public static File activeFile(Context context) {
        return new File(new File(context.getFilesDir(), "tracks"), "active-track.csv");
    }

    private static State reconcileState(File source, SharedPreferences state) throws IOException {
        long generation = Math.max(0L, state.getLong(KEY_GENERATION, 0L));
        if (!source.isFile() || source.length() == 0L) {
            persistState(state, source, 0d, generation);
            return new State(0d, generation);
        }
        long knownLength = state.getLong(KEY_LENGTH, -1L);
        if (knownLength == source.length() && state.contains(KEY_DISTANCE)) {
            double distance = Math.max(0d, Double.longBitsToDouble(
                    state.getLong(KEY_DISTANCE, Double.doubleToLongBits(0d))));
            return new State(distance, generation);
        }
        // Journal bytes changed without the metadata commit (for example process death after fsync).
        // Reconcile from the authoritative file and advance generation so stale renders cannot win.
        double measured = TrackJournal.distanceMeters(source);
        long repairedGeneration = generation + 1L;
        persistState(state, source, measured, repairedGeneration);
        return new State(measured, repairedGeneration);
    }

    private static void persistState(SharedPreferences state, File source, double distance, long generation) throws IOException {
        boolean committed = state.edit()
                .putLong(KEY_DISTANCE, Double.doubleToLongBits(Math.max(0d, distance)))
                .putLong(KEY_LENGTH, source != null && source.isFile() ? source.length() : 0L)
                .putLong(KEY_GENERATION, Math.max(0L, generation))
                .commit();
        if (!committed) throw new IOException("تعذر تثبيت حالة سجل المسار");
    }

    private static final class State {
        final double distance; final long generation;
        State(double distance, long generation) { this.distance = distance; this.generation = generation; }
    }

    public static final class AppendResult {
        public final long generation;
        public final boolean trimmed;
        public final boolean segmentStart;
        AppendResult(long generation, boolean trimmed, boolean segmentStart) {
            this.generation = generation; this.trimmed = trimmed; this.segmentStart = segmentStart;
        }
    }

    public static final class Snapshot {
        public final long generation;
        public final List<GeoPoint> points;
        Snapshot(long generation, List<GeoPoint> points) {
            this.generation = generation;
            this.points = points == null ? Collections.emptyList() : points;
        }
    }
}
''', encoding='utf-8')

# 3) Service: broadcast only after a fix is committed to the journal.
p = Path('app/src/main/java/com/abosultan/darbakmaps/BackgroundTrackService.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''    public static final String ACTION_FINALIZE_RESULT = "com.abosultan.darbakmaps.TRACK_FINALIZE_RESULT";\n''', '''    public static final String ACTION_FINALIZE_RESULT = "com.abosultan.darbakmaps.TRACK_FINALIZE_RESULT";\n    public static final String ACTION_TRACK_COMMITTED = "com.abosultan.darbakmaps.TRACK_COMMITTED";\n    public static final String EXTRA_GENERATION = "generation";\n    public static final String EXTRA_TRIMMED = "trimmed";\n''', 1)
old = '''                BackgroundTrackStore.append(this, accepted, newSegment, connectedMeters);\n                if (newSegment) TrackSessionState.markSegmentWritten(this);\n                TrackSessionState.onCommittedFix(this, accepted);\n'''
new = '''                BackgroundTrackStore.AppendResult append = BackgroundTrackStore.append(\n                        this, accepted, newSegment, connectedMeters);\n                if (newSegment) TrackSessionState.markSegmentWritten(this);\n                TrackSessionState.onCommittedFix(this, accepted);\n                Intent committed = new Intent(ACTION_TRACK_COMMITTED);\n                committed.setPackage(getPackageName());\n                committed.putExtra(EXTRA_GENERATION, append.generation);\n                committed.putExtra(EXTRA_TRIMMED, append.trimmed);\n                sendBroadcast(committed);\n'''
if old not in s: raise SystemExit('service append pattern missing')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# 4) MainActivity: rendering comes from committed snapshots only; stale async loads cannot win.
p = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = s.replace('''import android.content.Intent;\n''', '''import android.content.Intent;\nimport android.content.BroadcastReceiver;\nimport android.content.Context;\nimport android.content.IntentFilter;\n''', 1)
s = s.replace('''import com.abosultan.darbakmaps.data.BackgroundTrackStore;\n''', '''import com.abosultan.darbakmaps.data.BackgroundTrackStore;\nimport com.abosultan.darbakmaps.data.TrackRenderGate;\n''', 1)
s = s.replace('''    private final OfflineMapSearchEngine searchEngine = new OfflineMapSearchEngine();\n''', '''    private final OfflineMapSearchEngine searchEngine = new OfflineMapSearchEngine();\n    private final TrackRenderGate trackRenderGate = new TrackRenderGate();\n    private boolean trackReceiverRegistered;\n    private final BroadcastReceiver trackCommitReceiver = new BroadcastReceiver() {\n        @Override public void onReceive(Context context, Intent intent) {\n            if (intent == null || !BackgroundTrackService.ACTION_TRACK_COMMITTED.equals(intent.getAction())) return;\n            long generation = intent.getLongExtra(BackgroundTrackService.EXTRA_GENERATION, 0L);\n            requestCommittedTrackRender(generation);\n        }\n    };\n''', 1)
old = '''            if (MapUiPreferences.backgroundTrackEnabled(this) && !TrackSessionState.isPaused(this) && mapController != null) {\n                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());\n            }\n'''
if old not in s: raise SystemExit('direct drawing pattern missing')
s = s.replace(old, '', 1)
old = '''    private void restoreActiveTrack() {\n        if (!MapUiPreferences.backgroundTrackEnabled(this) || mapController == null) return;\n        mapController.beginTrack();\n        ioExecutor.execute(() -> {\n            List<GeoPoint> points = BackgroundTrackStore.loadActive(this);\n            runOnUiThread(() -> {\n                if (!isActivityUnavailable() && mapController != null && points.size() >= 2) {\n                    mapController.showActiveTrack(points);\n                }\n            });\n        });\n    }\n'''
new = '''    private void restoreActiveTrack() {\n        if (!MapUiPreferences.backgroundTrackEnabled(this) || mapController == null) return;\n        requestCommittedTrackRender(BackgroundTrackStore.generation(this));\n    }\n\n    private void requestCommittedTrackRender(long minimumGeneration) {\n        if (mapController == null) return;\n        final long token = trackRenderGate.request(minimumGeneration);\n        ioExecutor.execute(() -> {\n            BackgroundTrackStore.Snapshot snapshot = BackgroundTrackStore.loadActiveSnapshot(this);\n            runOnUiThread(() -> {\n                if (isActivityUnavailable() || mapController == null) return;\n                if (!trackRenderGate.mayApply(token, snapshot.generation)) return;\n                mapController.showActiveTrack(snapshot.points);\n            });\n        });\n    }\n'''
if old not in s: raise SystemExit('restoreActiveTrack pattern missing')
s = s.replace(old, new, 1)
# Do not create a visual segment before the service commits one.
s = s.replace('''        if (!paused && mapController != null) mapController.startTrackSegment();\n''', '', 1)
# onResume registration + refresh
old = '''        if (initialized) {\n            BackgroundTrackService.ensureRunning(this);\n            syncBackgroundTrackUi();\n            showPendingTrackResult();\n        }\n'''
new = '''        if (initialized) {\n            if (!trackReceiverRegistered) {\n                registerReceiver(trackCommitReceiver, new IntentFilter(BackgroundTrackService.ACTION_TRACK_COMMITTED));\n                trackReceiverRegistered = true;\n            }\n            BackgroundTrackService.ensureRunning(this);\n            syncBackgroundTrackUi();\n            restoreActiveTrack();\n            showPendingTrackResult();\n        }\n'''
if old not in s: raise SystemExit('onResume pattern missing')
s = s.replace(old, new, 1)
old = '''    protected void onPause() {\n        if (locationController != null) {\n            locationController.stop();\n        }\n        super.onPause();\n    }\n'''
new = '''    protected void onPause() {\n        if (trackReceiverRegistered) {\n            try { unregisterReceiver(trackCommitReceiver); } catch (RuntimeException ignored) {}\n            trackReceiverRegistered = false;\n        }\n        if (locationController != null) locationController.stop();\n        super.onPause();\n    }\n'''
if old not in s: raise SystemExit('onPause pattern missing')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# 5) OfflineMapController: no incremental raw-GPS drawing and no clear-at-4000 behavior.
p = Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapController.java')
s = p.read_text(encoding='utf-8')
old = '''    public void addTrackPoint(double latitude, double longitude) {\n        if (activeTrack == null) startTrackSegment();\n        if (activeTrackPointCount >= MAX_ACTIVE_DRAW_POINTS) {\n            // Bound only the live representation. The authoritative journal/GPX remains complete.\n            clearActiveTrackLayers();\n            startTrackSegment();\n            activeTrackPointCount = 0;\n        }\n        activeTrack.addPoint(new LatLong(latitude, longitude));\n        activeTrackPointCount++;\n        mapView.getLayerManager().redrawLayers();\n    }\n\n'''
if old not in s: raise SystemExit('addTrackPoint pattern missing')
s = s.replace(old, '', 1)
# Clear/begin no longer used to manufacture a visual segment; keep compatibility but empty.
s = s.replace('''    public void beginTrack() {\n        clearActiveTrackLayers();\n        startTrackSegment();\n    }\n''', '''    public void beginTrack() {\n        // Rendering is replaced only from a committed journal snapshot.\n    }\n''', 1)
# Never silently drop oldest segment in an incremental path. Snapshot reducer owns the policy.
s = s.replace('''    public void startTrackSegment() {\n        while (activeTrackSegments.size() >= MAX_ACTIVE_DRAW_SEGMENTS) {\n            Polyline oldest = activeTrackSegments.remove(0);\n            mapView.getLayerManager().getLayers().remove(oldest);\n        }\n        activeTrack = newTrackPolyline(255, 236, 122, 37, 7f);\n        activeTrackSegments.add(activeTrack);\n        mapView.getLayerManager().getLayers().add(activeTrack);\n    }\n''', '''    public void startTrackSegment() {\n        // Retained for binary/source compatibility. The service decides segment boundaries and\n        // showActiveTrack() renders the resulting committed snapshot.\n    }\n''', 1)
p.write_text(s, encoding='utf-8')

# Unit tests for stale render gating and turn-aware preview are added separately by this patch.
Path('app/src/test/java/com/abosultan/darbakmaps/data/TrackRenderGateTest.java').write_text(r'''package com.abosultan.darbakmaps.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackRenderGateTest {
    @Test public void olderAsyncLoadCannotReplaceNewerRequest() {
        TrackRenderGate gate = new TrackRenderGate();
        long old = gate.request(10L);
        long latest = gate.request(12L);
        assertFalse(gate.mayApply(old, 10L));
        assertFalse(gate.mayApply(latest, 11L));
        assertTrue(gate.mayApply(latest, 12L));
        assertEquals(12L, gate.appliedGeneration());
    }
}
''', encoding='utf-8')

Path('app/src/test/java/com/abosultan/darbakmaps/data/TrackJournalPreviewTest.java').write_text(r'''package com.abosultan.darbakmaps.data;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public class TrackJournalPreviewTest {
    @Test public void previewOver4000KeepsTrackVisibleAndPreservesGapAndStrongTurn() throws Exception {
        File dir = Files.createTempDirectory("darbak-preview").toFile();
        File file = new File(dir, "active.csv");
        long t = 1L;
        for (int i = 0; i < 3000; i++) TrackJournal.append(file, 25.0, 45.0 + i * 0.00001, t++, i == 0);
        // Strong bend that blind every-Nth sampling can miss.
        TrackJournal.append(file, 25.02, 45.03001, t++, false);
        for (int i = 0; i < 2200; i++) TrackJournal.append(file, 25.02 + i * 0.00001, 45.03001, t++, false);
        TrackJournal.append(file, 26.0, 46.0, t++, true);
        for (int i = 0; i < 900; i++) TrackJournal.append(file, 26.0, 46.0 + i * 0.00001, t++, false);

        List<GeoPoint> preview = TrackJournal.preview(file, 4000);
        assertFalse(preview.isEmpty());
        assertTrue(preview.size() <= 4020); // small boundary allowance
        boolean gap = false, bend = false;
        for (GeoPoint p : preview) {
            gap |= p.segmentStart && p.latitude > 25.9;
            bend |= Math.abs(p.latitude - 25.02) < 0.000001 && Math.abs(p.longitude - 45.03001) < 0.00005;
        }
        assertTrue("segment boundary must remain", gap);
        assertTrue("strong bend must remain", bend);
        assertTrue(preview.get(preview.size() - 1).longitude > 46.008);
    }
}
''', encoding='utf-8')
