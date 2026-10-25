package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll

internal enum class FlowCardVariant {
    Filled,
    Outlined,
}

internal object FlowCardDefaults {
    val ContentPadding = PaddingValues(0.dp)

    @Composable
    fun shape(variant: FlowCardVariant): Shape = when (variant) {
        FlowCardVariant.Filled -> MaterialTheme.shapes.large
        FlowCardVariant.Outlined -> MaterialTheme.shapes.medium
    }
}

@Composable
internal fun FlowCard(
    modifier: Modifier = Modifier,
    variant: FlowCardVariant = FlowCardVariant.Filled,
    contentPadding: PaddingValues = FlowCardDefaults.ContentPadding,
    shape: Shape = FlowCardDefaults.shape(variant),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val cardContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(contentPadding), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
    if (onClick == null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = when (variant) {
                FlowCardVariant.Filled -> MaterialTheme.colorScheme.secondaryContainer
                FlowCardVariant.Outlined -> MaterialTheme.colorScheme.surface
            },
            contentColor = when (variant) {
                FlowCardVariant.Filled -> MaterialTheme.colorScheme.onSecondaryContainer
                FlowCardVariant.Outlined -> MaterialTheme.colorScheme.onSurface
            },
            border = if (variant == FlowCardVariant.Outlined) {
                BorderStroke(MaterialTheme.flow.hairline, MaterialTheme.flow.quietOutline)
            } else {
                null
            },
            content = { cardContent() },
        )
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = cardModifier,
            shape = shape,
            color = when (variant) {
                FlowCardVariant.Filled -> MaterialTheme.colorScheme.secondaryContainer
                FlowCardVariant.Outlined -> MaterialTheme.colorScheme.surface
            },
            contentColor = when (variant) {
                FlowCardVariant.Filled -> MaterialTheme.colorScheme.onSecondaryContainer
                FlowCardVariant.Outlined -> MaterialTheme.colorScheme.onSurface
            },
            border = if (variant == FlowCardVariant.Outlined) {
                BorderStroke(MaterialTheme.flow.hairline, MaterialTheme.flow.quietOutline)
            } else {
                null
            },
            content = { cardContent() },
        )
    }
}

/** One accessible radio option row shared by the settings and setup pickers. */
@Composable
internal fun FlowPickerField(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    supportingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
) {
    val disabledAlpha = MaterialTheme.flow.disabledAlpha
    RowContent(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = MaterialTheme.flow.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        selected = selected,
        label = label,
        enabled = enabled,
        disabledAlpha = disabledAlpha,
        supportingText = supportingText,
        supportingTextColor = supportingTextColor,
    )
}

@Composable
private fun RowContent(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    enabled: Boolean,
    disabledAlpha: Float,
    supportingText: String?,
    supportingTextColor: Color,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else disabledAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) supportingTextColor else supportingTextColor.copy(
                        alpha = supportingTextColor.alpha * disabledAlpha,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FlowStatusText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlowSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp? = null,
    horizontalPadding: Dp? = null,
    verticalPadding: Dp? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    FlowTheme(darkTheme = isSystemInDarkTheme()) {
        val tokens = MaterialTheme.flow
        ModalBottomSheet(onDismissRequest = onDismissRequest) {
            Column(
                modifier.fillMaxWidth()
                    .heightIn(max = maxHeight ?: tokens.sheetMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = horizontalPadding ?: tokens.screenPadding,
                        vertical = verticalPadding ?: tokens.sheetVerticalPadding,
                    ),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

/** Alert-style FlowDialog. Theme is re-provided inside the platform dialog window. */
@Composable
internal fun FlowDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
) {
    FlowTheme(darkTheme = isSystemInDarkTheme()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            properties = properties,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}

/** Content-style FlowDialog for full-page and bespoke surfaces. */
@Composable
internal fun FlowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    FlowTheme(darkTheme = isSystemInDarkTheme()) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
    }
}

/** Shared circular container for the capture controls and setup's camera glyph. */
@Composable
internal fun FlowCircleSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    stateDescription: String? = null,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val semanticsModifier = modifier.semantics {
        contentDescription?.let { this.contentDescription = it }
        stateDescription?.let { this.stateDescription = it }
    }
    val body: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) { content() }
    }
    if (onClick == null) {
        Surface(
            modifier = semanticsModifier,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = { body() },
        )
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = semanticsModifier,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = { body() },
        )
    }
}
