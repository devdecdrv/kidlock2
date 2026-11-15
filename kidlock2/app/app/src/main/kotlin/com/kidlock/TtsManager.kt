package com.kidlock

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.*

object TtsManager {

    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(context: Context, text: String) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0
        )

        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.language = Locale("hi", "IN")
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kidlock_msg")
                }
            }
        } else if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kidlock_msg")
        }
    }
}
