from pathlib import Path
import re

ROOT = Path('app/src/main/java/com/abosultan/darbakmaps')
RES = Path('app/src/main/res')


def replace_method(src, start_marker, end_marker, replacement, label):
    start = src.find(start_marker)
    if start < 0:
        raise SystemExit('missing method start: ' + label)
    end = src.find(end_marker, start)
    if end < 0:
        raise SystemExit('missing method end: ' + label)
    return src[:start] + replacement + '\n\n' + src[end:]


# Finish palette cleanup, including alpha-blue references that survived earlier exact replacements.
for p in ROOT.rglob('*.java'):
    s = p.read_text()
    old = s
    s = re.sub(r'Color\.argb\((\d+),\s*57,\s*169,\s*255\)', r'Color.argb(\1, 216, 180, 91)', s)
    if s != old:
        p.write_text(s)

xml_replacements = {
    '39A9FF': 'D8B45B',
    '07111D': '08271F',
    '111D2B': '10342A',
    '101E2C': '19483A',
    'D7AD55': 'D8B45B',
    '9FB0C2': 'B9B3A5',
    'F4F7FA': 'F7F2E7',
}
for p in RES.rglob('*.xml'):
    s = p.read_text()
    old = s
    for before, after in xml_replacements.items():
        s = s.replace(before, after).replace(before.lower(), after.lower())
    if s != old:
        p.write_text(s)

# Saved places must fit safely inside 600px head unit without clipping the close action.
p = ROOT / 'SavedPlacesDialog.java'
s = p.read_text().replace('root.addView(list, new LinearLayout.LayoutParams(-1, dp(350)));',
                          'root.addView(list, new LinearLayout.LayoutParams(-1, dp(320)));')
p.write_text(s)

# DarbakPanels: remove stock progress/info/update dialogs from user-visible flows.
p = ROOT / 'DarbakPanels.java'
s = p.read_text()
s = s.replace('import android.app.ProgressDialog;\n', '')
s = s.replace('import android.widget.Switch;\n', '')

s = replace_method(s,
    '    private static void downloadRecommendedMap(Activity activity) {',
    '    private static void showCoordinates(Activity activity) {',
'''    private static void downloadRecommendedMap(Activity activity) {
        DarbakProgressDialog progress = new DarbakProgressDialog(activity, "خرائط دربك", false, null);
        progress.setMessage("بدء التنزيل…");
        progress.show();

        new Thread(() -> {
            try {
                RecommendedMapDownloader.download(activity, new RecommendedMapDownloader.Listener() {
                    @Override
                    public void onProgress(int percent, String message) {
                        activity.runOnUiThread(() -> progress.setProgress(percent, message));
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    DarbakInfoDialog.show(activity, "الخريطة جاهزة", "تم تجهيز خريطة دربك للعمل بدون إنترنت.");
                    activity.recreate();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    DarbakInfoDialog.show(activity, "تعذر تنزيل الخريطة",
                            error.getMessage() == null ? "تعذر تنزيل الخريطة" : error.getMessage());
                });
            }
        }, "darbak-map-download").start();
    }''', 'panel map download')

s = replace_method(s,
    '    private static void showCoordinates(Activity activity) {',
    '    static void showAbout(Activity activity) {',
'''    private static void showCoordinates(Activity activity) {
        String message = "بانتظار إشارة GPS";
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationManager manager = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
                Location location = manager == null ? null : manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    message = String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude());
                }
            } catch (RuntimeException ignored) {
                // Keep safe fallback on vendor ROMs.
            }
        }
        DarbakInfoDialog.show(activity, "الإحداثيات الحالية", message);
    }''', 'coordinates')

s = replace_method(s,
    '    private static void checkForUpdates(Activity activity) {',
    '    private static Dialog baseDialog(Activity activity) {',
'''    private static void checkForUpdates(Activity activity) {
        Toast.makeText(activity, "جارٍ التحقق من التحديث…", Toast.LENGTH_SHORT).show();
        UpdateManager.check(new UpdateManager.Callback() {
            @Override
            public void onStatus(String message) {
                activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onUpdate(UpdateManager.UpdateInfo update) {
                activity.runOnUiThread(() -> DarbakConfirmDialog.show(activity,
                        "تحديث " + update.versionName,
                        "نسخة جديدة من دربك جاهزة للتثبيت.",
                        "تنزيل وتثبيت", false,
                        () -> UpdateManager.downloadAndInstall(activity, update, this)));
            }
        });
    }''', 'update confirm')
p.write_text(s)

# MainActivity: every user-visible wait/choice/info path uses Darbak components.
p = ROOT / 'MainActivity.java'
s = p.read_text()
s = s.replace('import android.app.ProgressDialog;\n', '')

s = replace_method(s,
    '    private void executeSearch(OfflineMapSearchEngine.SearchRequest request, String title) {',
    '    private void showSearchResults(OfflineMapSearchEngine.SearchRequest request,',
'''    private void executeSearch(OfflineMapSearchEngine.SearchRequest request, String title) {
        if (searchRunning) { toast("البحث السابق ما زال جاريًا"); return; }
        searchRunning = true;
        lastSearchRequest = request;
        DarbakProgressDialog progress = new DarbakProgressDialog(this, title, false, null);
        progress.setMessage(request.offset > 0 ? "جارٍ تحميل الصفحة التالية…" : "جارٍ البحث…");
        progress.show();
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
    }''', 'search progress')

s = replace_method(s,
    '    private void showMapManager() {',
    '    private void showCoordinates() {',
'''    private void showMapManager() {
        File file = MapStorage.activeMap(this);
        String status = file.isFile()
                ? "الخريطة الحالية • " + Math.max(1, file.length() / (1024 * 1024)) + " م.ب • جاهزة أوفلاين"
                : "لا توجد حزمة خريطة مضافة";
        String[] actions = { "خريطة دربك السعودية (" + RecommendedMapDownloader.DISPLAY_SIZE + ")",
                "إضافة خريطة من USB أو الذاكرة" };
        DarbakChoiceDialog.show(this, "الخرائط الأوفلاين", status, actions,
                which -> { if (which == 0) confirmRecommendedMapDownload(); else chooseMapFile(); });
    }''', 'map manager')

s = replace_method(s,
    '    private void showCoordinates() {',
    '    private void showDeviceLicense() {',
'''    private void showCoordinates() {
        Location location = locationController == null ? null : locationController.getLastLocation();
        String message = location == null ? "بانتظار إشارة GPS" : coordinates(location.getLatitude(), location.getLongitude());
        DarbakInfoDialog.show(this, "الإحداثيات الحالية", message);
    }''', 'main coordinates')

s = replace_method(s,
    '    private void showDeviceLicense() {',
    '    private void checkForUpdates() {',
'''    private void showDeviceLicense() {
        String state = licenseManager.isLicensed() ? "مفعّل" : "غير مفعّل";
        DarbakInfoDialog.show(this, "ترخيص الجهاز",
                "الحالة: " + state + System.lineSeparator() + "رمز الجهاز: " + licenseManager.deviceCode());
    }''', 'device license')

s = replace_method(s,
    '    private void checkForUpdates() {',
    '    private void applyDisplayPreferences() {',
'''    private void checkForUpdates() {
        toast("جارٍ التحقق من التحديث…");
        UpdateManager.check(new UpdateManager.Callback() {
            @Override public void onStatus(String message) { runOnUiThread(() -> toast(message)); }
            @Override public void onUpdate(UpdateManager.UpdateInfo update) {
                runOnUiThread(() -> DarbakConfirmDialog.show(MainActivity.this,
                        "تحديث " + update.versionName,
                        "نسخة جديدة من دربك جاهزة للتثبيت.",
                        "تنزيل وتثبيت", false,
                        () -> {
                            toast("بدأ تنزيل التحديث");
                            UpdateManager.downloadAndInstall(MainActivity.this, update, this);
                        }));
            }
        });
    }''', 'main update confirm')

s = replace_method(s,
    '    private void showPendingTrackResult() {',
    '    @Override protected void onPause() {',
'''    private void showPendingTrackResult() {
        TrackRuntimeState.Result result = TrackRuntimeState.peekResult(this);
        if (result != null) {
            TrackRuntimeState.clearResult(this);
            if (result.success) {
                if (mapController != null && !MapUiPreferences.backgroundTrackEnabled(this)) {
                    mapController.showActiveTrack(java.util.Collections.emptyList());
                }
                toast(result.message);
            } else {
                DarbakConfirmDialog.show(this, "تعذر حفظ المسار",
                        result.message + System.lineSeparator() + System.lineSeparator()
                                + "بقي التسجيل محفوظًا للاستعادة.",
                        "إعادة المحاولة", false,
                        () -> BackgroundTrackService.retryFinalize(this));
            }
        } else {
            String writeError = TrackRuntimeState.writeError(this);
            if (writeError != null && !writeError.isEmpty()) toast(writeError);
        }
    }''', 'pending track result')

s = replace_method(s,
    '    private void importMap(Uri uri) {',
    '    private void importLegacyMigration(Uri uri) {',
'''    private void importMap(Uri uri) {
        DarbakProgressDialog progress = new DarbakProgressDialog(this, "إضافة خريطة", false, null);
        progress.setMessage("جارٍ نسخ وفحص ملف الخريطة…");
        progress.show();
        ioExecutor.execute(() -> {
            try {
                MapStorage.importMap(this, uri);
                runOnUiThread(() -> {
                    progress.dismiss();
                    toast("تمت إضافة الخريطة للعمل بدون إنترنت");
                    loadActiveMap();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    DarbakInfoDialog.show(this, "تعذر إضافة الخريطة",
                            error.getMessage() == null ? "تعذر إضافة الخريطة" : error.getMessage());
                });
            }
        });
    }''', 'map import')

s = replace_method(s,
    '    private void importLegacyMigration(Uri uri) {',
    '    private void confirmRecommendedMapDownload() {',
'''    private void importLegacyMigration(Uri uri) {
        DarbakProgressDialog progress = new DarbakProgressDialog(this, "استعادة بيانات دربك", false, null);
        progress.setMessage("جارٍ التحقق والاستعادة…");
        progress.show();
        ioExecutor.execute(() -> {
            try {
                LegacyMigration.Result result = LegacyMigration.importBackup(this, uri);
                runOnUiThread(() -> {
                    progress.dismiss();
                    placeRepository = new PlaceRepository(this);
                    if (mapController != null) mapController.showSavedPlaces(placeRepository.all(), MapUiPreferences.showSavedNames(this));
                    DarbakInfoDialog.show(this, "اكتملت الاستعادة",
                            "تمت استعادة " + result.savedPlaces + " موقع و" + result.gpxTracks + " مسار.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    DarbakInfoDialog.show(this, "تعذر الاستعادة",
                            error.getMessage() == null ? "لم تتغير بياناتك الحالية" : error.getMessage());
                });
            }
        });
    }''', 'legacy import')

s = replace_method(s,
    '    private void confirmRecommendedMapDownload() {',
    '    private void downloadRecommendedMap() {',
'''    private void confirmRecommendedMapDownload() {
        String message = "سيتم تنزيل خريطة دربك السعودية كاملة للعمل بدون إنترنت."
                + System.lineSeparator() + "الحجم " + RecommendedMapDownloader.DISPLAY_SIZE + "."
                + System.lineSeparator() + "اترك التطبيق مفتوحًا حتى يكتمل التحقق.";
        DarbakConfirmDialog.show(this, "تنزيل خريطة دربك السعودية", message,
                "تنزيل", false, this::downloadRecommendedMap);
    }''', 'map download confirm')

s = replace_method(s,
    '    private void downloadRecommendedMap() {',
    '    private void immersive() {',
'''    private void downloadRecommendedMap() {
        mapDownloadCancelled = false;
        DarbakProgressDialog progress = new DarbakProgressDialog(this, "خريطة دربك السعودية", true,
                () -> mapDownloadCancelled = true);
        progress.setMessage("بدء التنزيل…");
        progress.show();
        ioExecutor.execute(() -> {
            try {
                RecommendedMapDownloader.download(this, new RecommendedMapDownloader.Listener() {
                    @Override public void onProgress(int percent, String message) {
                        runOnUiThread(() -> {
                            if (!isActivityUnavailable() && progress.isShowing()) progress.setProgress(percent, message);
                        });
                    }
                    @Override public boolean isCancelled() {
                        return mapDownloadCancelled || Thread.currentThread().isInterrupted();
                    }
                });
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        progress.dismiss();
                        DarbakInfoDialog.show(this, "الخريطة جاهزة",
                                "تم تجهيز خريطة دربك السعودية للعمل بدون إنترنت.");
                        loadActiveMap();
                    }
                });
            } catch (RecommendedMapDownloader.CancelledException cancelled) {
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        progress.dismiss();
                        toast("تم الإيقاف ويمكن استكمال التنزيل لاحقًا");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        progress.dismiss();
                        DarbakInfoDialog.show(this, "تعذر تنزيل الخريطة",
                                error.getMessage() == null ? "تعذر تنزيل الخريطة" : error.getMessage());
                    }
                });
            }
        });
    }''', 'main map download')

# Keep the old private helper safe if any internal path references it later.
s = replace_method(s,
    '    private void saveCurrentPlace() {',
    '    private void toggleTrackPause() {',
'''    private void saveCurrentPlace() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        if (current == null) {
            toast("بانتظار إشارة GPS");
            return;
        }
        PointEditor.show(this, current.getLatitude(), current.getLongitude(), null);
    }''', 'save current place')
p.write_text(s)

# Clean stale comment and indentation from the main shell.
p = ROOT / 'CarScreenLayout.java'
s = p.read_text().replace(
    ' * DarbakMaps hybrid map-first layout for 1024x600 car screens.\n * The simple map-first shell remains the default; a richer side panel opens only on demand.',
    ' * DarbakMaps map-first layout for 1024x600 car screens.\n * One action path per task; secondary actions live in unified Darbak panels.')
s = s.replace('                dock.addView(dockAction(activity, "حفظ موقع", R.id.action_save), weighted());',
              '        dock.addView(dockAction(activity, "حفظ موقع", R.id.action_save), weighted());')
p.write_text(s)

print('Darbak 0.9.2 UI finalization applied')
