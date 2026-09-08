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
 * DarbakMaps 0.3 map-first layout for 1024x600 car screens.
 * The map is the product: controls appear on demand, speed stays visible.
 */
final class CarScreenLayout {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int SURFACE_ALT = Color.rgb(16, 30, 44);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int MUTED = Color.rgb(159, 176, 194);

    private CarScreenLayout() {}

    static View create(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        root.setId(R.id.root);
        root.setBackgroundColor(NIGHT);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        DarbakMapContainer map = new DarbakMapContainer(activity);
        map.setId(R.id.map_container);
        root.addView(map, frame(-1, -1, Gravity.FILL, 0, 0, 0, 0));

        FrameLayout tools = new FrameLayout(activity);
        tools.setId(R.id.tools_overlay);
        tools.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(tools, frame(-1, -1, Gravity.FILL, 0, 0, 0, 0));

        LinearLayout searchBox = new LinearLayout(activity);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        searchBox.setBackground(round(Color.argb(238, 17, 29, 43), dp(activity, 24), Color.argb(100, 57, 169, 255)));
        EditText search = new EditText(activity);
        search.setId(R.id.search_input);
        search.setSingleLine(true);
        search.setHint("ابحث عن مدينة، قرية، وادٍ، محطة أو معلم");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setTextSize(16f);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView searchMark = label(activity, "⌕", PRIMARY, 27f, Gravity.CENTER);
        searchBox.addView(searchMark, new LinearLayout.LayoutParams(dp(activity, 36), -1));
        tools.addView(searchBox, frame(dp(activity, 520), dp(activity, 52), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(activity, 14), 0, 0));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.darbak_brand);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("دربك");
        logo.setOnClickListener(view -> DarbakPanels.showAbout(activity));
        tools.addView(logo, frame(dp(activity, 66), dp(activity, 66), Gravity.TOP | Gravity.RIGHT, dp(activity, 14), dp(activity, 8), 0, 0));

        TextView gps = label(activity, "GPS بانتظار الإشارة", TEXT, 13f, Gravity.CENTER);
        gps.setId(R.id.gps_status);
        gps.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        gps.setBackground(round(Color.argb(232, 17, 29, 43), dp(activity, 18), Color.argb(75, 57, 169, 255)));
        tools.addView(gps, frame(-2, dp(activity, 36), Gravity.TOP | Gravity.RIGHT, dp(activity, 14), dp(activity, 78), 0, 0));

        LinearLayout leftControls = new LinearLayout(activity);
        leftControls.setOrientation(LinearLayout.VERTICAL);
        leftControls.setGravity(Gravity.CENTER);
        TextView plus = control(activity, "+", R.id.zoom_in, 29f);
        TextView minus = control(activity, "−", R.id.zoom_out, 29f);
        TextView locate = control(activity, "◎", R.id.center_location, 24f);
        DarbakOrientationButton orientation = new DarbakOrientationButton(activity);
        leftControls.addView(plus, new LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 52)));
        addSeparated(activity, leftControls, minus, 56, 52);
        addSeparated(activity, leftControls, locate, 56, 54);
        LinearLayout.LayoutParams orientationParams = new LinearLayout.LayoutParams(dp(activity, 76), dp(activity, 50));
        orientationParams.topMargin = dp(activity, 8);
        leftControls.addView(orientation, orientationParams);
        tools.addView(leftControls, frame(dp(activity, 76), -2, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, dp(activity, 14), 0));

        // Legacy mode controls remain hidden only so the old activity bindings stay safe.
        LinearLayout legacyModes = new LinearLayout(activity);
        TextView desert = new TextView(activity);
        desert.setId(R.id.mode_desert);
        TextView city = new TextView(activity);
        city.setId(R.id.mode_city);
        legacyModes.addView(desert, new LinearLayout.LayoutParams(1, 1));
        legacyModes.addView(city, new LinearLayout.LayoutParams(1, 1));
        legacyModes.setVisibility(View.GONE);
        tools.addView(legacyModes, frame(1, 1, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        dock.setBackground(round(Color.argb(242, 7, 17, 29), dp(activity, 22), Color.argb(105, 57, 169, 255)));
        dock.addView(dockAction(activity, "موقعي", R.id.action_map), weighted());
        dock.addView(dockAction(activity, "حفظ", R.id.action_save), weighted());
        dock.addView(dockAction(activity, "تسجيل مسار", R.id.action_record), weighted());
        dock.addView(dockAction(activity, "المحفوظات", R.id.action_saved), weighted());
        dock.addView(dockAction(activity, "المزيد", R.id.action_more), weighted());
        tools.addView(dock, frame(dp(activity, 570), dp(activity, 68), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, dp(activity, 12)));

        // Permanent speed widget. It is intentionally outside the tools overlay.
        LinearLayout speedPill = new LinearLayout(activity);
        speedPill.setOrientation(LinearLayout.HORIZONTAL);
        speedPill.setGravity(Gravity.CENTER);
        speedPill.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        speedPill.setBackground(round(Color.argb(218, 7, 17, 29), dp(activity, 18), Color.argb(65, 57, 169, 255)));
        TextView speed = label(activity, "0", TEXT, 31f, Gravity.CENTER);
        speed.setId(R.id.speed_value);
        speedPill.addView(speed, new LinearLayout.LayoutParams(dp(activity, 62), -1));
        TextView km = label(activity, "كم/س", MUTED, 11f, Gravity.CENTER);
        speedPill.addView(km, new LinearLayout.LayoutParams(dp(activity, 42), -1));
        root.addView(speedPill, frame(dp(activity, 112), dp(activity, 52), Gravity.TOP | Gravity.LEFT, 0, dp(activity, 12), dp(activity, 12), 0));

        LinearLayout empty = new LinearLayout(activity);
        empty.setId(R.id.no_map_panel);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(activity, 28), dp(activity, 22), dp(activity, 28), dp(activity, 22));
        empty.setBackground(round(Color.argb(248, 17, 29, 43), dp(activity, 24), Color.argb(100, 57, 169, 255)));
        empty.addView(label(activity, "الخريطة غير محمّلة", TEXT, 21f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        Button download = button(activity, "تنزيل خريطة الخليج", R.id.download_map, PRIMARY, NIGHT);
        empty.addView(download, new LinearLayout.LayoutParams(dp(activity, 290), dp(activity, 52)));
        Button importMap = button(activity, "إضافة خريطة من USB", R.id.import_map, SURFACE_ALT, TEXT);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(dp(activity, 290), dp(activity, 52));
        importParams.topMargin = dp(activity, 8);
        empty.addView(importMap, importParams);
        empty.setVisibility(View.GONE);
        root.addView(empty, frame(dp(activity, 400), -2, Gravity.CENTER, 0, 0, 0, 0));

        // Keep required map attribution tiny and unobtrusive.
        TextView attribution = label(activity, "© OpenStreetMap", MUTED, 9f, Gravity.CENTER);
        attribution.setPadding(dp(activity, 6), dp(activity, 2), dp(activity, 6), dp(activity, 2));
        attribution.setBackground(round(Color.argb(190, 7, 17, 29), dp(activity, 8), Color.TRANSPARENT));
        root.addView(attribution, frame(-2, -2, Gravity.BOTTOM | Gravity.LEFT, 0, 0, dp(activity, 8), dp(activity, 6)));

        map.post(map::showToolsTemporarily);
        return root;
    }

    private static void addSeparated(Activity activity, LinearLayout parent, View view, int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, width), dp(activity, height));
        params.topMargin = dp(activity, 8);
        parent.addView(view, params);
    }

    private static TextView control(Activity activity, String text, int id, float size) {
        TextView view = label(activity, text, PRIMARY, size, Gravity.CENTER);
        view.setId(id);
        view.setBackground(round(Color.argb(242, 17, 29, 43), dp(activity, 24), Color.argb(75, 57, 169, 255)));
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
        TextView view = id == R.id.action_more ? new DarbakMoreButton(activity) : new TextView(activity);
        view.setText(text);
        view.setTextColor(id == R.id.action_more ? PRIMARY : TEXT);
        view.setTextSize(13f);
        view.setGravity(Gravity.CENTER);
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
