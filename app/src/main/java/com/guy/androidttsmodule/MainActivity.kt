package com.guy.androidttsmodule

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guy.ttslibrary.TTS
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var inputText: EditText
    private lateinit var localeSpinner: Spinner
    private lateinit var speedSeekBar: SeekBar
    private lateinit var pitchSeekBar: SeekBar
    private lateinit var speedValue: TextView
    private lateinit var pitchValue: TextView
    private lateinit var statusText: TextView

    private val locales = listOf(
        Locale("en", "US"),
        Locale("en", "GB"),
        Locale("he", "IL"),
        Locale("ar"),
        Locale("es", "ES"),
        Locale("fr", "FR")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupUi()
        setupTts()
    }

    private fun bindViews() {
        inputText = findViewById(R.id.inputText)
        localeSpinner = findViewById(R.id.localeSpinner)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        pitchSeekBar = findViewById(R.id.pitchSeekBar)
        speedValue = findViewById(R.id.speedValue)
        pitchValue = findViewById(R.id.pitchValue)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.speakButton).setOnClickListener {
            val selectedLocale = locales[localeSpinner.selectedItemPosition]
            TTS.setLocale(selectedLocale)

            val text = inputText.text?.toString().orEmpty()
            TTS.speak(text)
        }

        findViewById<Button>(R.id.addButton).setOnClickListener {
            val selectedLocale = locales[localeSpinner.selectedItemPosition]
            TTS.setLocale(selectedLocale)

            val text = inputText.text?.toString().orEmpty()
            TTS.speakAdd(text)
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            TTS.stop()
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            TTS.openTtsSettings()
        }
    }

    private fun setupUi() {
        inputText.setText("Hello, this is a sentence.")

        val labels = locales.map { it.toLanguageTag() }
        localeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )

        speedSeekBar.progress = 10
        pitchSeekBar.progress = 10
        speedValue.text = "1.0x"
        pitchValue.text = "1.0x"

        speedSeekBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            val rate = progressToTtsValue(progress)
            speedValue.text = "${oneDecimal(rate)}x"
            TTS.setSpeechRate(rate)
        })

        pitchSeekBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            val pitch = progressToTtsValue(progress)
            pitchValue.text = "${oneDecimal(pitch)}x"
            TTS.setPitch(pitch)
        })
    }

    private fun setupTts() {
        TTS.init(
            context = applicationContext,
            initialConfig = TTS.TtsConfig(
                locale = Locale("en", "US"),
                speechRate = 1.0f,
                pitch = 1.0f
            ),
            listener = object : TTS.Listener {
                override fun onReady() {
                    runOnUiThread {
                        statusText.text = "TTS ready"
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        statusText.text = message
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStart(utteranceId: String) {
                    runOnUiThread {
                        statusText.text = "Speaking..."
                    }
                }

                override fun onDone(utteranceId: String) {
                    runOnUiThread {
                        statusText.text = "Done"
                    }
                }

                override fun onSpeakError(utteranceId: String) {
                    runOnUiThread {
                        statusText.text = "Speech error"
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        TTS.shutdown()
        super.onDestroy()
    }

    private fun progressToTtsValue(progress: Int): Float {
        // 0 -> 0.5, 10 -> 1.0, 30 -> 2.0
        return (0.5f + progress / 20f).coerceIn(0.5f, 2.0f)
    }

    private fun oneDecimal(value: Float): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun simpleSeekListener(onChanged: (progress: Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }
}
