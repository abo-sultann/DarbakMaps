package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class SettingsDialog {
    static void show(Context c) {
        MapUiSettings s = new MapUiSettings(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad=DarbakUi.dp(c,18); box.setPadding(pad,pad,pad,pad);
        box.setBackground(DarbakUi.rounded(DarbakUi.BG,DarbakUi.ACCENT,20,c));

        TextView title = label(c,"إعدادات دربك",22,true); title.setTextColor(DarbakUi.ACCENT); box.addView(title,rowParams(c,8));
        TextView hint = label(c,"تحكم بعناصر الخريطة والتشغيل. التغييرات تحفظ تلقائيًا.",14,false); hint.setTextColor(DarbakUi.TEXT_SECONDARY); box.addView(hint,rowParams(c,12));

        addToggle(c,box,s,MapUiSettings.SPEED,"إظهار السرعة");
        addToggle(c,box,s,MapUiSettings.MAP_TOOLS,"أدوات التكبير والتمركز");
        addToggle(c,box,s,MapUiSettings.BOTTOM_DOCK,"الشريط السفلي");
        addToggle(c,box,s,MapUiSettings.TAP_HIDE,"إخفاء الأدوات بالضغط على الخريطة");
        addToggle(c,box,s,MapUiSettings.AUTO_HIDE,"الإخفاء التلقائي للأدوات");

        final AlertDialog[] holder=new AlertDialog[1];
        TextView close=DarbakUi.action(c,"إغلاق"); close.setTextColor(DarbakUi.TEXT_SECONDARY); close.setOnClickListener(v->{if(holder[0]!=null)holder[0].dismiss();}); box.addView(close,rowParams(c,12));
        AlertDialog d=new AlertDialog.Builder(c).setView(box).create(); holder[0]=d;
        d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.55f);}}); d.show();
    }

    private static void addToggle(Context c, LinearLayout box, MapUiSettings s, String key, String name) {
        TextView row=DarbakUi.action(c,(s.get(key)?"●  ":"○  ")+name); row.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        row.setOnClickListener(v->{boolean next=!s.get(key);s.set(key,next);row.setText((next?"●  ":"○  ")+name);Toast.makeText(c,next?"تم التشغيل":"تم الإيقاف",Toast.LENGTH_SHORT).show();});
        box.addView(row,rowParams(c,8));
    }
    private static TextView label(Context c,String v,int sp,boolean bold){TextView t=new TextView(c);t.setText(v);t.setTextColor(DarbakUi.TEXT);t.setTextSize(sp);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private static LinearLayout.LayoutParams rowParams(Context c,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,DarbakUi.dp(c,50));p.topMargin=DarbakUi.dp(c,top);return p;}
    private SettingsDialog(){}
}
