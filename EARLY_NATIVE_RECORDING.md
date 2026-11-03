# Experimental early recording while keeping Google Phone's start notification

## Why

On Google Phone 244, LSPosed timing logs measured the following after the
Telecom call became active:

| Stage | Time after connected |
| --- | ---: |
| automatic recording policy accepted | 323 ms |
| recording disclosure requested | 335 ms |
| recorder start requested | 1723 ms |

The measured gap between requesting the original notice and starting the
recorder was **1388 ms**. The phone's normal beep setting did not shorten it
during the user's test.

## New switch (default OFF)

The module now includes **Start recording without waiting for the disclosure**
(Chinese: 接通后尽早开始录音（实验性）).

Only for newly connected eligible *automatic* calls, the module:
1. Allows Google's original no-argument `playStartDisclosure` future method
   to run, so the start notification is still requested normally.
2. Does not wait for that future to finish before the existing native
   recording-start continuation is scheduled.
3. Returns a verified completed native Future<Void> from the module's already
   discovered Future factory, preserving the expected return type.

It does **not** invoke the recorder manually and does not hide Google Phone's
recording indicator. The original asynchronous disclosure may overlap with the
beginning of the saved recording. Since the upstream module already modifies
TTS behavior, users must confirm the start notice is audible for their phone.

Activation is restricted to an active call with an automatic recording policy
hit in the last 2500 ms. Otherwise the original Google Phone flow is unchanged.

**Important:** This is experimental, not an exact 'start at connected = 0 ms'
guarantee. The automatic recording policy itself took 323 ms in the measured
call, and audio capture initialization occurs after the recorder's start
request.

## Testing

1. Install the debug APK and enable Google Phone scope in LSPosed.
2. In Google Phone choose its normal native beep start disclosure if available.
3. Place a call with a consenting tester and confirm the beep is still audible.
4. In the module's settings turn on **Automatically record all calls** and
   **Start recording without waiting for the disclosure**.
5. Restart Google Phone's process and make an outgoing and incoming test call.
6. Compare whether the saved audio includes the first second of conversation.
7. Read module log in LSPosed; expected:
   - `automatic-policy-accepted`
   - `disclosure-requested`
   - `disclosure-continues-recorder-released-early`
   - `native-recorder-start-requested`

If playback/recording fails or the notice is no longer audible, **turn the
experimental switch off**. This immediately restores the original wait on
subsequent calls, without uninstalling the module.

## Compatibility

Current discovery is designed for the inspected Google Phone 244.0.1001930441
APK. Unknown structures or missing methods fall back to original behavior.
The module retains the existing native recording feature hooks and caller
eligibility logic.
