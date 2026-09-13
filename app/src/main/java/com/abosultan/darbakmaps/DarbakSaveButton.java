package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

/** Darbak-styled save-location button that replaces the legacy AlertDialog flow. */
final class DarbakSaveButton extends TextView {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int GOLD = Color.rgb(215, 173, 85);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int MUTED = Color.rgb(159, 176, 194);

    DarbakSaveButton(Activity activity) {
        super(activity);
        super.setOnClickListener(view -> saveCurrent(activity));
    }

    @Override
    public void setOnClickListener(View.OnClickListener ignored) {
        // MainActivity still binds the legacy listener; keep the unified Darbak flow instead.
    }

    private static void saveCurrent(Activity activity) {
        Location location = lastLocation(activity);
        if (location == null) {
            Toast.makeText(activity, "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 26), dp(activity, 22), dp(activity, 26), dp(activity, 22));
        root.setBackground(round(NIGHT, dp(activity, 24), Color.argb(120, 215, 173, 85)));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        root.addView(text(activity, "حفظ الموقع", TEXT, 23f, Gravity.RIGHT), new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        root.addView(text(activity,
                String.format(java.util.Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()),
                MUTED, 13f, Gravity.RIGHT), new LinearLayout.LayoutParams(-1, dp(activity, 32)));

        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint("اسم الموقع — مثال: المخيم أو مدخل الشِعْب");
        name.setTextColor(TEXT);
        name.setHintTextColor(MUTED);
        name.setTextSize(16f);
        name.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        name.setBackground(round(SURFACE, dp(activity, 18), Color.argb(75, 57, 169, 255)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(activity, 56));
        inputParams.topMargin = dp(activity, 10);
        root.addView(name, inputParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        TextView save = text(activity, "حفظ", NIGHT, 16f, Gravity.CENTER);
        save.setBackground(round(PRIMARY, dp(activity, 18), Color.TRANSPARENT));
        TextView cancel = text(activity, "إلغاء", TEXT, 16f, Gravity.CENTER);
        cancel.setBackground(round(SURFACE, dp(activity, 18), Color.argb(70, 57, 169, 255)));
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(0, dp(activity, 50), 1f);
        action.setMargins(dp(activity, 5), 0, dp(activity, 5), 0);
        actions.addView(save, action);
        actions.addView(cancel, action);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        actionsParams.topMargin = dp(activity, 18);
        root.addView(actions, actionsParams);

        save.setOnClickListener(view -> {
            String label = name.getText().toString().trim();
            if (label.isEmpty()) label = "موقع محفوظ";
            new PlaceRepository(activity).add(label, location.getLatitude(), location.getLongitude());
            MapRuntimeBridge.showPoint(location.getLatitude(), location.getLongitude());
            Toast.makeText(activity, "تم حفظ الموقع", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        cancel.setOnClickListener(view -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setLayout(dp(activity, 620), WindowManager.LayoutParams.WRAP_CONTENT);
            window.getDecorView().setSystemUiVisibility(immersiveFlags());
        }
    }

    private static Location lastLocation(Activity activity) {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager manager = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
            if (manager == null) return null;
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps == null) return network;
            if (network == null) return gps;
            return gps.getTime() >= network.getTime() ? gps : network;
        } catch (RuntimeException ignored) {
            return null;
        }
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
