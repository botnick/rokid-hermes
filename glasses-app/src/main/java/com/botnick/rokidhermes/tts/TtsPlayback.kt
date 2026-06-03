package com.botnick.rokidhermes.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS playback through the Rokid AI Glasses built-in speakers, using Android's
 * built-in [TextToSpeech] engine. Speaks Hermes replies aloud.
 *
 * [TextToSpeech] initialises asynchronously, so a reply can arrive before the
 * engine is ready. We queue the latest pending utterance and flush it from
 * onInit, and never silently drop the first reply. [onInitResult] reports
 * success/failure once init completes so the UI can surface a "voice unavailable"
 * notice instead of failing silently — playback failure never blocks the chat.
 */
class TtsPlayback(
    context: Context,
    private val onInitResult: (Boolean) -> Unit = {}
) {

    private var ready = false
    private var failed = false
    private var pending: String? = null

    // lateinit lets the onInit callback reference `engine` safely — it only runs
    // after construction, by which point the field is assigned.
    private lateinit var engine: TextToSpeech

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engine.language = Locale.getDefault()
                pending?.let { text ->
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                    pending = null
                }
            } else {
                failed = true
            }
            onInitResult(!failed)
        }
    }

    val isFailed: Boolean get() = failed

    /** Speaks [text], interrupting anything currently playing. Queues if not ready yet. */
    fun speak(text: String) {
        if (failed || text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pending = null
        if (ready) engine.stop()
    }

    fun shutdown() {
        pending = null
        try {
            if (ready) engine.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        engine.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "hermes-reply"
    }
}
