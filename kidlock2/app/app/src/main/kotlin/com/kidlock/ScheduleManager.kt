package com.kidlock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.*

class ScheduleManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = context.getSharedPreferences("kidlock_schedule", Context.MODE_PRIVATE)

    companion object {
        fun setSchedule(context: Context, data: String) {
            context.getSharedPreferences("kidlock_schedule", Context.MODE_PRIVATE)
                .edit().putString("schedule", data).apply()
        }

        fun setBedtime(context: Context, time: String) {
            context.getSharedPreferences("kidlock_schedule", Context.MODE_PRIVATE)
                .edit().putString("bedtime", time).apply()
        }
    }

    fun startChecking() {
        check()
    }

    private fun check() {
        val now = Calendar.getInstance()
        val currentTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var shouldLock = false

        prefs.getString("bedtime", null)?.let { bt ->
            val parts = bt.split(":")
            if (parts.size == 2) {
                val btTotal = parts[0].toInt() * 60 + parts[1].toInt()
                if (currentTotal >= btTotal) shouldLock = true
            }
        }

        prefs.getString("schedule", null)?.let { sch ->
            val parts = sch.split("-")
            if (parts.size == 2) {
                val s = parts[0].split(":")
                val e = parts[1].split(":")
                if (s.size == 2 && e.size == 2) {
                    val start = s[0].toInt() * 60 + s[1].toInt()
                    val end = e[0].toInt() * 60 + e[1].toInt()
                    if (currentTotal < start || currentTotal > end) shouldLock = true
                }
            }
        }

        if (shouldLock && LockActivity.instance == null) {
            val i = Intent(context, LockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }

        handler.postDelayed({ check() }, 60_000)
    }
}
