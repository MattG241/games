package com.flowforge.android.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.engine.Values

/** Fires a scheduled scenario, then books the next alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = FlowForgeApp.instance
        val id = intent.getStringExtra(EXTRA_SCENARIO_ID) ?: return
        val blueprint = app.scenarios.get(id) ?: return
        if (blueprint.enabled) {
            val now = System.currentTimeMillis()
            app.launchScenario(
                blueprint,
                mapOf(
                    "timestamp" to now.toDouble(),
                    "time" to android.text.format.DateFormat.format("HH:mm", now).toString(),
                    "date" to android.text.format.DateFormat.format("yyyy-MM-dd", now).toString(),
                ),
                "Schedule",
            )
            app.scheduler.schedule(blueprint)
        }
    }

    companion object { const val EXTRA_SCENARIO_ID = "scenario_id" }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = FlowForgeApp.instance
        app.scenarios.reload()
        if (app.prefs.engineEnabled) app.startEngineService()
        app.scheduler.rescheduleAll()
        app.dispatchTrigger(
            "trigger.boot",
            mapOf("timestamp" to System.currentTimeMillis().toDouble()),
            "Device booted",
        )
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull() ?: return

        // Multipart messages arrive as several PDUs from the same sender.
        val from = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val app = FlowForgeApp.instance

        app.dispatchTrigger(
            "trigger.sms",
            mapOf(
                "from" to from,
                "text" to body,
                "timestamp" to System.currentTimeMillis().toDouble(),
            ),
            "SMS from $from",
        ) { trigger ->
            val wantFrom = trigger.param("from").trim()
            val wantContains = trigger.param("contains").trim()
            (wantFrom.isBlank() || from.replace(" ", "").endsWith(wantFrom.replace(" ", "").takeLast(9))) &&
                (wantContains.isBlank() || body.contains(wantContains, ignoreCase = true))
        }
    }
}

/**
 * Lets anything on the device start a scenario:
 * `am broadcast -a com.flowforge.android.RUN_SCENARIO --es scenario "Morning" --es payload '{"x":1}'`
 */
class ExternalTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = FlowForgeApp.instance
        val needle = intent.getStringExtra("scenario") ?: return
        val blueprint = app.scenarios.findByNameOrId(needle) ?: return

        @Suppress("UNCHECKED_CAST")
        val payload = (Values.parseJsonOrNull(intent.getStringExtra("payload").orEmpty()) as? Map<String, Any?>)
            ?: emptyMap()

        app.launchScenario(
            blueprint,
            payload + mapOf(
                "source" to "broadcast",
                "timestamp" to System.currentTimeMillis().toDouble(),
            ),
            "External broadcast",
        )
    }
}

