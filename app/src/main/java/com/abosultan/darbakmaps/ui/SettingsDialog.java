package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.MainActivity;
import com.abosultan.darbakmaps.core.MapImportManager;
import com.abosultan.darbakmaps.core.SessionStore;
import java.io.File;

final class SettingsDialog {
 static void show(Context c){
  MapUiSettings s=new MapUiSettings(c);SessionStore session=new SessionStore(c);
  LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);int pad=DarbakUi.dp(c,18);box.setPadding(pad,pad,pad,pad);box.setBackground(DarbakUi.rounded(DarbakUi.BG,DarbakUi.ACCENT,20,c));
  TextView title=label(c,"إعدادات دربك",22,true);title.setTextColor(DarbakUi.ACCENT);box.addView(title,rowParams(c,8));
  TextView hint=label(c,"مركز التحكم بخصائص دربك. جميع الخيارات تحفظ تلقائيًا.",14,false);hint.setTextColor(DarbakUi.TEXT_SECONDARY);box.addView(hint,rowParams(c,12));
  final AlertDialog[] holder=new AlertDialog[1];
  addSection(c,box,"الخريطة");File map=MapImportManager.destination(c);TextView mapInfo=label(c,map.isFile()?"الخريطة الحالية: "+map.getName()+"  •  "+formatSize(map.length()):"لا توجد خريطة معتمدة داخل التطبيق",14,false);mapInfo.setTextColor(DarbakUi.TEXT_SECONDARY);box.addView(mapInfo,rowParams(c,4));
  TextView importMap=DarbakUi.action(c,map.isFile()?"استبدال الخريطة":"إضافة خريطة");importMap.setOnClickListener(v->{if(holder[0]!=null)holder[0].dismiss();if(c instanceof MainActivity)((MainActivity)c).pickOfflineMap();else Toast.makeText(c,"تعذر فتح اختيار الخريطة",Toast.LENGTH_SHORT).show();});box.addView(importMap,rowParams(c,6));
  addSection(c,box,"العرض");addToggle(c,box,s,MapUiSettings.SPEED,"إظهار السرعة");addToggle(c,box,s,MapUiSettings.MAP_TOOLS,"أدوات التكبير والتمركز");addToggle(c,box,s,MapUiSettings.BOTTOM_DOCK,"الشريط السفلي");addToggle(c,box,s,MapUiSettings.TAP_HIDE,"إخفاء الأدوات بالضغط على الخريطة");addToggle(c,box,s,MapUiSettings.AUTO_HIDE,"الإخفاء التلقائي للأدوات");
  addSection(c,box,"المسارات");addSessionToggle(c,box,session,true,"تسجيل المسار تلقائيًا");
  addSection(c,box,"بدء التشغيل");addSessionToggle(c,box,session,false,"تشغيل دربك تلقائيًا مع الشاشة");
  TextView close=DarbakUi.action(c,"إغلاق");close.setTextColor(DarbakUi.TEXT_SECONDARY);close.setOnClickListener(v->{if(holder[0]!=null)holder[0].dismiss();});box.addView(close,rowParams(c,12));
  ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);scroll.addView(box,new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,ScrollView.LayoutParams.WRAP_CONTENT));
  AlertDialog d=new AlertDialog.Builder(c).setView(scroll).create();holder[0]=d;d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.55f);WindowManager.LayoutParams lp=w.getAttributes();lp.width=DarbakUi.dp(c,760);lp.height=DarbakUi.dp(c,520);w.setAttributes(lp);}});d.show();
 }
 private static void addSection(Context c,LinearLayout b,String name){TextView t=label(c,name,17,true);t.setTextColor(DarbakUi.ACCENT);b.addView(t,rowParams(c,10));}
 private static void addToggle(Context c,LinearLayout b,MapUiSettings s,String key,String name){TextView row=DarbakUi.action(c,(s.get(key)?"●  ":"○  ")+name);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);row.setOnClickListener(v->{boolean n=!s.get(key);s.set(key,n);row.setText((n?"●  ":"○  ")+name);Toast.makeText(c,n?"تم التشغيل":"تم الإيقاف",Toast.LENGTH_SHORT).show();});b.addView(row,rowParams(c,6));}
 private static void addSessionToggle(Context c,LinearLayout b,SessionStore s,boolean recording,String name){boolean current=recording?s.shouldResumeTrackRecording():s.shouldAutoLaunch();TextView row=DarbakUi.action(c,(current?"●  ":"○  ")+name);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);row.setOnClickListener(v->{boolean now=recording?s.shouldResumeTrackRecording():s.shouldAutoLaunch();boolean next=!now;if(recording)s.setTrackRecording(next);else s.setAutoLaunch(next);row.setText((next?"●  ":"○  ")+name);Toast.makeText(c,next?"تم التشغيل":"تم الإيقاف",Toast.LENGTH_SHORT).show();});b.addView(row,rowParams(c,6));}
 private static String formatSize(long n){if(n>=1024L*1024L*1024L)return String.format(java.util.Locale.US,"%.1f GB",n/(1024d*1024d*1024d));if(n>=1024L*1024L)return Math.round(n/(1024d*1024d))+" MB";return Math.round(n/1024d)+" KB";}
 private static TextView label(Context c,String v,int sp,boolean bold){TextView t=new TextView(c);t.setText(v);t.setTextColor(DarbakUi.TEXT);t.setTextSize(sp);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 private static LinearLayout.LayoutParams rowParams(Context c,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,DarbakUi.dp(c,50));p.topMargin=DarbakUi.dp(c,top);return p;}
 private SettingsDialog(){}
}
