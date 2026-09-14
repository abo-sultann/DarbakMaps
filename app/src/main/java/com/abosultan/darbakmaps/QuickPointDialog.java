package com.abosultan.darbakmaps;

import android.app.Activity;
import android.location.Location;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

/** Entry point for saving the current GPS position through the unified editor. */
public final class QuickPointDialog {
    private QuickPointDialog() {}

    public static void show(Activity activity, PlaceRepository repository, Location location) {
        if (location == null) {
            Toast.makeText(activity, "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        PointEditor.show(activity, location.getLatitude(), location.getLongitude(), null);
    }
}
