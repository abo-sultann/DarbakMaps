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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;
import com.abosultan.darbakmaps.data.TrackStorage;

import java.io.File;
import java.util.List;

/** Darbak-styled saved places/tracks button that replaces the legacy AlertDialog flow. */
final class DarbakSavedButton extends TextView {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int GOLD = Color.rgb(215, 173, 85);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int MUTED = Color.rgb(159, 176, 194);

    DarbakSavedButton(Activity activity) {
        super(activity);
        super.setOnClickListener(view -> showHub(activity));
    }

    @Override
    public void setOnClickListener(View.OnClickListener ignored) {
        // MainActivity still binds the legacy listener; keep the unified Darbak panel instead.
    }

    private static void showHub(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 20));
        root.setBackground(round(NIGHT, dp(activity, 24), Color.argb(120, 215, 173, 85)));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = text(activity, "محفوظات دربك", TEXT, 23f, Gravity.RIGHT);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        TextView subtitle = text(activity, "المواقع والمسارات المحفوظة على الجهاز", MUTED, 12f, Gravity.RIGHT);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(activity, 30)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        PlaceRepository repository = new PlaceRepository(activity);
        List<PlaceRepository.Place> places = repository.all();
        if (!places.isEmpty()) {
            list.addView(section(activity, "المواقع المحفوظة"));
            for (PlaceRepository.Place place : places) {
                TextView item = item(activity, place.name, String.format(java.util.Locale.US, "%.5f, %.5f", place.latitude, place.longitude));
                item.setOnClickListener(view -> {
                    if (!MapRuntimeBridge.hasActiveMap()) {
                        Toast.makeText(activity, "أضف خريطة أوفلاين أولًا", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MapRuntimeBridge.showPoint(place.latitude, place.longitude);
                    dialog.dismiss();
                });
                list.addView(item, itemParams(activity));
            }
        }

        File[] tracks = TrackStorage.list(activity);
        if (tracks.length > 0) {
            list.addView(section(activity, "المسارات السابقة"));
            for (File track : tracks) {
                String label = track.getName().replace(".gpx", "").replace('_', ' ');
                TextView item = item(activity, label, "اضغط لعرض المسار على الخريطة");
                item.setOnClickListener(view -> new Thread(() -> {
                    try {
                        java.util.List<com.abosultan.darbakmaps.data.GeoPoint> points = TrackStorage.load(track);
                        activity.runOnUiThread(() -> {
                            if (MapRuntimeBridge.showStoredTrack(points)) {
                                dialog.dismiss();
                            } else {
                                Toast.makeText(activity, "أضف خريطة أوفلاين أولًا", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception error) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, "تعذر فتح المسار", Toast.LENGTH_SHORT).show());
                    }
                }, "darbak-track-open").start());
                list.addView(item, itemParams(activity));
            }
        }

        if (places.isEmpty() && tracks.length == 0) {
            TextView empty = text(activity, "لا توجد مواقع أو مسارات محفوظة", MUTED, 17f, Gravity.CENTER);
            empty.setBackground(round(SURFACE, dp(activity, 20), Color.argb(70, 57, 169, 255)));
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(activity, 100)));
        }

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView close = text(activity, "إغلاق", PRIMARY, 16f, Gravity.CENTER);
        close.setBackground(round(SURFACE, dp(activity, 18), Color.argb(80, 57, 169, 255)));
        close.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(activity, 180), dp(activity, 48));
        closeParams.gravity = Gravity.CENTER;
        closeParams.topMargin = dp(activity, 10);
        root.addView(close, closeParams);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setLayout(dp(activity, 760), dp(activity, 500));
            window.getDecorView().setSystemUiVisibility(immersiveFlags());
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(dp(activity, 760), dp(activity, 500));
            window.getDecorView().setSystemUiVisibility(immersiveFlags());
        }
    }

    private static TextView section(Activity activity, String value) {
        TextView view = text(activity, value, GOLD, 14f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        return view;
    }

    private static TextView item(Activity activity, String title, String detail) {
        TextView view = text(activity, title + "\n" + detail, TEXT, 15f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setLineSpacing(2f, 1f);
        view.setPadding(dp(activity, 16), dp(activity, 7), dp(activity, 16), dp(activity, 7));
        view.setBackground(round(SURFACE, dp(activity, 18), Color.argb(65, 57, 169, 255)));
        return view;
    }

    private static LinearLayout.LayoutParams itemParams(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 66));
        params.setMargins(0, dp(activity, 4), 0, dp(activity, 4));
        return params;
    }

    private static TextView text(Activity activity, String value, int color, float size, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(gravity);
        return view;
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
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
