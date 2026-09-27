package com.jarvis.remote.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvis.remote.data.model.PermissionRequest
import com.jarvis.remote.data.model.QuestionInfo
import com.jarvis.remote.data.model.QuestionRequest

@Composable
fun PermissionRequestDialog(
    request: PermissionRequest,
    responding: Boolean,
    onReply: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!responding) onReply(PERMISSION_REJECT) },
        title = { Text("Permission requested") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = request.permission,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                request.patterns.forEach { pattern ->
                    Text(
                        text = pattern,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (request.always.isNotEmpty()) {
                    Spacer(modifier = Modifier.heightIn(min = 6.dp))
                    Text(
                        text = "Always allow also covers:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    request.always.forEach { pattern ->
                        Text(
                            text = pattern,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (responding) {
                    Spacer(modifier = Modifier.heightIn(min = 4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            DialogButton(text = "Allow once", enabled = !responding, onClick = { onReply(PERMISSION_ONCE) })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DialogButton(
                    text = "Always allow",
                    enabled = !responding,
                    onClick = { onReply(PERMISSION_ALWAYS) },
                )
                DialogButton(
                    text = "Deny",
                    enabled = !responding,
                    onClick = { onReply(PERMISSION_REJECT) },
                )
            }
        },
    )
}

@Composable
fun QuestionRequestDialog(
    request: QuestionRequest,
    responding: Boolean,
    onSubmit: (List<List<String>>) -> Unit,
    onReject: () -> Unit,
) {
    var draft by remember(request.id) { mutableStateOf(PendingPrompts.draftFor(request)) }

    Dialog(
        onDismissRequest = { if (!responding) onReject() },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = !responding),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Questions",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.heightIn(min = 4.dp))
                if (responding) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    request.questions.forEachIndexed { index, info ->
                        QuestionBlock(
                            info = info,
                            entry = draft.getOrNull(index),
                            enabled = !responding,
                            onToggle = { label ->
                                draft = PendingPrompts.toggle(request, draft, index, label)
                            },
                            onCustom = { text ->
                                draft = PendingPrompts.withCustom(draft, index, text)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.heightIn(min = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogButton(text = "Reject", enabled = !responding, onClick = onReject)
                    DialogButton(
                        text = "Submit",
                        enabled = !responding && PendingPrompts.canSubmit(request, draft),
                        onClick = { onSubmit(PendingPrompts.answers(request, draft)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    info: QuestionInfo,
    entry: QuestionDraft?,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onCustom: (String) -> Unit,
) {
    val heading = PendingPrompts.heading(info)
    val selected = entry?.selected.orEmpty()
    val custom = entry?.custom.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (heading.isNotBlank() && heading != info.question) {
            Text(
                text = heading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = info.question,
            style = MaterialTheme.typography.bodyMedium,
        )
        info.options.forEach { option ->
            val checked = option.label in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (info.multiple) {
                            Modifier.toggleable(
                                value = checked,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(option.label) },
                            )
                        } else {
                            Modifier.selectable(
                                selected = checked,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onToggle(option.label) },
                            )
                        }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (info.multiple) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                } else {
                    RadioButton(selected = checked, onClick = null, enabled = enabled)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = option.label, style = MaterialTheme.typography.bodyMedium)
                    if (option.description.isNotBlank()) {
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (info.custom) {
            OutlinedTextField(
                value = custom,
                onValueChange = onCustom,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = false,
                maxLines = 3,
                label = { Text("Custom answer") },
            )
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text)
    }
}

const val PERMISSION_ONCE = "once"
const val PERMISSION_ALWAYS = "always"
const val PERMISSION_REJECT = "reject"
