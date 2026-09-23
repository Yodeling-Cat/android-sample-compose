package uno.lux.mosaic.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.CompactCount
import uno.lux.mosaic.common.util.RelativeTime

@Composable
fun RelativeTime.asText(): String = when (this) {
    RelativeTime.Now -> stringResource(R.string.time_now)
    is RelativeTime.Minutes -> stringResource(R.string.time_minutes, value)
    is RelativeTime.Hours -> stringResource(R.string.time_hours, value)
    is RelativeTime.Days -> stringResource(R.string.time_days, value)
    is RelativeTime.Weeks -> stringResource(R.string.time_weeks, value)
}

@Composable
fun CompactCount.asText(): String = when (this) {
    is CompactCount.Ones -> text
    is CompactCount.Thousands -> stringResource(R.string.count_thousands, text)
    is CompactCount.Millions -> stringResource(R.string.count_millions, text)
}

@Composable
fun AppError.asText(): String = when (this) {
    AppError.NoConnection -> stringResource(R.string.error_no_connection)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    is AppError.Http -> serverMessage ?: stringResource(R.string.error_http, code)
    AppError.Unknown -> stringResource(R.string.error_unknown)
}

@Composable
fun FailedAction.asText(): String = when (this) {
    FailedAction.DELETE_POST -> stringResource(R.string.action_failed_delete_post)
    FailedAction.FOLLOW -> stringResource(R.string.action_failed_follow)
    FailedAction.SEND_COMMENT -> stringResource(R.string.action_failed_send_comment)
}

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

fun initials(name: String): String =
    name
        .trim()
        .split(' ')
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString(separator = "")
