package com.flowforge.android.engine.runners

import android.app.NotificationManager
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import android.provider.AlarmClock
import android.provider.Settings
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.triggers.FlowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

suspend fun runDeviceModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "app.open" -> openApp(node, env)
    "app.home" -> goHome(node, env)
    "intent.send" -> sendIntent(node, env)
    "device.url" -> openUrl(node, env)
    "clipboard.set" -> setClipboard(node, env)
    "clipboard.get" -> getClipboard(env)
    "device.vibrate" -> vibrate(node, env)
    "device.volume" -> setVolume(node, env)
    "device.ringer" -> setRinger(node, env)
    "device.dnd" -> setDnd(node, env)
    "device.brightness" -> setBrightness(node, env)
    "device.screenTimeout" -> setScreenTimeout(node, env)
    "device.torch" -> torch(node, env)
    "device.wakelock" -> wakelock(node, env)
    "device.lock" -> lockScreen(env)
    "device.wallpaper" -> setWallpaper(node, env)
    "device.location" -> getLocation(node, env)
    "device.info" -> deviceInfo(env)
    "device.foregroundApp" -> foregroundApp(env)
    "device.sensors" -> sensorSnapshot(node, env)
    "device.settingsPanel" -> openPanel(node, env)
    "clock.alarm" -> setAlarm(node, env)
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

private fun goHome(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val action = env.choice(node, "action", "Go home")
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    env.app.startActivity(intent)
    return mapOf("action" to action)
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

    vibrator(env).vibrate(VibrationEffect.createWaveform(pattern, -1))
    return mapOf("vibrated" to true, "durationMs" to pattern.sum().toDouble())
}

private fun vibrator(env: RunEnv): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (env.app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        env.app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

private fun setVolume(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val am = env.app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val name = env.choice(node, "stream", "Media")
    val stream = audioStream(name)
    val percent = env.number(node, "percent", 50.0).coerceIn(0.0, 100.0)
    val max = am.getStreamMaxVolume(stream)
    val level = Math.round(max * percent / 100.0).toInt().coerceIn(0, max)
    am.setStreamVolume(stream, level, 0)
    return mapOf("stream" to name, "level" to level.toDouble(), "max" to max.toDouble())
}

internal fun audioStream(name: String): Int = when (name) {
    "Ring" -> AudioManager.STREAM_RING
    "Notification" -> AudioManager.STREAM_NOTIFICATION
    "Alarm" -> AudioManager.STREAM_ALARM
    "Call" -> AudioManager.STREAM_VOICE_CALL
    else -> AudioManager.STREAM_MUSIC
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

private fun setScreenTimeout(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    if (!Settings.System.canWrite(env.app)) {
        error("Screen timeout needs the Modify system settings permission — grant it in Settings inside FlowForge")
    }
    val seconds = env.number(node, "seconds", 60.0).toInt().coerceIn(5, 60 * 60)
    Settings.System.putInt(
        env.app.contentResolver,
        Settings.System.SCREEN_OFF_TIMEOUT,
        seconds * 1000,
    )
    return mapOf("seconds" to seconds.toDouble())
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

private object Wakelocks { var held: PowerManager.WakeLock? = null }

@Suppress("DEPRECATION")
private fun wakelock(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val pm = env.app.getSystemService(Context.POWER_SERVICE) as PowerManager
    val action = env.choice(node, "action", "Acquire")

    if (action == "Release") {
        val existing = Wakelocks.held
        runCatching { if (existing?.isHeld == true) existing.release() }
        Wakelocks.held = null
        return mapOf("held" to false, "action" to action)
    }

    runCatching { Wakelocks.held?.takeIf { it.isHeld }?.release() }
    val screenOn = env.choice(node, "kind", "Screen on") == "Screen on"
    val flags = if (screenOn) {
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
    } else {
        PowerManager.PARTIAL_WAKE_LOCK
    }
    val minutes = env.number(node, "minutes", 10.0).coerceIn(1.0, 8 * 60.0)
    val lock = pm.newWakeLock(flags, "FlowForge::scenario")
    lock.acquire((minutes * 60_000).toLong())
    Wakelocks.held = lock
    return mapOf("held" to true, "action" to action, "minutes" to minutes)
}

private fun lockScreen(env: RunEnv): Map<String, Any?> {
    val service = FlowAccessibilityService.instance
        ?: error("Locking the screen needs the FlowForge accessibility service — turn it on in Settings inside FlowForge")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        error("Locking the screen this way needs Android 9 or newer")
    }
    val locked = service.performGlobal("Lock screen")
    return mapOf("locked" to locked, "via" to "accessibility")
}

private suspend fun setWallpaper(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val file = resolveFile(env, env.text(node, "path"))
    require(file.exists()) { "No image at ${file.absolutePath}" }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ?: error("That file is not an image Android can decode")

    val manager = WallpaperManager.getInstance(env.app)
    val target = env.choice(node, "target", "Home")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val flags = when (target) {
            "Lock" -> WallpaperManager.FLAG_LOCK
            "Both" -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
            else -> WallpaperManager.FLAG_SYSTEM
        }
        manager.setBitmap(bitmap, null, true, flags)
    } else {
        manager.setBitmap(bitmap)
    }
    mapOf("applied" to true, "target" to target)
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
        "altitude" to location.altitude,
        "speed" to location.speed.toDouble(),
    )
}

private fun deviceInfo(env: RunEnv): Map<String, Any?> {
    val app = env.app
    val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val cm = app.getSystemService(ConnectivityManager::class.java)
    val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    val km = app.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
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
    val now = System.currentTimeMillis()

    @Suppress("DEPRECATION")
    val info = runCatching { wifi?.connectionInfo }.getOrNull()

    return mapOf(
        "battery" to battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toDouble(),
        "charging" to battery.isCharging,
        "network" to network,
        "ssid" to (info?.ssid?.trim('"') ?: ""),
        "signal" to (info?.rssi?.toDouble() ?: 0.0),
        "wifiEnabled" to (wifi?.isWifiEnabled ?: false),
        "airplane" to (Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1),
        "screenOn" to pm.isInteractive,
        "locked" to km.isKeyguardLocked,
        "ringerMode" to when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        },
        "volumeMedia" to am.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble(),
        "freeStorageMb" to Math.round(stat.availableBytes / (1024.0 * 1024.0)).toDouble(),
        "totalStorageMb" to Math.round(stat.totalBytes / (1024.0 * 1024.0)).toDouble(),
        "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "androidVersion" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toDouble(),
        "time" to SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
        "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
        "timestamp" to now.toDouble(),
    )
}

internal fun currentForegroundPackage(context: Context): Pair<String, Long>? {
    val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
    val now = System.currentTimeMillis()
    val stats = runCatching {
        usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
    }.getOrNull().orEmpty()
    val top = stats.maxByOrNull { it.lastTimeUsed } ?: return null
    if (top.packageName.isNullOrBlank()) return null
    return top.packageName to top.lastTimeUsed
}

private fun foregroundApp(env: RunEnv): Map<String, Any?> {
    val top = currentForegroundPackage(env.app)
        ?: error("Foreground app needs usage access — grant it in Settings inside FlowForge")
    val label = runCatching {
        env.app.packageManager.getApplicationLabel(
            env.app.packageManager.getApplicationInfo(top.first, 0)
        ).toString()
    }.getOrDefault(top.first)
    return mapOf("package" to top.first, "appName" to label, "since" to top.second.toDouble())
}

private suspend fun sensorSnapshot(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val manager = env.app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: error("This device exposes no sensors")
    val wanted = env.choice(node, "sensors", "All")
    val timeout = env.number(node, "timeout", 2000.0).toLong().coerceIn(200, 15_000)

    val types = buildList {
        fun want(name: String) = wanted == "All" || wanted == name
        if (want("Light")) add("light" to Sensor.TYPE_LIGHT)
        if (want("Proximity")) add("proximity" to Sensor.TYPE_PROXIMITY)
        if (want("Accelerometer")) add("accelerometer" to Sensor.TYPE_ACCELEROMETER)
        if (want("Pressure")) add("pressure" to Sensor.TYPE_PRESSURE)
        if (want("Humidity")) add("humidity" to Sensor.TYPE_RELATIVE_HUMIDITY)
        if (want("Temperature")) add("temperature" to Sensor.TYPE_AMBIENT_TEMPERATURE)
    }

    val readings = linkedMapOf<String, Any?>()
    for ((name, sensorType) in types) {
        val sensor = manager.getDefaultSensor(sensorType)
        if (sensor == null) {
            readings[name] = null
            continue
        }
        readings[name] = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        val value: Any = if (event.values.size >= 3 && sensorType == Sensor.TYPE_ACCELEROMETER) {
                            listOf(
                                event.values[0].toDouble(),
                                event.values[1].toDouble(),
                                event.values[2].toDouble(),
                            )
                        } else {
                            event.values.firstOrNull()?.toDouble() ?: 0.0
                        }
                        if (cont.isActive) cont.resume(value)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }
    return readings
}

private fun setAlarm(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val kind = env.choice(node, "kind", "Timer")
    val label = env.text(node, "label").ifBlank { "FlowForge" }
    val skipUi = env.bool(node, "skipUi", true)

    val intent = if (kind == "Timer") {
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, env.number(node, "seconds", 300.0).toInt().coerceAtLeast(1))
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
    } else {
        val time = env.text(node, "time").ifBlank { "07:00" }
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, time.substringBefore(':').trim().toIntOrNull() ?: 7)
            .putExtra(AlarmClock.EXTRA_MINUTES, time.substringAfter(':', "0").trim().toIntOrNull() ?: 0)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
    }
    env.app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return mapOf("created" to true, "kind" to kind, "label" to label)
}

private fun openPanel(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val panel = env.choice(node, "panel", "Wi-Fi")
    val intent = when (panel) {
        "Wi-Fi" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)
        "Internet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        else Intent(Settings.ACTION_WIRELESS_SETTINGS)
        "Volume" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_VOLUME)
        else Intent(Settings.ACTION_SOUND_SETTINGS)
        "NFC" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_NFC)
        else Intent(Settings.ACTION_NFC_SETTINGS)
        "Bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "Airplane mode" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        "Do Not Disturb" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        "Battery saver" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        "Display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        "Sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
        "Location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        "Accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "Date & time" -> Intent(Settings.ACTION_DATE_SETTINGS)
        "Developer options" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        "App details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${env.app.packageName}"))
        else -> Intent(Settings.ACTION_SETTINGS)
    }
    env.app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return mapOf("panel" to panel)
}
