from pathlib import Path

p = Path('app/src/main/java/com/abosultan/darbakmaps/data/LegacyMigration.java')
s = p.read_text(encoding='utf-8')
# Recover a previously interrupted APPLYING transaction before accepting another archive.
s = s.replace('''    public static Result importBackup(Context context, Uri uri) throws IOException {\n        if (context == null || uri == null) throw new IOException("ملف الانتقال غير صالح");\n''', '''    public static Result importBackup(Context context, Uri uri) throws IOException {\n        if (context == null || uri == null) throw new IOException("ملف الانتقال غير صالح");\n        recoverInterrupted(context);\n''', 1)
start = s.index('    private static Result applyAtomically(Context context, Staged staged) throws IOException {')
end = s.index('\n    private static void validateStagedTracks', start)
replacement = r'''    private static final String TXN_DIR = ".migration-transaction";
    private static final String TXN_STATE = "state.txt";
    private static final String TXN_PREFS = "prefs.manifest";
    private static final String TXN_TRACKS = "tracks.manifest";

    /** Called at startup/import. An APPLYING transaction is rolled back before repositories open. */
    public static void recoverInterrupted(Context context) throws IOException {
        File txn = transactionDir(context);
        if (!txn.exists()) return;
        String state = readFirstLine(new File(txn, TXN_STATE));
        DataStoreLock.lock();
        try {
            if ("COMMITTED".equals(state)) {
                deleteTree(txn);
                return;
            }
            rollbackFromTransaction(context, txn);
            deleteTree(txn);
        } finally {
            DataStoreLock.unlock();
        }
    }

    private static Result applyAtomically(Context context, Staged staged) throws IOException {
        DataStoreLock.lock();
        File tracksDir = new File(context.getFilesDir(), "tracks");
        File txn = transactionDir(context);
        int restoredTracks = 0;
        try {
            if (txn.exists()) throw new IOException("توجد عملية استعادة سابقة تحتاج تعافيًا");
            if (!tracksDir.exists() && !tracksDir.mkdirs()) throw new IOException("تعذر إنشاء مجلد المسارات");
            File prefBackup = new File(txn, "prefs");
            if (!prefBackup.mkdirs()) throw new IOException("تعذر تجهيز سجل تراجع دائم للاستعادة");
            writeTextSync(new File(txn, TXN_STATE), "APPLYING\n", false);

            // Persist every original preference file before the first live mutation.
            StringBuilder prefManifest = new StringBuilder();
            for (StagedPreference pref : staged.preferences) {
                prefManifest.append(pref.name).append('\n');
                File live = sharedPrefsFile(context, pref.name);
                File backup = new File(prefBackup, pref.name + ".xml");
                if (live.isFile()) copyFile(live, backup);
                else writeTextSync(new File(prefBackup, pref.name + ".absent"), "absent\n", false);
            }
            writeTextSync(new File(txn, TXN_PREFS), prefManifest.toString(), false);
            writeTextSync(new File(txn, TXN_TRACKS), "", false);

            for (StagedTrack stagedTrack : staged.trackFiles) {
                File target;
                if ("active-track.csv".equals(stagedTrack.originalName)) {
                    target = uniqueTrackTarget(tracksDir, "مسار-مستعاد-قديم.gpx");
                    appendTrackManifest(txn, target.getName());
                    TrackJournal.export(stagedTrack.file, target, "مسار مستعاد من نسخة قديمة");
                } else {
                    target = uniqueTrackTarget(tracksDir, stagedTrack.originalName);
                    appendTrackManifest(txn, target.getName());
                    File pending = new File(target.getAbsolutePath() + ".migration-pending");
                    copyFile(stagedTrack.file, pending);
                    if (!pending.renameTo(target)) {
                        pending.delete();
                        throw new IOException("تعذر تثبيت ملف مسار أثناء الاستعادة");
                    }
                }
                restoredTracks++;
            }

            for (StagedPreference pref : staged.preferences) replacePreferences(context, pref.name, pref.values);

            // COMMITTED is fsync'ed before evidence is removed. A crash after this point keeps new data.
            writeTextSync(new File(txn, TXN_STATE), "COMMITTED\n", false);
            int places = new PlaceRepository(context).all().size();
            int gpx = TrackStorage.list(context).length;
            deleteTree(txn);
            return new Result(staged.preferences.size(), restoredTracks, places, gpx);
        } catch (Exception error) {
            IOException rollbackError = null;
            try { rollbackFromTransaction(context, txn); } catch (IOException e) { rollbackError = e; }
            if (rollbackError == null) deleteTree(txn);
            if (rollbackError != null) {
                IOException combined = new IOException("فشلت الاستعادة وتعذر التراجع الكامل؛ سيحاول دربك التعافي عند التشغيل التالي", error);
                combined.addSuppressed(rollbackError);
                throw combined;
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("فشلت الاستعادة وتمت إعادة البيانات الأصلية", error);
        } finally {
            DataStoreLock.unlock();
        }
    }

    private static void rollbackFromTransaction(Context context, File txn) throws IOException {
        if (txn == null || !txn.exists()) return;
        IOException first = null;
        File prefBackup = new File(txn, "prefs");
        for (String name : readLines(new File(txn, TXN_PREFS))) {
            if (name.isEmpty() || name.contains("/") || name.contains("\\")) continue;
            try {
                File backup = new File(prefBackup, name + ".xml");
                File absent = new File(prefBackup, name + ".absent");
                if (backup.isFile()) {
                    replacePreferences(context, name, parsePreferences(readFile(backup, MAX_PREF_BYTES)));
                } else if (absent.isFile()) {
                    if (!context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) {
                        throw new IOException("تعذر إعادة إعدادات سابقة فارغة");
                    }
                }
            } catch (IOException error) { if (first == null) first = error; }
        }
        File tracksDir = new File(context.getFilesDir(), "tracks");
        String root;
        try { root = tracksDir.getCanonicalPath() + File.separator; }
        catch (IOException error) { if (first == null) first = error; root = ""; }
        for (String name : readLines(new File(txn, TXN_TRACKS))) {
            if (name.isEmpty() || name.contains("/") || name.contains("\\")) continue;
            try {
                File target = new File(tracksDir, name);
                if (!root.isEmpty() && target.getCanonicalPath().startsWith(root) && target.exists() && !target.delete() && first == null) {
                    first = new IOException("تعذر حذف ملف استعادة جزئي");
                }
            } catch (IOException error) { if (first == null) first = error; }
        }
        if (first != null) throw first;
    }

    private static File transactionDir(Context context) { return new File(context.getFilesDir(), TXN_DIR); }

    private static File sharedPrefsFile(Context context, String name) {
        return new File(new File(context.getApplicationInfo().dataDir, "shared_prefs"), name + ".xml");
    }

    private static void appendTrackManifest(File txn, String name) throws IOException {
        writeTextSync(new File(txn, TXN_TRACKS), name + "\n", true);
    }

    private static void writeTextSync(File file, String text, boolean append) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("تعذر تجهيز سجل الاستعادة");
        try (FileOutputStream out = new FileOutputStream(file, append)) {
            out.write(text.getBytes("UTF-8"));
            out.getFD().sync();
        }
    }

    private static String readFirstLine(File file) throws IOException {
        if (!file.isFile()) return "";
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); return line == null ? "" : line.trim();
        }
    }

    private static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        if (!file.isFile()) return lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line; while ((line = reader.readLine()) != null) lines.add(line.trim());
        }
        return lines;
    }

    private static byte[] readFile(File file, long max) throws IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER]; long total = 0L; int read;
            while ((read = in.read(buffer)) != -1) {
                total += read; if (total > max) throw new IOException("نسخة إعدادات التراجع تجاوزت الحد الآمن");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')

# Startup must recover before opening repositories.
p = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = p.read_text(encoding='utf-8')
old = '''        startupPhase = "فتح البيانات المحلية";\n        placeRepository = new PlaceRepository(this);\n'''
new = '''        startupPhase = "استعادة اتساق البيانات";\n        try { LegacyMigration.recoverInterrupted(this); }\n        catch (java.io.IOException recoveryError) { throw new IllegalStateException(recoveryError.getMessage(), recoveryError); }\n        startupPhase = "فتح البيانات المحلية";\n        placeRepository = new PlaceRepository(this);\n'''
if old not in s: raise SystemExit('startup repository pattern missing')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Pure crash-state model test documents the intended transaction semantics without claiming Android process-death execution.
Path('app/src/test/java/com/abosultan/darbakmaps/data/MigrationTransactionSemanticsTest.java').write_text(r'''package com.abosultan.darbakmaps.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationTransactionSemanticsTest {
    @Test public void applyingRequiresRollbackWhileCommittedKeepsNewState() {
        assertTrue(needsRollback("APPLYING"));
        assertTrue(needsRollback(""));
        assertFalse(needsRollback("COMMITTED"));
    }
    private boolean needsRollback(String state) { return !"COMMITTED".equals(state); }
}
''', encoding='utf-8')
