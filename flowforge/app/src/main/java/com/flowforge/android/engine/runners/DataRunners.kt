package com.flowforge.android.engine.runners

import com.flowforge.android.engine.EvalScope
import com.flowforge.android.engine.Expression
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.engine.Values
import com.flowforge.android.model.ModuleNode
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.roundToLong

suspend fun runDataModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "tool.jsonBuild" -> buildJson(node, env)
    "tool.text" -> textTools(node, env)
    "tool.math" -> math(node, env)
    "tool.datetime" -> dateTime(node, env)
    "tool.hash" -> hash(node, env)
    "tool.random" -> random(node, env)
    else -> null
}

private fun buildJson(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val fields = linkedMapOf<String, Any?>()
    env.text(node, "fields").lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains('=') }
        .forEach { line ->
            val key = line.substringBefore('=').trim()
            val raw = line.substringAfter('=').trim()
            if (key.isEmpty()) return@forEach
            fields[key] = when {
                raw.equals("true", true) -> true
                raw.equals("false", true) -> false
                raw.equals("null", true) -> null
                raw.toDoubleOrNull() != null -> raw.toDouble()
                raw.startsWith("{") || raw.startsWith("[") -> Values.parseJsonOrNull(raw) ?: raw
                else -> raw
            }
        }
    val text = if (env.bool(node, "pretty", false)) Values.encodePretty(fields) else Values.encode(fields)
    return mapOf("json" to fields, "text" to text)
}

private fun textTools(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val text = env.text(node, "text")
    val find = env.text(node, "find")
    val replacement = env.text(node, "replace")
    val useRegex = env.bool(node, "regex", false)

    return when (env.choice(node, "action", "Replace")) {
        "Replace" -> {
            val value = if (useRegex && find.isNotEmpty()) {
                Regex(find).replace(text, replacement)
            } else if (find.isNotEmpty()) {
                text.replace(find, replacement)
            } else {
                text
            }
            mapOf("value" to value, "length" to value.length.toDouble())
        }
        "Split" -> {
            val parts = if (useRegex && find.isNotEmpty()) text.split(Regex(find))
            else text.split(find.ifEmpty { "\n" })
            mapOf("value" to parts.firstOrNull().orEmpty(), "parts" to parts, "count" to parts.size.toDouble())
        }
        "Join" -> {
            val parts = Values.asList(Expression.evaluate(node.params["text"], env.scope))
            val value = parts.joinToString(find.ifEmpty { ", " }) { Values.asText(it) }
            mapOf("value" to value, "count" to parts.size.toDouble(), "length" to value.length.toDouble())
        }
        "Trim" -> mapOf("value" to text.trim(), "length" to text.trim().length.toDouble())
        "Upper" -> mapOf("value" to text.uppercase(), "length" to text.length.toDouble())
        "Lower" -> mapOf("value" to text.lowercase(), "length" to text.length.toDouble())
        "Reverse" -> mapOf("value" to text.reversed(), "length" to text.length.toDouble())
        "Substring" -> {
            val from = env.number(node, "from", 0.0).toInt().coerceIn(0, text.length)
            val to = node.params["to"]?.takeIf { it.isNotBlank() }
                ?.let { env.number(node, "to", text.length.toDouble()).toInt() }
                ?.coerceIn(from, text.length) ?: text.length
            val value = text.substring(from, to)
            mapOf("value" to value, "length" to value.length.toDouble())
        }
        "Pad" -> {
            val width = env.number(node, "to", 0.0).toInt().coerceIn(0, 1000)
            val padChar = replacement.firstOrNull() ?: '0'
            val value = text.padStart(width, padChar)
            mapOf("value" to value, "length" to value.length.toDouble())
        }
        else -> {
            // Template: render the text field again so nested {{ }} inside a variable resolve too.
            val value = Expression.render(text, env.scope)
            mapOf("value" to value, "length" to value.length.toDouble())
        }
    }
}

private fun math(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val rendered = env.text(node, "expression").trim()
    require(rendered.isNotBlank()) { "Enter an expression to evaluate" }
    // Re-run the resolved text through the expression engine so arithmetic on mapped values works.
    val evaluated = Expression.evaluate("{{$rendered}}", EvalScope(env.scope.bundles, env.scope.vars))
    val number = Values.asNumber(evaluated)
        ?: error("\"$rendered\" did not evaluate to a number")

    val places = node.params["round"]?.trim()?.toIntOrNull()
    val value = if (places == null) number else {
        val factor = Math.pow(10.0, places.toDouble())
        (number * factor).roundToLong() / factor
    }
    return mapOf(
        "value" to value,
        "text" to Values.asText(value),
        "integer" to value.toLong().toDouble(),
    )
}

private fun dateTime(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val pattern = env.text(node, "pattern").ifBlank { "yyyy-MM-dd HH:mm" }
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())

    fun bundle(timestamp: Long, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "text" to formatter.format(timestamp),
            "timestamp" to timestamp.toDouble(),
            "iso" to iso.format(timestamp),
        ) + extra

    return when (env.choice(node, "action", "Format now")) {
        "Format now" -> bundle(System.currentTimeMillis())
        "Format a value" -> bundle(parseMoment(env.text(node, "value")))
        "Parse" -> {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).parse(env.text(node, "value").trim())?.time
            }.getOrNull() ?: parseMoment(env.text(node, "value"))
            bundle(parsed)
        }
        "Add" -> {
            val base = parseMoment(env.text(node, "value"))
            val amount = env.number(node, "amount", 0.0)
            val millis = when (env.choice(node, "unit", "Minutes")) {
                "Hours" -> amount * 3_600_000
                "Days" -> amount * 86_400_000
                "Weeks" -> amount * 604_800_000
                else -> amount * 60_000
            }
            bundle(base + millis.toLong())
        }
        else -> {
            val a = parseMoment(env.text(node, "value"))
            val b = parseMoment(env.text(node, "other"))
            val delta = abs(a - b)
            bundle(
                a,
                mapOf(
                    "days" to (delta / 86_400_000.0),
                    "hours" to (delta / 3_600_000.0),
                    "minutes" to (delta / 60_000.0),
                    "seconds" to (delta / 1000.0),
                    "millis" to delta.toDouble(),
                ),
            )
        }
    }
}

private fun hash(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val algorithm = env.choice(node, "algorithm", "SHA-256")
    val text = env.text(node, "text")
    val key = env.text(node, "key")
    val asBase64 = env.choice(node, "output", "Hex") == "Base64"

    fun encode(bytes: ByteArray): String =
        if (asBase64) Base64.getEncoder().encodeToString(bytes)
        else bytes.joinToString("") { "%02x".format(it) }

    val value = when (algorithm) {
        "Base64 encode" -> Base64.getEncoder().encodeToString(text.toByteArray())
        "Base64 decode" -> runCatching { String(Base64.getDecoder().decode(text)) }
            .getOrElse { error("That is not valid Base64") }
        "URL encode" -> URLEncoder.encode(text, "UTF-8")
        "URL decode" -> runCatching { URLDecoder.decode(text, "UTF-8") }
            .getOrElse { error("That is not a valid URL-encoded string") }
        "HMAC-SHA256", "HMAC-SHA1" -> {
            require(key.isNotBlank()) { "$algorithm needs a key" }
            val spec = if (algorithm == "HMAC-SHA256") "HmacSHA256" else "HmacSHA1"
            val mac = Mac.getInstance(spec)
            mac.init(SecretKeySpec(key.toByteArray(), spec))
            encode(mac.doFinal(text.toByteArray()))
        }
        else -> encode(MessageDigest.getInstance(algorithm).digest(text.toByteArray()))
    }
    return mapOf("value" to value, "algorithm" to algorithm)
}

private fun random(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val value: Any = when (env.choice(node, "kind", "Integer")) {
        "Decimal" -> {
            val min = env.number(node, "min", 0.0)
            val max = env.number(node, "max", 1.0)
            min + Math.random() * (max - min)
        }
        "UUID" -> UUID.randomUUID().toString()
        "Pick from list" -> {
            val options = env.text(node, "list").lines().map { it.trim() }.filter { it.isNotEmpty() }
            require(options.isNotEmpty()) { "Add some options to pick from" }
            options.random()
        }
        "Token" -> {
            val length = env.number(node, "length", 16.0).toInt().coerceIn(4, 256)
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            (1..length).map { alphabet.random() }.joinToString("")
        }
        else -> {
            val min = env.number(node, "min", 1.0).toLong()
            val max = env.number(node, "max", 100.0).toLong()
            (min..maxOf(min, max)).random().toDouble()
        }
    }
    return mapOf("value" to value, "text" to Values.asText(value))
}
