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
 * Lightweight 1024x600 car-screen layout using platform Views only.
 * Darbak UI V1: night surfaces, calm electric blue, large touch targets, no heavy effects.
 */
final class CarScreenLayout {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int SURFACE_ALT = Color.rgb(16, 30, 44);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int GOLD = Color.rgb(215, 173, 85);
    private static final int TEXT = Color.rgb(244, 247, 250);
    private static final int MUTED = Color.rgb(159, 176, 194);

    private CarScreenLayout() {
    }

    static View create(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        root.setId(R.id.root);
        root.setBackgroundColor(NIGHT);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        FrameLayout map = new FrameLayout(activity);
        map.setId(R.id.map_container);
        root.addView(map, frame(-1, -1, Gravity.FILL, 0, 0, 0, 0));

        LinearLayout searchBox = new LinearLayout(activity);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        searchBox.setBackground(round(Color.argb(244, 17, 29, 43), dp(activity, 24), Color.argb(105, 57, 169, 255)));
        EditText search = new EditText(activity);
        search.setId(R.id.search_input);
        search.setSingleLine(true);
        search.setHint("ابحث عن مدينة، وادٍ أو محطة وقود");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setTextSize(17f);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView searchMark = label(activity, "⌕", PRIMARY, 28f, Gravity.CENTER);
        searchBox.addView(searchMark, new LinearLayout.LayoutParams(dp(activity, 36), -1));
        root.addView(searchBox, frame(dp(activity, 470), dp(activity, 54), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(activity, 16), 0, 0));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.darbak_brand);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("دربك");
        logo.setOnClickListener(view -> DarbakPanels.showAbout(activity));
        root.addView(logo, frame(dp(activity, 76), dp(activity, 76), Gravity.TOP | Gravity.RIGHT, dp(activity, 14), dp(activity, 8), 0, 0));

        LinearLayout modes = new LinearLayout(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        modes.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        modes.setBackground(round(Color.argb(244, 17, 29, 43), dp(activity, 22), Color.argb(75, 57, 169, 255)));
        TextView desert = label(activity, "البر", NIGHT, 16f, Gravity.CENTER);
        desert.setId(R.id.mode_desert);
        desert.setBackground(round(PRIMARY, dp(activity, 18), Color.TRANSPARENT));
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
        TextView gps = label(activity, "GPS بانتظار الإشارة", TEXT, 14f, Gravity.CENTER);
        gps.setId(R.id.gps_status);
        gps.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        gps.setBackground(round(Color.argb(244, 17, 29, 43), dp(activity, 18), Color.argb(70, 57, 169, 255)));
        status.addView(gps, new LinearLayout.LayoutParams(-2, dp(activity, 38)));
        LinearLayout speedCard = new LinearLayout(activity);
        speedCard.setOrientation(LinearLayout.VERTICAL);
        speedCard.setGravity(Gravity.CENTER);
        speedCard.setBackground(round(Color.argb(244, 17, 29, 43), dp(activity, 22), Color.argb(80, 57, 169, 255)));
        TextView speed = label(activity, "0", PRIMARY, 38f, Gravity.CENTER);
        speed.setId(R.id.speed_value);
        speedCard.addView(speed, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView km = label(activity, "كم/س", MUTED, 13f, Gravity.CENTER);
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

        TextView attribution = label(activity, "© OpenStreetMap contributors", MUTED, 10f, Gravity.CENTER);
        attribution.setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3));
        attribution.setBackground(round(Color.argb(225, 7, 17, 29), dp(activity, 10), Color.TRANSPARENT));
        root.addView(attribution, frame(-2, -2, Gravity.BOTTOM | Gravity.LEFT, 0, 0, dp(activity, 16), dp(activity, 16)));

        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        dock.setBackground(round(Color.argb(247, 7, 17, 29), dp(activity, 22), Color.argb(115, 57, 169, 255)));
        dock.addView(dockAction(activity, "الخريطة", R.id.action_map), weighted());
        dock.addView(dockAction(activity, "حفظ موقع", R.id.action_save), weighted());
        dock.addView(dockAction(activity, "تسجيل مسار", R.id.action_record), weighted());
        dock.addView(dockAction(activity, "المحفوظات", R.id.action_saved), weighted());
        dock.addView(dockAction(activity, "المزيد", R.id.action_more), weighted());
        root.addView(dock, frame(dp(activity, 600), dp(activity, 74), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, dp(activity, 14)));
        return root;
    }

    private static TextView control(Activity activity, String text, int id, float size) {
        TextView view = label(activity, text, PRIMARY, size, Gravity.CENTER);
        view.setId(id);
        view.setBackground(round(Color.argb(247, 17, 29, 43), dp(activity, 25), Color.argb(75, 57, 169, 255)));
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
