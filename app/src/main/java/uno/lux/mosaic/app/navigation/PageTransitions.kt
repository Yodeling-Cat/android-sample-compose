package uno.lux.mosaic.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/*
 * How a full-screen page enters and leaves the `NavDisplay`. Pushing and popping are each other's
 * inverse, and the pop spec doubles as the predictive-back one so the gesture drags the very
 * animation the release plays out. Tab switches inside the shell are not navigation events and get
 * their own fade-through instead — see `ShellScreen`.
 */

/** How long a push or pop between full-screen pages runs. */
private const val PUSH_POP_DURATION_MILLIS = 320

/** Push: the new page slides in from the right while the old one recedes a quarter-width. */
internal fun pushTransition(): ContentTransform {
    val duration = PUSH_POP_DURATION_MILLIS

    return (
        slideInHorizontally(tween(duration)) { width ->
            width
        } + fadeIn(tween(duration))
    ) togetherWith
        (slideOutHorizontally(tween(duration)) { width -> -width / 4 } + fadeOut(tween(duration)))
}

/** Pop: the inverse of [pushTransition] — the leaving page slides back off to the right. */
internal fun popTransition(): ContentTransform {
    val duration = PUSH_POP_DURATION_MILLIS

    return (
        slideInHorizontally(tween(duration)) { width ->
            -width / 4
        } + fadeIn(tween(duration))
    ) togetherWith
        (slideOutHorizontally(tween(duration)) { width -> width } + fadeOut(tween(duration)))
}
