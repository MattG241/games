package com.flowforge.android.engine.runners

import com.flowforge.android.engine.RunEnv
import com.flowforge.android.engine.Values
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private val sharedClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

suspend fun runNetModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "http.request" -> httpRequest(node, env)
    "http.download" -> httpDownload(node, env)
    "http.upload" -> httpUpload(node, env)
    "net.ping" -> ping(node, env)
    "net.websocket" -> webSocketSend(node, env)
    "net.mqtt" -> mqttPublish(node, env)
    "webhook.respond" -> webhookRespond(node, env)
    else -> null
}

private fun Request.Builder.applyHeaderLines(raw: String) = apply {
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains(':') }
        .forEach { line ->
            val name = line.substringBefore(':').trim()
            val value = line.substringAfter(':').trim()
            if (name.isNotEmpty()) header(name, value)
        }
}

private fun Response.toBundle(parseJson: Boolean): Map<String, Any?> {
    val text = body?.string().orEmpty()
    return mapOf(
        "status" to code.toDouble(),
        "ok" to isSuccessful,
        "body" to text,
        "json" to if (parseJson) Values.parseJsonOrNull(text) else null,
        "headers" to headers.toMultimap().mapValues { it.value.joinToString(", ") },
    )
}

private suspend fun httpRequest(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = env.text(node, "url").trim()
    require(url.startsWith("http")) { "HTTP request needs an absolute http(s) URL (got \"$url\")" }

    val method = env.choice(node, "method", "GET").uppercase()
    val contentType = env.choice(node, "contentType", "application/json")
    val bodyText = env.text(node, "body")
    val timeout = env.number(node, "timeout", 30.0).toLong().coerceIn(1, 300)

    val builder = Request.Builder().url(url).applyHeaderLines(env.text(node, "headers"))

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

    client.newCall(builder.build()).execute().use { it.toBundle(env.bool(node, "parseJson", true)) }
}

private suspend fun httpDownload(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = env.text(node, "url").trim()
    require(url.startsWith("http")) { "Download needs an absolute http(s) URL" }
    val name = env.text(node, "filename").ifBlank { url.substringAfterLast('/').ifBlank { "download.bin" } }
    val target = resolveFile(env, name)
    target.parentFile?.mkdirs()

    val request = Request.Builder().url(url).applyHeaderLines(env.text(node, "headers")).build()
    sharedClient.newCall(request).execute().use { response ->
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

private suspend fun httpUpload(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = env.text(node, "url").trim()
    require(url.startsWith("http")) { "Upload needs an absolute http(s) URL" }
    val file = resolveFile(env, env.text(node, "path"))
    require(file.exists()) { "No file at ${file.absolutePath}" }

    val mime = env.text(node, "mimeType").ifBlank { "application/octet-stream" }
    val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
    env.text(node, "fields").lineSequence()
        .map { it.trim() }
        .filter { it.contains('=') }
        .forEach { multipart.addFormDataPart(it.substringBefore('='), it.substringAfter('=')) }
    multipart.addFormDataPart(
        env.text(node, "field").ifBlank { "file" },
        file.name,
        file.asRequestBody(mime.toMediaTypeOrNull()),
    )

    val request = Request.Builder().url(url)
        .applyHeaderLines(env.text(node, "headers"))
        .post(multipart.build())
        .build()

    sharedClient.newCall(request).execute().use { it.toBundle(true) }
}

private suspend fun ping(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val host = env.text(node, "host").trim()
        .removePrefix("https://").removePrefix("http://").substringBefore('/')
    require(host.isNotBlank()) { "Enter a host or IP to check" }
    val port = env.number(node, "port", 0.0).toInt()
    val timeout = env.number(node, "timeout", 3000.0).toInt().coerceIn(100, 60_000)

    val started = System.nanoTime()
    val address = runCatching { InetAddress.getByName(host) }.getOrNull()
    val reachable = when {
        address == null -> false
        port > 0 -> runCatching {
            Socket().use { it.connect(InetSocketAddress(address, port), timeout); true }
        }.getOrDefault(false)
        else -> runCatching { address.isReachable(timeout) }.getOrDefault(false)
    }
    val latency = (System.nanoTime() - started) / 1_000_000.0

    mapOf(
        "reachable" to reachable,
        "latencyMs" to Math.round(latency).toDouble(),
        "host" to host,
        "address" to (address?.hostAddress ?: ""),
        "port" to port.toDouble(),
    )
}

private suspend fun webSocketSend(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val url = env.text(node, "url").trim()
    require(url.startsWith("ws")) { "WebSocket needs a ws:// or wss:// URL" }
    val message = env.text(node, "message")
    val wait = env.bool(node, "waitForReply", true)
    val timeout = env.number(node, "timeout", 10.0).toLong().coerceIn(1, 120)

    val request = Request.Builder().url(url).applyHeaderLines(env.text(node, "headers")).build()

    var socket: WebSocket? = null
    val reply = withTimeoutOrNull(timeout * 1000) {
        suspendCancellableCoroutine { cont ->
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(message)
                    if (!wait && cont.isActive) cont.resume(null)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (cont.isActive) cont.resume(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            socket = sharedClient.newWebSocket(request, listener)
            cont.invokeOnCancellation { runCatching { socket?.cancel() } }
        }
    }
    runCatching { socket?.close(1000, "done") }

    mapOf(
        "sent" to true,
        "reply" to reply,
        "json" to reply?.let { Values.parseJsonOrNull(it) },
        "closed" to true,
    )
}

/**
 * A minimal MQTT 3.1.1 publisher: CONNECT, PUBLISH at QoS 0, DISCONNECT. Small enough to hand-roll,
 * which keeps a whole MQTT client library out of the app.
 */
private suspend fun mqttPublish(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val host = env.text(node, "host").trim()
    require(host.isNotBlank()) { "Enter the broker host" }
    val port = env.number(node, "port", 1883.0).toInt().coerceIn(1, 65535)
    val topic = env.text(node, "topic").trim()
    require(topic.isNotBlank()) { "Enter a topic to publish to" }
    val payload = env.text(node, "message").toByteArray()
    val clientId = env.text(node, "clientId").ifBlank { "flowforge" }
    val username = env.text(node, "username")
    val password = env.text(node, "password")
    val retain = env.bool(node, "retain", false)

    Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), 10_000)
        socket.soTimeout = 10_000
        val out = DataOutputStream(socket.getOutputStream())

        // CONNECT
        val variableHeader = mutableListOf<Byte>()
        variableHeader += encodeMqttString("MQTT")
        variableHeader += 0x04.toByte() // protocol level 3.1.1
        var flags = 0x02 // clean session
        if (username.isNotEmpty()) flags = flags or 0x80
        if (password.isNotEmpty()) flags = flags or 0x40
        variableHeader += flags.toByte()
        variableHeader += 0x00.toByte() // keep alive, high byte
        variableHeader += 0x3C.toByte() // keep alive, 60 seconds
        variableHeader += encodeMqttString(clientId)
        if (username.isNotEmpty()) variableHeader += encodeMqttString(username)
        if (password.isNotEmpty()) variableHeader += encodeMqttString(password)
        out.write(mqttPacket(0x10, variableHeader.toByteArray()))
        out.flush()

        // CONNACK
        val header = ByteArray(4)
        val read = socket.getInputStream().read(header)
        require(read >= 4 && header[0].toInt() and 0xF0 == 0x20) { "Broker did not accept the connection" }
        val returnCode = header[3].toInt()
        require(returnCode == 0) { "Broker refused the connection (code $returnCode)" }

        // PUBLISH (QoS 0)
        val publishBody = mutableListOf<Byte>()
        publishBody += encodeMqttString(topic)
        publishBody += payload.toList()
        out.write(mqttPacket(if (retain) 0x31 else 0x30, publishBody.toByteArray()))
        out.flush()

        // DISCONNECT
        out.write(mqttPacket(0xE0, ByteArray(0)))
        out.flush()
    }

    mapOf("published" to true, "topic" to topic, "bytes" to payload.size.toDouble())
}

private fun encodeMqttString(value: String): List<Byte> {
    val bytes = value.toByteArray()
    return listOf((bytes.size shr 8).toByte(), (bytes.size and 0xFF).toByte()) + bytes.toList()
}

private fun mqttPacket(header: Int, body: ByteArray): ByteArray {
    val length = mutableListOf<Byte>()
    var remaining = body.size
    do {
        var digit = remaining % 128
        remaining /= 128
        if (remaining > 0) digit = digit or 0x80
        length += digit.toByte()
    } while (remaining > 0)
    return byteArrayOf(header.toByte()) + length.toByteArray() + body
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
