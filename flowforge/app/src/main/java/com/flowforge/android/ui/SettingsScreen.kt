package com.flowforge.android.ui

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.flowforge.android.FlowForgeApp
import com.flowforge.android.core.ShellRunner
import com.flowforge.android.core.WebhookServer
import com.flowforge.android.triggers.FlowAccessibilityService
import com.flowforge.android.triggers.FlowNotificationListener

private val RUNTIME_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.ANSWER_PHONE_CALLS)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.WRITE_CONTACTS)
    add(Manifest.permission.READ_CALENDAR)
    add(Manifest.permission.WRITE_CALENDAR)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, navController: NavController) {
    val context = LocalContext.current
    val app = FlowForgeApp.instance
    val clipboard = LocalClipboardManager.current
    val scenarios by viewModel.scenarios.collectAsState()

    var engineOn by remember { mutableStateOf(app.prefs.engineEnabled) }
    var notifyErrors by remember { mutableStateOf(app.prefs.notifyOnError) }
    var port by remember { mutableStateOf(app.prefs.webhookPort.toString()) }
    var tileScenario by remember { mutableStateOf(app.prefs.tileScenarioId) }
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshToken++ }

    fun openSettings(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard("Engine") {
                    ToggleRow(
                        title = "Keep the engine running",
                        subtitle = "A quiet ongoing notification keeps device triggers alive.",
                        checked = engineOn,
                    ) { on ->
                        engineOn = on
                        app.prefs.engineEnabled = on
                        if (on) app.startEngineService() else app.stopEngineService()
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ToggleRow(
                        title = "Notify me when a scenario fails",
                        subtitle = "Posts a notification with the error message.",
                        checked = notifyErrors,
                    ) { on ->
                        notifyErrors = on
                        app.prefs.notifyOnError = on
                    }
                }
            }

            item {
                // Keyed on refreshToken so the badges re-evaluate after returning from Settings.
                val notificationAccess = remember(refreshToken) {
                    FlowNotificationListener.isEnabled(context)
                }
                val canWriteSettings = remember(refreshToken) { Settings.System.canWrite(context) }
                val dndAccess = remember(refreshToken) {
                    context.getSystemService(NotificationManager::class.java)
                        ?.isNotificationPolicyAccessGranted == true
                }
                val batteryExempt = remember(refreshToken) {
                    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .isIgnoringBatteryOptimizations(context.packageName)
                }
                val exactAlarms = remember(refreshToken) {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
                }
                val accessibility = remember(refreshToken) { FlowAccessibilityService.isEnabled(context) }
                val usageAccess = remember(refreshToken) {
                    val ops = context.getSystemService(AppOpsManager::class.java)
                    val mode = runCatching {
                        ops?.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            android.os.Process.myUid(),
                            context.packageName,
                        )
                    }.getOrNull()
                    mode == AppOpsManager.MODE_ALLOWED
                }

                SettingsCard("Access") {
                    Text(
                        "FlowForge only uses what your scenarios need — nothing is requested up front.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(RUNTIME_PERMISSIONS.toTypedArray()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Grant SMS, phone, contacts, calendar, location, camera, mic") }

                    AccessRow(
                        title = "Notification access",
                        granted = notificationAccess,
                        hint = "Needed by the Notification posted trigger",
                    ) { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

                    AccessRow(
                        title = "Modify system settings",
                        granted = canWriteSettings,
                        hint = "Needed to set screen brightness",
                    ) {
                        openSettings(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }

                    AccessRow(
                        title = "Accessibility service",
                        granted = accessibility,
                        hint = "Needed by the UI automation modules and Lock the screen",
                    ) { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

                    AccessRow(
                        title = "Usage access",
                        granted = usageAccess,
                        hint = "Needed by Foreground app and the App opened trigger",
                    ) { openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

                    AccessRow(
                        title = "Do Not Disturb access",
                        granted = dndAccess,
                        hint = "Needed for silent mode and DND modules",
                    ) { openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }

                    AccessRow(
                        title = "Ignore battery optimisation",
                        granted = batteryExempt,
                        hint = "Stops Android pausing scheduled scenarios",
                    ) { openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AccessRow(
                            title = "Exact alarms",
                            granted = exactAlarms,
                            hint = "Keeps scheduled triggers on time",
                        ) { openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                    }
                }
            }

            item {
                var shizukuState by remember { mutableStateOf(ShellRunner.shizukuAvailable()) }
                var rootState by remember { mutableStateOf<Boolean?>(null) }

                SettingsCard("Privileged tier (optional)") {
                    Text(
                        "The Privileged modules — silent Wi-Fi and mobile data toggles, force-stopping " +
                            "apps, granting permissions, hardware keys, free settings writes — need a " +
                            "channel Android does not give normal apps. Shizuku provides one without " +
                            "root; a rooted device works too. Everything else in FlowForge works without this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    AccessRow(
                        title = "Shizuku",
                        granted = shizukuState,
                        hint = if (ShellRunner.shizukuBound()) "Shizuku is running — tap to grant FlowForge access"
                        else "Not running. Install Shizuku and start it, then tap here.",
                    ) {
                        ShellRunner.requestShizukuPermission()
                        shizukuState = ShellRunner.shizukuAvailable()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Root", fontWeight = FontWeight.Medium)
                            Text(
                                when (rootState) {
                                    true -> "Root access granted"
                                    false -> "No root on this device"
                                    else -> "Not checked — tap to test (a root prompt may appear)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { rootState = ShellRunner.rootAvailable() }) {
                            Text("Check")
                        }
                    }
                }
            }

            item {
                SettingsCard("Webhooks") {
                    Text(
                        "Enabled webhook scenarios listen on this port. Anything on the same " +
                            "network can start them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { text ->
                            port = text.filter { it.isDigit() }.take(5)
                            port.toIntOrNull()?.takeIf { it in 1024..65535 }?.let {
                                app.prefs.webhookPort = it
                            }
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val base = "http://${WebhookServer.localAddress(context)}:${app.prefs.webhookPort}/"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            base,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { clipboard.setText(AnnotatedString(base)) }) {
                            Text("Copy")
                        }
                    }
                    Text(
                        "Restart the engine toggle above after changing the port.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard("Quick settings tile") {
                    Text(
                        "Choose which scenario the FlowForge tile runs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectField(
                        label = "Tile scenario",
                        value = scenarios.firstOrNull { it.id == tileScenario }?.name ?: "None",
                        options = listOf("None") + scenarios.map { it.name },
                        onSelect = { name ->
                            val picked = scenarios.firstOrNull { it.name == name }
                            tileScenario = picked?.id
                            app.prefs.tileScenarioId = picked?.id
                        },
                    )
                }
            }

            item {
                SettingsCard("Import a blueprint") {
                    Text(
                        "Paste any FlowForge blueprint JSON to add it as a new, paused scenario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("Blueprint JSON") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            importText = clipboard.getText()?.text.orEmpty()
                        }) { Text("Paste") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                importResult = viewModel.importBlueprint(importText).fold(
                                    onSuccess = { "Imported \"${it.name}\"" },
                                    onFailure = { "Could not read that blueprint" },
                                )
                                importText = ""
                            },
                            enabled = importText.isNotBlank(),
                        ) { Text("Import") }
                    }
                    importResult?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                SettingsCard("Start scenarios from anywhere") {
                    Text(
                        "Deep link: flowforge://run/<scenario name>\n" +
                            "Broadcast: am broadcast -a com.flowforge.android.RUN_SCENARIO " +
                            "--es scenario \"<name>\" --es payload '{\"key\":\"value\"}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel(title)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccessRow(title: String, granted: Boolean, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(if (granted) "success" else "Grant")
    }
}
