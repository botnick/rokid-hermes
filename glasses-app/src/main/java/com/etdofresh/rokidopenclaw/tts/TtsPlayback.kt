package com.etdofresh.rokidopenclaw.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS playback through the Rokid AI Glasses built-in speakers, using Android's
 * built-in [TextToSpeech] engine. Speaks Hermes replies aloud.
 */
class TtsPlayback(context: Context) {

    private var ready = false

    // Don't reference this field inside its own init lambda — set the language
    // lazily in speak() instead (cheap and idempotent).
    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /** Speaks [text], interrupting anything currently playing. */
    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        engine.language = Locale.getDefault()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        if (ready) engine.stop()
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "hermes-reply"
    }
}
