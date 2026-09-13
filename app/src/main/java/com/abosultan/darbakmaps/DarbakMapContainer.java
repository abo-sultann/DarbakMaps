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

        Activity activity = (Activity) getContext();
        MapRuntimeBridge.showPoint(point.latitude, point.longitude);

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(30, 12, 30, 8);
        form.setGravity(Gravity.RIGHT);

        TextView coordinate = new TextView(activity);
        coordinate.setText(String.format(java.util.Locale.US, "%.6f, %.6f", point.latitude, point.longitude));
        coordinate.setTextColor(Color.DKGRAY);
        coordinate.setTextSize(13f);
        coordinate.setGravity(Gravity.RIGHT);
        form.addView(coordinate, new LinearLayout.LayoutParams(-1, 38));

        String[] typeLabels = {"⛺ مخيم", "⌂ بيت / استراحة", "ط طائر السمان", "💧 ماء / بئر", "♣ شجرة / روضة", "◆ سيارة", "▣ مدخل / بوابة", "★ موقع عام"};
        String[] typeKeys = {PlaceRepository.ICON_CAMP, PlaceRepository.ICON_HOME, PlaceRepository.ICON_QUAIL,
                PlaceRepository.ICON_WATER, PlaceRepository.ICON_TREE, PlaceRepository.ICON_CAR,
                PlaceRepository.ICON_GATE, PlaceRepository.ICON_STAR};
        Spinner type = new Spinner(activity);
        type.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, typeLabels));
        form.addView(type, new LinearLayout.LayoutParams(-1, 58));

        String[] shortcuts = {"بدون اختصار", "مخيم", "بيت", "موقع السمان", "مشب", "مدخل", "موقف", "بئر", "شجرة مميزة", "روضة", "موقع تجمع", "نقطة رجوع"};
        Spinner shortcut = new Spinner(activity);
        shortcut.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, shortcuts));
        form.addView(shortcut, new LinearLayout.LayoutParams(-1, 58));

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("اسم الموقع — مثال: مخيم أبو سلطان");
        input.setPadding(18, 8, 18, 8);
        form.addView(input, new LinearLayout.LayoutParams(-1, 58));

        EditText note = new EditText(activity);
        note.setSingleLine(true);
        note.setHint("ملاحظة اختيارية — طريق الدخول، علامة قريبة...");
        note.setPadding(18, 8, 18, 8);
        form.addView(note, new LinearLayout.LayoutParams(-1, 58));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("حفظ موقع على الخريطة")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (ignored, which) -> {
                    String typed = input.getText().toString().trim();
                    String quick = shortcut.getSelectedItemPosition() <= 0 ? "" : shortcuts[shortcut.getSelectedItemPosition()];
                    String name = typed.isEmpty() ? quick : typed;
                    if (name.isEmpty()) name = "موقع محفوظ";
                    int index = Math.max(0, Math.min(type.getSelectedItemPosition(), typeKeys.length - 1));
                    String category = typeLabels[index].replaceFirst("^[^ ]+\\s*", "").trim();
                    new PlaceRepository(activity).addDetailed(name, point.latitude, point.longitude,
                            typeKeys[index], category, note.getText().toString());
                    MapRuntimeBridge.refreshSavedPlaces(activity);
                    Toast.makeText(activity, "تم حفظ الموقع وعرض علامته على الخريطة", Toast.LENGTH_SHORT).show();
                })
                .create();
        showImmersive(dialog);
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
