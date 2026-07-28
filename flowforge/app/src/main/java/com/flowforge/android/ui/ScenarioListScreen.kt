package com.flowforge.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.ModuleCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioListScreen(viewModel: AppViewModel, navController: NavController) {
    val scenarios by viewModel.scenarios.collectAsState()
    val runs by viewModel.runs.collectAsState()
    var showTemplates by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FlowForge", fontWeight = FontWeight.Bold)
                        Text(
                            "${scenarios.count { it.enabled }} of ${scenarios.size} live",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("history") }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Run history")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("data") }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Data store")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTemplates = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New scenario") },
            )
        },
    ) { padding ->
        if (scenarios.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    "⚡",
                    "No scenarios yet",
                    "Tap New scenario to wire a trigger to a chain of actions.",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(scenarios, key = { it.id }) { blueprint ->
                    val lastRun = runs.firstOrNull { it.scenarioId == blueprint.id }
                    ScenarioCard(
                        blueprint = blueprint,
                        lastRunStatus = lastRun?.status,
                        lastRunAt = lastRun?.startedAt,
                        onOpen = { navController.navigate("editor/${blueprint.id}") },
                        onToggle = { viewModel.setEnabled(blueprint, it) },
                        onRun = { viewModel.runNow(blueprint) },
                    )
                }
            }
        }
    }

    if (showTemplates) {
        ModalBottomSheet(
            onDismissRequest = { showTemplates = false },
            sheetState = sheetState,
        ) {
            TemplatePicker { template ->
                showTemplates = false
                val created = viewModel.save(template.build())
                navController.navigate("editor/${created.id}")
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    blueprint: Blueprint,
    lastRunStatus: String?,
    lastRunAt: Long?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
) {
    val triggerSpec = ModuleCatalog.specOrUnknown(blueprint.trigger?.type ?: "trigger.manual")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleAvatar(triggerSpec)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        blueprint.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${triggerSpec.name} · ${blueprint.modules.size} module" +
                            if (blueprint.modules.size == 1) "" else "s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = blueprint.enabled, onCheckedChange = onToggle)
            }

            if (lastRunStatus != null || blueprint.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
            }

            if (blueprint.description.isNotBlank()) {
                Text(
                    blueprint.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lastRunStatus != null) {
                    StatusPill(lastRunStatus)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        lastRunAt?.let {
                            SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it))
                        }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run now")
                }
            }
        }
    }
}
