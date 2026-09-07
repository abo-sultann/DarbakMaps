package com.abosultan.darbakmaps.activation;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.abosultan.darbakmaps.R;

public final class ActivationActivity extends Activity {
    private LicenseManager licenseManager;
    private Button activateButton;
    private TextView messageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        immersive();
        licenseManager = new LicenseManager(this);
        if (licenseManager.isLicensed()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_activation);

        TextView deviceCode = findViewById(R.id.device_code);
        EditText activationCode = findViewById(R.id.activation_code);
        activateButton = findViewById(R.id.activate_button);
        messageView = findViewById(R.id.activation_message);
        deviceCode.setText(licenseManager.deviceCode());

        activateButton.setOnClickListener(view -> {
            activateButton.setEnabled(false);
            messageView.setText("جارٍ التحقق…");
            licenseManager.activate(activationCode.getText().toString(), (success, message) -> runOnUiThread(() -> {
                messageView.setText(message);
                activateButton.setEnabled(true);
                if (success) {
                    setResult(RESULT_OK);
                    finish();
                }
            }));
        });
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            immersive();
        }
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }
}

