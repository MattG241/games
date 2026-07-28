package com.flowforge.android.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.FilterRule
import com.flowforge.android.model.ModuleCatalog
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.model.ModuleSpec
import com.flowforge.android.model.ParamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------- picker

@Composable
fun ModulePickerSheet(showTriggers: Boolean, onPick: (ModuleSpec) -> Unit) {
    var query by remember { mutableStateOf("") }
    val pool = if (showTriggers) ModuleCatalog.triggers else ModuleCatalog.actions
    val filtered = pool.filter {
        query.isBlank() ||
            it.name.contains(query, true) ||
            it.group.contains(query, true) ||
            it.description.contains(query, true)
    }
    val grouped = filtered.groupBy { it.group }

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Text(
            if (showTriggers) "Choose a trigger" else "Add a module",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            grouped.forEach { (group, specs) ->
                item(key = "header-$group") {
                    SectionLabel(group, Modifier.padding(top = 14.dp, bottom = 6.dp))
                }
                items(specs, key = { it.type }) { spec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(spec) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModuleAvatar(spec, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(spec.name, fontWeight = FontWeight.Medium)
                            if (spec.description.isNotBlank()) {
                                Text(
                                    spec.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- config

@Composable
fun ModuleConfigSheet(
    blueprint: Blueprint,
    node: ModuleNode,
    onChange: (ModuleNode) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val spec = ModuleCatalog.specOrUnknown(node.type)
    var draft by remember(node.id, node.type) { mutableStateOf(node) }
    var mappingTarget by remember { mutableStateOf<String?>(null) }
    var filterEnabled by remember(node.id) { mutableStateOf(node.filter != null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun push(updated: ModuleNode) {
        draft = updated
        onChange(updated)
    }

    fun setParam(key: String, value: String) =
        push(draft.copy(params = draft.params + (key to value)))

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleAvatar(spec, badge = node.id)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    spec.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove module")
            }
        }

        if (spec.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = draft.label.orEmpty(),
            onValueChange = { push(draft.copy(label = it.ifBlank { null })) },
            label = { Text("Label (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        spec.params.forEach { param ->
            Spacer(Modifier.height(12.dp))
            val current = draft.params[param.key] ?: param.default
            when (param.type) {
                ParamType.BOOL -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = current.toBoolean(),
                        onCheckedChange = { setParam(param.key, it.toString()) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(param.label)
                }

                ParamType.SELECT -> SelectField(
                    label = param.label,
                    value = current.ifBlank { param.options.firstOrNull().orEmpty() },
                    options = param.options,
                    onSelect = { setParam(param.key, it) },
                )

                ParamType.APP -> AppField(
                    label = param.label,
                    value = current,
                    onSelect = { setParam(param.key, it) },
                )

                else -> OutlinedTextField(
                    value = current,
                    onValueChange = { setParam(param.key, it) },
                    label = { Text(param.label) },
                    placeholder = { if (param.hint.isNotBlank()) Text(param.hint) },
                    singleLine = param.type != ParamType.MULTILINE,
                    minLines = if (param.type == ParamType.MULTILINE) 3 else 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (param.type == ParamType.NUMBER) KeyboardType.Number
                        else KeyboardType.Text
                    ),
                    trailingIcon = {
                        if (param.mappable) {
                            TextButton(onClick = { mappingTarget = param.key }) {
                                Text("{ }", fontFamily = FontFamily.Monospace)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- gate ----
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = filterEnabled,
                onCheckedChange = { on ->
                    filterEnabled = on
                    push(draft.copy(filter = if (on) (draft.filter ?: FilterRule()) else null))
                },
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Only continue if…", fontWeight = FontWeight.Medium)
                Text(
                    "Stops the run here when the condition fails",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (filterEnabled) {
            Spacer(Modifier.height(10.dp))
            FilterEditor(
                blueprint = blueprint,
                beforeModuleId = node.id,
                rule = draft.filter ?: FilterRule(),
                onChange = { push(draft.copy(filter = it)) },
            )
        }

        // ---- outputs ----
        if (spec.outputs.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("This module outputs")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column {
                    spec.outputs.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { field ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "{{${node.id}.$field}}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        if (spec.permissions.isNotEmpty() || spec.specialAccess != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Needs extra access — grant it from Settings inside FlowForge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }

    mappingTarget?.let { key ->
        MappingPickerDialog(
            blueprint = blueprint,
            beforeModuleId = node.id,
            onDismiss = { mappingTarget = null },
            onPick = { token ->
                val current = draft.params[key].orEmpty()
                setParam(key, current + token)
                mappingTarget = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this module?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------- fields

@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // A transparent hit area over the read-only field, so tapping anywhere opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun AppField(label: String, value: String, onSelect: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onSelect,
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("com.example.app") },
        trailingIcon = { TextButton(onClick = { picking = true }) { Text("Pick") } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        InstalledAppDialog(
            onDismiss = { picking = false },
            onPick = { onSelect(it); picking = false },
        )
    }
}

@Composable
private fun InstalledAppDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            runCatching {
                pm.getInstalledApplications(0)
                    .filter { info: ApplicationInfo -> pm.getLaunchIntentForPackage(info.packageName) != null }
                    .map { info -> pm.getApplicationLabel(info).toString() to info.packageName }
                    .sortedBy { it.first.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    val filtered = apps.filter {
        query.isBlank() || it.first.contains(query, true) || it.second.contains(query, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.second }) { (name, pkg) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(pkg) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(name, fontWeight = FontWeight.Medium)
                            Text(
                                pkg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun FilterEditor(
    blueprint: Blueprint,
    beforeModuleId: Int?,
    rule: FilterRule,
    onChange: (FilterRule) -> Unit,
) {
    var mappingTarget by remember { mutableStateOf<String?>(null) }

    Column {
        OutlinedTextField(
            value = rule.left,
            onValueChange = { onChange(rule.copy(left = it)) },
            label = { Text("Value") },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = { mappingTarget = "left" }) {
                    Text("{ }", fontFamily = FontFamily.Monospace)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SelectField(
            label = "Condition",
            value = rule.op,
            options = FilterRule.OPERATORS,
            onSelect = { onChange(rule.copy(op = it)) },
        )
        if (rule.op !in setOf("is empty", "is not empty", "is true")) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rule.right,
                onValueChange = { onChange(rule.copy(right = it)) },
                label = { Text("Compare to") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { mappingTarget = "right" }) {
                        Text("{ }", fontFamily = FontFamily.Monospace)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    mappingTarget?.let { side ->
        MappingPickerDialog(
            blueprint = blueprint,
            beforeModuleId = beforeModuleId,
            onDismiss = { mappingTarget = null },
            onPick = { token ->
                onChange(
                    if (side == "left") rule.copy(left = rule.left + token)
                    else rule.copy(right = rule.right + token)
                )
                mappingTarget = null
            },
        )
    }
}

// ---------------------------------------------------------------------- mapping

@Composable
fun MappingPickerDialog(
    blueprint: Blueprint,
    beforeModuleId: Int?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val sources = remember(blueprint, beforeModuleId) {
        if (beforeModuleId == null) buildList { blueprint.forEachNode { add(it) } }
        else blueprint.modulesBefore(beforeModuleId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert a value") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (sources.isEmpty()) {
                    item {
                        Text(
                            "Nothing runs before this module yet, so there is no output to map.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                sources.forEach { source ->
                    val spec = ModuleCatalog.specOrUnknown(source.type)
                    item(key = "src-${source.id}") {
                        Row(
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModuleAvatar(spec, size = 26.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${source.id}. ${source.label ?: spec.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(spec.outputs, key = { "out-${source.id}-$it" }) { field ->
                        TokenRow("{{${source.id}.$field}}", onPick)
                    }
                    if (spec.outputs.isEmpty()) {
                        item(key = "empty-${source.id}") {
                            TokenRow("{{${source.id}}}", onPick)
                        }
                    }
                }
                item(key = "builtins") {
                    SectionLabel("Built in", Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(BUILT_IN_TOKENS, key = { it }) { token -> TokenRow(token, onPick) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TokenRow(token: String, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(token) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(token, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

private val BUILT_IN_TOKENS = listOf(
    "{{now}}",
    "{{formatDate(now; \"yyyy-MM-dd HH:mm\")}}",
    "{{uuid}}",
    "{{vars.myVar}}",
    "{{upper(1.text)}}",
    "{{trim(1.text)}}",
    "{{json(2.json)}}",
    "{{if(1.level < 20; \"low\"; \"ok\")}}",
)
