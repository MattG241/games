package com.flowforge.android.data

import android.content.Context
import com.flowforge.android.model.Blueprint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.UUID

@Serializable
data class StepLog(
    val moduleId: Int,
    val type: String,
    val name: String,
    val status: String,
    val durationMs: Long = 0,
    val input: String = "",
    val output: String = "",
    val error: String? = null,
)

@Serializable
data class RunRecord(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val scenarioName: String,
    val startedAt: Long,
    val finishedAt: Long = 0,
    val status: String = "running",
    val triggerSummary: String = "",
    val steps: List<StepLog> = emptyList(),
    val error: String? = null,
) {
    val durationMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0)
}

/** Scenarios live as one blueprint JSON per file, so export/import is a straight copy. */
class ScenarioStore(context: Context) {

    private val dir = File(context.filesDir, "scenarios").apply { mkdirs() }
    private val _scenarios = MutableStateFlow<List<Blueprint>>(emptyList())
    val scenarios: StateFlow<List<Blueprint>> = _scenarios.asStateFlow()

    init { reload() }

    fun reload() {
        val loaded = dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { Blueprint.fromJson(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
        _scenarios.value = loaded
    }

    fun get(id: String): Blueprint? = _scenarios.value.firstOrNull { it.id == id }

    fun findByNameOrId(needle: String): Blueprint? =
        get(needle) ?: _scenarios.value.firstOrNull { it.name.equals(needle.trim(), ignoreCase = true) }

    fun save(blueprint: Blueprint): Blueprint {
        val stamped = blueprint.copy(updatedAt = System.currentTimeMillis())
        File(dir, "${stamped.id}.json").writeText(stamped.toJson())
        _scenarios.value = (_scenarios.value.filterNot { it.id == stamped.id } + stamped)
            .sortedByDescending { it.updatedAt }
        return stamped
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        _scenarios.value = _scenarios.value.filterNot { it.id == id }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        get(id)?.let { save(it.copy(enabled = enabled)) }
    }
}

/** Rolling execution history, newest first. */
class RunStore(context: Context) {

    private val file = File(context.filesDir, "runs.json")
    private val _runs = MutableStateFlow<List<RunRecord>>(emptyList())
    val runs: StateFlow<List<RunRecord>> = _runs.asStateFlow()

    init {
        _runs.value = runCatching {
            Blueprint.json.decodeFromString(ListSerializer(RunRecord.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun record(run: RunRecord) {
        val next = (listOf(run) + _runs.value.filterNot { it.id == run.id }).take(MAX_RUNS)
        _runs.value = next
        runCatching {
            file.writeText(Blueprint.json.encodeToString(ListSerializer(RunRecord.serializer()), next))
        }
    }

    fun forScenario(scenarioId: String): List<RunRecord> = _runs.value.filter { it.scenarioId == scenarioId }

    fun get(id: String): RunRecord? = _runs.value.firstOrNull { it.id == id }

    @Synchronized
    fun clear() {
        _runs.value = emptyList()
        file.delete()
    }

    private companion object { const val MAX_RUNS = 250 }
}

/** Key/value storage shared by every scenario — the Data store module reads and writes this. */
class KeyValueStore(context: Context) {

    private val file = File(context.filesDir, "datastore.json")
    private val _entries = MutableStateFlow<Map<String, String>>(emptyMap())
    val entries: StateFlow<Map<String, String>> = _entries.asStateFlow()

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    init {
        _entries.value = runCatching {
            Blueprint.json.decodeFromString(serializer, file.readText())
        }.getOrDefault(emptyMap())
    }

    fun get(key: String): String? = _entries.value[key]

    @Synchronized
    fun put(key: String, value: String) {
        _entries.value = _entries.value + (key to value)
        persist()
    }

    @Synchronized
    fun remove(key: String): Boolean {
        val existed = _entries.value.containsKey(key)
        _entries.value = _entries.value - key
        persist()
        return existed
    }

    fun keys(): List<String> = _entries.value.keys.sorted()

    private fun persist() {
        runCatching { file.writeText(Blueprint.json.encodeToString(serializer, _entries.value)) }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("flowforge", Context.MODE_PRIVATE)

    var webhookPort: Int
        get() = sp.getInt("webhook_port", 8420)
        set(v) = sp.edit().putInt("webhook_port", v).apply()

    var engineEnabled: Boolean
        get() = sp.getBoolean("engine_enabled", true)
        set(v) = sp.edit().putBoolean("engine_enabled", v).apply()

    var tileScenarioId: String?
        get() = sp.getString("tile_scenario", null)
        set(v) = sp.edit().putString("tile_scenario", v).apply()

    var notifyOnError: Boolean
        get() = sp.getBoolean("notify_error", true)
        set(v) = sp.edit().putBoolean("notify_error", v).apply()
}
