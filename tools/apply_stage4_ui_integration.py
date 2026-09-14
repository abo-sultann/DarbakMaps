from pathlib import Path

main = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
layout = Path('app/src/main/java/com/abosultan/darbakmaps/CarScreenLayout.java')
s = main.read_text(encoding='utf-8')
l = layout.read_text(encoding='utf-8')


def rep(text, old, new, label):
    if old not in text:
        raise SystemExit('missing pattern: ' + label)
    return text.replace(old, new, 1)

s = rep(s,
'''        findViewById(R.id.action_saved).setOnClickListener(view -> showSavedHub());''',
'''        findViewById(R.id.action_saved).setOnClickListener(view -> showSavedPlacesPanel());
        findViewById(R.id.action_saved).setOnLongClickListener(view -> {
            showSavedHub();
            return true;
        });''', 'saved action')

old = '''    private void showSearchResults(List<OfflineMapSearchEngine.Result> results, boolean complete, boolean failed) {
        if (results.isEmpty()) {
            if (!complete) {
                toast("البحث ما زال يفهرس الخريطة؛ أعد البحث لاستكمال بقية المناطق");
            } else if (failed) {
                toast("اكتمل البحث جزئيًا وتعذر قراءة بعض أجزاء الخريطة");
            } else {
                toast(MapStorage.activeMap(this).isFile()
                        ? "لا توجد نتائج مطابقة بعد اكتمال الفهرسة"
                        : "لا توجد نتائج؛ أضف خريطة دربك للبحث في المدن والمعالم");
            }
            return;
        }
        String[] labels = new String[results.size()];
        for (int index = 0; index < results.size(); index++) {
            OfflineMapSearchEngine.Result result = results.get(index);
            String distance = formatDistance(result.distanceMeters);
            labels[index] = result.name + "\\n" + result.source + (distance.isEmpty() ? "" : " • " + distance);
        }
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(complete ? (failed ? "نتائج البحث — بعض أجزاء الخريطة تعذرت" : "نتائج البحث") : "نتائج جزئية — أعد البحث للاستكمال")
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
'''
new = '''    private void showSearchResults(List<OfflineMapSearchEngine.Result> results, boolean complete, boolean failed) {
        if (results.isEmpty()) {
            if (!complete) {
                toast("الفهرسة لم تكتمل بعد؛ أعد البحث لاستكمال بقية الخريطة");
            } else if (failed) {
                toast("اكتمل البحث جزئيًا وتعذر قراءة بعض أجزاء الخريطة");
            } else {
                toast(MapStorage.activeMap(this).isFile()
                        ? "لا توجد نتائج مطابقة في البيانات المفهرسة"
                        : "لا توجد نتائج؛ أضف خريطة دربك للبحث في المعالم");
            }
            return;
        }
        String[] labels = new String[results.size()];
        for (int index = 0; index < results.size(); index++) {
            OfflineMapSearchEngine.Result result = results.get(index);
            String distance = formatDistance(result.distanceMeters);
            labels[index] = result.name + "\\n" + result.source
                    + (distance.isEmpty() ? "" : " • " + distance);
        }
        String state = searchEngine.isTruncated()
                ? "نتائج مقتطعة — ضيّق البحث أو استخدم البحث القريب"
                : (!complete ? "نتائج جزئية — الفهرسة تستكمل تدريجيًا" : "نتائج البحث");
        if (failed) state += " • تعذر جزء من الخريطة";
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(state)
                .setItems(labels, (dialog, which) -> showSearchResultActions(results.get(which)))
                .setNegativeButton("إغلاق", null)
                .create());
    }

    private void showSearchResultActions(OfflineMapSearchEngine.Result result) {
        String[] actions = {"عرض على الخريطة", "حفظ بالأيقونة", "توجيه مباشر", "البحث حول هذا المعلم"};
        showImmersive(new AlertDialog.Builder(this)
                .setTitle(result.name)
                .setMessage(result.source + (result.distanceMeters == null ? "" : " • " + formatDistance(result.distanceMeters)))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (mapController != null) mapController.showPoint(result.latitude, result.longitude);
                        else toast("الخريطة غير جاهزة");
                    } else if (which == 1) {
                        PointEditor.show(this, result.latitude, result.longitude, null);
                    } else if (which == 2) {
                        startDirectNavigation(result.name, result.latitude, result.longitude);
                    } else {
                        showNearbySearchOptions(result.latitude, result.longitude, result.name);
                    }
                })
                .setNegativeButton("رجوع", null)
                .create());
    }

    void showNearbySearchFromCurrentLocation() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        if (current == null) {
            toast("بانتظار GPS للبحث حول موقعي");
            return;
        }
        showNearbySearchOptions(current.getLatitude(), current.getLongitude(), "موقعي الحالي");
    }

    private void showNearbySearchOptions(double latitude, double longitude, String centerLabel) {
        String[] categories = {
                OfflineMapSearchEngine.CATEGORY_ALL,
                OfflineMapSearchEngine.CATEGORY_WADIS,
                OfflineMapSearchEngine.CATEGORY_MOUNTAINS,
                OfflineMapSearchEngine.CATEGORY_LANDMARKS,
                OfflineMapSearchEngine.CATEGORY_VILLAGES,
                OfflineMapSearchEngine.CATEGORY_WATER,
                OfflineMapSearchEngine.CATEGORY_SERVICES
        };
        showImmersive(new AlertDialog.Builder(this)
                .setTitle("حول " + centerLabel)
                .setItems(categories, (categoryDialog, categoryIndex) -> {
                    int[] radii = {5, 10, 25, 50, 100};
                    String[] radiusLabels = {"5 كم", "10 كم", "25 كم", "50 كم", "100 كم"};
                    showImmersive(new AlertDialog.Builder(this)
                            .setTitle("النطاق • " + categories[categoryIndex])
                            .setItems(radiusLabels, (radiusDialog, radiusIndex) -> runNearbySearch(
                                    latitude, longitude, centerLabel, categories[categoryIndex], radii[radiusIndex]))
                            .setNegativeButton("إلغاء", null)
                            .create());
                })
                .setNegativeButton("إلغاء", null)
                .create());
    }

    private void runNearbySearch(double latitude, double longitude, String centerLabel,
                                 String category, int radiusKm) {
        if (searchRunning) {
            toast("البحث السابق ما زال جاريًا");
            return;
        }
        searchRunning = true;
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("بحث قريب");
        progress.setMessage("حول " + centerLabel + " • " + radiusKm + " كم");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        showImmersive(progress);
        File activeMap = MapStorage.activeMap(this);
        ioExecutor.execute(() -> {
            OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                    "", category, latitude, longitude, radiusKm * 1000f, true, 120);
            List<OfflineMapSearchEngine.Result> results = searchEngine.search(
                    request, activeMap.isFile() ? activeMap : null, placeRepository.all());
            boolean complete = searchEngine.isComplete();
            boolean failed = searchEngine.hasFailed();
            runOnUiThread(() -> {
                searchRunning = false;
                if (isActivityUnavailable()) return;
                progress.dismiss();
                showSearchResults(results, complete, failed);
            });
        });
    }

    private void showSavedPlacesPanel() {
        Location current = locationController == null ? null : locationController.getLastLocation();
        SavedPlacesDialog.show(this, placeRepository, current,
                place -> startDirectNavigation(place.name, place.latitude, place.longitude));
    }

    private void startDirectNavigation(String name, double latitude, double longitude) {
        if (mapController == null) {
            toast("الخريطة غير جاهزة");
            return;
        }
        BacktrackGuidance.stop(this);
        NavigationGuidance.start(this, name, latitude, longitude);
        toast("توجيه مباشر في البر — الخط لا يعني وجود طريق صالح للعبور");
    }
'''
s = rep(s, old, new, 'search results/actions')

s = rep(s,
'''            NavigationGuidance.update(this, location);
            BacktrackGuidance.update(this, location);
            syncBackgroundTrackUi();''',
'''            NavigationGuidance.update(this, location);
            BacktrackGuidance.update(this, location);
            SavedPlacesDialog.updateLocation(location);
            syncBackgroundTrackUi();''', 'saved live update')

s = rep(s,
'''            speedValue.setText("—");
            if (!enabled) {
                NavigationGuidance.stop(this);''',
'''            speedValue.setText("—");
            if (!enabled) {
                SavedPlacesDialog.updateLocation(null);
                NavigationGuidance.stop(this);''', 'saved GPS unavailable')

l = rep(l,
'''        TextView searchMark = label(activity, "⌕", PRIMARY, 27f, Gravity.CENTER);
        searchBox.addView(searchMark, new LinearLayout.LayoutParams(dp(activity, 36), -1));''',
'''        TextView searchMark = label(activity, "◎", PRIMARY, 22f, Gravity.CENTER);
        searchMark.setContentDescription("البحث حول موقعي");
        searchMark.setOnClickListener(view -> {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).showNearbySearchFromCurrentLocation();
            }
        });
        searchBox.addView(searchMark, new LinearLayout.LayoutParams(dp(activity, 42), -1));''', 'nearby search button')

main.write_text(s, encoding='utf-8')
layout.write_text(l, encoding='utf-8')
