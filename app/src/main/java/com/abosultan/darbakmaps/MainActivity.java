package com.abosultan.darbakmaps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
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

import androidx.core.content.ContextCompat;

import com.abosultan.darbakmaps.activation.ActivationActivity;
import com.abosultan.darbakmaps.activation.LicenseManager;
import com.abosultan.darbakmaps.data.GeoPoint;
import com.abosultan.darbakmaps.data.LegacyMigration;
import com.abosultan.darbakmaps.data.PlaceRepository;
import com.abosultan.darbakmaps.data.TrackStorage;
import com.abosultan.darbakmaps.data.BackgroundTrackStore;
import com.abosultan.darbakmaps.data.TrackRenderGate;
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
    private static final int REQUEST_MIGRATION = 703;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final OfflineMapSearchEngine searchEngine = new OfflineMapSearchEngine();
    private final TrackRenderGate trackRenderGate = new TrackRenderGate();
    private boolean trackReceiverRegistered;
    private final BroadcastReceiver trackCommitReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !BackgroundTrackService.ACTION_TRACK_COMMITTED.equals(intent.getAction())) return;
            long generation = intent.getLongExtra(BackgroundTrackService.EXTRA_GENERATION, 0L);
            requestCommittedTrackRender(generation);
        }
    };

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
    private OfflineMapSearchEngine.SearchRequest lastSearchRequest;
    private String startupPhase = "بدء التشغيل";

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
        if (initialized) return;
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
        startupPhase = "استعادة اتساق البيانات";
        try { LegacyMigration.recoverInterrupted(this); }
        catch (java.io.IOException recoveryError) { throw new IllegalStateException(recoveryError.getMessage(), recoveryError); }
        startupPhase = "فتح البيانات المحلية";
        placeRepository = new PlaceRepository(this);
        locationController = new LocationController(this, this);
        startupPhase = "ربط الأزرار";
        bindActions();
        NavigationGuidance.restore(this);
        startupPhase = "تجهيز شاشة الخريطة";
        loadActiveMap();
        startupPhase = "تشغيل GPS";
        try { ensureLocationPermission(); }
        catch (RuntimeException error) { gpsStatus.setText("GPS متاح بعد منح الصلاحية من إعدادات الجهاز"); }
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
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView message = new TextView(this);
        String detail = error.getClass().getSimpleName();
        if (error.getMessage() != null && !error.getMessage().trim().isEmpty()) detail += ": " + error.getMessage();
        message.setText("تم منع انهيار التطبيق.\nالمرحلة: " + startupPhase + "\nالسبب: " + detail);
        message.setTextColor(Color.rgb(92, 110, 103));
        message.setTextSize(17f);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, 20, 0, 24);
        panel.addView(message, mp);
        Button retry = new Button(this);
        retry.setText("إعادة المحاولة");
        retry.setOnClickListener(v -> initializeSafely());
        panel.addView(retry, new LinearLayout.LayoutParams(280, 64));
        setContentView(panel);
    }

    private void saveStartupFailure(Throwable error) {
        File report = new File(getFilesDir(), "last_startup_failure.txt");
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(report, false))) { error.printStackTrace(writer); }
        catch (Exception ignored) {}
    }

    private void bindActions() {
        findViewById(R.id.zoom_in).setOnClickListener(view -> { if (mapController != null) mapController.zoomIn(); });
        findViewById(R.id.zoom_out).setOnClickListener(view -> { if (mapController != null) mapController.zoomOut(); });
        findViewById(R.id.center_location).setOnClickListener(view -> centerOnCurrentLocation());
        findViewById(R.id.import_map).setOnClickListener(view -> chooseMapFile());
        findViewById(R.id.download_map).setOnClickListener(view -> confirmRecommendedMapDownload());
        findViewById(R.id.action_map).setOnClickListener(view -> centerOnCurrentLocation());
        findViewById(R.id.action_save).setOnClickListener(view -> QuickPointDialog.show(this, placeRepository,
                locationController == null ? null : locationController.getLastLocation()));
        actionRecord.setOnClickListener(view -> toggleTrackPause());
        actionRecord.setOnLongClickListener(view -> { saveAutomaticTrackSnapshot(); return true; });
        View navStop = findViewById(R.id.nav_stop);
        if (navStop != null) navStop.setOnClickListener(view -> { BacktrackGuidance.stop(this); NavigationGuidance.stop(this); });
        findViewById(R.id.action_saved).setOnClickListener(view -> showSavedPlacesPanel());
        findViewById(R.id.action_saved).setOnLongClickListener(view -> { showSavedHub(); return true; });
        findViewById(R.id.action_more).setOnClickListener(view -> DarbakPanels.showMore(this));
        modeDesert.setOnClickListener(view -> selectMapMode(true));
        modeCity.setOnClickListener(view -> selectMapMode(false));
        EditText search = findViewById(R.id.search_input);
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(search); performSearch(search.getText().toString()); return true;
            }
            return false;
        });
    }

    private void loadActiveMap() {
        try { RecommendedMapDownloader.recoverInterruptedInstall(this); }
        catch (Exception recoveryError) { toast(recoveryError.getMessage() == null ? "تعذر استعادة الخريطة السابقة" : recoveryError.getMessage()); }
        searchEngine.clear();
        File mapFile = MapStorage.activeMap(this);
        if (!mapFile.isFile()) {
            if (mapController != null) { mapController.destroy(); mapController = null; }
            mapContainer.removeAllViews();
            noMapPanel.setVisibility(View.VISIBLE);
            return;
        }
        noMapPanel.setVisibility(View.GONE);
        try {
            if (mapController != null) mapController.destroy();
            mapController = new OfflineMapController(this, mapFile);
            mapController.setSavedPlaceTapListener(new OfflineMapController.SavedPlaceTapListener() {
                @Override public void onSavedPlaceTap(PlaceRepository.Place place) {
                    startDirectNavigation(place.name, place.latitude, place.longitude);
                }
                @Override public void onSavedPlaceClusterTap(List<PlaceRepository.Place> places) { showSavedPlaceCluster(places); }
            });
            mapContainer.removeAllViews();
            mapContainer.addView(mapController.view(), new FrameLayout.LayoutParams(-1, -1));
            mapController.showSavedPlaces(placeRepository.all(), MapUiPreferences.showSavedNames(this));
            restoreActiveTrack();
            NavigationGuidance.restore(this);
        } catch (Throwable error) {
            if (mapController != null) { mapController.destroy(); mapController = null; }
            mapContainer.removeAllViews();
            noMapPanel.setVisibility(View.VISIBLE);
            toast("تعذر فتح ملف الخريطة");
        }
    }

    private void performSearch(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) { toast("اكتب اسم مدينة أو مكان للبحث"); return; }
        Location current = locationController == null ? null : locationController.getLastLocation();
        OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(query,
                OfflineMapSearchEngine.CATEGORY_ALL,
                current == null ? null : current.getLatitude(), current == null ? null : current.getLongitude(),
                0f, false, 40, 0);
        executeSearch(request, "بحث");
    }

    private void executeSearch(OfflineMapSearchEngine.SearchRequest request, String title) {
        if (searchRunning) { toast("البحث السابق ما زال جاريًا"); return; }
        searchRunning = true;
        lastSearchRequest = request;
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle(title);
        progress.setMessage(request.offset > 0 ? "جارٍ تحميل الصفحة التالية…" : "جارٍ البحث…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        showImmersive(progress);
        File activeMap = MapStorage.activeMap(this);
        ioExecutor.execute(() -> {
            List<OfflineMapSearchEngine.Result> results = searchEngine.search(request,
                    activeMap.isFile() ? activeMap : null, placeRepository.all());
            boolean complete = searchEngine.isComplete();
            boolean failed = searchEngine.hasFailed();
            boolean queryMore = searchEngine.hasMoreResults();
            runOnUiThread(() -> {
                searchRunning = false;
                if (isActivityUnavailable()) return;
                progress.dismiss();
                showSearchResults(request, results, complete, failed, queryMore);
            });
        });
    }

    private void showSearchResults(OfflineMapSearchEngine.SearchRequest request,
                                   List<OfflineMapSearchEngine.Result> results,
                                   boolean complete, boolean failed, boolean queryMore) {
        if (mapController != null) mapController.showSearchResults(results);
        if (results.isEmpty()) {
            if (!complete) toast("لا توجد نتائج في الجزء المفهرس حتى الآن؛ الفهرسة ما زالت جارية");
            else if (failed) toast("اكتمل الفهرس مع تعذر قراءة بعض أجزاء الخريطة");
            else toast(MapStorage.activeMap(this).isFile() ? "لا توجد نتائج مطابقة" : "أضف خريطة دربك للبحث في المعالم");
            return;
        }
        String[] labels = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            OfflineMapSearchEngine.Result result = results.get(i);
            String distance = formatDistance(result.distanceMeters);
            labels[i] = result.name + "\n" + result.source + (distance.isEmpty() ? "" : " • " + distance);
        }
        String state = complete ? "نتائج البحث" : "نتائج من الجزء المفهرس — الفهرسة مستمرة";
        if (failed) state += " • تعذر جزء من الخريطة";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(state + (request.offset > 0 ? " • صفحة " + (request.offset / Math.max(1, request.limit) + 1) : ""))
                .setItems(labels, (dialog, which) -> showSearchResultActions(results.get(which)))
                .setNegativeButton("إغلاق", null);
        if (queryMore) builder.setPositiveButton("المزيد", (dialog, which) -> {
            OfflineMapSearchEngine.SearchRequest next = searchEngine.nextPageRequest(request);
            if (next != null && next != request) executeSearch(next, "بحث");
        });
        showImmersive(builder.create());
    }

    private void showSearchResultActions(OfflineMapSearchEngine.Result result) {
        String distance = formatDistance(result.distanceMeters);
        String[] actions = {"عرض على الخريطة", "حفظ بالأيقونة", "توجيه مباشر", "البحث حول هذا المعلم"};
        String message = result.source + (distance.isEmpty() ? "" : " • " + distance)
                + "\n" + coordinates(result.latitude, result.longitude);
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(result.name)
                .setMessage(message)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (mapController != null) mapController.showPoint(result.latitude, result.longitude);
                    } else if (which == 1) {
                        PointEditor.show(this, result.latitude, result.longitude, null);
                    } else if (which == 2) {
                        startDirectNavigation(result.name, result.latitude, result.longitude);
                    } else {
                        showNearbySearchOptions(result.latitude, result.longitude, result.name);
                    }
                })
                .setNegativeButton("إغلاق", null)
                .create());
    }

    void showNearbySearchFromCurrentLocation() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        if (current == null) {
            toast("بانتظار GPS للبحث حول موقعي");
            return;
        }
        showNearbySearchOptions(current.getLatitude(), current.getLongitude(), "موقعي");
    }

    private void showNearbySearchOptions(double latitude, double longitude, String centerLabel) {
        String[] categories = { OfflineMapSearchEngine.CATEGORY_ALL, OfflineMapSearchEngine.CATEGORY_WADIS,
                OfflineMapSearchEngine.CATEGORY_MOUNTAINS, OfflineMapSearchEngine.CATEGORY_LANDMARKS,
                OfflineMapSearchEngine.CATEGORY_VILLAGES, OfflineMapSearchEngine.CATEGORY_WATER,
                OfflineMapSearchEngine.CATEGORY_SERVICES };
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("بحث حول " + centerLabel)
                .setItems(categories, (dialog, categoryIndex) -> showNearbyRadiusPicker(latitude, longitude, centerLabel, categories[categoryIndex]))
                .setNegativeButton("إلغاء", null)
                .create());
    }

    private void showNearbyRadiusPicker(double latitude, double longitude, String centerLabel, String category) {
        int[] radii = {5, 10, 25, 50, 100};
        String[] labels = {"5 كم", "10 كم", "25 كم", "50 كم", "100 كم"};
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(category + " • حول " + centerLabel)
                .setItems(labels, (dialog, which) -> runNearbySearch(latitude, longitude, centerLabel, category, radii[which]))
                .setNegativeButton("إلغاء", null)
                .create());
    }

    private void runNearbySearch(double latitude, double longitude, String centerLabel, String category, int radiusKm) {
        OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                "", category, latitude, longitude, radiusKm * 1000f, true, 120, 0);
        executeSearch(request, "بحث قريب • " + centerLabel + " • " + radiusKm + " كم");
    }

    void showNearbySearchAroundPoint(double latitude, double longitude) {
        showNearbySearchOptions(latitude, longitude, "النقطة المحددة");
    }

    private void showSavedPlacesPanel() {
        SavedPlacesDialog.show(this, placeRepository, locationController == null ? null : locationController.getLastLocation(),
                place -> startDirectNavigation(place.name, place.latitude, place.longitude));
    }

    private void showSavedPlaceCluster(List<PlaceRepository.Place> places) {
        if (places == null || places.isEmpty()) return;
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).name;
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("عدة مواقع في نفس المكان")
                .setItems(labels, (dialog, which) -> {
                    PlaceRepository.Place selected = places.get(which);
                    startDirectNavigation(selected.name, selected.latitude, selected.longitude);
                })
                .setNegativeButton("إلغاء", null)
                .create());
    }

    private void startDirectNavigation(String name, double latitude, double longitude) {
        NavigationGuidance.start(this, name, latitude, longitude);
        BacktrackGuidance.stop(this);
        if (mapController != null) mapController.setNavigationTarget(latitude, longitude, MapUiPreferences.ROUTING_DIRECT);
        toast("توجيه مباشر في البر — الخط لا يعني وجود طريق صالح");
    }

    private void restoreActiveTrack() {
        if (!MapUiPreferences.backgroundTrackEnabled(this) || mapController == null) return;
        requestCommittedTrackRender(BackgroundTrackStore.generation(this));
    }

    private void requestCommittedTrackRender(long minimumGeneration) {
        if (mapController == null) return;
        final long token = trackRenderGate.request(minimumGeneration);
        ioExecutor.execute(() -> {
            BackgroundTrackStore.Snapshot snapshot = BackgroundTrackStore.loadActiveSnapshot(this);
            runOnUiThread(() -> {
                if (isActivityUnavailable() || mapController == null) return;
                if (!trackRenderGate.mayApply(token, snapshot.generation)) return;
                mapController.showActiveTrack(snapshot.points);
            });
        });
    }

    private void selectMapMode(boolean desert) {
        modeDesert.setBackgroundColor(desert ? getColor(R.color.darbak_gold) : Color.TRANSPARENT);
        modeCity.setBackgroundColor(desert ? Color.TRANSPARENT : getColor(R.color.darbak_gold));
        modeDesert.setTextColor(getColor(desert ? R.color.darbak_green_deep : R.color.darbak_muted));
        modeCity.setTextColor(getColor(desert ? R.color.darbak_muted : R.color.darbak_green_deep));
        if (mapController != null) mapController.setDesertMode(desert);
    }

    private void centerOnCurrentLocation() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        if (location == null) { toast("بانتظار إشارة GPS"); return; }
        if (mapController != null) { mapController.resumeFollow(); mapController.centerOn(location.getLatitude(), location.getLongitude()); }
    }

    private void saveCurrentPlace() {
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("حفظ موقع بدقة")
                .setMessage("أخفِ الأدوات بلمسة على الخريطة، ثم اضغط مطولاً على النقطة المطلوبة.\n\nسيظهر نموذج الحفظ لاختيار الأيقونة والنوع والاسم المختصر والملاحظة، وتبقى العلامة ظاهرة على الخريطة.")
                .setNegativeButton("إلغاء", null).setPositiveButton("فهمت", null).create());
    }

    private void toggleTrackPause() {
        if (!MapUiPreferences.backgroundTrackEnabled(this)) {
            MapUiPreferences.setBackgroundTrackEnabled(this, true);
            TrackSessionState.beginIfNeeded(this);
            BackgroundTrackService.ensureRunning(this);
            onBackgroundTrackSettingChanged(true);
        }
        boolean paused = TrackSessionState.togglePaused(this);
        syncBackgroundTrackUi();
        toast(paused ? "توقف التسجيل التلقائي مؤقتًا — السجل محفوظ" : "استؤنف تسجيل آخر 1000 كم تلقائيًا");
    }

    private void saveAutomaticTrackSnapshot() {
        toast("جارٍ حفظ نسخة من المسار التلقائي…");
        ioExecutor.execute(() -> {
            try {
                File saved = BackgroundTrackStore.snapshotActive(this);
                runOnUiThread(() -> { if (!isActivityUnavailable()) toast("تم حفظ نسخة: " + saved.getName()); });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!isActivityUnavailable()) toast(error.getMessage() == null ? "تعذر حفظ نسخة المسار" : error.getMessage()); });
            }
        });
    }

    void onBackgroundTrackSettingChanged(boolean enabled) {
        if (enabled && mapController != null) restoreActiveTrack();
        syncBackgroundTrackUi();
    }

    private void syncBackgroundTrackUi() {
        if (actionRecord == null) return;
        TrackSessionState.updateActionLabel(actionRecord, this);
        View statsPanel = findViewById(R.id.track_stats_panel);
        TextView statsText = findViewById(R.id.track_stats_text);
        boolean showStats = MapUiPreferences.backgroundTrackEnabled(this) && MapUiPreferences.showTrackStats(this);
        if (statsPanel != null) statsPanel.setVisibility(showStats ? View.VISIBLE : View.GONE);
        if (statsText != null && showStats) statsText.setText(TrackSessionState.summary(this) + (TrackSessionState.isPaused(this) ? " • متوقف مؤقتًا" : ""));
    }

    private void showSavedHub() {
        String[] items = { "المواقع المحفوظة (" + placeRepository.all().size() + ")", "الأقرب إلى موقعي",
                "حفظ نسخة من آخر 1000 كم", "المسارات السابقة (" + TrackStorage.list(this).length + ")" };
        showImmersive(new AlertDialog.Builder(this).setTitle("المحفوظات والمسارات")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showPlaces(placeRepository.all(), "المواقع المحفوظة");
                    else if (which == 1) showNearbyPlaces();
                    else if (which == 2) saveAutomaticTrackSnapshot();
                    else showTracks();
                }).setNegativeButton("إغلاق", null).create());
    }

    private void showPlaces(List<PlaceRepository.Place> places, String title) {
        if (places.isEmpty()) { toast("لا توجد مواقع محفوظة بعد"); return; }
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).name + "\n" + coordinates(places.get(i).latitude, places.get(i).longitude);
        showImmersive(new AlertDialog.Builder(this).setTitle(title)
                .setItems(labels, (dialog, which) -> { PlaceRepository.Place place = places.get(which); startDirectNavigation(place.name, place.latitude, place.longitude); })
                .setNegativeButton("إغلاق", null).create());
    }

    private void showNearbyPlaces() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        if (current == null) { toast("بانتظار GPS لترتيب المواقع حسب القرب"); return; }
        List<PlaceRepository.Place> places = placeRepository.nearest(current.getLatitude(), current.getLongitude(), 40);
        showPlaces(places, "الأقرب إلى موقعي");
    }

    private void showTracks() {
        File[] tracks = TrackStorage.list(this);
        if (tracks.length == 0) { toast("لا توجد مسارات محفوظة بعد"); return; }
        String[] names = new String[tracks.length];
        for (int i = 0; i < tracks.length; i++) names[i] = tracks[i].getName();
        showImmersive(new AlertDialog.Builder(this).setTitle("المسارات السابقة")
                .setItems(names, (dialog, which) -> openTrack(tracks[which])).setNegativeButton("إغلاق", null).create());
    }

    private void openTrack(File file) {
        ioExecutor.execute(() -> {
            try {
                List<GeoPoint> points = TrackStorage.readDisplay(file, 7000);
                runOnUiThread(() -> {
                    if (points.size() < 2) { toast("المسار فارغ أو غير صالح"); return; }
                    if (mapController == null) { toast("أضف الخريطة أولًا لعرض المسار"); return; }
                    mapController.showStoredTrack(points);
                    promptBacktrack(file);
                });
            } catch (Exception error) { runOnUiThread(() -> toast("تعذر قراءة المسار")); }
        });
    }

    private void promptBacktrack(File file) {
        showImmersive(new AlertDialog.Builder(this).setTitle("مسار محفوظ")
                .setMessage("يمكن عرض المسار فقط، أو تشغيل وضع العودة لاتباع نفس الأثر بالعكس دون حساب طريق جديد.")
                .setNegativeButton("عرض فقط", null)
                .setPositiveButton("ابدأ العودة", (dialog, which) -> startBacktrack(file)).create());
    }

    private void startBacktrack(File file) {
        ioExecutor.execute(() -> {
            try {
                List<GeoPoint> points = TrackStorage.readForNavigation(file);
                if (points.size() < 2) { runOnUiThread(() -> toast("المسار لا يحتوي نقاطًا كافية")); return; }
                Location current = locationController == null ? null : locationController.getLastLocation();
                if (current != null) {
                    com.abosultan.darbakmaps.data.TrackNavigator probe = new com.abosultan.darbakmaps.data.TrackNavigator(points);
                    if (probe.update(current.getLatitude(), current.getLongitude()) == null) {
                        runOnUiThread(() -> toast("لا يوجد مقطع مسار متصل صالح للعودة من موقعك الحالي")); return;
                    }
                }
                BacktrackGuidance.start(this, file);
                NavigationGuidance.stop(this);
                runOnUiThread(() -> {
                    if (mapController != null) mapController.clearNavigationTarget();
                    toast("اتبع الخط الظاهر للرجوع على نفس الطريق");
                });
            } catch (Exception error) { runOnUiThread(() -> toast("تعذر بدء الرجوع على المسار")); }
        });
    }

    private void showMore() { DarbakPanels.showMore(this); }
    private void showStartupSettings() { DarbakPanels.showSettings(this); }
    private void showAbout() { DarbakPanels.showAbout(this); }

    private void showMapManager() {
        File file = MapStorage.activeMap(this);
        String status = file.isFile() ? "الخريطة الحالية: " + Math.max(1, file.length() / (1024 * 1024)) + " م.ب\nجاهزة للعمل بدون إنترنت" : "لا توجد حزمة خريطة مضافة";
        String[] actions = { "تنزيل خريطة دربك السعودية (" + RecommendedMapDownloader.DISPLAY_SIZE + ")", "إضافة خريطة من USB أو الذاكرة" };
        showImmersive(new AlertDialog.Builder(this).setTitle("الخرائط الأوفلاين").setMessage(status)
                .setItems(actions, (dialog, which) -> { if (which == 0) confirmRecommendedMapDownload(); else chooseMapFile(); })
                .setNegativeButton("إغلاق", null).create());
    }

    private void showCoordinates() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        String message = location == null ? "بانتظار إشارة GPS" : coordinates(location.getLatitude(), location.getLongitude());
        showImmersive(new AlertDialog.Builder(this).setTitle("الإحداثيات الحالية").setMessage(message).setPositiveButton("حسنًا", null).create());
    }

    private void showDeviceLicense() {
        String state = licenseManager.isLicensed() ? "مفعّل" : "غير مفعّل";
        showImmersive(new AlertDialog.Builder(this).setTitle("ترخيص الجهاز")
                .setMessage("الحالة: " + state + "\nرمز الجهاز: " + licenseManager.deviceCode()).setPositiveButton("حسنًا", null).create());
    }

    private void checkForUpdates() {
        toast("جارٍ التحقق من التحديث…");
        UpdateManager.check(new UpdateManager.Callback() {
            @Override public void onStatus(String message) { runOnUiThread(() -> toast(message)); }
            @Override public void onUpdate(UpdateManager.UpdateInfo update) {
                runOnUiThread(() -> showImmersive(new AlertDialog.Builder(MainActivity.this)
                        .setTitle("تحديث " + update.versionName).setMessage("نسخة جديدة من دربك جاهزة للتثبيت")
                        .setNegativeButton("لاحقًا", null).setPositiveButton("تنزيل وتثبيت", (dialog, which) -> {
                            toast("بدأ تنزيل التحديث"); UpdateManager.downloadAndInstall(MainActivity.this, update, this);
                        }).create()));
            }
        });
    }

    private void applyDisplayPreferences() {
        if (MapUiPreferences.keepScreenOn(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            MapUiPreferences.ensureAutomaticTracking(this); locationController.start(); BackgroundTrackService.ensureRunning(this);
        }
    }

    @Override public void onLocation(Location location) {
        runOnUiThread(() -> {
            if (location.hasSpeed()) speedValue.setText(String.valueOf(Math.max(0, Math.round(location.getSpeed() * 3.6f))));
            else speedValue.setText("—");
            gpsStatus.setText("GPS متصل • أوفلاين");
            if (mapController != null) mapController.updateLocation(location.getLatitude(), location.getLongitude(), location.hasBearing() ? location.getBearing() : Float.NaN);
            NavigationGuidance.update(this, location); BacktrackGuidance.update(this, location); SavedPlacesDialog.updateLocation(location); syncBackgroundTrackUi();
        });
    }

    @Override public void onProviderState(boolean enabled) {
        runOnUiThread(() -> {
            gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح"); speedValue.setText("—");
            if (!enabled) {
                SavedPlacesDialog.updateLocation(null); NavigationGuidance.stop(this); BacktrackGuidance.stop(this);
                View nav = findViewById(R.id.nav_panel); if (nav != null) nav.setVisibility(View.GONE);
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                MapUiPreferences.ensureAutomaticTracking(this); locationController.start(); BackgroundTrackService.ensureRunning(this);
            } else gpsStatus.setText("صلاحية GPS مطلوبة");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACTIVATION) {
            if (licenseManager.isLicensed()) initializeSafely(); else finishAffinity();
        } else if (requestCode == REQUEST_MAP_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
            importMap(uri);
        } else if (requestCode == REQUEST_MIGRATION && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importLegacyMigration(data.getData());
        }
    }

    @Override protected void onResume() {
        super.onResume(); immersive();
        if (initialized && locationController != null && locationController.hasPermission()) { MapUiPreferences.ensureAutomaticTracking(this); locationController.start(); }
        if (initialized) {
            if (!trackReceiverRegistered) {
                ContextCompat.registerReceiver(this, trackCommitReceiver,
                        new IntentFilter(BackgroundTrackService.ACTION_TRACK_COMMITTED),
                        ContextCompat.RECEIVER_NOT_EXPORTED);
                trackReceiverRegistered = true;
            }
            BackgroundTrackService.ensureRunning(this); syncBackgroundTrackUi(); restoreActiveTrack(); showPendingTrackResult();
        }
    }

    private void showPendingTrackResult() {
        TrackRuntimeState.Result result = TrackRuntimeState.peekResult(this);
        if (result != null) {
            TrackRuntimeState.clearResult(this);
            if (result.success) {
                if (mapController != null && !MapUiPreferences.backgroundTrackEnabled(this)) mapController.showActiveTrack(java.util.Collections.emptyList());
                toast(result.message);
            } else {
                showImmersive(new AlertDialog.Builder(this).setTitle("تعذر حفظ المسار")
                        .setMessage(result.message + "\n\nبقي التسجيل محفوظًا للاستعادة.")
                        .setNegativeButton("لاحقًا", null).setPositiveButton("إعادة المحاولة", (dialog, which) -> BackgroundTrackService.retryFinalize(this)).create());
            }
        } else {
            String writeError = TrackRuntimeState.writeError(this); if (writeError != null && !writeError.isEmpty()) toast(writeError);
        }
    }

    @Override protected void onPause() {
        if (trackReceiverRegistered) {
            try { unregisterReceiver(trackCommitReceiver); } catch (RuntimeException ignored) {}
            trackReceiverRegistered = false;
        }
        if (locationController != null) locationController.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (locationController != null) locationController.stop();
        if (mapController != null) mapController.destroy();
        searchEngine.clear(); ioExecutor.shutdownNow(); super.onDestroy();
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed());
    }

    private void chooseMapFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MAP_FILE);
    }

    void chooseLegacyMigration() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MIGRATION);
    }

    private void importMap(Uri uri) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("إضافة خريطة"); progress.setMessage("جارٍ نسخ وفحص ملف الخريطة…"); progress.setCancelable(false); showImmersive(progress);
        ioExecutor.execute(() -> {
            try {
                MapStorage.importMap(this, uri);
                runOnUiThread(() -> { progress.dismiss(); toast("تمت إضافة الخريطة للعمل بدون إنترنت"); loadActiveMap(); });
            } catch (Exception error) {
                runOnUiThread(() -> { progress.dismiss(); toast(error.getMessage() == null ? "تعذر إضافة الخريطة" : error.getMessage()); });
            }
        });
    }

    private void importLegacyMigration(Uri uri) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("استعادة بيانات دربك"); progress.setMessage("جارٍ التحقق والاستعادة…"); progress.setCancelable(false); showImmersive(progress);
        ioExecutor.execute(() -> {
            try {
                LegacyMigration.Result result = LegacyMigration.importBackup(this, uri);
                runOnUiThread(() -> {
                    progress.dismiss(); placeRepository = new PlaceRepository(this);
                    if (mapController != null) mapController.showSavedPlaces(placeRepository.all(), MapUiPreferences.showSavedNames(this));
                    toast("تمت الاستعادة: " + result.savedPlaces + " موقع و" + result.gpxTracks + " مسار");
                });
            } catch (Exception error) {
                runOnUiThread(() -> { progress.dismiss(); showImmersive(new AlertDialog.Builder(this).setTitle("تعذر الاستعادة")
                        .setMessage(error.getMessage() == null ? "لم تتغير بياناتك الحالية" : error.getMessage()).setPositiveButton("حسنًا", null).create()); });
            }
        });
    }

    private void confirmRecommendedMapDownload() {
        String message = "سيتم تنزيل خريطة دربك السعودية كاملة للعمل بدون إنترنت.\nالحجم " + RecommendedMapDownloader.DISPLAY_SIZE + ".\nاترك التطبيق مفتوحًا حتى يكتمل التحقق.";
        showImmersive(new AlertDialog.Builder(this).setTitle("تنزيل خريطة دربك السعودية").setMessage(message)
                .setNegativeButton("إلغاء", null).setPositiveButton("تنزيل", (dialog, which) -> downloadRecommendedMap()).create());
    }

    private void downloadRecommendedMap() {
        mapDownloadCancelled = false;
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("خريطة دربك السعودية"); progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); progress.setMax(100); progress.setCancelable(true);
        progress.setOnCancelListener(dialog -> mapDownloadCancelled = true); showImmersive(progress);
        ioExecutor.execute(() -> {
            try {
                RecommendedMapDownloader.download(this, new RecommendedMapDownloader.Listener() {
                    @Override public void onProgress(int percent, String message) { runOnUiThread(() -> { if (!isActivityUnavailable() && progress.isShowing()) { progress.setProgress(percent); progress.setMessage(message); } }); }
                    @Override public boolean isCancelled() { return mapDownloadCancelled || Thread.currentThread().isInterrupted(); }
                });
                runOnUiThread(() -> { if (!isActivityUnavailable()) { progress.dismiss(); toast("تم تجهيز خريطة دربك السعودية للعمل أوفلاين"); loadActiveMap(); } });
            } catch (RecommendedMapDownloader.CancelledException cancelled) {
                runOnUiThread(() -> { if (!isActivityUnavailable()) { progress.dismiss(); toast("تم الإيقاف ويمكن استكمال التنزيل لاحقًا"); } });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!isActivityUnavailable()) { progress.dismiss(); toast(error.getMessage() == null ? "تعذر تنزيل الخريطة" : error.getMessage()); } });
            }
        });
    }

    private void immersive() {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showImmersive(Dialog dialog) {
        if (dialog == null || isActivityUnavailable()) return;
        Window window = dialog.getWindow();
        if (window != null) window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.setOnShowListener(ignored -> {
            Window shown = dialog.getWindow();
            if (shown != null) { shown.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility()); shown.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE); }
        });
        try { dialog.show(); } catch (WindowManager.BadTokenException ignored) {}
    }

    private void toast(String message) {
        if (isActivityUnavailable()) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String formatDistance(Float meters) {
        if (meters == null) return "";
        if (meters < 1000f) return Math.max(1, Math.round(meters)) + " م";
        return String.format(Locale.US, "%.1f كم", meters / 1000f);
    }

    private String coordinates(double latitude, double longitude) {
        return String.format(Locale.US, "%.5f, %.5f", latitude, longitude);
    }
}
