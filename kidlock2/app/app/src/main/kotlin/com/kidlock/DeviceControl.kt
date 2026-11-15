package com.kidlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

object DeviceControl {

    fun reboot(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, AdminReceiver::class.java)
            if (dpm.isAdminActive(admin) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.reboot(admin)
            }
        } catch (e: Exception) {
            // fallback root
            try { Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot")) } catch (ex: Exception) {}
        }
    }

    fun shutdown(context: Context) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
        } catch (e: Exception) {}
    }
}
