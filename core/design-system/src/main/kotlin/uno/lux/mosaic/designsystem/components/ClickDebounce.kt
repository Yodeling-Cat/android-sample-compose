package uno.lux.mosaic.designsystem.components

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

internal class ClickDebounceGuard(
    private val debounceMs: Long,
    private val timeSource: () -> Long = { SystemClock.uptimeMillis() },
) {
    private var lastClickMs = 0L

    fun tryFire(action: () -> Unit) {
        val now = timeSource()

        if (now - lastClickMs > debounceMs) {
            lastClickMs = now
            action()
        }
    }
}

@Composable
fun (() -> Unit).rememberDebounced(debounceMs: Long = 500L): () -> Unit {
    val currentAction by rememberUpdatedState(this)
    val guard = remember { ClickDebounceGuard(debounceMs) }

    return remember { { guard.tryFire(currentAction) } }
}

fun Modifier.debouncedClickable(
    enabled: Boolean = true,
    debounceMs: Long = 500L,
    onClick: () -> Unit,
): Modifier = composed {
    val debounced = onClick.rememberDebounced(debounceMs)

    clickable(enabled = enabled, onClick = debounced)
}
