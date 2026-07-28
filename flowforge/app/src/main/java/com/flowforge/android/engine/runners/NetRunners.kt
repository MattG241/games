package com.flowforge.android.engine.runners

import com.flowforge.android.engine.RunEnv
import com.flowforge.android.engine.Values
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private val sharedClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

suspend fun runNetModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "http.request" -> httpRequest(node, env)
    "http.download" -> httpDownload(node, env)
    "webhook.respond" -> webhookRespond(node, env)
    else -> null
}

private suspend fun httpRequest(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = env.text(node, "url").trim()
    require(url.startsWith("http")) { "HTTP request needs an absolute http(s) URL (got \"$url\")" }

    val method = env.choice(node, "method", "GET").uppercase()
    val contentType = env.choice(node, "contentType", "application/json")
    val bodyText = env.text(node, "body")
    val timeout = env.number(node, "timeout", 30.0).toLong().coerceIn(1, 300)

    val builder = Request.Builder().url(url)

    env.text(node, "headers").lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains(':') }
        .forEach { line ->
            val name = line.substringBefore(':').trim()
            val value = line.substringAfter(':').trim()
            if (name.isNotEmpty()) builder.header(name, value)
        }

    env.text(node, "bearer").takeIf { it.isNotBlank() }?.let {
        builder.header("Authorization", "Bearer $it")
    }
    val user = env.text(node, "basicUser")
    if (user.isNotBlank()) {
        builder.header("Authorization", Credentials.basic(user, env.text(node, "basicPass")))
    }

    val hasBody = method in setOf("POST", "PUT", "PATCH", "DELETE") && contentType != "none"
    val requestBody = if (hasBody) bodyText.toRequestBody(contentType.toMediaTypeOrNull()) else null
    when (method) {
        "GET" -> builder.get()
        "HEAD" -> builder.head()
        else -> builder.method(method, requestBody)
    }

    val client = sharedClient.newBuilder()
        .callTimeout(timeout, TimeUnit.SECONDS)
        .readTimeout(timeout, TimeUnit.SECONDS)
        .build()

    client.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        val parsed = if (env.bool(node, "parseJson", true)) Values.parseJsonOrNull(text) else null
        mapOf(
            "status" to response.code.toDouble(),
            "ok" to response.isSuccessful,
            "body" to text,
            "json" to parsed,
            "headers" to response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
        )
    }
}

private suspend fun httpDownload(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = env.text(node, "url").trim()
    require(url.startsWith("http")) { "Download needs an absolute http(s) URL" }
    val name = env.text(node, "filename").ifBlank { url.substringAfterLast('/').ifBlank { "download.bin" } }
    val target = resolveFile(env, name)
    target.parentFile?.mkdirs()

    sharedClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("Download failed with HTTP ${response.code}")
        val bytes = response.body?.bytes() ?: ByteArray(0)
        target.writeBytes(bytes)
        mapOf(
            "path" to target.absolutePath,
            "bytes" to bytes.size.toDouble(),
            "uri" to android.net.Uri.fromFile(target).toString(),
        )
    }
}

private fun webhookRespond(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val responder = env.webhookResponder
        ?: return mapOf("sent" to false, "reason" to "This run was not started by a webhook")
    val status = env.number(node, "status", 200.0).toInt()
    val contentType = env.text(node, "contentType").ifBlank { "application/json" }
    val body = env.text(node, "body")
    responder(status, contentType, body)
    return mapOf("sent" to true, "status" to status.toDouble())
}

/** Bare names land in the app's own Documents folder; absolute paths are used as given. */
internal fun resolveFile(env: RunEnv, path: String): File {
    val trimmed = path.trim()
    if (trimmed.startsWith("/")) return File(trimmed)
    val base = env.app.getExternalFilesDir(null) ?: env.app.filesDir
    return File(base, trimmed)
}
