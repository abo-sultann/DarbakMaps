package com.abosultan.darbakmaps.map;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/** Runs only when CI supplies the approved 181MB Saudi Mapsforge file. */
public class SaudiMapSearchIntegrationTest {
    @Test public void probesActualSaudiMapCategoriesAndNearbyPerformance() throws Exception {
        String path = System.getProperty("darbak.map.path", "");
        Assume.assumeTrue("real map not supplied", !path.isEmpty());
        File map = new File(path);
        assertTrue(map.isFile() && map.length() > 180_000_000L);

        String[] categories = {
                OfflineMapSearchEngine.CATEGORY_SERVICES,
                OfflineMapSearchEngine.CATEGORY_VILLAGES,
                OfflineMapSearchEngine.CATEGORY_WADIS,
                OfflineMapSearchEngine.CATEGORY_MOUNTAINS,
                OfflineMapSearchEngine.CATEGORY_WATER,
                OfflineMapSearchEngine.CATEGORY_LANDMARKS
        };
        double[][] centers = {
                {24.7136, 46.6753}, // Riyadh / Wadi Hanifa region
                {26.3592, 43.9818}, // Al-Qassim
                {27.5114, 41.7208}  // Hail / mountain-desert region
        };
        Map<String,Integer> counts = new LinkedHashMap<>();
        long slowestMs = 0L;
        long maxHeapDelta = 0L;
        StringBuilder report = new StringBuilder();
        report.append("DarbakMaps Saudi map search acceptance\n");
        report.append("mapBytes=").append(map.length()).append('\n');

        for (String category : categories) {
            int total = 0;
            for (double[] center : centers) {
                OfflineMapSearchEngine engine = new OfflineMapSearchEngine();
                Runtime runtime = Runtime.getRuntime();
                long before = runtime.totalMemory() - runtime.freeMemory();
                long started = System.nanoTime();
                OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                        "", category, center[0], center[1], 100_000f, true, 40);
                List<OfflineMapSearchEngine.Result> found = engine.search(request, map, Collections.emptyList());
                long elapsed = (System.nanoTime() - started) / 1_000_000L;
                long after = runtime.totalMemory() - runtime.freeMemory();
                long delta = Math.max(0L, after - before);
                slowestMs = Math.max(slowestMs, elapsed);
                maxHeapDelta = Math.max(maxHeapDelta, delta);
                total += found.size();
                report.append(String.format(Locale.US,
                        "%s @ %.4f,%.4f => %d results, %d ms, heapDelta=%d\n",
                        category, center[0], center[1], found.size(), elapsed, delta));
                for (int i = 0; i < Math.min(3, found.size()); i++) {
                    OfflineMapSearchEngine.Result r = found.get(i);
                    report.append("  sample: ").append(r.name).append(" | ").append(r.source)
                            .append(" | ").append(r.distanceMeters).append("m\n");
                }
            }
            counts.put(category, total);
        }
        report.append("counts=").append(counts).append('\n');
        report.append("slowestMs=").append(slowestMs).append('\n');
        report.append("maxHeapDelta=").append(maxHeapDelta).append('\n');
        String reportPath = System.getProperty("darbak.search.report", "");
        if (!reportPath.isEmpty()) try (FileWriter out = new FileWriter(reportPath)) { out.write(report.toString()); }
        System.out.println(report);

        // These are robust baseline categories expected in the approved Saudi map. Other zero-count
        // categories are reported as data coverage findings, not hidden as UI failures.
        assertTrue("services must exist in actual Saudi data", counts.get(OfflineMapSearchEngine.CATEGORY_SERVICES) > 0);
        assertTrue("settlements must exist in actual Saudi data", counts.get(OfflineMapSearchEngine.CATEGORY_VILLAGES) > 0);
        assertTrue("actual-map nearby query unexpectedly slow in CI", slowestMs < 20_000L);
        assertTrue("actual-map query heap delta exceeded CI safety envelope", maxHeapDelta < 256L * 1024L * 1024L);
    }
}
