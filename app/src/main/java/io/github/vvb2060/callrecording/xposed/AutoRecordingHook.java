package io.github.vvb2060.callrecording.xposed;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Google Phone's native automatic-recording eligibility hook.
 *
 * Discovers the producer by its diagnostic string, verified signature and
 * called Future factory. Never uses a hard-coded obfuscated class name and
 * never clicks a recording button or modifies the user's contacts.
 *
 * This is an experimental policy hook: an upstream gate may still prevent
 * Google Phone from starting its built-in recorder.
 */
public final class AutoRecordingHook {
    private static final String TAG = "CallRecording/AutoAll";
    private static final String POLICY_MARKER = "produceShouldStartAutomaticRecordingForContact";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_HIT = new AtomicBoolean();
    private static volatile Method completedFutureFactory;

    private AutoRecordingHook() {}

    public static void install(DexHelper dex) {
        if (INSTALLED.get()) return;
        try {
            Method policy = discoverPolicy(dex);
            if (policy == null) {
                status("not_found", "No unique supported policy method");
                return;
            }
            Method factory = discoverCompletedFutureFactory(dex, policy);
            if (factory == null) {
                status("factory_missing", "No unique compatible immediate Future factory");
                return;
            }
            if (!INSTALLED.compareAndSet(false, true)) return;
            try {
                XposedBridge.hookMethod(policy, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!SettingsBridge.allCallsEnabled()) return;
                        try {
                            Object immediateTrue = factory.invoke(null, Boolean.TRUE);
                            if (!policy.getReturnType().isInstance(immediateTrue)) return;
                            param.setResult(immediateTrue);
                            RecordingTimingHook.markAutoPolicy();
                            if (FIRST_HIT.compareAndSet(false, true)) {
                                SettingsBridge.report("hit");
                                XposedBridge.log(TAG + ": first eligibility override");
                            }
                        } catch (Throwable error) {
                            // Any failure leaves Google Phone's original decision intact.
                            if (SettingsBridge.diagnosticsEnabled()) {
                                XposedBridge.log(TAG + ": cannot create completed Future: " + error);
                            }
                        }
                    }
                });
                completedFutureFactory = factory;
                status("installed", "Policy hooked: " + policy);
            } catch (Throwable error) {
                INSTALLED.set(false);
                throw error;
            }
        } catch (Throwable error) {
            status("error", "Discovery failed safely: " + error);
        }
    }

    /**
     * The Google Phone 244 start-disclosure continuation consumes Boolean,
     * not Void: Lkxn.apply (case 5) unboxes Boolean.booleanValue().
     *
     * Return only a verified completed Future<Boolean.TRUE>. A null result
     * causes the continuation to fail before CallRecorder.startRecording().
     */
    static Object completedTrueFuture() {
        Method factory = completedFutureFactory;
        if (factory == null) return null;
        try {
            Object value = factory.invoke(null, Boolean.TRUE);
            if (!(value instanceof Future<?>)) return null;
            Future<?> future = (Future<?>) value;
            if (!future.isDone() || !Boolean.TRUE.equals(future.get())) return null;
            return value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method discoverPolicy(DexHelper dex) {
        long[] matches = dex.findMethodUsingString(
                POLICY_MARKER, false, -1, (short) 1, null, -1,
                null, null, null, false);
        Set<Method> valid = new LinkedHashSet<>();
        for (long index : matches) {
            Member member = dex.decodeMethodIndex(index);
            if (!(member instanceof Method)) continue;
            Method method = (Method) member;
            if (method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != Object.class) continue;
            if (!Future.class.isAssignableFrom(method.getReturnType())) continue;
            valid.add(method);
        }
        return valid.size() == 1 ? valid.iterator().next() : null;
    }

    private static Method discoverCompletedFutureFactory(DexHelper dex, Method policy) {
        long policyIndex = dex.encodeMethodIndex(policy);
        // DexHelper.findMethodInvoking returns methods invoked BY policy.
        long[] callees = dex.findMethodInvoking(policyIndex, -1, (short) 1,
                null, -1, null, null, null, false);
        Set<Method> factories = new LinkedHashSet<>();
        for (long index : callees) {
            Member member = dex.decodeMethodIndex(index);
            if (!(member instanceof Method)) continue;
            Method method = (Method) member;
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != Object.class) continue;
            if (!policy.getReturnType().isAssignableFrom(method.getReturnType())) continue;
            factories.add(method);
        }
        if (factories.size() != 1) return null;
        Method factory = factories.iterator().next();
        try {
            factory.setAccessible(true);
            Object sample = factory.invoke(null, Boolean.TRUE);
            if (!policy.getReturnType().isInstance(sample)
                    || !(sample instanceof Future<?>)) return null;
            Future<?> future = (Future<?>) sample;
            if (!future.isDone() || !Boolean.TRUE.equals(future.get())) return null;
            return factory;
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": rejected factory: " + error);
            return null;
        }
    }

    private static void status(String code, String message) {
        XposedBridge.log(TAG + ": " + message);
        SettingsBridge.report(code);
    }
}
