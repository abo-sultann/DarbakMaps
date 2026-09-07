package com.abosultan.darbakmaps;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Builds the first screen without XML inflation. Several old Allwinner Android 7 ROMs contain
 * modified resource inflaters, so the car-screen path deliberately uses only platform views.
 */
final class CarScreenLayout {
    private static final int GREEN = Color.rgb(8, 62, 45);
    private static final int DEEP_GREEN = Color.rgb(3, 39, 30);
    private static final int GOLD = Color.rgb(217, 174, 85);
    private static final int LIGHT_GOLD = Color.rgb(241, 215, 147);
    private static final int IVORY = Color.rgb(255, 249, 235);
    private static final int MUTED = Color.rgb(92, 110, 103);

    private CarScreenLayout() {
    }

    static View create(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        root.setId(R.id.root);
        root.setBackgroundColor(IVORY);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        FrameLayout map = new FrameLayout(activity);
        map.setId(R.id.map_container);
        root.addView(map, frame(-1, -1, Gravity.FILL, 0, 0, 0, 0));

        LinearLayout searchBox = new LinearLayout(activity);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        searchBox.setBackground(round(Color.argb(244, 255, 255, 255), dp(activity, 24), Color.argb(45, 8, 62, 45)));
        EditText search = new EditText(activity);
        search.setId(R.id.search_input);
        search.setSingleLine(true);
        search.setHint("ابحث عن مدينة، وادٍ أو محطة وقود");
        search.setTextColor(DEEP_GREEN);
        search.setHintTextColor(MUTED);
        search.setTextSize(17f);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView searchMark = label(activity, "⌕", GREEN, 28f, Gravity.CENTER);
        searchBox.addView(searchMark, new LinearLayout.LayoutParams(dp(activity, 36), -1));
        root.addView(searchBox, frame(dp(activity, 470), dp(activity, 54), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(activity, 16), 0, 0));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.darbak_brand);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, frame(dp(activity, 68), dp(activity, 68), Gravity.TOP | Gravity.RIGHT, dp(activity, 14), dp(activity, 10), 0, 0));

        LinearLayout modes = new LinearLayout(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        modes.setBackground(round(Color.argb(244, 255, 255, 255), dp(activity, 22), Color.argb(45, 8, 62, 45)));
        TextView desert = label(activity, "البر", DEEP_GREEN, 16f, Gravity.CENTER);
        desert.setId(R.id.mode_desert);
        desert.setBackground(round(GOLD, dp(activity, 18), Color.TRANSPARENT));
        TextView city = label(activity, "المدينة", MUTED, 16f, Gravity.CENTER);
        city.setId(R.id.mode_city);
        modes.addView(desert, new LinearLayout.LayoutParams(dp(activity, 80), dp(activity, 40)));
        modes.addView(city, new LinearLayout.LayoutParams(dp(activity, 80), dp(activity, 40)));
        root.addView(modes, frame(-2, dp(activity, 48), Gravity.TOP | Gravity.LEFT, 0, dp(activity, 18), dp(activity, 18), 0));

        LinearLayout zoom = new LinearLayout(activity);
        zoom.setOrientation(LinearLayout.VERTICAL);
        zoom.setGravity(Gravity.CENTER);
        TextView plus = control(activity, "+", R.id.zoom_in, 30f);
        TextView minus = control(activity, "−", R.id.zoom_out, 30f);
        TextView locate = control(activity, "◎", R.id.center_location, 25f);
        zoom.addView(plus, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 54)));
        LinearLayout.LayoutParams separated = new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 54));
        separated.topMargin = dp(activity, 8);
        zoom.addView(minus, separated);
        LinearLayout.LayoutParams separatedAgain = new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58));
        separatedAgain.topMargin = dp(activity, 8);
        zoom.addView(locate, separatedAgain);
        root.addView(zoom, frame(dp(activity, 58), -2, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, dp(activity, 18), 0));

        LinearLayout status = new LinearLayout(activity);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.RIGHT);
        TextView gps = label(activity, "GPS بانتظار الإشارة", Color.WHITE, 14f, Gravity.CENTER);
        gps.setId(R.id.gps_status);
        gps.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        gps.setBackground(round(Color.argb(242, 3, 39, 30), dp(activity, 18), Color.TRANSPARENT));
        status.addView(gps, new LinearLayout.LayoutParams(-2, dp(activity, 38)));
        LinearLayout speedCard = new LinearLayout(activity);
        speedCard.setOrientation(LinearLayout.VERTICAL);
        speedCard.setGravity(Gravity.CENTER);
        speedCard.setBackground(round(Color.argb(242, 3, 39, 30), dp(activity, 22), Color.TRANSPARENT));
        TextView speed = label(activity, "0", LIGHT_GOLD, 38f, Gravity.CENTER);
        speed.setId(R.id.speed_value);
        speedCard.addView(speed, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView km = label(activity, "كم/س", Color.WHITE, 13f, Gravity.CENTER);
        speedCard.addView(km, new LinearLayout.LayoutParams(-1, dp(activity, 30)));
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(dp(activity, 96), dp(activity, 96));
        speedParams.topMargin = dp(activity, 10);
        status.addView(speedCard, speedParams);
        root.addView(status, frame(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL, dp(activity, 18), 0, 0, 0));

        LinearLayout empty = new LinearLayout(activity);
        empty.setId(R.id.no_map_panel);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(activity, 28), dp(activity, 22), dp(activity, 28), dp(activity, 22));
        empty.setBackground(round(Color.argb(247, 255, 249, 235), dp(activity, 24), Color.argb(70, 8, 62, 45)));
        empty.addView(label(activity, "لا توجد خريطة", DEEP_GREEN, 21f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        Button download = button(activity, "تنزيل خريطة الخليج الموصى بها", R.id.download_map, GOLD, DEEP_GREEN);
        empty.addView(download, new LinearLayout.LayoutParams(dp(activity, 290), dp(activity, 52)));
        Button importMap = button(activity, "إضافة خريطة من USB أو الذاكرة", R.id.import_map, DEEP_GREEN, Color.WHITE);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(dp(activity, 290), dp(activity, 52));
        importParams.topMargin = dp(activity, 8);
        empty.addView(importMap, importParams);
        empty.setVisibility(View.GONE);
        root.addView(empty, frame(dp(activity, 400), -2, Gravity.CENTER, 0, 0, 0, 0));

        TextView attribution = label(activity, "© OpenStreetMap contributors", MUTED, 10f, Gravity.CENTER);
        attribution.setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3));
        attribution.setBackground(round(Color.argb(235, 255, 255, 255), dp(activity, 10), Color.TRANSPARENT));
        root.addView(attribution, frame(-2, -2, Gravity.BOTTOM | Gravity.LEFT, 0, 0, dp(activity, 16), dp(activity, 16)));

        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        dock.setBackground(round(Color.argb(245, 8, 62, 45), dp(activity, 22), Color.argb(80, 241, 215, 147)));
        dock.addView(dockAction(activity, "الخريطة", R.id.action_map), weighted());
        dock.addView(dockAction(activity, "حفظ موقع", R.id.action_save), weighted());
        dock.addView(dockAction(activity, "تسجيل مسار", R.id.action_record), weighted());
        dock.addView(dockAction(activity, "المحفوظات", R.id.action_saved), weighted());
        dock.addView(dockAction(activity, "المزيد", R.id.action_more), weighted());
        root.addView(dock, frame(dp(activity, 600), dp(activity, 74), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, dp(activity, 14)));
        return root;
    }

    private static TextView control(Activity activity, String text, int id, float size) {
        TextView view = label(activity, text, GREEN, size, Gravity.CENTER);
        view.setId(id);
        view.setBackground(round(Color.argb(247, 255, 255, 255), dp(activity, 25), Color.argb(55, 8, 62, 45)));
        return view;
    }

    private static Button button(Activity activity, String text, int id, int color, int textColor) {
        Button button = new Button(activity);
        button.setId(id);
        button.setText(text);
        button.setTextSize(15f);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(color, dp(activity, 18), Color.TRANSPARENT));
        return button;
    }

    private static TextView dockAction(Activity activity, String text, int id) {
        TextView view = label(activity, text, Color.WHITE, 13f, Gravity.CENTER);
        view.setId(id);
        view.setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 6));
        return view;
    }

    private static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private static TextView label(Activity activity, String text, int color, float size, int gravity) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(gravity);
        return view;
    }

    private static GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(radius);
        if (Color.alpha(stroke) > 0) {
            background.setStroke(1, stroke);
        }
        return background;
    }

    private static FrameLayout.LayoutParams frame(int width, int height, int gravity,
                                                   int right, int top, int left, int bottom) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
