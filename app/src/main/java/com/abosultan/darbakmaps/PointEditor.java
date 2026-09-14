package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

/** One editor for current-position saves, map long-press saves and future edits. */
final class PointEditor {
    private PointEditor() {}

    static void show(Activity activity, double latitude, double longitude, PlaceRepository.Place existing) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24, 12, 24, 12);
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView coords = new TextView(activity);
        coords.setText(String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude));
        form.addView(coords, new LinearLayout.LayoutParams(-1, 40));

        String[] labels = {"مخيم", "بيت / استراحة", "موقع السمان", "ماء / بئر", "شجرة / روضة", "سيارة", "مدخل / بوابة", "عام"};
        String[] keys = {
                PlaceRepository.ICON_CAMP, PlaceRepository.ICON_HOME, PlaceRepository.ICON_QUAIL,
                PlaceRepository.ICON_WATER, PlaceRepository.ICON_TREE, PlaceRepository.ICON_CAR,
                PlaceRepository.ICON_GATE, PlaceRepository.ICON_STAR
        };

        Spinner type = new Spinner(activity);
        type.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(type, new LinearLayout.LayoutParams(-1, 58));

        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint("اسم الموقع");
        form.addView(name, new LinearLayout.LayoutParams(-1, 58));

        EditText note = new EditText(activity);
        note.setHint("ملاحظة اختيارية — طريق الدخول أو علامة قريبة");
        note.setMaxLines(3);
        form.addView(note, new LinearLayout.LayoutParams(-1, 84));

        if (existing != null) {
            name.setText(existing.name);
            note.setText(existing.note);
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(existing.iconKey)) {
                    type.setSelection(i);
                    break;
                }
            }
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "حفظ موقع" : "تعديل الموقع")
                .setView(scroll)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int selected = Math.max(0, Math.min(type.getSelectedItemPosition(), keys.length - 1));
            String title = name.getText().toString().trim();
            if (title.isEmpty()) title = labels[selected];
            try {
                PlaceRepository repository = new PlaceRepository(activity);
                if (existing == null) {
                    repository.addDetailed(title, latitude, longitude, keys[selected], labels[selected], note.getText().toString());
                } else if (!repository.update(existing.id, title, keys[selected], labels[selected], note.getText().toString())) {
                    throw new IllegalStateException("الموقع لم يعد موجودًا");
                }
                MapRuntimeBridge.refreshSavedPlaces(activity);
                MapRuntimeBridge.showPoint(latitude, longitude);
                Toast.makeText(activity, existing == null ? "تم حفظ الموقع" : "تم تحديث الموقع", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (RuntimeException error) {
                Toast.makeText(activity,
                        error.getMessage() == null ? "تعذر حفظ الموقع" : error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
