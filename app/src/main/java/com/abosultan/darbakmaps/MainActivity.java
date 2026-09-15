package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.abosultan.darbakmaps.core.AndroidLocationEngine;
import com.abosultan.darbakmaps.core.CoreContracts.LocationSnapshot;
import com.abosultan.darbakmaps.core.SqliteTrackRecorder;
import com.abosultan.darbakmaps.ui.HomeScreen;

public final class MainActivity extends Activity {
    private static final int GPS_PERMISSION = 25;
    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private AndroidLocationEngine locationEngine;
    private SqliteTrackRecorder trackRecorder;
    private final Runnable trackPump = new Runnable() {
        @Override public void run() {
            if (locationEngine != null && trackRecorder != null) {
                LocationSnapshot point = locationEngine.latest();
                trackRecorder.append(point);
                getWindow().getDecorView().postDelayed(this, 2000L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyImmersiveMode();
        locationEngine = new AndroidLocationEngine(this);
        trackRecorder = new SqliteTrackRecorder(this);
        trackRecorder.restoreAutomaticState();
        setContentView(new HomeScreen(this));
        ensureGpsPermission();
    }

    private void ensureGpsPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, GPS_PERMISSION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GPS_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            locationEngine.start();
        }
        applyImmersiveMode();
    }

    @Override protected void onResume() {
        super.onResume();
        locationEngine.start();
        getWindow().getDecorView().removeCallbacks(trackPump);
        getWindow().getDecorView().post(trackPump);
    }

    @Override protected void onPause() {
        getWindow().getDecorView().removeCallbacks(trackPump);
        locationEngine.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        getWindow().getDecorView().removeCallbacks(trackPump);
        if (trackRecorder != null) trackRecorder.close();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
    }
}
