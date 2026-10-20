package com.mp3tag.mobile.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mp3tag.mobile.data.tag.TagSnapshot
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.util.PatternParser
import org.jaudiotagger.tag.FieldKey
import java.io.File

// ---------------------------------------------------------------------------
// Shared preview row
// ---------------------------------------------------------------------------

@Composable
private fun PreviewList(
    rows: List<PreviewRow>,
    maxHeight: Dp = 260.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
    ) {
        items(rows) { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            ) {
                if (row.error != null) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Column {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.error ?: row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class PreviewRow(val title: String, val detail: String, val error: String? = null)

// ---------------------------------------------------------------------------
// 1. Tags from filename
// ---------------------------------------------------------------------------

@Composable
fun TagsFromFilenameDialog(
    snapshots: List<TagSnapshot>,
    savedPatterns: List<String>,
    rememberPattern: (String) -> Unit,
    onApply: (List<Pair<String, Map<FieldKey, String>>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowInfo = rememberAdaptiveWindowInfo()
    var pattern by rememberSaveable { mutableStateOf(PatternParser.COMMON_PATTERNS.first()) }
    val suggestions = PatternParser.COMMON_PATTERNS + savedPatterns
    val parsed = remember(pattern, snapshots) {
        snapshots.map { it to PatternParser.parseFileName(pattern, File(it.path).name) }
    }
    val matches = parsed.count { it.second != null }

    val previewRows = remember(parsed) {
        parsed.map { (snapshot, values) ->
            if (values == null) {
                PreviewRow(File(snapshot.path).name, "No match for this pattern", "No match")
            } else {
                PreviewRow(
                    File(snapshot.path).name,
                    values.entries.joinToString(" • ") { (key, value) ->
                        "${key.name.lowercase().replace('_', ' ')}: $value"
                    },
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !windowInfo.useTwoPane),
        modifier = if (windowInfo.useTwoPane) {
            Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f)
        } else {
            Modifier
        },
        title = { Text("Tags from filename") },
        text = {
            if (windowInfo.useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            singleLine = true,
                            label = { Text("Pattern") },
                            supportingText = { Text("Use %artist%, %title%, %album%, %track%, %year%, %genre%…") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                        ) {
                            suggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = pattern == suggestion,
                                    onClick = { pattern = suggestion },
                                    label = { Text(suggestion, maxLines = 1) },
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$matches of ${snapshots.size} files match",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        PreviewList(
                            rows = previewRows,
                            maxHeight = (windowInfo.heightDp * 0.45f).dp.coerceIn(130.dp, 300.dp),
                        )
                    }
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        singleLine = true,
                        label = { Text("Pattern") },
                        supportingText = { Text("Use %artist%, %title%, %album%, %track%, %year%, %genre%…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            FilterChip(
                                selected = pattern == suggestion,
                                onClick = { pattern = suggestion },
                                label = { Text(suggestion, maxLines = 1) },
                            )
                        }
                    }
                    Text(
                        text = "$matches of ${snapshots.size} files match",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    PreviewList(
                        rows = previewRows,
                        maxHeight = (windowInfo.heightDp * 0.30f).dp.coerceIn(120.dp, 260.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    rememberPattern(pattern)
                    onApply(parsed.mapNotNull { (snapshot, values) ->
                        values?.let { snapshot.path to it }
                    })
                    onDismiss()
                },
                enabled = matches > 0,
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// 2. Rename from tags
// ---------------------------------------------------------------------------

@Composable
fun RenameFromTagsDialog(
    snapshots: List<TagSnapshot>,
    savedPatterns: List<String>,
    rememberPattern: (String) -> Unit,
    onApply: (pattern: String, onDone: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowInfo = rememberAdaptiveWindowInfo()
    var pattern by rememberSaveable { mutableStateOf("%track% - %title%") }
    val suggestions = PatternParser.COMMON_PATTERNS + savedPatterns
    val segments = remember(pattern) { PatternParser.compile(pattern) }
    val previews = remember(pattern, snapshots) {
        snapshots.map { snapshot ->
            val newName = segments?.let { segs ->
                PatternParser.buildFileName(segs, snapshot.fields)?.let { base ->
                    "${PatternParser.sanitizeFileName(base)}.${File(snapshot.path).extension.ifBlank { "dat" }}"
                }
            }
            Triple(snapshot, newName, segments == null)
        }
    }
    val valid = previews.count { it.second != null }

    val previewRows = remember(previews) {
        previews.map { (snapshot, newName, badPattern) ->
            when {
                badPattern -> PreviewRow(File(snapshot.path).name, "Invalid pattern", "Invalid pattern")
                newName == null -> PreviewRow(File(snapshot.path).name, "Missing tag values", "Missing tags")
                else -> PreviewRow(File(snapshot.path).name, "→ $newName")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !windowInfo.useTwoPane),
        modifier = if (windowInfo.useTwoPane) {
            Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f)
        } else {
            Modifier
        },
        title = { Text("Rename from tags") },
        text = {
            if (windowInfo.useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            singleLine = true,
                            label = { Text("Pattern") },
                            supportingText = { Text("Builds the new file name from tag values") },
                            isError = segments == null && pattern.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                        ) {
                            suggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = pattern == suggestion,
                                    onClick = { pattern = suggestion },
                                    label = { Text(suggestion, maxLines = 1) },
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$valid of ${snapshots.size} files will be renamed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        PreviewList(
                            rows = previewRows,
                            maxHeight = (windowInfo.heightDp * 0.45f).dp.coerceIn(130.dp, 300.dp),
                        )
                    }
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        singleLine = true,
                        label = { Text("Pattern") },
                        supportingText = { Text("Builds the new file name from tag values") },
                        isError = segments == null && pattern.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            FilterChip(
                                selected = pattern == suggestion,
                                onClick = { pattern = suggestion },
                                label = { Text(suggestion, maxLines = 1) },
                            )
                        }
                    }
                    Text(
                        text = "$valid of ${snapshots.size} files will be renamed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    PreviewList(
                        rows = previewRows,
                        maxHeight = (windowInfo.heightDp * 0.30f).dp.coerceIn(120.dp, 260.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    rememberPattern(pattern)
                    val done = { onDismiss() }
                    onApply(pattern, done)
                },
                enabled = valid > 0,
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// 3. Batch actions (regex replace / case conversion)
// ---------------------------------------------------------------------------

private enum class BatchActionType(val label: String) {
    REGEX("Regex replace"),
    CASE("Case conversion"),
}

private enum class CaseMode(val label: String) {
    UPPER("UPPERCASE"),
    LOWER("lowercase"),
    TITLE("Title Case"),
}

@Composable
fun BatchActionsDialog(
    snapshots: List<TagSnapshot>,
    displayedKeys: List<FieldKey>,
    onApply: (List<Triple<String, FieldKey, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowInfo = rememberAdaptiveWindowInfo()

    var actionTypeName by rememberSaveable { mutableStateOf(BatchActionType.REGEX.name) }
    val actionType = BatchActionType.entries.firstOrNull { it.name == actionTypeName } ?: BatchActionType.REGEX

    var fieldName by rememberSaveable {
        mutableStateOf((displayedKeys.firstOrNull() ?: FieldKey.TITLE).name)
    }
    val field = displayedKeys.firstOrNull { it.name == fieldName }
        ?: displayedKeys.firstOrNull()
        ?: FieldKey.TITLE

    var find by rememberSaveable { mutableStateOf("") }
    var replaceWith by rememberSaveable { mutableStateOf("") }

    var caseModeName by rememberSaveable { mutableStateOf(CaseMode.UPPER.name) }
    val caseMode = CaseMode.entries.firstOrNull { it.name == caseModeName } ?: CaseMode.UPPER

    val transform: (String) -> String = when (actionType) {
        BatchActionType.REGEX -> { value ->
            try {
                find.toRegex().replace(value, replaceWith)
            } catch (_: Exception) {
                value
            }
        }
        BatchActionType.CASE -> { value ->
            when (caseMode) {
                CaseMode.UPPER -> value.uppercase()
                CaseMode.LOWER -> value.lowercase()
                CaseMode.TITLE -> value.lowercase()
                    .split(' ')
                    .joinToString(" ") { p -> p.replaceFirstChar { it.uppercaseChar() } }
            }
        }
    }

    val regexValid = actionType != BatchActionType.REGEX || runCatching { find.toRegex() }.isSuccess

    val changes = remember(actionType, field, find, replaceWith, caseMode, snapshots) {
        if (!regexValid) return@remember emptyList<Triple<String, FieldKey, String>>()
        snapshots.mapNotNull { snapshot ->
            val old = snapshot.fields[field].orEmpty()
            if (old.isEmpty()) return@mapNotNull null
            val new = transform(old)
            if (new != old) Triple(snapshot.path, field, new) else null
        }
    }

    val previewRows = remember(changes, snapshots, field) {
        changes.take(25).map { (path, _, new) ->
            val snapshot = snapshots.first { it.path == path }
            PreviewRow(
                File(path).name,
                "${snapshot.fields[field].orEmpty()}  →  $new",
            )
        }
    }

    @Composable
    fun ControlsSection() {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BatchActionType.entries.forEach { type ->
                FilterChip(
                    selected = actionType == type,
                    onClick = { actionTypeName = type.name },
                    label = { Text(type.label) },
                )
            }
        }

        Text(
            text = "Field",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
        ) {
            displayedKeys.forEach { key ->
                FilterChip(
                    selected = field == key,
                    onClick = { fieldName = key.name },
                    label = { Text(fieldLabel(key), maxLines = 1) },
                )
            }
        }

        when (actionType) {
            BatchActionType.REGEX -> {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    singleLine = true,
                    label = { Text("Find (regex)") },
                    isError = !regexValid,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = replaceWith,
                    onValueChange = { replaceWith = it },
                    singleLine = true,
                    label = { Text("Replace with (\$1, \$2… allowed)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            BatchActionType.CASE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    CaseMode.entries.forEach { mode ->
                        FilterChip(
                            selected = caseMode == mode,
                            onClick = { caseModeName = mode.name },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !windowInfo.useTwoPane),
        modifier = if (windowInfo.useTwoPane) {
            Modifier.widthIn(max = 780.dp).fillMaxWidth(0.94f)
        } else {
            Modifier
        },
        title = { Text("Batch actions") },
        text = {
            if (windowInfo.useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ControlsSection()
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(
                            text = "${changes.size} value(s) will change",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        PreviewList(
                            rows = previewRows,
                            maxHeight = (windowInfo.heightDp * 0.45f).dp.coerceIn(130.dp, 300.dp),
                        )
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ControlsSection()
                    Text(
                        text = "${changes.size} value(s) will change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    PreviewList(
                        rows = previewRows,
                        maxHeight = (windowInfo.heightDp * 0.28f).dp.coerceIn(110.dp, 240.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(changes)
                    onDismiss()
                },
                enabled = changes.isNotEmpty(),
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
