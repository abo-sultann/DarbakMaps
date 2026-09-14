package com.abosultan.darbakmaps.data;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class TrackJournalRetentionTest {
    @Test
    public void trimsSyntheticRoutePast1200KmToNewest1000Km() throws Exception {
        File dir = Files.createTempDirectory("darbak-track-test").toFile();
        File file = new File(dir, "active-track.csv");
        long time = 1_700_000_000_000L;

        // ~1.11 km per point at equator. 1081 connected edges ~= 1202 km.
        double lon = 0d;
        TrackJournal.append(file, 0d, lon, time, true);
        for (int i = 1; i <= 1081; i++) {
            lon += 0.01d;
            TrackJournal.append(file, 0d, lon, time + i * 1000L, false);
        }

        double before = TrackJournal.distanceMeters(file);
        assertTrue("precondition: synthetic trip > 1200 km", before > 1_200_000d);

        double retained = TrackJournal.trimToDistance(file, 1_000_000d);
        assertTrue("retained distance must not exceed one edge beyond cap", retained <= 1_001_500d);
        assertTrue("retained distance should remain close to 1000 km", retained >= 995_000d);
    }

    @Test
    public void segmentGapDoesNotCountAsDistance() throws Exception {
        File dir = Files.createTempDirectory("darbak-gap-test").toFile();
        File file = new File(dir, "active-track.csv");
        TrackJournal.append(file, 25.0, 45.0, 1000L, true);
        TrackJournal.append(file, 25.0, 45.01, 2000L, false);
        TrackJournal.append(file, 30.0, 50.0, 3000L, true);
        TrackJournal.append(file, 30.0, 50.01, 4000L, false);

        double distance = TrackJournal.distanceMeters(file);
        assertTrue("two short connected edges only", distance > 1800d && distance < 2300d);
    }

    @Test
    public void recoversBackupWhenPrimaryMissing() throws Exception {
        File dir = Files.createTempDirectory("darbak-recover-test").toFile();
        File file = new File(dir, "active-track.csv");
        TrackJournal.append(file, 25.0, 45.0, 1000L, true);
        TrackJournal.append(file, 25.0, 45.01, 2000L, false);

        File backup = new File(file.getAbsolutePath() + ".trim-backup");
        assertTrue(file.renameTo(backup));
        TrackJournal.recoverInterruptedTrim(file);

        assertTrue(file.isFile());
        assertTrue(TrackJournal.distanceMeters(file) > 900d);
    }
}
