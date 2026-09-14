from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing pattern: {label}")
    return text.replace(old, new, 1)

car = Path('app/src/main/java/com/abosultan/darbakmaps/CarScreenLayout.java')
s = car.read_text()
s = s.replace('TextView speed = label(activity, "0", TEXT, 31f, Gravity.CENTER);',
              'TextView speed = label(activity, "—", TEXT, 31f, Gravity.CENTER);')
s = s.replace('root.addView(trackStatsPanel, frame(dp(activity, 335), dp(activity, 46), Gravity.BOTTOM | Gravity.RIGHT,\n                dp(activity, 14), 0, 0, dp(activity, 14)));',
              'root.addView(trackStatsPanel, frame(dp(activity, 335), dp(activity, 46), Gravity.BOTTOM | Gravity.RIGHT,\n                dp(activity, 14), 0, 0, dp(activity, 92)));')
car.write_text(s)

main = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = main.read_text()

s = replace_once(s,
'''            TrackSessionState.updateActionLabel(actionRecord, this);\n            toast(paused ? "تم إيقاف تسجيل المسار مؤقتًا" : "تمت متابعة تسجيل المسار");''',
'''            TrackSessionState.updateActionLabel(actionRecord, this);\n            if (!paused && mapController != null) mapController.startTrackSegment();\n            toast(paused ? "تم إيقاف تسجيل المسار مؤقتًا" : "تمت متابعة تسجيل المسار");''',
'pause visual segment')

s = replace_once(s,
'''    private void loadActiveMap() {\n        searchEngine.clear();''',
'''    private void loadActiveMap() {\n        try {\n            RecommendedMapDownloader.recoverInterruptedInstall(this);\n        } catch (Exception recoveryError) {\n            toast(recoveryError.getMessage() == null ? "تعذر استعادة الخريطة السابقة" : recoveryError.getMessage());\n        }\n        searchEngine.clear();''',
'map recovery')

s = replace_once(s,
'''        if (mapController != null) {\n            mapController.centerOn(location.getLatitude(), location.getLongitude());\n        }''',
'''        if (mapController != null) {\n            mapController.resumeFollow();\n            mapController.centerOn(location.getLatitude(), location.getLongitude());\n        }''',
'explicit refollow')

s = replace_once(s,
'''    private void toggleTrackRecording() {\n        boolean enabled = !MapUiPreferences.backgroundTrackEnabled(this);''',
'''    private void toggleTrackRecording() {\n        if (TrackRuntimeState.isFinalizing(this)) {\n            toast("جارٍ حفظ المسار؛ انتظر ظهور النتيجة");\n            return;\n        }\n        boolean enabled = !MapUiPreferences.backgroundTrackEnabled(this);''',
'finalizing guard')

s = s.replace('mapController.showStoredTrack(points);', 'mapController.showActiveTrack(points);', 1)

s = replace_once(s,
'''    private void showSavedHub() {\n        String[] items = {''',
'''    private void showSavedHub() {\n        if (placeRepository.hasCorruptStore()) {\n            showImmersive(new AlertDialog.Builder(this)\n                    .setTitle("تعذر قراءة المواقع المحفوظة")\n                    .setMessage("احتفظ التطبيق بالبيانات الأصلية ولم يكتب فوقها. لا تضف أو تحذف مواقع قبل الاستعادة أو التصدير.")\n                    .setPositiveButton("حسنًا", null)\n                    .create());\n            return;\n        }\n        String[] items = {''',
'corrupt saved-store UI')

old = '''    private void showPlaceActions(PlaceRepository.Place selected) {\n        int routingMode = MapUiPreferences.routingMode(this);\n        String routingLabel = MapRuntimeBridge.routingLabel(routingMode);\n        String[] actions = {"عرض على الخريطة", "توجيه — " + routingLabel, "حذف الموقع"};'''
new = '''    private void showPlaceActions(PlaceRepository.Place selected) {\n        int routingMode = MapUiPreferences.routingMode(this);\n        String routingLabel = MapRuntimeBridge.routingLabel(routingMode);\n        String[] actions = {"عرض على الخريطة", "توجيه — " + routingLabel, "تعديل الموقع", "حذف الموقع"};'''
s = replace_once(s, old, new, 'saved edit action')

old = '''                    } else {\n                        AlertDialog confirm = new AlertDialog.Builder(this)\n                                .setTitle("حذف الموقع؟")\n                                .setMessage(selected.name)\n                                .setNegativeButton("إلغاء", null)\n                                .setPositiveButton("حذف", (d, w) -> {\n                                    placeRepository.delete(selected.id);\n                                    MapRuntimeBridge.refreshSavedPlaces(this);\n                                    toast("تم حذف الموقع");\n                                }).create();\n                        showImmersive(confirm);\n                    }'''
new = '''                    } else if (which == 2) {\n                        PointEditor.show(this, selected.latitude, selected.longitude, selected);\n                    } else {\n                        AlertDialog confirm = new AlertDialog.Builder(this)\n                                .setTitle("حذف الموقع؟")\n                                .setMessage(selected.name)\n                                .setNegativeButton("إلغاء", null)\n                                .setPositiveButton("حذف", (d, w) -> {\n                                    try {\n                                        if (!placeRepository.delete(selected.id)) {\n                                            throw new IllegalStateException("الموقع لم يعد موجودًا");\n                                        }\n                                        MapRuntimeBridge.refreshSavedPlaces(this);\n                                        toast("تم حذف الموقع");\n                                    } catch (RuntimeException error) {\n                                        toast(error.getMessage() == null ? "تعذر حذف الموقع" : error.getMessage());\n                                    }\n                                }).create();\n                        showImmersive(confirm);\n                    }'''
s = replace_once(s, old, new, 'safe edit/delete')

old = '''    @Override\n    public void onLocation(Location location) {\n        runOnUiThread(() -> {\n            int speed = location.hasSpeed() ? Math.max(0, Math.round(location.getSpeed() * 3.6f)) : 0;\n            speedValue.setText(String.valueOf(speed));\n            gpsStatus.setText("GPS متصل • أوفلاين");\n            if (mapController != null) {\n                mapController.updateLocation(location.getLatitude(), location.getLongitude(),\n                        location.hasBearing() ? location.getBearing() : 0f);\n            }'''
new = '''    @Override\n    public void onLocation(Location location) {\n        runOnUiThread(() -> {\n            if (location.hasSpeed()) {\n                int speed = Math.max(0, Math.round(location.getSpeed() * 3.6f));\n                speedValue.setText(String.valueOf(speed));\n            } else {\n                speedValue.setText("—");\n            }\n            gpsStatus.setText("GPS متصل • أوفلاين");\n            if (mapController != null) {\n                mapController.updateLocation(location.getLatitude(), location.getLongitude(),\n                        location.hasBearing() ? location.getBearing() : Float.NaN);\n            }'''
s = replace_once(s, old, new, 'speed and bearing unavailable')

s = replace_once(s,
'''    @Override\n    public void onProviderState(boolean enabled) {\n        runOnUiThread(() -> gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح"));\n    }''',
'''    @Override\n    public void onProviderState(boolean enabled) {\n        runOnUiThread(() -> {\n            gpsStatus.setText(enabled ? "GPS يبحث عن الإشارة" : "GPS غير متاح");\n            speedValue.setText("—");\n            if (!enabled) {\n                NavigationGuidance.stop(this);\n                View nav = findViewById(R.id.nav_panel);\n                if (nav != null) nav.setVisibility(View.GONE);\n            }\n        });\n    }''',
'provider stale UI')

s = replace_once(s,
'''        if (initialized) {\n            BackgroundTrackService.ensureRunning(this);\n            syncBackgroundTrackUi();\n        }''',
'''        if (initialized) {\n            BackgroundTrackService.ensureRunning(this);\n            syncBackgroundTrackUi();\n            showPendingTrackResult();\n        }''',
'onResume result')

marker = '''    @Override\n    protected void onPause() {'''
method = '''    private void showPendingTrackResult() {\n        TrackRuntimeState.Result result = TrackRuntimeState.peekResult(this);\n        if (result != null) {\n            TrackRuntimeState.clearResult(this);\n            if (result.success) {\n                toast(result.message);\n            } else {\n                showImmersive(new AlertDialog.Builder(this)\n                        .setTitle("تعذر حفظ المسار")\n                        .setMessage(result.message + "\\n\\nبقي التسجيل محفوظًا للاستعادة.")\n                        .setNegativeButton("لاحقًا", null)\n                        .setPositiveButton("إعادة المحاولة", (dialog, which) -> BackgroundTrackService.retryFinalize(this))\n                        .create());\n            }\n        } else {\n            String writeError = TrackRuntimeState.writeError(this);\n            if (writeError != null && !writeError.isEmpty()) toast(writeError);\n        }\n    }\n\n'''
if marker not in s:
    raise SystemExit('missing onPause marker')
s = s.replace(marker, method + marker, 1)
main.write_text(s)
