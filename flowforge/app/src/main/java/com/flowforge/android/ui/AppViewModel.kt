package com.flowforge.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.core.FlowService
import com.flowforge.android.data.RunRecord
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.ModuleCatalog
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = FlowForgeApp.instance

    val scenarios: StateFlow<List<Blueprint>> get() = app.scenarios.scenarios
    val runs: StateFlow<List<RunRecord>> get() = app.runs.runs
    val dataEntries: StateFlow<Map<String, String>> get() = app.keyValues.entries

    /** Last finished run, so the editor can flash a result toast. */
    val lastRun = MutableStateFlow<RunRecord?>(null)
    val busy = MutableStateFlow(false)

    fun scenario(id: String): Blueprint? = app.scenarios.get(id)

    fun createScenario(name: String = "New scenario"): Blueprint {
        val blueprint = Blueprint(
            id = UUID.randomUUID().toString(),
            name = name,
            modules = listOf(
                ModuleNode(
                    id = 1,
                    type = "trigger.manual",
                    params = ModuleCatalog.defaultParams(ModuleCatalog.specOrUnknown("trigger.manual")),
                )
            ),
        )
        return app.scenarios.save(blueprint)
    }

    fun save(blueprint: Blueprint): Blueprint {
        val saved = app.scenarios.save(blueprint)
        syncBackground(saved)
        return saved
    }

    fun delete(id: String) {
        app.scheduler.cancel(id)
        app.scenarios.delete(id)
        FlowService.refresh(getApplication())
    }

    fun setEnabled(blueprint: Blueprint, enabled: Boolean) {
        val saved = app.scenarios.save(blueprint.copy(enabled = enabled))
        syncBackground(saved)
    }

    fun duplicate(blueprint: Blueprint) {
        app.scenarios.save(
            blueprint.copy(
                id = UUID.randomUUID().toString(),
                name = "${blueprint.name} copy",
                enabled = false,
            )
        )
    }

    fun importBlueprint(text: String): Result<Blueprint> = runCatching {
        val parsed = Blueprint.fromJson(text)
        app.scenarios.save(parsed.copy(id = UUID.randomUUID().toString(), enabled = false))
    }

    fun runNow(blueprint: Blueprint) {
        busy.value = true
        viewModelScope.launch {
            val record = app.engine.execute(
                blueprint,
                mapOf(
                    "source" to "manual",
                    "timestamp" to System.currentTimeMillis().toDouble(),
                ),
                "Run manually",
            )
            lastRun.value = record
            busy.value = false
        }
    }

    fun runsFor(scenarioId: String): List<RunRecord> = app.runs.forScenario(scenarioId)

    fun run(id: String): RunRecord? = app.runs.get(id)

    fun clearHistory() = app.runs.clear()

    fun putData(key: String, value: String) = app.keyValues.put(key, value)

    fun removeData(key: String) { app.keyValues.remove(key) }

    private fun syncBackground(blueprint: Blueprint) {
        if (blueprint.enabled) app.scheduler.schedule(blueprint) else app.scheduler.cancel(blueprint.id)
        if (app.prefs.engineEnabled) {
            app.startEngineService()
            FlowService.refresh(getApplication())
        }
    }
}
