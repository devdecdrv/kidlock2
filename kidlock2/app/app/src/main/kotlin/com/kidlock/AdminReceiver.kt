package com.kidlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        HideManager.hideIcon(context)
    }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Cannot remove without administrator password."
    }
    override fun onDisabled(context: Context, intent: Intent) {
        val i = Intent(context, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
