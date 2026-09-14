package com.abosultan.darbakmaps.data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Chooses which disconnected segments to render when a journal has more gaps than the layer cap.
 * Keeps the newest half in full, plus an even sample across older history including the first.
 */
public final class TrackDisplayPolicy {
    private TrackDisplayPolicy() {}

    public static Set<Integer> selectSegments(int totalSegments, int maxSegments) {
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        if (totalSegments <= 0 || maxSegments <= 0) return selected;
        if (totalSegments <= maxSegments) {
            for (int i = 0; i < totalSegments; i++) selected.add(i);
            return selected;
        }
        int recentCount = Math.max(1, maxSegments / 2);
        int recentStart = Math.max(0, totalSegments - recentCount);
        int historicalSlots = Math.max(1, maxSegments - recentCount);
        if (historicalSlots == 1) {
            selected.add(0);
        } else {
            for (int slot = 0; slot < historicalSlots; slot++) {
                int index = (int) Math.round(slot * (recentStart - 1d) / (historicalSlots - 1d));
                if (index >= 0 && index < recentStart) selected.add(index);
            }
        }
        for (int i = recentStart; i < totalSegments; i++) selected.add(i);
        return selected;
    }
}
