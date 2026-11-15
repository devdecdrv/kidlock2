package com.kidlock

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.*

object AppUsageReporter {

    fun reportToTelegram(context: Context, botToken: String, chatId: String) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        val startTime = cal.timeInMillis

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            System.currentTimeMillis()
        )

        val appList = stats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(10)

        val totalMinutes = stats.sumOf { it.totalTimeInForeground } / 1000 / 60

        var msg = "📊 App Usage Today\nTotal: $totalMinutes min\n\n"
        for (a in appList) {
            val minutes = a.totalTimeInForeground / 1000 / 60
            msg += "• ${a.packageName}: $minutes min\n"
        }

        TelegramListener.instance.sendMessage(msg)
    }
}
