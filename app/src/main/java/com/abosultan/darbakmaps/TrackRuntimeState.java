package com.abosultan.darbakmaps;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent recorder state/result so the UI can recover after Activity/process changes. */
public final class TrackRuntimeState {
    private static final String PREFS = "darbak_track_runtime";
    private static final String KEY_STATE = "state";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_RESULT_PENDING = "result_pending";
    private static final String KEY_RESTART = "restart_requested";
    private static final String KEY_WRITE_ERROR = "write_error";

    public static final String IDLE = "idle";
    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String FINALIZING = "finalizing";
    public static final String FAILED = "failed";

    private TrackRuntimeState() {}

    public static void setState(Context context, String state) {
        prefs(context).edit().putString(KEY_STATE, state).commit();
    }

    public static String state(Context context) {
        return prefs(context).getString(KEY_STATE, IDLE);
    }

    public static boolean isFinalizing(Context context) {
        return FINALIZING.equals(state(context));
    }

    public static void requestRestart(Context context) {
        prefs(context).edit().putBoolean(KEY_RESTART, true).commit();
    }

    public static boolean consumeRestart(Context context) {
        SharedPreferences p = prefs(context);
        boolean value = p.getBoolean(KEY_RESTART, false);
        if (value) p.edit().putBoolean(KEY_RESTART, false).commit();
        return value;
    }

    public static void setWriteError(Context context, String message) {
        prefs(context).edit()
                .putString(KEY_WRITE_ERROR, message == null ? "تعذر كتابة نقطة في المسار" : message)
                .putString(KEY_STATE, FAILED)
                .commit();
    }

    public static String writeError(Context context) {
        return prefs(context).getString(KEY_WRITE_ERROR, "");
    }

    public static void clearWriteError(Context context) {
        prefs(context).edit().remove(KEY_WRITE_ERROR).commit();
    }

    public static void recordFinalizeResult(Context context, boolean success, String message) {
        prefs(context).edit()
                .putBoolean(KEY_SUCCESS, success)
                .putString(KEY_MESSAGE, message == null ? "" : message)
                .putBoolean(KEY_RESULT_PENDING, true)
                .putString(KEY_STATE, success ? IDLE : FAILED)
                .commit();
    }

    public static Result peekResult(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_RESULT_PENDING, false)) return null;
        return new Result(p.getBoolean(KEY_SUCCESS, false), p.getString(KEY_MESSAGE, ""));
    }

    public static void clearResult(Context context) {
        prefs(context).edit().putBoolean(KEY_RESULT_PENDING, false).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
