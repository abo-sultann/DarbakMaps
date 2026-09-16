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

public final class HomeScreen extends FrameLayout {
    private static final long AUTO_HIDE_MS=6500L;
    private final MapUiSettings settings;
    private TextView speedView; private View tools,dock; private boolean controlsVisible=true,moved; private float downX,downY;
    private final Runnable statusPump=new Runnable(){@Override public void run(){LocationSnapshot s=LiveLocationStore.latest();speedView.setText(s.valid?Math.round(s.speedKmh)+" كم/س":"— كم/س");speedView.setVisibility(settings.get(MapUiSettings.SPEED)?VISIBLE:GONE);postDelayed(this,1000L);}};
    private final Runnable hideControls=()->setControlsVisible(false);

    public HomeScreen(Context c){super(c);settings=new MapUiSettings(c);setLayoutDirection(View.LAYOUT_DIRECTION_RTL);setBackgroundColor(DarbakUi.BG);build(c);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(statusPump);post(statusPump);showControls();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(statusPump);removeCallbacks(hideControls);super.onDetachedFromWindow();}

    private void setControlsVisible(boolean visible){controlsVisible=visible;if(tools!=null)tools.setVisibility(visible&&settings.get(MapUiSettings.MAP_TOOLS)?VISIBLE:GONE);if(dock!=null)dock.setVisibility(visible&&settings.get(MapUiSettings.BOTTOM_DOCK)?VISIBLE:GONE);removeCallbacks(hideControls);if(visible&&settings.get(MapUiSettings.AUTO_HIDE))postDelayed(hideControls,AUTO_HIDE_MS);}
    private void showControls(){setControlsVisible(true);} private void toggleControls(){if(settings.get(MapUiSettings.TAP_HIDE))setControlsVisible(!controlsVisible);}

    private void build(Context c){
        final OfflineMapView map=new OfflineMapView(c); map.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();moved=false;}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>18||Math.abs(e.getY()-downY)>18)moved=true;}else if(e.getActionMasked()==MotionEvent.ACTION_UP&&!moved)toggleControls();return false;}); addView(map,new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        speedView=text(c,"— كم/س",22,true);speedView.setGravity(Gravity.CENTER);speedView.setBackground(DarbakUi.rounded(0xB80B0F12,DarbakUi.BORDER,18,c));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(DarbakUi.dp(c,122),DarbakUi.dp(c,52),Gravity.TOP|Gravity.LEFT);sp.leftMargin=DarbakUi.dp(c,18);sp.topMargin=DarbakUi.dp(c,14);addView(speedView,sp);
        LinearLayout toolBox=new LinearLayout(c);tools=toolBox;toolBox.setOrientation(LinearLayout.VERTICAL);toolBox.setGravity(Gravity.CENTER);TextView zi=tool(c,"＋");zi.setContentDescription("تكبير");zi.setOnClickListener(v->{showControls();map.zoomIn();});toolBox.addView(zi,tp(c));TextView zo=tool(c,"−");zo.setContentDescription("تصغير");zo.setOnClickListener(v->{showControls();map.zoomOut();});toolBox.addView(zo,tp(c));TextView rc=tool(c,"◎");rc.setContentDescription("موقعي");rc.setOnClickListener(v->{showControls();if(!map.recenterOnGps())Toast.makeText(c,map.hasMap()?"بانتظار إشارة GPS":"الخريطة غير جاهزة",Toast.LENGTH_SHORT).show();});toolBox.addView(rc,tp(c));FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(DarbakUi.dp(c,60),LayoutParams.WRAP_CONTENT,Gravity.LEFT|Gravity.CENTER_VERTICAL);tlp.leftMargin=DarbakUi.dp(c,18);addView(toolBox,tlp);
        LinearLayout dockBox=new LinearLayout(c);dock=dockBox;dockBox.setGravity(Gravity.CENTER);dockBox.setPadding(DarbakUi.dp(c,8),DarbakUi.dp(c,6),DarbakUi.dp(c,8),DarbakUi.dp(c,6));dockBox.setBackground(DarbakUi.rounded(0xD912191F,DarbakUi.BORDER,24,c));addDock(c,dockBox,"⌕","بحث",v->{showControls();showSearchMenu(c,map);});addDock(c,dockBox,"⌖","حفظ موقع",v->{showControls();picker(c,map);});addDock(c,dockBox,"★","المواقع",v->{showControls();SavedPlacesDialog.show(c,map);});addDock(c,dockBox,"〽","المسارات",v->{showControls();TracksDialog.show(c,map);});TextView more=addDock(c,dockBox,"•••","المزيد",v->{showControls();MoreDialog.show(c,map);});more.setOnLongClickListener(v->{showControls();MoreDialog.showDiagnostics(c,map);return true;});FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(DarbakUi.dp(c,470),DarbakUi.dp(c,68),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dlp.bottomMargin=DarbakUi.dp(c,16);addView(dockBox,dlp);showControls();
    }
    private static TextView addDock(Context c,LinearLayout d,String glyph,String desc,View.OnClickListener l){TextView a=tool(c,glyph);a.setContentDescription(desc);a.setGravity(Gravity.CENTER);a.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,DarbakUi.dp(c,54),1f);if(d.getChildCount()>0)p.rightMargin=DarbakUi.dp(c,7);d.addView(a,p);return a;}
    private static void showSearchMenu(Context c,OfflineMapView map){String[] o={"القريب مني","بحث قريب بالاسم","بحث بإحداثيات"};DarbakDialog.menu(c,"البحث",o,w->{if(w==0)NearbyPoiDialog.show(c,map);else if(w==1)NearbyPoiDialog.showNameSearch(c,map);else showCoordinateSearch(c,map);});}
    private static void showCoordinateSearch(Context c,OfflineMapView map){EditText i=new EditText(c);i.setHint("مثال: 26.3592, 43.9818");i.setHintTextColor(DarbakUi.TEXT_SECONDARY);i.setTextColor(DarbakUi.TEXT);i.setSingleLine(true);i.setTextSize(20f);i.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);int p=DarbakUi.dp(c,18);i.setPadding(p,p,p,p);new AlertDialog.Builder(c).setTitle("بحث بالإحداثيات").setView(i).setPositiveButton("اذهب",(d,w)->{double[] x=CoordinateParser.parse(i.getText().toString());if(x==null){Toast.makeText(c,"الإحداثية غير صحيحة",Toast.LENGTH_SHORT).show();return;}if(!map.focusPlace(x[0],x[1]))Toast.makeText(c,"الخريطة غير جاهزة",Toast.LENGTH_SHORT).show();}).setNegativeButton("إلغاء",null).show();}
    private static void picker(Context c,OfflineMapView map){String[] l={"طير سمان","ماء","مخيم","وقود","أخرى"},v={"summan","water","camp","fuel","other"};DarbakDialog.menu(c,"حفظ الموقع كـ",l,w->{boolean ok=map.saveCurrentPlace(v[w]);Toast.makeText(c,ok?"تم حفظ الموقع — "+l[w]:"بانتظار إشارة GPS",Toast.LENGTH_SHORT).show();});}
    private static TextView tool(Context c,String v){TextView t=DarbakUi.action(c,v);t.setTextSize(24f);t.setTextColor(DarbakUi.ACCENT);return t;}private static LinearLayout.LayoutParams tp(Context c){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(DarbakUi.dp(c,56),DarbakUi.dp(c,56));p.bottomMargin=DarbakUi.dp(c,10);return p;}private static TextView text(Context c,String v,int sp,boolean b){TextView x=new TextView(c);x.setText(v);x.setTextColor(DarbakUi.TEXT);x.setTextSize(sp);if(b)x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return x;}
}
