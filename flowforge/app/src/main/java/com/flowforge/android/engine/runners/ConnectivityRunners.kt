package com.flowforge.android.engine.runners

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.engine.Values
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.triggers.NfcWriteGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

suspend fun runConnectivityModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "wifi.connect" -> suggestWifi(node, env)
    "bluetooth.toggle" -> toggleBluetooth(node, env)
    "bluetooth.device" -> connectBluetoothDevice(node, env)
    "nfc.write" -> writeNfcTag(node, env)
    "location.track" -> trackLocation(node, env)
    "location.navigate" -> navigate(node, env)
    else -> null
}

private fun suggestWifi(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val ssid = env.text(node, "ssid").trim()
    require(ssid.isNotBlank()) { "Enter the network name" }
    val password = env.text(node, "password")

    val wifi = env.app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: error("Wi-Fi is unavailable on this device")

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        error("Connecting to a named network needs Android 10 or newer")
    }

    val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
    if (password.isNotBlank()) builder.setWpa2Passphrase(password)
    val suggestion = builder.build()

    // Replacing the previous suggestion keeps the list from growing every run.
    runCatching { wifi.removeNetworkSuggestions(listOf(suggestion)) }
    val status = wifi.addNetworkSuggestions(listOf(suggestion))
    val ok = status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
        status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE

    return mapOf(
        "suggested" to ok,
        "ssid" to ssid,
        "status" to status.toDouble(),
        "note" to "Android joins suggested networks on its own schedule and may ask you once.",
    )
}

private fun toggleBluetooth(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val manager = env.app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: error("This device has no Bluetooth")
    val requested = env.choice(node, "state", "On")
    val wantOn = when (requested) {
        "Off" -> false
        "Toggle" -> !adapter.isEnabled
        else -> true
    }

    // Android 13 removed the ability to toggle Bluetooth silently.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        env.app.startActivity(intent)
        return mapOf(
            "state" to if (wantOn) "On" else "Off",
            "changed" to false,
            "via" to "settings",
            "note" to "Android 13+ blocks silent toggling — opened Bluetooth settings instead. " +
                "Use the privileged Toggle a radio module for a silent switch.",
        )
    }

    @Suppress("DEPRECATION", "MissingPermission")
    val changed = if (wantOn) adapter.enable() else adapter.disable()
    return mapOf("state" to if (wantOn) "On" else "Off", "changed" to changed, "via" to "adapter")
}

@Suppress("MissingPermission")
private suspend fun connectBluetoothDevice(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val manager = env.app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: error("This device has no Bluetooth")
    val wanted = env.text(node, "device").trim()
    require(wanted.isNotBlank()) { "Enter the name of a paired device" }
    val connect = env.choice(node, "action", "Connect") == "Connect"

    val device: BluetoothDevice = runCatching { adapter.bondedDevices }.getOrNull()
        ?.firstOrNull { it.name?.contains(wanted, ignoreCase = true) == true }
        ?: error("No paired device whose name contains \"$wanted\"")

    // A2DP exposes connect/disconnect, but only through hidden methods — reflection is the
    // only route without system privileges, and it genuinely works on most builds.
    val done = withTimeoutOrNull(10_000L) {
        withContext(Dispatchers.IO) {
            runCatching {
                val proxy = openProfileProxy(env, adapter, BluetoothProfile.A2DP) ?: return@runCatching false
                val method = proxy.javaClass.getMethod(
                    if (connect) "connect" else "disconnect",
                    BluetoothDevice::class.java,
                )
                method.isAccessible = true
                val result = method.invoke(proxy, device) as? Boolean ?: false
                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                result
            }.getOrDefault(false)
        }
    } ?: false

    return mapOf(
        "done" to done,
        "device" to (device.name ?: wanted),
        "address" to device.address,
        "note" to if (done) "" else "The audio profile refused the request — some builds block this.",
    )
}

private suspend fun openProfileProxy(
    env: RunEnv,
    adapter: BluetoothAdapter,
    profile: Int,
): BluetoothProfile? = withTimeoutOrNull(5_000L) {
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile?) {
                if (cont.isActive) cont.resume(proxy)
            }

            override fun onServiceDisconnected(p: Int) = Unit
        }
        if (!adapter.getProfileProxy(env.app, listener, profile) && cont.isActive) {
            cont.resume(null)
        }
    }
}

private suspend fun writeNfcTag(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val payload = env.text(node, "payload")
    require(payload.isNotBlank()) { "Enter what to write to the tag" }
    val isUrl = env.choice(node, "kind", "Text") == "URL"
    val timeout = env.number(node, "timeout", 30.0).toLong().coerceIn(5, 300)

    val result = NfcWriteGate.arm(env.app, payload, isUrl, timeout * 1000)
    return mapOf(
        "written" to result.written,
        "id" to result.tagId,
        "bytes" to result.bytes.toDouble(),
        "error" to result.error,
    )
}

private object Tracking {
    var listener: LocationListener? = null
    var file: File? = null
}

@Suppress("MissingPermission")
private suspend fun trackLocation(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val lm = env.app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val action = env.choice(node, "action", "Start")

    if (action == "Stop") {
        Tracking.listener?.let { runCatching { lm.removeUpdates(it) } }
        Tracking.listener = null
        return mapOf("tracking" to false, "path" to (Tracking.file?.absolutePath ?: ""))
    }

    Tracking.listener?.let { runCatching { lm.removeUpdates(it) } }

    val target = resolveFile(env, env.text(node, "filename").ifBlank { "flowforge/track.jsonl" })
    target.parentFile?.mkdirs()
    Tracking.file = target

    val intervalMs = env.number(node, "intervalSeconds", 60.0).toLong().coerceAtLeast(1) * 1000
    val metres = env.number(node, "metres", 25.0).toFloat().coerceAtLeast(0f)

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val row = mapOf(
                "timestamp" to location.time.toDouble(),
                "time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(location.time),
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy.toDouble(),
                "speed" to location.speed.toDouble(),
            )
            runCatching { target.appendText(Values.encode(row) + "\n") }
        }

        @Deprecated("Required on older API levels")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> error("No location provider is enabled")
    }

    withContext(Dispatchers.Main) {
        lm.requestLocationUpdates(provider, intervalMs, metres, listener)
    }
    Tracking.listener = listener

    return mapOf("tracking" to true, "path" to target.absolutePath, "provider" to provider)
}

private fun navigate(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val destination = env.text(node, "destination").trim()
    require(destination.isNotBlank()) { "Enter an address or lat,lng" }
    val mode = when (env.choice(node, "mode", "Drive")) {
        "Walk" -> "w"
        "Cycle" -> "b"
        "Transit" -> "r"
        else -> "d"
    }
    val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$mode")
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (intent.resolveActivity(env.app.packageManager) == null) {
        // No navigation app — fall back to a plain maps lookup.
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(destination)}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        env.app.startActivity(fallback)
        return mapOf("opened" to true, "destination" to destination, "mode" to "map")
    }

    env.app.startActivity(intent)
    return mapOf("opened" to true, "destination" to destination, "mode" to mode)
}
