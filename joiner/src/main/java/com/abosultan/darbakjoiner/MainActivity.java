package com.abosultan.darbakjoiner;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_FOLDER = 4101;
    private static final int REQ_STORAGE = 4102;
    private static final String BASE = "DarbakMaps-bundle.zip";
    private static final String EXPECTED_SHA256 = "4e7d6daba1da9f75c061c9dedd74bd8e847b94a214cef1bb32af26d5ca99f6cd";

    private TextView status;
    private Button choose;
    private Uri pendingTree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 34, 48, 34);
        root.setBackgroundColor(Color.rgb(7, 17, 14));

        TextView title = new TextView(this);
        title.setText("دربك — دمج الملفات");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("يجمع أجزاء Darbak Maps الثلاثة على الشاشة بدون كمبيوتر");
        subtitle.setTextColor(Color.rgb(199, 164, 75));
        subtitle.setTextSize(18f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.setMargins(0, 14, 0, 26);
        root.addView(subtitle, sub);

        choose = new Button(this);
        choose.setText("اختيار مجلد الأجزاء الثلاثة");
        choose.setTextSize(19f);
        choose.setOnClickListener(v -> chooseFolder());
        root.addView(choose, new LinearLayout.LayoutParams(430, 72));

        status = new TextView(this);
        status.setText("ضع ملفات .001 و .002 و .003 في مجلد واحد ثم اختر هذا المجلد.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(17f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, 26, 0, 0);
        root.addView(status, sp);
        return root;
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null) return;

        if ("file".equalsIgnoreCase(tree.getScheme())) {
            pendingTree = tree;
            ensureLegacyStorageAndJoin();
            return;
        }

        final int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(tree, flags); } catch (Exception ignored) {}
        joinDocumentTree(tree);
    }

    private void ensureLegacyStorageAndJoin() {
        if (Build.VERSION.SDK_INT >= 23 &&
                (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        } else if (pendingTree != null) {
            joinLegacyFolder(new File(pendingTree.getPath()));
            pendingTree = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE) return;
        boolean ok = grantResults.length > 0;
        for (int result : grantResults) ok &= result == PackageManager.PERMISSION_GRANTED;
        if (ok && pendingTree != null) {
            Uri uri = pendingTree;
            pendingTree = null;
            joinLegacyFolder(new File(uri.getPath()));
        } else {
            pendingTree = null;
            status.setText("خطأ: يلزم السماح بالوصول للتخزين لدمج الملفات.");
        }
    }

    private ProgressDialog showProgress() {
        ProgressDialog p = new ProgressDialog(this);
        p.setTitle("Darbak Joiner");
        p.setMessage("جارٍ فحص الأجزاء…");
        p.setIndeterminate(false);
        p.setMax(100);
        p.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        p.setCancelable(false);
        p.show();
        choose.setEnabled(false);
        return p;
    }

    private void joinLegacyFolder(File dir) {
        ProgressDialog p = showProgress();
        new Thread(() -> {
            try {
                if (dir == null || !dir.isDirectory()) throw new Exception("المجلد غير صالح");

                List<File> parts = new ArrayList<>();
                for (int n = 1; n <= 3; n++) {
                    File f = new File(dir, BASE + String.format(Locale.US, ".%03d", n));
                    if (!f.isFile()) throw new Exception("الملف مفقود: " + f.getName());
                    parts.add(f);
                }

                File outFile = new File(dir, BASE);
                if (outFile.exists() && !outFile.delete()) throw new Exception("تعذر استبدال الملف النهائي القديم");

                long total = 0;
                for (File f : parts) total += Math.max(0, f.length());
                long done = 0;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                try (OutputStream rawOut = new FileOutputStream(outFile);
                     BufferedOutputStream out = new BufferedOutputStream(rawOut, 1024 * 1024)) {
                    byte[] buffer = new byte[1024 * 1024];
                    for (File part : parts) {
                        try (InputStream rawIn = new FileInputStream(part);
                             BufferedInputStream in = new BufferedInputStream(rawIn, 1024 * 1024)) {
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                                digest.update(buffer, 0, read);
                                done += read;
                                final int percent = total > 0 ? (int) Math.min(100, (done * 100L) / total) : 0;
                                runOnUiThread(() -> { p.setProgress(percent); p.setMessage("جارٍ الدمج… " + percent + "%"); });
                            }
                        }
                    }
                    out.flush();
                }

                verifyAndFinish(outFile, digest, p);
            } catch (Exception e) {
                fail(p, e);
            }
        }, "darbak-joiner-legacy").start();
    }

    private void joinDocumentTree(Uri treeUri) {
        ProgressDialog p = showProgress();
        new Thread(() -> {
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(this, treeUri);
                if (dir == null || !dir.isDirectory()) throw new Exception("المجلد غير صالح");

                List<DocumentFile> parts = new ArrayList<>();
                for (int n = 1; n <= 3; n++) {
                    String name = BASE + String.format(Locale.US, ".%03d", n);
                    DocumentFile f = dir.findFile(name);
                    if (f == null || !f.isFile()) throw new Exception("الملف مفقود: " + name);
                    parts.add(f);
                }

                DocumentFile old = dir.findFile(BASE);
                if (old != null) old.delete();
                DocumentFile outFile = dir.createFile("application/zip", BASE);
                if (outFile == null) throw new Exception("تعذر إنشاء الملف النهائي");

                long total = 0;
                for (DocumentFile f : parts) total += Math.max(0, f.length());
                long done = 0;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                try (OutputStream rawOut = getContentResolver().openOutputStream(outFile.getUri(), "w");
                     BufferedOutputStream out = new BufferedOutputStream(rawOut, 1024 * 1024)) {
                    if (rawOut == null) throw new Exception("تعذر فتح الملف النهائي");
                    byte[] buffer = new byte[1024 * 1024];
                    for (DocumentFile part : parts) {
                        try (InputStream rawIn = getContentResolver().openInputStream(part.getUri());
                             BufferedInputStream in = new BufferedInputStream(rawIn, 1024 * 1024)) {
                            if (rawIn == null) throw new Exception("تعذر قراءة " + part.getName());
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                                digest.update(buffer, 0, read);
                                done += read;
                                final int percent = total > 0 ? (int) Math.min(100, (done * 100L) / total) : 0;
                                runOnUiThread(() -> { p.setProgress(percent); p.setMessage("جارٍ الدمج… " + percent + "%"); });
                            }
                        }
                    }
                    out.flush();
                }

                String actual = hex(digest.digest());
                if (!EXPECTED_SHA256.equalsIgnoreCase(actual)) {
                    outFile.delete();
                    throw new Exception("فشل التحقق من سلامة الملف. أعد تنزيل الأجزاء الثلاثة.");
                }
                success(p);
            } catch (Exception e) {
                fail(p, e);
            }
        }, "darbak-joiner-tree").start();
    }

    private void verifyAndFinish(File outFile, MessageDigest digest, ProgressDialog p) throws Exception {
        String actual = hex(digest.digest());
        if (!EXPECTED_SHA256.equalsIgnoreCase(actual)) {
            outFile.delete();
            throw new Exception("فشل التحقق من سلامة الملف. أعد تنزيل الأجزاء الثلاثة.");
        }
        success(p);
    }

    private void success(ProgressDialog p) {
        runOnUiThread(() -> {
            p.dismiss();
            choose.setEnabled(true);
            status.setText("تم الدمج والتحقق بنجاح ✅\nالملف الناتج: " + BASE + "\nافتحه لاستخراج APK والخريطة.");
            Toast.makeText(this, "تم إنشاء الحزمة بنجاح", Toast.LENGTH_LONG).show();
        });
    }

    private void fail(ProgressDialog p, Exception e) {
        runOnUiThread(() -> {
            p.dismiss();
            choose.setEnabled(true);
            String m = e.getMessage() == null ? "تعذر دمج الملفات" : e.getMessage();
            status.setText("خطأ: " + m);
            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
        });
    }

    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) b.append(String.format(Locale.US, "%02x", x & 0xff));
        return b.toString();
    }
}
