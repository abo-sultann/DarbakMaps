from pathlib import Path
import re


def must_sub(pattern, replacement, text, label, flags=re.S):
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, got {count}")
    return out


# MainActivity: route all rich UI through Darbak panels, load saved markers,
# professional saved-location actions, and apply persisted screen controls.
main_path = Path("app/src/main/java/com/abosultan/darbakmaps/MainActivity.java")
s = main_path.read_text()
s = s.replace(
    "findViewById(R.id.action_more).setOnClickListener(view -> showMore());",
    "findViewById(R.id.action_more).setOnClickListener(view -> DarbakPanels.showMore(this));",
)
s = s.replace(
    "noMapPanel.setVisibility(View.GONE);\n                return;",
    "noMapPanel.setVisibility(View.GONE);\n                MapRuntimeBridge.refreshSavedPlaces(this);\n                return;",
    1,
)
s = s.replace(
    '"لا توجد نتائج؛ أضف خريطة الخليج للبحث في المدن والمعالم"',
    '"لا توجد نتائج؛ أضف خريطة دربك للبحث في المدن والمعالم"',
)
s = s.replace(
    'immersive();\n        licenseManager = new LicenseManager(this);',
    'immersive();\n        applyDisplayPreferences();\n        licenseManager = new LicenseManager(this);',
    1,
)

save_method = '''    private void saveCurrentPlace() {
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("حفظ موقع بدقة")
                .setMessage("أخفِ الأدوات بلمسة على الخريطة، ثم اضغط مطولاً على النقطة المطلوبة.\\n\\nسيظهر نموذج الحفظ لاختيار الأيقونة والنوع والاسم المختصر والملاحظة، وتبقى العلامة ظاهرة على الخريطة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("فهمت", null)
                .create());
    }

'''
s = must_sub(
    r"    private void saveCurrentPlace\(\) \{.*?\n    \}\n\n(?=    private void toggleTrackRecording)",
    save_method,
    s,
    "saveCurrentPlace",
)

places_method = '''    private void showPlaces(List<PlaceRepository.Place> places, String title) {
        if (places.isEmpty()) {
            toast("لا توجد مواقع مطابقة");
            return;
        }
        String[] labels = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            PlaceRepository.Place place = places.get(i);
            labels[i] = PlaceRepository.iconGlyph(place.iconKey) + "  " + place.name
                    + "\\n" + place.category + " • " + coordinates(place.latitude, place.longitude);
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
        String[] actions = {"عرض على الخريطة", "توجيه — " + routingLabel, "حذف الموقع"};
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(PlaceRepository.iconGlyph(selected.iconKey) + "  " + selected.name)
                .setMessage(selected.note == null || selected.note.isEmpty()
                        ? selected.category
                        : selected.category + "\\n" + selected.note)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (mapController != null) mapController.showPoint(selected.latitude, selected.longitude);
                        else toast("الخريطة غير جاهزة");
                    } else if (which == 1) {
                        if (mapController == null) {
                            toast("الخريطة غير جاهزة");
                            return;
                        }
                        MapRuntimeBridge.navigateTo(this, selected.latitude, selected.longitude);
                        if (routingMode == MapUiPreferences.ROUTING_ROADS) {
                            toast("وضع الطرق تجريبي في هذه النسخة؛ سيبقى خط الهدف ظاهرًا حتى اكتمال محرك الطرق");
                        } else {
                            toast("بدأ التوجيه المباشر إلى " + selected.name);
                        }
                    } else {
                        AlertDialog confirm = new AlertDialog.Builder(this)
                                .setTitle("حذف الموقع؟")
                                .setMessage(selected.name)
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("حذف", (d, w) -> {
                                    placeRepository.delete(selected.id);
                                    MapRuntimeBridge.refreshSavedPlaces(this);
                                    toast("تم حذف الموقع");
                                }).create();
                        showImmersive(confirm);
                    }
                })
                .setNegativeButton("رجوع", null)
                .create());
    }

'''
s = must_sub(
    r"    private void showPlaces\(List<PlaceRepository\.Place> places, String title\) \{.*?\n    \}\n\n(?=    private void showTracks)",
    places_method,
    s,
    "showPlaces",
)

s = must_sub(
    r"    private void showStartupSettings\(\) \{.*?\n    \}\n\n(?=    private void showMapManager)",
    "    private void showStartupSettings() {\n        DarbakPanels.showSettings(this);\n    }\n\n",
    s,
    "showStartupSettings",
)
s = must_sub(
    r"    private void showAbout\(\) \{.*?\n    \}\n\n(?=    private void checkForUpdates)",
    "    private void showAbout() {\n        DarbakPanels.showAbout(this);\n    }\n\n",
    s,
    "showAbout",
)

# Add display preference helper before location permission method.
display_helper = '''    private void applyDisplayPreferences() {
        if (MapUiPreferences.keepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

'''
if "private void applyDisplayPreferences()" not in s:
    s = s.replace("    private void ensureLocationPermission() {", display_helper + "    private void ensureLocationPermission() {", 1)

# Apply speed visibility once the layout exists.
s = s.replace(
    "speedValue = findViewById(R.id.speed_value);",
    "speedValue = findViewById(R.id.speed_value);\n        View speedPanel = findViewById(R.id.speed_panel);\n        if (speedPanel != null) speedPanel.setVisibility(MapUiPreferences.showSpeed(this) ? View.VISIBLE : View.GONE);",
    1,
)
main_path.write_text(s)


# DarbakPanels: make Settings a real control center and keep full Darbak identity.
panels_path = Path("app/src/main/java/com/abosultan/darbakmaps/DarbakPanels.java")
p = panels_path.read_text()
settings = '''    static void showSettings(Activity activity) {
        Dialog dialog = baseDialog(activity);
        LinearLayout root = panel(activity, 18);
        root.addView(title(activity, "إعدادات دربك"));
        root.addView(subtitle(activity, "التحكم بالخريطة والتوجيه والعلامات وشاشة السيارة"));

        root.addView(toggleCard(activity,
                "التشغيل مع الشاشة",
                "يفتح دربك تلقائيًا بعد تشغيل الشاشة",
                StartupPreferences.isEnabled(activity),
                checked -> StartupPreferences.setEnabled(activity, checked)));

        TextView routing = text(activity,
                "نمط التوجيه: " + MapRuntimeBridge.routingLabel(MapUiPreferences.routingMode(activity)) + "  •  اضغط للتغيير",
                TEXT, 15f, Gravity.CENTER);
        routing.setBackground(round(SURFACE_ALT, dp(activity, 20), Color.argb(90, 215, 173, 85)));
        routing.setOnClickListener(view -> {
            int next = MapUiPreferences.routingMode(activity) == MapUiPreferences.ROUTING_DIRECT
                    ? MapUiPreferences.ROUTING_ROADS : MapUiPreferences.ROUTING_DIRECT;
            MapUiPreferences.setRoutingMode(activity, next);
            String suffix = next == MapUiPreferences.ROUTING_ROADS ? " (تجريبي)" : "";
            routing.setText("نمط التوجيه: " + MapRuntimeBridge.routingLabel(next) + suffix + "  •  اضغط للتغيير");
        });
        LinearLayout.LayoutParams routingParams = new LinearLayout.LayoutParams(-1, dp(activity, 60));
        routingParams.topMargin = dp(activity, 7);
        root.addView(routing, routingParams);

        root.addView(toggleCard(activity,
                "علامات المواقع المحفوظة",
                "إظهار الأيقونات على الخريطة دائمًا",
                MapUiPreferences.showSavedPlaces(activity),
                checked -> { MapUiPreferences.setShowSavedPlaces(activity, checked); MapRuntimeBridge.refreshSavedPlaces(activity); }), compact(activity));

        root.addView(toggleCard(activity,
                "أسماء المواقع المحفوظة",
                "إظهار الاسم بجوار أيقونة المخيم أو البيت وغيرها",
                MapUiPreferences.showSavedLabels(activity),
                checked -> { MapUiPreferences.setShowSavedLabels(activity, checked); MapRuntimeBridge.refreshSavedPlaces(activity); }), compact(activity));

        root.addView(toggleCard(activity,
                "إظهار السرعة",
                "إظهار أو إخفاء قراءة كم/س على الخريطة",
                MapUiPreferences.showSpeed(activity),
                checked -> {
                    MapUiPreferences.setShowSpeed(activity, checked);
                    View speed = activity.findViewById(R.id.speed_panel);
                    if (speed != null) speed.setVisibility(checked ? View.VISIBLE : View.GONE);
                }), compact(activity));

        root.addView(toggleCard(activity,
                "إبقاء الشاشة مضاءة",
                "مناسب للقيادة والبر أثناء عرض الخريطة",
                MapUiPreferences.keepScreenOn(activity),
                checked -> {
                    MapUiPreferences.setKeepScreenOn(activity, checked);
                    if (checked) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }), compact(activity));

        TextView orientation = text(activity,
                "اتجاه الخريطة: " + MapRuntimeBridge.label(MapUiPreferences.orientation(activity)) + "  •  اضغط للتغيير",
                TEXT, 15f, Gravity.CENTER);
        orientation.setBackground(round(SURFACE_ALT, dp(activity, 20), Color.argb(80, 57, 169, 255)));
        orientation.setOnClickListener(view -> {
            int mode = MapRuntimeBridge.cycleOrientation(activity);
            orientation.setText("اتجاه الخريطة: " + MapRuntimeBridge.label(mode) + "  •  اضغط للتغيير");
        });
        LinearLayout.LayoutParams orientationParams = new LinearLayout.LayoutParams(-1, dp(activity, 58));
        orientationParams.topMargin = dp(activity, 7);
        root.addView(orientation, orientationParams);

        TextView behavior = text(activity,
                "الأدوات تظهر وتختفي بلمسة على الخريطة فقط — بدون مؤقت",
                GOLD, 12.5f, Gravity.CENTER);
        LinearLayout.LayoutParams behaviorParams = new LinearLayout.LayoutParams(-1, dp(activity, 38));
        behaviorParams.topMargin = dp(activity, 6);
        root.addView(behavior, behaviorParams);

        TextView done = action(activity, "تم", PRIMARY, NIGHT);
        done.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(activity, 160), dp(activity, 44));
        doneParams.gravity = Gravity.CENTER;
        doneParams.topMargin = dp(activity, 6);
        root.addView(done, doneParams);

        dialog.setContentView(root);
        show(dialog, activity, 820);
    }

    private static LinearLayout.LayoutParams compact(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 72));
        params.topMargin = dp(activity, 6);
        return params;
    }

'''
p = must_sub(
    r"    static void showSettings\(Activity activity\) \{.*?\n    \}\n\n(?=    private interface ToggleAction)",
    settings,
    p,
    "DarbakPanels.showSettings",
)
p = p.replace(
    '"أدوات أساسية فقط — الخريطة تبقى هي الواجهة"',
    '"واجهة دربك للبر — أوفلاين ومهيأة لشاشة السيارة"',
)
p = p.replace(
    '"تشغيل الشاشة وسلوك الأدوات"',
    '"التوجيه والعلامات واتجاه الخريطة والعرض"',
)
p = p.replace(
    '"تنزيل خريطة الخليج الموصى بها"',
    '"خريطة دربك السعودية المعتمدة"',
)
panels_path.write_text(p)


# Car screen: no obsolete GCC downloader in the UI; official map import only.
layout_path = Path("app/src/main/java/com/abosultan/darbakmaps/CarScreenLayout.java")
c = layout_path.read_text()
c = c.replace(
    'Button download = button(activity, "تنزيل خريطة الخليج", R.id.download_map, PRIMARY, NIGHT);\n        empty.addView(download, new LinearLayout.LayoutParams(dp(activity, 290), dp(activity, 52)));',
    'Button download = button(activity, "خريطة دربك", R.id.download_map, PRIMARY, NIGHT);\n        download.setVisibility(View.GONE);\n        empty.addView(download, new LinearLayout.LayoutParams(1, 1));',
)
c = c.replace(
    'Button importMap = button(activity, "إضافة خريطة من USB", R.id.import_map, SURFACE_ALT, TEXT);',
    'Button importMap = button(activity, "إضافة خريطة دربك من USB أو الذاكرة", R.id.import_map, SURFACE_ALT, TEXT);',
)
c = c.replace(
    'LinearLayout speedPill = new LinearLayout(activity);',
    'LinearLayout speedPill = new LinearLayout(activity);\n        speedPill.setId(R.id.speed_panel);',
    1,
)
layout_path.write_text(c)


# Search: T3-safe bounded first search. Focused tiles are scanned first.
search_path = Path("app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapSearchEngine.java")
q = search_path.read_text()
q = q.replace("private static final int MAX_INDEX_ITEMS = 24_000;", "private static final int MAX_INDEX_ITEMS = 10_000;")
q = q.replace("private static final int NEARBY_RADIUS_TILES = 9;", "private static final int NEARBY_RADIUS_TILES = 5;")
if "INDEX_BUDGET_NANOS" not in q:
    q = q.replace(
        "private static final int NEARBY_RADIUS_TILES = 5;",
        "private static final int NEARBY_RADIUS_TILES = 5;\n    private static final long INDEX_BUDGET_NANOS = 2_800_000_000L;",
        1,
    )
    q = q.replace(
        "LinkedHashMap<String, Result> output = new LinkedHashMap<>();\n        MapFile mapFile = null;",
        "LinkedHashMap<String, Result> output = new LinkedHashMap<>();\n        final long deadline = System.nanoTime() + INDEX_BUDGET_NANOS;\n        MapFile mapFile = null;",
        1,
    )
    q = q.replace(
        "for (TileRef tileRef : tiles) {\n                if (output.size() >= MAX_INDEX_ITEMS) {",
        "for (TileRef tileRef : tiles) {\n                if (System.nanoTime() >= deadline) break;\n                if (output.size() >= MAX_INDEX_ITEMS) {",
        1,
    )
search_path.write_text(q)


# Version for this coherent test release.
gradle_path = Path("app/build.gradle")
g = gradle_path.read_text()
g = re.sub(r"versionCode\s+\d+", "versionCode 13", g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '0.6.0'", g, count=1)
gradle_path.write_text(g)

print("Darbak Maps UX migration applied")
