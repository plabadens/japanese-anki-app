package dev.pierre.japaneseanki.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

internal fun hapticConstantFor(event: TouchFeedbackEvent, sdkInt: Int): Int = when (event) {
    TouchFeedbackEvent.WORD_SELECTION,
    TouchFeedbackEvent.MEANING_SELECTION,
    TouchFeedbackEvent.MODE_SELECTION -> HapticFeedbackConstants.CLOCK_TICK
    TouchFeedbackEvent.SHUTTER_ACCEPTED -> HapticFeedbackConstants.VIRTUAL_KEY
    TouchFeedbackEvent.DELIVERY_VERIFIED -> if (sdkInt >= 30) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
}

/** Dispatches only when an attempted interaction changes its model key. */
@Composable
internal fun rememberChangeFeedback(
    event: TouchFeedbackEvent,
    key: Any?,
    enabled: Boolean = true,
): () -> Unit {
    val dispatcher = LocalTouchFeedbackDispatcher.current
    val state = remember(event) { ChangeFeedbackState(lastKey = key) }
    val requestVersion = remember(event) { mutableStateOf(0) }
    val version = requestVersion.value
    val scope = rememberCoroutineScope()
    SideEffect {
        val pending = state.pending
        if (pending != null && key != pending.key) {
            if (enabled) dispatcher.dispatch(event)
            state.pending = null
        }
        state.lastKey = key
    }
    return remember(event, dispatcher, scope, version) {
        {
            val pending = PendingChange(key = state.lastKey, id = ++state.nextRequestId)
            state.pending = pending
            requestVersion.value = pending.id
            scope.launch {
                delay(250)
                if (state.pending?.id == pending.id) {
                    state.pending = null
                    requestVersion.value++
                }
            }
        }
    }
}

private data class PendingChange(val key: Any?, val id: Int)

private class ChangeFeedbackState(
    var lastKey: Any?,
    var pending: PendingChange? = null,
    var nextRequestId: Int = 0,
)

@Immutable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal data class MotionPolicy(val durationScale: Float, val scheme: MotionScheme) {
    val animated: Boolean get() = durationScale > 0f
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val LocalMotion = staticCompositionLocalOf {
    MotionPolicy(1f, MotionScheme.standard())
}

@Immutable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal class FlowMotion(private val scheme: MotionScheme, private val animated: Boolean) {
    fun <T> colour(): FiniteAnimationSpec<T> =
        if (animated) scheme.fastEffectsSpec() else snap()

    fun <T> smallSpatial(): FiniteAnimationSpec<T> =
        if (animated) scheme.fastSpatialSpec() else snap()

    fun <T> containerSpatial(): FiniteAnimationSpec<T> =
        if (animated) scheme.defaultSpatialSpec() else snap()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberFlowMotion(): FlowMotion {
    val policy = LocalMotion.current
    return remember(policy.scheme, policy.animated) {
        FlowMotion(policy.scheme, policy.animated)
    }
}

/** Uses platform haptics without IGNORE_GLOBAL_SETTING, so Android honours touch-feedback settings. */
internal class PlatformTouchFeedbackDispatcher(
    private val view: View,
    private val lifecycleOwner: LifecycleOwner,
) : TouchFeedbackDispatcher {
    override fun dispatch(event: TouchFeedbackEvent) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !view.isShown) return
        view.performHapticFeedback(hapticConstantFor(event, Build.VERSION.SDK_INT))
    }
}

@Composable
internal fun rememberSystemMotionDurationScale(): Float {
    val lifecycleOwner = LocalLifecycleOwner.current
    var durationScale by remember { mutableStateOf(readSystemMotionDurationScale()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) durationScale = readSystemMotionDurationScale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return durationScale
}

private fun readSystemMotionDurationScale(): Float =
    if (Build.VERSION.SDK_INT < 26) 1f else ValueAnimator.getDurationScale()

internal enum class ProgressSize(val diameter: Dp, val strokeWidth: Dp) {
    Small(16.dp, 2.dp),
    Medium(32.dp, 2.dp),
    Large(40.dp, 2.dp),
}

/** A non-animated indeterminate indicator that retains progress semantics. */
@Composable
internal fun StaticProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color,
    progressSize: ProgressSize,
    stateDescription: String,
) {
    Canvas(
        modifier = modifier.size(progressSize.diameter).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            this.stateDescription = stateDescription
        },
    ) {
        val stroke = progressSize.strokeWidth.toPx()
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
    progressSize: ProgressSize,
    stateDescription: String,
) {
    if (LocalMotion.current.animated) {
        CircularProgressIndicator(
            modifier = modifier.size(progressSize.diameter).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                this.stateDescription = stateDescription
            },
            color = color,
            strokeWidth = progressSize.strokeWidth,
        )
    } else {
        StaticProgressIndicator(
            modifier = modifier,
            color = color,
            progressSize = progressSize,
            stateDescription = stateDescription,
        )
    }
}
