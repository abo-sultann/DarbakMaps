package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import com.abosultan.darbakmaps.data.TrackStorage;
import com.abosultan.darbakmaps.data.BackgroundTrackStore;
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
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean resultRegistered;
    private final android.content.BroadcastReceiver trackResult=new android.content.BroadcastReceiver(){
        public void onReceive(android.content.Context c,Intent i){toast(i.getStringExtra("message"));syncBackgroundTrackUi();}
    };
    private final Runnable statsTick=new Runnable(){public void run(){if(initialized){syncBackgroundTrackUi();uiHandler.postDelayed(this,2000);}}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        immersive();
        applyDisplayPreferences();
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
        immersive();

        startupPhase = "ربط عناصر الواجهة";
        mapContainer = findViewById(R.id.map_container);
        noMapPanel = findViewById(R.id.no_map_panel);
        gpsStatus = findViewById(R.id.gps_status);
        speedValue = findViewById(R.id.speed_value);
        View speedPanel = findViewById(R.id.speed_panel);
        if (speedPanel != null) speedPanel.setVisibility(MapUiPreferences.showSpeed(this) ? View.VISIBLE : View.GONE);
        actionRecord = findViewById(R.id.action_record);
        modeDesert = findViewById(R.id.mode_desert);
        modeCity = findViewById(R.id.mode_city);

        startupPhase = "فتح البيانات المحلية";
        placeRepository = new PlaceRepository(this);
        locationController = new LocationController(this, this);
        startupPhase = "ربط الأزرار";
        bindActions();
        NavigationGuidance.restore(this);
        startupPhase = "تجهيز شاشة الخريطة";
        loadActiveMap();
        startupPhase = "تشغيل GPS";
        try {
            ensureLocationPermission();
        } catch (RuntimeException error) {
            gpsStatus.setText("GPS متاح بعد منح الصلاحية من إعدادات الجهاز");
        }
        BackgroundTrackService.ensureRunning(this);
        syncBackgroundTrackUi();
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
        findViewById(R.id.action_save).setOnClickListener(view -> {
            Location fix=locationController==null?null:locationController.getLastLocation();
            if(fix==null){toast("بانتظار إشارة GPS حديثة ودقيقة");return;}
            PointEditor.show(this,fix.getLatitude(),fix.getLongitude(),null);
        });
        actionRecord.setOnClickListener(view -> toggleTrackRecording());
        actionRecord.setOnLongClickListener(view -> {
            if (!MapUiPreferences.backgroundTrackEnabled(this)) return false;
            boolean paused = TrackSessionState.togglePaused(this);
            TrackSessionState.updateActionLabel(actionRecord, this);
        actionRecord.setEnabled(!BackgroundTrackService.finishing(this));
        if(BackgroundTrackService.finishing(this))actionRecord.setText("جارٍ الحفظ…");
            toast(paused ? "تم إيقاف تسجيل المسار مؤقتًا" : "تمت متابعة تسجيل المسار");
            return true;
        });
        View navStop = findViewById(R.id.nav_stop);
        if (navStop != null) navStop.setOnClickListener(view -> {
            BacktrackGuidance.stop(this);
            NavigationGuidance.stop(this);
        });
        findViewById(R.id.action_saved).setOnClickListener(view -> showSavedHub());
        findViewById(R.id.action_more).setOnClickListener(view -> DarbakPanels.showMore(this));

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
                MapRuntimeBridge.refreshSavedPlaces(this);
                NavigationGuidance.restore(this);
                BacktrackGuidance.restore(this);
                restoreActiveTrack();
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
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("الخرائط");
        progress.setMessage("جارٍ التجهيز…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        showImmersive(progress);
        ioExecutor.execute(() -> {
            try {
                MapStorage.importMap(this, uri);
                runOnUiThread(() -> {
                    if(isActivityUnavailable())return;
                    progress.dismiss();
                    toast("تمت إضافة الخريطة وأصبحت جاهزة أوفلاين");
                    loadActiveMap();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if(isActivityUnavailable())return;
                    progress.dismiss();
                    toast(error.getMessage() == null ? "تعذر إضافة الخريطة" : error.getMessage());
                });
            }
        });
    }

    private void confirmRecommendedMapDownload() {
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("خريطة دربك السعودية")
                .setMessage(RecommendedMapDownloader.DISPLAY_SIZE + " • Wi‑Fi")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تنزيل", (dialog, which) -> downloadRecommendedMap())
                .create());
    }

    private void downloadRecommendedMap() {
        mapDownloadCancelled = false;
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("خرائط دربك");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setMessage("بدء تنزيل خريطة دربك السعودية…");
        progress.setCancelable(true);
        progress.setCanceledOnTouchOutside(false);
        progress.setOnCancelListener(dialog -> mapDownloadCancelled = true);
        showImmersive(progress);

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
                    toast("تمت إضافة خريطة دربك السعودية وأصبحت جاهزة أوفلاين");
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
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("بحث");
        progress.setMessage("جارٍ البحث…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        showImmersive(progress);
        File activeMap = MapStorage.activeMap(this);
        Location current = locationController == null ? null : locationController.getLastLocation();
        Double latitude = current == null ? null : current.getLatitude();
        Double longitude = current == null ? null : current.getLongitude();

        ioExecutor.execute(() -> {
            try {
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
            }catch(RuntimeException error){runOnUiThread(()->{searchRunning=false;if(!isActivityUnavailable()){progress.dismiss();toast("تعذر البحث الآن؛ البيانات الأصلية محفوظة");}});}
        });
    }

    private void showSearchResults(List<OfflineMapSearchEngine.Result> results) {
        if (results.isEmpty() && searchEngine.isComplete() && !searchEngine.hasFailed()) {
            toast(MapStorage.activeMap(this).isFile()
                    ? "لا توجد نتائج مطابقة داخل الخريطة"
                    : "لا توجد نتائج؛ أضف خريطة دربك للبحث في المدن والمعالم");
            return;
        }
        String[] labels = new String[results.size()];
        for (int index = 0; index < results.size(); index++) {
            OfflineMapSearchEngine.Result result = results.get(index);
            String distance = formatDistance(result.distanceMeters);
            labels[index] = result.name + "\n" + result.source + (distance.isEmpty() ? "" : " • " + distance);
        }
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(searchEngine.hasFailed()?"تعذر قراءة جزء من الخريطة":searchEngine.isComplete()?"نتائج البحث":"نتائج أولية — لم يكتمل فحص الخريطة")
                .setPositiveButton(searchEngine.isComplete()?"تم":"متابعة البحث",(d,w)->{
                    if(!searchEngine.isComplete())performSearch(((EditText)findViewById(R.id.search_input)).getText().toString());
                })
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
                .create());
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
            mapController.resumeFollow();
            mapController.centerOn(location.getLatitude(), location.getLongitude());
        }
    }

    private void saveCurrentPlace() {
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("حفظ موقع بدقة")
                .setMessage("أخفِ الأدوات بلمسة على الخريطة، ثم اضغط مطولاً على النقطة المطلوبة.\n\nسيظهر نموذج الحفظ لاختيار الأيقونة والنوع والاسم المختصر والملاحظة، وتبقى العلامة ظاهرة على الخريطة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("فهمت", null)
                .create());
    }

    private void toggleTrackRecording() {
        if(BackgroundTrackService.finishing(this)){toast("جارٍ حفظ المسار…");return;}
        if(MapUiPreferences.backgroundTrackEnabled(this)&&TrackSessionState.isPaused(this)){
            TrackSessionState.togglePaused(this);syncBackgroundTrackUi();toast("تمت متابعة المسار");return;
        }
        boolean enabled = !MapUiPreferences.backgroundTrackEnabled(this);
        BackgroundTrackService.setEnabled(this, enabled);
        onBackgroundTrackSettingChanged(enabled);
        toast(enabled
                ? "بدأ رسم وتسجيل المسار — سيستمر عند إغلاق التطبيق"
                : "جارٍ حفظ المسار…");
    }

    void onBackgroundTrackSettingChanged(boolean enabled) {
        if (enabled) {
            if (mapController != null) {
                mapController.beginTrack();
                restoreActiveTrack();
            }
        } else if (mapController != null) {
            mapController.beginTrack();
        }
        syncBackgroundTrackUi();
    }

    private void syncBackgroundTrackUi() {
        if (actionRecord == null) return;
        TrackSessionState.updateActionLabel(actionRecord, this);
        actionRecord.setEnabled(!BackgroundTrackService.finishing(this));
        if(BackgroundTrackService.finishing(this))actionRecord.setText("جارٍ الحفظ…");
        View statsPanel = findViewById(R.id.track_stats_panel);
        TextView statsText = findViewById(R.id.track_stats_text);
        boolean showStats = MapUiPreferences.backgroundTrackEnabled(this) && MapUiPreferences.showTrackStats(this);
        if (statsPanel != null) statsPanel.setVisibility(showStats ? View.VISIBLE : View.GONE);
        if (statsText != null && showStats) {
            String state=BackgroundTrackService.status(this);
            statsText.setText(TrackSessionState.summary(this) + (TrackSessionState.isPaused(this) ? " • متوقف مؤقتًا" : "")
                    +(state.startsWith("تعذر")?" • "+state:""));
        }
    }

    private void restoreActiveTrack() {
        if (!MapUiPreferences.backgroundTrackEnabled(this) || mapController == null) return;
        mapController.beginTrack();
        ioExecutor.execute(() -> {
            List<GeoPoint> points = BackgroundTrackStore.loadActive(this);
            runOnUiThread(() -> {
                if (!isActivityUnavailable() && mapController != null && points.size() >= 2) {
                    mapController.restoreRecordedTrack(points);
                }
            });
        });
    }

    private void showSavedHub() {
        String[] items = {
                "المواقع المحفوظة (" + placeRepository.all().size() + ")",
                "الأقرب إلى موقعي",
                "المسارات السابقة (" + TrackStorage.list(this).length + ")"
        };
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("محفوظاتي")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showPlaces(placeRepository.all(), "المواقع المحفوظة");
                    } else if (which == 1) {
                        showNearbyPlaces();
                    } else {
                        showTracks();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .create());
    }

    private void showPlaces(List<PlaceRepository.Place> places, String title) {
        if (places.isEmpty()) {
            toast("لا توجد مواقع مطابقة");
            return;
        }
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            PlaceRepository.Place place = places.get(i);
            labels[i] = PlaceRepository.iconGlyph(place.iconKey) + "  " + place.name
                    + "\n" + place.category + " • " + coordinates(place.latitude, place.longitude);
        }
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (dialog, which) -> showPlaceActions(places.get(which)))
                .setNegativeButton("إغلاق", null)
                .create());
    }

    private void showPlaceActions(PlaceRepository.Place selected) {
        int routingMode = MapUiPreferences.routingMode(this);
        String routingLabel = MapRuntimeBridge.routingLabel(routingMode);
        String[] actions = {"عرض على الخريطة", "توجيه — " + routingLabel, "تعديل الاسم والفئة والملاحظة", "حذف الموقع"};
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(PlaceRepository.iconGlyph(selected.iconKey) + "  " + selected.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (mapController != null) mapController.showPoint(selected.latitude, selected.longitude);
                        else toast("الخريطة غير جاهزة");
                    } else if (which == 1) {
                        if (mapController == null) {
                            toast("الخريطة غير جاهزة");
                            return;
                        }
                        BacktrackGuidance.stop(this);
                        NavigationGuidance.start(this, selected.name, selected.latitude, selected.longitude);
                        if (routingMode == MapUiPreferences.ROUTING_ROADS) {
                            toast("وضع الطرق تجريبي في هذه النسخة؛ سيبقى خط الهدف ظاهرًا حتى اكتمال محرك الطرق");
                        } else {
                            toast("بدأ التوجيه المباشر إلى " + selected.name);
                        }
                    } else if(which==2){
                        PointEditor.show(this,selected.latitude,selected.longitude,selected);
                    } else {
                        AlertDialog confirm = new AlertDialog.Builder(this)
                                .setTitle("حذف الموقع؟")
                                .setMessage(selected.name)
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("حذف", (d, w) -> {
                                    try{placeRepository.delete(selected.id);MapRuntimeBridge.refreshSavedPlaces(this);toast("تم حذف الموقع");}
                                    catch(RuntimeException error){toast(error.getMessage());}
                                }).create();
                        showImmersive(confirm);
                    }
                })
                .setNegativeButton("رجوع", null)
                .create());
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
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("المسارات السابقة")
                .setItems(labels, (dialog, which) -> showTrackActions(tracks[which]))
                .setNegativeButton("إغلاق", null)
                .create());
    }

    private void loadStoredTrack(File track) {
        if (mapController == null) {
            toast("أضف حزمة خريطة لعرض المسار");
            return;
        }
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("المسارات");
        progress.setMessage("جارٍ الفتح…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        showImmersive(progress);
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

    private void showNearbyPlaces() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        if (current == null) {
            toast("بانتظار إشارة GPS");
            return;
        }
        List<PlaceRepository.Place> places = new java.util.ArrayList<>(placeRepository.all());
        java.util.Collections.sort(places, (a, b) -> Float.compare(distanceTo(current, a), distanceTo(current, b)));
        if (places.size() > 30) places = new java.util.ArrayList<>(places.subList(0, 30));
        showPlaces(places, "أقرب المواقع إليك");
    }

    private float distanceTo(Location current, PlaceRepository.Place place) {
        float[] out = new float[1];
        Location.distanceBetween(current.getLatitude(), current.getLongitude(), place.latitude, place.longitude, out);
        return out[0];
    }

    private void showTrackActions(File track) {
        String[] actions = {"عرض المسار على الخريطة", "الرجوع على نفس الطريق", "إخفاء المسار المعروض"};
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(track.getName().replace(".gpx", "").replace('_', ' '))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) loadStoredTrack(track);
                    else if (which == 1) startBacktrack(track);
                    else {
                        BacktrackGuidance.stop(this);
                        MapRuntimeBridge.clearStoredTrack();
                        toast("تم إخفاء المسار");
                    }
                })
                .setNegativeButton("إلغاء", null)
                .create());
    }

    private void startBacktrack(File track) {
        if (mapController == null) {
            toast("أضف حزمة خريطة لعرض المسار");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<GeoPoint> points = TrackStorage.load(track);
                if (points.size() < 2) throw new IllegalStateException("المسار قصير");
                GeoPoint start = points.get(0);
                runOnUiThread(() -> {
                    if(isActivityUnavailable())return;
                    BacktrackGuidance.start(this, track, points);
                    toast("اتبع الخط الظاهر للرجوع على نفس الطريق");
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("تعذر بدء الرجوع على المسار"));
            }
        });
    }

    private void showMore() {
        String[] items = {
                "الإعدادات",
                "الخرائط",
                "الإحداثيات",
                "التحديث",
                "الترخيص",
                "حول دربك"
        };
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("المزيد")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showStartupSettings();
                            break;
                        case 1:
                            showMapManager();
                            break;
                        case 2:
                            showCoordinates();
                            break;
                        case 3:
                            checkForUpdates();
                            break;
                        case 4:
                            showDeviceLicense();
                            break;
                        default:
                            showAbout();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .create());
    }

    private void showStartupSettings() {
        DarbakPanels.showSettings(this);
    }

    private void showMapManager() {
        File file = MapStorage.activeMap(this);
        String status = file.isFile()
                ? "الخريطة الحالية: " + Math.max(1, file.length() / (1024 * 1024)) + " م.ب\nجاهزة للعمل بدون إنترنت"
                : "لا توجد حزمة خريطة مضافة";
        String[] actions = {
                "تنزيل خريطة دربك السعودية الموصى بها (" + RecommendedMapDownloader.DISPLAY_SIZE + ")",
                "إضافة خريطة من USB أو الذاكرة"
        };
        showImmersive(new AlertDialog.Builder(this)
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
                .create());
    }

    private void showCoordinates() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        String message = location == null
                ? "بانتظار إشارة GPS"
                : coordinates(location.getLatitude(), location.getLongitude());
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("الإحداثيات الحالية")
                .setMessage(message)
                .setPositiveButton("حسنًا", null)
                .create());
    }

    private void showDeviceLicense() {
        String state = licenseManager.isLicensed() ? "مفعّل" : "غير مفعّل";
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("ترخيص الجهاز")
                .setMessage("الحالة: " + state + "\nرمز الجهاز: " + licenseManager.deviceCode())
                .setPositiveButton("حسنًا", null)
                .create());
    }

    private void showAbout() {
        DarbakPanels.showAbout(this);
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
                runOnUiThread(() -> showImmersive(new AlertDialog.Builder(MainActivity.this)
                        .setTitle("تحديث " + update.versionName)
                        .setMessage("نسخة جديدة من دربك جاهزة للتثبيت")
                        .setNegativeButton("لاحقًا", null)
                        .setPositiveButton("تنزيل وتثبيت", (dialog, which) -> {
                            toast("بدأ تنزيل التحديث");
                            UpdateManager.downloadAndInstall(MainActivity.this, update, this);
                        })
                        .create()));
            }
        });
    }

    private void applyDisplayPreferences() {
        if (MapUiPreferences.keepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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
            if (MapUiPreferences.backgroundTrackEnabled(this) && !TrackSessionState.isPaused(this) && mapController != null) {
                mapController.addTrackPoint(location.getLatitude(), location.getLongitude());
            }
            if(BacktrackGuidance.isActive(this))BacktrackGuidance.update(this,location);
            else NavigationGuidance.update(this, location);
            syncBackgroundTrackUi();
        });
    }

    @Override
    public void onProviderState(boolean enabled) {
        runOnUiThread(() -> {
            if(gpsStatus==null)return;
            gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح • بانتظار إشارة حديثة");
            if(!enabled){if(speedValue!=null)speedValue.setText("—");NavigationGuidance.noFix(this);if(mapController!=null)mapController.markLocationStale();}
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationController.start();
                BackgroundTrackService.ensureRunning(this);
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
        immersive();
        if (initialized && locationController != null && locationController.hasPermission()) {
            locationController.start();
        }
        if (initialized) {
            if(!resultRegistered){
                android.content.IntentFilter filter=new android.content.IntentFilter(BackgroundTrackService.RESULT);
                if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(trackResult,filter,android.content.Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(trackResult,filter);
                resultRegistered=true;
            }
            uiHandler.removeCallbacks(statsTick);uiHandler.post(statsTick);
            BackgroundTrackService.ensureRunning(this);
            syncBackgroundTrackUi();
            restoreActiveTrack();
        }
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(statsTick);
        if(resultRegistered){unregisterReceiver(trackResult);resultRegistered=false;}
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(immersiveFlags());
        decor.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
                    || (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                decor.postDelayed(() -> decor.setSystemUiVisibility(immersiveFlags()), 250L);
            }
        });
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

    private <T extends Dialog> T showImmersive(T dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.getDecorView().setSystemUiVisibility(immersiveFlags());
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        return dialog;
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

