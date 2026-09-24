package com.jarvis.remote.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MessageView(item: TranscriptItem, modifier: Modifier = Modifier) {
    when {
        item.role == MessageRole.USER -> UserBubble(item.text, modifier)
        item.role == MessageRole.SYSTEM -> ReasoningBlock(item.text, modifier)
        item.toolName != null -> ToolCard(item, modifier)
        else -> AssistantBubble(item.text, streaming = item.isStreaming, error = item.isError, modifier = modifier)
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
fun AssistantBubble(
    text: String,
    streaming: Boolean = false,
    error: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var cursorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(streaming) {
        if (!streaming) return@LaunchedEffect
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    Row(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = text + if (streaming && cursorVisible) " ▌" else "",
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReasoningBlock(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(item: TranscriptItem, modifier: Modifier = Modifier) {
    var expanded by remember(item.key) { mutableStateOf(!item.collapsed) }
    val accent = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(
                width = 1.dp,
                color = if (item.isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse tool output" else "Expand tool output",
                    tint = accent,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolHeader(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (expanded) "Tap to collapse" else "Tap to expand",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = item.text.takeIf { it.isNotBlank() } ?: "(no output)",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun toolHeader(item: TranscriptItem): String {
    val name = item.toolName ?: "tool"
    val title = item.toolTitle
    return if (title.isNullOrBlank()) name else "$name ($title)"
}