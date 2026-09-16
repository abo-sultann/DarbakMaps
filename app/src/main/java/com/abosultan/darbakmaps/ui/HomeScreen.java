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
    private static final long AUTO_HIDE_MS = 6500L;
    private TextView speedView;
    private View[] transientControls;
    private boolean controlsVisible = true;
    private boolean moved;
    private float downX, downY;

    private final Runnable statusPump = new Runnable() {
        @Override public void run() {
            LocationSnapshot s = LiveLocationStore.latest();
            speedView.setText(s.valid ? Math.round(s.speedKmh) + " كم/س" : "— كم/س");
            postDelayed(this, 1000L);
        }
    };
    private final Runnable hideControls = () -> setControlsVisible(false);

    public HomeScreen(Context c) {
        super(c);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setBackgroundColor(DarbakUi.BG);
        build(c);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(statusPump); post(statusPump); showControls();
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(statusPump); removeCallbacks(hideControls); super.onDetachedFromWindow();
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (transientControls != null) for (View v : transientControls) v.setVisibility(visible ? View.VISIBLE : View.GONE);
        removeCallbacks(hideControls);
        if (visible) postDelayed(hideControls, AUTO_HIDE_MS);
    }
    private void showControls() { setControlsVisible(true); }
    private void toggleControls() { setControlsVisible(!controlsVisible); }

    private void build(Context c) {
        final OfflineMapView map = new OfflineMapView(c);
        map.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX=e.getX(); downY=e.getY(); moved=false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (Math.abs(e.getX()-downX)>18 || Math.abs(e.getY()-downY)>18) moved=true;
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved) {
                toggleControls();
            }
            return false;
        });
        addView(map, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        speedView = text(c, "— كم/س", 22, true);
        speedView.setGravity(Gravity.CENTER);
        speedView.setBackground(DarbakUi.rounded(0xB80B0F12, DarbakUi.BORDER, 18, c));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(DarbakUi.dp(c, 122), DarbakUi.dp(c, 52), Gravity.TOP | Gravity.LEFT);
        sp.leftMargin=DarbakUi.dp(c,18); sp.topMargin=DarbakUi.dp(c,14); addView(speedView, sp);

        LinearLayout tools = new LinearLayout(c);
        tools.setOrientation(LinearLayout.VERTICAL); tools.setGravity(Gravity.CENTER);
        TextView zi=tool(c,"＋"); zi.setContentDescription("تكبير"); zi.setOnClickListener(v->{showControls();map.zoomIn();}); tools.addView(zi,tp(c));
        TextView zo=tool(c,"−"); zo.setContentDescription("تصغير"); zo.setOnClickListener(v->{showControls();map.zoomOut();}); tools.addView(zo,tp(c));
        TextView rc=tool(c,"◎"); rc.setContentDescription("موقعي"); rc.setOnClickListener(v->{showControls();if(!map.recenterOnGps())Toast.makeText(c,map.hasMap()?"بانتظار إشارة GPS":"الخريطة غير جاهزة",Toast.LENGTH_SHORT).show();}); tools.addView(rc,tp(c));
        FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(DarbakUi.dp(c,60),LayoutParams.WRAP_CONTENT,Gravity.LEFT|Gravity.CENTER_VERTICAL); tlp.leftMargin=DarbakUi.dp(c,18); addView(tools,tlp);

        LinearLayout dock=new LinearLayout(c); dock.setGravity(Gravity.CENTER); dock.setPadding(DarbakUi.dp(c,8),DarbakUi.dp(c,6),DarbakUi.dp(c,8),DarbakUi.dp(c,6)); dock.setBackground(DarbakUi.rounded(0xD912191F,DarbakUi.BORDER,24,c));
        addDock(c,dock,"⌕","بحث",v->{showControls();showSearchMenu(c,map);});
        addDock(c,dock,"⌖","حفظ موقع",v->{showControls();picker(c,map);});
        addDock(c,dock,"★","المواقع",v->{showControls();SavedPlacesDialog.show(c,map);});
        addDock(c,dock,"〽","المسارات",v->{showControls();TracksDialog.show(c,map);});
        TextView more=addDock(c,dock,"•••","المزيد",v->{showControls();MoreDialog.show(c,map);}); more.setOnLongClickListener(v->{showControls();MoreDialog.showDiagnostics(c,map);return true;});
        FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(DarbakUi.dp(c,470),DarbakUi.dp(c,68),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL); dlp.bottomMargin=DarbakUi.dp(c,16); addView(dock,dlp);

        transientControls=new View[]{tools,dock};
    }

    private static TextView addDock(Context c, LinearLayout dock, String glyph, String desc, View.OnClickListener listener) {
        TextView a=tool(c,glyph); a.setContentDescription(desc); a.setGravity(Gravity.CENTER); a.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,DarbakUi.dp(c,54),1f); if(dock.getChildCount()>0)p.rightMargin=DarbakUi.dp(c,7); dock.addView(a,p); return a;
    }

    private static void showSearchMenu(Context c, OfflineMapView map) {
        final String[] options={"القريب مني","بحث قريب بالاسم","بحث بإحداثيات"};
        DarbakDialog.menu(c,"البحث",options,which->{if(which==0)NearbyPoiDialog.show(c,map);else if(which==1)NearbyPoiDialog.showNameSearch(c,map);else showCoordinateSearch(c,map);});
    }
    private static void showCoordinateSearch(Context c, OfflineMapView map) {
        EditText input=new EditText(c); input.setHint("مثال: 26.3592, 43.9818"); input.setHintTextColor(DarbakUi.TEXT_SECONDARY); input.setTextColor(DarbakUi.TEXT); input.setSingleLine(true); input.setTextSize(20f); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); int pad=DarbakUi.dp(c,18); input.setPadding(pad,pad,pad,pad);
        new AlertDialog.Builder(c).setTitle("بحث بالإحداثيات").setMessage("أدخل خط العرض ثم خط الطول. يقبل الأرقام العربية أو الإنجليزية.").setView(input).setPositiveButton("اذهب",(d,w)->{double[] coordinate=CoordinateParser.parse(input.getText().toString());if(coordinate==null){Toast.makeText(c,"الإحداثية غير صحيحة",Toast.LENGTH_SHORT).show();return;}if(!map.focusPlace(coordinate[0],coordinate[1])){Toast.makeText(c,"الخريطة غير جاهزة",Toast.LENGTH_SHORT).show();return;}Toast.makeText(c,"تم إظهار الموقع على الخريطة",Toast.LENGTH_SHORT).show();}).setNegativeButton("إلغاء",null).show();
    }
    private static void picker(Context c, OfflineMapView map) {
        final String[] labels={"طير سمان","ماء","مخيم","وقود","أخرى"}; final String[] values={"summan","water","camp","fuel","other"}; DarbakDialog.menu(c,"حفظ الموقع كـ",labels,w->{boolean ok=map.saveCurrentPlace(values[w]);Toast.makeText(c,ok?"تم حفظ الموقع — "+labels[w]:"بانتظار إشارة GPS",Toast.LENGTH_SHORT).show();});
    }
    private static TextView tool(Context c,String v){TextView t=DarbakUi.action(c,v);t.setTextSize(24f);t.setTextColor(DarbakUi.ACCENT);return t;}
    private static LinearLayout.LayoutParams tp(Context c){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(DarbakUi.dp(c,56),DarbakUi.dp(c,56));p.bottomMargin=DarbakUi.dp(c,10);return p;}
    private static TextView text(Context c,String v,int sp,boolean bold){TextView x=new TextView(c);x.setText(v);x.setTextColor(DarbakUi.TEXT);x.setTextSize(sp);x.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);if(bold)x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return x;}
}
