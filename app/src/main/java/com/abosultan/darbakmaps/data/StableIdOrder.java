package com.abosultan.darbakmaps.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure helper used by tests/documentation for stable visible row identity. */
public final class StableIdOrder {
    private StableIdOrder() {}
    public static List<String> preserve(List<String> visible, List<String> fresh) {
        Set<String> remaining = new LinkedHashSet<>(fresh);
        List<String> result = new ArrayList<>();
        for (String id : visible) if (remaining.remove(id)) result.add(id);
        result.addAll(remaining);
        return result;
    }
}
