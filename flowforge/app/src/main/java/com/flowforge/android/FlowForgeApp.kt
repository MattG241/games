package com.flowforge.android

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.flowforge.android.core.FlowService
import com.flowforge.android.core.Scheduler
import com.flowforge.android.data.KeyValueStore
import com.flowforge.android.data.Prefs
import com.flowforge.android.data.RunRecord
import com.flowforge.android.data.RunStore
import com.flowforge.android.data.ScenarioStore
import com.flowforge.android.engine.Engine
import com.flowforge.android.engine.runners.Notifications
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowForgeApp : Application() {

    val scenarios: ScenarioStore by lazy { ScenarioStore(this) }
    val runs: RunStore by lazy { RunStore(this) }
    val keyValues: KeyValueStore by lazy { KeyValueStore(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val engine: Engine by lazy { Engine(this, runs) }
    val scheduler: Scheduler by lazy { Scheduler(this) }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)
        scenarios.reload()
        scheduler.rescheduleAll()
        if (prefs.engineEnabled) startEngineService()
    }

    fun startEngineService() {
        runCatching {
            val intent = Intent(this, FlowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }.onFailure { Log.w(TAG, "Could not start the engine service", it) }
    }

    fun stopEngineService() {
        runCatching { stopService(Intent(this, FlowService::class.java)) }
    }

    /** Fire and forget — used by every trigger source. */
    fun launchScenario(
        blueprint: Blueprint,
        bundle: Map<String, Any?>,
        summary: String,
        webhookResponder: ((Int, String, String) -> Unit)? = null,
        onFinished: ((RunRecord) -> Unit)? = null,
    ) {
        appScope.launch {
            val record = engine.execute(blueprint, bundle, summary, webhookResponder)
            if (record.status == "error" && prefs.notifyOnError) {
                Notifications.post(
                    this@FlowForgeApp,
                    ("err" + blueprint.id).hashCode(),
                    "${blueprint.name} failed",
                    record.error ?: "The scenario stopped with an error",
                    Notifications.CHANNEL_DEFAULT,
                )
            }
            onFinished?.invoke(record)
        }
    }

    /**
     * Runs every enabled scenario whose trigger is [triggerType] and whose own settings
     * accept this event.
     */
    fun dispatchTrigger(
        triggerType: String,
        bundle: Map<String, Any?>,
        summary: String = "",
        accepts: (ModuleNode) -> Boolean = { true },
    ): Int {
        var fired = 0
        scenarios.scenarios.value
            .filter { it.enabled }
            .forEach { blueprint ->
                val trigger = blueprint.trigger ?: return@forEach
                if (trigger.type != triggerType) return@forEach
                if (!runCatching { accepts(trigger) }.getOrDefault(false)) return@forEach
                fired++
                launchScenario(blueprint, bundle, summary.ifBlank { triggerType })
            }
        return fired
    }

    companion object {
        private const val TAG = "FlowForgeApp"

        @Volatile
        lateinit var instance: FlowForgeApp
            private set
    }
}
