package com.kidlock

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class TelegramListener(private val context: Context) {

    private val BOT_TOKEN = "8901039134:AAEBI8Wn-HKC1b3IG9qzz4hIwKnKv_UyIcA"
    private val CHAT_ID   = "8889476230"
    private val BASE_URL  = "https://api.telegram.org/bot$BOT_TOKEN"

    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateId = 0L
    private var running = false

    fun startPolling() {
        if (running) return
        running = true
        poll()
    }

    fun stopPolling() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun poll() {
        Thread {
            try {
                val url = "$BASE_URL/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.connectTimeout = 35000
                conn.readTimeout = 35000
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(response)
                if (json.getBoolean("ok")) {
                    val updates = json.getJSONArray("result")
                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        lastUpdateId = update.getLong("update_id")
                        val message = update.optJSONObject("message") ?: continue
                        val chatId = message.getJSONObject("chat").getString("id")
                        if (chatId != CHAT_ID) continue
                        val text = message.optString("text", "")
                        handleCommand(text)
                    }
                }
            } catch (e: Exception) {
                Log.e("TelegramListener", "poll error: ${e.message}")
            }

            if (running) {
                handler.postDelayed({ poll() }, 1000)
            }
        }.start()
    }

    private fun handleCommand(text: String) {
        when {
            text == "/lock" -> {
                val i = Intent(context, LockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                sendMessage("🔒 Phone locked.")
            }
            text == "/unlock" -> {
                LockActivity.dismiss()
                sendMessage("🔓 Phone unlocked.")
            }
            text.startsWith("/speak ") -> {
                val msg = text.removePrefix("/speak ").trim()
                TtsManager.speak(context, msg)
                sendMessage("🔊 Speaking: $msg")
            }
            text == "/camera" -> {
                CameraManager.captureAndSend(context, BOT_TOKEN, CHAT_ID)
                sendMessage("📸 Capturing photo...")
            }
            text == "/status" -> {
                StatusReporter.reportToTelegram(context, BOT_TOKEN, CHAT_ID)
            }
            text == "/applog" -> {
                AppUsageReporter.reportToTelegram(context, BOT_TOKEN, CHAT_ID)
            }
            text.startsWith("/block ") -> {
                val pkg = text.removePrefix("/block ").trim()
                AppBlocker.block(context, pkg)
                sendMessage("🚫 Blocked: $pkg")
            }
            text.startsWith("/unblock ") -> {
                val pkg = text.removePrefix("/unblock ").trim()
                AppBlocker.unblock(context, pkg)
                sendMessage("✅ Unblocked: $pkg")
            }
            text.startsWith("/bedtime ") -> {
                val time = text.removePrefix("/bedtime ").trim()
                ScheduleManager.setBedtime(context, time)
                sendMessage("🌙 Bedtime set: $time")
            }
            text.startsWith("/schedule ") -> {
                val parts = text.removePrefix("/schedule ").trim().split(" ")
                if (parts.size == 2) {
                    ScheduleManager.setSchedule(context, "${parts[0]}-${parts[1]}")
                    sendMessage("⏰ Allowed hours: ${parts[0]} to ${parts[1]}")
                }
            }
            text == "/help" -> {
                sendMessage(
                    "📋 Commands:\n\n" +
                    "/lock — lock phone\n" +
                    "/unlock — unlock phone\n" +
                    "/speak <text> — speak on phone\n" +
                    "/camera — take photo\n" +
                    "/status — battery & last seen\n" +
                    "/applog — today's app usage\n" +
                    "/block <pkg> — block app\n" +
                    "/unblock <pkg> — unblock app\n" +
                    "/bedtime 21:00 — auto lock at time\n" +
                    "/schedule 16:00 18:00 — allowed hours\n"
                )
            }
        }
    }

    fun sendMessage(text: String) {
        Thread {
            try {
                val url = "$BASE_URL/sendMessage"
                val body = JSONObject().apply {
                    put("chat_id", CHAT_ID)
                    put("text", text)
                }.toString()
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(body.toByteArray())
                conn.inputStream.bufferedReader().readText()
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("TelegramListener", "sendMessage error: ${e.message}")
            }
        }.start()
    }

    fun sendAudio(filePath: String, caption: String) {
        Thread {
            try {
                val file = java.io.File(filePath)
                if (!file.exists()) return@Thread
                val boundary = "----TelegramBoundary${System.currentTimeMillis()}"
                val url = "$BASE_URL/sendAudio"
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true

                val os = conn.outputStream
                // chat_id
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$CHAT_ID\r\n".toByteArray())
                // caption
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n".toByteArray())
                // file
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"${file.name}\"\r\nContent-Type: audio/mpeg\r\n\r\n".toByteArray())
                os.write(file.readBytes())
                os.write("\r\n--$boundary--\r\n".toByteArray())
                os.flush()

                conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                file.delete()
            } catch (e: Exception) {
                Log.e("TelegramListener", "sendAudio error: ${e.message}")
            }
        }.start()
    }

    fun sendPhoto(filePath: String) {
        Thread {
            try {
                val file = java.io.File(filePath)
                if (!file.exists()) return@Thread
                val boundary = "----TelegramBoundary${System.currentTimeMillis()}"
                val url = "$BASE_URL/sendPhoto"
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true

                val os = conn.outputStream
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$CHAT_ID\r\n".toByteArray())
                os.write("--$boundary\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
                os.write(file.readBytes())
                os.write("\r\n--$boundary--\r\n".toByteArray())
                os.flush()

                conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                file.delete()
            } catch (e: Exception) {
                Log.e("TelegramListener", "sendPhoto error: ${e.message}")
            }
        }.start()
    }

    companion object {
        lateinit var instance: TelegramListener
    }
}
