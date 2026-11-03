package io.github.vvb2060.callrecording.xposed;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.AndroidAppHelper;

/** Reads settings from this module without exposing a world-readable prefs file. */
final class SettingsBridge {
    private static final Uri URI =
            Uri.parse("content://io.github.vvb2060.callrecording.settings");

    private SettingsBridge() {}

    static boolean allCallsEnabled() {
        Bundle options = read();
        return options != null && options.getBoolean("auto_record_all", false);
    }

    static boolean diagnosticsEnabled() {
        Bundle options = read();
        return options != null && options.getBoolean("diagnostic_logging", false);
    }

    static void report(String code) {
        try {
            Application application = AndroidAppHelper.currentApplication();
            if (application != null) {
                application.getContentResolver().call(URI, "report", code, null);
            }
        } catch (Throwable ignored) {
            // Never allow diagnostics to interfere with calls.
        }
    }

    private static Bundle read() {
        try {
            Application application = AndroidAppHelper.currentApplication();
            if (application == null) return null;
            return application.getContentResolver().call(URI, "read", null, null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
