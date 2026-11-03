# CallRecording: Google Phone native auto-recording extension

This branch extends [vvb2060/CallRecording](https://github.com/vvb2060/CallRecording) v1.3 without replacing its LSPosed framework, native DexHelper, or original recording-unlock hooks.

## Features

- **Automatically record all calls**: default on, aims to cover all incoming/outgoing calls, saved contacts, and unsaved numbers, without maintaining a list of contacts.
- **Native policy hook**: locates the `produceShouldStartAutomaticRecordingForContact` producer via a DEX string and signature, then returns Google Phone's own completed `Future<Boolean.TRUE>`. Does not click the record button, capture audio itself, or modify contacts.
- **Settings UI**: launcher activity with two switches (automatic recording, diagnostics) and a hook-status summary; simplified Chinese and English.
- **Cross-process settings**: `ContentProvider` allows only this module UID and Google Phone UID to read settings / report installation status.
- **Fail-closed discovery**: when the marker, signature, or result factory cannot be verified unambiguously, leave Google Phone's original automatic-recording decision unchanged.

## What is unchanged

- Original `canRecordCall`, `withinCrosbyGeoFence`, `isCallRecordingCountry`, locale, and TTS hooks.
- Original native DEX scanner, Xposed entry point and LSPosed scope `com.google.android.dialer`.
- **No region/country selector**: original behavior retained.

## Getting started

1. Install a debug build from the GitHub Actions artifact.
2. Enable this module with `com.google.android.dialer` in LSPosed.
3. Open **Call Recording** from the launcher, enable **Automatically record all calls** (default enabled), then restart the Google Phone process.
4. Open the settings activity and refresh **Hook status**.
5. With a consenting test caller, check known contacts, unknown numbers, incoming/outgoing calls, and screen-off situations. Verify that Google Phone actually saves an audio file that contains both sides.

## Debug

```sh
adb logcat -d | grep 'CallRecording/AutoAll'
```

- `Policy hooked`: successfully identified the native eligibility method; **not** a successful recording.
- `first eligibility override`: Google Phone called that policy and the hook returned true.
- `No unique supported policy method` or `No unique compatible immediate Future factory`: upstream changed enough that the safe fallback was used.

## Compatibility and limitations

Analysis used the supplied Google Phone `244.0.1001930441` APK to verify that `Lkyy;->b(Object):Lajcq;` refers to `produceShouldStartAutomaticRecordingForContact`, `Lajcq;` implements `java.util.concurrent.Future`, and the producer calls the `Laiql;->z(Object):Lajcq;` immediate-result factory. No obfuscated names are embedded in the module.

The hook **does not guarantee successful auto-recording on every version**: the producer may be gated by earlier flags or by device/system recorder permissions, or be renamed in a newer release. Test both hook execution and the saved audio. No root daemon or alternate audio recorder is created.

**Privacy and consent:** the original upstream module also has recording-announcement/TTS behavior. Review how that interacts with notice requirements in your jurisdiction; inform participants as appropriate before recording.

## Build

The repository has a GitHub Actions build matrix (Linux, Windows and macOS) running `./gradlew assemble`. The new UI uses only Android platform widgets; no extra Gradle dependencies are required.
