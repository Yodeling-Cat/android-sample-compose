package uno.lux.mosaic.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.CompactCount
import uno.lux.mosaic.common.util.RelativeTime

/** Resolves a [RelativeTime] bucket to localized compact text ("now", "5m", …). */
@Composable
fun RelativeTime.asText(): String = when (this) {
    RelativeTime.Now -> stringResource(R.string.time_now)
    is RelativeTime.Minutes -> stringResource(R.string.time_minutes, value)
    is RelativeTime.Hours -> stringResource(R.string.time_hours, value)
    is RelativeTime.Days -> stringResource(R.string.time_days, value)
    is RelativeTime.Weeks -> stringResource(R.string.time_weeks, value)
}

/** Resolves a [CompactCount] to localized text, attaching the unit suffix from resources. */
@Composable
fun CompactCount.asText(): String = when (this) {
    is CompactCount.Ones -> text
    is CompactCount.Thousands -> stringResource(R.string.count_thousands, text)
    is CompactCount.Millions -> stringResource(R.string.count_millions, text)
}

/**
 * Maps an [AppError] to its localized user-facing message. [AppError.Http] shows the server's own
 * description of what was wrong when one was parsed out of the error body — untranslated, but the
 * rule that fired beats a generic apology — and names the bare status code otherwise.
 */
@Composable
fun AppError.asText(): String = when (this) {
    AppError.NoConnection -> stringResource(R.string.error_no_connection)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    is AppError.Http -> serverMessage ?: stringResource(R.string.error_http, code)
    AppError.Unknown -> stringResource(R.string.error_unknown)
}

/**
 * Names what a [FailedAction] failed to do, localized. The message states the action rather than
 * the cause: the cause is already logged, and "couldn't delete the post" is what tells the user
 * their tap did nothing.
 */
@Composable
fun FailedAction.asText(): String = when (this) {
    FailedAction.DELETE_POST -> stringResource(R.string.action_failed_delete_post)
    FailedAction.FOLLOW -> stringResource(R.string.action_failed_follow)
    FailedAction.SEND_COMMENT -> stringResource(R.string.action_failed_send_comment)
}

/**
 * A media duration as colon-separated digits: 95 -> "1:35", 615 -> "10:15", 3723 -> "1:02:03".
 * Minutes are zero-padded only past the hour mark; seconds always are. The colon form is
 * locale-neutral, so unlike the bucketed formatters above this returns display text directly.
 * Negative inputs are coerced to zero.
 */
fun formatVideoDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = safe % 3_600 / 60
    val seconds = safe % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Up to two uppercase initials drawn from the first words of a display name:
 * "Ada Lovelace" -> "AL", "Linus" -> "L", "  grace  hopper " -> "GH". Blank input -> "".
 */
fun initials(name: String): String =
    name
        .trim()
        .split(' ')
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString(separator = "")
