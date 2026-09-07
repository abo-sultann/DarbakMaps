package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abosultan.darbakmaps.activation.ActivationActivity;
import com.abosultan.darbakmaps.activation.LicenseManager;
import com.abosultan.darbakmaps.data.GeoPoint;
import com.abosultan.darbakmaps.data.PlaceRepository;
import com.abosultan.darbakmaps.data.TrackRecorder;
import com.abosultan.darbakmaps.data.TrackStorage;
import com.abosultan.darbakmaps.location.LocationController;
import com.abosultan.darbakmaps.map.DarbakPreviewMapView;
import com.abosultan.darbakmaps.map.MapStorage;
import com.abosultan.darbakmaps.map.OfflineMapSearchEngine;
import com.abosultan.darbakmaps.map.OfflineMapController;
import com.abosultan.darbakmaps.map.RecommendedMapDownloader;
import com.abosultan.darbakmaps.update.UpdateManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements LocationController.Callback {
    private static final int REQUEST_ACTIVATION = 700;
    private static final int REQUEST_LOCATION = 701;
    private static final int REQUEST_MAP_FILE = 702;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final TrackRecorder trackRecorder = new TrackRecorder();
    private final OfflineMapSearchEngine searchEngine = new OfflineMapSearchEngine();

    private LicenseManager licenseManager;
    private LocationController locationController;
    private PlaceRepository placeRepository;
    private OfflineMapController mapController;
    private FrameLayout mapContainer;
    private View noMapPanel;
    private TextView gpsStatus;
    private TextView speedValue;
    private TextView actionRecord;
    private TextView modeDesert;
    private TextView modeCity;
    private boolean initialized;
    private volatile boolean mapDownloadCancelled;
    private volatile boolean searchRunning;
    private String startupPhase = "بدء التشغيل";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        immersive();
        licenseManager = new LicenseManager(this);
        if (!licenseManager.isLicensed()) {
            startActivityForResult(new Intent(this, ActivationActivity.class), REQUEST_ACTIVATION);
            return;
        }
        initializeSafely();
    }

    private void initializeSafely() {
        try {
            initializeApp();
        } catch (Throwable error) {
            initialized = false;
            saveStartupFailure(error);
            showStartupRecovery(error);
        }
    }

    private void initializeApp() {
        if (initialized) {
            return;
        }
        initialized = true;
        startupPhase = "إنشاء واجهة شاشة السيارة";
        setContentView(CarScreenLayout.create(this));

        startupPhase = "ربط عناصر الواجهة";
        mapContainer = findViewById(R.id.map_container);
        noMapPanel = findViewById(R.id.no_map_panel);
        gpsStatus = findViewById(R.id.gps_status);
        speedValue = findViewById(R.id.speed_value);
        actionRecord = findViewById(R.id.action_record);
        modeDesert = findViewById(R.id.mode_desert);
        modeCity = findViewById(R.id.mode_city);

        startupPhase = "فتح البيانات المحلية";
        placeRepository = new PlaceRepository(this);
        locationController = new LocationController(this, this);
        startupPhase = "ربط الأزرار";
        bindActions();
        startupPhase = "تجهيز شاشة الخريطة";
        loadActiveMap();
        startupPhase = "تشغيل GPS";
        try {
            ensureLocationPermission();
        } catch (RuntimeException error) {
            gpsStatus.setText("GPS متاح بعد منح الصلاحية من إعدادات الجهاز");
        }
        startupPhase = "اكتمل";
    }

    private void showStartupRecovery(Throwable error) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(48, 32, 48, 32);
        panel.setBackgroundColor(Color.rgb(255, 249, 235));

        TextView title = new TextView(this);
        title.setText("دربك — وضع التشغيل الآمن");
        title.setTextColor(Color.rgb(3, 39, 30));
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        String detail = error.getClass().getSimpleName();
        if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
            detail += ": " + error.getMessage();
        }
        message.setText("تم منع انهيار التطبيق.\nالمرحلة: " + startupPhase + "\nالسبب: " + detail);
        message.setTextColor(Color.rgb(92, 110, 103));
        message.setTextSize(17f);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 20, 0, 24);
        panel.addView(message, messageParams);

        Button retry = new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setOnClickListener(view -> initializeSafely());
        panel.addView(retry, new LinearLayout.LayoutParams(280, 64));
        setContentView(panel);
    }

    private void saveStartupFailure(Throwable error) {
        File report = new File(getFilesDir(), "last_startup_failure.txt");
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(report, false))) {
            error.printStackTrace(writer);
        } catch (Exception ignored) {
            // The recovery screen is still useful when the vendor ROM blocks file writes.
        }
    }

    private void bindActions() {
        findViewById(R.id.zoom_in).setOnClickListener(view -> {
            if (mapController != null) {
                mapController.zoomIn();
            }
        });
        findViewById(R.id.zoom_out).setOnClickListener(view -> {
            if (mapController != null) {
                mapController.zoomOut();
            }
        });
        findViewById(R.id.center_location).setOnClickListener(view -> centerOnCurrentLocation());
        findViewById(R.id.import_map).setOnClickListener(view -> chooseMapFile());
        findViewById(R.id.download_map).setOnClickListener(view -> confirmRecommendedMapDownload());
        findViewById(R.id.action_map).setOnClickListener(view -> centerOnCurrentLocation());
        findViewById(R.id.action_save).setOnClickListener(view -> saveCurrentPlace());
        actionRecord.setOnClickListener(view -> toggleTrackRecording());
        findViewById(R.id.action_saved).setOnClickListener(view -> showSavedHub());
        findViewById(R.id.action_more).setOnClickListener(view -> showMore());

        modeDesert.setOnClickListener(view -> selectMapMode(true));
        modeCity.setOnClickListener(view -> selectMapMode(false));

        EditText search = findViewById(R.id.search_input);
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(search);
                performSearch(search.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void loadActiveMap() {
        searchEngine.clear();
        if (mapController != null) {
            mapController.destroy();
            mapController = null;
        }
        mapContainer.removeAllViews();
        File mapFile = MapStorage.activeMap(this);
        if (mapFile.isFile() && mapFile.length() > 0) {
            try {
                mapController = new OfflineMapController(this, mapFile);
                mapContainer.addView(mapController.view(), new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
                noMapPanel.setVisibility(View.GONE);
                return;
            } catch (RuntimeException error) {
                toast("تعذر فتح حزمة الخريطة");
            }
        }
        mapContainer.addView(new DarbakPreviewMapView(this), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        noMapPanel.setVisibility(View.VISIBLE);
    }

    private void chooseMapFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MAP_FILE);
    }

    private void importMap(Uri uri) {
        ProgressDialog progress = ProgressDialog.show(this, "خرائط دربك", "جارٍ تجهيز الخريطة…", true, false);
        ioExecutor.execute(() -> {
            try {
                MapStorage.importMap(this, uri);
                runOnUiThread(() -> {
                    progress.dismiss();
                    toast("تمت إضافة الخريطة وأصبحت جاهزة أوفلاين");
                    loadActiveMap();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    toast(error.getMessage() == null ? "تعذر إضافة الخريطة" : error.getMessage());
                });
            }
        });
    }

    private void confirmRecommendedMapDownload() {
        new AlertDialog.Builder(this)
                .setTitle("خريطة الخليج الأوفلاين")
                .setMessage("تشمل السعودية ودول الخليج وتعمل بعد تنزيلها دون إنترنت. "
                        + "حجم التنزيل " + RecommendedMapDownloader.DISPLAY_SIZE
                        + ". يُفضّل استخدام Wi‑Fi.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تنزيل", (dialog, which) -> downloadRecommendedMap())
                .show();
    }

    private void downloadRecommendedMap() {
        mapDownloadCancelled = false;
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("خرائط دربك");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setMessage("بدء تنزيل خريطة الخليج…");
        progress.setCancelable(true);
        progress.setCanceledOnTouchOutside(false);
        progress.setOnCancelListener(dialog -> mapDownloadCancelled = true);
        progress.show();

        ioExecutor.execute(() -> {
            try {
                RecommendedMapDownloader.download(this, new RecommendedMapDownloader.Listener() {
                    @Override
                    public void onProgress(int percent, String message) {
                        runOnUiThread(() -> {
                            if (!isActivityUnavailable()) {
                                progress.setProgress(percent);
                                progress.setMessage(message);
                            }
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return mapDownloadCancelled || Thread.currentThread().isInterrupted();
                    }
                });
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) {
                        return;
                    }
                    progress.dismiss();
                    toast("تمت إضافة خريطة الخليج وأصبحت جاهزة أوفلاين");
                    loadActiveMap();
                });
            } catch (RecommendedMapDownloader.CancelledException cancelled) {
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        progress.dismiss();
                        toast(cancelled.getMessage());
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        progress.dismiss();
                        toast(error.getMessage() == null ? "تعذر تنزيل الخريطة" : error.getMessage());
                    }
                });
            }
        });
    }

    private void performSearch(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            toast("اكتب اسم مدينة أو مكان للبحث");
            return;
        }
        if (searchRunning) {
            toast("البحث السابق ما زال جاريًا");
            return;
        }
        searchRunning = true;
        ProgressDialog progress = ProgressDialog.show(
                this,
                "بحث أوفلاين",
                "جارٍ البحث داخل الخريطة والمواقع المحفوظة…",
                true,
                false
        );
        File activeMap = MapStorage.activeMap(this);
        Location current = locationController == null ? null : locationController.getLastLocation();
        Double latitude = current == null ? null : current.getLatitude();
        Double longitude = current == null ? null : current.getLongitude();

        ioExecutor.execute(() -> {
            List<OfflineMapSearchEngine.Result> results = searchEngine.search(
                    query,
                    activeMap.isFile() ? activeMap : null,
                    placeRepository.all(),
                    latitude,
                    longitude,
                    40
            );
            runOnUiThread(() -> {
                searchRunning = false;
                if (isActivityUnavailable()) {
                    return;
                }
                progress.dismiss();
                showSearchResults(results);
            });
        });
    }

    private void showSearchResults(List<OfflineMapSearchEngine.Result> results) {
        if (results.isEmpty()) {
            toast(MapStorage.activeMap(this).isFile()
                    ? "لا توجد نتائج مطابقة داخل الخريطة"
                    : "لا توجد نتائج؛ أضف خريطة الخليج للبحث في المدن والمعالم");
            return;
        }
        String[] labels = new String[results.size()];
        for (int index = 0; index < results.size(); index++) {
            OfflineMapSearchEngine.Result result = results.get(index);
            String distance = formatDistance(result.distanceMeters);
            labels[index] = result.name + "\n" + result.source + (distance.isEmpty() ? "" : " • " + distance);
        }
        new AlertDialog.Builder(this)
                .setTitle("نتائج البحث")
                .setItems(labels, (dialog, which) -> {
                    OfflineMapSearchEngine.Result result = results.get(which);
                    if (mapController == null) {
                        toast("أضف حزمة خريطة لعرض الموقع");
                        return;
                    }
                    mapController.showPoint(result.latitude, result.longitude);
                    toast(result.name);
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void selectMapMode(boolean desert) {
        modeDesert.setBackgroundColor(desert ? getColor(R.color.darbak_gold) : Color.TRANSPARENT);
        modeCity.setBackgroundColor(desert ? Color.TRANSPARENT : getColor(R.color.darbak_gold));
        modeDesert.setTextColor(getColor(desert ? R.color.darbak_green_deep : R.color.darbak_muted));
        modeCity.setTextColor(getColor(desert ? R.color.darbak_muted : R.color.darbak_green_deep));
        if (mapController != null) {
            mapController.setDesertMode(desert);
        }
    }

    private void centerOnCurrentLocation() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        if (location == null) {
            toast("بانتظار إشارة GPS");
            return;
        }
        if (mapController != null) {
            mapController.centerOn(location.getLatitude(), location.getLongitude());
        }
    }

    private void saveCurrentPlace() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        if (location == null) {
            toast("لا يمكن الحفظ قبل وصول إشارة GPS");
            return;
        }
        EditText input = new EditText(this);
        input.setHint("مثال: المخيم أو مدخل الشِعْب");
        input.setSingleLine(true);
        input.setPadding(28, 8, 28, 8);
        new AlertDialog.Builder(this)
                .setTitle("حفظ الموقع الحالي")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = "موقع محفوظ";
                    }
                    placeRepository.add(name, location.getLatitude(), location.getLongitude());
                    toast("تم حفظ الموقع داخل الجهاز");
                })
                .show();
    }

    private void toggleTrackRecording() {
        if (!trackRecorder.isRecording()) {
            trackRecorder.start();
            if (mapController != null) {
                mapController.beginTrack();
            }
            actionRecord.setText("إيقاف وحفظ");
            toast("بدأ تسجيل المسار");
            return;
        }

        List<GeoPoint> points = trackRecorder.stop();
        actionRecord.setText(R.string.record_track);
        if (points.size() < 2) {
            toast("المسار قصير ولم يُحفظ");
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("مسار " + new SimpleDateFormat("dd-MM HH:mm", Locale.US).format(new Date()));
        input.selectAll();
        new AlertDialog.Builder(this)
                .setTitle("اسم المسار")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    ioExecutor.execute(() -> {
                        try {
                            TrackStorage.save(this, name.isEmpty() ? "مسار محفوظ" : name, points);
                            runOnUiThread(() -> toast("تم حفظ المسار بصيغة GPX"));
                        } catch (Exception error) {
                            runOnUiThread(() -> toast("تعذر حفظ المسار"));
                        }
                    });
                })
                .show();
    }

    private void showSavedHub() {
        String[] items = {
                "المواقع المحفوظة (" + placeRepository.all().size() + ")",
                "المسارات السابقة (" + TrackStorage.list(this).length + ")"
        };
        new AlertDialog.Builder(this)
                .setTitle("محفوظاتي")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showPlaces(placeRepository.all(), "المواقع المحفوظة");
                    } else {
                        showTracks();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showPlaces(List<PlaceRepository.Place> places, String title) {
        if (places.isEmpty()) {
            toast("لا توجد مواقع مطابقة");
            return;
        }
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            PlaceRepository.Place place = places.get(i);
            labels[i] = place.name + "\n" + coordinates(place.latitude, place.longitude);
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    PlaceRepository.Place selected = places.get(which);
                    if (mapController != null) {
                        mapController.showPoint(selected.latitude, selected.longitude);
                    } else {
                        toast("أضف حزمة خريطة لعرض الموقع");
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showTracks() {
        File[] tracks = TrackStorage.list(this);
        if (tracks.length == 0) {
            toast("لا توجد مسارات محفوظة");
            return;
        }
        String[] labels = new String[tracks.length];
        for (int i = 0; i < tracks.length; i++) {
            labels[i] = tracks[i].getName().replace(".gpx", "").replace('_', ' ');
        }
        new AlertDialog.Builder(this)
                .setTitle("المسارات السابقة")
                .setItems(labels, (dialog, which) -> loadStoredTrack(tracks[which]))
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void loadStoredTrack(File track) {
        if (mapController == null) {
            toast("أضف حزمة خريطة لعرض المسار");
            return;
        }
        ProgressDialog progress = ProgressDialog.show(
                this, "المسارات", "جارٍ فتح المسار…", true, false);
        ioExecutor.execute(() -> {
            try {
                List<GeoPoint> points = TrackStorage.load(track);
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) {
                        return;
                    }
                    progress.dismiss();
                    mapController.showStoredTrack(points);
                    toast("تم عرض المسار على الخريطة");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        progress.dismiss();
                        toast(error.getMessage() == null ? "تعذر فتح المسار" : error.getMessage());
                    }
                });
            }
        });
    }

    private void showMore() {
        String[] items = {
                "إدارة الخرائط الأوفلاين",
                "عرض الإحداثيات",
                "التحقق من التحديث",
                "ترخيص هذا الجهاز",
                "حول دربك"
        };
        new AlertDialog.Builder(this)
                .setTitle("أدوات دربك")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showMapManager();
                            break;
                        case 1:
                            showCoordinates();
                            break;
                        case 2:
                            checkForUpdates();
                            break;
                        case 3:
                            showDeviceLicense();
                            break;
                        default:
                            showAbout();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showMapManager() {
        File file = MapStorage.activeMap(this);
        String status = file.isFile()
                ? "الخريطة الحالية: " + Math.max(1, file.length() / (1024 * 1024)) + " م.ب\nجاهزة للعمل بدون إنترنت"
                : "لا توجد حزمة خريطة مضافة";
        String[] actions = {
                "تنزيل خريطة الخليج الموصى بها (" + RecommendedMapDownloader.DISPLAY_SIZE + ")",
                "إضافة خريطة من USB أو الذاكرة"
        };
        new AlertDialog.Builder(this)
                .setTitle("الخرائط الأوفلاين")
                .setMessage(status)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        confirmRecommendedMapDownload();
                    } else {
                        chooseMapFile();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void showCoordinates() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        String message = location == null
                ? "بانتظار إشارة GPS"
                : coordinates(location.getLatitude(), location.getLongitude());
        new AlertDialog.Builder(this)
                .setTitle("الإحداثيات الحالية")
                .setMessage(message)
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private void showDeviceLicense() {
        String state = licenseManager.isLicensed() ? "مفعّل" : "غير مفعّل";
        new AlertDialog.Builder(this)
                .setTitle("ترخيص الجهاز")
                .setMessage("الحالة: " + state + "\nرمز الجهاز: " + licenseManager.deviceCode())
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("دربك " + BuildConfig.VERSION_NAME)
                .setMessage("خرائط متجهية أوفلاين للبر والمدن\n"
                        + "بدون صور أقمار صناعية\n\n"
                        + "بيانات الخريطة © مساهمو OpenStreetMap\n"
                        + "تقنية العرض Mapsforge\n\n"
                        + "جميع الحقوق محفوظة لأبوسلطان")
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private void checkForUpdates() {
        toast("جارٍ التحقق من التحديث…");
        UpdateManager.check(new UpdateManager.Callback() {
            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> toast(message));
            }

            @Override
            public void onUpdate(UpdateManager.UpdateInfo update) {
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("تحديث " + update.versionName)
                        .setMessage("نسخة جديدة من دربك جاهزة للتثبيت")
                        .setNegativeButton("لاحقًا", null)
                        .setPositiveButton("تنزيل وتثبيت", (dialog, which) -> {
                            toast("بدأ تنزيل التحديث");
                            UpdateManager.downloadAndInstall(MainActivity.this, update, this);
                        })
                        .show());
            }
        });
    }

    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            locationController.start();
        }
    }

    @Override
    public void onLocation(Location location) {
        runOnUiThread(() -> {
            int speed = location.hasSpeed() ? Math.max(0, Math.round(location.getSpeed() * 3.6f)) : 0;
            speedValue.setText(String.valueOf(speed));
            gpsStatus.setText("GPS متصل • أوفلاين");
            if (mapController != null) {
                mapController.updateLocation(location.getLatitude(), location.getLongitude(),
                        location.hasBearing() ? location.getBearing() : 0f);
            }
            if (trackRecorder.add(location) && mapController != null) {
                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());
            }
        });
    }

    @Override
    public void onProviderState(boolean enabled) {
        runOnUiThread(() -> gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationController.start();
            } else {
                gpsStatus.setText("صلاحية GPS مطلوبة");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACTIVATION) {
            if (licenseManager.isLicensed()) {
                initializeSafely();
            } else {
                finishAffinity();
            }
        } else if (requestCode == REQUEST_MAP_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some file providers grant access only during this import operation.
            }
            importMap(uri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (initialized && locationController != null && locationController.hasPermission()) {
            locationController.start();
        }
    }

    @Override
    protected void onPause() {
        if (locationController != null) {
            locationController.stop();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapDownloadCancelled = true;
        if (mapController != null) {
            mapController.destroy();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
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

    private void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private String coordinates(double latitude, double longitude) {
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude);
    }

    private String formatDistance(Float distanceMeters) {
        if (distanceMeters == null) {
            return "";
        }
        if (distanceMeters < 1000f) {
            return Math.round(distanceMeters) + " م";
        }
        return String.format(Locale.US, "%.1f كم", distanceMeters / 1000f);
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || isDestroyed();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
