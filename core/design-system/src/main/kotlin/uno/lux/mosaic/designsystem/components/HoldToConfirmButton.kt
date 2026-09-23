package uno.lux.mosaic.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uno.lux.mosaic.designsystem.R
import uno.lux.mosaic.designsystem.theme.MosaicAccentBrush
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import kotlin.time.Duration.Companion.milliseconds

internal enum class HoldPhase { IDLE, HOLDING, HINT }

/**
 * A press outlives the finger while its hint runs, so presses are numbered and only the newest may
 * write [phase].
 */
@Stable
internal class HoldToConfirmState(
    private val holdMillis: Long,
    private val hintMillis: Long,
) {
    var phase: HoldPhase by mutableStateOf(HoldPhase.IDLE)
        private set

    private var presses = 0

    /** [onConfirm] fires under the still-held finger, not on its release. */
    suspend fun press(awaitRelease: suspend () -> Unit, onConfirm: () -> Unit) {
        val press = ++presses
        // Unguarded: nothing can have superseded a press that has not suspended yet.
        phase = HoldPhase.HOLDING

        try {
            val heldLongEnough = withTimeoutOrNull(holdMillis.milliseconds) { awaitRelease() } == null

            if (heldLongEnough) {
                onConfirm()
            } else {
                moveTo(press, HoldPhase.HINT)
                delay(hintMillis.milliseconds)
            }
        } finally {
            // Also the cancellation path: a gesture torn down mid-hold must not leave a stuck fill.
            moveTo(press, HoldPhase.IDLE)
        }
    }

    private fun moveTo(press: Int, next: HoldPhase) {
        if (press == presses) phase = next
    }
}

/** An accessibility service confirms at once through the semantics [onClick]. */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
    hintText: String = stringResource(R.string.hold_to_confirm_hint),
    holdMillis: Long = 1_500L,
    hintMillis: Long = 1_500L,
) {
    val haptics = LocalHapticFeedback.current
    val colors = ButtonDefaults.buttonColors()
    val shape = ButtonDefaults.shape
    val active = enabled && !isBusy
    val accent = MaterialTheme.colorScheme.primary

    // A live button carries the brand gradient; an inert one keeps the same hue, dimmed to a
    // wash, so waiting for the form to fill reads as *not yet* rather than as a different control.
    val container = if (active) {
        MosaicAccentBrush
    } else {
        SolidColor(accent.copy(alpha = DISABLED_CONTAINER_ALPHA))
    }
    val contentColor = if (active) {
        colors.contentColor
    } else {
        accent.copy(alpha = DISABLED_LABEL_ALPHA)
    }

    val state = remember(holdMillis, hintMillis) { HoldToConfirmState(holdMillis, hintMillis) }
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val hintAlpha = remember { Animatable(0f) }

    // Kept current so the gesture isn't torn down and rebuilt every time the caller recomposes.
    val currentConfirm by rememberUpdatedState(onConfirm)
    val confirm = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentConfirm()
    }

    LaunchedEffect(state.phase) {
        val holding = state.phase == HoldPhase.HOLDING
        // The nudge is up for the whole interaction, not just after a release: it is what tells a
        // user mid-hold that the finger has to stay put. Only an untouched button reads [text].
        val nudging = state.phase != HoldPhase.IDLE

        launch { scale.animateTo(if (holding) PRESSED_SCALE else 1f, tween(PRESS_RESPONSE_MILLIS)) }
        launch { hintAlpha.animateTo(if (nudging) 1f else 0f, tween(LABEL_SWAP_MILLIS)) }

        if (holding) {
            // Matching the hold means the sweep lands exactly as the action fires, whatever
            // shape [SweepEasing] gives the travel in between.
            progress.animateTo(1f, tween(holdMillis.toInt(), easing = SweepEasing))
        } else {
            progress.animateTo(0f, tween(DRAIN_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // Every animation here is read in the draw/layer phase, so a frame of the sweep or of
            // the label swap costs no recomposition — only an actual phase change does. The scale
            // shares its layer with the clip rather than stacking a second one.
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.shape = shape
                clip = true
            }.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = MinHeight)
            .background(container)
            .drawBehind {
                val fraction = progress.value
                if (fraction <= 0f) return@drawBehind

                val width = size.width * fraction
                val edge = LeadingEdgeWidth.toPx()

                drawRect(
                    color = contentColor.copy(alpha = FILL_ALPHA),
                    size = Size(width = width, height = size.height),
                )

                // A bright leading edge, so the sweep reads as a moving front rather than a stain.
                drawRect(
                    color = contentColor.copy(alpha = LEADING_EDGE_ALPHA),
                    topLeft = Offset(x = width - edge, y = 0f),
                    size = Size(width = edge, height = size.height),
                )
            }.pointerInput(active) {
                if (!active) return@pointerInput

                detectTapGestures(
                    onPress = {
                        state.press(awaitRelease = { tryAwaitRelease() }, onConfirm = confirm)
                    },
                )
            }.semantics(mergeDescendants = true) {
                role = Role.Button
                // Named here rather than merged up from the labels, since both of them are always
                // in the tree and a screen reader would otherwise read the pair back to back.
                contentDescription = text
                if (!active) disabled()
                // The same [confirm] the gesture fires, so the escape hatch keeps whatever
                // confirming comes to entail rather than drifting into its own version of it.
                onClick(label = text) {
                    if (active) confirm()
                    active
                }
            }.padding(ButtonDefaults.ContentPadding),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                strokeWidth = 2.5.dp,
                color = contentColor,
                modifier = Modifier.size(20.dp),
            )
        } else {
            // The two labels are stacked and both stay in the layout for good — only their alpha
            // moves. Swapping which one is *composed* would resize the box to whichever label is
            // showing, so the shorter one would drift off centrer as the other faded in.
            HoldLabel(text = text, color = contentColor) { 1f - hintAlpha.value }

            HoldLabel(text = hintText, color = contentColor) { hintAlpha.value }
        }
    }
}

/** Both labels stay in the tree, so each clears its own semantics and the button names itself. */
@Composable
private fun HoldLabel(
    text: String,
    color: Color,
    alpha: () -> Float,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clearAndSetSemantics {}
            .graphicsLayer { this.alpha = alpha() },
    )
}

private val MinHeight = 52.dp

private const val PRESSED_SCALE = 0.97f
private const val PRESS_RESPONSE_MILLIS = 120

private const val LABEL_SWAP_MILLIS = 180

private const val DISABLED_CONTAINER_ALPHA = 0.12f

private const val DISABLED_LABEL_ALPHA = 0.55f

private const val FILL_ALPHA = 0.24f
private const val LEADING_EDGE_ALPHA = 0.85f

private val LeadingEdgeWidth = 2.dp

/**
 * The end control point stops short of 1: easing to a standstill would make the bar look finished
 * before the hold fires.
 */
private val SweepEasing = CubicBezierEasing(0.25f, 0.45f, 0.55f, 0.9f)

private const val DRAIN_MILLIS = 220

@Preview(showBackground = true)
@Composable
private fun HoldToConfirmButtonPreview() {
    MosaicTheme {
        HoldToConfirmButton(
            text = "Publish",
            onConfirm = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(name = "Disabled", showBackground = true)
@Composable
private fun HoldToConfirmButtonDisabledPreview() {
    MosaicTheme {
        HoldToConfirmButton(
            text = "Publish",
            onConfirm = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(name = "Busy", showBackground = true)
@Composable
private fun HoldToConfirmButtonBusyPreview() {
    MosaicTheme {
        HoldToConfirmButton(
            text = "Publish",
            onConfirm = {},
            isBusy = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
