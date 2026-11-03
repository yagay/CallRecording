package io.github.vvb2060.callrecording.xposed;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Optional and experimental: run native recorder startup in parallel with
 * Google Phone's original start-recording disclosure, instead of waiting for
 * the disclosure future to finish.
 *
 * The ORIGINAL disclosure method is executed in full. Only its returned
 * future is changed, and only for a newly connected, eligible automatic call.
 * Never attempts to call private recorder.startRecording() manually.
 *
 * Expected to remove the ~1388ms disclosure wait seen in Phone 244 logs; not
 * a guarantee of capture from the exact millisecond of connection.
 */
final class EarlyRecordingHook {
    private static final String TAG = "CallRecording/Early";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicLong LAST_RELEASED_POLICY = new AtomicLong(Long.MIN_VALUE);

    private EarlyRecordingHook() {}

    static void install(Method disclosure) {
        if (disclosure == null || INSTALLED.get()) return;
        if (!Future.class.isAssignableFrom(disclosure.getReturnType())
                || disclosure.getParameterCount() != 0) {
            XposedBridge.log(TAG + ": disclosure method is incompatible, original behavior retained");
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            XposedBridge.hookMethod(disclosure, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    boolean earlyEnabled = SettingsBridge.earlyRecordingEnabled();
                    boolean autoEnabled = SettingsBridge.allCallsEnabled();
                    if (!earlyEnabled || !autoEnabled || param.hasThrowable()) {
                        XposedBridge.log(TAG + ": disclosure hook not applied; earlyEnabled="
                                + earlyEnabled + " autoEnabled=" + autoEnabled
                                + " originalThrew=" + param.hasThrowable());
                        return;
                    }
                    try {
                        long policy = RecordingTimingHook.currentEarlyPolicyTimestamp();
                        if (policy <= 0 || LAST_RELEASED_POLICY.get() == policy) {
                            XposedBridge.log(TAG + ": disclosure hook not applied; active-policy-window-or-duplicate");
                            return;
                        }

                        Object original = param.getResult();
                        if (!(original instanceof Future<?>)) {
                            XposedBridge.log(TAG + ": disclosure original result not a Future");
                            return;
                        }
                        if (((Future<?>) original).isDone()) {
                            XposedBridge.log(TAG + ": disclosure already completed; no replacement needed");
                            return;
                        }

                        // Google Phone 244's next transform unboxes a Boolean.
                        // The previous Future<Void>/null replacement stopped
                        // the recording chain: no Google recording was saved.
                        // Reuse the verified native completed Boolean.TRUE.
                        Object ready = AutoRecordingHook.completedTrueFuture();
                        if (!disclosure.getReturnType().isInstance(ready)) {
                            XposedBridge.log(TAG + ": completed Boolean.TRUE Future unavailable or incompatible");
                            return;
                        }

                        if (!LAST_RELEASED_POLICY.compareAndSet(
                                LAST_RELEASED_POLICY.get(), policy)) return;

                        // Original playback continues asynchronously.
                        // Only the dependency chain can advance immediately.
                        param.setResult(ready);
                        RecordingTimingHook.markEarlyRelease();
                        XposedBridge.log(TAG + ": native Boolean.TRUE continuation released; original notice still running");
                    } catch (Throwable e) {
                        // Fail closed. No change to Google's original Future.
                        Log.w(TAG, "Early release refused, original flow kept", e);
                        XposedBridge.log(TAG + ": early release failed, original flow kept: " + e);
                    }
                }
            });
            XposedBridge.log(TAG + ": parallel native recording hook installed (default OFF)");
        } catch (Throwable e) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": failed to install, original recording flow kept: " + e);
        }
    }
}
