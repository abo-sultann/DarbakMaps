package com.abosultan.darbakmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.data.PlaceRepository;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Car-screen saved-place browser: icon filters, nearest ordering, distance and direction. */
public final class SavedPlacesDialog {
    public interface Navigator {
        void navigateTo(PlaceRepository.Place place);
    }

    private static WeakReference<Panel> active = new WeakReference<>(null);

    private SavedPlacesDialog() {}

    public static void show(Activity activity, PlaceRepository repository, Location location, Navigator navigator) {
        Panel previous = active.get();
        if (previous != null) previous.dismiss();
        Panel panel = new Panel(activity, repository, location, navigator);
        active = new WeakReference<>(panel);
        panel.show();
    }

    public static void updateLocation(Location location) {
        Panel panel = active.get();
        if (panel != null) panel.updateLocation(location);
    }

    private static final class Panel {
        private final Activity activity;
        private final PlaceRepository repository;
        private final Navigator navigator;
        private final PlacesAdapter adapter;
        private AlertDialog dialog;
        private ListView list;
        private Location location;
        private String iconFilter;
        private boolean touching;
        private long lastUiUpdate;

        Panel(Activity activity, PlaceRepository repository, Location location, Navigator navigator) {
            this.activity = activity;
            this.repository = repository;
            this.navigator = navigator;
            this.location = location == null ? null : new Location(location);
            this.adapter = new PlacesAdapter(activity);
        }

        void show() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            root.setPadding(10, 8, 10, 8);

            TextView help = new TextView(activity);
            help.setText("اضغط للتوجيه • ضغط مطوّل للتعديل أو الحذف");
            help.setTextSize(14f);
            help.setTextColor(Color.DKGRAY);
            help.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(help, new LinearLayout.LayoutParams(-1, dp(34)));

            HorizontalScrollView filtersScroll = new HorizontalScrollView(activity);
            filtersScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout filters = new LinearLayout(activity);
            filters.setOrientation(LinearLayout.HORIZONTAL);
            filters.setGravity(Gravity.CENTER_VERTICAL);
            addFilter(filters, "الكل", null);
            addFilter(filters, "سمان", PlaceRepository.ICON_QUAIL);
            addFilter(filters, "مخيم", PlaceRepository.ICON_CAMP);
            addFilter(filters, "ماء", PlaceRepository.ICON_WATER);
            addFilter(filters, "شجرة", PlaceRepository.ICON_TREE);
            addFilter(filters, "صيد", PlaceRepository.ICON_HUNTING);
            addFilter(filters, "علامة", PlaceRepository.ICON_STAR);
            filtersScroll.addView(filters);
            root.addView(filtersScroll, new LinearLayout.LayoutParams(-1, dp(52)));

            list = new ListView(activity);
            list.setDividerHeight(1);
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                PlaceRepository.Place place = adapter.getItem(position);
                if (place != null && navigator != null) {
                    navigator.navigateTo(place);
                    if (dialog != null) dialog.dismiss();
                }
            });
            list.setOnItemLongClickListener((parent, view, position, id) -> {
                PlaceRepository.Place place = adapter.getItem(position);
                if (place != null) showEditDelete(place);
                return true;
            });
            list.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) touching = true;
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    touching = false;
                    rebuild(true);
                }
                return false;
            });
            root.addView(list, new LinearLayout.LayoutParams(-1, dp(350)));

            dialog = new AlertDialog.Builder(activity)
                    .setTitle("المواقع المحفوظة")
                    .setView(root)
                    .setNegativeButton("إغلاق", null)
                    .create();
            dialog.setOnDismissListener(d -> {
                Panel current = active.get();
                if (current == this) active.clear();
            });
            dialog.show();
            rebuild(true);
        }

        void dismiss() {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }

        void updateLocation(Location value) {
            location = value == null ? null : new Location(value);
            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdate < 800L) return;
            lastUiUpdate = now;
            activity.runOnUiThread(() -> rebuild(!touching));
        }

        private void addFilter(LinearLayout parent, String label, String key) {
            Button button = new Button(activity);
            button.setText(label);
            button.setTextSize(13f);
            button.setMinWidth(dp(82));
            button.setOnClickListener(v -> {
                iconFilter = key;
                rebuild(true);
            });
            parent.addView(button, new LinearLayout.LayoutParams(dp(92), dp(48)));
        }

        private void rebuild(boolean reorder) {
            if (dialog == null || !dialog.isShowing()) return;
            List<PlaceRepository.Place> places = iconFilter == null
                    ? new ArrayList<>(repository.all())
                    : new ArrayList<>(repository.byIcon(iconFilter));
            if (reorder && location != null) {
                Collections.sort(places, (a, b) -> Float.compare(distance(location, a), distance(location, b)));
            }
            adapter.setData(places, location, reorder);
        }

        private void showEditDelete(PlaceRepository.Place place) {
            String[] actions = {"تعديل", "حذف"};
            new AlertDialog.Builder(activity)
                    .setTitle(place.name)
                    .setItems(actions, (d, which) -> {
                        if (which == 0) {
                            PointEditor.show(activity, place.latitude, place.longitude, place);
                        } else {
                            confirmDelete(place);
                        }
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
        }

        private void confirmDelete(PlaceRepository.Place place) {
            new AlertDialog.Builder(activity)
                    .setTitle("حذف الموقع؟")
                    .setMessage(place.name)
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("حذف", (d, w) -> {
                        try {
                            if (!repository.delete(place.id)) throw new IllegalStateException("الموقع لم يعد موجودًا");
                            MapRuntimeBridge.refreshSavedPlaces(activity);
                            rebuild(true);
                            new AlertDialog.Builder(activity)
                                    .setTitle("تم حذف الموقع")
                                    .setMessage("يمكن التراجع الآن دون تغيير الإحداثيات أو الأيقونة.")
                                    .setNegativeButton("إغلاق", null)
                                    .setPositiveButton("تراجع", (undoDialog, undoWhich) -> {
                                        try {
                                            repository.restore(place);
                                            MapRuntimeBridge.refreshSavedPlaces(activity);
                                            rebuild(true);
                                        } catch (RuntimeException error) {
                                            Toast.makeText(activity, "تعذر التراجع عن الحذف", Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .show();
                        } catch (RuntimeException error) {
                            Toast.makeText(activity,
                                    error.getMessage() == null ? "تعذر حذف الموقع" : error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
        }
    }

    private static final class PlacesAdapter extends BaseAdapter {
        private final Activity activity;
        private final List<PlaceRepository.Place> data = new ArrayList<>();
        private final Map<String, Float> smoothed = new HashMap<>();
        private Location location;

        PlacesAdapter(Activity activity) {
            this.activity = activity;
        }

        void setData(List<PlaceRepository.Place> places, Location location, boolean reordered) {
            data.clear();
            data.addAll(places);
            this.location = location == null ? null : new Location(location);
            if (reordered && data.isEmpty()) smoothed.clear();
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }
        @Override public PlaceRepository.Place getItem(int position) { return position >= 0 && position < data.size() ? data.get(position) : null; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setPadding(dp(8), dp(4), dp(8), dp(4));

                PlaceIconView icon = new PlaceIconView(activity);
                row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

                LinearLayout texts = new LinearLayout(activity);
                texts.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(activity);
                name.setTextSize(18f);
                name.setTextColor(Color.rgb(7, 17, 29));
                TextView detail = new TextView(activity);
                detail.setTextSize(14f);
                detail.setTextColor(Color.DKGRAY);
                texts.addView(name, new LinearLayout.LayoutParams(-1, dp(30)));
                texts.addView(detail, new LinearLayout.LayoutParams(-1, dp(26)));
                row.addView(texts, new LinearLayout.LayoutParams(0, dp(58), 1f));

                DirectionView direction = new DirectionView(activity);
                row.addView(direction, new LinearLayout.LayoutParams(dp(74), dp(58)));
                holder = new RowHolder(icon, name, detail, direction);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            PlaceRepository.Place place = getItem(position);
            holder.icon.setIconKey(place.iconKey);
            holder.name.setText(place.name);
            if (location == null) {
                holder.detail.setText("بانتظار GPS");
                holder.direction.setUnavailable();
                return convertView;
            }

            float meters = distance(location, place);
            float target = DirectionMath.bearing(location.getLatitude(), location.getLongitude(),
                    place.latitude, place.longitude);
            holder.detail.setText(formatDistance(meters));
            boolean movingBearing = location.hasBearing() && location.hasSpeed() && location.getSpeed() >= 0.7f;
            if (movingBearing) {
                float relative = DirectionMath.relative(target, location.getBearing());
                Float old = smoothed.get(place.id);
                float stable = DirectionMath.smoothAngle(old == null ? Float.NaN : old, relative, 0.35f);
                smoothed.put(place.id, stable);
                holder.direction.setRelative(stable);
            } else {
                holder.direction.setCardinal(DirectionMath.cardinalArabic(target));
            }
            return convertView;
        }
    }

    private static final class RowHolder {
        final PlaceIconView icon;
        final TextView name;
        final TextView detail;
        final DirectionView direction;
        RowHolder(PlaceIconView icon, TextView name, TextView detail, DirectionView direction) {
            this.icon = icon; this.name = name; this.detail = detail; this.direction = direction;
        }
    }

    private static final class DirectionView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Float angle;
        private String label = "";

        DirectionView(Activity context) {
            super(context);
            paint.setColor(Color.rgb(7, 62, 45));
            paint.setStyle(Paint.Style.FILL);
            text.setColor(Color.DKGRAY);
            text.setTextSize(dp(12));
            text.setTextAlign(Paint.Align.CENTER);
        }

        void setRelative(float angle) { this.angle = angle; label = ""; invalidate(); }
        void setCardinal(String label) { angle = null; this.label = label == null ? "" : label; invalidate(); }
        void setUnavailable() { angle = null; label = "GPS"; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            if (angle != null) {
                canvas.save();
                canvas.rotate(angle, cx, cy);
                Path arrow = new Path();
                arrow.moveTo(cx, cy - dp(20));
                arrow.lineTo(cx - dp(9), cy + dp(12));
                arrow.lineTo(cx, cy + dp(7));
                arrow.lineTo(cx + dp(9), cy + dp(12));
                arrow.close();
                canvas.drawPath(arrow, paint);
                canvas.restore();
            } else {
                canvas.drawText(label, cx, cy + dp(4), text);
            }
        }
    }

    private static final class PlaceIconView extends View {
        private String key = PlaceRepository.ICON_STAR;
        private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        PlaceIconView(Activity context) {
            super(context);
            gold.setColor(Color.rgb(215, 173, 85));
            gold.setStyle(Paint.Style.FILL);
            line.setColor(Color.rgb(7, 62, 45));
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(2));
            line.setStrokeCap(Paint.Cap.ROUND);
        }

        void setIconKey(String key) { this.key = key; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (PlaceRepository.ICON_QUAIL.equals(key)) {
                canvas.drawOval(new RectF(cx - dp(12), cy - dp(4), cx + dp(7), cy + dp(8)), gold);
                canvas.drawCircle(cx + dp(9), cy - dp(8), dp(6), gold);
                Path beak = new Path();
                beak.moveTo(cx + dp(14), cy - dp(9)); beak.lineTo(cx + dp(21), cy - dp(6)); beak.lineTo(cx + dp(14), cy - dp(4)); beak.close();
                canvas.drawPath(beak, gold);
                canvas.drawLine(cx - dp(5), cy + dp(7), cx - dp(7), cy + dp(14), line);
                canvas.drawLine(cx + dp(2), cy + dp(7), cx + dp(1), cy + dp(14), line);
            } else if (PlaceRepository.ICON_CAMP.equals(key)) {
                Path p = new Path(); p.moveTo(cx, cy - dp(15)); p.lineTo(cx - dp(16), cy + dp(14)); p.lineTo(cx + dp(16), cy + dp(14)); p.close(); canvas.drawPath(p, line);
            } else if (PlaceRepository.ICON_WATER.equals(key)) {
                Path p = new Path(); p.moveTo(cx, cy - dp(17)); p.cubicTo(cx - dp(18), cy + dp(2), cx - dp(10), cy + dp(16), cx, cy + dp(17)); p.cubicTo(cx + dp(10), cy + dp(16), cx + dp(18), cy + dp(2), cx, cy - dp(17)); p.close(); canvas.drawPath(p, line);
            } else if (PlaceRepository.ICON_TREE.equals(key)) {
                canvas.drawRect(cx - dp(3), cy + dp(2), cx + dp(3), cy + dp(16), gold); canvas.drawCircle(cx, cy - dp(5), dp(11), gold); canvas.drawCircle(cx - dp(8), cy + dp(1), dp(8), gold); canvas.drawCircle(cx + dp(8), cy + dp(1), dp(8), gold);
            } else if (PlaceRepository.ICON_HUNTING.equals(key)) {
                canvas.drawCircle(cx, cy, dp(13), line); canvas.drawCircle(cx, cy, dp(4), line); canvas.drawLine(cx - dp(18), cy, cx + dp(18), cy, line); canvas.drawLine(cx, cy - dp(18), cx, cy + dp(18), line);
            } else {
                canvas.drawCircle(cx, cy, dp(12), gold);
            }
        }
    }

    private static float distance(Location location, PlaceRepository.Place place) {
        float[] out = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), place.latitude, place.longitude, out);
        return out[0];
    }

    private static String formatDistance(float meters) {
        if (meters < 1000f) return Math.round(meters) + " م";
        return String.format(Locale.US, "%.1f كم", meters / 1000f);
    }

    private static int dp(float value) {
        return Math.round(value * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }
}
