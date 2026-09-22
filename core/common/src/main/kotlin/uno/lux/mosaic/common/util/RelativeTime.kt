package uno.lux.mosaic.common.util

import java.time.Duration
import java.time.Instant

/**
 * A relative timestamp bucketed for compact, social-style display. The bucketing is pure
 * and unit-tested here; turning a bucket into localized text ("now", "5m", …) happens in
 * the UI layer against string resources.
 */
sealed interface RelativeTime {
    data object Now : RelativeTime

    data class Minutes(
        val value: Long,
    ) : RelativeTime

    data class Hours(
        val value: Long,
    ) : RelativeTime

    data class Days(
        val value: Long,
    ) : RelativeTime

    data class Weeks(
        val value: Long,
    ) : RelativeTime
}

/** Buckets the gap between [createdAt] and [now]; [now] is injectable for deterministic tests. */
fun relativeTime(createdAt: Instant, now: Instant = Instant.now()): RelativeTime {
    val elapsed = Duration.between(createdAt, now)

    return when {
        elapsed < Duration.ofMinutes(1) -> RelativeTime.Now
        elapsed < Duration.ofHours(1) -> RelativeTime.Minutes(elapsed.toMinutes())
        elapsed < Duration.ofDays(1) -> RelativeTime.Hours(elapsed.toHours())
        elapsed < Duration.ofDays(7) -> RelativeTime.Days(elapsed.toDays())
        else -> RelativeTime.Weeks(elapsed.toDays() / 7)
    }
}
