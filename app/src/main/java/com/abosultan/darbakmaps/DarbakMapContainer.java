package com.abosultan.darbakmaps;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Map container that treats a clean single tap as a request to show/hide controls. */
final class DarbakMapContainer extends FrameLayout {
    private static final long TAP_TIMEOUT_MS = 420L;
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
                if (!moved && elapsed <= TAP_TIMEOUT_MS) {
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
