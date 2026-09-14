package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

import java.util.Locale;

/** Darbak-native place editor. No stock Android spinner/dialog UI on the car screen. */
final class PointEditor {
    private static final int DEEP = Color.rgb(8, 39, 31);
    private static final int SURFACE = Color.rgb(16, 52, 42);
    private static final int SURFACE_ALT = Color.rgb(25, 72, 58);
    private static final int GOLD = Color.rgb(216, 180, 91);
    private static final int GOLD_LIGHT = Color.rgb(241, 216, 142);
    private static final int TEXT = Color.rgb(247, 242, 231);
    private static final int MUTED = Color.rgb(185, 179, 165);

    private PointEditor() {}

    static void show(Activity activity, double latitude, double longitude, PlaceRepository.Place existing) {
        final String[] labels = {"سمان", "مخيم", "ماء / بئر", "شجرة / روضة", "صيد", "علامة"};
        final String[] keys = {
                PlaceRepository.ICON_QUAIL,
                PlaceRepository.ICON_CAMP,
                PlaceRepository.ICON_WATER,
                PlaceRepository.ICON_TREE,
                PlaceRepository.ICON_HUNTING,
                PlaceRepository.ICON_STAR
        };
        final int[] selected = {0};

        if (existing != null) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(existing.iconKey)) {
                    selected[0] = i;
                    break;
                }
            }
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 20));
        root.setBackground(round(DEEP, dp(activity, 26), Color.argb(180, 216, 180, 91)));

        TextView title = text(activity, existing == null ? "حفظ موقع" : "تعديل الموقع", TEXT, 24f, Gravity.RIGHT);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(activity, 38)));

        TextView subtitle = text(activity,
                existing == null ? "اختر نوع الموقع فقط؛ الاسم ليس إلزاميًا" : "عدّل النوع أو البيانات ثم احفظ",
                MUTED, 13f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(activity, 30)));

        TextView coords = text(activity,
                String.format(Locale.US, "%.6f  •  %.6f", latitude, longitude),
                GOLD_LIGHT, 12f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        coords.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        coords.setBackground(round(SURFACE, dp(activity, 12), Color.TRANSPARENT));
        LinearLayout.LayoutParams coordsParams = new LinearLayout.LayoutParams(-1, dp(activity, 34));
        coordsParams.bottomMargin = dp(activity, 12);
        root.addView(coords, coordsParams);

        TextView typeLabel = text(activity, "نوع الموقع", GOLD, 13f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(typeLabel, new LinearLayout.LayoutParams(-1, dp(activity, 26)));

        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final TextView[] chipViews = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(activity, labels[i], i == selected[0] ? DEEP : TEXT, 14f, Gravity.CENTER);
            chip.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            chip.setBackground(round(i == selected[0] ? GOLD : SURFACE_ALT,
                    dp(activity, 16), i == selected[0] ? GOLD : Color.argb(90, 216, 180, 91)));
            chip.setOnClickListener(v -> {
                selected[0] = index;
                for (int j = 0; j < chipViews.length; j++) {
                    TextView c = chipViews[j];
                    if (c == null) continue;
                    boolean active = j == selected[0];
                    c.setTextColor(active ? DEEP : TEXT);
                    c.setBackground(round(active ? GOLD : SURFACE_ALT,
                            dp(activity, 16), active ? GOLD : Color.argb(90, 216, 180, 91)));
                }
            });
            chipViews[i] = chip;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(activity, 112), dp(activity, 46));
            cp.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
            chips.addView(chip, cp);
        }
        scroller.addView(chips);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(activity, 54));
        scrollParams.bottomMargin = dp(activity, 10);
        root.addView(scroller, scrollParams);

        EditText name = input(activity, "اسم اختياري");
        if (existing != null) {
            String autoPrefix = PlaceRepository.iconLabel(existing.iconKey) + " — ";
            if (existing.name != null && !existing.name.startsWith(autoPrefix)) name.setText(existing.name);
        }
        root.addView(name, new LinearLayout.LayoutParams(-1, dp(activity, 50)));

        EditText note = input(activity, "ملاحظة اختيارية");
        note.setSingleLine(false);
        note.setMaxLines(2);
        if (existing != null && existing.note != null) note.setText(existing.note);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, dp(activity, 64));
        noteParams.topMargin = dp(activity, 8);
        root.addView(note, noteParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(activity, 14), 0, 0);

        TextView save = text(activity, existing == null ? "حفظ الموقع" : "حفظ التعديل", DEEP, 16f, Gravity.CENTER);
        save.setBackground(round(GOLD, dp(activity, 17), Color.TRANSPARENT));
        TextView cancel = text(activity, "إلغاء", TEXT, 15f, Gravity.CENTER);
        cancel.setBackground(round(SURFACE_ALT, dp(activity, 17), Color.argb(70, 216, 180, 91)));
        actions.addView(save, weighted(activity));
        actions.addView(cancel, weighted(activity));
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(activity, 62)));

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String typedName = name.getText().toString().trim();
            String iconKey = keys[Math.max(0, Math.min(selected[0], keys.length - 1))];
            try {
                PlaceRepository repository = new PlaceRepository(activity);
                if (existing == null) {
                    repository.addDetailed(typedName, latitude, longitude, iconKey,
                            PlaceRepository.iconLabel(iconKey), note.getText().toString().trim());
                } else if (!repository.update(existing.id, typedName, iconKey,
                        PlaceRepository.iconLabel(iconKey), note.getText().toString().trim())) {
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

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.setOnShowListener(ignored -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shown.setLayout(dp(activity, 760), WindowManager.LayoutParams.WRAP_CONTENT);
                shown.getDecorView().setSystemUiVisibility(immersiveFlags());
            }
        });
        dialog.show();
        root.requestFocus();
    }

    private static EditText input(Activity activity, String hint) {
        EditText view = new EditText(activity);
        view.setHint(hint);
        view.setHintTextColor(MUTED);
        view.setTextColor(TEXT);
        view.setTextSize(15f);
        view.setSingleLine(true);
        view.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        view.setBackground(round(SURFACE, dp(activity, 14), Color.argb(70, 216, 180, 91)));
        return view;
    }

    private static TextView text(Activity activity, String value, int color, float size, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(gravity);
        return view;
    }

    private static LinearLayout.LayoutParams weighted(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 48), 1f);
        params.setMargins(dp(activity, 5), 0, dp(activity, 5), 0);
        return params;
    }

    private static GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(radius);
        if (Color.alpha(stroke) > 0) background.setStroke(1, stroke);
        return background;
    }

    private static int immersiveFlags() {
        return View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
