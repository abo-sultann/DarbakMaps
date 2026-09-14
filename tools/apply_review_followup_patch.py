from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing pattern: {label}")
    return text.replace(old, new, 1)

main = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = main.read_text()
s = replace_once(s,
    'import com.abosultan.darbakmaps.data.GeoPoint;\n',
    'import com.abosultan.darbakmaps.data.GeoPoint;\nimport com.abosultan.darbakmaps.data.LegacyMigration;\n',
    'migration import')
s = replace_once(s,
    '    private static final int REQUEST_MAP_FILE = 702;\n',
    '    private static final int REQUEST_MAP_FILE = 702;\n    private static final int REQUEST_MIGRATION = 703;\n',
    'migration request code')

old = '''            runOnUiThread(() -> {\n                searchRunning = false;\n                if (isActivityUnavailable()) {\n                    return;\n                }\n                progress.dismiss();\n                showSearchResults(results);\n            });'''
new = '''            boolean complete = searchEngine.isComplete();\n            boolean failed = searchEngine.hasFailed();\n            runOnUiThread(() -> {\n                searchRunning = false;\n                if (isActivityUnavailable()) return;\n                progress.dismiss();\n                showSearchResults(results, complete, failed);\n            });'''
s = replace_once(s, old, new, 'search completion status')

old = '''    private void showSearchResults(List<OfflineMapSearchEngine.Result> results) {\n        if (results.isEmpty()) {\n            toast(MapStorage.activeMap(this).isFile()\n                    ? "لا توجد نتائج مطابقة داخل الخريطة"\n                    : "لا توجد نتائج؛ أضف خريطة دربك للبحث في المدن والمعالم");\n            return;\n        }'''
new = '''    private void showSearchResults(List<OfflineMapSearchEngine.Result> results, boolean complete, boolean failed) {\n        if (results.isEmpty()) {\n            if (!complete) {\n                toast("البحث ما زال يفهرس الخريطة؛ أعد البحث لاستكمال بقية المناطق");\n            } else if (failed) {\n                toast("اكتمل البحث جزئيًا وتعذر قراءة بعض أجزاء الخريطة");\n            } else {\n                toast(MapStorage.activeMap(this).isFile()\n                        ? "لا توجد نتائج مطابقة بعد اكتمال الفهرسة"\n                        : "لا توجد نتائج؛ أضف خريطة دربك للبحث في المدن والمعالم");\n            }\n            return;\n        }'''
s = replace_once(s, old, new, 'partial result method')
s = replace_once(s,
    '                .setTitle("نتائج البحث")\n',
    '                .setTitle(complete ? (failed ? "نتائج البحث — بعض أجزاء الخريطة تعذرت" : "نتائج البحث") : "نتائج جزئية — أعد البحث للاستكمال")\n',
    'partial result title')

marker = '''    private void importMap(Uri uri) {'''
migration_methods = '''    void chooseLegacyMigration() {\n        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);\n        intent.addCategory(Intent.CATEGORY_OPENABLE);\n        intent.setType("application/zip");\n        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);\n        startActivityForResult(intent, REQUEST_MIGRATION);\n    }\n\n    private void importLegacyMigration(Uri uri) {\n        ProgressDialog progress = new ProgressDialog(this);\n        progress.setTitle("انتقال بيانات دربك");\n        progress.setMessage("جارٍ التحقق والاستعادة…");\n        progress.setIndeterminate(true);\n        progress.setCancelable(false);\n        showImmersive(progress);\n        ioExecutor.execute(() -> {\n            try {\n                LegacyMigration.Result result = LegacyMigration.importBackup(this, uri);\n                runOnUiThread(() -> {\n                    if (isActivityUnavailable()) return;\n                    progress.dismiss();\n                    placeRepository = new PlaceRepository(this);\n                    MapRuntimeBridge.refreshSavedPlaces(this);\n                    loadActiveMap();\n                    showImmersive(new AlertDialog.Builder(this)\n                            .setTitle("تمت استعادة بيانات دربك")\n                            .setMessage("المواقع المحفوظة: " + result.savedPlaces\n                                    + "\\nملفات GPX: " + result.gpxTracks\n                                    + "\\nملفات الإعدادات: " + result.preferenceFiles\n                                    + "\\nملفات المسارات المستعادة: " + result.trackFiles)\n                            .setPositiveButton("حسنًا", null)\n                            .create());\n                });\n            } catch (Exception error) {\n                runOnUiThread(() -> {\n                    if (!isActivityUnavailable()) {\n                        progress.dismiss();\n                        toast(error.getMessage() == null ? "تعذر استعادة بيانات النسخة القديمة" : error.getMessage());\n                    }\n                });\n            }\n        });\n    }\n\n'''
if marker not in s:
    raise SystemExit('missing importMap marker')
s = s.replace(marker, migration_methods + marker, 1)

old = '''        } else if (requestCode == REQUEST_MAP_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {\n            Uri uri = data.getData();\n            try {\n                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);\n            } catch (SecurityException ignored) {\n                // Some file providers grant access only during this import operation.\n            }\n            importMap(uri);\n        }'''
new = '''        } else if (requestCode == REQUEST_MAP_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {\n            Uri uri = data.getData();\n            try {\n                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);\n            } catch (SecurityException ignored) {\n                // Some file providers grant access only during this import operation.\n            }\n            importMap(uri);\n        } else if (requestCode == REQUEST_MIGRATION && resultCode == RESULT_OK && data != null && data.getData() != null) {\n            importLegacyMigration(data.getData());\n        }'''
s = replace_once(s, old, new, 'migration activity result')
main.write_text(s)

panels = Path('app/src/main/java/com/abosultan/darbakmaps/DarbakPanels.java')
p = panels.read_text()
anchor = '''        TextView about = card(activity, "حول دربك", "الإصدار والهوية والتشخيص", () -> {'''
insert = '''        TextView migration = card(activity, "استعادة بيانات نسخة قديمة", "استخدم ZIP الذي أنشأته أداة الانتقال قبل إزالة النسخة القديمة", () -> {\n            dialog.dismiss();\n            if (activity instanceof MainActivity) ((MainActivity) activity).chooseLegacyMigration();\n            else Toast.makeText(activity, "افتح الاستعادة من الشاشة الرئيسية", Toast.LENGTH_SHORT).show();\n        });\n        LinearLayout.LayoutParams migrationParams = new LinearLayout.LayoutParams(-1, dp(activity, 82));\n        migrationParams.setMargins(dp(activity, 6), dp(activity, 6), dp(activity, 6), 0);\n        root.addView(migration, migrationParams);\n\n'''
if anchor not in p:
    raise SystemExit('missing DarbakPanels about anchor')
p = p.replace(anchor, insert + anchor, 1)
panels.write_text(p)
