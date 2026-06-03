package com.botnick.rokidhermes.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS playback through the Rokid AI Glasses built-in speakers, using Android's
 * built-in [TextToSpeech] engine. Speaks Hermes replies aloud.
 *
 * Language is chosen dynamically PER REPLY: text containing Thai is spoken with a
 * Thai voice, otherwise with [speak]'s preferred locale. The setLanguage result
 * is checked, so a missing voice surfaces a notice via [onNotice] (null clears it)
 * instead of speaking garbled audio. Playback failure never blocks the chat —
 * the reply text is always on the HUD regardless.
 */
class TtsPlayback(
    context: Context,
    private val onNotice: (String?) -> Unit = {}
) {

    private var ready = false
    private var failed = false
    private var pending: Pair<String, Locale>? = null

    // lateinit lets the onInit callback reference `engine` safely — it only runs
    // after construction, by which point the field is assigned.
    private lateinit var engine: TextToSpeech

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                pending?.let { (text, locale) ->
                    doSpeak(text, locale)
                    pending = null
                }
            } else {
                failed = true
                onNotice("Voice replies unavailable — text only")
            }
        }
    }

    /**
     * Speaks [text], interrupting anything playing. [preferred] is the voice locale
     * used when the text isn't detectably Thai. Queues if the engine isn't ready yet.
     */
    fun speak(text: String, preferred: Locale = Locale.getDefault()) {
        if (failed || text.isBlank()) return
        val target = if (hasThai(text)) THAI else preferred
        if (!ready) {
            pending = text to target
            return
        }
        doSpeak(text, target)
    }

    private fun doSpeak(text: String, target: Locale) {
        val result = engine.setLanguage(target)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val name = if (target.language == "th") "Thai" else target.displayLanguage
            onNotice("No $name voice installed — text only")
            return
        }
        onNotice(null)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun hasThai(s: String): Boolean = s.any { it.code in 0x0E00..0x0E7F }

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
        val THAI: Locale = Locale("th", "TH")
    }
}
