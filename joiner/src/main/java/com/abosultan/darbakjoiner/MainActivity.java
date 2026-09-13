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

import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_FOLDER = 4101;
    private static final int REQ_STORAGE = 4102;
    private static final String BASE = "DarbakMaps-bundle.zip";
    private static final String EXPECTED_SHA256 = "4e7d6daba1da9f75c061c9dedd74bd8e847b94a214cef1bb32af26d5ca99f6cd";

    private TextView status;
    private Button choose;
    private Uri pendingTree;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48,34,48,34);
        root.setBackgroundColor(Color.rgb(7,17,14));

        TextView title = new TextView(this);
        title.setText("دربك — دمج الملفات");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        TextView sub = new TextView(this);
        sub.setText("يجمع أجزاء Darbak Maps الثلاثة على الشاشة بدون كمبيوتر");
        sub.setTextColor(Color.rgb(199,164,75));
        sub.setTextSize(18f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(-1,-2);
        sp1.setMargins(0,14,0,26);
        root.addView(sub,sp1);

        choose = new Button(this);
        choose.setText("اختيار مجلد الأجزاء الثلاثة");
        choose.setTextSize(19f);
        choose.setOnClickListener(v -> chooseFolder());
        root.addView(choose,new LinearLayout.LayoutParams(430,72));

        status = new TextView(this);
        status.setText("ضع ملفات .001 و .002 و .003 في مجلد واحد ثم اختر هذا المجلد.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(17f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1,-2);
        sp2.setMargins(0,26,0,0);
        root.addView(status,sp2);
        return root;
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,REQ_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        if ("file".equalsIgnoreCase(tree.getScheme())) {
            pendingTree = tree;
            if (Build.VERSION.SDK_INT >= 23 && (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},REQ_STORAGE);
            } else {
                Uri u = pendingTree; pendingTree = null; joinLegacy(new File(u.getPath()));
            }
        } else {
            try { getContentResolver().takePersistableUriPermission(tree,data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch (Exception ignored) {}
            joinTree(tree);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode != REQ_STORAGE) return;
        boolean ok = grantResults.length > 0;
        for (int r : grantResults) ok &= r == PackageManager.PERMISSION_GRANTED;
        if (ok && pendingTree != null) { Uri u = pendingTree; pendingTree = null; joinLegacy(new File(u.getPath())); }
        else { pendingTree = null; status.setText("خطأ: يلزم السماح بالوصول للتخزين."); }
    }

    private ProgressDialog progress() {
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

    private void joinLegacy(File dir) {
        ProgressDialog p = progress();
        new Thread(() -> {
            try {
                if (dir == null || !dir.isDirectory()) throw new Exception("المجلد غير صالح");
                File[] parts = new File[3];
                long total = 0;
                for (int i=0;i<3;i++) { parts[i] = new File(dir,BASE + String.format(Locale.US,".%03d",i+1)); if (!parts[i].isFile()) throw new Exception("الملف مفقود: " + parts[i].getName()); total += parts[i].length(); }
                File out = new File(dir,BASE);
                if (out.exists() && !out.delete()) throw new Exception("تعذر استبدال الملف النهائي القديم");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long done = 0;
                try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(out),1024*1024)) {
                    byte[] buf = new byte[1024*1024];
                    for (File part : parts) try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(part),1024*1024)) {
                        int n; while ((n=in.read(buf))!=-1) { os.write(buf,0,n); md.update(buf,0,n); done += n; final int pc=(int)Math.min(100,(done*100L)/total); runOnUiThread(() -> { p.setProgress(pc); p.setMessage("جارٍ الدمج… " + pc + "%"); }); }
                    }
                }
                verify(md,out,p);
            } catch (Exception e) { fail(p,e); }
        },"darbak-joiner").start();
    }

    private void joinTree(Uri tree) {
        ProgressDialog p = progress();
        new Thread(() -> {
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(this,tree);
                if (dir == null || !dir.isDirectory()) throw new Exception("المجلد غير صالح");
                DocumentFile[] parts = new DocumentFile[3];
                long total = 0;
                for (int i=0;i<3;i++) { String n=BASE + String.format(Locale.US,".%03d",i+1); parts[i]=dir.findFile(n); if (parts[i]==null || !parts[i].isFile()) throw new Exception("الملف مفقود: " + n); total += parts[i].length(); }
                DocumentFile old = dir.findFile(BASE); if (old != null) old.delete();
                DocumentFile out = dir.createFile("application/zip",BASE); if (out == null) throw new Exception("تعذر إنشاء الملف النهائي");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long done=0;
                try (BufferedOutputStream os = new BufferedOutputStream(getContentResolver().openOutputStream(out.getUri(),"w"),1024*1024)) {
                    byte[] buf=new byte[1024*1024];
                    for (DocumentFile part:parts) try (BufferedInputStream in=new BufferedInputStream(getContentResolver().openInputStream(part.getUri()),1024*1024)) {
                        int n; while ((n=in.read(buf))!=-1) { os.write(buf,0,n); md.update(buf,0,n); done += n; final int pc=(int)Math.min(100,(done*100L)/total); runOnUiThread(() -> { p.setProgress(pc); p.setMessage("جارٍ الدمج… " + pc + "%"); }); }
                    }
                }
                String actual=hex(md.digest());
                if (!EXPECTED_SHA256.equalsIgnoreCase(actual)) { out.delete(); throw new Exception("فشل التحقق من سلامة الملف."); }
                success(p);
            } catch (Exception e) { fail(p,e); }
        },"darbak-joiner-tree").start();
    }

    private void verify(MessageDigest md,File out,ProgressDialog p) throws Exception {
        String actual=hex(md.digest());
        if (!EXPECTED_SHA256.equalsIgnoreCase(actual)) { out.delete(); throw new Exception("فشل التحقق من سلامة الملف."); }
        success(p);
    }

    private void success(ProgressDialog p) { runOnUiThread(() -> { p.dismiss(); choose.setEnabled(true); status.setText("تم الدمج والتحقق بنجاح ✅\nالملف الناتج: " + BASE); Toast.makeText(this,"تم إنشاء الحزمة بنجاح",Toast.LENGTH_LONG).show(); }); }
    private void fail(ProgressDialog p,Exception e) { runOnUiThread(() -> { p.dismiss(); choose.setEnabled(true); String m=e.getMessage()==null?"تعذر دمج الملفات":e.getMessage(); status.setText("خطأ: " + m); Toast.makeText(this,m,Toast.LENGTH_LONG).show(); }); }
    private static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for (byte x:b) s.append(String.format(Locale.US,"%02x",x & 0xff)); return s.toString(); }
}
