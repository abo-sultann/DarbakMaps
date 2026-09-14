package com.abosultan.darbakmaps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Lightweight offline guidance HUD for saved places and direct desert navigation. */
public final class NavigationGuidance {
    private static final String PREFS = "darbak_guidance";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_NAME = "name";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static boolean arrivalShown;

    private NavigationGuidance() {}

    public static void start(Activity activity, String name, double lat, double lon) {
        prefs(activity).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_NAME, name == null ? "موقع محفوظ" : name)
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .apply();
        arrivalShown = false;
        MapRuntimeBridge.navigateTo(activity, lat, lon);
        restore(activity);
    }

    public static void restore(Activity activity) {
        if (!isActive(activity)) {
            hide(activity);
            return;
        }
        View panel = activity.findViewById(R.id.nav_panel);
        if (panel != null) panel.setVisibility(View.VISIBLE);
        TextView name = activity.findViewById(R.id.nav_name);
        if (name != null) name.setText(targetName(activity));
        MapRuntimeBridge.navigateTo(activity, targetLat(activity), targetLon(activity));
    }

    public static void stop(Activity activity) {
        prefs(activity).edit().clear().apply();
        arrivalShown = false;
        MapRuntimeBridge.clearNavigation();
        hide(activity);
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void update(Activity activity, Location location) {
        if (!isActive(activity) || location == null) return;
        guideTo(activity,location,targetName(activity),targetLat(activity),targetLon(activity),true);
    }
    public static void noFix(Activity activity){
        if(!isActive(activity)&&!BacktrackGuidance.isActive(activity))return;
        TextView distance=activity.findViewById(R.id.nav_distance),detail=activity.findViewById(R.id.nav_detail);
        TextView arrow=activity.findViewById(R.id.nav_arrow);
        if(distance!=null)distance.setText("—");if(detail!=null)detail.setText("بانتظار إشارة GPS حديثة");if(arrow!=null)arrow.setAlpha(0.3f);
    }
    public static void guideTo(Activity activity,Location location,String label,double lat,double lon,boolean arrival){
        float[] result = new float[3];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), lat, lon, result);
        MapRuntimeBridge.navigateToFix(activity,lat,lon);
        float meters = Math.max(0f, result[0]);
        float targetBearing = normalize(result[1]);
        float vehicleBearing = location.hasBearing() ? normalize(location.getBearing()) : 0f;
        float relative = normalizeSigned(targetBearing - vehicleBearing);

        View panel = activity.findViewById(R.id.nav_panel);
        TextView arrow = activity.findViewById(R.id.nav_arrow);
        TextView name = activity.findViewById(R.id.nav_name);
        TextView distance = activity.findViewById(R.id.nav_distance);
        TextView detail = activity.findViewById(R.id.nav_detail);
        if (panel != null) panel.setVisibility(View.VISIBLE);
        boolean headingKnown=location.hasBearing()&&location.hasSpeed()&&location.getSpeed()>1f;
        if (arrow != null){arrow.setRotation(headingKnown?relative:targetBearing);arrow.setAlpha(headingKnown?1f:0.45f);}
        if (name != null) name.setText(label);
        if (distance != null) distance.setText(formatDistance(meters));

        String eta = "";
        if (location.hasSpeed() && location.getSpeed() > 2f) {
            long minutes = Math.max(1L, Math.round((meters / location.getSpeed()) / 60f));
            eta = " • تقريبًا " + minutes + " د";
        }
        String mode = MapRuntimeBridge.routingLabel(MapUiPreferences.routingMode(activity));
        String warning = !headingKnown ? " • الاتجاه بالنسبة للشمال حتى تتحرك" : MapUiPreferences.offRouteAlert(activity) && Math.abs(relative) > 70f ? " • عدّل اتجاهك" : "";
        if (detail != null) {
            detail.setText(String.format(Locale.US, "%s • اتجاه %03d°%s%s",
                    mode, Math.round(targetBearing), eta, warning));
        }

        if (arrival && meters <= 40f && !arrivalShown) {
            arrivalShown = true;
            Toast.makeText(activity, "وصلت إلى " + targetName(activity), Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatDistance(float meters) {
        if (meters >= 1000f) return String.format(Locale.US, "باقي %.1f كم", meters / 1000f);
        return "باقي " + Math.round(meters) + " م";
    }

    private static String targetName(Context context) {
        return prefs(context).getString(KEY_NAME, "موقع محفوظ");
    }

    private static double targetLat(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong(KEY_LAT, Double.doubleToLongBits(0d)));
    }

    private static double targetLon(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong(KEY_LON, Double.doubleToLongBits(0d)));
    }

    private static void hide(Activity activity) {
        View panel = activity.findViewById(R.id.nav_panel);
        if (panel != null) panel.setVisibility(View.GONE);
    }

    private static float normalize(float value) {
        float v = value % 360f;
        if (v < 0f) v += 360f;
        return v;
    }

    private static float normalizeSigned(float value) {
        float v = normalize(value);
        return v > 180f ? v - 360f : v;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

