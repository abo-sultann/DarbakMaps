package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** First real 1024x600 shell for the clean rebuild. Data is intentionally mock-only until engines are connected. */
public final class HomeScreen extends FrameLayout {
    public HomeScreen(Context context) {
        super(context);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setBackgroundColor(DarbakUi.BG);
        build(context);
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

        TextView gps = text(c, "GPS  —", 16, false);
        gps.setGravity(Gravity.CENTER);
        top.addView(gps, new LinearLayout.LayoutParams(DarbakUi.dp(c, 105), DarbakUi.dp(c, 52)));

        TextView speed = text(c, "0 كم/س", 18, true);
        speed.setGravity(Gravity.CENTER);
        top.addView(speed, new LinearLayout.LayoutParams(DarbakUi.dp(c, 115), DarbakUi.dp(c, 52)));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, DarbakUi.dp(c, 72), Gravity.TOP);
        addView(top, topLp);

        TextView search = DarbakUi.action(c, "⌕   ابحث عن موقع أو إحداثية");
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 430), DarbakUi.dp(c, 56), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        searchLp.topMargin = DarbakUi.dp(c, 88);
        addView(search, searchLp);

        LinearLayout tools = new LinearLayout(c);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER);
        String[] toolNames = {"＋", "−", "◎", "◈"};
        for (String name : toolNames) {
            TextView t = DarbakUi.action(c, name);
            t.setTextSize(24);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(DarbakUi.dp(c, 56), DarbakUi.dp(c, 56));
            p.bottomMargin = DarbakUi.dp(c, 10);
            tools.addView(t, p);
        }
        FrameLayout.LayoutParams toolsLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 60), LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        toolsLp.leftMargin = DarbakUi.dp(c, 18);
        addView(tools, toolsLp);

        TextView mapState = text(c, "الخريطة الحالية • المحرك غير مربوط بعد", 16, false);
        mapState.setGravity(Gravity.CENTER);
        mapState.setBackground(DarbakUi.rounded(0xE6102040, DarbakUi.BORDER, 16, c));
        FrameLayout.LayoutParams stateLp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 320), DarbakUi.dp(c, 48), Gravity.CENTER);
        stateLp.topMargin = DarbakUi.dp(c, 85);
        addView(mapState, stateLp);

        LinearLayout dock = new LinearLayout(c);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(DarbakUi.dp(c, 8), DarbakUi.dp(c, 7), DarbakUi.dp(c, 8), DarbakUi.dp(c, 7));
        dock.setBackground(DarbakUi.rounded(0xF2102040, DarbakUi.BORDER, 22, c));
        String[] actions = {"المزيد", "المسارات", "المواقع", "حفظ موقع", "بحث"};
        for (String action : actions) {
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
