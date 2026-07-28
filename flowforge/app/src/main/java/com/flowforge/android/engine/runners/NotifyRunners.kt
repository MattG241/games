package com.flowforge.android.engine.runners

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.flowforge.android.R
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.triggers.FlowNotificationListener
import com.flowforge.android.triggers.NotificationActionReceiver
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
    "notify.dismiss" -> dismissNotification(node, env)
    "notify.reply" -> replyToNotification(node, env)
    "notify.snooze" -> snoozeNotification(node, env)
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

    Notifications.ensureChannels(env.app)
    val builder = NotificationCompat.Builder(env.app, channel)
        .setSmallIcon(R.drawable.ic_tile)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setOngoing(env.bool(node, "ongoing", false))
        .setPriority(
            if (channel == Notifications.CHANNEL_HIGH) NotificationCompat.PRIORITY_HIGH
            else NotificationCompat.PRIORITY_DEFAULT
        )

    // Each "Label=Scenario" line becomes a button that fires that scenario when tapped.
    val buttons = env.text(node, "actions").lineSequence()
        .map { it.trim() }
        .filter { it.contains('=') }
        .take(3)
        .toList()

    buttons.forEachIndexed { index, line ->
        val label = line.substringBefore('=').trim()
        val scenario = line.substringAfter('=').trim()
        if (label.isEmpty() || scenario.isEmpty()) return@forEachIndexed
        val intent = Intent(env.app, NotificationActionReceiver::class.java)
            .setAction("${NotificationActionReceiver.ACTION_RUN}.$id.$index")
            .putExtra(NotificationActionReceiver.EXTRA_SCENARIO, scenario)
            .putExtra(NotificationActionReceiver.EXTRA_LABEL, label)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, id)
        val pending = PendingIntent.getBroadcast(
            env.app,
            (id.toString() + index).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(R.drawable.ic_tile, label, pending)
    }

    runCatching {
        env.app.getSystemService(NotificationManager::class.java)?.notify(id, builder.build())
    }.onFailure { error("Could not post the notification — check the notifications permission") }

    return mapOf("id" to id.toDouble(), "title" to title, "buttons" to buttons.size.toDouble())
}

private fun dismissNotification(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val scope = env.choice(node, "scope", "By key")

    if (scope == "My own by tag") {
        val tag = env.text(node, "tag")
        val manager = env.app.getSystemService(NotificationManager::class.java)
        if (tag.isBlank()) manager?.cancelAll() else manager?.cancel(tag.hashCode())
        return mapOf("dismissed" to true, "count" to 1.0)
    }

    val listener = FlowNotificationListener.instance
        ?: error("Dismissing other apps' notifications needs notification access — grant it in Settings inside FlowForge")

    return when (scope) {
        "All" -> {
            val count = listener.activeNotifications?.size ?: 0
            listener.cancelAllNotifications()
            mapOf("dismissed" to true, "count" to count.toDouble())
        }
        "All from an app" -> {
            val pkg = env.text(node, "package").trim()
            require(pkg.isNotBlank()) { "Pick an app" }
            val keys = listener.activeNotifications
                ?.filter { it.packageName == pkg }
                ?.map { it.key }
                .orEmpty()
            keys.forEach { runCatching { listener.cancelNotification(it) } }
            mapOf("dismissed" to keys.isNotEmpty(), "count" to keys.size.toDouble())
        }
        else -> {
            val key = env.text(node, "key").trim()
            require(key.isNotBlank()) { "Map in a notification key, e.g. {{1.key}}" }
            listener.cancelNotification(key)
            mapOf("dismissed" to true, "count" to 1.0)
        }
    }
}

private fun replyToNotification(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val listener = FlowNotificationListener.instance
        ?: error("Inline replies need notification access — grant it in Settings inside FlowForge")
    val key = env.text(node, "key").trim()
    require(key.isNotBlank()) { "Map in a notification key, e.g. {{1.key}}" }
    val text = env.text(node, "text")
    require(text.isNotBlank()) { "Enter the reply text" }

    val sbn = listener.activeNotifications?.firstOrNull { it.key == key }
        ?: error("That notification is no longer showing")

    val action = sbn.notification.actions?.firstOrNull { candidate ->
        candidate.remoteInputs?.any { it.allowFreeFormInput } == true
    } ?: error("That notification does not offer an inline reply")

    val inputs = action.remoteInputs ?: error("That notification does not offer an inline reply")
    val results = Bundle()
    inputs.forEach { results.putCharSequence(it.resultKey, text) }

    val intent = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val compatInputs = inputs.map { native ->
        RemoteInput.Builder(native.resultKey)
            .setLabel(native.label)
            .setAllowFreeFormInput(native.allowFreeFormInput)
            .build()
    }.toTypedArray()
    RemoteInput.addResultsToIntent(compatInputs, intent, results)

    action.actionIntent.send(env.app, 0, intent)
    return mapOf("replied" to true, "key" to key, "text" to text)
}

private fun snoozeNotification(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        error("Snoozing needs Android 8 or newer")
    }
    val listener = FlowNotificationListener.instance
        ?: error("Snoozing needs notification access — grant it in Settings inside FlowForge")
    val key = env.text(node, "key").trim()
    require(key.isNotBlank()) { "Map in a notification key, e.g. {{1.key}}" }
    val minutes = env.number(node, "minutes", 10.0).coerceIn(1.0, 24 * 60.0)
    val duration = (minutes * 60_000).toLong()

    listener.snoozeNotification(key, duration)
    return mapOf(
        "snoozed" to true,
        "untilTimestamp" to (System.currentTimeMillis() + duration).toDouble(),
        "minutes" to minutes,
    )
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
