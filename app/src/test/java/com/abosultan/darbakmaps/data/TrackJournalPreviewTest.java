package com.abosultan.darbakmaps.data;

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
