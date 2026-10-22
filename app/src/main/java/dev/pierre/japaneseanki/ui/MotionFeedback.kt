package dev.pierre.japaneseanki.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

internal enum class TouchFeedbackEvent {
    WORD_SELECTION,
    MEANING_SELECTION,
    MODE_SELECTION,
    SHUTTER_ACCEPTED,
    DELIVERY_VERIFIED,
}

internal fun interface TouchFeedbackDispatcher {
    fun dispatch(event: TouchFeedbackEvent)
}

private object NoTouchFeedback : TouchFeedbackDispatcher {
    override fun dispatch(event: TouchFeedbackEvent) = Unit
}

internal val LocalTouchFeedbackDispatcher = staticCompositionLocalOf<TouchFeedbackDispatcher> {
    NoTouchFeedback
}

internal val LocalMotionEnabled = staticCompositionLocalOf { true }

/** Uses platform haptics without IGNORE_GLOBAL_SETTING, so Android honours touch-feedback settings. */
internal class PlatformTouchFeedbackDispatcher(
    private val view: View,
    private val lifecycleOwner: LifecycleOwner,
) : TouchFeedbackDispatcher {
    override fun dispatch(event: TouchFeedbackEvent) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !view.isShown) return
        val constant = when (event) {
            TouchFeedbackEvent.WORD_SELECTION,
            TouchFeedbackEvent.MEANING_SELECTION,
            TouchFeedbackEvent.MODE_SELECTION -> HapticFeedbackConstants.CLOCK_TICK
            TouchFeedbackEvent.SHUTTER_ACCEPTED -> HapticFeedbackConstants.VIRTUAL_KEY
            TouchFeedbackEvent.DELIVERY_VERIFIED -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
internal fun rememberSystemMotionEnabled(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(readSystemMotionEnabled()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = readSystemMotionEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

private fun readSystemMotionEnabled(): Boolean =
    Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()

/** A non-animated indeterminate indicator that retains progress semantics. */
@Composable
internal fun StaticProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color,
    diameter: Dp,
    strokeWidth: Dp = 2.dp,
    stateDescription: String,
) {
    Canvas(
        modifier = modifier.size(diameter).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            this.stateDescription = stateDescription
        },
    ) {
        val stroke = strokeWidth.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val topLeft = Offset((size.width - radius * 2f) / 2f, (size.height - radius * 2f) / 2f)
        val ringSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        drawArc(
            color = color.copy(alpha = 0.24f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = ringSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = ringSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Motion-aware busy indicator; the non-animated branch remains a labelled progress node. */
@Composable
internal fun MotionAwareProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color,
    diameter: Dp,
    strokeWidth: Dp = 2.dp,
    stateDescription: String,
) {
    if (LocalMotionEnabled.current) {
        CircularProgressIndicator(
            modifier = modifier.size(diameter).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                this.stateDescription = stateDescription
            },
            color = color,
            strokeWidth = strokeWidth,
        )
    } else {
        StaticProgressIndicator(
            modifier = modifier,
            color = color,
            diameter = diameter,
            strokeWidth = strokeWidth,
            stateDescription = stateDescription,
        )
    }
}
