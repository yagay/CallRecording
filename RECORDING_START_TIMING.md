# Google Phone: diagnose missing audio after call connection

This is a **diagnostic build, not a recording-timing fix**. The existing native automatic recording and call announcements remain unchanged.

## Verified sequence in Google Phone 244

For the uploaded Phone_244.0.1001930441.apk, static DEX inspection found:

1. Telecom call becomes active (call connected)
2. Auto-record eligibility producer is accepted
3. Native start disclosure is requested (synthetic callback containing `playStartDisclosure`)
4. Only after the disclosure completes, native `CallRecorder.startRecording` requests audio capture

The module's existing auto-record eligibility hook does **not** skip steps 3–4.

## Diagnostic hook

- `android.telecom.Call.internalUpdate` after-call hook records the first observation of `STATE_ACTIVE`
- Native auto-recording eligibility hook marks the accepted-policy timestamp
- `playStartDisclosure`'s no-argument recorder method is dynamically identified through DEX call sites and records the disclosure request time
- A separate native recording method containing `TidepodsCallAudioImpl_createDownlinkAndUplinkTee` is dynamically identified and logs when the recorder start is requested
- Fail closed on method mismatches, never modify control flow, return values, audio files, or call details

Markers use `SystemClock.elapsedRealtime()`. Log lines report elapsed milliseconds since call activation, policy and disclosure; **no phone numbers or recordings**.

## Use

1. Install the Debug APK from the GitHub Actions artifact for PR diagnostics/recording-start-latency.
2. In LSPosed enable the module for Google Phone, restart the phone app's process.
3. Open Call Recording settings; turn **Diagnostic logging** ON (all-calls auto-recording can stay ON).
4. With an informed, consenting test caller, place one outgoing call and answer one incoming call, each starting with a few spoken words immediately after connection.
5. Inspect saved audio and then capture logs:

```sh
adb logcat -d -v time | grep 'CallRecording/Timing'
```

Useful events:
- `telecom-connected` — first active Telecom call state observed
- `automatic-policy-accepted` — our auto record eligibility override ran
- `disclosure-requested` — Google Phone requested the native recording notice
- `native-recorder-start-requested` — Google Phone finally invoked native recorder startup
- `native-recorder-start-future-returned` — the method returned its asynchronous result; audio capture may still initialize afterwards

If a stage is missing, report it; the native method may have been changed or the call may take a different path. If recorder startup is *already* immediate but the file still cuts off, the delay is likely in audio capture initialization rather than the notice.

## Next step after diagnostics

Prioritize a **short, native beep disclosure** if Google Phone exposes that option and the user accepts it. Google Phone 244 includes `CALL_RECORDING_DISCLOSURE_BEEP_SOUND_AVAILABLE`, a start-beep OGG asset, and a settings option for preferred disclosure type. That may reduce pre-recording delay but isn't guaranteed; do not silently bypass notice or move native recorder calls ahead of Google's state guards. Only change behavior after verifying these timestamps and test files.
