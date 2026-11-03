package io.github.vvb2060.callrecording.xposed;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;

import java.lang.reflect.Method;

/** Reads settings from this module without exposing a world-readable prefs file. */
final class SettingsBridge {
    private static final Uri URI =
            Uri.parse("content://io.github.vvb2060.callrecording.settings");

    private SettingsBridge() {}

    static boolean allCallsEnabled() {
        Bundle options = read();
        return options != null && options.getBoolean("auto_record_all", false);
    }

    static boolean earlyRecordingEnabled() {
        Bundle options = read();
        return options != null && options.getBoolean("early_native_recording", false);
    }

    static boolean diagnosticsEnabled() {
        Bundle options = read();
        return options != null && options.getBoolean("diagnostic_logging", false);
    }

    static void report(String code) {
        try {
            Application application = currentApplication();
            if (application != null) {
                application.getContentResolver().call(URI, "report", code, null);
            }
        } catch (Throwable ignored) {
            // Never allow diagnostics to interfere with calls.
        }
    }

    private static Bundle read() {
        try {
            Application application = currentApplication();
            if (application == null) return null;
            return application.getContentResolver().call(URI, "read", null, null);
        } catch (Throwable ignored) {
            return null;
        }
    }
    private static Application currentApplication() {
        // The original Xposed API v82 artifact does not expose AndroidAppHelper
        // at compile time; find it through reflection when LSPosed provides it.
        try {
            Class<?> helper = Class.forName("de.robv.android.xposed.AndroidAppHelper");
            Object app = helper.getMethod("currentApplication").invoke(null);
            if (app instanceof Application) return (Application) app;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method method = activityThread.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object app = method.invoke(null);
            if (app instanceof Application) return (Application) app;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
