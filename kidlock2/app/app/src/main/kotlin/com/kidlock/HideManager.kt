package com.kidlock

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object HideManager {
    fun hideIcon(context: Context) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
