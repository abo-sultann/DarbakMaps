package com.abosultan.darbakmaps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.LiveLocationStore;

/** Lightweight 1024x600 shell over a real offline map surface. */
public final class HomeScreen extends FrameLayout {
    private TextView gpsView; private TextView speedView;
    private final Runnable statusPump=new Runnable(){@Override public void run(){LocationSnapshot s=LiveLocationStore.latest();if(s.valid){gpsView.setText("GPS  ●");speedView.setText(Math.round(s.speedKmh)+" كم/س");}else{gpsView.setText("GPS  —");speedView.setText("— كم/س");}postDelayed(this,1000L);}};
    public HomeScreen(Context c){super(c);setLayoutDirection(View.LAYOUT_DIRECTION_RTL);setBackgroundColor(DarbakUi.BG);setContentDescription("Darbak Maps Offline Home");build(c);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(statusPump);post(statusPump);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(statusPump);super.onDetachedFromWindow();}
    private void build(Context c){
        final OfflineMapView map=new OfflineMapView(c);addView(map,new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(DarbakUi.dp(c,18),DarbakUi.dp(c,10),DarbakUi.dp(c,18),DarbakUi.dp(c,10));top.setBackgroundColor(0xE60A1633);TextView title=text(c,"دربك للخرائط",22,true);top.addView(title,new LinearLayout.LayoutParams(0,DarbakUi.dp(c,52),1f));gpsView=text(c,"GPS  —",16,false);gpsView.setGravity(Gravity.CENTER);top.addView(gpsView,new LinearLayout.LayoutParams(DarbakUi.dp(c,105),DarbakUi.dp(c,52)));speedView=text(c,"— كم/س",18,true);speedView.setGravity(Gravity.CENTER);top.addView(speedView,new LinearLayout.LayoutParams(DarbakUi.dp(c,115),DarbakUi.dp(c,52)));addView(top,new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,DarbakUi.dp(c,72),Gravity.TOP));
        TextView search=DarbakUi.action(c,"⌕   ابحث عن موقع أو إحداثية");search.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);FrameLayout.LayoutParams slp=new FrameLayout.LayoutParams(DarbakUi.dp(c,430),DarbakUi.dp(c,56),Gravity.TOP|Gravity.CENTER_HORIZONTAL);slp.topMargin=DarbakUi.dp(c,88);addView(search,slp);
        LinearLayout tools=new LinearLayout(c);tools.setOrientation(LinearLayout.VERTICAL);tools.setGravity(Gravity.CENTER);TextView zi=tool(c,"＋");zi.setOnClickListener(v->map.zoomIn());tools.addView(zi,toolParams(c));TextView zo=tool(c,"−");zo.setOnClickListener(v->map.zoomOut());tools.addView(zo,toolParams(c));TextView rc=tool(c,"◎");rc.setContentDescription("إعادة التمركز على السيارة");rc.setOnClickListener(v->map.recenterOnGps());tools.addView(rc,toolParams(c));TextView or=tool(c,"◈");or.setContentDescription("الخريطة باتجاه الشمال");tools.addView(or,toolParams(c));FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(DarbakUi.dp(c,60),LayoutParams.WRAP_CONTENT,Gravity.LEFT|Gravity.CENTER_VERTICAL);tlp.leftMargin=DarbakUi.dp(c,18);addView(tools,tlp);
        if(!map.hasMap()){TextView st=text(c,"ضع ملف .map في DarbakMaps أو Maps على الذاكرة/SD",15,false);st.setGravity(Gravity.CENTER);st.setBackground(DarbakUi.rounded(0xE6102040,DarbakUi.BORDER,16,c));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(DarbakUi.dp(c,470),DarbakUi.dp(c,48),Gravity.CENTER);lp.topMargin=DarbakUi.dp(c,85);addView(st,lp);}
        LinearLayout dock=new LinearLayout(c);dock.setGravity(Gravity.CENTER);dock.setPadding(DarbakUi.dp(c,8),DarbakUi.dp(c,7),DarbakUi.dp(c,8),DarbakUi.dp(c,7));dock.setBackground(DarbakUi.rounded(0xF2102040,DarbakUi.BORDER,22,c));
        for(String action:new String[]{"المزيد","المسارات","المواقع","حفظ موقع","بحث"}){TextView a=DarbakUi.action(c,action);if("حفظ موقع".equals(action)){a.setOnClickListener(v->showPlaceCategoryPicker(c,map));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,DarbakUi.dp(c,56),1f);if(dock.getChildCount()>0)p.rightMargin=DarbakUi.dp(c,8);dock.addView(a,p);}
        FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(DarbakUi.dp(c,680),DarbakUi.dp(c,72),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dlp.bottomMargin=DarbakUi.dp(c,16);addView(dock,dlp);
    }
    private static void showPlaceCategoryPicker(Context c, OfflineMapView map){
        final String[] labels={"طير سمان","ماء","مخيم","وقود","أخرى"};
        final String[] values={"summan","water","camp","fuel","other"};
        new AlertDialog.Builder(c).setTitle("حفظ الموقع كـ").setItems(labels,(d,which)->{
            boolean ok=map.saveCurrentPlace(values[which]);
            Toast.makeText(c,ok?"تم حفظ الموقع — "+labels[which]:"بانتظار إشارة GPS",Toast.LENGTH_SHORT).show();
        }).setNegativeButton("إلغاء",null).show();
    }
    private static TextView tool(Context c,String v){TextView t=DarbakUi.action(c,v);t.setTextSize(24);return t;}
    private static LinearLayout.LayoutParams toolParams(Context c){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(DarbakUi.dp(c,56),DarbakUi.dp(c,56));p.bottomMargin=DarbakUi.dp(c,10);return p;}
    private static TextView text(Context c,String v,int sp,boolean bold){TextView x=new TextView(c);x.setText(v);x.setTextColor(DarbakUi.TEXT);x.setTextSize(sp);x.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);if(bold)x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return x;}
}
