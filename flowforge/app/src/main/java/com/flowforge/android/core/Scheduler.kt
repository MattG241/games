package com.flowforge.android.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.model.Blueprint
import com.flowforge.android.triggers.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Turns `Schedule` triggers into AlarmManager alarms, one pending alarm per scenario. */
class Scheduler(private val context: Context) {

    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun rescheduleAll() {
        val app = FlowForgeApp.instance
        app.scenarios.scenarios.value.forEach { blueprint ->
            if (blueprint.enabled && blueprint.trigger?.type == "trigger.schedule") schedule(blueprint)
            else cancel(blueprint.id)
        }
    }

    fun schedule(blueprint: Blueprint) {
        val trigger = blueprint.trigger ?: return
        if (trigger.type != "trigger.schedule") return
        val next = nextFireTime(blueprint) ?: return
        val pending = pendingIntent(blueprint.id)
        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            if (canExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            }
        }.onFailure { Log.w(TAG, "Could not schedule ${blueprint.name}", it) }
    }

    fun cancel(scenarioId: String) {
        runCatching { alarms.cancel(pendingIntent(scenarioId)) }
    }

    fun nextFireTime(blueprint: Blueprint, from: Long = System.currentTimeMillis()): Long? {
        val trigger = blueprint.trigger ?: return null
        return when (trigger.param("mode", "Every N minutes")) {
            "Daily at time" -> {
                val time = trigger.param("time", "09:00")
                val hour = time.substringBefore(':').trim().toIntOrNull() ?: 9
                val minute = time.substringAfter(':', "0").trim().toIntOrNull() ?: 0
                val allowed = parseDays(trigger.param("days"))
                val cal = Calendar.getInstance().apply {
                    timeInMillis = from
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
                var guard = 0
                while (allowed.isNotEmpty() && cal.get(Calendar.DAY_OF_WEEK) !in allowed && guard < 8) {
                    cal.add(Calendar.DAY_OF_YEAR, 1); guard++
                }
                cal.timeInMillis
            }
            else -> {
                val minutes = trigger.param("minutes", "15").toLongOrNull()?.coerceAtLeast(1) ?: 15
                from + minutes * 60_000L
            }
        }
    }

    fun describeNext(blueprint: Blueprint): String {
        val next = nextFireTime(blueprint) ?: return "not scheduled"
        return SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(next)
    }

    private fun parseDays(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val map = mapOf(
            "sun" to Calendar.SUNDAY, "mon" to Calendar.MONDAY, "tue" to Calendar.TUESDAY,
            "wed" to Calendar.WEDNESDAY, "thu" to Calendar.THURSDAY, "fri" to Calendar.FRIDAY,
            "sat" to Calendar.SATURDAY,
        )
        return raw.split(',', ' ', ';')
            .mapNotNull { token -> map[token.trim().lowercase().take(3)] }
            .toSet()
    }

    private fun pendingIntent(scenarioId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("com.flowforge.android.ALARM")
            .putExtra(AlarmReceiver.EXTRA_SCENARIO_ID, scenarioId)
        return PendingIntent.getBroadcast(
            context,
            scenarioId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object { const val TAG = "Scheduler" }
}
