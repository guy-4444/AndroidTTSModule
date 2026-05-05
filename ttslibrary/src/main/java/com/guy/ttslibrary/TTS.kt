package com.guy.ttslibrary

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Simple reusable Android Text-To-Speech wrapper.
 *
 * Usage:
 *   TTS.init(applicationContext)
 *   TTS.setLocale(Locale("he", "IL"))
 *   TTS.speak("שלום")
 *   TTS.shutdown()
 */
object TTS {

    private const val ACTION_TTS_SETTINGS_FALLBACK = "com.android.settings.TTS_SETTINGS"

    enum class QueueMode(val androidValue: Int) {
        FLUSH(TextToSpeech.QUEUE_FLUSH),
        ADD(TextToSpeech.QUEUE_ADD)
    }

    data class TtsConfig(
        val locale: Locale = Locale.getDefault(),
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
        val volume: Float = 1.0f,
        val pan: Float = 0.0f,
        val queueMode: QueueMode = QueueMode.FLUSH
    )

    interface Listener {
        fun onReady() {}
        fun onError(message: String) {}
        fun onStart(utteranceId: String) {}
        fun onDone(utteranceId: String) {}
        fun onSpeakError(utteranceId: String) {}
    }

    private var tts: TextToSpeech? = null
    private var appContext: Context? = null
    private var listener: Listener? = null

    private var initialized = false
    private var initializing = false
    private var pendingLocale: Locale? = null

    private var config = TtsConfig()

    fun init(
        context: Context,
        initialConfig: TtsConfig = TtsConfig(),
        listener: Listener? = null
    ) {
        val safeContext = context.applicationContext

        this.appContext = safeContext
        this.listener = listener
        this.config = initialConfig
        this.pendingLocale = initialConfig.locale

        if (initialized || initializing) return

        initializing = true
        tts = TextToSpeech(safeContext) { status ->
            initializing = false

            if (status == TextToSpeech.SUCCESS) {
                initialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        TTS.listener?.onStart(utteranceId)
                    }

                    override fun onDone(utteranceId: String) {
                        TTS.listener?.onDone(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        TTS.listener?.onSpeakError(utteranceId)
                    }

                    override fun onError(utteranceId: String, errorCode: Int) {
                        TTS.listener?.onSpeakError(utteranceId)
                    }
                })

                applyConfig(config)
                TTS.listener?.onReady()
            } else {
                initialized = false
                TTS.listener?.onError("TextToSpeech initialization failed. Status=$status")
            }
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun isReady(): Boolean = initialized && tts != null

    fun speak(
        text: CharSequence,
        queueMode: QueueMode = config.queueMode,
        utteranceId: String = UUID.randomUUID().toString(),
        volume: Float = config.volume,
        pan: Float = config.pan
    ): Boolean {
        if (text.isBlank()) {
            listener?.onError("Cannot speak empty text.")
            return false
        }

        val engine = tts
        if (!initialized || engine == null) {
            listener?.onError("TTS is not ready. Call TTS.init(context) before speak().")
            return false
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
            putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan.coerceIn(-1f, 1f))
        }

        val result = engine.speak(text, queueMode.androidValue, params, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    fun speakAdd(text: CharSequence): Boolean {
        return speak(text, QueueMode.ADD)
    }

    fun stop(): Boolean {
        return tts?.stop() == TextToSpeech.SUCCESS
    }

    fun setLocale(locale: Locale): Boolean {
        config = config.copy(locale = locale)
        pendingLocale = locale

        val engine = tts ?: return false
        if (!initialized) return false

        val availability = engine.isLanguageAvailable(locale)
        if (
            availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            listener?.onError("Locale not supported or missing data: ${locale.toLanguageTag()}")
            return false
        }

        val result = engine.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun getLocale(): Locale = config.locale

    fun setSpeechRate(rate: Float): Boolean {
        val safeRate = rate.coerceIn(0.1f, 4.0f)
        config = config.copy(speechRate = safeRate)
        return tts?.setSpeechRate(safeRate) == TextToSpeech.SUCCESS
    }

    fun getSpeechRate(): Float = config.speechRate

    fun setPitch(pitch: Float): Boolean {
        val safePitch = pitch.coerceIn(0.1f, 4.0f)
        config = config.copy(pitch = safePitch)
        return tts?.setPitch(safePitch) == TextToSpeech.SUCCESS
    }

    fun getPitch(): Float = config.pitch

    fun setVolume(volume: Float) {
        config = config.copy(volume = volume.coerceIn(0f, 1f))
    }

    fun getVolume(): Float = config.volume

    fun setPan(pan: Float) {
        config = config.copy(pan = pan.coerceIn(-1f, 1f))
    }

    fun getPan(): Float = config.pan

    fun setAudioAttributes(
        usage: Int = AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH
    ): Boolean {
        val engine = tts ?: return false

        val attrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
            .build()

        return engine.setAudioAttributes(attrs) == TextToSpeech.SUCCESS
    }

    fun isLanguageSupported(locale: Locale): Boolean {
        val engine = tts ?: return false
        val availability = engine.isLanguageAvailable(locale)
        return availability != TextToSpeech.LANG_MISSING_DATA &&
                availability != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun getAvailableLocales(): Set<Locale> {
        return tts?.availableLanguages.orEmpty()
    }

    /**
     * Opens the direct TTS settings screen on many Android devices.
     * If the direct settings screen is not exposed by the device, falls back to Accessibility settings.
     */
    fun openTtsSettings() {
        val context = appContext ?: return

        val directIntent = Intent(ACTION_TTS_SETTINGS_FALLBACK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val packageManager = context.packageManager
        val finalIntent = if (directIntent.resolveActivity(packageManager) != null) {
            directIntent
        } else {
            fallbackIntent
        }

        context.startActivity(finalIntent)
    }

    /**
     * Optional helper if you want to send the user to install/check TTS voice data.
     * Usually not needed unless setLocale(...) reports missing data.
     */
    fun createCheckTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        initializing = false
        appContext = null
        listener = null
    }

    private fun applyConfig(config: TtsConfig) {
        setLocale(pendingLocale ?: config.locale)
        setSpeechRate(config.speechRate)
        setPitch(config.pitch)
        setVolume(config.volume)
        setPan(config.pan)
        setAudioAttributes()
    }
}