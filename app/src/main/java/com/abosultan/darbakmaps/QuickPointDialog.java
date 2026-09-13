package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.location.Location;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

/** One-tap waypoint creation for driving/off-road use. */
public final class QuickPointDialog {
    private QuickPointDialog() {}

    public static void show(Activity activity, PlaceRepository repository, Location location) {
        if (location == null) {
            Toast.makeText(activity, "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = {"مخيم", "بيت", "موقع سمان", "ماء / بئر", "شجرة مميزة", "سيارة", "مدخل", "خطر", "تجمع", "نقطة رجوع"};
        final String[] icons = {
                PlaceRepository.ICON_CAMP, PlaceRepository.ICON_HOME, PlaceRepository.ICON_QUAIL,
                PlaceRepository.ICON_WATER, PlaceRepository.ICON_TREE, PlaceRepository.ICON_CAR,
                PlaceRepository.ICON_GATE, PlaceRepository.ICON_STAR, PlaceRepository.ICON_STAR,
                PlaceRepository.ICON_STAR
        };
        new AlertDialog.Builder(activity)
                .setTitle("حفظ سريع للموقع الحالي")
                .setItems(names, (dialog, which) -> {
                    repository.addDetailed(names[which], location.getLatitude(), location.getLongitude(),
                            icons[which], names[which], "حفظ سريع أثناء القيادة");
                    MapRuntimeBridge.refreshSavedPlaces(activity);
                    Toast.makeText(activity, "تم حفظ " + names[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }
}
