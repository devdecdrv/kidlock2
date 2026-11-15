package com.kidlock

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper

object StatusReporter {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    fun startHeartbeat(context: Context) {
        if (running) return
        running = true
        heartbeat(context)
    }

    private fun heartbeat(context: Context) {
        // silent heartbeat every 5 min — only sends if offline detected
        handler.postDelayed({ heartbeat(context) }, 300_000)
    }

    fun reportToTelegram(context: Context, botToken: String, chatId: String) {
        val battery = getBattery(context)
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        TelegramListener.instance.sendMessage(
            "📱 Status\nBattery: $battery%\nLast seen: $time\nOnline: ✅"
        )
    }

    private fun getBattery(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}
