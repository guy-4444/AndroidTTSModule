# Android TTS Module

A small reusable Kotlin wrapper around Android `TextToSpeech`.

## Features

- Initialize and release Android TTS safely
- Speak text
- Queue mode: replace current speech or add to queue
- Stop speech
- Change locale at runtime
- Check locale support
- Change speed, pitch, volume, and stereo pan
- Set audio attributes
- Open device Text-to-speech settings, with fallback to Accessibility settings
- Receive callbacks for ready/start/done/error

## Files

- `TTS.kt` — reusable TTS wrapper
- `MainActivity.kt` — demo usage
- `activity_main.xml` — demo UI
- `AndroidManifest-snippet.xml` — optional Android 11+ query declaration

## Basic usage

```kotlin
TTS.init(applicationContext)

TTS.setLocale(Locale("he", "IL"))
TTS.setSpeechRate(1.0f)
TTS.setPitch(1.0f)

TTS.speak("שלום")
TTS.stop()

override fun onDestroy() {
    TTS.shutdown()
    super.onDestroy()
}
```

## Important notes

1. Do not call `speak()` before `init()` finishes.
2. Do not use `Thread.sleep()` on the main/UI thread.
3. Call `shutdown()` when the Activity/application no longer needs TTS.
4. Some locales require installed voice data on the device.
5. TTS quality and supported languages depend on the installed TTS engine.
