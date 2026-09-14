package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.view.MapView;

/** Map-first gesture shell: tap toggles tools, long press saves the exact touched point. */
final class DarbakMapContainer extends FrameLayout {
    private static final long TAP_TIMEOUT_MS = 420L;
    private static final long LONG_PRESS_MS = 650L;

    private final int touchSlop;
    private float downX;
    private float downY;
    private long downTime;
    private boolean moved;

    DarbakMapContainer(Context context) {
        this(context, null);
    }

    DarbakMapContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = event.getEventTime();
                moved = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                moved = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop) {
                    moved = true;
                    MapRuntimeBridge.suspendFollow();
                }
                break;
            case MotionEvent.ACTION_UP:
                long elapsed = event.getEventTime() - downTime;
                if (!moved && elapsed >= LONG_PRESS_MS) {
                    final float x = event.getX();
                    final float y = event.getY();
                    post(() -> saveWaypointAt(x, y));
                } else if (!moved && elapsed <= TAP_TIMEOUT_MS) {
                    post(this::toggleTools);
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    void showToolsTemporarily() {
        setToolsVisible(true);
    }

    private void saveWaypointAt(float x, float y) {
        if (!(getContext() instanceof Activity) || getChildCount() == 0 || !(getChildAt(0) instanceof MapView)) return;
        MapView mapView = (MapView) getChildAt(0);
        LatLong point = mapView.getMapViewProjection().fromPixels(x, y);
        if (point == null) return;

        PointEditor.show((Activity)getContext(),point.latitude,point.longitude,null);
    }

    private void toggleTools() {
        View tools = rootView().findViewById(R.id.tools_overlay);
        if (tools != null) setToolsVisible(tools.getVisibility() != View.VISIBLE);
    }

    private void setToolsVisible(boolean visible) {
        View tools = rootView().findViewById(R.id.tools_overlay);
        if (tools != null) tools.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showImmersive(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setSystemUiVisibility(immersiveFlags());
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    private int immersiveFlags() {
        return View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private View rootView() {
        Context context = getContext();
        if (context instanceof Activity) return ((Activity) context).getWindow().getDecorView();
        return getRootView();
    }
}

