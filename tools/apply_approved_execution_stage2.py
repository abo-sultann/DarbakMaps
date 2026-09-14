from pathlib import Path

# Triggered after workflow installation; keep this script idempotent to preserve CI quota.
path = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = path.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing pattern: {label}')
    s = s.replace(old, new, 1)

old = '''        actionRecord.setOnClickListener(view -> toggleTrackRecording());
        actionRecord.setOnLongClickListener(view -> {
            if (!MapUiPreferences.backgroundTrackEnabled(this)) return false;
            boolean paused = TrackSessionState.togglePaused(this);
            TrackSessionState.updateActionLabel(actionRecord, this);
            if (!paused && mapController != null) mapController.startTrackSegment();
            toast(paused ? "تم إيقاف تسجيل المسار مؤقتًا" : "تمت متابعة تسجيل المسار");
            return true;
        });'''
new = '''        actionRecord.setOnClickListener(view -> toggleTrackPause());
        actionRecord.setOnLongClickListener(view -> {
            saveAutomaticTrackSnapshot();
            return true;
        });'''
replace_once(old, new, 'record action')

old = '''    private void toggleTrackRecording() {
        if (TrackRuntimeState.isFinalizing(this)) {
            toast("جارٍ حفظ المسار؛ انتظر ظهور النتيجة");
            return;
        }
        boolean enabled = !MapUiPreferences.backgroundTrackEnabled(this);
        BackgroundTrackService.setEnabled(this, enabled);
        onBackgroundTrackSettingChanged(enabled);
        toast(enabled
                ? "بدأ رسم وتسجيل المسار — سيستمر عند إغلاق التطبيق"
                : "جارٍ إنهاء وحفظ المسار…");
    }
'''
new = '''    private void toggleTrackPause() {
        if (!MapUiPreferences.backgroundTrackEnabled(this)) {
            MapUiPreferences.setBackgroundTrackEnabled(this, true);
            TrackSessionState.beginIfNeeded(this);
            BackgroundTrackService.ensureRunning(this);
            onBackgroundTrackSettingChanged(true);
        }
        boolean paused = TrackSessionState.togglePaused(this);
        if (!paused && mapController != null) mapController.startTrackSegment();
        syncBackgroundTrackUi();
        toast(paused
                ? "توقف التسجيل التلقائي مؤقتًا — السجل محفوظ"
                : "استؤنف تسجيل آخر 1000 كم تلقائيًا");
    }

    private void saveAutomaticTrackSnapshot() {
        toast("جارٍ حفظ نسخة من المسار التلقائي…");
        ioExecutor.execute(() -> {
            try {
                File saved = BackgroundTrackStore.snapshotActive(this);
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) toast("تم حفظ نسخة: " + saved.getName());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isActivityUnavailable()) {
                        toast(error.getMessage() == null ? "تعذر حفظ نسخة المسار" : error.getMessage());
                    }
                });
            }
        });
    }
'''
replace_once(old, new, 'toggle recorder method')

old = '''        String[] items = {
                "المواقع المحفوظة (" + placeRepository.all().size() + ")",
                "الأقرب إلى موقعي",
                "المسارات السابقة (" + TrackStorage.list(this).length + ")"
        };'''
new = '''        String[] items = {
                "المواقع المحفوظة (" + placeRepository.all().size() + ")",
                "الأقرب إلى موقعي",
                "حفظ نسخة من آخر 1000 كم",
                "المسارات السابقة (" + TrackStorage.list(this).length + ")"
        };'''
replace_once(old, new, 'saved hub items')

old = '''                    if (which == 0) {
                        showPlaces(placeRepository.all(), "المواقع المحفوظة");
                    } else if (which == 1) {
                        showNearbyPlaces();
                    } else {
                        showTracks();
                    }'''
new = '''                    if (which == 0) {
                        showPlaces(placeRepository.all(), "المواقع المحفوظة");
                    } else if (which == 1) {
                        showNearbyPlaces();
                    } else if (which == 2) {
                        saveAutomaticTrackSnapshot();
                    } else {
                        showTracks();
                    }'''
replace_once(old, new, 'saved hub action')

old = '''    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            locationController.start();
        }
    }'''
new = '''    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            MapUiPreferences.ensureAutomaticTracking(this);
            locationController.start();
            BackgroundTrackService.ensureRunning(this);
        }
    }'''
replace_once(old, new, 'automatic tracking permission')

old = '''            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationController.start();
                BackgroundTrackService.ensureRunning(this);'''
new = '''            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                MapUiPreferences.ensureAutomaticTracking(this);
                locationController.start();
                BackgroundTrackService.ensureRunning(this);'''
replace_once(old, new, 'permission result migration')

old = '''        if (initialized && locationController != null && locationController.hasPermission()) {
            locationController.start();
        }'''
new = '''        if (initialized && locationController != null && locationController.hasPermission()) {
            MapUiPreferences.ensureAutomaticTracking(this);
            locationController.start();
        }'''
replace_once(old, new, 'resume automatic tracking')

path.write_text(s, encoding='utf-8')
