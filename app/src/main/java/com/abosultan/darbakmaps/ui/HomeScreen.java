package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.CoordinateParser;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;

import java.util.Locale;

public final class HomeScreen extends FrameLayout {
    private static final long AUTO_HIDE_MS = 6500L;
    private static final long LONG_PRESS_MS = 700L;
    private static final float MAP_TOUCH_SLOP_PX = 18f;

    private final MapUiSettings settings;
    private TextView speedView;
    private TextView gpsView;
    private View tools;
    private View dock;
    private boolean controlsVisible = true;
    private boolean mapGestureActive;
    private boolean moved;
    private float downX;
    private float downY;
    private long downEventTime;

    private final Runnable statusPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot s = LiveLocationStore.latest();
            speedView.setText(s.valid ? Math.round(s.speedKmh) + " كم/س" : "— كم/س");
            speedView.setVisibility(settings.get(MapUiSettings.SPEED) ? VISIBLE : GONE);
            updateGpsInfo(s);
            postDelayed(this, 1000L);
        }
    };

    private final Runnable hideControls = () -> setControlsVisible(false);
    private Runnable longPressSave;

    public HomeScreen(Context c) {
        super(c);
        settings = new MapUiSettings(c);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setBackgroundColor(DarbakUi.BG);
        build(c);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(statusPump);
        post(statusPump);
        showControls();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(statusPump);
        removeCallbacks(hideControls);
        mapGestureActive = false;
        super.onDetachedFromWindow();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mapGestureActive = isMapSurfaceTouch(e.getX(), e.getY());
            if (mapGestureActive) {
                downX = e.getX();
                downY = e.getY();
                downEventTime = e.getEventTime();
                moved = false;
            }
        } else if (mapGestureActive && action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(e.getX() - downX) > MAP_TOUCH_SLOP_PX
                    || Math.abs(e.getY() - downY) > MAP_TOUCH_SLOP_PX) {
                moved = true;
            }
        } else if (mapGestureActive && action == MotionEvent.ACTION_POINTER_DOWN) {
            moved = true;
        } else if (action == MotionEvent.ACTION_UP) {
            if (mapGestureActive && !moved) {
                long heldMs = Math.max(0L, e.getEventTime() - downEventTime);
                if (heldMs >= LONG_PRESS_MS) longPressSave.run();
                else toggleControls();
            }
            mapGestureActive = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mapGestureActive = false;
        }
        return super.dispatchTouchEvent(e);
    }

    private boolean isMapSurfaceTouch(float x, float y) {
        return !inside(tools, x, y)
                && !inside(dock, x, y)
                && !inside(speedView, x, y)
                && !inside(gpsView, x, y);
    }

    private static boolean inside(View view, float x, float y) {
        return view != null
                && view.getVisibility() == View.VISIBLE
                && x >= view.getLeft() && x < view.getRight()
                && y >= view.getTop() && y < view.getBottom();
    }

    private void updateGpsInfo(LocationSnapshot s) {
        if (gpsView == null) return;
        if (!controlsVisible) {
            gpsView.setVisibility(GONE);
            return;
        }
        boolean gps = settings.get(MapUiSettings.GPS_INFO);
        boolean alt = settings.get(MapUiSettings.ALTITUDE);
        boolean coord = settings.get(MapUiSettings.COORDINATES);
        if (!gps && !alt && !coord) {
            gpsView.setVisibility(GONE);
            return;
        }
        StringBuilder b = new StringBuilder();
        if (!s.valid) {
            b.append("GPS: بانتظار الإشارة");
        } else {
            if (gps) b.append(s.hasAccuracy ? "GPS ±" + Math.round(s.accuracyMeters) + "م" : "GPS متصل");
            if (alt) {
                if (b.length() > 0) b.append("   ");
                b.append(s.hasAltitude ? "ارتفاع " + Math.round(s.altitudeMeters) + "م" : "ارتفاع —");
            }
            if (coord) {
                if (b.length() > 0) b.append("\n");
                b.append(String.format(Locale.US, "%.5f, %.5f", s.latitude, s.longitude));
            }
        }
        gpsView.setText(b.toString());
        gpsView.setVisibility(VISIBLE);
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (tools != null) tools.setVisibility(visible && settings.get(MapUiSettings.MAP_TOOLS) ? VISIBLE : GONE);
        if (dock != null) dock.setVisibility(visible && settings.get(MapUiSettings.BOTTOM_DOCK) ? VISIBLE : GONE);
        if (gpsView != null) {
            gpsView.setVisibility(visible && (settings.get(MapUiSettings.GPS_INFO)
                    || settings.get(MapUiSettings.ALTITUDE)
                    || settings.get(MapUiSettings.COORDINATES)) ? VISIBLE : GONE);
        }
        removeCallbacks(hideControls);
        if (visible && settings.get(MapUiSettings.AUTO_HIDE)) postDelayed(hideControls, AUTO_HIDE_MS);
    }

    private void showControls() { setControlsVisible(true); }

    private void toggleControls() {
        if (settings.get(MapUiSettings.TAP_HIDE)) setControlsVisible(!controlsVisible);
    }

    private void build(Context c) {
        final OfflineMapView map = new OfflineMapView(c);
        longPressSave = () -> {
            if (mapGestureActive && !moved) pickerAt(c, map, downX, downY);
        };
        addView(map, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        speedView = text(c, "— كم/س", 22, true);
        speedView.setGravity(Gravity.CENTER);
        speedView.setBackground(DarbakUi.rounded(0xB80B0F12, DarbakUi.BORDER, 18, c));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 122), DarbakUi.dp(c, 52), Gravity.TOP | Gravity.LEFT);
        sp.leftMargin = DarbakUi.dp(c, 18);
        sp.topMargin = DarbakUi.dp(c, 14);
        addView(speedView, sp);

        gpsView = text(c, "GPS: بانتظار الإشارة", 14, false);
        gpsView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        gpsView.setPadding(DarbakUi.dp(c, 12), DarbakUi.dp(c, 5), DarbakUi.dp(c, 12), DarbakUi.dp(c, 5));
        gpsView.setBackground(DarbakUi.rounded(0xB80B0F12, DarbakUi.BORDER, 14, c));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 290), DarbakUi.dp(c, 58), Gravity.TOP | Gravity.RIGHT);
        gp.rightMargin = DarbakUi.dp(c, 18);
        gp.topMargin = DarbakUi.dp(c, 14);
        addView(gpsView, gp);

        LinearLayout toolBox = new LinearLayout(c);
        tools = toolBox;
        toolBox.setOrientation(LinearLayout.VERTICAL);
        toolBox.setGravity(Gravity.CENTER);
        TextView zi = tool(c, "＋");
        zi.setContentDescription("تكبير");
        zi.setOnClickListener(v -> { showControls(); map.zoomIn(); });
        toolBox.addView(zi, tp(c));
        TextView zo = tool(c, "−");
        zo.setContentDescription("تصغير");
        zo.setOnClickListener(v -> { showControls(); map.zoomOut(); });
        toolBox.addView(zo, tp(c));
        TextView rc = tool(c, "◎");
        rc.setContentDescription("موقعي");
        rc.setOnClickListener(v -> {
            showControls();
            if (!map.recenterOnGps()) {
                Toast.makeText(c, map.hasMap() ? "بانتظار إشارة GPS" : "الخريطة غير جاهزة", Toast.LENGTH_SHORT).show();
            }
        });
        toolBox.addView(rc, tp(c));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 60), LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tlp.leftMargin = DarbakUi.dp(c, 18);
        addView(toolBox, tlp);

        LinearLayout dockBox = new LinearLayout(c);
        dock = dockBox;
        dockBox.setGravity(Gravity.CENTER);
        dockBox.setPadding(DarbakUi.dp(c, 8), DarbakUi.dp(c, 6), DarbakUi.dp(c, 8), DarbakUi.dp(c, 6));
        dockBox.setBackground(DarbakUi.rounded(0xD912191F, DarbakUi.BORDER, 24, c));
        addDock(c, dockBox, "⌕", "بحث", v -> { showControls(); showSearchMenu(c, map); });
        addDock(c, dockBox, "⌖", "حفظ موقع", v -> { showControls(); picker(c, map); });
        addDock(c, dockBox, "★", "المواقع", v -> { showControls(); SavedPlacesDialog.show(c, map); });
        addDock(c, dockBox, "〽", "المسارات", v -> { showControls(); TracksDialog.show(c, map); });
        TextView more = addDock(c, dockBox, "•••", "المزيد", v -> { showControls(); MoreDialog.show(c, map); });
        more.setOnLongClickListener(v -> {
            showControls();
            MoreDialog.showDiagnostics(c, map);
            return true;
        });
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 470), DarbakUi.dp(c, 68), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = DarbakUi.dp(c, 16);
        addView(dockBox, dlp);
        showControls();
    }

    private static TextView addDock(Context c, LinearLayout d, String glyph, String desc, View.OnClickListener listener) {
        TextView a = tool(c, glyph);
        a.setContentDescription(desc);
        a.setGravity(Gravity.CENTER);
        a.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, DarbakUi.dp(c, 54), 1f);
        if (d.getChildCount() > 0) p.rightMargin = DarbakUi.dp(c, 7);
        d.addView(a, p);
        return a;
    }

    private static void showSearchMenu(Context c, OfflineMapView map) {
        String[] options = {"القريب مني", "بحث قريب بالاسم", "بحث بإحداثيات"};
        DarbakDialog.menu(c, "البحث", options, which -> {
            if (which == 0) NearbyPoiDialog.show(c, map);
            else if (which == 1) NearbyPoiDialog.showNameSearch(c, map);
            else showCoordinateSearch(c, map);
        });
    }

    private static void showCoordinateSearch(Context c, OfflineMapView map) {
        EditText input = new EditText(c);
        input.setHint("مثال: 26.3592, 43.9818");
        input.setHintTextColor(DarbakUi.TEXT_SECONDARY);
        input.setTextColor(DarbakUi.TEXT);
        input.setSingleLine(true);
        input.setTextSize(20f);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int p = DarbakUi.dp(c, 18);
        input.setPadding(p, p, p, p);
        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("بحث بالإحداثيات")
                .setView(input)
                .setPositiveButton("اذهب", (d, w) -> {
                    double[] x = CoordinateParser.parse(input.getText().toString());
                    if (x == null) {
                        Toast.makeText(c, "الإحداثية غير صحيحة", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!map.focusPlace(x[0], x[1])) Toast.makeText(c, "الخريطة غير جاهزة", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .create();
        DarbakUi.showImmersive(dialog);
    }

    private static void picker(Context c, OfflineMapView map) {
        String[] labels = {"طير سمان", "ماء", "مخيم", "وقود", "أخرى"};
        String[] values = {"summan", "water", "camp", "fuel", "other"};
        DarbakDialog.menu(c, "حفظ الموقع كـ", labels, which -> {
            boolean ok = map.saveCurrentPlace(values[which]);
            Toast.makeText(c, ok ? "تم حفظ الموقع — " + labels[which] : "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
        });
    }

    private static void pickerAt(Context c, OfflineMapView map, float x, float y) {
        String[] labels = {"طير سمان", "ماء", "مخيم", "وقود", "أخرى"};
        String[] values = {"summan", "water", "camp", "fuel", "other"};
        DarbakDialog.menu(c, "حفظ هذه النقطة كـ", labels, which -> {
            boolean ok = map.saveMapPlace(x, y, values[which]);
            Toast.makeText(c, ok ? "تم حفظ النقطة — " + labels[which] : "تعذر تحديد النقطة على الخريطة", Toast.LENGTH_SHORT).show();
        });
    }

    private static TextView tool(Context c, String value) {
        TextView t = DarbakUi.action(c, value);
        t.setTextSize(24f);
        t.setTextColor(DarbakUi.ACCENT);
        return t;
    }

    private static LinearLayout.LayoutParams tp(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(DarbakUi.dp(c, 56), DarbakUi.dp(c, 56));
        p.bottomMargin = DarbakUi.dp(c, 10);
        return p;
    }

    private static TextView text(Context c, String value, int sp, boolean bold) {
        TextView x = new TextView(c);
        x.setText(value);
        x.setTextColor(DarbakUi.TEXT);
        x.setTextSize(sp);
        if (bold) x.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return x;
    }
}
