package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.core.CoordinateParser;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;

public final class HomeScreen extends FrameLayout {
    private TextView gpsView;
    private TextView speedView;

    private final Runnable statusPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot s = LiveLocationStore.latest();
            if (s.valid) {
                gpsView.setText("GPS  ●");
                speedView.setText(Math.round(s.speedKmh) + " كم/س");
            } else {
                gpsView.setText("GPS  —");
                speedView.setText("— كم/س");
            }
            postDelayed(this, 1000L);
        }
    };

    public HomeScreen(Context c) {
        super(c);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setBackgroundColor(DarbakUi.BG);
        build(c);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(statusPump);
        post(statusPump);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(statusPump);
        super.onDetachedFromWindow();
    }

    private void build(Context c) {
        final OfflineMapView map = new OfflineMapView(c);
        addView(map, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(DarbakUi.dp(c, 18), DarbakUi.dp(c, 10), DarbakUi.dp(c, 18), DarbakUi.dp(c, 10));
        top.setBackgroundColor(0xE60A1633);
        TextView title = text(c, "دربك للخرائط", 22, true);
        top.addView(title, new LinearLayout.LayoutParams(0, DarbakUi.dp(c, 52), 1f));
        gpsView = text(c, "GPS  —", 16, false);
        gpsView.setGravity(Gravity.CENTER);
        top.addView(gpsView, new LinearLayout.LayoutParams(DarbakUi.dp(c, 105), DarbakUi.dp(c, 52)));
        speedView = text(c, "— كم/س", 18, true);
        speedView.setGravity(Gravity.CENTER);
        top.addView(speedView, new LinearLayout.LayoutParams(DarbakUi.dp(c, 115), DarbakUi.dp(c, 52)));
        addView(top, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, DarbakUi.dp(c, 72), Gravity.TOP));

        TextView search = DarbakUi.action(c, "⌕   ابحث أو اعرض القريب");
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        search.setOnClickListener(v -> showSearchMenu(c, map));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 430), DarbakUi.dp(c, 56), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        slp.topMargin = DarbakUi.dp(c, 88);
        addView(search, slp);

        LinearLayout tools = new LinearLayout(c);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER);
        TextView zi = tool(c, "＋");
        zi.setOnClickListener(v -> map.zoomIn());
        tools.addView(zi, tp(c));
        TextView zo = tool(c, "−");
        zo.setOnClickListener(v -> map.zoomOut());
        tools.addView(zo, tp(c));
        TextView rc = tool(c, "◎");
        rc.setOnClickListener(v -> map.recenterOnGps());
        tools.addView(rc, tp(c));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 60), LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tlp.leftMargin = DarbakUi.dp(c, 18);
        addView(tools, tlp);

        LinearLayout dock = new LinearLayout(c);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(DarbakUi.dp(c, 8), DarbakUi.dp(c, 7), DarbakUi.dp(c, 8), DarbakUi.dp(c, 7));
        dock.setBackground(DarbakUi.rounded(0xF2102040, DarbakUi.BORDER, 22, c));
        for (String action : new String[]{"المزيد", "المسارات", "المواقع", "حفظ موقع", "بحث"}) {
            TextView a = DarbakUi.action(c, action);
            if ("حفظ موقع".equals(action)) a.setOnClickListener(v -> picker(c, map));
            else if ("المواقع".equals(action)) a.setOnClickListener(v -> SavedPlacesDialog.show(c, map));
            else if ("المسارات".equals(action)) a.setOnClickListener(v -> TracksDialog.show(c, map));
            else if ("بحث".equals(action)) a.setOnClickListener(v -> showSearchMenu(c, map));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, DarbakUi.dp(c, 56), 1f);
            if (dock.getChildCount() > 0) p.rightMargin = DarbakUi.dp(c, 8);
            dock.addView(a, p);
        }
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 680), DarbakUi.dp(c, 72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = DarbakUi.dp(c, 16);
        addView(dock, dlp);
    }

    private static void showSearchMenu(Context c, OfflineMapView map) {
        final String[] options = {"القريب مني", "بحث بإحداثيات"};
        new AlertDialog.Builder(c)
                .setTitle("البحث")
                .setItems(options, (d, which) -> {
                    if (which == 0) NearbyPoiDialog.show(c, map);
                    else showCoordinateSearch(c, map);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static void showCoordinateSearch(Context c, OfflineMapView map) {
        EditText input = new EditText(c);
        input.setHint("مثال: 26.3592, 43.9818");
        input.setSingleLine(true);
        input.setTextSize(20f);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = DarbakUi.dp(c, 18);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(c)
                .setTitle("بحث بالإحداثيات")
                .setMessage("أدخل خط العرض ثم خط الطول. يقبل الأرقام العربية أو الإنجليزية.")
                .setView(input)
                .setPositiveButton("اذهب", (d, w) -> {
                    double[] coordinate = CoordinateParser.parse(input.getText().toString());
                    if (coordinate == null) {
                        Toast.makeText(c, "الإحداثية غير صحيحة", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    map.focusPlace(coordinate[0], coordinate[1]);
                    Toast.makeText(c, "تم إظهار الموقع على الخريطة", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static void picker(Context c, OfflineMapView map) {
        final String[] labels = {"طير سمان", "ماء", "مخيم", "وقود", "أخرى"};
        final String[] values = {"summan", "water", "camp", "fuel", "other"};
        new AlertDialog.Builder(c)
                .setTitle("حفظ الموقع كـ")
                .setItems(labels, (d, w) -> {
                    boolean ok = map.saveCurrentPlace(values[w]);
                    Toast.makeText(c, ok ? "تم حفظ الموقع — " + labels[w] : "بانتظار إشارة GPS", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static TextView tool(Context c, String v) {
        TextView t = DarbakUi.action(c, v);
        t.setTextSize(24f);
        return t;
    }

    private static LinearLayout.LayoutParams tp(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(DarbakUi.dp(c, 56), DarbakUi.dp(c, 56));
        p.bottomMargin = DarbakUi.dp(c, 10);
        return p;
    }

    private static TextView text(Context c, String v, int sp, boolean bold) {
        TextView x = new TextView(c);
        x.setText(v);
        x.setTextColor(DarbakUi.TEXT);
        x.setTextSize(sp);
        x.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        if (bold) x.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return x;
    }
}
