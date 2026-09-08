package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.view.MapView;

/** Map-first gesture shell: tap toggles tools, long press saves a waypoint. */
final class DarbakMapContainer extends FrameLayout {
    private static final long TAP_TIMEOUT_MS = 420L;
    private static final long LONG_PRESS_MS = 650L;
    private static final long AUTO_HIDE_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private float downX;
    private float downY;
    private long downTime;
    private boolean moved;

    private final Runnable autoHide = () -> setToolsVisible(false);

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
        if (!(getContext() instanceof Activity) || getChildCount() == 0 || !(getChildAt(0) instanceof MapView)) {
            return;
        }
        MapView mapView = (MapView) getChildAt(0);
        LatLong point = mapView.getMapViewProjection().fromPixels(x, y);
        if (point == null) {
            return;
        }
        Activity activity = (Activity) getContext();
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("مثال: مدخل الشِعْب أو موقع المخيم");
        input.setPadding(28, 8, 28, 8);

        new AlertDialog.Builder(activity)
                .setTitle("حفظ نقطة على الخريطة")
                .setMessage(String.format(java.util.Locale.US, "%.6f, %.6f", point.latitude, point.longitude))
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "نقطة بر";
                    }
                    new PlaceRepository(activity).add(name, point.latitude, point.longitude);
                    MapRuntimeBridge.showPoint(point.latitude, point.longitude);
                    Toast.makeText(activity, "تم حفظ النقطة", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleTools() {
        View tools = rootView().findViewById(R.id.tools_overlay);
        if (tools == null) {
            return;
        }
        setToolsVisible(tools.getVisibility() != View.VISIBLE);
    }

    private void setToolsVisible(boolean visible) {
        View tools = rootView().findViewById(R.id.tools_overlay);
        if (tools == null) {
            return;
        }
        handler.removeCallbacks(autoHide);
        tools.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && MapUiPreferences.autoHideTools(getContext())) {
            handler.postDelayed(autoHide, AUTO_HIDE_MS);
        }
    }

    private View rootView() {
        Context context = getContext();
        if (context instanceof Activity) {
            return ((Activity) context).getWindow().getDecorView();
        }
        return getRootView();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(autoHide);
        super.onDetachedFromWindow();
    }
}
