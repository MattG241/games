package com.flowforge.android.core

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.R
import com.flowforge.android.engine.runners.Notifications
import com.flowforge.android.ui.MainActivity
import kotlin.math.sqrt

/**
 * Keeps every live device trigger registered. It runs as a foreground service so Android does not
 * kill the listeners while the app is in the background.
 */
class FlowService : Service() {

    private val app get() = FlowForgeApp.instance
    private var webhookServer: WebhookServer? = null
    private var sensorManager: SensorManager? = null
    private var shakeListener: SensorEventListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var lastBatteryLevel = -1
    private var lastCallState = TelephonyManager.EXTRA_STATE_IDLE

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handleSystemEvent(intent)
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        registerSystemReceivers()
        registerNetworkCallback()
        startWebhookServer()
        updateShakeListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            startWebhookServer()
            updateShakeListener()
            startForegroundNotification()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(systemReceiver) }
        runCatching {
            networkCallback?.let {
                (getSystemService(ConnectivityManager::class.java))?.unregisterNetworkCallback(it)
            }
        }
        shakeListener?.let { sensorManager?.unregisterListener(it) }
        webhookServer?.stop()
        webhookServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ setup

    private fun startForegroundNotification() {
        Notifications.ensureChannels(this)
        val active = runCatching { app.scenarios.scenarios.value.count { it.enabled } }.getOrDefault(0)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("FlowForge is watching")
            .setContentText(
                if (active == 1) "1 scenario is live" else "$active scenarios are live"
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else 0,
                )
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "Foreground start refused", it) }
    }

    private fun registerSystemReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(systemReceiver, filter)
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return
                dispatchWifi("Connected")
            }

            override fun onLost(network: Network) = dispatchWifi("Disconnected")
        }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                callback,
            )
            networkCallback = callback
        }
    }

    private fun startWebhookServer() {
        webhookServer?.stop()
        val needed = app.scenarios.scenarios.value.any { it.enabled && it.trigger?.type == "trigger.webhook" }
        if (!needed) { webhookServer = null; return }
        webhookServer = runCatching {
            WebhookServer(app.prefs.webhookPort).also { it.start() }
        }.onFailure { Log.w(TAG, "Webhook server failed to start", it) }.getOrNull()
    }

    private fun updateShakeListener() {
        val wanted = app.scenarios.scenarios.value.any { it.enabled && it.trigger?.type == "trigger.shake" }
        val manager = sensorManager ?: (getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            .also { sensorManager = it }
        shakeListener?.let { manager?.unregisterListener(it); shakeListener = null }
        if (!wanted || manager == null) return

        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = object : SensorEventListener {
            private var lastShake = 0L
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val force = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (now - lastShake < 1500) return
                app.dispatchTrigger(
                    "trigger.shake",
                    mapOf("force" to force, "timestamp" to now.toDouble()),
                    "Shake",
                ) { trigger ->
                    force >= (trigger.param("sensitivity", "16").toDoubleOrNull() ?: 16.0)
                }.let { fired -> if (fired > 0) lastShake = now }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        shakeListener = listener
    }

    // ------------------------------------------------------------------ events

    private fun handleSystemEvent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                val connected = intent.action == Intent.ACTION_POWER_CONNECTED
                val state = if (connected) "Connected" else "Disconnected"
                app.dispatchTrigger(
                    "trigger.power",
                    mapOf("state" to state, "level" to currentBatteryLevel().toDouble()),
                    "Power $state",
                ) { it.param("state", "Connected") == state }
            }

            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level < 0 || scale <= 0) return
                val percent = level * 100 / scale
                val previous = lastBatteryLevel
                lastBatteryLevel = percent
                if (previous < 0 || previous == percent) return

                val charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    .let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                app.dispatchTrigger(
                    "trigger.battery",
                    mapOf(
                        "level" to percent.toDouble(),
                        "charging" to charging,
                        "temperature" to intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0,
                    ),
                    "Battery $percent%",
                ) { trigger ->
                    val threshold = trigger.param("level", "20").toIntOrNull() ?: 20
                    // Only on the crossing, so the scenario does not repeat every percent.
                    if (trigger.param("compare", "Below") == "Below") percent <= threshold && previous > threshold
                    else percent >= threshold && previous < threshold
                }
            }

            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> {
                val state = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> "Screen on"
                    Intent.ACTION_SCREEN_OFF -> "Screen off"
                    else -> "Unlocked"
                }
                app.dispatchTrigger(
                    "trigger.screen",
                    mapOf("state" to state, "timestamp" to System.currentTimeMillis().toDouble()),
                    state,
                ) { it.param("state", "Screen on") == state }
            }

            Intent.ACTION_HEADSET_PLUG -> {
                val plugged = intent.getIntExtra("state", 0) == 1
                val state = if (plugged) "Plugged in" else "Unplugged"
                app.dispatchTrigger(
                    "trigger.headset",
                    mapOf("state" to state, "hasMic" to (intent.getIntExtra("microphone", 0) == 1)),
                    "Headset $state",
                ) { it.param("state", "Plugged in") == state }
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                val state = if (on) "Turned on" else "Turned off"
                app.dispatchTrigger("trigger.airplane", mapOf("state" to state), "Airplane $state") {
                    it.param("state", "Turned on") == state
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val state = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) "Connected" else "Disconnected"
                val device: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val name = runCatching { device?.name }.getOrNull().orEmpty()
                app.dispatchTrigger(
                    "trigger.bluetooth",
                    mapOf("state" to state, "device" to name, "address" to (device?.address ?: "")),
                    "Bluetooth $state: $name",
                ) { trigger ->
                    val wanted = trigger.param("device").trim()
                    trigger.param("state", "Connected") == state &&
                        (wanted.isBlank() || name.contains(wanted, ignoreCase = true))
                }
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val raw = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                if (raw == lastCallState) return
                val previous = lastCallState
                lastCallState = raw
                val state = when {
                    raw == TelephonyManager.EXTRA_STATE_RINGING -> "Ringing"
                    raw == TelephonyManager.EXTRA_STATE_OFFHOOK -> "Answered"
                    previous != TelephonyManager.EXTRA_STATE_IDLE -> "Ended"
                    else -> return
                }
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
                app.dispatchTrigger(
                    "trigger.call",
                    mapOf("state" to state, "number" to number),
                    "Call $state",
                ) { it.param("state", "Ringing") == state }
            }
        }
    }

    private fun dispatchWifi(state: String) {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val ssid = runCatching { wifi?.connectionInfo?.ssid?.trim('"') }.getOrNull().orEmpty()
        app.dispatchTrigger(
            "trigger.wifi",
            mapOf("state" to state, "ssid" to ssid),
            "Wi-Fi $state",
        ) { trigger ->
            val wanted = trigger.param("ssid").trim()
            trigger.param("state", "Connected") == state &&
                (wanted.isBlank() || ssid.equals(wanted, ignoreCase = true))
        }
    }

    private fun currentBatteryLevel(): Int =
        (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

    companion object {
        private const val TAG = "FlowService"
        private const val SERVICE_NOTIFICATION_ID = 4201
        const val ACTION_REFRESH = "com.flowforge.android.REFRESH"

        fun refresh(context: Context) {
            runCatching {
                val intent = Intent(context, FlowService::class.java).setAction(ACTION_REFRESH)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
    }
}
