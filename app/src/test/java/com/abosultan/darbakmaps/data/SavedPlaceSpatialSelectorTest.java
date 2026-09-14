package com.abosultan.darbakmaps.data;

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
