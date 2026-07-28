package com.flowforge.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.ModuleCatalog
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.model.Route

/** Where a newly picked module should land. */
private sealed interface InsertTarget {
    data class AfterModule(val afterId: Int?) : InsertTarget
    data class InRoute(val routerId: Int, val routeIndex: Int) : InsertTarget
    data object ReplaceTrigger : InsertTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: AppViewModel, navController: NavController, scenarioId: String) {
    val existing = remember(scenarioId) { viewModel.scenario(scenarioId) }
    if (existing == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("🤔", "Scenario not found", "It may have been deleted.")
        }
        return
    }

    var blueprint by remember { mutableStateOf(existing) }
    var insertTarget by remember { mutableStateOf<InsertTarget?>(null) }
    var configNode by remember { mutableStateOf<ModuleNode?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showBlueprint by remember { mutableStateOf(false) }
    var routeFilterTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val lastRun by viewModel.lastRun.collectAsState()

    fun edit(transform: (Blueprint) -> Blueprint) {
        blueprint = viewModel.save(transform(blueprint))
    }

    LaunchedEffect(lastRun?.id) {
        val run = lastRun ?: return@LaunchedEffect
        if (run.scenarioId != blueprint.id) return@LaunchedEffect
        snackbar.showSnackbar(
            when (run.status) {
                "success" -> "Ran in ${run.durationMs} ms"
                "filtered" -> "Stopped by a filter"
                else -> "Error: ${run.error ?: "unknown"}"
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            blueprint.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (blueprint.enabled) "Live" else "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (blueprint.enabled) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = blueprint.enabled,
                        onCheckedChange = { enabled ->
                            blueprint = blueprint.copy(enabled = enabled)
                            viewModel.setEnabled(blueprint, enabled)
                        },
                    )
                    IconButton(onClick = { viewModel.runNow(blueprint) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run once")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { showMenu = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Run history") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("history/${blueprint.id}")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("View blueprint JSON") },
                                onClick = { showMenu = false; showBlueprint = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy blueprint") },
                                onClick = {
                                    showMenu = false
                                    clipboard.setText(AnnotatedString(blueprint.toJson()))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = { showMenu = false; viewModel.duplicate(blueprint) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = { showMenu = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            FlowChain(
                modules = blueprint.modules,
                isRoot = true,
                onTap = { configNode = it },
                onInsertAfter = { insertTarget = InsertTarget.AfterModule(it) },
                onTapTrigger = { configNode = it },
                onReplaceTrigger = { insertTarget = InsertTarget.ReplaceTrigger },
                onAddInRoute = { routerId, routeIndex ->
                    insertTarget = InsertTarget.InRoute(routerId, routeIndex)
                },
                onAddRoute = { routerId ->
                    edit { bp ->
                        bp.replaceNode(routerId) { router ->
                            router.copy(routes = router.routes + Route(label = "Route ${router.routes.size + 1}"))
                        }
                    }
                },
                onEditRoute = { routerId, routeIndex -> routeFilterTarget = routerId to routeIndex },
            )

            Spacer(Modifier.height(120.dp))
        }
    }

    // ------------------------------------------------------------------ sheets

    insertTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { insertTarget = null },
            sheetState = sheetState,
        ) {
            ModulePickerSheet(
                showTriggers = target is InsertTarget.ReplaceTrigger,
                onPick = { spec ->
                    val newNode = ModuleNode(
                        id = blueprint.nextModuleId(),
                        type = spec.type,
                        params = ModuleCatalog.defaultParams(spec),
                        routes = if (spec.type == "flow.router")
                            listOf(Route("Route 1"), Route("Route 2")) else emptyList(),
                    )
                    val landedId = when (target) {
                        is InsertTarget.ReplaceTrigger -> {
                            val headId = blueprint.modules.firstOrNull()?.id ?: 1
                            edit { bp ->
                                val replacement = ModuleNode(
                                    id = headId,
                                    type = spec.type,
                                    params = ModuleCatalog.defaultParams(spec),
                                )
                                bp.copy(modules = listOf(replacement) + bp.modules.drop(1))
                            }
                            headId
                        }
                        is InsertTarget.AfterModule -> {
                            edit { it.insertAfter(target.afterId, newNode) }
                            newNode.id
                        }
                        is InsertTarget.InRoute -> {
                            edit { it.insertInRoute(target.routerId, target.routeIndex, newNode) }
                            newNode.id
                        }
                    }
                    insertTarget = null
                    configNode = blueprint.findNode(landedId)
                },
            )
        }
    }

    configNode?.let { node ->
        ModalBottomSheet(
            onDismissRequest = { configNode = null },
            sheetState = sheetState,
        ) {
            ModuleConfigSheet(
                blueprint = blueprint,
                node = node,
                onChange = { updated -> edit { it.replaceNode(node.id) { updated } } },
                onDelete = {
                    edit { it.removeNode(node.id) }
                    configNode = null
                },
                onClose = { configNode = null },
            )
        }
    }

    routeFilterTarget?.let { (routerId, routeIndex) ->
        val route = blueprint.findNode(routerId)?.routes?.getOrNull(routeIndex)
        if (route == null) {
            routeFilterTarget = null
        } else {
            RouteFilterDialog(
                blueprint = blueprint,
                route = route,
                onDismiss = { routeFilterTarget = null },
                onSave = { label, rule ->
                    edit { bp ->
                        bp.replaceNode(routerId) { router ->
                            val routes = router.routes.toMutableList()
                            routes[routeIndex] = routes[routeIndex].copy(label = label, filter = rule)
                            router.copy(routes = routes)
                        }
                    }
                    routeFilterTarget = null
                },
            )
        }
    }

    if (showRename) {
        var draftName by remember { mutableStateOf(blueprint.name) }
        var draftDescription by remember { mutableStateOf(blueprint.description) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Scenario details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        label = { Text("Notes") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    edit { it.copy(name = draftName.ifBlank { "Untitled scenario" }, description = draftDescription) }
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete ${blueprint.name}?") },
            text = { Text("This removes the scenario and stops its triggers. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(blueprint.id)
                    showDelete = false
                    navController.popBackStack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }

    if (showBlueprint) {
        AlertDialog(
            onDismissRequest = { showBlueprint = false },
            title = { Text("Blueprint") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CodeBlock(blueprint.toJson())
                }
            },
            confirmButton = { TextButton(onClick = { showBlueprint = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun RouteFilterDialog(
    blueprint: Blueprint,
    route: Route,
    onDismiss: () -> Unit,
    onSave: (String, com.flowforge.android.model.FilterRule?) -> Unit,
) {
    var label by remember { mutableStateOf(route.label) }
    var enabled by remember { mutableStateOf(route.filter != null) }
    var rule by remember { mutableStateOf(route.filter ?: com.flowforge.android.model.FilterRule()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Route name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Only run this route when…")
                }
                if (enabled) {
                    Spacer(Modifier.height(8.dp))
                    FilterEditor(
                        blueprint = blueprint,
                        beforeModuleId = null,
                        rule = rule,
                        onChange = { rule = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(label.ifBlank { "Route" }, if (enabled) rule else null)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------- flow canvas

@Composable
private fun FlowChain(
    modules: List<ModuleNode>,
    isRoot: Boolean,
    onTap: (ModuleNode) -> Unit,
    onInsertAfter: (Int?) -> Unit,
    onTapTrigger: (ModuleNode) -> Unit,
    onReplaceTrigger: () -> Unit,
    onAddInRoute: (Int, Int) -> Unit,
    onAddRoute: (Int) -> Unit,
    onEditRoute: (Int, Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        modules.forEachIndexed { index, node ->
            val isTrigger = isRoot && index == 0
            ModuleCard(
                node = node,
                isTrigger = isTrigger,
                onClick = { if (isTrigger) onTapTrigger(node) else onTap(node) },
                onSwapTrigger = if (isTrigger) onReplaceTrigger else null,
            )

            if (node.type == "flow.router") {
                RouterBranches(
                    router = node,
                    onTap = onTap,
                    onInsertAfter = onInsertAfter,
                    onAddInRoute = onAddInRoute,
                    onAddRoute = onAddRoute,
                    onEditRoute = onEditRoute,
                    onTapTrigger = onTapTrigger,
                    onReplaceTrigger = onReplaceTrigger,
                )
            } else {
                Connector(onAdd = { onInsertAfter(node.id) })
            }
        }

        if (modules.isEmpty()) {
            AddButton("Add a module") { onInsertAfter(null) }
        }
    }
}

@Composable
private fun RouterBranches(
    router: ModuleNode,
    onTap: (ModuleNode) -> Unit,
    onInsertAfter: (Int?) -> Unit,
    onAddInRoute: (Int, Int) -> Unit,
    onAddRoute: (Int) -> Unit,
    onEditRoute: (Int, Int) -> Unit,
    onTapTrigger: (ModuleNode) -> Unit,
    onReplaceTrigger: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp)) {
        router.routes.forEachIndexed { routeIndex, route ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    route.label.ifBlank { "Route ${routeIndex + 1}" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                route.filter?.let {
                    Text(
                        "if ${it.left} ${it.op} ${it.right}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onEditRoute(router.id, routeIndex) }) { Text("Filter") }
            }
            Column(Modifier.padding(start = 22.dp)) {
                FlowChain(
                    modules = route.modules,
                    isRoot = false,
                    onTap = onTap,
                    onInsertAfter = onInsertAfter,
                    onTapTrigger = onTapTrigger,
                    onReplaceTrigger = onReplaceTrigger,
                    onAddInRoute = onAddInRoute,
                    onAddRoute = onAddRoute,
                    onEditRoute = onEditRoute,
                )
                AddButton("Add to ${route.label.ifBlank { "route ${routeIndex + 1}" }}") {
                    onAddInRoute(router.id, routeIndex)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        AddButton("Add a route") { onAddRoute(router.id) }
    }
}

@Composable
private fun ModuleCard(
    node: ModuleNode,
    isTrigger: Boolean,
    onClick: () -> Unit,
    onSwapTrigger: (() -> Unit)?,
) {
    val spec = ModuleCatalog.specOrUnknown(node.type)
    val summary = spec.summaryKey?.let { node.params[it] }?.takeIf { it.isNotBlank() }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleAvatar(spec, badge = node.id)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        node.label ?: spec.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (isTrigger) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(spec.color).copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "TRIGGER",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(spec.color),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else if (spec.description.isNotBlank()) {
                    Text(
                        spec.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                node.filter?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "only if ${it.left} ${it.op} ${it.right}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                    )
                }
            }
            if (onSwapTrigger != null) {
                TextButton(onClick = onSwapTrigger) { Text("Change") }
            }
        }
    }
}

@Composable
private fun Connector(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).padding(start = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(38.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add module",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
