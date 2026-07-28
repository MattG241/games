package com.flowforge.android.triggers

import android.app.NotificationManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.flowforge.android.FlowForgeApp

/** Feeds `Notification posted` triggers. Needs notification access, granted from Settings. */
class FlowNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        dispatch(sbn, "trigger.notification")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        dispatch(sbn, "trigger.notificationRemoved")
    }

    private fun dispatch(sbn: StatusBarNotification, triggerType: String) {
        val app = runCatching { FlowForgeApp.instance }.getOrNull() ?: return
        if (sbn.packageName == packageName) return // never react to our own notifications

        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val ongoing = sbn.isOngoing
        val canReply = sbn.notification?.actions?.any { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: false
        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        app.dispatchTrigger(
            triggerType,
            mapOf(
                "package" to sbn.packageName,
                "appName" to appName,
                "title" to title,
                "text" to text,
                "subText" to subText,
                "postedAt" to sbn.postTime.toDouble(),
                "key" to sbn.key,
                "canReply" to canReply,
                "ongoing" to ongoing,
            ),
            "Notification from $appName",
        ) { trigger ->
            val wantPackage = trigger.param("package").trim()
            val wantContains = trigger.param("contains").trim()
            val skipOngoing = trigger.param("ignoreOngoing", "true").toBoolean()
            (triggerType != "trigger.notification" || !skipOngoing || !ongoing) &&
                (wantPackage.isBlank() || wantPackage.equals(sbn.packageName, ignoreCase = true)) &&
                (wantContains.isBlank() ||
                    title.contains(wantContains, true) || text.contains(wantContains, true))
        }
    }

    companion object {
        @Volatile
        var instance: FlowNotificationListener? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            val component = ComponentName(context, FlowNotificationListener::class.java)
            return flat.split(':').any {
                ComponentName.unflattenFromString(it) == component
            }
        }
    }
}

/** Runs the scenario behind a notification button. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = runCatching { FlowForgeApp.instance }.getOrNull() ?: return
        val needle = intent.getStringExtra(EXTRA_SCENARIO) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val blueprint = app.scenarios.findByNameOrId(needle)
        if (blueprint == null) {
            Toast.makeText(context, "No scenario named \"$needle\"", Toast.LENGTH_SHORT).show()
            return
        }

        if (notificationId >= 0) {
            runCatching {
                context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
            }
        }

        app.launchScenario(
            blueprint,
            mapOf(
                "source" to "notification button",
                "button" to label,
                "timestamp" to System.currentTimeMillis().toDouble(),
            ),
            "Notification button: $label",
        )
    }

    companion object {
        const val ACTION_RUN = "com.flowforge.android.NOTIFICATION_ACTION"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_LABEL = "label"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

/** Quick-settings tile that runs one chosen scenario. */
class RunTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val app = runCatching { FlowForgeApp.instance }.getOrNull() ?: return
        val scenario = app.prefs.tileScenarioId?.let { app.scenarios.get(it) }
        qsTile?.apply {
            label = scenario?.name ?: "FlowForge"
            state = if (scenario != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val app = runCatching { FlowForgeApp.instance }.getOrNull() ?: return
        val scenario = app.prefs.tileScenarioId?.let { app.scenarios.get(it) }
        if (scenario == null) {
            Toast.makeText(this, "Pick a tile scenario in FlowForge settings", Toast.LENGTH_SHORT).show()
            return
        }
        app.launchScenario(
            scenario,
            mapOf("source" to "tile", "timestamp" to System.currentTimeMillis().toDouble()),
            "Quick settings tile",
        )
        Toast.makeText(this, "Running ${scenario.name}", Toast.LENGTH_SHORT).show()
    }
}
