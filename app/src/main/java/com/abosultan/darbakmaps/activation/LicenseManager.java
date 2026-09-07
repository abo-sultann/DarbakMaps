package com.abosultan.darbakmaps.activation;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;

import com.abosultan.darbakmaps.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LicenseManager {
    public interface Callback {
        void onResult(boolean success, String message);
    }

    private static final String PREFS = "darbak_license";
    private static final String KEY_TOKEN = "signed_license";
    private static final String PRODUCT = "darbak-maps";

    // The matching private key belongs only on the activation server.
    private static final String PUBLIC_KEY_BASE64 = "";

    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LicenseManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isLicensed() {
        if (BuildConfig.ALLOW_DEVELOPMENT_ACCESS) {
            return true;
        }
        return verify(preferences.getString(KEY_TOKEN, ""));
    }

    public String deviceCode() {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) {
            androidId = "unknown-device";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((PRODUCT + ":" + androidId).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0 && i % 4 == 0) {
                    value.append('-');
                }
                value.append(String.format(Locale.US, "%02X", digest[i]));
            }
            return value.toString();
        } catch (Exception ignored) {
            return androidId.toUpperCase(Locale.US);
        }
    }

    public void activate(String code, Callback callback) {
        String activationCode = code == null ? "" : code.trim().toUpperCase(Locale.US);
        if (activationCode.length() < 6) {
            callback.onResult(false, "أدخل رمز التفعيل كاملًا");
            return;
        }
        if (BuildConfig.ACTIVATION_URL.isEmpty()) {
            callback.onResult(false, "خادم التفعيل لم يُربط بعد");
            return;
        }
        if (!BuildConfig.ACTIVATION_URL.startsWith("https://")) {
            callback.onResult(false, "عنوان التفعيل غير آمن");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BuildConfig.ACTIVATION_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);

                JSONObject request = new JSONObject();
                request.put("product", PRODUCT);
                request.put("code", activationCode);
                request.put("deviceId", deviceCode());
                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream output = connection.getOutputStream();
                output.write(body);
                output.close();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    callback.onResult(false, "رمز التفعيل غير صالح أو تم استخدامه");
                    return;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                String token = new JSONObject(response.toString()).optString("licenseToken");
                if (!verify(token)) {
                    callback.onResult(false, "تعذر التحقق من ترخيص الجهاز");
                    return;
                }
                preferences.edit().putString(KEY_TOKEN, token).commit();
                callback.onResult(true, "تم تفعيل دربك على هذا الجهاز");
            } catch (Exception error) {
                callback.onResult(false, "تعذر الاتصال بخادم التفعيل");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private boolean verify(String token) {
        if (token == null || token.isEmpty() || PUBLIC_KEY_BASE64.isEmpty()) {
            return false;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return false;
            }
            byte[] payload = Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            byte[] signatureBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject claims = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            if (!PRODUCT.equals(claims.optString("product"))
                    || !deviceCode().equals(claims.optString("deviceId"))) {
                return false;
            }

            byte[] publicBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(publicBytes));
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(parts[0].getBytes(StandardCharsets.US_ASCII));
            return verifier.verify(signatureBytes);
        } catch (Exception ignored) {
            return false;
        }
    }
}

