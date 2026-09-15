package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.abosultan.darbakmaps.core.AndroidLocationEngine;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;

/** Lightweight 1024x600 shell. Live GPS status is independent from the future map renderer. */
public final class HomeScreen extends FrameLayout {
    private final AndroidLocationEngine location;
    private TextView gpsView;
    private TextView speedView;
    private final Runnable statusPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot s = location.latest();
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

    public HomeScreen(Context context) {
        super(context);
        location = new AndroidLocationEngine(context);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setBackgroundColor(DarbakUi.BG);
        setContentDescription("Darbak Maps Home Visual Gate 2");
        build(context);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        location.start();
        removeCallbacks(statusPump);
        post(statusPump);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(statusPump);
        location.stop();
        super.onDetachedFromWindow();
    }

    private void build(Context c) {
        MapPlaceholderView map = new MapPlaceholderView(c);
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

        TextView search = DarbakUi.action(c, "⌕   ابحث عن موقع أو إحداثية");
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 430), DarbakUi.dp(c, 56), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        searchLp.topMargin = DarbakUi.dp(c, 88);
        addView(search, searchLp);

        LinearLayout tools = new LinearLayout(c);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER);
        for (String name : new String[]{"＋", "−", "◎", "◈"}) {
            TextView t = DarbakUi.action(c, name);
            t.setTextSize(24);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(DarbakUi.dp(c, 56), DarbakUi.dp(c, 56));
            p.bottomMargin = DarbakUi.dp(c, 10);
            tools.addView(t, p);
        }
        FrameLayout.LayoutParams toolsLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 60), LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        toolsLp.leftMargin = DarbakUi.dp(c, 18);
        addView(tools, toolsLp);

        TextView mapState = text(c, "الخريطة الأوفلاين • بانتظار ربط محرك العرض", 16, false);
        mapState.setGravity(Gravity.CENTER);
        mapState.setBackground(DarbakUi.rounded(0xE6102040, DarbakUi.BORDER, 16, c));
        FrameLayout.LayoutParams stateLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 350), DarbakUi.dp(c, 48), Gravity.CENTER);
        stateLp.topMargin = DarbakUi.dp(c, 85);
        addView(mapState, stateLp);

        LinearLayout dock = new LinearLayout(c);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(DarbakUi.dp(c, 8), DarbakUi.dp(c, 7), DarbakUi.dp(c, 8), DarbakUi.dp(c, 7));
        dock.setBackground(DarbakUi.rounded(0xF2102040, DarbakUi.BORDER, 22, c));
        for (String action : new String[]{"المزيد", "المسارات", "المواقع", "حفظ موقع", "بحث"}) {
            TextView a = DarbakUi.action(c, action);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, DarbakUi.dp(c, 56), 1f);
            if (dock.getChildCount() > 0) p.rightMargin = DarbakUi.dp(c, 8);
            dock.addView(a, p);
        }
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 680), DarbakUi.dp(c, 72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.bottomMargin = DarbakUi.dp(c, 16);
        addView(dock, dockLp);
    }

    private static TextView text(Context c, String value, int sp, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(DarbakUi.TEXT);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
}
