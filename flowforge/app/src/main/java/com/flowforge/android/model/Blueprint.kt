package com.flowforge.android.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A scenario is stored exactly like a Make.com blueprint: one JSON document holding an ordered
 * chain of modules. Module ids are stable and are what mapping expressions refer to, e.g. `{{3.body}}`.
 */
@Serializable
data class Blueprint(
    val id: String,
    val name: String = "Untitled scenario",
    val description: String = "",
    val enabled: Boolean = false,
    val modules: List<ModuleNode> = emptyList(),
    /** Seed values available to every run as `{{vars.name}}`. */
    val variables: Map<String, String> = emptyMap(),
    val updatedAt: Long = 0L,
) {
    val trigger: ModuleNode? get() = modules.firstOrNull()

    fun nextModuleId(): Int {
        var max = 0
        forEachNode { if (it.id > max) max = it.id }
        return max + 1
    }

    fun forEachNode(action: (ModuleNode) -> Unit) {
        fun walk(list: List<ModuleNode>) {
            for (n in list) {
                action(n)
                n.routes.forEach { walk(it.modules) }
            }
        }
        walk(modules)
    }

    fun findNode(id: Int): ModuleNode? {
        var found: ModuleNode? = null
        forEachNode { if (it.id == id) found = it }
        return found
    }

    /** Every module that executes before [id], in chain order — the valid mapping sources. */
    fun modulesBefore(id: Int): List<ModuleNode> {
        val acc = mutableListOf<ModuleNode>()
        fun walk(list: List<ModuleNode>): Boolean {
            for (n in list) {
                if (n.id == id) return true
                acc += n
                for (r in n.routes) {
                    val snapshot = acc.size
                    if (walk(r.modules)) return true
                    // A sibling route's modules are not visible outside that route.
                    while (acc.size > snapshot) acc.removeAt(acc.size - 1)
                }
            }
            return false
        }
        walk(modules)
        return acc
    }

    fun replaceNode(id: Int, transform: (ModuleNode) -> ModuleNode): Blueprint =
        copy(modules = mapList(modules) { if (it.id == id) transform(it) else it })

    fun removeNode(id: Int): Blueprint = copy(modules = removeIn(modules, id))

    /** Inserts [node] directly after module [afterId]; pass null to append to the root chain. */
    fun insertAfter(afterId: Int?, node: ModuleNode): Blueprint {
        if (afterId == null) return copy(modules = modules + node)
        return copy(modules = insertIn(modules, afterId, node))
    }

    fun insertInRoute(routerId: Int, routeIndex: Int, node: ModuleNode): Blueprint =
        replaceNode(routerId) { r ->
            val routes = r.routes.toMutableList()
            if (routeIndex !in routes.indices) return@replaceNode r
            routes[routeIndex] = routes[routeIndex].let { it.copy(modules = it.modules + node) }
            r.copy(routes = routes)
        }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun fromJson(text: String): Blueprint = json.decodeFromString(serializer(), text)

        private fun mapList(list: List<ModuleNode>, f: (ModuleNode) -> ModuleNode): List<ModuleNode> =
            list.map { n ->
                val mapped = f(n)
                if (mapped.routes.isEmpty()) mapped
                else mapped.copy(routes = mapped.routes.map { r -> r.copy(modules = mapList(r.modules, f)) })
            }

        private fun removeIn(list: List<ModuleNode>, id: Int): List<ModuleNode> =
            list.filter { it.id != id }.map { n ->
                if (n.routes.isEmpty()) n
                else n.copy(routes = n.routes.map { r -> r.copy(modules = removeIn(r.modules, id)) })
            }

        private fun insertIn(list: List<ModuleNode>, afterId: Int, node: ModuleNode): List<ModuleNode> {
            val out = mutableListOf<ModuleNode>()
            for (n in list) {
                val withRoutes =
                    if (n.routes.isEmpty()) n
                    else n.copy(routes = n.routes.map { r -> r.copy(modules = insertIn(r.modules, afterId, node)) })
                out += withRoutes
                if (n.id == afterId) out += node
            }
            return out
        }
    }
}

@Serializable
data class ModuleNode(
    val id: Int,
    val type: String,
    val label: String? = null,
    val params: Map<String, String> = emptyMap(),
    /** Only used by router modules. */
    val routes: List<Route> = emptyList(),
    /** Optional per-module filter — the flow stops here when it evaluates false. */
    val filter: FilterRule? = null,
) {
    fun param(key: String, fallback: String = ""): String = params[key]?.takeIf { it.isNotBlank() } ?: fallback
}

@Serializable
data class Route(
    val label: String = "Route",
    val filter: FilterRule? = null,
    val modules: List<ModuleNode> = emptyList(),
)

@Serializable
data class FilterRule(
    val left: String = "",
    val op: String = "equals",
    val right: String = "",
) {
    companion object {
        val OPERATORS = listOf(
            "equals", "not equals", "contains", "not contains", "starts with", "ends with",
            "matches regex", "greater than", "less than", "is empty", "is not empty", "is true",
        )
    }
}
