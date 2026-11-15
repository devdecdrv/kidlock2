package com.kidlock

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

object AppBlocker {

    private const val PREFS = "kidlock_blocked"
    private val handler = Handler(Looper.getMainLooper())
    private var monitoring = false

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun block(context: Context, packageName: String) {
        prefs(context).edit().putBoolean(packageName, true).apply()
        startMonitoring(context)
    }

    fun unblock(context: Context, packageName: String) {
        prefs(context).edit().remove(packageName).apply()
    }

    fun startMonitoring(context: Context) {
        if (monitoring) return
        monitoring = true
        check(context)
    }

    private fun check(context: Context) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
        val current = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
        val blocked = prefs(context).all.keys

        if (current in blocked) {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(home)
        }
        handler.postDelayed({ check(context) }, 1000)
    }
}
