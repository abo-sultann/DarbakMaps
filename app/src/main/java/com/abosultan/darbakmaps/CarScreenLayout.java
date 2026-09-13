package com.abosultan.darbakmaps;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * DarbakMaps hybrid map-first layout for 1024x600 car screens.
 * The simple map-first shell remains the default; a richer side panel opens only on demand.
 */
final class CarScreenLayout {
    private static final int NIGHT = Color.rgb(7, 17, 29);
    private static final int SURFACE = Color.rgb(17, 29, 43);
    private static final int SURFACE_ALT = Color.rgb(16, 30, 44);
    private static final int PRIMARY = Color.rgb(57, 169, 255);
    private static final int GOLD = Color.rgb(215, 173, 85);
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


        LinearLayout navPanel = new LinearLayout(activity);
        navPanel.setId(R.id.nav_panel);
        navPanel.setOrientation(LinearLayout.HORIZONTAL);
        navPanel.setGravity(Gravity.CENTER_VERTICAL);
        navPanel.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6));
        navPanel.setBackground(round(Color.argb(246, 7, 17, 29), dp(activity, 20), Color.argb(160, 215, 173, 85)));
        TextView navArrow = label(activity, "➤", GOLD, 34f, Gravity.CENTER);
        navArrow.setId(R.id.nav_arrow);
        navPanel.addView(navArrow, new LinearLayout.LayoutParams(dp(activity, 58), -1));
        LinearLayout navText = new LinearLayout(activity);
        navText.setOrientation(LinearLayout.VERTICAL);
        navText.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        TextView navName = label(activity, "", TEXT, 16f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        navName.setId(R.id.nav_name);
        TextView navDistance = label(activity, "", GOLD, 20f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        navDistance.setId(R.id.nav_distance);
        TextView navDetail = label(activity, "", MUTED, 11f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        navDetail.setId(R.id.nav_detail);
        navText.addView(navName, new LinearLayout.LayoutParams(-1, dp(activity, 24)));
        navText.addView(navDistance, new LinearLayout.LayoutParams(-1, dp(activity, 29)));
        navText.addView(navDetail, new LinearLayout.LayoutParams(-1, dp(activity, 20)));
        navPanel.addView(navText, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView navStop = label(activity, "×", TEXT, 25f, Gravity.CENTER);
        navStop.setId(R.id.nav_stop);
        navStop.setBackground(round(SURFACE_ALT, dp(activity, 14), Color.argb(85, 215, 173, 85)));
        navPanel.addView(navStop, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));
        navPanel.setVisibility(View.GONE);
        tools.addView(navPanel, frame(dp(activity, 470), dp(activity, 88), Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                0, dp(activity, 74), 0, 0));

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

        LinearLayout sidePanel = new LinearLayout(activity);
        sidePanel.setOrientation(LinearLayout.VERTICAL);
        sidePanel.setGravity(Gravity.TOP);
        sidePanel.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        sidePanel.setBackground(round(Color.argb(248, 7, 17, 29), dp(activity, 22), Color.argb(150, 215, 173, 85)));
        sidePanel.setVisibility(View.GONE);

        LinearLayout panelHeader = new LinearLayout(activity);
        panelHeader.setOrientation(LinearLayout.HORIZONTAL);
        panelHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView panelTitle = label(activity, "لوحة دربك", GOLD, 19f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        panelHeader.addView(panelTitle, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));
        TextView panelClose = label(activity, "×", TEXT, 27f, Gravity.CENTER);
        panelClose.setBackground(round(SURFACE_ALT, dp(activity, 16), Color.argb(80, 215, 173, 85)));
        panelHeader.addView(panelClose, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 40)));
        sidePanel.addView(panelHeader, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        TextView panelSubtitle = label(activity, "OSM + المشاري • أوفلاين", MUTED, 12f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        sidePanel.addView(panelSubtitle, new LinearLayout.LayoutParams(-1, dp(activity, 28)));

        TextView searchAction = panelAction(activity, "⌕   البحث");
        searchAction.setOnClickListener(view -> {
            sidePanel.setVisibility(View.GONE);
            search.requestFocus();
            search.setSelection(search.getText().length());
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        });
        sidePanel.addView(searchAction, panelActionParams(activity));

        TextView centerAction = panelAction(activity, "◎   توسيط على موقعي");
        centerAction.setOnClickListener(view -> {
            View action = root.findViewById(R.id.action_map);
            if (action != null) action.performClick();
        });
        sidePanel.addView(centerAction, panelActionParams(activity));

        TextView saveAction = panelAction(activity, "＋   حفظ الموقع الحالي");
        saveAction.setOnClickListener(view -> {
            View action = root.findViewById(R.id.action_save);
            if (action != null) action.performClick();
        });
        sidePanel.addView(saveAction, panelActionParams(activity));

        TextView savedAction = panelAction(activity, "☆   المحفوظات والمسارات");
        savedAction.setOnClickListener(view -> {
            View action = root.findViewById(R.id.action_saved);
            if (action != null) action.performClick();
        });
        sidePanel.addView(savedAction, panelActionParams(activity));

        TextView moreAction = panelAction(activity, "⚙   الإعدادات والمزيد");
        moreAction.setOnClickListener(view -> {
            View action = root.findViewById(R.id.action_more);
            if (action != null) action.performClick();
        });
        sidePanel.addView(moreAction, panelActionParams(activity));

        TextView layersTitle = label(activity, "طبقات خريطة دربك", GOLD, 13f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams layersTitleParams = new LinearLayout.LayoutParams(-1, dp(activity, 30));
        layersTitleParams.topMargin = dp(activity, 5);
        sidePanel.addView(layersTitle, layersTitleParams);
        sidePanel.addView(layerLegend(activity, "●  الطرق المعبدة", Color.rgb(223, 176, 64)));
        sidePanel.addView(layerLegend(activity, "●  الدروب البرية", Color.rgb(176, 92, 51)));
        sidePanel.addView(layerLegend(activity, "●  الشعاب والأودية", Color.rgb(61, 166, 203)));
        sidePanel.addView(layerLegend(activity, "●  الجبال والمعالم", Color.rgb(157, 112, 61)));

        TextView layerHint = label(activity, "تظهر التفاصيل تلقائيًا حسب مستوى التكبير", MUTED, 10.5f, Gravity.CENTER);
        LinearLayout.LayoutParams layerHintParams = new LinearLayout.LayoutParams(-1, dp(activity, 26));
        layerHintParams.topMargin = dp(activity, 4);
        sidePanel.addView(layerHint, layerHintParams);

        tools.addView(sidePanel, frame(dp(activity, 276), dp(activity, 418), Gravity.RIGHT | Gravity.CENTER_VERTICAL,
                dp(activity, 14), 0, 0, 0));

        TextView sideToggle = label(activity, "☰", GOLD, 25f, Gravity.CENTER);
        sideToggle.setContentDescription("فتح لوحة دربك");
        sideToggle.setBackground(round(Color.argb(242, 7, 17, 29), dp(activity, 20), Color.argb(145, 215, 173, 85)));
        sideToggle.setOnClickListener(view -> {
            boolean show = sidePanel.getVisibility() != View.VISIBLE;
            sidePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        });
        panelClose.setOnClickListener(view -> sidePanel.setVisibility(View.GONE));
        tools.addView(sideToggle, frame(dp(activity, 52), dp(activity, 52), Gravity.RIGHT | Gravity.CENTER_VERTICAL,
                dp(activity, 14), 0, 0, 0));

        LinearLayout speedPill = new LinearLayout(activity);
        speedPill.setId(R.id.speed_panel);
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


        LinearLayout trackStatsPanel = new LinearLayout(activity);
        trackStatsPanel.setId(R.id.track_stats_panel);
        trackStatsPanel.setGravity(Gravity.CENTER);
        trackStatsPanel.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        trackStatsPanel.setBackground(round(Color.argb(228, 7, 17, 29), dp(activity, 16), Color.argb(95, 215, 173, 85)));
        TextView trackStats = label(activity, "", TEXT, 12f, Gravity.CENTER);
        trackStats.setId(R.id.track_stats_text);
        trackStatsPanel.addView(trackStats, new LinearLayout.LayoutParams(-1, -1));
        trackStatsPanel.setVisibility(View.GONE);
        root.addView(trackStatsPanel, frame(dp(activity, 335), dp(activity, 46), Gravity.BOTTOM | Gravity.RIGHT,
                dp(activity, 14), 0, 0, dp(activity, 14)));

        LinearLayout empty = new LinearLayout(activity);
        empty.setId(R.id.no_map_panel);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(activity, 28), dp(activity, 22), dp(activity, 28), dp(activity, 22));
        empty.setBackground(round(Color.argb(248, 17, 29, 43), dp(activity, 24), Color.argb(100, 57, 169, 255)));
        empty.addView(label(activity, "الخريطة غير محمّلة", TEXT, 21f, Gravity.CENTER), new LinearLayout.LayoutParams(-1, dp(activity, 42)));
        Button download = button(activity, "خريطة دربك", R.id.download_map, PRIMARY, NIGHT);
        download.setVisibility(View.GONE);
        empty.addView(download, new LinearLayout.LayoutParams(1, 1));
        Button importMap = button(activity, "إضافة خريطة دربك من USB أو الذاكرة", R.id.import_map, SURFACE_ALT, TEXT);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(dp(activity, 290), dp(activity, 52));
        importParams.topMargin = dp(activity, 8);
        empty.addView(importMap, importParams);
        empty.setVisibility(View.GONE);
        root.addView(empty, frame(dp(activity, 400), -2, Gravity.CENTER, 0, 0, 0, 0));

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
        TextView view;
        if (id == R.id.action_more) {
            view = new DarbakMoreButton(activity);
        } else if (id == R.id.action_saved) {
            view = new DarbakSavedButton(activity);
        } else if (id == R.id.action_save) {
            view = new DarbakSaveButton(activity);
        } else {
            view = new TextView(activity);
        }
        view.setText(text);
        view.setTextColor((id == R.id.action_more || id == R.id.action_saved || id == R.id.action_save) ? PRIMARY : TEXT);
        view.setTextSize(13f);
        view.setGravity(Gravity.CENTER);
        view.setId(id);
        view.setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 6));
        return view;
    }

    private static TextView panelAction(Activity activity, String text) {
        TextView view = label(activity, text, TEXT, 14f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        view.setBackground(round(SURFACE_ALT, dp(activity, 16), Color.argb(60, 215, 173, 85)));
        return view;
    }

    private static LinearLayout.LayoutParams panelActionParams(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 43));
        params.topMargin = dp(activity, 6);
        return params;
    }

    private static TextView layerLegend(Activity activity, String text, int color) {
        TextView view = label(activity, text, color, 11.5f, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
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
        if (Color.alpha(stroke) > 0) background.setStroke(1, stroke);
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
