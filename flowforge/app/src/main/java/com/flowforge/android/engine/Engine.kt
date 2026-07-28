package com.flowforge.android.engine

import android.content.Context
import android.util.Log
import com.flowforge.android.data.RunRecord
import com.flowforge.android.data.RunStore
import com.flowforge.android.data.StepLog
import com.flowforge.android.engine.runners.runCommsModule
import com.flowforge.android.engine.runners.runConnectivityModule
import com.flowforge.android.engine.runners.runDataModule
import com.flowforge.android.engine.runners.runDeviceModule
import com.flowforge.android.engine.runners.runFileModule
import com.flowforge.android.engine.runners.runMediaModule
import com.flowforge.android.engine.runners.runNetModule
import com.flowforge.android.engine.runners.runNotifyModule
import com.flowforge.android.engine.runners.runPrivilegedModule
import com.flowforge.android.engine.runners.runToolModule
import com.flowforge.android.engine.runners.runUiModule
import com.flowforge.android.engine.runners.runVisionModule
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.FilterRule
import com.flowforge.android.model.ModuleCatalog
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Thrown by the Stop module and by any fatal module error. */
class StopRun(val status: String, override val message: String) : RuntimeException(message)

/** A quiet halt — a filter said no. The run is still a success. */
class FilteredOut(val reason: String) : RuntimeException(reason)

class RunEnv(
    val app: Context,
    val scope: EvalScope,
    val steps: MutableList<StepLog>,
    /** Set by a webhook trigger so the Webhook response module can reply to the caller. */
    var webhookResponder: ((Int, String, String) -> Unit)? = null,
) {
    /** Running totals for the Aggregate module, keyed by module id. */
    val aggregates: MutableMap<Int, MutableList<String>> = linkedMapOf()

    fun text(node: ModuleNode, key: String, fallback: String = ""): String =
        Expression.render(node.params[key] ?: fallback, scope)

    fun value(node: ModuleNode, key: String): Any? = Expression.evaluate(node.params[key], scope)

    fun bool(node: ModuleNode, key: String, fallback: Boolean = false): Boolean {
        val raw = node.params[key] ?: return fallback
        if (raw.isBlank()) return fallback
        return Values.asBool(Expression.evaluate(raw, scope))
    }

    fun number(node: ModuleNode, key: String, fallback: Double = 0.0): Double =
        Values.asNumber(Expression.evaluate(node.params[key], scope)) ?: fallback

    fun choice(node: ModuleNode, key: String, fallback: String): String =
        (node.params[key] ?: fallback).ifBlank { fallback }
}

class Engine(private val app: Context, private val runs: RunStore) {

    /**
     * Executes a whole scenario. Never throws — a failure becomes a RunRecord with status `error`.
     */
    suspend fun execute(
        blueprint: Blueprint,
        triggerBundle: Map<String, Any?>,
        triggerSummary: String = "",
        webhookResponder: ((Int, String, String) -> Unit)? = null,
    ): RunRecord {
        val started = System.currentTimeMillis()
        val scope = EvalScope()
        blueprint.variables.forEach { (k, v) -> scope.vars[k] = v }

        val steps = mutableListOf<StepLog>()
        val env = RunEnv(app, scope, steps, webhookResponder)

        var status = "success"
        var error: String? = null

        val trigger = blueprint.trigger
        if (trigger == null) {
            status = "error"
            error = "Scenario has no trigger module"
        } else {
            scope.bundles[trigger.id] = triggerBundle
            steps += StepLog(
                moduleId = trigger.id,
                type = trigger.type,
                name = trigger.label ?: ModuleCatalog.specOrUnknown(trigger.type).name,
                status = "success",
                output = Values.encodePretty(triggerBundle),
            )
            try {
                withTimeout(RUN_TIMEOUT_MS) {
                    runChain(blueprint.modules, 1, env)
                }
            } catch (e: FilteredOut) {
                status = "filtered"
                error = e.reason
            } catch (e: StopRun) {
                status = if (e.status == "Error") "error" else "success"
                error = e.message.ifBlank { null }
            } catch (e: Exception) {
                status = "error"
                error = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Scenario ${blueprint.name} failed", e)
            }
        }

        val record = RunRecord(
            id = UUID.randomUUID().toString(),
            scenarioId = blueprint.id,
            scenarioName = blueprint.name,
            startedAt = started,
            finishedAt = System.currentTimeMillis(),
            status = status,
            triggerSummary = triggerSummary,
            steps = steps,
            error = error,
        )
        runs.record(record)
        return record
    }

    /** Runs [nodes] from [index] onward. Iterators re-enter this with the same tail. */
    private suspend fun runChain(nodes: List<ModuleNode>, index: Int, env: RunEnv) {
        if (index >= nodes.size) return
        val node = nodes[index]
        val spec = ModuleCatalog.specOrUnknown(node.type)
        val name = node.label ?: spec.name

        // A module-level filter gates the module and everything after it.
        node.filter?.let { rule ->
            if (!evaluateFilter(rule, env)) throw FilteredOut("Filter before module ${node.id} stopped the run")
        }

        when (node.type) {
            "flow.filter" -> {
                val rule = FilterRule(
                    node.param("left"), node.param("op", "equals"), node.param("right")
                )
                val passed = evaluateFilter(rule, env)
                env.steps += StepLog(
                    node.id, node.type, name,
                    status = if (passed) "success" else "filtered",
                    input = "${env.text(node, "left")} ${rule.op} ${env.text(node, "right")}",
                    output = Values.encodePretty(mapOf("passed" to passed)),
                )
                env.scope.bundles[node.id] = mapOf("passed" to passed)
                if (!passed) throw FilteredOut("Filter (module ${node.id}) stopped the run")
                runChain(nodes, index + 1, env)
                return
            }

            "flow.router" -> {
                val taken = mutableListOf<String>()
                node.routes.forEachIndexed { i, route ->
                    val ok = route.filter?.let { evaluateFilter(it, env) } ?: true
                    if (!ok) return@forEachIndexed
                    taken += route.label.ifBlank { "Route ${i + 1}" }
                    val branch = RunEnv(env.app, env.scope.copyForBranch(), env.steps, env.webhookResponder)
                    try {
                        runChain(route.modules, 0, branch)
                    } catch (e: FilteredOut) {
                        // A branch filtering itself out must not kill the sibling branches.
                        env.steps += StepLog(node.id, node.type, name, "filtered", output = e.reason)
                    }
                }
                env.steps += StepLog(
                    node.id, node.type, name, "success",
                    output = Values.encodePretty(mapOf("routes" to taken)),
                )
                return // a router is always the end of its own chain
            }

            "flow.iterator" -> {
                val items = Values.asList(env.value(node, "array"))
                env.steps += StepLog(
                    node.id, node.type, name, "success",
                    input = node.param("array"),
                    output = Values.encodePretty(mapOf("total" to items.size)),
                )
                items.forEachIndexed { i, item ->
                    env.scope.bundles[node.id] = mapOf(
                        "value" to item, "index" to (i + 1).toDouble(), "total" to items.size.toDouble()
                    )
                    runChain(nodes, index + 1, env)
                }
                return
            }

            "flow.repeater" -> {
                val count = env.number(node, "count", 1.0).toInt().coerceIn(1, 1000)
                val gap = env.number(node, "gapMs", 0.0).toLong().coerceIn(0, 60_000)
                env.steps += StepLog(node.id, node.type, name, "success", output = "repeats: $count")
                for (i in 1..count) {
                    env.scope.bundles[node.id] = mapOf("index" to i.toDouble(), "total" to count.toDouble())
                    runChain(nodes, index + 1, env)
                    if (gap > 0 && i < count) delay(gap)
                }
                return
            }
        }

        // Ordinary module: execute, log, continue.
        val startedAt = System.currentTimeMillis()
        val inputSummary = describeInput(node, env)
        try {
            val output = runModule(node, spec.type, env)
            env.scope.bundles[node.id] = output
            env.steps += StepLog(
                moduleId = node.id,
                type = node.type,
                name = name,
                status = "success",
                durationMs = System.currentTimeMillis() - startedAt,
                input = inputSummary,
                output = Values.encodePretty(output),
            )
        } catch (e: StopRun) {
            env.steps += StepLog(
                node.id, node.type, name,
                status = if (e.status == "Error") "error" else "success",
                durationMs = System.currentTimeMillis() - startedAt,
                input = inputSummary,
                output = e.message,
            )
            throw e
        } catch (e: Exception) {
            env.steps += StepLog(
                node.id, node.type, name, "error",
                durationMs = System.currentTimeMillis() - startedAt,
                input = inputSummary,
                output = "",
                error = e.message ?: e.javaClass.simpleName,
            )
            throw e
        }
        runChain(nodes, index + 1, env)
    }

    private suspend fun runModule(node: ModuleNode, type: String, env: RunEnv): Map<String, Any?> =
        runNetModule(type, node, env)
            ?: runNotifyModule(type, node, env)
            ?: runCommsModule(type, node, env)
            ?: runDeviceModule(type, node, env)
            ?: runMediaModule(type, node, env)
            ?: runConnectivityModule(type, node, env)
            ?: runFileModule(type, node, env)
            ?: runVisionModule(type, node, env)
            ?: runUiModule(type, node, env)
            ?: runPrivilegedModule(type, node, env)
            ?: runDataModule(type, node, env)
            ?: runToolModule(type, node, env)
            ?: mapOf("skipped" to true, "reason" to "Unknown module type $type")

    private fun evaluateFilter(rule: FilterRule, env: RunEnv): Boolean {
        val left = Expression.evaluate(rule.left, env.scope)
        val right = Expression.evaluate(rule.right, env.scope)
        return Expression.testCondition(left, rule.op, right)
    }

    private fun describeInput(node: ModuleNode, env: RunEnv): String {
        val spec = ModuleCatalog.specOrUnknown(node.type)
        if (spec.params.isEmpty()) return ""
        val resolved = spec.params.mapNotNull { p ->
            val raw = node.params[p.key] ?: return@mapNotNull null
            if (raw.isBlank()) return@mapNotNull null
            val shown = if (p.key.lowercase().contains("pass") || p.key == "bearer") "••••••"
            else Expression.render(raw, env.scope)
            p.key to shown
        }.toMap()
        return if (resolved.isEmpty()) "" else Values.encodePretty(resolved)
    }

    private companion object {
        const val TAG = "FlowEngine"
        const val RUN_TIMEOUT_MS = 5 * 60_000L
    }
}
