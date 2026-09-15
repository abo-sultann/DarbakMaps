package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.abosultan.darbakmaps.core.TrackRecordingService;
import com.abosultan.darbakmaps.ui.HomeScreen;

public final class MainActivity extends Activity {
    private static final int STARTUP_PERMISSIONS = 25;
    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyImmersiveMode();
        setContentView(new HomeScreen(this));
        ensurePermissions();
    }

    private void ensurePermissions() {
        boolean gps = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean storage = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (gps && storage) {
            startTrackService();
            return;
        }
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (!gps) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!storage) missing.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        requestPermissions(missing.toArray(new String[0]), STARTUP_PERMISSIONS);
    }

    private void startTrackService() { startService(new Intent(this, TrackRecordingService.class)); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STARTUP_PERMISSIONS
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startTrackService();
        }
        applyImmersiveMode();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void applyImmersiveMode() { getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS); }
}
