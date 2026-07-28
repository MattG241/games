package com.flowforge.android.engine.runners

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.flowforge.android.R
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object Notifications {
    const val CHANNEL_DEFAULT = "flow_default"
    const val CHANNEL_HIGH = "flow_high"
    const val CHANNEL_SILENT = "flow_silent"
    const val CHANNEL_SERVICE = "flow_service"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(CHANNEL_DEFAULT, "Scenario messages", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_HIGH, "Scenario alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_SILENT, "Quiet scenario messages", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_SERVICE, "Automation engine", NotificationManager.IMPORTANCE_MIN),
        ).forEach { manager.createNotificationChannel(it) }
    }

    fun post(context: Context, id: Int, title: String, text: String, channel: String, ongoing: Boolean = false) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setPriority(if (channel == CHANNEL_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        }
    }
}

suspend fun runNotifyModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "notify.send" -> sendNotification(node, env)
    "sms.send" -> sendSms(node, env)
    "device.tts" -> speak(node, env)
    "device.toast" -> toast(node, env)
    else -> null
}

private fun sendNotification(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val title = env.text(node, "title").ifBlank { "FlowForge" }
    val body = env.text(node, "text")
    val channel = when (env.choice(node, "channel", "Default")) {
        "High (heads up)" -> Notifications.CHANNEL_HIGH
        "Silent" -> Notifications.CHANNEL_SILENT
        else -> Notifications.CHANNEL_DEFAULT
    }
    val tag = env.text(node, "tag")
    val id = if (tag.isNotBlank()) tag.hashCode() else (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    Notifications.post(env.app, id, title, body, channel, env.bool(node, "ongoing", false))
    return mapOf("id" to id.toDouble(), "title" to title)
}

private fun sendSms(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val to = env.text(node, "to").trim()
    val message = env.text(node, "message")
    require(to.isNotBlank()) { "SMS needs a destination number" }
    require(message.isNotBlank()) { "SMS needs a message body" }

    val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        env.app.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    } ?: error("No SMS service available on this device")

    val parts = manager.divideMessage(message)
    if (parts.size > 1) {
        manager.sendMultipartTextMessage(to, null, parts, null, null)
    } else {
        manager.sendTextMessage(to, null, message, null, null)
    }
    return mapOf("to" to to, "parts" to parts.size.toDouble())
}

private suspend fun speak(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val text = env.text(node, "text")
    if (text.isBlank()) return mapOf("spoken" to false, "reason" to "Nothing to say")
    val rate = env.number(node, "rate", 1.0).toFloat().coerceIn(0.3f, 3.0f)
    val queue = if (env.bool(node, "queue", false)) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH

    val spoke = withTimeoutOrNull(15_000L) {
        suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(env.app) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engine?.language = Locale.getDefault()
                    engine?.setSpeechRate(rate)
                    engine?.speak(text, queue, null, "flowforge")
                    if (cont.isActive) cont.resume(true)
                } else if (cont.isActive) {
                    cont.resume(false)
                }
            }
            cont.invokeOnCancellation { runCatching { engine?.shutdown() } }
        }
    } ?: false

    return mapOf("spoken" to spoke, "text" to text)
}

private suspend fun toast(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.Main) {
    val text = env.text(node, "text")
    val length = if (env.bool(node, "long", false)) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    Toast.makeText(env.app, text, length).show()
    mapOf("shown" to true, "text" to text)
}
