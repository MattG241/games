package com.flowforge.android.engine.runners

import com.flowforge.android.FlowForgeApp
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.engine.StopRun
import com.flowforge.android.engine.Values
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

suspend fun runToolModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "file.write" -> writeFile(node, env)
    "file.read" -> readFile(node, env)
    "tool.setVariable" -> setVariable(node, env)
    "tool.transform" -> transform(node, env)
    "tool.json" -> parseJson(node, env)
    "tool.regex" -> matchPattern(node, env)
    "tool.datastore" -> dataStore(node, env)
    "tool.log" -> logMessage(node, env)
    "flow.sleep" -> sleep(node, env)
    "flow.stop" -> stop(node, env)
    "flow.aggregate" -> aggregate(node, env)
    "scenario.run" -> runScenario(node, env)
    else -> null
}

private suspend fun writeFile(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val target = resolveFile(env, env.text(node, "path"))
    require(target.path.isNotBlank()) { "Give the file a name" }
    target.parentFile?.mkdirs()
    val content = env.text(node, "content")
    if (env.choice(node, "mode", "Overwrite") == "Append") target.appendText(content)
    else target.writeText(content)
    mapOf("path" to target.absolutePath, "bytes" to target.length().toDouble())
}

private suspend fun readFile(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val target = resolveFile(env, env.text(node, "path"))
    if (!target.exists()) {
        mapOf("exists" to false, "content" to "", "json" to null, "bytes" to 0.0)
    } else {
        val content = target.readText()
        mapOf(
            "exists" to true,
            "content" to content,
            "json" to if (env.bool(node, "parseJson", false)) Values.parseJsonOrNull(content) else null,
            "bytes" to target.length().toDouble(),
        )
    }
}

private fun setVariable(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val name = node.param("name").trim()
    require(name.isNotBlank()) { "Variable needs a name" }
    val value = env.value(node, "value")
    env.scope.vars[name] = value
    return mapOf("name" to name, "value" to value)
}

private fun transform(node: ModuleNode, env: RunEnv): Map<String, Any?> =
    mapOf("value" to env.value(node, "value"))

private fun parseJson(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val text = env.text(node, "text")
    val parsed = Values.parseJsonOrNull(text)
    return mapOf("json" to parsed, "valid" to (parsed != null))
}

private fun matchPattern(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val text = env.text(node, "text")
    val pattern = env.text(node, "pattern")
    require(pattern.isNotBlank()) { "Enter a regular expression" }
    val regex = Regex(pattern)
    return if (env.bool(node, "all", false)) {
        val all = regex.findAll(text).map { it.value }.toList()
        mapOf("matched" to all.isNotEmpty(), "matches" to all, "count" to all.size.toDouble())
    } else {
        val match = regex.find(text)
        mapOf(
            "matched" to (match != null),
            "match" to match?.value,
            "groups" to (match?.groupValues?.drop(1) ?: emptyList<String>()),
            "count" to (if (match != null) 1.0 else 0.0),
        )
    }
}

private fun dataStore(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val store = FlowForgeApp.instance.keyValues
    val key = env.text(node, "key").trim()
    return when (env.choice(node, "action", "Get")) {
        "Set" -> {
            require(key.isNotBlank()) { "Data store needs a key" }
            val value = env.text(node, "value")
            store.put(key, value)
            mapOf("key" to key, "value" to value)
        }
        "Add number" -> {
            require(key.isNotBlank()) { "Data store needs a key" }
            val delta = Values.asNumber(env.value(node, "value")) ?: 1.0
            val current = Values.asNumber(store.get(key)) ?: 0.0
            val next = current + delta
            store.put(key, Values.asText(next))
            mapOf("key" to key, "value" to next, "previous" to current)
        }
        "Delete" -> mapOf("key" to key, "existed" to store.remove(key))
        "List keys" -> mapOf("keys" to store.keys(), "count" to store.keys().size.toDouble())
        else -> {
            val raw = store.get(key)
            mapOf("key" to key, "value" to raw, "existed" to (raw != null))
        }
    }
}

private fun logMessage(node: ModuleNode, env: RunEnv): Map<String, Any?> =
    mapOf("message" to env.text(node, "message"))

private suspend fun sleep(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val ms = env.number(node, "ms", 1000.0).toLong().coerceIn(0, 120_000)
    delay(ms)
    return mapOf("slept" to ms.toDouble())
}

private fun stop(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    throw StopRun(env.choice(node, "status", "Success"), env.text(node, "message"))
}

private fun aggregate(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val bucket = env.aggregates.getOrPut(node.id) { mutableListOf() }
    bucket += env.text(node, "value")
    val separator = node.param("separator", ", ")
    return mapOf("text" to bucket.joinToString(separator), "count" to bucket.size.toDouble())
}

private suspend fun runScenario(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val app = FlowForgeApp.instance
    val needle = node.param("scenario").trim()
    val target = app.scenarios.findByNameOrId(needle)
        ?: error("No scenario named \"$needle\"")

    val payloadText = env.text(node, "payload")
    @Suppress("UNCHECKED_CAST")
    val payload = (Values.parseJsonOrNull(payloadText) as? Map<String, Any?>)
        ?: mapOf("source" to "scenario.run", "payload" to payloadText)

    if (!env.bool(node, "wait", true)) {
        app.launchScenario(target, payload, "Called by another scenario")
        return mapOf("status" to "started", "scenario" to target.name)
    }

    if (nestingDepth.incrementAndGet() > MAX_NESTING) {
        nestingDepth.decrementAndGet()
        error("Scenarios are nested more than $MAX_NESTING deep — check for a call loop")
    }
    try {
        val record = app.engine.execute(target, payload, "Called by another scenario")
        return mapOf(
            "status" to record.status,
            "scenario" to target.name,
            "output" to record.steps.lastOrNull()?.output,
            "error" to record.error,
        )
    } finally {
        nestingDepth.decrementAndGet()
    }
}

/** Stops a chain of scenarios calling each other from recursing forever. */
private val nestingDepth = java.util.concurrent.atomic.AtomicInteger(0)
private const val MAX_NESTING = 5
