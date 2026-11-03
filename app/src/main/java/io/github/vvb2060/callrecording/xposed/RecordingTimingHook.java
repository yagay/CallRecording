package io.github.vvb2060.callrecording.xposed;

import android.os.SystemClock;
import android.telecom.Call;
import android.util.Log;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Observes, but never changes, Google Phone's recording startup chronology.
 *
 * Uses the user's existing "Diagnostic logging" switch. No telephone
 * numbers, contacts, audio buffers or file paths are logged.
 *
 * Dynamic matching works with the supplied Phone 244 DEX and fails closed
 * when the Google Phone internal method structure changes.
 */
final class RecordingTimingHook {
    private static final String TAG = "CallRecording/Timing";
    private static final AtomicBoolean CALL_HOOKED = new AtomicBoolean();
    private static final AtomicBoolean RECORDER_HOOKED = new AtomicBoolean();

    private static volatile Call lastActiveCall;
    private static volatile long connectedAt;
    private static volatile long policyAt;
    private static volatile long disclosureAt;
    private static volatile long recordRequestAt;

    private RecordingTimingHook() {}

    static void installCallState() {
        if (!CALL_HOOKED.compareAndSet(false, true)) return;
        try {
            XposedBridge.hookAllMethods(Call.class, "internalUpdate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Call)) return;
                    Call call = (Call) param.thisObject;
                    int state = call.getState();
                    if (state == Call.STATE_ACTIVE && lastActiveCall != call) {
                        lastActiveCall = call;
                        connectedAt = SystemClock.elapsedRealtime();
                        policyAt = 0;
                        disclosureAt = 0;
                        recordRequestAt = 0;
                        event("telecom-connected");
                    } else if (lastActiveCall == call &&
                            (state == Call.STATE_DISCONNECTED ||
                             state == Call.STATE_DISCONNECTING)) {
                        event("telecom-disconnected");
                        lastActiveCall = null;
                    }
                }
            });
            event("call-state-hook-installed");
        } catch (Throwable error) {
            CALL_HOOKED.set(false);
            Log.w(TAG, "Cannot observe call-state transitions", error);
        }
    }

    static void markAutoPolicy() {
        policyAt = SystemClock.elapsedRealtime();
        event("automatic-policy-accepted");
    }

    static void installNativePipeline(DexHelper dex) {
        if (RECORDER_HOOKED.get()) return;
        try {
            Method record = uniqueRecorderMethod(dex);
            if (record == null) {
                event("recorder-method-unavailable");
                return;
            }
            Method disclose = uniqueDisclosureMethod(dex, record.getDeclaringClass());
            if (!RECORDER_HOOKED.compareAndSet(false, true)) return;

            if (disclose != null) {
                XposedBridge.hookMethod(disclose, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        disclosureAt = SystemClock.elapsedRealtime();
                        event("disclosure-requested");
                    }
                });
            } else {
                event("disclosure-method-unavailable");
            }

            XposedBridge.hookMethod(record, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    recordRequestAt = SystemClock.elapsedRealtime();
                    event("native-recorder-start-requested");
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    event("native-recorder-start-future-returned");
                }
            });
            event("recording-pipeline-hook-installed");
        } catch (Throwable error) {
            RECORDER_HOOKED.set(false);
            if (SettingsBridge.diagnosticsEnabled()) {
                Log.w(TAG, "Recording pipeline discovery failed", error);
            }
        }
    }

    private static Method uniqueRecorderMethod(DexHelper dex) {
        Set<Method> matched = new LinkedHashSet<>();
        for (long id : dex.findMethodUsingString(
                "TidepodsCallAudioImpl_createDownlinkAndUplinkTee",
                false, -1, (short) 1, null, -1, null, null, null, false)) {
            Member member = dex.decodeMethodIndex(id);
            if (!(member instanceof Method)) continue;
            Method method = (Method) member;
            if (method.getParameterTypes()[0] != long.class) continue;
            if (!Future.class.isAssignableFrom(method.getReturnType())) continue;
            matched.add(method);
        }
        return matched.size() == 1 ? matched.iterator().next() : null;
    }

    private static Method uniqueDisclosureMethod(DexHelper dex, Class<?> recorderClass) {
        Set<Method> matched = new LinkedHashSet<>();
        for (long callerId : dex.findMethodUsingString(
                "playStartDisclosure", false, -1, (short) 1, null,
                -1, null, null, null, false)) {
            Member caller = dex.decodeMethodIndex(callerId);
            if (!(caller instanceof Method)) continue;
            for (long calleeId : dex.findMethodInvoking(
                    dex.encodeMethodIndex(caller), -1, (short) 0, null,
                    -1, null, null, null, false)) {
                Member callee = dex.decodeMethodIndex(calleeId);
                if (!(callee instanceof Method)) continue;
                Method method = (Method) callee;
                if (method.getDeclaringClass() == recorderClass &&
                        method.getParameterCount() == 0 &&
                        Future.class.isAssignableFrom(method.getReturnType())) {
                    matched.add(method);
                }
            }
        }
        return matched.size() == 1 ? matched.iterator().next() : null;
    }

    private static void event(String name) {
        if (!SettingsBridge.diagnosticsEnabled()) return;
        long now = SystemClock.elapsedRealtime();
        String active = connectedAt == 0 ? "n/a" : String.valueOf(now - connectedAt);
        String fromPolicy = policyAt == 0 ? "n/a" : String.valueOf(now - policyAt);
        String fromDisclosure = disclosureAt == 0 ? "n/a" : String.valueOf(now - disclosureAt);
        Log.i(TAG, name + " elapsedMs=" + now
                + " sinceConnectedMs=" + active
                + " sincePolicyMs=" + fromPolicy
                + " sinceDisclosureMs=" + fromDisclosure);
    }
}
