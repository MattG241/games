package com.flowforge.android.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull

/**
 * Runtime values are plain Kotlin: String, Double, Boolean, List<Any?>, Map<String, Any?>, null.
 * These helpers bridge that to JSON for storage, HTTP bodies and the run log.
 */
object Values {

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    val jsonPretty = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fromJson(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive ->
            if (element.isString) element.content
            else element.booleanOrNull ?: element.doubleOrNull ?: element.content
        is JsonArray -> element.map { fromJson(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to fromJson(v) }
    }

    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), toJson(v)) }
        }
        is Iterable<*> -> buildJsonArray { value.forEach { add(toJson(it)) } }
        is Array<*> -> buildJsonArray { value.forEach { add(toJson(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    fun parseJsonOrNull(text: String): Any? = try {
        if (text.isBlank()) null else fromJson(json.parseToJsonElement(text.trim()))
    } catch (_: Exception) {
        null
    }

    fun encode(value: Any?): String = json.encodeToString(JsonElement.serializer(), toJson(value))

    fun encodePretty(value: Any?): String =
        jsonPretty.encodeToString(JsonElement.serializer(), toJson(value))

    /** How a value appears when interpolated into a text field. */
    fun asText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Double -> if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15)
            value.toLong().toString() else value.toString()
        is Float -> asText(value.toDouble())
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Map<*, *>, is Iterable<*>, is Array<*> -> encode(value)
        else -> value.toString()
    }

    fun asNumber(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

    fun asBool(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotEmpty() && !value.equals("false", true) && value != "0"
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }

    fun asList(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is List<*> -> value
        is Iterable<*> -> value.toList()
        is Array<*> -> value.toList()
        is Map<*, *> -> value.values.toList()
        is String -> parseJsonOrNull(value)?.let { if (it is List<*>) it else listOf(it) } ?: listOf(value)
        else -> listOf(value)
    }

    /** Walks `a.b[0].c` style paths through nested maps/lists. */
    fun dig(root: Any?, path: String): Any? {
        var cur = root
        var i = 0
        val buf = StringBuilder()
        fun step(key: String) {
            if (key.isEmpty()) return
            cur = when (val c = cur) {
                is Map<*, *> -> c[key]
                is List<*> -> key.toIntOrNull()?.let { c.getOrNull(it) }
                else -> null
            }
        }
        while (i < path.length) {
            when (val ch = path[i]) {
                '.' -> { step(buf.toString()); buf.clear() }
                '[' -> {
                    step(buf.toString()); buf.clear()
                    val end = path.indexOf(']', i)
                    if (end < 0) return null
                    val idx = path.substring(i + 1, end).trim().trim('"', '\'')
                    cur = when (val c = cur) {
                        is List<*> -> idx.toIntOrNull()?.let { c.getOrNull(it) }
                        is Map<*, *> -> c[idx]
                        else -> null
                    }
                    i = end
                }
                else -> buf.append(ch)
            }
            i++
        }
        step(buf.toString())
        return cur
    }
}
