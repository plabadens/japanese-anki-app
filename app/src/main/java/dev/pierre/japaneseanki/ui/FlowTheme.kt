package dev.pierre.japaneseanki.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pierre.japaneseanki.R

/**
 * Semantic roles from the portrait expressive-flow study (mockup/expressive-flow/README.md).
 * Pitch keeps its own ink-blue role so theme tinting cannot change the contour colour.
 */
internal object FlowPalette {
    val lightSurface = Color(0xFFFFF8F5)
    val lightOnSurface = Color(0xFF2B1712)
    val lightPrimary = Color(0xFF9C3329)
    val lightOnPrimary = Color(0xFFFFFFFF)
    val lightSecondaryContainer = Color(0xFFF1DDD2)
    val lightOnSecondaryContainer = Color(0xFF4B2B20)
    val lightOutline = Color(0xFF8B6D60)
    val lightSelectedOutline = Color(0xFF795247)
    val lightTertiary = Color(0xFF354B70)
    val lightError = Color(0xFFA51D26)
    val lightSurfaceVariant = Color(0xFFF6E7DE)

    val darkSurface = Color(0xFF1C1411)
    val darkOnSurface = Color(0xFFF5DED4)
    val darkPrimary = Color(0xFFFFB4A3)
    val darkOnPrimary = Color(0xFF5D160D)
    val darkSecondaryContainer = Color(0xFF4A3329)
    val darkOnSecondaryContainer = Color(0xFFF5DCCC)
    val darkOutline = Color(0xFFB89D8E)
    val darkSelectedOutline = Color(0xFFD2A693)
    val darkTertiary = Color(0xFFB3C8EE)
    val darkError = Color(0xFFFFB2B7)
    val darkSurfaceVariant = Color(0xFF2E211C)
}

internal val FlowLightColors = lightColorScheme(
    primary = FlowPalette.lightPrimary,
    onPrimary = FlowPalette.lightOnPrimary,
    primaryContainer = FlowPalette.lightSelectedOutline,
    onPrimaryContainer = Color(0xFFFFF8F5),
    secondary = FlowPalette.lightSelectedOutline,
    onSecondary = Color(0xFFFFF8F5),
    secondaryContainer = FlowPalette.lightSecondaryContainer,
    onSecondaryContainer = FlowPalette.lightOnSecondaryContainer,
    tertiary = FlowPalette.lightTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE6FA),
    onTertiaryContainer = Color(0xFF182E50),
    background = FlowPalette.lightSurface,
    onBackground = FlowPalette.lightOnSurface,
    surface = FlowPalette.lightSurface,
    onSurface = FlowPalette.lightOnSurface,
    surfaceVariant = FlowPalette.lightSurfaceVariant,
    onSurfaceVariant = Color(0xFF6B554B),
    outline = FlowPalette.lightOutline,
    outlineVariant = Color(0xFFD8C3B7),
    error = FlowPalette.lightError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8E6B7),
    onErrorContainer = Color(0xFF4C370A),
)

internal val FlowDarkColors = darkColorScheme(
    primary = FlowPalette.darkPrimary,
    onPrimary = FlowPalette.darkOnPrimary,
    primaryContainer = FlowPalette.darkSelectedOutline,
    onPrimaryContainer = Color(0xFF2B1712),
    secondary = FlowPalette.darkSelectedOutline,
    onSecondary = Color(0xFF2B1712),
    secondaryContainer = FlowPalette.darkSecondaryContainer,
    onSecondaryContainer = FlowPalette.darkOnSecondaryContainer,
    tertiary = FlowPalette.darkTertiary,
    onTertiary = Color(0xFF182E50),
    tertiaryContainer = Color(0xFF273D5E),
    onTertiaryContainer = Color(0xFFDCE6FA),
    background = FlowPalette.darkSurface,
    onBackground = FlowPalette.darkOnSurface,
    surface = FlowPalette.darkSurface,
    onSurface = FlowPalette.darkOnSurface,
    surfaceVariant = FlowPalette.darkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD3B6A9),
    outline = FlowPalette.darkOutline,
    outlineVariant = Color(0xFF5A4339),
    error = FlowPalette.darkError,
    onError = Color(0xFF5D160D),
    errorContainer = Color(0xFF4A3B1D),
    onErrorContainer = Color(0xFFF8E6B7),
)

/** Type scale named by the study (titleLarge 22/500, displaySmall 36/700, bodyLarge 16). */
internal val FlowTypography: Typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        displaySmall = displaySmall.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
internal fun FlowTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) FlowDarkColors else FlowLightColors,
        typography = FlowTypography,
        content = content,
    )
}

/**
 * Widest the portrait composition may become. A rotated or tablet window keeps the study's
 * single-column composition centred instead of stretching its photo slot and sentence.
 */
internal val AppContentMaxWidth = 520.dp


@Composable
internal fun FlowTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val backLabel = stringResource(R.string.back)
    val discardLabel = stringResource(R.string.legacy_capture_discard_draft)
    val settingsLabel = stringResource(R.string.settings)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = backLabel },
            ) { FlowIcons.Back(Modifier.size(24.dp)) }

            onClose != null -> IconButton(
                onClick = onClose,
                modifier = Modifier.semantics { contentDescription = discardLabel },
            ) { FlowIcons.Close(Modifier.size(22.dp)) }

            else -> Box(Modifier.size(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null && onClose == null) 4.dp else 0.dp),
        )
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = settingsLabel }) {
                Icon(MaterialSymbols.Settings, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Primary action: 56 dp high, vermilion container, arrow affordance. */
@Composable
internal fun FlowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (showArrow) {
            Box(Modifier.width(8.dp))
            FlowIcons.Arrow(Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FlowCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        content = content,
    )
}

@Composable
internal fun FlowRoundIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Minimal geometric icons for controls the study uses but MaterialSymbols does not carry. */
internal object FlowIcons {
    @Composable
    fun Back(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.28f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(
            tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.46f, h * 0.32f),
            stroke, StrokeCap.Round,
        )
        drawLine(
            tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.46f, h * 0.68f),
            stroke, StrokeCap.Round,
        )
    }

    @Composable
    fun Arrow(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) = Canvas(modifier) {
        val color = if (tint == Color.Unspecified) Color.White else tint
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.62f, h * 0.28f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.62f, h * 0.72f), stroke, StrokeCap.Round)
    }

    @Composable
    fun ChevronDown(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawLine(tint, Offset(size.width * 0.2f, size.height * 0.4f), Offset(size.width * 0.5f, size.height * 0.68f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.5f, size.height * 0.68f), Offset(size.width * 0.8f, size.height * 0.4f), stroke, StrokeCap.Round)
    }

    @Composable
    fun Gallery(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            tint,
            topLeft = Offset(size.width * 0.14f, size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
            style = Stroke(stroke),
        )
        drawCircle(tint, radius = size.width * 0.06f, center = Offset(size.width * 0.34f, size.height * 0.38f))
        drawLine(
            tint, Offset(size.width * 0.2f, size.height * 0.72f), Offset(size.width * 0.45f, size.height * 0.48f),
            stroke, StrokeCap.Round,
        )
        drawLine(
            tint, Offset(size.width * 0.45f, size.height * 0.48f), Offset(size.width * 0.66f, size.height * 0.66f),
            stroke, StrokeCap.Round,
        )
        drawLine(
            tint, Offset(size.width * 0.66f, size.height * 0.66f), Offset(size.width * 0.78f, size.height * 0.56f),
            stroke, StrokeCap.Round,
        )
    }

    @Composable
    fun Keyboard(modifier: Modifier = Modifier, tint: Color = Color(0xFF2B1712)) = Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            tint,
            topLeft = Offset(size.width * 0.12f, size.height * 0.28f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.46f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.1f),
            style = Stroke(stroke),
        )
        val rowY = listOf(0.42f, 0.56f)
        rowY.forEach { y ->
            var x = 0.22f
            while (x < 0.8f) {
                drawCircle(tint, radius = size.width * 0.025f, center = Offset(size.width * x, size.height * y))
                x += 0.12f
            }
        }
        drawLine(
            tint, Offset(size.width * 0.34f, size.height * 0.66f), Offset(size.width * 0.66f, size.height * 0.66f),
            stroke, StrokeCap.Round,
        )
    }

    @Composable
    fun Expand(modifier: Modifier = Modifier, tint: Color = Color.White) = Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val m = 0.18f
        val arm = 0.16f
        // four corner brackets
        listOf(
            Pair(Offset(w * m, h * m), Offset(w * (m + arm), h * m)),
            Pair(Offset(w * m, h * m), Offset(w * m, h * (m + arm))),
            Pair(Offset(w * (1 - m), h * m), Offset(w * (1 - m - arm), h * m)),
            Pair(Offset(w * (1 - m), h * m), Offset(w * (1 - m), h * (m + arm))),
            Pair(Offset(w * m, h * (1 - m)), Offset(w * (m + arm), h * (1 - m))),
            Pair(Offset(w * m, h * (1 - m)), Offset(w * m, h * (1 - m - arm))),
            Pair(Offset(w * (1 - m), h * (1 - m)), Offset(w * (1 - m - arm), h * (1 - m))),
            Pair(Offset(w * (1 - m), h * (1 - m)), Offset(w * (1 - m), h * (1 - m - arm))),
        ).forEach { (a, b) -> drawLine(tint, a, b, stroke, StrokeCap.Round) }
    }

    @Composable
    fun Check(modifier: Modifier = Modifier, tint: Color = Color.White) = Canvas(modifier) {
        val stroke = 2.4.dp.toPx()
        drawLine(
            tint, Offset(size.width * 0.2f, size.height * 0.52f), Offset(size.width * 0.42f, size.height * 0.74f),
            stroke, StrokeCap.Round,
        )
        drawLine(
            tint, Offset(size.width * 0.42f, size.height * 0.74f), Offset(size.width * 0.82f, size.height * 0.28f),
            stroke, StrokeCap.Round,
        )
    }

    @Composable
    fun Close(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val inset = 0.26f
        drawLine(
            tint, Offset(size.width * inset, size.height * inset),
            Offset(size.width * (1 - inset), size.height * (1 - inset)), stroke, StrokeCap.Round,
        )
        drawLine(
            tint, Offset(size.width * (1 - inset), size.height * inset),
            Offset(size.width * inset, size.height * (1 - inset)), stroke, StrokeCap.Round,
        )
    }

    /** Filled bolt used by the camera flash control in the study's Input view. */
    @Composable
    fun Flash(modifier: Modifier = Modifier, tint: Color = Color.White) = Canvas(modifier) {
        val points = listOf(
            Offset(size.width * 0.58f, size.height * 0.06f),
            Offset(size.width * 0.24f, size.height * 0.55f),
            Offset(size.width * 0.46f, size.height * 0.55f),
            Offset(size.width * 0.40f, size.height * 0.94f),
            Offset(size.width * 0.76f, size.height * 0.44f),
            Offset(size.width * 0.53f, size.height * 0.44f),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, tint)
    }
}
