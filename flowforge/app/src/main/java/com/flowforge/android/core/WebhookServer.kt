package com.flowforge.android.core

import android.content.Context
import android.net.wifi.WifiManager
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.engine.Values
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A small HTTP listener so anything on the same network — a laptop, another phone, Home Assistant,
 * a Make.com HTTP module — can start a scenario. One server serves every webhook trigger; the
 * request path selects the scenario.
 */
class WebhookServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val app = FlowForgeApp.instance
        val path = session.uri.trim('/')
        val method = session.method.name

        val body = readBody(session)
        val query = session.parms.toMap()
        val headers = session.headers.toMap()

        val matches = app.scenarios.scenarios.value.filter { blueprint ->
            val trigger = blueprint.trigger ?: return@filter false
            if (!blueprint.enabled || trigger.type != "trigger.webhook") return@filter false
            val wantPath = trigger.param("path", "hook").trim('/')
            val wantMethod = trigger.param("method", "ANY")
            val secret = trigger.param("secret").trim()
            val presented = query["key"] ?: headers["x-key"] ?: ""
            wantPath.equals(path, ignoreCase = true) &&
                (wantMethod == "ANY" || wantMethod.equals(method, ignoreCase = true)) &&
                (secret.isEmpty() || secret == presented)
        }

        if (matches.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error":"No enabled scenario is listening on /$path"}""",
            )
        }

        val bundle = mapOf(
            "body" to body,
            "json" to Values.parseJsonOrNull(body),
            "query" to query,
            "headers" to headers,
            "method" to method,
            "path" to path,
            "ip" to (headers["http-client-ip"] ?: headers["remote-addr"] ?: ""),
        )

        // Give the scenario a short window to send its own reply via the Webhook response module.
        val latch = CountDownLatch(1)
        var status = 200
        var contentType = "application/json"
        var replyBody = """{"ok":true,"scenarios":${matches.size}}"""
        var custom = false

        val firstResponder: (Int, String, String) -> Unit = { s, c, b ->
            status = s
            contentType = c
            replyBody = b
            custom = true
            latch.countDown()
        }

        matches.forEachIndexed { index, blueprint ->
            val responder = if (index == 0) firstResponder else null

            app.launchScenario(blueprint, bundle, "Webhook /$path", responder) {
                if (index == 0) latch.countDown()
            }
        }

        runCatching { latch.await(20, TimeUnit.SECONDS) }
        if (!custom) {
            replyBody = """{"ok":true,"scenarios":${matches.size}}"""
        }
        return newFixedLengthResponse(
            Response.Status.lookup(status) ?: Response.Status.OK,
            contentType,
            replyBody,
        )
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        return runCatching {
            session.parseBody(files)
            files["postData"] ?: files["content"] ?: ""
        }.getOrDefault("")
    }

    companion object {
        /** Best-effort local address so the UI can show a copyable webhook URL. */
        fun localAddress(context: Context): String {
            runCatching {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val ip = wifi?.connectionInfo?.ipAddress ?: 0
                if (ip != 0) {
                    return "%d.%d.%d.%d".format(
                        ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
                    )
                }
            }
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
                    ?.let { return it }
            }
            return "127.0.0.1"
        }
    }
}
