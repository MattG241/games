package com.flowforge.android.engine.runners

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.KeyEvent
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

suspend fun runDeviceModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "app.open" -> openApp(node, env)
    "intent.send" -> sendIntent(node, env)
    "device.url" -> openUrl(node, env)
    "clipboard.set" -> setClipboard(node, env)
    "clipboard.get" -> getClipboard(env)
    "device.vibrate" -> vibrate(node, env)
    "device.volume" -> setVolume(node, env)
    "device.ringer" -> setRinger(node, env)
    "device.dnd" -> setDnd(node, env)
    "device.brightness" -> setBrightness(node, env)
    "device.torch" -> torch(node, env)
    "device.media" -> mediaControl(node, env)
    "device.location" -> getLocation(node, env)
    "device.info" -> deviceInfo(env)
    "device.settingsPanel" -> openPanel(node, env)
    else -> null
}

private fun openApp(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val pkg = env.text(node, "package").trim()
    require(pkg.isNotBlank()) { "Pick an app to open" }
    val intent = env.app.packageManager.getLaunchIntentForPackage(pkg)
        ?: error("$pkg has no launchable activity")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    env.app.startActivity(intent)
    return mapOf("package" to pkg, "launched" to true)
}

private fun sendIntent(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val action = env.text(node, "action").trim()
    val uri = env.text(node, "uri").trim()
    val pkg = env.text(node, "package").trim()
    val component = env.text(node, "component").trim()
    val mime = env.text(node, "mimeType").trim()

    val intent = Intent()
    if (action.isNotBlank()) intent.action = action
    if (uri.isNotBlank() && mime.isNotBlank()) intent.setDataAndType(Uri.parse(uri), mime)
    else {
        if (uri.isNotBlank()) intent.data = Uri.parse(uri)
        if (mime.isNotBlank()) intent.type = mime
    }
    if (pkg.isNotBlank()) intent.setPackage(pkg)
    if (pkg.isNotBlank() && component.isNotBlank()) intent.setClassName(pkg, component)

    env.text(node, "extras").lineSequence()
        .map { it.trim() }
        .filter { it.contains('=') }
        .forEach { line ->
            val key = line.substringBefore('=').trim()
            val raw = line.substringAfter('=').trim()
            when {
                key.isEmpty() -> Unit
                raw.equals("true", true) || raw.equals("false", true) -> intent.putExtra(key, raw.toBoolean())
                raw.toLongOrNull() != null -> intent.putExtra(key, raw.toLong())
                raw.toDoubleOrNull() != null -> intent.putExtra(key, raw.toDouble())
                else -> intent.putExtra(key, raw)
            }
        }

    when (env.choice(node, "kind", "Activity")) {
        "Broadcast" -> env.app.sendBroadcast(intent)
        "Service" -> {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) env.app.startForegroundService(intent)
            else env.app.startService(intent)
        }
        else -> {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            env.app.startActivity(intent)
        }
    }
    return mapOf("sent" to true, "action" to action, "uri" to uri)
}

private fun openUrl(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val url = env.text(node, "url").trim()
    require(url.isNotBlank()) { "Enter a URL to open" }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    env.app.startActivity(intent)
    return mapOf("opened" to true, "url" to url)
}

private fun setClipboard(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val text = env.text(node, "text")
    val cm = env.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("FlowForge", text))
    return mapOf("text" to text)
}

private fun getClipboard(env: RunEnv): Map<String, Any?> {
    val cm = env.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(env.app)?.toString().orEmpty()
    // Android 10+ only hands the clipboard to the focused app, so this can legitimately be empty.
    return mapOf("text" to text)
}

private fun vibrate(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val pattern = env.text(node, "pattern").ifBlank { "0,200,100,200" }
        .split(',').mapNotNull { it.trim().toLongOrNull() }.toLongArray()
    if (pattern.isEmpty()) return mapOf("vibrated" to false)

    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (env.app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        env.app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    return mapOf("vibrated" to true, "durationMs" to pattern.sum().toDouble())
}

private fun setVolume(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val am = env.app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val stream = when (env.choice(node, "stream", "Media")) {
        "Ring" -> AudioManager.STREAM_RING
        "Notification" -> AudioManager.STREAM_NOTIFICATION
        "Alarm" -> AudioManager.STREAM_ALARM
        "Call" -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }
    val percent = env.number(node, "percent", 50.0).coerceIn(0.0, 100.0)
    val max = am.getStreamMaxVolume(stream)
    val level = Math.round(max * percent / 100.0).toInt().coerceIn(0, max)
    am.setStreamVolume(stream, level, 0)
    return mapOf("stream" to env.choice(node, "stream", "Media"), "level" to level.toDouble(), "max" to max.toDouble())
}

private fun setRinger(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val am = env.app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val nm = env.app.getSystemService(NotificationManager::class.java)
    val mode = env.choice(node, "mode", "Silent")
    if (mode == "Silent" && nm?.isNotificationPolicyAccessGranted == false) {
        error("Silent mode needs Do Not Disturb access — grant it in Settings inside FlowForge")
    }
    am.ringerMode = when (mode) {
        "Normal" -> AudioManager.RINGER_MODE_NORMAL
        "Vibrate" -> AudioManager.RINGER_MODE_VIBRATE
        else -> AudioManager.RINGER_MODE_SILENT
    }
    return mapOf("mode" to mode)
}

private fun setDnd(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val nm = env.app.getSystemService(NotificationManager::class.java)
        ?: error("Notification service unavailable")
    if (!nm.isNotificationPolicyAccessGranted) {
        error("Do Not Disturb needs policy access — grant it in Settings inside FlowForge")
    }
    val mode = env.choice(node, "mode", "On")
    nm.setInterruptionFilter(
        when (mode) {
            "Off" -> NotificationManager.INTERRUPTION_FILTER_ALL
            "Priority only" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            "Alarms only" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            else -> NotificationManager.INTERRUPTION_FILTER_NONE
        }
    )
    return mapOf("mode" to mode)
}

private fun setBrightness(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    if (!Settings.System.canWrite(env.app)) {
        error("Brightness needs the Modify system settings permission — grant it in Settings inside FlowForge")
    }
    val percent = env.number(node, "percent", 60.0).coerceIn(0.0, 100.0)
    if (env.bool(node, "auto", true)) {
        Settings.System.putInt(
            env.app.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }
    val value = Math.round(255.0 * percent / 100.0).toInt().coerceIn(1, 255)
    Settings.System.putInt(env.app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
    return mapOf("percent" to percent, "raw" to value.toDouble())
}

private object TorchState { var on = false }

private fun torch(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val cm = env.app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cm.cameraIdList.firstOrNull { id ->
        cm.getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: error("This device has no flash")

    val requested = env.choice(node, "state", "On")
    val on = when (requested) {
        "Off" -> false
        "Toggle" -> !TorchState.on
        else -> true
    }
    cm.setTorchMode(cameraId, on)
    TorchState.on = on
    return mapOf("state" to if (on) "On" else "Off")
}

private fun mediaControl(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val am = env.app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val action = env.choice(node, "action", "Play/Pause")
    val code = when (action) {
        "Play" -> KeyEvent.KEYCODE_MEDIA_PLAY
        "Pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "Next" -> KeyEvent.KEYCODE_MEDIA_NEXT
        "Previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        "Stop" -> KeyEvent.KEYCODE_MEDIA_STOP
        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    }
    val now = System.currentTimeMillis()
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    return mapOf("action" to action)
}

@Suppress("MissingPermission")
private suspend fun getLocation(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val lm = env.app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fine = env.choice(node, "accuracy", "Coarse") == "Fine"
    val provider = when {
        fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> error("No location provider is enabled")
    }
    val maxAge = env.number(node, "maxAgeMinutes", 10.0) * 60_000.0

    val cached = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    val fresh = cached?.takeIf { System.currentTimeMillis() - it.time <= maxAge }
    val location: Location = fresh ?: withTimeoutOrNull(30_000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(l: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(l)
                    }

                    @Deprecated("Required on older API levels")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(p: String) = Unit
                    override fun onProviderDisabled(p: String) = Unit
                }
                runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener) }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
    } ?: cached ?: error("Could not get a location fix")

    return mapOf(
        "latitude" to location.latitude,
        "longitude" to location.longitude,
        "accuracy" to location.accuracy.toDouble(),
        "provider" to location.provider,
        "age" to ((System.currentTimeMillis() - location.time) / 1000.0),
    )
}

private fun deviceInfo(env: RunEnv): Map<String, Any?> {
    val app = env.app
    val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val cm = app.getSystemService(ConnectivityManager::class.java)
    val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    val wifi = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
    val network = when {
        caps == null -> "none"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }

    val stat = StatFs(Environment.getDataDirectory().path)
    val freeMb = stat.availableBytes / (1024.0 * 1024.0)
    val now = System.currentTimeMillis()

    return mapOf(
        "battery" to battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toDouble(),
        "charging" to battery.isCharging,
        "network" to network,
        "ssid" to (runCatching { wifi?.connectionInfo?.ssid?.trim('"') }.getOrNull() ?: ""),
        "wifiEnabled" to (wifi?.isWifiEnabled ?: false),
        "airplane" to (Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1),
        "screenOn" to pm.isInteractive,
        "ringerMode" to when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        },
        "volumeMedia" to am.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble(),
        "freeStorageMb" to Math.round(freeMb).toDouble(),
        "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "androidVersion" to Build.VERSION.RELEASE,
        "time" to SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
        "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
        "timestamp" to now.toDouble(),
    )
}

private fun openPanel(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val panel = env.choice(node, "panel", "Wi-Fi")
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (panel) {
            "Wi-Fi" -> Intent(Settings.Panel.ACTION_WIFI)
            "Internet" -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "Volume" -> Intent(Settings.Panel.ACTION_VOLUME)
            "NFC" -> Intent(Settings.Panel.ACTION_NFC)
            "Bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "Airplane mode" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${env.app.packageName}"))
        }
    } else {
        when (panel) {
            "Wi-Fi", "Internet" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "Bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "Airplane mode" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
    }
    env.app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return mapOf("panel" to panel)
}
